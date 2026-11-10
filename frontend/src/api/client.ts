import i18n from '../i18n';
import type { ApiProblem } from './types';

const mutatingMethods = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ApiProblem | null,
  ) {
    super(problem?.detail ?? `HTTP ${status}`);
    this.name = 'ApiError';
  }
}

function cookie(name: string): string | null {
  const prefix = `${name}=`;
  const value = document.cookie
    .split(';')
    .map((entry) => entry.trim())
    .find((entry) => entry.startsWith(prefix))
    ?.slice(prefix.length);
  if (!value) return null;
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}

function isProblem(value: unknown): value is ApiProblem {
  if (!value || typeof value !== 'object') return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.type === 'string' &&
    typeof candidate.title === 'string' &&
    typeof candidate.status === 'number' &&
    typeof candidate.detail === 'string' &&
    typeof candidate.instance === 'string' &&
    typeof candidate.code === 'string'
  );
}

async function readProblem(response: Response): Promise<ApiProblem | null> {
  if (!response.headers.get('content-type')?.includes('application/problem+json')) return null;
  const body: unknown = await response.json().catch(() => null);
  return isProblem(body) ? body : null;
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = new URL(path, window.location.origin);
  if (url.origin !== window.location.origin) {
    throw new TypeError('API requests must use the current origin');
  }
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json, application/problem+json');
  headers.set('Accept-Language', i18n.language);
  if (mutatingMethods.has(method)) {
    const token = cookie('XSRF-TOKEN');
    if (token) headers.set('X-XSRF-TOKEN', token);
  }

  const response = await fetch(`${url.pathname}${url.search}`, {
    ...init,
    method,
    headers,
    credentials: 'include',
  });

  if (!response.ok) {
    const problem = await readProblem(response);
    if (response.status === 401) window.dispatchEvent(new Event('one-padel:session-expired'));
    throw new ApiError(response.status, problem);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
