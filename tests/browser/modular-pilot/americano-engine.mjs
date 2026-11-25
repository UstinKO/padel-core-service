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
const base = input.origin + '/api/tournaments/americano/' + input.tournamentId;
const read = async path => {
  const response = await context.request.get(path);
  assert.equal(response.status(), 200);
  return response.json();
};

try {
  await page.goto(input.origin + '/login?lang=ru');
  await page.locator('#username').fill('preview-admin@example.test');
  await page.locator('#password').fill(input.password);
  await Promise.all([page.waitForURL(url => url.pathname !== '/login'),
    page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  await page.goto(input.origin + `/admin/tournaments/${input.tournamentId}?lang=ru`);
  const form = page.locator('#americanoForm');
  await form.locator('[name=courts]').fill('2');
  await form.locator('[name=totalRounds]').fill('7');
  await form.locator('[name=pointsPerMatch]').selectOption('21');
  await form.locator('[name=generationMode]').selectOption('FULL');
  await Promise.all([page.waitForURL(url => url.pathname === `/tournaments/americano/admin/${input.tournamentId}`),
    form.locator('#btnIniciarTorneo').click()]);
  const rounds = await read(base + '/rounds');
  assert.equal(rounds.length, 7);
  const partners = new Set();
  for (const round of rounds) {
    assert.equal(round.matches.length, 2);
    const players = new Set();
    for (const match of round.matches) {
      for (const pair of [[match.team1Player1Id, match.team1Player2Id],
        [match.team2Player1Id, match.team2Player2Id]]) {
        const key = [...pair].sort((a, b) => a - b).join(':');
        assert.ok(!partners.has(key), 'Полный цикл не повторяет партнёрства');
        partners.add(key);
        for (const player of pair) {
          assert.ok(!players.has(player), 'Игрок участвует в раунде один раз');
          players.add(player);
        }
      }
      assert.ok([1, 2].includes(match.courtNumber));
    }
    assert.equal(players.size, 8);
  }
  assert.equal(partners.size, 28);
  await expect(page.locator('.round-card')).toHaveCount(7);
  await expect(page.locator('#currentRoundDisplay')).toHaveText('1');
  for (const match of rounds[0].matches) {
    const card = page.locator(`#match-${match.id}`);
    await card.locator('.score-input').nth(0).fill('13');
    await card.locator('.score-input').nth(1).fill('8');
    const saved = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/tournaments/americano/matches/${match.id}/result`);
    await card.locator('.submit-score-btn').click();
    assert.equal((await saved).status(), 200);
    await expect(card).toHaveAttribute('data-completed', 'true');
  }
  await expect.poll(async () => (await read(base + '/rounds'))[0].status).toBe('COMPLETED');
  await page.reload();
  await page.locator('#nextRound').click();
  await expect(page.locator('#currentRoundDisplay')).toHaveText('2');
  await expect(page.locator('.round-card:visible')).toHaveAttribute('data-round-id', String(rounds[1].id));
  const ranking = await read(base + '/ranking/simple');
  assert.equal(ranking.length, 8);
  assert.deepEqual(ranking.map(player => player.totalScore).sort((a, b) => a - b),
    [8, 8, 8, 8, 13, 13, 13, 13]);
  const views = [];
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 });
    for (const language of ['ru', 'es', 'en']) {
      for (const mode of ['admin', 'public']) {
        const path = mode === 'admin' ? `/tournaments/americano/admin/${input.tournamentId}`
          : `/tournaments/americano/${input.tournamentId}`;
        assert.equal((await page.goto(input.origin + path + '?lang=' + language)).status(), 200);
        await expect(page.locator('.round-card')).toHaveCount(7);
        await page.reload();
        await expect(page.locator('.round-card')).toHaveCount(7);
      }
      views.push({ width, language, adminAndPublic: true, rounds: 7, refresh: true });
    }
  }
  assert.equal((await page.goto(input.origin + `/admin/tournaments/${input.tournamentId}/conduct-history`)).status(), 200);
  assert.deepEqual(errors, []);
  await writeFile('target/americano-engine/browser.json', JSON.stringify({ result: 'PASS',
    players: 8, courts: 2, rounds: 7, matches: 14, uniquePartnerPairs: 28,
    uiInitialization: true, uiResults: 2, firstRoundCompleted: true, displayedNextRound: 2,
    rankingPlayers: ranking.length, views, conductHistoryPage: true, javaScriptErrors: 0 }, null, 2) + '\n');
  console.log('Americano: полное расписание, результаты UI, завершение раунда, следующий раунд, история и 12 страниц — PASS');
} finally {
  await browser.close();
}
