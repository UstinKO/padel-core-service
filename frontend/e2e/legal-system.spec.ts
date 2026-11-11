import { expect, test } from '@playwright/test';
const anonymous = { authenticated: false, userType: null, id: null, roles: [], fullName: null };
test.beforeEach(async ({ page, context }) => {
  await context.addCookies([
    { name: 'lang', value: 'en', url: 'http://127.0.0.1:4173' },
    { name: 'XSRF-TOKEN', value: 'cookie-csrf', url: 'http://127.0.0.1:4173' },
  ]);
  await page.route('**/api/auth/session', (route) => route.fulfill({ json: anonymous }));
});
test('legal copy, canonical, all languages and keyboard cookie customization', async ({ page }) => {
  const thirdParty: string[] = [];
  page.on('request', (request) => {
    if (/google-analytics|clarity\.ms|connect\.facebook/.test(request.url()))
      thirdParty.push(request.url());
  });
  await page.route('**/api/cookies/customize?*', async (route) => {
    expect(route.request().headers()['x-xsrf-token']).toBe('cookie-csrf');
    expect(new URL(route.request().url()).searchParams.get('analytics')).toBe('true');
    await route.fulfill({
      status: 200,
      headers: { 'Set-Cookie': 'cookieConsent=customized; Path=/; SameSite=Lax' },
      body: '',
    });
  });
  await page.goto('/preview/privacidad');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Privacy');
  await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/privacidad$/);
  await page.getByRole('button', { name: 'Cookie settings', exact: true }).click();
  await page.getByRole('dialog').getByLabel('Analytics', { exact: true }).check();
  await page.getByRole('button', { name: 'Save choice', exact: true }).click();
  await expect(page.getByRole('dialog')).not.toBeVisible();
  await expect(page.getByRole('button', { name: 'Cookie settings', exact: true })).toBeFocused();
  expect(
    (await page.context().cookies()).find((cookie) => cookie.name === 'cookieConsent')?.value,
  ).toBe('customized');
  await page.getByRole('combobox', { name: /Language|Idioma|Язык/ }).selectOption('ru');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('конфиденциальности');
  await page.getByRole('combobox', { name: /Language|Idioma|Язык/ }).selectOption('es');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Privacidad');
  expect(thirdParty).toEqual([]);
});
test('failed cookie save stays visible and never replays a mutation automatically', async ({
  page,
  context,
}) => {
  let posts = 0;
  await page.route('**/api/cookies/accept', async (route) => {
    posts++;
    await route.fulfill({ status: 503, body: '' });
  });
  await page.goto('/preview/cookies');
  await page.getByRole('button', { name: 'Accept all', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('could not be saved');
  await context.setOffline(true);
  await expect(
    page.getByText('Check your connection. Forms will not be submitted again automatically.'),
  ).toBeVisible();
  await context.setOffline(false);
  await page.waitForTimeout(200);
  expect(posts).toBe(1);
  expect(
    (await context.cookies()).find((cookie) => cookie.name === 'cookieConsent'),
  ).toBeUndefined();
});
test('session expiry preserves a safe return and forbidden never forces another login', async ({
  page,
}) => {
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      json: {
        ...anonymous,
        authenticated: true,
        userType: 'PLAYER',
        id: 42,
        fullName: 'Expired player',
      },
    }),
  );
  await page.route('**/api/players/me', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/session-required',
        title: 'Sign in',
        detail: 'Sign in',
        status: 401,
        instance: '/api/players/me',
        code: 'SESSION_REQUIRED',
      }),
    }),
  );
  await page.route('**/api/public/config', (route) =>
    route.fulfill({ json: { captchaEnabled: false, googleEnabled: false } }),
  );
  await page.goto('/preview/perfil');
  await expect(page).toHaveURL(/preview\/login\?expired=true&destination=%2Fpreview%2Fperfil/);
  await expect(page.getByRole('status')).toContainText('Sign in again');
  await page.goto('/preview/error/403');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Access denied');
  await expect(
    page.locator('main').getByRole('link', { name: 'Sign in', exact: true }),
  ).toHaveCount(0);
});
test('429 exposes actual wait and 503 offers a read retry without empty shell', async ({
  page,
}) => {
  await page.route('**/api/public/home', (route) =>
    route.fulfill({
      status: 429,
      headers: { 'Retry-After': '37' },
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/rate-limited',
        title: 'Wait',
        detail: 'Wait',
        status: 429,
        instance: '/api/public/home',
        code: 'RATE_LIMITED',
      }),
    }),
  );
  await page.goto('/preview/');
  await expect(page.getByRole('heading', { name: 'Too many requests' })).toBeVisible();
  await expect(
    page
      .getByRole('alert')
      .filter({ hasText: 'Too many requests' })
      .getByRole('button', { name: 'Try again', exact: true }),
  ).toBeDisabled();
  await expect(page.getByText(/Wait time: 3[67] seconds/)).toBeVisible();
  await page.goto('/preview/error/503');
  await expect(page.getByRole('heading', { name: 'Service unavailable' })).toBeVisible();
  await page.goto('/preview/unknown-path');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});

test('form errors have translated labels and focus the corresponding controls', async ({
  page,
}) => {
  await page.route('**/api/public/config', (route) =>
    route.fulfill({ json: { captchaEnabled: false, googleEnabled: false } }),
  );
  await page.route('**/api/players/registro', (route) =>
    route.fulfill({
      status: 422,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/validation-error',
        status: 422,
        title: 'Validation',
        detail: 'Correct fields',
        instance: '/api/players/registro',
        code: 'VALIDATION_ERROR',
        fieldErrors: [
          {
            field: 'recaptchaToken',
            code: 'CAPTCHA_INVALID',
            message: 'Complete the security check.',
          },
        ],
      }),
    }),
  );
  await page.goto('/preview/players/registro');
  for (const [label, value] of [
    ['First name', 'Maria'],
    ['Last name', 'Garcia'],
    ['Email', 'maria@example.test'],
    ['Telegram', '@mariag'],
    ['Password', 'secret-password'],
    ['Confirm password', 'secret-password'],
  ])
    await page.getByLabel(label, { exact: true }).fill(value);
  await page.getByLabel('Player level').selectOption('C7');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByRole('alert')).not.toContainText('recaptchaToken');
  await page
    .getByRole('alert')
    .getByRole('link', { name: /Security check/ })
    .click();
  await expect(page.locator('#account-recaptchaToken')).toBeFocused();
  await page.getByRole('combobox', { name: /Language|Idioma|Язык/ }).selectOption('ru');
  await expect(page.getByRole('alert')).toContainText('Пройдите проверку безопасности.');
  await page.getByRole('combobox', { name: /Language|Idioma|Язык/ }).selectOption('en');
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      json: {
        ...anonymous,
        authenticated: true,
        userType: 'PLAYER',
        id: 42,
        fullName: 'Maria Garcia',
      },
    }),
  );
  await page.route('**/api/players/me', (route) =>
    route.fulfill({
      json: {
        id: 42,
        nombre: 'Maria',
        apellido: 'Garcia',
        email: 'maria@example.test',
        telefono: null,
        telegramUsername: '@mariag',
        nivelJugador: 'C7',
        preferredLocale: 'en',
        guestAccount: false,
        oauth2User: false,
      },
    }),
  );
  await page.route('**/api/players/me/password', (route) =>
    route.fulfill({
      status: 422,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/validation-error',
        status: 422,
        title: 'Validation',
        detail: 'Correct fields',
        instance: '/api/players/me/password',
        code: 'VALIDATION_ERROR',
        fieldErrors: [
          {
            field: 'currentPassword',
            code: 'CURRENT_PASSWORD_INVALID',
            message: 'The current password is incorrect.',
          },
        ],
      }),
    }),
  );
  await page.goto('/preview/perfil');
  await page.getByLabel('Current password', { exact: true }).fill('wrong-password');
  await page.getByLabel('New password', { exact: true }).fill('another-secret');
  await page.getByLabel('Confirm password', { exact: true }).fill('another-secret');
  await page.getByRole('button', { name: 'Change password', exact: true }).click();
  await page
    .getByRole('alert')
    .getByRole('link', { name: /Current password/ })
    .click();
  await expect(page.getByLabel('Current password', { exact: true })).toBeFocused();
});
