import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { setTimeout } from 'node:timers/promises';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch();
const cases = [];
let rateLimitRetries = 0;
let queue = Promise.resolve();
const origin = input.origin;

async function contextFor(width) {
  const context = await browser.newContext({ viewport: { width, height: 900 }, locale: 'ru-RU',
    httpCredentials: { origin, username: 'preview', password: input.gatewayPassword } });
  await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: origin }]);
  await context.route('**/*', route => new URL(route.request().url()).origin === origin
    ? route.continue() : route.abort());
  await context.route('**/api/**', async route => {
    if (new URL(route.request().url()).origin !== origin) return route.abort();
    const wait = queue;
    queue = queue.then(() => setTimeout(1100));
    await wait;
    let response = await route.fetch();
    if (response.status() === 429) {
      rateLimitRetries++;
      await setTimeout((Number(response.headers()['retry-after'] ?? '1') + 1) * 1000);
      response = await route.fetch();
      assert.notEqual(response.status(), 429, 'Лимит сохраняется после Retry-After');
    }
    return route.fulfill({ response });
  });
  return context;
}

try {
  for (const width of [1440, 390]) {
    const context = await contextFor(width);
    const page = await context.newPage();
    page.setDefaultTimeout(20000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(origin + '/login?lang=ru');
    await page.locator('#username').fill('preview-admin@example.test');
    await page.locator('#password').fill(input.password);
    await Promise.all([page.waitForURL(url => url.pathname !== '/login'),
      page.locator('form.auth-form button[type=submit]').click()]);
    for (const language of ['ru', 'es', 'en']) {
      const translations = JSON.parse(await readFile(`frontend/src/i18n/${language}.json`, 'utf8'));
      const document = await page.goto(origin + '/preview/clubs?lang=' + language);
      assert.equal(document.status(), 200);
      if (input.empty) {
        await expect(page.locator('.club-card')).toHaveCount(0);
        await expect(page.getByText(translations.clubs.empty, { exact: true })).toBeVisible();
        cases.push({ width, language, emptyServerList: true });
        continue;
      }
      const list = await context.request.get(origin + '/api/public/clubs');
      assert.equal(list.status(), 200);
      const clubs = await list.json();
      await expect(page.locator('.club-card h2 a')).toHaveText(clubs.map(club => club.name));
      assert.ok(!clubs.some(club => club.id === input.hidden.id));
      await page.locator('.club-card h2 a').filter({ hasText: input.nullable.nombre }).click();
      await expect(page.getByRole('heading', { level: 1, name: input.nullable.nombre, exact: true })).toBeVisible();
      await expect(page.getByText(translations.clubs.noDescription, { exact: true })).toBeVisible();
      await expect(page.locator('.entity-heading + section a[target="_blank"]')).toHaveCount(0);
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', origin + '/clubs/' + input.nullable.id);
      await page.reload();
      await expect(page.getByRole('heading', { level: 1, name: input.nullable.nombre, exact: true })).toBeVisible();
      let text = await page.locator('main').innerText();
      assert.ok(!text.includes('qa-private') && !text.includes('undefined') && !text.includes('[object Object]'));

      await page.goto(origin + '/preview/clubs/' + input.full.id + '?lang=' + language);
      await expect(page.getByRole('heading', { level: 1, name: input.full.nombre, exact: true })).toBeVisible();
      await expect(page.locator('a[href="https://example.test/map"]')).toHaveAttribute('rel', /noopener/);
      await expect(page.locator('a[href="https://example.test/club"]')).toHaveAttribute('rel', /noopener/);
      text = await page.locator('main').innerText();
      assert.ok(text.includes('Описание <&>') && !text.includes('qa-private'));

      const catalogue = await context.request.get(origin + '/api/public/catalog?size=12');
      assert.equal(catalogue.status(), 200);
      const event = (await catalogue.json()).items[0];
      assert.ok(event, 'В отдельном preview должно быть событие каталога');
      const selected = page.waitForResponse(response => {
        const url = new URL(response.url());
        return url.pathname === '/api/public/catalog' && url.searchParams.get('clubId') === String(event.club.id);
      });
      await page.goto(origin + '/preview/torneos?from=2000-01-01&lang=' + language);
      await page.locator('label').filter({ has: page.locator('select') })
        .filter({ hasText: translations.catalog.club }).locator('select').selectOption(String(event.club.id));
      const filtered = await selected;
      assert.equal(filtered.status(), 200);
      const data = await filtered.json();
      assert.ok(data.items.length > 0 && data.items.every(item => item.club.id === event.club.id));
      const linkedEvent = data.items[0];
      await page.locator(`.event-card h3 a[href$="/torneo/${linkedEvent.id}"]`).click();
      await page.locator(`.entity-facts a[href$="/clubs/${linkedEvent.club.id}"]`).click();
      await expect(page.getByRole('heading', { level: 1, name: linkedEvent.club.name, exact: true })).toBeVisible();

      for (const id of [input.hidden.id, '9223372036854775807']) {
        const response = await page.goto(origin + '/preview/clubs/' + id + '?lang=' + language);
        assert.equal(response.status(), 404);
        await expect(page.locator('.system-state h1')).toHaveText(translations.details.notFound);
        await page.reload();
        await expect(page.locator('.system-state h1')).toHaveText(translations.details.notFound);
        await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', /noindex/);
      }
      cases.push({ width, language, listOrder: true, nullableCard: true, escapedPublicText: true,
        privateFieldsAbsent: true, canonicalUrl: true, deepLinkAndRefresh: true,
        clubFilter: true, catalogueToEventToClub: true, inactiveAndUnknown404: true });
    }
    assert.deepEqual(errors, []);
    await context.close();
  }
  const result = { result: 'PASS', label: input.label, empty: input.empty, cases,
    javaScriptErrors: 0, rateLimitRetries, externalRequestsBlocked: true };
  await writeFile(`target/club-acceptance/browser-${input.label}${input.empty ? '-empty' : ''}.json`,
    JSON.stringify(result, null, 2) + '\n');
  console.log(`club browser ${input.label}${input.empty ? ' empty' : ''}: ${cases.length} PASS`);
} finally {
  await browser.close();
}
