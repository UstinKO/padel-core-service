import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Client } from '@stomp/stompjs';
import { LiveConnection } from './realtime';

function setup() {
  const unsubscribe = vi.fn(),
    subscribe = vi.fn(() => ({ unsubscribe }));
  const fake = {
    connected: false,
    onConnect: () => {},
    onWebSocketClose: () => {},
    activate: vi.fn(),
    deactivate: vi.fn(async () => {}),
    subscribe,
  };
  const factory = vi.fn(() => fake as unknown as Client);
  const connection = new LiveConnection(factory);
  const connect = () => {
    fake.connected = true;
    fake.onConnect();
  };
  return { fake, factory, connection, connect, unsubscribe, subscribe };
}
afterEach(() => vi.useRealTimers());
describe('Общий live-клиент', () => {
  it('StrictMode и два слушателя используют один клиент; уход снимает подписку', async () => {
    vi.useFakeTimers();
    const s = setup();
    const changed = vi.fn(),
      connected = vi.fn();
    const first = s.connection.subscribe('/topic/public/live/42', changed, connected);
    first();
    const second = s.connection.subscribe('/topic/public/live/42', changed, connected);
    const third = s.connection.subscribe('/topic/public/live/42', changed, connected);
    s.connect();
    expect(s.factory).toHaveBeenCalledTimes(1);
    expect(s.subscribe).toHaveBeenCalledTimes(1);
    expect(connected).toHaveBeenCalledTimes(2);
    second();
    expect(s.unsubscribe).not.toHaveBeenCalled();
    third();
    expect(s.unsubscribe).toHaveBeenCalledTimes(1);
    await vi.runAllTimersAsync();
    expect(s.fake.deactivate).toHaveBeenCalledTimes(1);
  });
  it('после reconnect восстанавливает точную подписку и требует чтения', () => {
    const s = setup();
    const connected = vi.fn();
    const cleanup = s.connection.subscribe('/topic/public/live/42', vi.fn(), connected);
    s.connect();
    s.fake.onWebSocketClose();
    expect(s.connection.state()).toBe('reconnecting');
    s.connect();
    expect(s.subscribe).toHaveBeenCalledTimes(2);
    expect(connected).toHaveBeenCalledTimes(2);
    cleanup();
  });
  it('новый клиент ждёт окончания асинхронного отключения старого', async () => {
    vi.useFakeTimers();
    const s = setup();
    let release = () => {};
    s.fake.deactivate.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          release = resolve;
        }),
    );
    s.connection.subscribe('/topic/public/live/42', vi.fn(), vi.fn())();
    await vi.advanceTimersByTimeAsync(0);
    const cleanup = s.connection.subscribe('/topic/public/live/43', vi.fn(), vi.fn());
    expect(s.factory).toHaveBeenCalledTimes(1);
    release();
    await Promise.resolve();
    await Promise.resolve();
    expect(s.factory).toHaveBeenCalledTimes(2);
    cleanup();
    await vi.runAllTimersAsync();
  });
});
