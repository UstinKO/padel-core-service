import { expect, test } from '@playwright/test';

const anonymous = { authenticated: false, userType: null, id: null, roles: [], fullName: null };
const player = {
  authenticated: true,
  userType: 'PLAYER',
  id: 31,
  roles: ['ROLE_PLAYER'],
  fullName: 'Maria Garcia',
  email: 'maria@example.test',
};
const profile = {
  id: 31,
  nombre: 'Maria',
  apellido: 'Garcia',
  email: 'maria@example.test',
  telefono: null,
  telegramUsername: '@mariag',
  nivelJugador: 'C7',
  preferredLocale: 'es',
  guestAccount: false,
  oauth2User: false,
};
test.beforeEach(async ({ page, context }) => {
  await context.addCookies([
    { name: 'lang', value: 'en', url: 'http://127.0.0.1:4173' },
    { name: 'XSRF-TOKEN', value: 'test-csrf', url: 'http://127.0.0.1:4173' },
  ]);
  await page.route('**/api/auth/session', (route) => route.fulfill({ json: anonymous }));
  await page.route('**/api/public/config', (route) =>
    route.fulfill({
      json: {
        captchaEnabled: false,
        captchaSiteKey: '',
        googleEnabled: false,
        supportedLocales: ['es', 'ru', 'en'],
        locale: 'en',
      },
    }),
  );
});
test('registration keeps safe fields and language, exposes typed field errors and CSRF', async ({
  page,
}) => {
  let posts = 0;
  await page.route('**/api/players/registro', async (route) => {
    posts++;
    expect(route.request().headers()['x-xsrf-token']).toBe('test-csrf');
    await route.fulfill({
      status: 422,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/validation-error',
        title: 'Correct fields',
        status: 422,
        detail: 'Correct fields',
        instance: '/api/players/registro',
        code: 'VALIDATION_ERROR',
        fieldErrors: [
          { field: 'email', code: 'EMAIL_DUPLICATE', message: 'This email is already in use.' },
        ],
      }),
    });
  });
  await page.goto('/preview/players/registro');
  await page.getByLabel('First name', { exact: true }).fill('Maria');
  await page.getByLabel('Last name', { exact: true }).fill('Garcia');
  await page.getByLabel('Email', { exact: true }).fill('maria@example.test');
  await page.getByLabel('Telegram', { exact: true }).fill('@mariag');
  await page.getByLabel('Player level').selectOption('C7');
  await page.getByLabel('Password', { exact: true }).fill('secret-password');
  await page.getByLabel('Confirm password', { exact: true }).fill('secret-password');
  await page.getByRole('button', { name: 'Create account', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Correct the highlighted fields.');
  await expect(page.getByLabel('First name', { exact: true })).toHaveValue('Maria');
  await expect(page.getByLabel('Password', { exact: true })).toHaveValue('');
  await page.getByRole('combobox', { name: /Idioma|Language|Язык/ }).selectOption('ru');
  await expect(page.getByLabel('Имя', { exact: true })).toHaveValue('Maria');
  await expect(page.getByRole('alert')).toContainText('Эта почта уже используется.');
  expect((await page.context().cookies()).find((cookie) => cookie.name === 'lang')?.value).toBe(
    'ru',
  );
  expect(posts).toBe(1);
  await expect(page.getByRole('link', { name: /Google/ })).toHaveCount(0);
});
test('login handles 401 and 429 without HTML navigation or losing email', async ({ page }) => {
  let posts = 0;
  await page.route('**/login', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.continue();
      return;
    }
    posts++;
    await route.fulfill({
      status: posts === 1 ? 401 : 429,
      headers: { 'Retry-After': '37' },
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: '/problems/login',
        title: 'Failed',
        status: posts === 1 ? 401 : 429,
        detail: 'Incorrect email or password.',
        instance: '/login',
        code: posts === 1 ? 'LOGIN_INVALID' : 'RATE_LIMITED',
        retryAfter: 37,
      }),
    });
  });
  await page.goto('/preview/login?destination=%2Fpreview%2Fperfil');
  await page.getByLabel('Email', { exact: true }).fill('maria@example.test');
  await page.getByLabel('Password', { exact: true }).fill('secret-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('Incorrect email');
  await expect(page.getByLabel('Email', { exact: true })).toHaveValue('maria@example.test');
  await page.getByLabel('Password', { exact: true }).fill('secret-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('37');
  await expect(page).toHaveURL(/preview\/login/);
});
test('profile shows actual player and persists safe edits through a language change', async ({
  page,
}) => {
  await page.route('**/api/auth/session', (route) => route.fulfill({ json: player }));
  await page.route('**/api/players/me', (route) => route.fulfill({ json: profile }));
  await page.goto('/preview/perfil');
  await expect(page.getByLabel('First name', { exact: true })).toHaveValue('Maria');
  await page.getByLabel('First name', { exact: true }).fill('Mariana');
  await page.getByRole('combobox', { name: /Idioma|Language|Язык/ }).selectOption('ru');
  await expect(page.getByLabel('Имя', { exact: true })).toHaveValue('Mariana');
  await expect(page.getByLabel('Электронная почта', { exact: true })).toHaveAttribute(
    'readonly',
    '',
  );
});
test('expired reset link offers a new request and never confirms automatically', async ({
  page,
}) => {
  await page.route('**/api/password-reset/validate?*', (route) =>
    route.fulfill({ json: { valid: false, code: 'RESET_TOKEN_INVALID' } }),
  );
  await page.goto('/preview/recuperar-password?token=expired');
  await expect(page.getByRole('alert')).toContainText('invalid or expired');
  await expect(page.getByRole('link', { name: 'Recover password', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Reset password', exact: true })).toHaveCount(0);
});

test('an explicit URL language updates the shared server cookie on first load', async ({
  page,
}) => {
  await page.goto('/preview/login?lang=ru');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Войти');
  expect((await page.context().cookies()).find((cookie) => cookie.name === 'lang')?.value).toBe(
    'ru',
  );
});
