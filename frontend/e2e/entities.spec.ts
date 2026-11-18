import { expect, test } from '@playwright/test';

const club = {
  id: 7,
  name: 'Club del barrio',
  address: 'Calle 42',
  city: 'Buenos Aires',
  description: 'Public club description',
  logoUrl: null,
  mapUrl: 'https://maps.example.test/club7',
  websiteUrl: 'https://club.example.test',
  canonicalUrl: 'https://public.example.test/clubs/7',
};
const event = {
  id: 42,
  name: 'Copa del barrio',
  club: { id: 7, name: club.name, address: club.address },
  type: 'AMERICANO_TEAMS',
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
  occupied: 8,
  available: 0,
  waitlistCount: 2,
  websiteUrl: 'https://public.example.test/torneo/42',
  description: 'Public conditions',
  cancellationDeadline: '2026-12-13T18:00:00-03:00',
  participants: {
    confirmed: [{ id: 'pair-1', names: ['Alice', 'Bea'], seekingPartner: false }],
    waitlist: [{ id: 'pair-2', names: ['Clara', 'Dina'], seekingPartner: true }],
  },
};
const details = {
  event,
  organizer: { contact: '+54 911 1234-5678', whatsappUrl: 'https://wa.me/5491112345678' },
  format: 'TEAM_PLAYOFF',
  phase: 'QUALIFICATION',
  actions: [
    { type: 'LIVE', url: '/tournaments/team-playoff/42' },
    { type: 'CALENDAR', url: '/api/public/tournaments/42/calendar/event.ics?download=true' },
  ],
  canonicalUrl: 'https://public.example.test/torneo/42',
  calendarUrl: '/api/public/tournaments/42/calendar/event.ics?download=true',
  pngUrl: null,
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('one-padel-language', 'en'));
  await page.route('**/api/public/events/42', (route) => route.fulfill({ json: details }));
  await page.route('**/api/public/clubs', (route) => route.fulfill({ json: [club] }));
  await page.route('**/api/public/clubs/7', (route) => route.fulfill({ json: club }));
  await page.route('**/api/public/catalog?**', (route) =>
    route.fulfill({ json: { items: [event], page: 0, size: 12, totalElements: 1, totalPages: 1 } }),
  );
});

test('event has server state, public participants and canonical share QR', async ({
  page,
  context,
}) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.goto('/preview/torneo/42');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(event.name);
  await expect(page.getByText('Alice / Bea', { exact: true })).toBeVisible();
  await expect(page.getByText('Clara / Dina', { exact: true })).toBeVisible();
  await expect(page.getByText('0 pairs', { exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Matches and results' })).toHaveAttribute(
    'href',
    /\/tournaments\/team-playoff\/42$/,
  );
  const share = page.getByRole('button', { name: 'Share', exact: true });
  await share.click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('textbox')).toHaveValue(details.canonicalUrl);
  await expect(dialog.getByRole('img', { name: 'QR code for the public link' })).toHaveAttribute(
    'src',
    /^data:image\/png;base64,/,
  );
  await expect(dialog.getByRole('link', { name: 'Telegram' })).not.toHaveAttribute(
    'href',
    /preview/,
  );
  await dialog.getByRole('button', { name: 'Copy link' }).click();
  await expect(dialog.getByRole('status')).toHaveText('Link copied');
  await page.keyboard.press('Escape');
  await expect(dialog).not.toBeVisible();
  await expect(share).toBeFocused();
  for (const language of ['ru', 'es', 'en']) {
    await page.locator('.language-picker select').selectOption(language);
    for (const width of [320, 390, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
    }
  }
});

test('formats and terminal phases use server fields', async ({ page }) => {
  for (const [format, phase, status, label] of [
    ['TEAM_PAIRS', 'NOT_STARTED', 'REGISTRO_ABIERTO', 'Pairs event'],
    ['TEAM_AMERICANO', 'LIVE', 'CERRADO', 'Team Americano'],
    ['TEAM_PLAYOFF', 'PLAYOFF', 'CERRADO', 'qualification and playoff'],
    ['KING_OF_COURT', 'FINISHED', 'FINALIZADO', 'King of Court'],
    ['AMERICANO', 'CANCELLED', 'CANCELADO', 'Individual Americano'],
  ]) {
    await page.route('**/api/public/events/42', (route) =>
      route.fulfill({
        json: {
          ...details,
          format,
          phase,
          actions: [],
          event: { ...event, status, registrationOpen: false },
        },
      }),
    );
    await page.goto('/preview/torneo/42');
    await expect(page.locator('.entity-heading .kicker')).toContainText(label);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(event.name);
    await expect(page.getByRole('link', { name: 'Matches and results' })).toHaveCount(0);
  }
});

test('unknown id and unavailable data remain different states', async ({ page }) => {
  await page.route('**/api/public/events/999', (route) =>
    route.fulfill({ status: 404, json: { code: 'EVENT_NOT_FOUND' } }),
  );
  await page.goto('/preview/torneo/999');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
  await page.route('**/api/public/events/42', (route) =>
    route.fulfill({ status: 503, json: { code: 'PUBLIC_DATA_UNAVAILABLE' } }),
  );
  await page.goto('/preview/torneo/42');
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Page not found' })).toHaveCount(0);
  await page.goto('/preview/torneo/invalid');
  await expect(page.getByRole('heading', { name: 'Page not found' })).toBeVisible();
});

test('club navigation and club catalogue cannot be changed by a foreign club query', async ({
  page,
}) => {
  await page.goto('/preview/clubs');
  await page.getByRole('link', { name: club.name, exact: true }).click();
  await expect(page).toHaveURL(/\/preview\/clubs\/7$/);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(club.name);
  let selected = '';
  await page.route('**/api/public/catalog?**', (route) => {
    selected = new URL(route.request().url()).searchParams.get('clubId') ?? '';
    return route.fulfill({
      json: { items: [event], page: 0, size: 12, totalElements: 1, totalPages: 1 },
    });
  });
  await page.goto('/preview/clubs/7?clubId=99');
  await expect(page.getByRole('heading', { name: event.name })).toBeVisible();
  expect(selected).toBe('7');
  await expect(page.getByRole('link', { name: 'View event: Copa del barrio' })).toHaveAttribute(
    'href',
    '/preview/torneo/42',
  );
  await page.getByRole('link', { name: 'View event: Copa del barrio' }).click();
  await expect(page).toHaveURL(/\/preview\/torneo\/42$/);
  await page.goBack();
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(club.name);
});

test('registration checks the server after losing the response to a single submission', async ({
  page,
}) => {
  let saved = false;
  let submissions = 0;
  await page.route('**/api/public/events/42', (route) =>
    route.fulfill({
      json: {
        ...details,
        event: { ...event, modality: 'INDIVIDUAL' },
        phase: 'NOT_STARTED',
        actions: [{ type: 'REGISTRATION_INFO', url: '/torneo/42' }],
      },
    }),
  );
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      json: { authenticated: true, userType: 'PLAYER', id: 1, hasValidContact: true, roles: [] },
    }),
  );
  await page.route('**/players/tournaments/42/my-registration', (route) =>
    route.fulfill({
      json: saved
        ? { registered: true, registrationId: 1, status: 'WAITLIST', waitlistPosition: 2 }
        : { registered: false },
    }),
  );
  await page.route('**/players/tournaments/42/register', async (route) => {
    submissions++;
    saved = true;
    await route.abort('failed');
  });
  await page.goto('/preview/torneo/42');
  const booking = page.locator('#tournament-registration');
  await booking.getByRole('button', { name: 'Register', exact: true }).click();
  await expect(booking).toContainText('Waiting list position: 2');
  await expect(booking.getByRole('button', { name: 'Register', exact: true })).toHaveCount(0);
  expect(submissions).toBe(1);
});
