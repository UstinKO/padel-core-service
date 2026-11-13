import fs from 'node:fs/promises';
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await fs.readFile(process.argv[2], 'utf8'));
const { origin, gatewayPassword, password } = fixture;
const browser = await chromium.launch({ headless: true });
const require = (condition, message) => { if (!condition) throw new Error(message); };
async function context(authenticated = true) {
  const context = await browser.newContext(authenticated ? {httpCredentials: {username: 'preview', password: gatewayPassword}} : {});
  await context.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
  return context;
}
async function login(context, email) {
  const page = await context.newPage();
  await page.goto(origin + '/login?lang=ru');
  await page.locator('#username').fill(email);
  await page.locator('#password').fill(password);
  await Promise.all([page.waitForURL(url => !url.pathname.startsWith('/login')), page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  return page;
}
async function csrf(context) {
  const cookie = (await context.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN');
  return {'X-XSRF-TOKEN': decodeURIComponent(cookie.value)};
}
try {
  const external = await context(false);
  for (const path of ['/__preview', '/api/public/config', '/web/index.html', '/app/', '/login', '/actuator/health/readiness', '/__preview/mail']) {
    const response = await external.request.get(origin + path);
    require(response.status() === 401, 'Нет внешнего барьера: ' + path);
    require(response.headers()['x-robots-tag']?.includes('noindex'), 'Нет noindex: ' + path);
  }
  const guest = await context();
  const guestPage = await guest.newPage();
  await guestPage.goto(origin + '/preview/torneos');
  require(new URL(guestPage.url()).pathname === '/login', 'Preview разрешён без сессии приложения');
  const player = await context();
  const playerPage = await login(player, 'preview-player-0@example.test');
  const forbidden = await player.request.get(origin + '/api/internal/ui-preview');
  require(forbidden.status() === 403, 'Игрок читает административную диагностику');
  const forbiddenReset = await player.request.post(origin + '/api/internal/ui-preview/reset', {headers: await csrf(player)});
  require(forbiddenReset.status() === 403, 'Игрок выполняет reset');
  const preview = await playerPage.goto(origin + '/preview/torneos');
  require(preview.status() === 403, 'Неожиданный ответ preview для игрока: HTTP ' + preview.status()
    + ', путь ' + new URL(preview.url()).pathname);
  const admin = await context();
  const page = await login(admin, 'preview-admin@example.test');
  const status = await admin.request.get(origin + '/api/internal/ui-preview');
  require(status.status() === 200 && (await status.json()).environment === 'preview', 'Нет диагностики preview');
  require((await admin.request.post(origin + '/api/internal/ui-preview/reset')).status() === 403, 'Reset обходит CSRF');
  for (const path of ['/preview/torneos', '/web/index.html', '/', '/app/']) {
    const response = await page.goto(origin + path);
    require(response.status() === 200, 'Недоступен маршрут: ' + path);
    require(response.headers()['x-environment'] === 'preview', 'Нет маркировки среды: ' + path);
    if (path.startsWith('/preview')) {
      await page.locator('#root').waitFor();
      await page.reload();
      require(await page.locator('#root').innerText() !== '', 'React не отрисовался');
    }
  }
  require((await admin.request.get(origin + '/oauth2/authorization/google')).status() === 404, 'OAuth доступен в preview');
  await page.goto(origin + '/__preview');
  require((await page.textContent('body')).includes('Тестовый контур'), 'Нет маркированной точки входа');
  await page.screenshot({path: 'target/ui-preview-qa/landing.png', fullPage: true});
  console.log('Браузер: внешний барьер, роли, session/CSRF, deep link/refresh, React/legacy/Expo и отключение OAuth проверены');
} finally { await browser.close(); }
