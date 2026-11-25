import { browser, assert, expect, input, write } from './support.mjs';

try {
  const context = await browser.newContext({ httpCredentials: {
    origin: input.origin, username: 'preview', password: input.gatewayPassword,
  } });
  await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
    ? route.continue() : route.abort());
  const page = await context.newPage();
  const isSchedule = response => new URL(response.url()).pathname === '/api/public/tournaments';
  const ordinary = page.waitForResponse(isSchedule);
  await page.goto(input.origin + '/app/');
  const denied = await ordinary;
  assert.equal(denied.status(), 401, 'Обычный preview-клиент сохраняет ограничение шлюза');
  assert.match(denied.headers()['www-authenticate'], /Basic/);

  const authorization = 'Basic ' + Buffer.from('preview:' + input.gatewayPassword).toString('base64');
  await page.route(input.origin + '/api/public/tournaments*', route => route.continue({
    headers: { ...route.request().headers(), authorization },
  }));
  const adapted = page.waitForResponse(response => isSchedule(response) && response.status() === 200);
  await page.reload();
  const events = await (await adapted).json();
  assert.ok(events.length > 0, 'API действительно вернул события');
  await expect.poll(async () => {
    const body = await page.locator('body').innerText();
    return events.some(event => body.includes(event.club.name));
  }).toBe(true);
  await write('expo-smoke', { ordinaryPreviewApi: 401, gatewayBasicChallenge: true,
    qaAdapterApi: 200, events: events.length, rendered: true, externalRequestsBlocked: true,
    ordinaryPreviewLimitationPreserved: true });
  console.log('Expo: API 200 и события отрисованы через QA-адаптер; обычный preview сохраняет Basic 401');
} finally {
  await browser.close();
}
