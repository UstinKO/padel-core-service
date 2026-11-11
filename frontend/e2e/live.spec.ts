import { expect, test, type Page, type WebSocketRoute } from '@playwright/test';
import { king, americano, playoff } from '../src/test/liveFixtures';
import type { LiveSnapshot } from '../src/api/live';
async function setup(page: Page) {
  await page.route('**/api/auth/session', (r) =>
    r.fulfill({ json: { authenticated: false, userType: null, id: null, roles: [] } }),
  );
  await page.route('**/ws/info?*', (r) =>
    r.fulfill({ json: { websocket: true, cookie_needed: false, origins: ['*:*'], entropy: 1 } }),
  );
  let socket: WebSocketRoute | undefined,
    subscription = '',
    connections = 0,
    subscribes = 0,
    unsubscribes = 0;
  await page.routeWebSocket('**/ws/**/websocket', (ws) => {
    socket = ws;
    connections++;
    ws.send('o');
    ws.onMessage((value) => {
      for (const frame of JSON.parse(String(value)) as string[]) {
        if (frame.startsWith('CONNECT') || frame.startsWith('STOMP'))
          ws.send('a' + JSON.stringify(['CONNECTED\nversion:1.2\nheart-beat:0,0\n\n\0']));
        if (frame.startsWith('SUBSCRIBE')) {
          subscription = /\nid:([^\n]+)/.exec(frame)?.[1] ?? '';
          subscribes++;
        }
        if (frame.startsWith('UNSUBSCRIBE')) unsubscribes++;
      }
    });
  });
  return {
    connections: () => connections,
    subscribes: () => subscribes,
    unsubscribes: () => unsubscribes,
    close: () => socket?.close(),
    event: () =>
      socket?.send(
        'a' +
          JSON.stringify([
            `MESSAGE\nsubscription:${subscription}\nmessage-id:live\ndestination:/topic/public/live/42\ncontent-type:application/json\n\n{"eventId":42,"type":"CHANGED"}\0`,
          ]),
      ),
  };
}
for (const [format, path, snapshot] of [
  ['KING_OF_COURT', 'torneo/42/king-of-court', king],
  ['AMERICANO', 'tournaments/americano/42', americano()],
  ['TEAM_PLAYOFF', 'tournaments/team-playoff/42', playoff()],
] as const)
  test(`${format}: раунды, рейтинг, история, участник, share и PNG`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', (e) => errors.push(e.message));
    await setup(page);
    await page.route('**/api/public/events/42/live', (r) => r.fulfill({ json: snapshot }));
    await page.goto(`/preview/${path}?lang=en`);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(snapshot.metadata.name!);
    await page.getByRole('link', { name: 'Ranking', exact: true }).click();
    await expect(page.locator('table')).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Court bonuses' })).toHaveCount(
      format === 'KING_OF_COURT' ? 1 : 0,
    );
    const person = page.locator('table .live-person-button').first();
    await person.click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await expect(person).toBeFocused();
    await page.getByRole('link', { name: 'History', exact: true }).click();
    await expect(page.locator('.live-match')).toHaveCount(1);
    await page.reload();
    await expect(page.locator('.live-match')).toHaveCount(1);
    await page.getByRole('button', { name: 'Share', exact: true }).click();
    await expect(page.getByRole('textbox')).toHaveValue(snapshot.metadata.canonicalUrl!);
    await expect(page.getByRole('img', { name: /QR/ })).toBeVisible();
    await page.keyboard.press('Escape');
    await page.getByRole('link', { name: 'Schedule', exact: true }).click();
    if (format === 'TEAM_PLAYOFF') {
      const download = page.waitForEvent('download');
      await page.getByRole('button', { name: 'Download bracket PNG' }).click();
      expect((await download).suggestedFilename()).toBe('1padel-playoff.png');
      await expect(page.getByRole('link', { name: '→ Next match' })).toBeVisible();
    } else await expect(page.getByRole('heading', { level: 2, name: /Round 19/ })).toBeVisible();
    expect(errors).toEqual([]);
  });
test('WS: один клиент, coalescing, stale, reconnect и уход', async ({ page }) => {
  const ws = await setup(page);
  let reads = 0,
    failed = false;
  let value: LiveSnapshot = structuredClone(king);
  await page.route('**/api/public/events/42/live', (r) => {
    reads++;
    return failed
      ? r.fulfill({
          status: 503,
          contentType: 'application/problem+json',
          json: {
            type: 'x',
            title: 'Unavailable',
            detail: 'Unavailable',
            status: 503,
            instance: 'x',
            code: 'LIVE_DATA_UNAVAILABLE',
          },
        })
      : r.fulfill({ json: value });
  });
  await page.goto('/preview/torneo/42/king-of-court?lang=en&tab=ranking');
  await expect.poll(ws.subscribes).toBe(1);
  await expect(page.getByText('Results updated', { exact: true })).toBeVisible();
  expect(ws.connections()).toBe(1);
  const before = reads;
  failed = true;
  for (let i = 0; i < 30; i++) ws.event();
  await expect(page.getByText('Results may be out of date', { exact: true })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(4);
  expect(reads).toBeLessThanOrEqual(before + 1);
  failed = false;
  value = structuredClone(king);
  value.ranking[0].totalPoints = 123;
  ws.close();
  await expect(page.getByText('Reconnecting', { exact: true })).toBeVisible();
  await expect.poll(ws.connections, { timeout: 15000 }).toBe(2);
  await expect(page.locator('tbody tr').first()).toContainText('123');
  await expect(page.getByText('Results updated', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'About the event' }).click();
  await expect.poll(ws.unsubscribes).toBe(1);
});
test('429 соблюдает паузу, 404 очищает прежний снимок', async ({ page }) => {
  const ws = await setup(page);
  let status = 200,
    reads = 0;
  await page.route('**/api/public/events/42/live', (r) => {
    reads++;
    return r.fulfill(
      status === 200
        ? { json: king }
        : {
            status,
            headers: { 'Retry-After': '4' },
            contentType: 'application/problem+json',
            json: {
              type: 'x',
              title: 'Unavailable',
              detail: 'Unavailable',
              status,
              instance: 'x',
              code: status === 404 ? 'LIVE_EVENT_NOT_FOUND' : 'RATE_LIMITED',
            },
          },
    );
  });
  await page.goto('/preview/torneo/42/king-of-court?lang=en');
  await expect.poll(ws.subscribes).toBe(1);
  await expect(page.getByText('Results updated', { exact: true })).toBeVisible();
  status = 429;
  ws.event();
  await expect(page.getByText(/Too many requests. Updates resume/)).toBeVisible();
  const before = reads;
  await page.getByRole('button', { name: 'Refresh', exact: true }).click();
  await page.waitForTimeout(1000);
  expect(reads).toBe(before);
  status = 404;
  await expect(page.getByRole('heading', { name: 'Not found' })).toBeVisible({ timeout: 10000 });
  await expect(page.getByText(king.metadata.name!, { exact: true })).toHaveCount(0);
  await expect(page.locator('.live-match')).toHaveCount(0);
});
test('409: устаревший instance и round-robin сохраняют безопасную ссылку', async ({ page }) => {
  await setup(page);
  for (const code of ['LIVE_INSTANCE_CHANGED', 'LIVE_FORMAT_UNSUPPORTED']) {
    await page.route('**/api/public/king-of-court/77/live', (r) =>
      r.fulfill({
        status: 409,
        contentType: 'application/problem+json',
        json: {
          type: 'x',
          title: 'Conflict',
          detail: 'Conflict',
          status: 409,
          instance: 'x',
          code,
          canonicalUrl:
            code === 'LIVE_INSTANCE_CHANGED'
              ? 'https://public.example.test/torneo/42/king-of-court'
              : 'https://public.example.test/tournaments/team-americano/42',
        },
      }),
    );
    await page.goto('/preview/tournaments/king-of-court/77?lang=en');
    await expect(
      page.getByRole('link', {
        name: code === 'LIVE_INSTANCE_CHANGED' ? 'Open tournament' : 'Open previous view',
      }),
    ).toHaveAttribute(
      'href',
      code === 'LIVE_INSTANCE_CHANGED'
        ? '/preview/torneo/42/king-of-court'
        : 'https://public.example.test/tournaments/team-americano/42',
    );
    await page.unroute('**/api/public/king-of-court/77/live');
  }
});
test('320px, landscape, табло, 19 раундов и устаревшие фильтры', async ({ page }) => {
  await setup(page);
  await page.route('**/api/public/events/42/live', (r) => r.fulfill({ json: king }));
  await page.setViewportSize({ width: 320, height: 760 });
  await page.goto('/preview/torneo/42/king-of-court?lang=ru&round=missing&player=999&tab=courts');
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(king.metadata.name!);
  await expect(page).not.toHaveURL(/round=|player=/);
  await expect(page.locator('.live-round-picker select option')).toHaveCount(19);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('live-320.png'), fullPage: true });
  await page.setViewportSize({ width: 844, height: 390 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.getByRole('button', { name: 'Табло', exact: true }).click();
  await expect(page.locator('.live-projector')).toBeVisible();
  await page.screenshot({ path: test.info().outputPath('live-projector.png'), fullPage: true });
});

test('большая сетка и серверные terminal/not-started фазы', async ({ page }) => {
  await setup(page);
  const snapshot = structuredClone(playoff());
  const source = snapshot.rounds[1].matches[0];
  snapshot.rounds[1].matches = Array.from({ length: 8 }, (_, index) => ({
    ...structuredClone(source),
    id: 300 + index,
    nextMatchId: index === 0 ? 103 : null,
  }));
  await page.route('**/api/public/events/42/live', (r) => r.fulfill({ json: snapshot }));
  await page.goto('/preview/tournaments/team-playoff/42?lang=es&tab=schedule');
  await expect(page.locator('.live-bracket .live-match')).toHaveCount(10);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('live-bracket-390.png'), fullPage: true });
  await page.unroute('**/api/public/events/42/live');
  for (const phase of ['NOT_STARTED', 'FINISHED', 'CANCELLED'] as const) {
    const value = {
      ...structuredClone(king),
      metadata: { ...king.metadata, phase },
      ...(phase === 'NOT_STARTED' ? { rounds: [], ranking: [] } : {}),
    };
    await page.route('**/api/public/events/42/live', (r) => r.fulfill({ json: value }));
    await page.goto('/preview/torneo/42/king-of-court?lang=en');
    if (phase === 'NOT_STARTED')
      await expect(page.getByRole('heading', { name: 'The draw is not ready yet' })).toBeVisible();
    else
      await expect(page.locator('.event-tags')).toHaveText(
        phase === 'FINISHED' ? 'Finished' : 'Cancelled',
      );
    await page.unroute('**/api/public/events/42/live');
  }
});
