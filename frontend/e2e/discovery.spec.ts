import { expect, test } from '@playwright/test';

const event = {
  id: 42,
  name: 'Copa del barrio',
  club: { id: 7, name: 'Club del barrio', address: null },
  type: 'AMERICANO',
  modality: 'DOBLES',
  gender: 'MIXTO',
  level: 'C7',
  startsAt: '2026-12-14T18:00:00-03:00',
  timeZone: 'America/Argentina/Buenos_Aires',
  duration: '2h',
  price: 12000,
  currency: 'ARS',
  status: 'REGISTRO_ABIERTO',
  registrationOpen: true,
  capacity: 8,
  capacityUnit: 'PAIRS',
  occupied: 7,
  available: 1,
  waitlistCount: 2,
  websiteUrl: 'https://example.test/torneo/42',
};
const pageData = { items: [event], page: 0, size: 12, totalElements: 13, totalPages: 2 };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('one-padel-language', 'en'));
  await page.route('**/api/public/clubs', (route) =>
    route.fulfill({
      json: [{ id: 7, name: 'Club del barrio', city: 'Buenos Aires', address: null }],
    }),
  );
  await page.route('**/api/public/home', (route) =>
    route.fulfill({
      json: { totalClubs: 1, totalPlayers: 12, totalTournaments: 13, upcomingTournaments: [event] },
    }),
  );
  await page.route('**/api/public/catalog?**', (route) =>
    route.fulfill({
      json: { ...pageData, page: Number(new URL(route.request().url()).searchParams.get('page')) },
    }),
  );
});

test('home and catalogue use server counts, canonical event links and all languages', async ({
  page,
}) => {
  await page.goto('/preview/');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Your next game');
  await expect(page.getByRole('heading', { name: event.name })).toBeVisible();
  await expect(page.getByRole('link', { name: 'View event: Copa del barrio' })).toHaveAttribute(
    'href',
    '/torneo/42',
  );
  await expect(page.getByText('1 pair', { exact: true })).toBeVisible();
  await page.getByLabel('Language').selectOption('ru');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Ваша следующая игра');
  await page.getByLabel('Язык').selectOption('es');
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Tu próximo partido');
  for (const width of [320, 390, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
  }
});

test('filters, pagination, back and reload preserve URL state', async ({ page }) => {
  await page.goto('/preview/torneos?type=AMERICANO&level=C7&from=2026-12-01');
  await expect(page.getByRole('combobox', { name: 'Format', exact: true })).toHaveValue(
    'AMERICANO',
  );
  await page.getByRole('combobox', { name: 'Club', exact: true }).selectOption('7');
  await expect(page).toHaveURL(/clubId=7/);
  await expect(page.getByRole('heading', { name: event.name })).toBeVisible();
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await expect(page).toHaveURL(/page=1/);
  await page.reload();
  await expect(page.getByRole('combobox', { name: 'Level', exact: true })).toHaveValue('C7');
  await expect(page.getByText('Page 2 of 2')).toBeVisible();
  await page.goBack();
  await expect(page).not.toHaveURL(/page=1/);
  await expect(page.getByRole('heading', { name: event.name })).toBeVisible();
});

test('loading, error, retry and empty result retain filters', async ({ page }) => {
  let phase: 'loading' | 'error' | 'empty' = 'loading';
  let release: (() => void) | undefined;
  const ready = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route('**/api/public/catalog?**', async (route) => {
    if (phase === 'loading') await ready;
    if (phase === 'error')
      await route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        json: {
          type: '/problems/unavailable',
          title: 'Unavailable',
          status: 503,
          detail: 'Safe error',
          code: 'PUBLIC_DATA_UNAVAILABLE',
          instance: '/api/public/catalog',
        },
      });
    else await route.fulfill({ json: { ...pageData, items: [], totalElements: 0, totalPages: 0 } });
  });
  await page.goto('/preview/torneos?level=C7');
  await expect(page.getByRole('heading', { name: 'Loading events' })).toBeVisible();
  phase = 'error';
  release?.();
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Level', exact: true })).toHaveValue('C7');
  phase = 'empty';
  await page.getByRole('button', { name: 'Try again' }).click();
  await expect(page.getByRole('heading', { name: 'No matching events' })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Level', exact: true })).toHaveValue('C7');
});
