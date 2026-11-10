import { queryOptions } from '@tanstack/react-query';

import { apiFetch } from './client';
import type { SpaSession } from './types';

export const sessionQuery = queryOptions({
  queryKey: ['session'],
  queryFn: () => apiFetch<SpaSession>('/api/auth/session'),
  staleTime: 60_000,
  gcTime: 5 * 60_000,
  retry: 1,
});
