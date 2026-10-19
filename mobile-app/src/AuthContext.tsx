import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import { AuthError, createAuthClient, parseSessionFragment } from './auth';
import type { Profile } from './auth';
import { createNativeSessionStore, createWebSessionStore } from './sessionStore';

const secureKey = 'onepadel.refresh.v1';
export const secureAuthContext = Platform.OS !== 'web' || (typeof window !== 'undefined' && (window.location.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(window.location.hostname)));
// Веб: refresh-токен в localStorage переживает закрытие/повторное открытие вкладки (LFPT-0646) —
// только за гейтом secureAuthContext (HTTPS/localhost), как и сам вход.
// Native: без изменений, SecureStore.
const store = Platform.OS === 'web'
  ? createWebSessionStore(secureAuthContext && typeof window !== 'undefined' ? window.localStorage : null, secureKey)
  : createNativeSessionStore(SecureStore, secureKey, SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY);

type Auth = { profile: Profile | null; restoring: boolean; restoreError: string | null; googleCode: string | null;
  login: (email: string, password: string) => Promise<void>; google: () => Promise<void>; cancelGoogle: () => void;
  logout: () => Promise<void>; retryRestore: () => Promise<void>; client: ReturnType<typeof createAuthClient> };
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [restoring, setRestoring] = useState(true);
  const [restoreError, setRestoreError] = useState<string | null>(null);
  const [googleCode, setGoogleCode] = useState<string | null>(null);
  const attempt = useRef(0);
  const client = useMemo(() => createAuthClient({ store, baseUrl: Platform.OS === 'web' && typeof window !== 'undefined' && window.location.protocol === 'https:' && ['1-padel.com', 'www.1-padel.com'].includes(window.location.hostname) ? window.location.origin : 'https://1-padel.com', onExpired: () => setProfile(null) }), []);
  const retryRestore = useCallback(async () => {
    setRestoring(true); setRestoreError(null);
    try {
      // Возврат после входа через Google: сервер кладёт токены во fragment /app/#accessToken=... (LFPT-0637).
      // Сразу убираем их из адресной строки, чтобы не остались в истории и закладках.
      const returned = Platform.OS === 'web' && typeof window !== 'undefined' ? parseSessionFragment(window.location.hash) : null;
      if (returned) window.history.replaceState(null, '', window.location.pathname + window.location.search);
      setProfile(returned ? await client.adopt(returned) : await client.restore());
    }
    catch (error) { if (error instanceof AuthError && !['SESSION_EXPIRED', 'ACCOUNT_UNAVAILABLE', 'CANCELLED'].includes(error.code)) setRestoreError(error.code); }
    finally { setRestoring(false); }
  }, [client]);
  useEffect(() => { void retryRestore(); return () => { attempt.current++; }; }, [retryRestore]);
  const cancelGoogle = useCallback(() => { attempt.current++; client.cancelPending(); setGoogleCode(null); }, [client]);
  async function google() {
    if (!secureAuthContext) throw new AuthError('INSECURE');
    // Вход через Google — серверный redirect-флоу сайта: страница уходит на Google и возвращается на /app/.
    // В нативном приложении вернуть токены из браузера пока некуда — только веб-версия.
    if (Platform.OS !== 'web') throw new AuthError('UNAVAILABLE');
    cancelGoogle();
    window.location.assign(client.googleUrl());
    await new Promise<never>(() => {});
  }
  async function login(email: string, password: string) {
    if (!secureAuthContext) throw new AuthError('INSECURE');
    cancelGoogle(); setRestoreError(null); setProfile(await client.login(email, password));
  }
  async function logout() { cancelGoogle(); await client.logout(); setProfile(null); }
  return <Context.Provider value={{ profile, restoring, restoreError, googleCode, login, google, cancelGoogle, logout, retryRestore, client }}>{children}</Context.Provider>;
}
export function useAuth() { const value = useContext(Context); if (!value) throw new Error('AuthProvider required'); return value; }
