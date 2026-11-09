export type Locale = 'es' | 'ru' | 'en';
export const locales: Locale[] = ['es', 'ru', 'en'];
export const localeNames = { es: 'Español', ru: 'Русский', en: 'English' };
export const isLocale = (v: unknown): v is Locale => locales.includes(v as Locale);
const storageKey = '1padel-ui-language';
export function resolveLocale(urlLanguage: string | null, saved: string | null, browser: string): Locale {
  if (isLocale(urlLanguage)) return urlLanguage;
  if (isLocale(saved)) return saved;
  const short = browser.toLowerCase().split('-')[0];
  return isLocale(short) ? short : 'es';
}
function initial(): Locale {
  if (typeof window === 'undefined') return 'es';
  let saved: string | null = null;
  try { saved = localStorage.getItem(storageKey); } catch { /* private browsing */ }
  const url = new URL(location.href);
  return resolveLocale(url.searchParams.get('lang') ?? new URLSearchParams(url.hash.split('?')[1] || '').get('lang'), saved, navigator.language);
}
let locale: Locale = initial();
const listeners = new Set<() => void>();
export const getLocale = () => locale;
export const subscribeLocale = (callback: () => void) => { listeners.add(callback); return () => { listeners.delete(callback); }; };
function syncDocument() {
  if (typeof document === 'undefined') return;
  document.documentElement.lang = locale;
  document.title = {es:'1-Padel · Eventos y tus partidos',ru:'1-Padel · Мероприятия и ваши игры',en:'1-Padel · Events and your games'}[locale];
}
export function setLocale(next: Locale) {
  locale = next;
  try { localStorage.setItem(storageKey, next); } catch { /* selection still works in memory */ }
  if (typeof window !== 'undefined') { const url = new URL(location.href); url.searchParams.set('lang',next); history.replaceState(history.state,'',url); }
  syncDocument();
  listeners.forEach(fn => fn());
}
syncDocument();
