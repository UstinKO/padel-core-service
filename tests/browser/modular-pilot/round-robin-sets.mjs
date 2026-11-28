import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch();
const context = await browser.newContext({ httpCredentials: {
  origin: input.origin, username: 'preview', password: input.gatewayPassword,
} });
await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
  ? route.continue() : route.abort());
await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
const page = await context.newPage();
page.setDefaultTimeout(20000);
const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('dialog', dialog => dialog.accept());
const read = async path => {
  const response = await context.request.get(input.origin + path);
  assert.equal(response.status(), 200);
  return response.json();
};
const openForm = async form => {
  const details = form.locator('xpath=ancestor::details[1]');
  if (await details.count() && !await details.evaluate(element => element.open)) {
    await details.locator('summary').first().click();
  }
};
const saveScore = async (id, first, second) => {
  const form = page.locator(`#rr-score-${id}`);
  await openForm(form);
  await form.locator('[name=team1Score]').fill(String(first));
  await form.locator('[name=team2Score]').fill(String(second));
  const response = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname === `/tournaments/team-americano/api/round-robin/matches/${id}/result`);
  await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), form.locator('button[type=submit]').click()]);
  const saved = await response;
  assert.equal(saved.status(), 200);
  await expect(page.locator(`#rr-score-${id} [name=team1Score]`)).toHaveValue(String(first));
  await expect(page.locator(`#rr-score-${id} [name=team2Score]`)).toHaveValue(String(second));
};
const report = { result: 'PASS', cases: [], javaScriptErrors: 0 };

try {
  await page.goto(input.origin + '/login?lang=ru');
  await page.locator('#username').fill('preview-admin@example.test');
  await page.locator('#password').fill(input.password);
  await Promise.all([page.waitForURL(url => url.pathname !== '/login'),
    page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  for (const fixture of input.cases) {
    const id = fixture.tournamentId;
    const base = `/tournaments/team-americano/api/${id}/ranking`;
    await page.goto(input.origin + `/admin/tournaments/${id}?lang=ru`);
    const start = page.locator('.rr-start-form');
    await start.locator('[name=courts]').fill('2');
    await start.locator('[name=scoringMode]').selectOption(fixture.mode);
    await Promise.all([page.waitForURL(url => url.pathname === `/tournaments/team-americano/admin/${id}`),
      start.locator('button[type=submit]').click()]);
    const initial = await read(base);
    assert.equal(initial.scoringMode, fixture.mode);
    assert.equal(initial.totalTeams, 4);
    assert.equal(initial.totalRounds, 3);
    assert.deepEqual(initial.ranking.map(team => team.currentPosition),
      fixture.mode === 'SETS' ? [1, 1, 1, 1] : [1, 2, 3, 4]);
    await expect(page.locator('.rr-table-scroll tbody tr td:first-child'))
      .toHaveText(initial.ranking.map(team => String(team.currentPosition)));
    const teams = [...initial.ranking].sort((a, b) => a.teamNumber - b.teamNumber);
    const byId = new Map(teams.map(team => [team.id, team.teamNumber]));
    const byCaptain = new Map(teams.map(team => [team.player1Id, team.teamNumber]));
    const rounds = await read(`/api/tournaments/americano/${id}/rounds`);
    const matches = rounds.flatMap(round => round.matches).map(match => ({ ...match,
      first: byId.get(match.team1Id) ?? byCaptain.get(match.team1Player1Id),
      second: byId.get(match.team2Id) ?? byCaptain.get(match.team2Player1Id),
    }));
    assert.equal(matches.length, 6);
    const scores = fixture.mode === 'SETS'
      ? new Map([['1:2', [6, 4]], ['1:3', [6, 4]], ['1:4', [2, 6]],
        ['2:3', [6, 5]], ['2:4', [6, 5]], ['3:4', [6, 4]]])
      : new Map([['1:2', [18, 14]], ['1:3', [16, 16]], ['1:4', [12, 20]],
        ['2:3', [16, 16]], ['2:4', [16, 16]], ['3:4', [14, 18]]]);
    for (const match of matches) {
      assert.ok(match.first && match.second, 'Матч связан с постоянными парами');
      const key = [match.first, match.second].sort((a, b) => a - b).join(':');
      const canonical = scores.get(key);
      match.scores = match.first < match.second ? canonical : [...canonical].reverse();
      await saveScore(match.id, ...match.scores);
    }
    let circle = await read(base);
    assert.equal(circle.roundRobinComplete, true);
    const expected = fixture.mode === 'SETS' ? [1, 2, 4, 3] : [4, 1, 2, 3];
    assert.deepEqual(circle.ranking.map(team => team.teamNumber), expected);
    if (fixture.mode === 'SETS') {
      const first = circle.ranking.find(team => team.teamNumber === 1);
      const second = circle.ranking.find(team => team.teamNumber === 2);
      assert.equal(first.matchesWon, second.matchesWon);
      assert.equal(first.gameDifference, second.gameDifference);
      const direct = matches.find(match => [match.first, match.second].sort().join(':') === '1:2');
      await saveScore(direct.id, ...[...direct.scores].reverse());
      assert.equal((await read(base)).ranking[0].teamNumber, 2);
      await saveScore(direct.id, ...direct.scores);
      circle = await read(base);
      assert.deepEqual(circle.ranking.map(team => team.teamNumber), expected);
    } else {
      const tied = circle.ranking.filter(team => team.teamNumber !== 4);
      assert.deepEqual(tied.map(team => team.totalScore), [46, 46, 46]);
      assert.deepEqual(tied.map(team => team.matchesWon), [1, 0, 0]);
      assert.deepEqual(circle.ranking.map(team => team.currentPosition), [1, 2, 3, 4]);
      const direct = matches.find(match => [match.first, match.second].sort().join(':') === '1:2');
      await saveScore(direct.id, ...[...direct.scores].reverse());
      assert.deepEqual((await read(base)).ranking.map(team => team.teamNumber), [4, 2, 3, 1]);
      await saveScore(direct.id, ...direct.scores);
      circle = await read(base);
      assert.deepEqual(circle.ranking.map(team => team.teamNumber), expected);
    }
    const beforeExtra = circle.ranking;
    const add = page.locator('[data-rr-add]');
    await openForm(add);
    await add.locator('[name=team1Id]').selectOption(String(teams[2].id));
    await add.locator('[name=team2Id]').selectOption(String(teams[3].id));
    await add.locator('[name=courtNumber]').selectOption('1');
    await add.locator('[name=placementStart]').selectOption('1');
    const added = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/tournaments/team-americano/api/round-robin/${id}/additional`);
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), add.locator('button[type=submit]').click()]);
    const saved = await added;
    assert.equal(saved.status(), 200);
    const additional = (await read(base)).additionalMatches;
    assert.equal(additional.length, 1);
    const extra = additional[0];
    await saveScore(extra.id, ...(fixture.mode === 'SETS' ? [6, 5] : [18, 14]));
    const withExtra = await read(base);
    assert.deepEqual(withExtra.ranking, beforeExtra);
    assert.equal(withExtra.finalRanking[0].teamId, teams[2].id);
    await saveScore(extra.id, ...(fixture.mode === 'SETS' ? [5, 6] : [14, 18]));
    const correctedExtra = await read(base);
    assert.equal(correctedExtra.finalRanking[0].teamId, teams[3].id);
    assert.deepEqual(correctedExtra.ranking, beforeExtra);
    await saveScore(extra.id, ...(fixture.mode === 'SETS' ? [6, 5] : [18, 14]));
    const restoredExtra = await read(base);
    assert.deepEqual(restoredExtra.finalRanking, withExtra.finalRanking);
    assert.deepEqual(restoredExtra.ranking, beforeExtra);
    await Promise.all([page.waitForNavigation({ waitUntil: 'domcontentloaded' }), page.locator('.rr-finish button[type=submit]').click()]);
    await expect(page.locator('.rr-places li')).toHaveCount(4);
    const finished = await read(base);
    assert.deepEqual(finished.ranking, beforeExtra);
    assert.equal(finished.finalRanking[0].teamId, teams[2].id);
    let views = 0;
    for (const width of [1440, 390]) {
      await page.setViewportSize({ width, height: 900 });
      for (const language of ['ru', 'es', 'en']) {
        for (const mode of ['admin', 'public']) {
          const path = mode === 'admin' ? `/tournaments/team-americano/admin/${id}` : `/tournaments/team-americano/${id}`;
          assert.equal((await page.goto(input.origin + path + '?lang=' + language)).status(), 200);
          await expect(page.locator('.rr-table-scroll tbody tr')).toHaveCount(4);
          await expect(page.locator('.rr-places li')).toHaveCount(4);
          await expect(page.locator('.rr-table-scroll tbody tr td:first-child'))
            .toHaveText(finished.ranking.map(team => String(team.currentPosition)));
          await expect(page.locator('.rr-table-scroll tbody tr td:nth-child(2) > strong'))
            .toHaveText(expected.map(number => '№' + number));
          await page.reload();
          await expect(page.locator('.rr-table-scroll tbody tr')).toHaveCount(4);
          await expect(page.locator('.rr-table-scroll tbody tr td:first-child'))
            .toHaveText(finished.ranking.map(team => String(team.currentPosition)));
          views++;
        }
      }
    }
    assert.equal((await page.goto(input.origin + `/admin/tournaments/${id}/conduct-history`)).status(), 200);
    report.cases.push({ mode: fixture.mode, teams: 4, rounds: 3, circleMatches: 6,
      circleOrder: expected, sharedInitialPlaces: fixture.mode === 'SETS',
      headToHeadCorrectionAndReturn: fixture.mode === 'SETS', additionalFinal: true,
      pointsTiesByWinsAndNumber: fixture.mode === 'POINTS', scoreCorrectionAndReturn: true,
      additionalCorrectionAndReturn: true,
      circleStatsPreserved: true, finished: true, historyPage: true, views, refresh: true });
    console.log('Round Robin ' + fixture.mode + ': круг, места, финал, завершение, история и 12 страниц — PASS');
  }
  assert.deepEqual(errors, []);
  await writeFile('target/round-robin-engine/browser.json', JSON.stringify(report, null, 2) + '\n');
} finally {
  await browser.close();
}
