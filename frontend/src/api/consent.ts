export type ConsentChoice = 'accepted' | 'rejected' | 'customized' | 'unknown';
function cookie(name: string) {
  return document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}
export function readConsent() {
  const value = cookie('cookieConsent');
  const choice: ConsentChoice =
    value === 'accepted' || value === 'rejected' || value === 'customized' ? value : 'unknown';
  return {
    choice,
    analytics:
      choice === 'accepted' || (choice === 'customized' && cookie('analyticsConsent') === 'true'),
    marketing:
      choice === 'accepted' || (choice === 'customized' && cookie('marketingConsent') === 'true'),
  };
}
interface ConsentScripts {
  gtag?: (...args: unknown[]) => void;
  clarity?: (...args: unknown[]) => void;
  initClarity?: () => void;
}
export function applyConsent() {
  const consent = readConsent();
  const scripts = window as Window & ConsentScripts;
  scripts.gtag?.('consent', 'update', {
    analytics_storage: consent.analytics ? 'granted' : 'denied',
  });
  if (consent.analytics) {
    scripts.initClarity?.();
    scripts.clarity?.('consent');
  } else scripts.clarity?.('consent', false);
  window.dispatchEvent(new Event('marketing-consent-changed'));
}
