import { expect, test, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const resources = resolve(import.meta.dirname, '../../src/main/resources');
const fragment = readFileSync(
  resolve(resources, 'templates/fragments/payment-receipts.html'),
  'utf8',
).match(/<section th:fragment="receipts"[\s\S]*?<\/section>/)![0];
const script = readFileSync(resolve(resources, 'static/js/payment-receipts.js'), 'utf8');
const csrf = readFileSync(resolve(resources, 'static/js/csrf.js'), 'utf8');
const css = readFileSync(resolve(resources, 'static/css/payment-receipts.css'), 'utf8');
const id = '11111111-1111-4111-8111-111111111111';
const original = {
  id,
  registrationId: 100,
  registrationVersion: 0,
  playerId: 42,
  playerName: '<img src=x onerror="window.receiptXss=true"> Учебный игрок',
  eventName: 'Учебное событие',
  contentType: 'image/png',
  state: 'PENDING',
  createdAt: '2030-01-01T12:00:00Z',
  reviewedAt: null,
  rejectionReason: null as string | null,
  paymentId: null as number | null,
  currentCycle: true,
};

function labels(lang: string) {
  const file = readFileSync(resolve(resources, `i18n/messages_${lang}.properties`), 'utf8');
  return Object.fromEntries(
    Array.from(file.matchAll(/^([^#\r\n=]+)=(.*)$/gm), (match) => [match[1], match[2]]),
  );
}

function html(lang: string, accepted = false) {
  const copy = labels(lang);
  let section = fragment.replace(
    /th:text="#\{(receipt\.ui\.[\w]+)\}">[^<]*/g,
    (match, key: string) => match.replace(/>[^<]*$/, '>' + copy[key]),
  );
  section = section.replace(
    /<option th:each="method[\s\S]*?<\/option>/,
    `<option value="BANK_TRANSFER">${copy['enum.paymentMethod.BANK_TRANSFER']}</option>`,
  );
  section = section.replace(
    'aria-labelledby="receipts-title"',
    'aria-labelledby="receipts-title" data-receipts-url="/api/admin/tournaments/10/receipts"',
  );
  return `<!doctype html><html lang="${lang}"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width">
    <style>body{font-family:Arial;margin:16px}.btn{padding:10px;border:1px solid #cbd5e1;border-radius:6px;cursor:pointer}
    .btn-primary{background:#047857;color:white}${css}</style></head><body>
    ${section}<form id="paymentForm"><input aria-label="Legacy amount" name="amount" value="${accepted ? '1000' : ''}">
    <button type="submit">Save legacy</button></form><a data-receipt-registration="100" hidden></a>
    <script>${csrf}</script><script>${script}</script></body></html>`;
}

async function fixture(page: Page, lang = 'ru', currentCycle = true) {
  const state = {
    receipt: { ...original, currentCycle },
    decisions: [] as Array<{ path: string; body: Record<string, unknown>; csrf?: string }>,
  };
  await page
    .context()
    .addCookies([{ name: 'XSRF-TOKEN', value: 'fixture-csrf', domain: '127.0.0.1', path: '/' }]);
  await page.route('**/__receipt-ui', (route) =>
    route.fulfill({
      contentType: 'text/html',
      body: html(lang, state.receipt.state === 'ACCEPTED'),
    }),
  );
  await page.route('**/api/admin/tournaments/10/receipts**', async (route) => {
    const request = route.request();
    if (request.method() === 'POST') {
      const body = request.postDataJSON();
      state.decisions.push({ path: request.url(), body, csrf: request.headers()['x-xsrf-token'] });
      state.receipt = {
        ...state.receipt,
        state: request.url().endsWith('/accept') ? 'ACCEPTED' : 'REJECTED',
        rejectionReason: body.reason || null,
        paymentId: request.url().endsWith('/accept') ? 200 : null,
      };
      await route.fulfill({ contentType: 'application/json', body: JSON.stringify(state.receipt) });
    } else if (request.url().endsWith('/file')) {
      await route.fulfill({
        contentType: 'image/png',
        body: Buffer.from(
          'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/aksAAAAASUVORK5CYII=',
          'base64',
        ),
      });
    } else {
      await route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify([state.receipt]),
      });
    }
  });
  await page.goto('/__receipt-ui');
  await expect(page.locator('.receipt-card')).toHaveCount(1);
  return state;
}

for (const lang of ['es', 'ru', 'en']) {
  test(`receipt review is localized and readable: ${lang}`, async ({ page }, testInfo) => {
    const copy = labels(lang);
    await fixture(page, lang);
    await expect(page.getByRole('heading', { name: copy['receipt.ui.title'] })).toBeVisible();
    await expect(page.locator('.receipt-card img')).toHaveCount(0);
    await expect(page.locator('.receipt-card h3')).toContainText('<img');
    await expect(
      page.getByRole('button', { name: copy['receipt.ui.accept'], exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: copy['receipt.ui.view'] }).click();
    await expect(page.locator('.receipt-preview')).toBeVisible();
    expect(
      await page
        .locator('#payment-receipts')
        .evaluate((node) => node.scrollWidth <= node.clientWidth + 1),
    ).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`receipts-${lang}.png`), fullPage: true });
  });
}

test('approval uses CSRF, submits once, and reloads current payment data', async ({ page }) => {
  const state = await fixture(page);
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page
    .getByRole('button', { name: 'Подтвердить оплату', exact: true })
    .evaluate((button: HTMLButtonElement) => {
      button.click();
      button.click();
    });
  await expect(page.locator('.receipt-state')).toHaveText('Оплата подтверждена');
  await expect(page.getByLabel('Legacy amount')).toHaveValue('1000');
  expect(state.decisions).toHaveLength(1);
  expect(state.decisions[0].csrf).toBe('fixture-csrf');
  expect(state.decisions[0].body).toEqual({
    amount: '1000',
    currency: 'ARS',
    method: 'BANK_TRANSFER',
  });
});

test('rejection keeps unsaved payment edits and renders the reason as text', async ({ page }) => {
  const state = await fixture(page);
  await page.getByLabel('Legacy amount').fill('2000');
  await page.locator('.receipt-reject summary').click();
  const reason = '<img src=x onerror="window.receiptXss=true"> Нужен итоговый чек';
  await page.getByLabel('Что нужно исправить').fill(reason);
  await page.getByRole('button', { name: 'Запросить другой чек', exact: true }).click();
  await expect(page.locator('.receipt-state')).toHaveText('Нужен другой чек');
  await expect(page.locator('.receipt-reason')).toHaveText(reason);
  await expect(page.locator('.receipt-reason img')).toHaveCount(0);
  await expect(page.getByLabel('Legacy amount')).toHaveValue('2000');
  await expect(page.getByLabel('Legacy amount')).toBeEnabled();
  expect(state.decisions[0].path).toContain('/reject');
});

test('unsaved payment edits block approval before any request', async ({ page }) => {
  const state = await fixture(page);
  await page.getByLabel('Legacy amount').fill('2000');
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page.getByRole('button', { name: 'Подтвердить оплату', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Сначала сохрани');
  expect(state.decisions).toHaveLength(0);
});

test('an uncertain payment response locks the old form until reload', async ({ page }) => {
  await fixture(page);
  await page.route('**/receipts/*/accept', (route) => route.abort('failed'));
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page.getByRole('button', { name: 'Подтвердить оплату', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('обнови страницу');
  await expect(page.getByLabel('Legacy amount')).toBeDisabled();
  await expect(page.locator('[data-receipts-reload]')).toBeVisible();
});

test('old cycles show history without payment actions', async ({ page }) => {
  await fixture(page, 'ru', false);
  await expect(page.locator('.receipt-outdated')).toBeVisible();
  await expect(page.locator('.receipt-review')).toHaveCount(0);
});
