import { afterEach, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { delayUntil, retryDeadline } from './useLive';
afterEach(() => vi.useRealTimers());
it('429 соблюдает серверную паузу, остальные ошибки не придумывают её', () => {
  expect(
    retryDeadline(
      new ApiError(
        429,
        {
          type: 'x',
          title: 'x',
          detail: 'x',
          instance: 'x',
          code: 'RATE_LIMITED',
          status: 429,
          retryAfter: 60,
        },
        1000,
      ),
    ),
  ).toBe(61000);
  expect(retryDeadline(new ApiError(503, null))).toBe(0);
});
it('ожидание refetch отменяется при уходе со страницы', async () => {
  vi.useFakeTimers();
  const controller = new AbortController();
  const waiting = delayUntil(Date.now() + 60000, controller.signal);
  const rejected = expect(waiting).rejects.toMatchObject({ name: 'AbortError' });
  controller.abort();
  await rejected;
  expect(vi.getTimerCount()).toBe(0);
});
