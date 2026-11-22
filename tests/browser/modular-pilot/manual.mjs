// Этот браузер не принимает и не отклоняет confirm: выбор делает человек.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createInterface } from 'node:readline';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const plan = JSON.parse(await readFile('target/modular-pilot/manual-plan.json', 'utf8'));
assert.equal(plan.jarSha256, input.jarSha256);
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch({ headless: false });
const cdn = new Set(['https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js',
  'https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js']);
const emails = { manager: 'pilot-manager@example.test', foreign: 'pilot-foreign@example.test',
  admin: 'preview-admin@example.test', anonymous: null };
const contexts = new Map();

async function open(key, role = 'manager', view = 'adminPath') {
  assert.ok(Object.hasOwn(plan.cases, key), 'Неизвестный ручной кейс');
  assert.ok(Object.hasOwn(emails, role), 'Неизвестная роль');
  assert.ok(['adminPath', 'publicPath', 'reactPath', 'historyPath'].includes(view));
  let context = contexts.get(role);
  if (!context) {
    context = await browser.newContext({ locale: 'ru-RU', httpCredentials: {
      origin: input.origin, username: 'preview', password: input.gatewayPassword,
    } });
    contexts.set(role, context);
    // Включая вкладки, которые человек откроет самостоятельно.
    context.on('page', page => page.on('dialog', () => {}));
    await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
      || cdn.has(route.request().url()) ? route.continue() : route.abort());
    await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
    if (emails[role]) {
      const login = await context.newPage();
      await login.goto(input.origin + '/login?lang=ru');
      await login.locator('#username').fill(emails[role]);
      await login.locator('#password').fill(input.password);
      await login.locator('form.auth-form button[type="submit"]').click();
      await login.waitForURL(url => url.pathname !== '/login');
      await login.close();
    }
  }
  const page = await context.newPage();
  await page.goto(input.origin + plan.cases[key][view]);
  if (view === 'adminPath' && ['M01', 'M02', 'M03'].includes(key)) {
    await page.locator('form[action$="/init-playoff"]').scrollIntoViewIfNeeded();
  }
  console.log(`Открыт ${key}, роль ${role}, ${view}; ручной итог не изменён`);
}

try {
  await open('M01');
  const commands = createInterface({ input: process.stdin });
  console.log('Для новой вкладки: M02 manager adminPath. Закройте окно для выхода; resume-manual откроет его снова.');
  browser.on('disconnected', () => commands.close());
  for await (const line of commands) {
    try {
      const [key, role, view] = line.trim().split(/\s+/);
      await open(key, role, view);
    } catch {
      console.error('Не удалось открыть вкладку; проверьте кейс/роль/маршрут и доступность стенда');
    }
  }
} finally {
  await browser.close();
}
