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
  const booking = await playerPage.goto(origin + '/preview/torneo/' + fixture.tournamentIds[1]);
  require(booking.status() === 200, 'Карточка React недоступна синтетическому игроку');
  const profile = await playerPage.goto(origin + '/preview/perfil');
  require(profile.status() === 200, 'Профиль React недоступен синтетическому игроку');
  const cabinet = await playerPage.goto(origin + '/preview/players/dashboard');
  require(cabinet.status() === 200, 'Кабинет React недоступен синтетическому игроку');
  await playerPage.getByRole('heading', {name: /Мои мероприятия|Mis eventos|My events/}).waitFor();
  const searchCard = playerPage.locator('article').filter({has: playerPage.getByRole('heading', {name: 'Preview 3', exact: true})});
  const consent = searchCard.getByRole('button', {name: /Показывать мои контакты|Compartir mis datos|Share my contacts/});
  await consent.click();
  await searchCard.getByRole('button', {name: /Показывать мои контакты|Compartir mis datos|Share my contacts/, pressed: true}).waitFor();
  await playerPage.reload();
  await searchCard.getByRole('button', {name: /Показывать мои контакты|Compartir mis datos|Share my contacts/, pressed: true}).click();
  await searchCard.getByRole('button', {name: /Показывать мои контакты|Compartir mis datos|Share my contacts/, pressed: false}).waitFor();

  const pairCard = playerPage.locator('article').filter({has: playerPage.getByRole('heading', {name: 'Preview 2', exact: true})});
  await pairCard.getByRole('button', {name: /Отменить запись пары|Dar de baja a la pareja|Cancel the pair/}).click();
  await playerPage.getByRole('dialog').getByRole('button', {name: /^(Подтвердить|Confirmar|Confirm)$/}).click();
  await pairCard.waitFor({state: 'hidden'});
  await playerPage.getByRole('button', {name: /История|Historial|History/}).click();
  await pairCard.getByText(/Пара снялась|Se retiró la pareja|Pair withdrew/).waitFor();
  const playerEvents = await player.request.get(origin + '/api/players/me/events');
  const overview = await playerEvents.json();
  require(playerEvents.status() === 200 && overview.history.some(entry => entry.event.name === 'Preview 2'
    && entry.cancellations.some(item => item.reasonCode === 'PAIR_WITHDRAWAL')),
  'API не сохранил отмену пары в истории');

  const promoted = await context();
  const promotedPage = await login(promoted, 'preview-player-2@example.test');
  await promotedPage.goto(origin + '/preview/players/dashboard');
  const promotedCard = promotedPage.locator('article').filter({has: promotedPage.getByRole('heading', {name: 'Preview 2', exact: true})});
  await promotedCard.getByRole('button', {name: /Подтвердить участие|Confirmar participación|Confirm participation/}).click();
  await promotedCard.getByText(/Preview Player 2 · (Подтверждено|Confirmada|Confirmed)/).waitFor();
  console.log('Кабинет React: согласие на показ контактов, отмена пары в истории и подтверждение после продвижения из резерва проверены');
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
