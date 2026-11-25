import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch();
const context = await browser.newContext({ httpCredentials: {
  origin: input.origin, username: 'preview', password: input.gatewayPassword,
} });
const cdn = new Set([
  'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js',
  'https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js',
]);
await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
  || cdn.has(route.request().url()) ? route.continue() : route.abort());
await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
const page = await context.newPage();
const errors = [];
const diagnostics = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {
  if (message.type() === 'error' || message.type() === 'warning') diagnostics.push(message.text());
});
page.on('dialog', dialog => dialog.accept());

try {
  await page.goto(input.origin + '/login?lang=ru');
  await page.locator('#username').fill('preview-admin@example.test');
  await page.locator('#password').fill(input.password);
  await Promise.all([page.waitForURL(url => url.pathname !== '/login'),
    page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  const token = (await context.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN');
  let kingId = input.kingId;
  if (!kingId) {
    const initialized = await context.request.post(input.origin
      + `/api/king-of-court/tournaments/${input.tournamentId}/initialize`,
    { headers: { 'X-XSRF-TOKEN': decodeURIComponent(token.value) }, data: { maxCourts: 2, calibrationRounds: 0 } });
    assert.equal(initialized.status(), 200);
    kingId = (await initialized.json()).id;
  }
  const state = async () => {
    const response = await context.request.get(input.origin + `/api/king-of-court/tournaments/${kingId}/state`);
    assert.equal(response.status(), 200);
    return response.json();
  };
  const first = await state();
  assert.equal(first.currentRound, 1);
  assert.equal(first.courts.length, 2);
  await page.goto(input.origin + `/admin/tournaments/king-of-court/${kingId}?lang=ru`);
  await expect(page.locator('.court-card')).toHaveCount(2);
  await page.waitForFunction(() => window.kingOfCourt?.stompClient?.connected);
  await page.evaluate(() => {
    window.kingMessages = 0;
    window.kingOfCourt.stompClient.subscribe(`/topic/tournament/${window.kingOfCourt.kingId}`,
      () => window.kingMessages++);
  });
  for (const court of first.courts) {
    await page.locator(`.enter-result-btn[data-court-id="${court.id}"]`).click();
    await page.locator('#winner1').selectOption(String(court.teams[0].players[0].id));
    await expect(page.locator('#winner2')).toHaveValue(String(court.teams[0].players[1].id));
    await page.locator('#scoreWinners').fill('6');
    await page.locator('#scoreLosers').fill('3');
    const saved = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/king-of-court/matches/result');
    await page.locator('#save-result-btn').click();
    assert.equal((await saved).status(), 200);
    await expect(page.locator('#resultModal')).toBeHidden();
    const current = await state();
    await page.waitForFunction(version => window.kingOfCourt.conductVersion === version, current.conductVersion);
  }
  await expect(page.locator('#nextRoundBtn')).toBeVisible();
  const advanced = page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith('/next-round'));
  await page.locator('#nextRoundBtn').click();
  assert.equal((await advanced).status(), 200);
  await page.waitForFunction(() => window.kingOfCourt.currentRound === 2 && window.kingMessages > 0);
  const second = await state();
  const pair = team => team.players.map(player => player.id).sort((a, b) => a - b).join(':');
  const previous = new Set(first.courts.flatMap(court => court.teams.map(pair)));
  assert.equal(second.currentRound, 2);
  assert.equal(second.courts.length, 2);
  for (const court of second.courts) {
    for (const team of court.teams) assert.ok(!previous.has(pair(team)), 'Не должно быть немедленного повтора партнёров');
  }
  const messages = await page.evaluate(() => window.kingMessages);
  const views = [];
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 });
    for (const language of ['ru', 'es', 'en']) {
      assert.equal((await page.goto(input.origin + `/admin/tournaments/king-of-court/${kingId}?lang=${language}`)).status(), 200);
      await expect(page.locator('.court-card')).toHaveCount(2);
      await page.reload();
      await expect(page.locator('.court-card')).toHaveCount(2);
      views.push({ width, language, courts: 2, refresh: true });
    }
  }
  assert.equal((await page.goto(input.origin + `/admin/tournaments/${input.tournamentId}/conduct-history`)).status(), 200);
  assert.deepEqual(errors, []);
  await writeFile('target/king-engine/browser.json', JSON.stringify({ result: 'PASS', kingId,
    initialized: true, uiResults: 2, nextRound: 2, immediatePartnerRepeats: 0,
    webSocketMessages: messages, javaScriptErrors: 0, views, conductHistoryPage: true,
    webSocketLibraries: 'Два штатных CDN script URL; остальные внешние запросы заблокированы' }, null, 2) + '\n');
  console.log('King of Court: initialize/UI results/next round/real WS/history/6 views PASS');
} catch (error) {
  console.error(JSON.stringify({ diagnostics, errors, libraries: await page.evaluate(() => ({
    sockjs: typeof SockJS, stomp: typeof Stomp, connected: window.kingOfCourt?.connected,
  })) }));
  throw error;
} finally {
  await browser.close();
}
