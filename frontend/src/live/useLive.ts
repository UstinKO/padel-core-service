import { useCallback, useEffect, useRef, useSyncExternalStore } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { getLive, type LiveSnapshot } from '../api/live';
import { validEntityId } from '../api/eventDetails';
import { liveConnection } from './realtime';

export function retryDeadline(error: unknown): number {
  return error instanceof ApiError && error.status === 429
    ? error.receivedAt + Math.max(1, error.problem?.retryAfter ?? 20) * 1000
    : 0;
}
export function delayUntil(deadline: number, signal: AbortSignal): Promise<void> {
  if (signal.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'));
  return new Promise((resolve, reject) => {
    const abort = () => {
      clearTimeout(timer);
      reject(new DOMException('Aborted', 'AbortError'));
    };
    const timer = setTimeout(
      () => {
        signal.removeEventListener('abort', abort);
        resolve();
      },
      Math.max(0, deadline - Date.now()),
    );
    signal.addEventListener('abort', abort, { once: true });
  });
}
export function useLive(id: string, alias: boolean) {
  const { i18n } = useTranslation();
  const client = useQueryClient();
  const gate = useRef({ key: '', next: 0 });
  const key = ['public-live', alias ? 'king' : 'event', id, i18n.language];
  const identity = key.join(':');
  const query = useQuery<LiveSnapshot | null>({
    queryKey: key,
    enabled: validEntityId(id),
    retry: false,
    queryFn: async ({ signal }) => {
      if (gate.current.key !== identity) gate.current = { key: identity, next: 0 };
      await delayUntil(gate.current.next, signal);
      gate.current.next = Date.now() + 3000;
      try {
        return await getLive(id, alias, signal);
      } catch (error) {
        gate.current.next = Math.max(gate.current.next, retryDeadline(error));
        if (error instanceof ApiError && [403, 404, 409].includes(error.status))
          client.setQueryData(key, null);
        throw error;
      }
    },
    staleTime: 5000,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
    refetchInterval: (query) => Math.max(20000, retryDeadline(query.state.error) - Date.now()),
    refetchIntervalInBackground: false,
  });
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const refresh = useCallback(() => {
    if (document.visibilityState === 'hidden' || timer.current) return;
    timer.current = setTimeout(() => {
      timer.current = undefined;
      const queryKey = ['public-live', alias ? 'king' : 'event', id, i18n.language];
      if (client.getQueryState(queryKey)?.fetchStatus !== 'fetching')
        void client.invalidateQueries({ queryKey });
    }, 500);
  }, [alias, id, i18n.language, client]);
  const topic = query.data?.metadata.topic ?? '';
  useEffect(() => {
    const dispose = topic ? liveConnection.subscribe(topic, refresh, refresh) : () => {};
    const visible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    document.addEventListener('visibilitychange', visible);
    return () => {
      dispose();
      clearTimeout(timer.current);
      timer.current = undefined;
      document.removeEventListener('visibilitychange', visible);
    };
  }, [topic, refresh]);
  const connection = useSyncExternalStore(liveConnection.observe, liveConnection.state);
  return { ...query, connection: topic ? connection : 'idle', refresh };
}
