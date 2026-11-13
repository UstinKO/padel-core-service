import { expect, test } from '@playwright/test';
import type { PlayerEvent } from '../src/api/playerEvents';

const entry: PlayerEvent = {
  event: {
    id: 180,
    name: 'Americano C7',
    type: 'AMERICANO_TEAMS',
    status: 'REGISTRO_ABIERTO',
    modality: 'DOBLES',
    gender: 'MIXTO',
    level: 'C7',
    startsAt: '2026-11-19T13:30:00-03:00',
    clubName: 'Test Club',
  },
  registrationId: 32,
  version: 0,
  registrationStatus: 'WAITLIST',
  mode: 'SOLO',
  partnerName: null,
  queuedAt: '2026-11-12T09:45:00',
  queuePosition: 2,
  participationStatus: 'NOT_REQUIRED',
  participation: [],
  replacementNeeded: false,
  replacementSearch: false,
  shareContacts: false,
  paymentStatus: null,
  availableActions: ['CHANGE_CONTACT_VISIBILITY', 'WITHDRAW'],
  cancellations: [],
};

test.beforeEach(async ({ page, context }) => {
  await context.addCookies([
    { name: 'lang', value: 'en', url: 'http://127.0.0.1:4173' },
    { name: 'XSRF-TOKEN', value: 'test-csrf', url: 'http://127.0.0.1:4173' },
  ]);
  await page.route('**/api/auth/session', (route) =>
    route.fulfill({
      json: {
        authenticated: true,
        userType: 'PLAYER',
        id: 31,
        roles: ['ROLE_PLAYER'],
        fullName: 'Test Player',
      },
    }),
  );
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

test('displays empty states and allows retry after a server error', async ({ page }) => {
  let available = false;
  await page.route('**/api/players/me/events', (route) =>
    !available
      ? route.fulfill({ status: 503 })
      : route.fulfill({ json: { upcoming: [], history: [] } }),
  );
  await page.goto('/preview/players/dashboard');
  const retry = page.getByRole('button', { name: 'Try again' });
  await expect(retry).toBeVisible();
  available = true;
  await retry.click();
  await expect(page.getByText('No upcoming events.')).toBeVisible();
  await page.getByRole('button', { name: 'History' }).click();
  await expect(page.getByText('No past events yet.')).toBeVisible();
});

test('requires a fresh login after session expiry', async ({ page }) => {
  await page.route('**/api/players/me/events', (route) => route.fulfill({ status: 401 }));
  await page.goto('/preview/players/dashboard');
  await expect(page).toHaveURL(
    /\/preview\/login\?expired=true&destination=%2Fpreview%2Fplayers%2Fdashboard$/,
  );
  await expect(page.getByText('Sign in again to continue.')).toBeVisible();
});

test('withdraws only after confirmation and reconciles when the response is lost', async ({
  page,
}) => {
  let posts = 0;
  const cancelled: PlayerEvent = {
    ...entry,
    registrationStatus: 'CANCELLED',
    availableActions: [],
    queuePosition: null,
    cancellations: [
      {
        eventId: 'cancellation',
        cancelledAt: null,
        initiator: 'PLAYER',
        reasonCode: 'PARTICIPANT_WITHDRAWAL',
        organizerComment: null,
      },
    ],
  };
  await page.route('**/api/players/me/events', (route) =>
    route.fulfill({
      json: posts ? { upcoming: [], history: [cancelled] } : { upcoming: [entry], history: [] },
    }),
  );
  await page.route('**/api/players/me/events/32/withdraw', async (route) => {
    posts++;
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-xsrf-token']).toBe('test-csrf');
    expect(route.request().postDataJSON()).toEqual({ version: 0 });
    await route.abort();
  });
  await page.goto('/preview/players/dashboard');
  await expect(page.getByText('On the waitlist')).toBeVisible();
  await page.getByRole('button', { name: 'Leave the event' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  expect(posts).toBe(0);
  await dialog.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('No upcoming events.')).toBeVisible();
  await page.getByRole('button', { name: 'History' }).click();
  await expect(page.getByText('Cancelled by a player')).toBeVisible();
  expect(posts).toBe(1);
});

test('uses the participation actions for each player and updates their states', async ({
  page,
}) => {
  const own = {
    requestId: 'own',
    playerName: 'Test Player',
    status: 'PENDING',
    delegated: false,
    availableActions: ['CONFIRM'],
  };
  const guest = {
    requestId: 'guest',
    playerName: 'Test Guest',
    status: 'PENDING',
    delegated: true,
    availableActions: ['DECLINE'],
  };
  await page.route('**/api/players/me/events', (route) =>
    route.fulfill({
      json: {
        upcoming: [
          {
            ...entry,
            partnerName: guest.playerName,
            mode: 'PAIR',
            registrationStatus: 'CONFIRMED',
            availableActions: [],
            paymentStatus: 'PAID',
            participation: [own, guest],
          },
        ],
        history: [],
      },
    }),
  );
  await page.route('**/api/players/me/participation/*', async (route) => {
    const confirm = route.request().url().endsWith('/own');
    expect(route.request().headers()['x-xsrf-token']).toBe('test-csrf');
    expect(route.request().postDataJSON()).toEqual({ decision: confirm ? 'CONFIRM' : 'DECLINE' });
    const item = confirm ? own : guest;
    item.status = confirm ? 'CONFIRMED' : 'DECLINED';
    item.availableActions = [];
    await route.fulfill({ json: item });
  });
  await page.goto('/preview/players/dashboard');
  await expect(page.getByText('Paid', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Confirm participation' }).click();
  await expect(page.getByText('Test Player · Confirmed')).toBeVisible();
  await page.getByRole('button', { name: 'Decline participation' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Test Guest')).toBeVisible();
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByText('Test Guest · Declined')).toBeVisible();
});

test('shows the cancellation quote in USDT before cancelling the pair', async ({ page }) => {
  let cancelled = false;
  const quote = {
    token: 'test-quote',
    currency: 'USDT',
    obligation: 15,
    alreadyPaid: 10,
    remainingDue: 5,
    refundable: 0,
  };
  await page.route('**/api/players/me/events', (route) =>
    route.fulfill({
      json: {
        upcoming: cancelled ? [] : [{ ...entry, mode: 'PAIR', availableActions: ['CANCEL_PAIR'] }],
        history: [],
      },
    }),
  );
  await page.route('**/api/tournaments/double/180/pair-cancellation', async (route) => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ token: 'test-quote' });
      expect(route.request().headers()['x-xsrf-token']).toBe('test-csrf');
      cancelled = true;
    }
    await route.fulfill({ json: quote });
  });
  await page.goto('/preview/players/dashboard');
  await page.getByRole('button', { name: 'Cancel the pair' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('5.00 USDT', { exact: true })).toBeVisible();
  expect(cancelled).toBe(false);
  await dialog.getByRole('button', { name: 'Confirm', exact: true }).click();
  await expect(page.getByText('No upcoming events.')).toBeVisible();
  expect(cancelled).toBe(true);
});
