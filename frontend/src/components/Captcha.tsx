import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';

interface Recaptcha {
  render: (
    element: HTMLElement,
    options: {
      sitekey: string;
      callback: (token: string) => void;
      'expired-callback': () => void;
      'error-callback': () => void;
      hl: string;
    },
  ) => number;
  reset: (id: number) => void;
  ready: (callback: () => void) => void;
}
declare global {
  interface Window {
    grecaptcha?: Recaptcha;
  }
}
let loading: Promise<void> | undefined;
function load() {
  loading ??= new Promise<void>((resolve, reject) => {
    if (window.grecaptcha) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://www.google.com/recaptcha/api.js?render=explicit';
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loading = undefined;
      reject(new Error('CAPTCHA_UNAVAILABLE'));
    };
    document.head.append(script);
  });
  return loading;
}
export function Captcha({
  siteKey,
  onToken,
  generation,
}: {
  siteKey: string;
  onToken: (token: string) => void;
  generation: number;
}) {
  const element = useRef<HTMLDivElement>(null);
  const tokenCallback = useRef(onToken);
  const { i18n, t } = useTranslation();
  useEffect(() => {
    tokenCallback.current = onToken;
  }, [onToken]);
  useEffect(() => {
    let active = true;
    let widget: number | undefined;
    void load()
      .then(() =>
        window.grecaptcha?.ready(() => {
          if (!active || !element.current) return;
          widget = window.grecaptcha?.render(element.current, {
            sitekey: siteKey,
            hl: i18n.language,
            callback: (token) => tokenCallback.current(token),
            'expired-callback': () => tokenCallback.current(''),
            'error-callback': () => tokenCallback.current(''),
          });
        }),
      )
      .catch(() => tokenCallback.current(''));
    return () => {
      active = false;
      if (widget !== undefined) window.grecaptcha?.reset(widget);
    };
  }, [siteKey, i18n.language, generation]);
  return (
    <div className="account-captcha" aria-label={t('account.captcha')}>
      <div key={`${i18n.language}-${generation}`} ref={element} />
      <p>{t('account.captchaHelp')}</p>
    </div>
  );
}
