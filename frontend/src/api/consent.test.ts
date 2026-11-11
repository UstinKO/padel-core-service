import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyConsent, readConsent } from './consent';
import { apiFetch, parseRetryAfter } from './client';

afterEach(() => {
  for (const name of ['cookieConsent', 'analyticsConsent', 'marketingConsent'])
    document.cookie = `${name}=; Path=/; Max-Age=0`;
  vi.unstubAllGlobals();
});
describe('consent contract', () => {
  it('does not grant analytics or marketing without a saved valid choice', () => {
    document.cookie = 'cookieConsent=corrupt; Path=/';
    expect(readConsent()).toEqual({ choice: 'unknown', analytics: false, marketing: false });
    const gtag = vi.fn();
    const initClarity = vi.fn();
    const clarity = vi.fn();
    Object.assign(window, { gtag, initClarity, clarity });
    applyConsent();
    expect(initClarity).not.toHaveBeenCalled();
    expect(gtag).toHaveBeenCalledWith('consent', 'update', { analytics_storage: 'denied' });
    expect(clarity).toHaveBeenCalledWith('consent', false);
  });
  it('uses per-purpose cookies only for customized consent and allows revoke', () => {
    document.cookie = 'cookieConsent=customized; Path=/';
    document.cookie = 'analyticsConsent=true; Path=/';
    document.cookie = 'marketingConsent=false; Path=/';
    expect(readConsent()).toEqual({ choice: 'customized', analytics: true, marketing: false });
    document.cookie = 'cookieConsent=rejected; Path=/';
    expect(readConsent().analytics).toBe(false);
  });
  it('accepts the existing empty 200 cookie endpoint response', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(new Response(null, { status: 200, headers: { 'Content-Length': '0' } })),
    );
    await expect(apiFetch('/api/cookies/reject', { method: 'POST' })).resolves.toBeUndefined();
  });
  it('preserves Retry-After even if a gateway returns an empty 429', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(new Response(null, { status: 429, headers: { 'Retry-After': '37' } })),
    );
    await expect(apiFetch('/api/example')).rejects.toMatchObject({
      status: 429,
      problem: { retryAfter: 37 },
    });
  });
  it('reads Retry-After seconds and HTTP-date', () => {
    const now = Date.parse('2026-11-11T10:00:00Z');
    expect(parseRetryAfter('37', now)).toBe(37);
    expect(parseRetryAfter('Wed, 11 Nov 2026 10:00:40 GMT', now)).toBe(40);
    expect(parseRetryAfter('invalid', now)).toBe(0);
  });
});
