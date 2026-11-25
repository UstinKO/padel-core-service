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
const siteCss = ['style.css', 'admin.css', 'payment-management.css']
  .map((file) => readFileSync(resolve(resources, 'static/css/' + file), 'utf8'))
  .join('\n');
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
    <style>${siteCss}${css}body{margin:16px}</style></head><body>
    ${section}<form id="paymentForm"><table><tbody>
    <tr data-registration-id="100"><td>Учебный игрок <button type="button" data-receipt-registration="100" aria-haspopup="dialog" aria-controls="receipt-review-dialog" hidden></button></td>
    <td><input aria-label="Legacy amount" name="amount" value="${accepted ? '1000' : ''}"></td></tr>
    <tr data-registration-id="100" data-test-partner><td>Партнёр</td></tr>
    <tr data-registration-id="200"><td>Другой игрок <button type="button" data-receipt-registration="200" hidden></button></td></tr>
    <tr data-registration-id="300"><td>Без чека <button type="button" data-receipt-registration="300" hidden></button></td></tr>
    </tbody></table><button type="submit">Save legacy</button></form>
    <script>${csrf}</script><script>${script}</script></body></html>`;
}

async function fixture(
  page: Page,
  lang = 'ru',
  currentCycle = true,
  open = true,
  extra: (typeof original)[] = [],
) {
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
        body: JSON.stringify([state.receipt, ...extra]),
      });
    }
  });
  await page.goto('/__receipt-ui');
  await expect(page.locator('[data-receipt-registration="100"]')).toBeVisible();
  if (open) await page.locator('[data-receipt-registration="100"]').click();
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
  await expect(page.getByLabel('Legacy amount')).toHaveValue('1000');
  await expect(page.locator('[data-receipt-registration="100"]')).toContainText('Принят');
  await page.locator('[data-receipt-registration="100"]').click();
  await expect(page.locator('.receipt-state')).toHaveText('Оплата подтверждена');
  expect(state.decisions).toHaveLength(1);
  expect(state.decisions[0].csrf).toBe('fixture-csrf');
  expect(state.decisions[0].body).toEqual({
    amount: '1000',
    currency: 'ARS',
    method: 'BANK_TRANSFER',
  });
});

test('rejection keeps unsaved payment edits and renders the reason as text', async ({ page }) => {
  const state = await fixture(page, 'ru', true, false);
  await page.getByLabel('Legacy amount').fill('2000');
  await page.locator('[data-receipt-registration="100"]').click();
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
  const state = await fixture(page, 'ru', true, false);
  await page.getByLabel('Legacy amount').fill('2000');
  await page.locator('[data-receipt-registration="100"]').click();
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page.getByRole('button', { name: 'Подтвердить оплату', exact: true }).click();
  await expect(page.locator('.receipt-dialog [data-receipts-status]')).toContainText(
    'Сначала сохрани',
  );
  expect(state.decisions).toHaveLength(0);
});

test('an uncertain payment response locks the old form until reload', async ({ page }) => {
  await fixture(page);
  await page.route('**/receipts/*/accept', (route) => route.abort('failed'));
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page.getByRole('button', { name: 'Подтвердить оплату', exact: true }).click();
  await expect(page.locator('.receipt-dialog [data-receipts-status]')).toContainText(
    'обнови страницу',
  );
  await expect(page.getByLabel('Legacy amount')).toBeDisabled();
  await expect(page.locator('.receipt-dialog [data-receipts-reload]')).toBeVisible();
  await page.locator('[data-receipts-close]').click();
  await expect(page.locator('#payment-receipts > [data-receipts-reload]')).toBeFocused();
});

test('old cycles show history without payment actions', async ({ page }) => {
  await fixture(page, 'ru', false);
  await expect(page.locator('.receipt-outdated')).toBeVisible();
  await expect(page.locator('.receipt-review')).toHaveCount(0);
});

test('initial view stays compact and opens only the selected player receipts', async ({ page }) => {
  await fixture(page, 'ru', true, false, [
    {
      ...original,
      id: 'other',
      registrationId: 200,
      playerName: 'Другой игрок',
      state: 'ACCEPTED',
    },
  ]);
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('.receipt-card')).toHaveCount(0);
  await expect(page.locator('[data-receipt-registration="100"]')).toContainText('На проверке');
  await expect(page.locator('[data-receipt-registration="200"]')).toContainText('Принят');
  await expect(page.locator('[data-receipt-registration="300"]')).toBeHidden();
  const bounds = await page.locator('#payment-receipts').boundingBox();
  expect(bounds!.height).toBeLessThan(120);
  await page.locator('[data-receipt-registration="200"]').click();
  await expect(page.locator('.receipt-card')).toHaveCount(1);
  await expect(page.locator('.receipt-card h3')).toHaveText('Другой игрок');
  await expect(page.locator('.receipt-review')).toHaveCount(0);
});

test('pending filter keeps paired rows and form values without counting old cycles', async ({
  page,
}) => {
  await fixture(page, 'ru', true, false, [
    { ...original, id: 'old', registrationId: 200, currentCycle: false },
  ]);
  await page.getByLabel('Legacy amount').fill('1234');
  await expect(page.locator('[data-receipts-count]')).toHaveText('1');
  await page.locator('[data-receipts-pending]').click();
  await expect(page.locator('[data-receipts-pending]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('tr[data-registration-id="100"]')).toHaveCount(2);
  await expect(page.locator('tr[data-test-partner]')).toBeVisible();
  await expect(page.locator('tr[data-registration-id="200"]')).toBeHidden();
  await expect(page.locator('tr[data-registration-id="300"]')).toBeHidden();
  await page.locator('[data-receipts-clear]').click();
  await expect(page.locator('tr[data-registration-id="200"]')).toBeVisible();
  await expect(page.getByLabel('Legacy amount')).toHaveValue('1234');
  await expect(page.locator('[data-receipt-registration="200"]')).toContainText('История');
});

test('all receipts preserves history for a removed player and multiple files', async ({ page }) => {
  await fixture(page, 'ru', true, false, [
    { ...original, id: 'newer', state: 'ACCEPTED', createdAt: '2030-01-02T12:00:00Z' },
    {
      ...original,
      id: 'removed',
      registrationId: 999,
      playerName: 'Снятый игрок',
      currentCycle: false,
    },
  ]);
  await expect(page.locator('[data-receipt-registration="100"]')).toContainText('На проверке · 2');
  await page.locator('[data-receipt-registration="100"]').click();
  await expect(page.locator('.receipt-card')).toHaveCount(2);
  await page.locator('[data-receipts-close]').click();
  await page.locator('[data-receipts-all]').click();
  await expect(page.locator('.receipt-card')).toHaveCount(3);
  await expect(page.locator('#receipt-removed .receipt-outdated')).toBeVisible();
  await expect(page.locator('#receipt-removed .receipt-review')).toHaveCount(0);
});

test('dialog traps focus and Escape restores the player button', async ({ page }) => {
  await fixture(page);
  await expect(page.locator('[data-receipts-close]')).toBeFocused();
  await page.keyboard.press('Tab');
  await expect(page.getByRole('button', { name: 'Посмотреть фото' })).toBeFocused();
  await page.keyboard.press('Shift+Tab');
  await expect(page.locator('[data-receipts-close]')).toBeFocused();
  await page.getByLabel('Legacy amount').evaluate((input: HTMLInputElement) => input.focus());
  await expect(page.getByLabel('Legacy amount')).not.toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.locator('[data-receipt-registration="100"]')).toBeFocused();
  await expect(page.locator('.receipt-card')).toHaveCount(0);
});

test('rejecting the last pending receipt updates the filter and returns focus safely', async ({
  page,
}) => {
  await fixture(page, 'ru', true, false);
  await page.locator('[data-receipts-pending]').click();
  await page.locator('[data-receipt-registration="100"]').click();
  await page.locator('.receipt-reject summary').click();
  await page.getByLabel('Что нужно исправить').fill('Нужен другой тестовый файл');
  await page.getByRole('button', { name: 'Запросить другой чек', exact: true }).click();
  await expect(page.locator('[data-receipts-count]')).toHaveText('0');
  await expect(page.locator('.receipt-state')).toHaveText('Нужен другой чек');
  await page.locator('[data-receipts-close]').click();
  await expect(page.locator('[data-receipts-clear]')).toBeFocused();
  await expect(page.locator('[data-receipts-empty-filter]')).toBeVisible();
  await page.locator('[data-receipts-clear]').click();
  await expect(page.locator('[data-receipt-registration="100"]')).toContainText('Нужен другой');
});

test('PDF uses the protected download and the dialog fits a narrow screen', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 640 });
  await fixture(page, 'ru', true, false, [
    {
      ...original,
      id: 'pdf',
      registrationId: 200,
      contentType: 'application/pdf',
      playerName: 'ОченьДлинноеИмя'.repeat(20),
    },
  ]);
  await page.locator('[data-receipt-registration="200"]').click();
  await expect(page.getByRole('button', { name: 'Посмотреть фото' })).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Скачать чек' })).toHaveAttribute(
    'href',
    '/api/admin/tournaments/10/receipts/pdf/file',
  );
  const box = await page.getByRole('dialog').boundingBox();
  expect(box!.x).toBeGreaterThanOrEqual(0);
  expect(box!.x + box!.width).toBeLessThanOrEqual(320);
  expect(
    await page.getByRole('dialog').evaluate((node) => node.scrollWidth <= node.clientWidth + 1),
  ).toBe(true);
  await page
    .getByRole('button', { name: 'Подтвердить оплату', exact: true })
    .scrollIntoViewIfNeeded();
  await expect(page.locator('[data-receipts-close]')).toBeInViewport();
});

test('an expired session reports the error inside the open dialog', async ({ page }) => {
  await fixture(page);
  await page.route('**/receipts/*/accept', (route) =>
    route.fulfill({ status: 403, contentType: 'application/json', body: '{}' }),
  );
  await page.getByLabel('Сумма по чеку').fill('1000');
  await page.getByLabel('Способ оплаты').selectOption('BANK_TRANSFER');
  await page.getByRole('button', { name: 'Подтвердить оплату', exact: true }).click();
  await expect(page.locator('.receipt-dialog [data-receipts-status]')).toContainText(
    'сессия завершена',
  );
  await expect(page.locator('.receipt-dialog [data-receipts-reload]')).toBeVisible();
});

test('refresh updates row status and pending counter without opening the dialog', async ({
  page,
}) => {
  await fixture(page, 'ru', true, false);
  await page.route('**/api/admin/tournaments/10/receipts', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify([{ ...original, state: 'REJECTED' }]),
    }),
  );
  await page.locator('[data-receipts-refresh]').click();
  await expect(page.locator('[data-receipt-registration="100"]')).toContainText('Нужен другой');
  await expect(page.locator('[data-receipts-count]')).toHaveText('0');
  await expect(page.getByRole('dialog')).not.toBeVisible();
});
