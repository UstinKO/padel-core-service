import { expect, test } from '@playwright/test';

const session = {
  authenticated: true,
  userType: 'OWNER',
  id: 1,
  fullName: 'Preview Admin',
  email: 'preview@example.test',
  roles: ['ROLE_SUPER_ADMIN'],
  clubId: null,
  hasValidContact: null,
  preferredLocale: 'es',
};

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(session),
    });
  });
});

test('preview shell renders session and navigation', async ({ page }) => {
  await page.goto('/preview/foundation');

  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.locator('#main-content').getByText('Preview Admin')).toBeVisible();
  await expect(page.getByText('ROLE_SUPER_ADMIN')).toBeVisible();
  await page
    .getByRole('link', { name: /Componentes|Компоненты|Components/ })
    .first()
    .click();
  await expect(page.getByRole('textbox')).toBeVisible();
});
