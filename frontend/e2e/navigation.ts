import type { Page } from '@playwright/test';

export async function openMenu(page: Page) {
  const toggle = page.locator('.nav-toggle');
  if ((await toggle.isVisible()) && (await toggle.getAttribute('aria-expanded')) === 'false') {
    await toggle.click();
  }
}
