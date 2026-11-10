import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { apiFetch } from './client';

describe('apiFetch', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf%20token; path=/';
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
  });

  it('adds session credentials, language and CSRF for mutations', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    await apiFetch('/api/example', { method: 'POST' });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers);
    expect(init.credentials).toBe('include');
    expect(headers.get('X-XSRF-TOKEN')).toBe('csrf token');
    expect(headers.get('Accept-Language')).toBeTruthy();
  });

  it('exposes a typed ProblemDetail without parsing arbitrary HTML', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            type: 'about:blank',
            title: 'Forbidden',
            status: 403,
            detail: 'Access denied',
            instance: '/api/example',
            code: 'ACCESS_DENIED',
          }),
          { status: 403, headers: { 'content-type': 'application/problem+json' } },
        ),
      ),
    );

    await expect(apiFetch('/api/example')).rejects.toMatchObject({
      status: 403,
      problem: { code: 'ACCESS_DENIED' },
    });
  });

  it('rejects cross-origin URLs before sending credentials or CSRF', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(apiFetch('https://example.test/api')).rejects.toThrow(
      'API requests must use the current origin',
    );
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
