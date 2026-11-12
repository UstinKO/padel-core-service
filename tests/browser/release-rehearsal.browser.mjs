import fs from 'node:fs/promises';
import { createInterface } from 'node:readline';
const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await fs.readFile(process.argv[2], 'utf8'));
const lines = createInterface({ input: process.stdin });
const browser = await chromium.launch({ headless: true });
async function open(marker) {
  const context = await browser.newContext({ httpCredentials: { username: 'preview', password: fixture.gatewayPassword } });
  await context.route('**/*', route => new URL(route.request().url()).origin === fixture.origin ? route.continue() : route.abort());
  const page = await context.newPage();page.setDefaultTimeout(15000);
  await page.goto(fixture.origin + '/login');
  await page.locator('#username').fill('preview-admin@example.test');await page.locator('#password').fill(fixture.password);
  await Promise.all([page.waitForURL(url => !url.pathname.startsWith('/login')), page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);
  await page.goto(fixture.origin + '/preview/torneos');
  await page.waitForFunction(expected => window.__releaseProbe === expected, marker);
  await expect(page.locator('#root')).not.toBeEmpty();
  await page.reload();await page.waitForFunction(expected => window.__releaseProbe === expected, marker);
  return { context, page };
}
try {
  const original = await open('A');
  console.log('A_READY');
  for await (const line of lines) {
    if (line === 'B') {
      const previous = await original.page.evaluate(async path => (await import(path)).default, fixture.previousLazy);
      expect(previous).toBe('A');
      const current = await open('B');
      await current.page.goto(fixture.origin + '/');await expect(current.page.locator('main.main-content')).toBeVisible();
      await expect(current.page.locator('#root')).toHaveCount(0);
      await current.context.close();console.log('B_READY');
    } else if (line === 'A') {
      const restored = await open('A');await restored.context.close();
      console.log('ROLLBACK_READY');break;
    } else throw new Error('Неизвестный этап репетиции');
  }
  await original.context.close();
} finally { lines.close();await browser.close(); }
