import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { apiFetch } from './client';
import type { SpaSession } from './types';

export interface Profile {
  id: number;
  nombre: string;
  apellido: string;
  email: string;
  telefono: string | null;
  telegramUsername: string | null;
  nivelJugador: string;
  preferredLocale: string;
  guestAccount: boolean;
  oauth2User: boolean;
}
export interface AccountResult {
  success: boolean;
  code: string;
  destination: string | null;
}
export interface PublicConfig {
  captchaEnabled: boolean;
  captchaSiteKey: string;
  googleEnabled: boolean;
  supportedLocales: string[];
  locale: string;
}
export interface LoginResult {
  success: boolean;
  session: SpaSession;
  destination: string;
}
export const sessionKey = ['account', 'session'] as const;
export const profileKey = ['account', 'profile'] as const;
export function jsonPost<T>(path: string, body: unknown, method = 'POST') {
  return apiFetch<T>(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}
export function login(email: string, password: string, remember: boolean, destination: string) {
  return apiFetch<LoginResult>('/login', {
    method: 'POST',
    body: new URLSearchParams({
      username: email,
      password,
      'remember-me': String(remember),
      destination,
    }),
  });
}
export function safeDestination(value: string | null): string | null {
  if (!value || value.length > 2048) return null;
  let decoded = value;
  try {
    for (let i = 0; i < 5; i++) {
      if (
        !decoded.startsWith('/') ||
        decoded.startsWith('//') ||
        decoded.includes('\\') ||
        Array.from(decoded).some((char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)
      )
        return null;
      if (new URL(decoded, window.location.origin).origin !== window.location.origin) return null;
      const next = decodeURIComponent(decoded);
      if (next === decoded) return value;
      decoded = next;
    }
  } catch {
    return null;
  }
  return null;
}
export function useSession() {
  const client = useQueryClient();
  useEffect(() => {
    const refresh = () => {
      void client.invalidateQueries({ queryKey: sessionKey });
      void client.invalidateQueries({ queryKey: profileKey });
    };
    const visible = () => {
      if (document.visibilityState === 'visible') refresh();
    };
    window.addEventListener('focus', refresh);
    window.addEventListener('one-padel:account-changed', refresh);
    document.addEventListener('visibilitychange', visible);
    const channel =
      typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('one-padel-account');
    if (channel) channel.onmessage = refresh;
    return () => {
      window.removeEventListener('focus', refresh);
      window.removeEventListener('one-padel:account-changed', refresh);
      document.removeEventListener('visibilitychange', visible);
      channel?.close();
    };
  }, [client]);
  return useQuery({
    queryKey: sessionKey,
    queryFn: () => apiFetch<SpaSession>('/api/auth/session'),
    retry: false,
  });
}
export function accountChanged() {
  window.dispatchEvent(new Event('one-padel:account-changed'));
  if (typeof BroadcastChannel !== 'undefined') {
    const channel = new BroadcastChannel('one-padel-account');
    channel.postMessage('refresh');
    channel.close();
  }
}
