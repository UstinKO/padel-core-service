import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const readonly = process.argv.includes('--read-only');
const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch();
const errors = [];
const contexts = [];
const output = 'target/round-robin-acceptance';
const dictionaries = Object.fromEntries(await Promise.all(['ru', 'es', 'en'].map(async language => {
  const text = await readFile(`src/main/resources/i18n/messages_${language}.properties`, 'utf8');
  return [language, Object.fromEntries(text.split(/\r?\n/).filter(line => line.includes('='))
    .map(line => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]))];
})));
const sample = JSON.parse(await readFile('src/test/resources/contracts/round-robin-query/points-initial.json', 'utf8'));
const createPage = async () => {
  const context = await browser.newContext({ httpCredentials: {
    origin: input.origin, username: 'preview', password: input.gatewayPassword,
  } });
  contexts.push(context);
  await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
    ? route.continue() : route.abort());
  await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
  const page = await context.newPage();
  page.setDefaultTimeout(20000);
  page.on('pageerror', error => errors.push(error.message));
  page.on('dialog', dialog => dialog.accept());
  return page;
};
const admin = await createPage();
const publicPage = await createPage();
const read = async path => {
  const response = await admin.context().request.get(input.origin + path);
  assert.equal(response.status(), 200);
  return response.json();
};
const ranking = id => read(`/tournaments/team-americano/api/${id}/ranking`);
const adminPath = id => `/tournaments/team-americano/admin/${id}`;
const openForm = async form => {
  const details = form.locator('xpath=ancestor::details[1]');
  if (await details.count() && !await details.evaluate(element => element.open)) await details.locator('summary').first().click();
};
const score = async (id, first, second) => {
  const form = admin.locator(`#rr-score-${id}`);
  await openForm(form);
  await form.locator('[name=team1Score]').fill(String(first));
  await form.locator('[name=team2Score]').fill(String(second));
  const response = admin.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname === `/tournaments/team-americano/api/round-robin/matches/${id}/result`);
  await Promise.all([admin.waitForNavigation({ waitUntil: 'domcontentloaded' }), form.locator('button[type=submit]').click()]);
  assert.equal((await response).status(), 200);
};
const additional = async (fixture, teams, first, second, place, court) => {
  const previous = new Set((await ranking(fixture.tournamentId)).additionalMatches.map(match => match.id));
  const form = admin.locator('[data-rr-add]');
  await openForm(form);
  await form.locator('[name=team1Id]').selectOption(String(teams[first - 1].id));
  await form.locator('[name=team2Id]').selectOption(String(teams[second - 1].id));
  await form.locator('[name=placementStart]').selectOption(place === null ? '' : String(place));
  await form.locator('[name=courtNumber]').selectOption(String(court));
  await form.locator('[name=label]').fill('Acceptance QA');
  const response = admin.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith(`/${fixture.tournamentId}/additional`));
  await Promise.all([admin.waitForNavigation({ waitUntil: 'domcontentloaded' }), form.locator('button[type=submit]').click()]);
  assert.equal((await response).status(), 200);
  const added = (await ranking(fixture.tournamentId)).additionalMatches.filter(match => !previous.has(match.id));
  assert.equal(added.length, 1);
  return added[0];
};
const contract = body => {
  assert.deepEqual(Object.keys(body).sort(), Object.keys(sample).sort());
  for (const row of body.ranking) {
    assert.deepEqual(Object.keys(row).sort(), Object.keys(sample.ranking[0]).sort());
    for (const field of ['setDifference', 'qualStatus', 'tournamentStatus', 'registrationSource', 'hasPaid', 'attended', 'adminComment']) {
      assert.equal(row[field], null);
    }
    assert.equal(Object.hasOwn(row, 'player1PlayingSide'), false);
    assert.equal(Object.hasOwn(row, 'player2PlayingSide'), false);
  }
};
const matrix = async (fixture, expected, final) => {
  let views = 0;
  for (const width of [1440, 390]) for (const language of ['ru', 'es', 'en']) {
    for (const [page, kind] of [[admin, 'admin'], [publicPage, 'public']]) {
      await page.setViewportSize({ width, height: 900 });
      const path = kind === 'admin' ? adminPath(fixture.tournamentId) : `/tournaments/team-americano/${fixture.tournamentId}`;
      assert.equal((await page.goto(input.origin + path + '?lang=' + language)).status(), 200);
      await expect(page.locator('html')).toHaveAttribute('lang', language);
      await expect(page.locator('.rr-banner strong')).toHaveText(dictionaries[language]['rr.title']);
      const verify = async () => {
        await expect(page.locator('.rr-table-scroll tbody tr td:first-child'))
          .toHaveText(expected.ranking.map(team => String(team.currentPosition)));
        await expect(page.locator('.rr-table-scroll tbody tr td:nth-child(2) > strong'))
          .toHaveText(expected.ranking.map(team => '№' + team.teamNumber));
        await expect(page.locator('.rr-table-scroll tbody tr td:nth-child(2) > span'))
          .toHaveText(expected.ranking.map(team => team.displayName));
        if (fixture.mode === 'SETS') {
          await expect(page.locator('.rr-table-scroll tbody tr td:nth-child(3)'))
            .toHaveText(expected.ranking.map(team => String(team.matchesWon)));
          await expect(page.locator('.rr-table-scroll tbody tr td:nth-child(4)'))
            .toHaveText(expected.ranking.map(team => (team.gameDifference > 0 ? '+' : '') + team.gameDifference));
        } else {
          for (const [index, field] of ['totalScore', 'matchesPlayed', 'matchesWon', 'matchesLost', 'pointDifference'].entries()) {
            await expect(page.locator(`.rr-table-scroll tbody tr td:nth-child(${index + 4})`))
              .toHaveText(expected.ranking.map(team => String(team[field])));
          }
          if (width === 390) {
            await page.locator('.rr-table-scroll').evaluate(element => element.scrollLeft = element.scrollWidth);
            assert.ok(await page.locator('.rr-table-scroll').evaluate(element => element.scrollLeft > 0));
            await page.locator('.rr-table-scroll').evaluate(element => element.scrollLeft = 0);
          }
        }
        if (final) {
          await expect(page.locator('.rr-places li > strong')).toHaveText(expected.finalRanking.map(team => '№' + team.teamNumber));
          await expect(page.locator('.rr-place')).toHaveText(expected.finalRanking.map(team => team.position + '.'));
        }
        if (fixture.kind === 'guest') {
          const firstRow = page.locator('.rr-table-scroll tbody tr').first();
          await expect(firstRow).toContainText('Invitado QA 1378');
          const sides = firstRow.locator('.playing-side-info');
          await expect(sides.first()).toContainText('—');
          await expect(sides.first()).toContainText(dictionaries[language]['playingSide.left']);
          await expect(sides.last()).toContainText('—');
          if (kind === 'admin') await expect(sides.last()).toContainText(dictionaries[language]['playingSide.missing']);
          await expect(page.locator('.rr-table-scroll tbody tr').nth(1).locator('.playing-side-info').last())
            .toContainText(dictionaries[language]['playingSide.right']);
        }
      };
      await verify();
      await page.reload();
      await verify();
      if (!readonly && width === 390 && language === 'ru') {
        await page.locator('.rr').screenshot({ path: `${output}/${fixture.key}-${kind}.png` });
      }
      views++;
    }
  }
  assert.equal((await admin.goto(input.origin + `/admin/tournaments/${fixture.tournamentId}?lang=ru`)).status(), 200);
  const resume = admin.locator(`a[href="${adminPath(fixture.tournamentId)}"]`).first();
  await expect(resume).toBeVisible();
  await Promise.all([admin.waitForURL(url => url.pathname === adminPath(fixture.tournamentId)), resume.click()]);
  await expect(admin.locator('.rr-table-scroll tbody tr')).toHaveCount(expected.totalTeams);
  return views;
};

const report = { result: 'PASS', readonly, cases: [], javaScriptErrors: 0, sameOriginOnly: true };
try {
  await admin.goto(input.origin + '/login?lang=ru');
  await admin.locator('#username').fill('preview-admin@example.test');
  await admin.locator('#password').fill(input.password);
  await Promise.all([admin.waitForURL(url => url.pathname !== '/login'),
    admin.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  for (const fixture of input.cases) {
    const id = fixture.tournamentId;
    if (fixture.kind === 'extended' && !readonly) {
      await admin.goto(input.origin + `/admin/tournaments/${id}?lang=ru`);
      const start = admin.locator('.rr-start-form');
      await start.locator('[name=courts]').fill('3');
      await start.locator('[name=scoringMode]').selectOption(fixture.mode);
      await Promise.all([admin.waitForURL(url => url.pathname === adminPath(id)), start.locator('button[type=submit]').click()]);
      const initial = await ranking(id);
      contract(initial);
      assert.equal(initial.totalRounds, 5);
      assert.equal(initial.totalTeams, 6);
      assert.deepEqual(initial.ranking.map(team => team.currentPosition), fixture.mode === 'SETS' ? [1, 1, 1, 1, 1, 1] : [1, 2, 3, 4, 5, 6]);
      const teams = [...initial.ranking].sort((a, b) => a.teamNumber - b.teamNumber);
      const byCaptain = new Map(teams.map(team => [team.player1Id, team.teamNumber]));
      const byId = new Map(teams.map(team => [team.id, team.teamNumber]));
      const matches = (await read(`/api/tournaments/americano/${id}/rounds`)).flatMap(round => round.matches);
      assert.equal(matches.length, 15);
      const win = fixture.mode === 'SETS' ? 6 : 18;
      const lose = fixture.mode === 'SETS' ? 4 : 14;
      for (const [index, match] of matches.entries()) {
        const first = byId.get(match.team1Id) ?? byCaptain.get(match.team1Player1Id);
        const second = byId.get(match.team2Id) ?? byCaptain.get(match.team2Player1Id);
        assert.ok(first && second);
        await score(match.id, first < second ? win : lose, first < second ? lose : win);
        if (index === 0) {
          const partial = await ranking(id);
          assert.equal(partial.completedRounds, 0);
          assert.equal(partial.finished, false);
          assert.equal(partial.roundRobinComplete, false);
          assert.equal(partial.ranking.reduce((total, team) => total + team.matchesPlayed, 0), 2);
        }
      }
      const circle = await ranking(id);
      assert.deepEqual(circle.ranking.map(team => team.teamNumber), [1, 2, 3, 4, 5, 6]);
      assert.deepEqual(circle.ranking.map(team => team.matchesWon), [5, 4, 3, 2, 1, 0]);
      assert.equal(circle.finished, true);
      if (fixture.mode === 'POINTS') assert.deepEqual(circle.ranking.map(team => team.totalScore), [90, 86, 82, 78, 74, 70]);
      else assert.deepEqual(circle.ranking.map(team => team.gameDifference), [10, 6, 2, -2, -6, -10]);
      const final = await additional(fixture, teams, 5, 6, 1, 1);
      await expect(admin.locator('.rr-finish button[type=submit]')).toBeDisabled();
      await score(final.id, lose, win);
      const beforeCorrection = await ranking(id);
      assert.equal(beforeCorrection.finalRanking[0].teamNumber, 6);
      await admin.goto(input.origin + `/admin/tournaments/${id}/conduct-history`);
      const version = await admin.locator('a[href*="version="]').first().getAttribute('href');
      assert.ok(version);
      await admin.goto(input.origin + adminPath(id));
      await score(final.id, win, lose);
      assert.equal((await ranking(id)).finalRanking[0].teamNumber, 5);
      await admin.goto(input.origin + version);
      const restore = admin.locator('form[action$="/restore"]');
      await expect(restore.locator('button')).toBeVisible();
      const response = admin.waitForResponse(item => item.request().method() === 'POST' && new URL(item.url()).pathname.endsWith('/restore'));
      await Promise.all([admin.waitForNavigation({ waitUntil: 'domcontentloaded' }), restore.locator('button').click()]);
      assert.equal((await response).status(), 302);
      assert.deepEqual(await ranking(id), beforeCorrection);
      await admin.goto(input.origin + adminPath(id));
      const bronze = await additional(fixture, teams, 3, 4, 3, 2);
      await score(bronze.id, lose, win);
      assert.deepEqual((await ranking(id)).finalRanking.map(team => team.teamNumber), [6, 5, 4, 3, 1, 2]);
      const other = await additional(fixture, teams, 1, 2, 5, 3);
      await score(other.id, lose, win);
      const placed = await ranking(id);
      assert.deepEqual(placed.finalRanking.map(team => team.teamNumber), [6, 5, 4, 3, 2, 1]);
      const free = await additional(fixture, teams, 1, 3, null, 1);
      assert.deepEqual((await ranking(id)).finalRanking, placed.finalRanking);
      await expect(admin.locator('.rr-finish button[type=submit]')).toBeDisabled();
      await score(free.id, win, lose);
      const completed = await ranking(id);
      assert.deepEqual(completed.ranking, circle.ranking);
      assert.deepEqual(completed.finalRanking, placed.finalRanking);
      await Promise.all([admin.waitForNavigation({ waitUntil: 'domcontentloaded' }), admin.locator('.rr-finish button[type=submit]').click()]);
      assert.deepEqual(await ranking(id), completed);
    }
    const body = await ranking(id);
    contract(body);
    if (fixture.kind === 'empty') {
      assert.equal(body.totalRounds, 0);
      assert.equal(body.totalTeams, 0);
      assert.equal(body.finished, false);
      assert.equal(body.pointsPerMatch, 32);
      assert.equal(body.courts, 1);
      assert.deepEqual(body.ranking, []);
      assert.equal((await publicPage.goto(input.origin + `/tournaments/team-americano/${id}`)).status(), 200);
      await expect(publicPage.locator('.rr-table-scroll')).toHaveCount(0);
      assert.equal((await admin.goto(input.origin + `/admin/tournaments/${id}`)).status(), 200);
      report.cases.push({ kind: 'empty', mode: 'POINTS', defaults: true, publicAndAdminCard: true });
      continue;
    }
    if (fixture.kind === 'guest') {
      const team = body.ranking.find(row => row.teamNumber === 1);
      assert.equal(team.player1Nivel, null);
      assert.equal(team.player2Nivel, null);
      assert.equal(team.player2Id, null);
      assert.equal(team.player2Name, 'Invitado QA 1378');
      assert.equal(team.player2Phone, '+5491100000137');
    } else {
      assert.deepEqual(body.ranking.map(team => team.teamNumber), [1, 2, 3, 4, 5, 6]);
      assert.deepEqual(body.finalRanking.map(team => team.teamNumber), [6, 5, 4, 3, 2, 1]);
      assert.equal(body.additionalMatches.length, 4);
      assert.equal(body.finished, true);
    }
    const views = await matrix(fixture, body, fixture.kind === 'extended');
    report.cases.push({ kind: fixture.kind, mode: fixture.mode, views, refresh: true, exactFields: true, statisticsMatchApi: true,
      ...(fixture.kind === 'extended' ? readonly ? { circleAndFinalVerified: true } : { partial: true, circle: true, final: true, bronze: true, otherPlace: true,
        unplaced: true, pendingFinishDisabled: true, historyRestore: true, circleStatisticsPreserved: true }
        : { guest: true, nullableLevels: true, sideVisibleInHtmlOnly: true }) });
    console.log(`Круговой read-срез ${fixture.key}: браузер, API и 12 представлений — PASS`);
  }
  assert.deepEqual(errors, []);
  await writeFile(`${output}/${readonly ? 'read-only-browser' : 'browser'}.json`, JSON.stringify(report, null, 2) + '\n');
} catch (error) {
  await writeFile(process.argv[2] + '.admin-failure-private.html', await admin.content(), { mode: 0o600 });
  await writeFile(process.argv[2] + '.public-failure-private.html', await publicPage.content(), { mode: 0o600 });
  throw error;
} finally {
  for (const context of contexts) await context.close();
  await browser.close();
}
