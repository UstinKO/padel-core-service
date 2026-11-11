import { useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import QRCode from 'qrcode';
import { Button } from './Button';

export function ShareDialog({ url, title }: { url: string; title: string }) {
  const { t } = useTranslation();
  const dialog = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false);
  const [copied, setCopied] = useState(false);
  const [copyError, setCopyError] = useState(false);
  const qr = useQuery({
    queryKey: ['local-qr', url],
    queryFn: () => QRCode.toDataURL(url, { width: 240, margin: 2 }),
    enabled: open,
    staleTime: Infinity,
  });
  useEffect(() => {
    if (open && !dialog.current?.open) dialog.current?.showModal();
    if (!open && dialog.current?.open) dialog.current?.close();
  }, [open]);
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    const element = dialog.current;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = previous;
      element?.close();
    };
  }, [open]);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
      setCopyError(false);
    } catch {
      setCopyError(true);
    }
  };
  return (
    <>
      <Button
        variant="secondary"
        onClick={() => {
          setCopied(false);
          setCopyError(false);
          setOpen(true);
        }}
      >
        {t('details.share')}
      </Button>
      <dialog
        className="share-dialog"
        ref={dialog}
        aria-labelledby="share-title"
        onClose={() => setOpen(false)}
        onClick={(event) => {
          if (event.target === event.currentTarget) dialog.current?.close();
        }}
      >
        <form method="dialog" className="dialog-heading">
          <h2 id="share-title">{t('details.shareTitle')}</h2>
          <Button variant="ghost" aria-label={t('details.close')}>
            {t('details.close')}
          </Button>
        </form>
        <p>{title}</p>
        <label className="field">
          <span className="field__label">{t('details.publicLink')}</span>
          <input
            className="field__input"
            readOnly
            value={url}
            onFocus={(event) => event.target.select()}
          />
        </label>
        <div className="button-row">
          <Button onClick={() => void copy()}>{t('details.copy')}</Button>
          <a
            className="button button--secondary"
            target="_blank"
            rel="noopener noreferrer"
            href={`https://wa.me/?text=${encodeURIComponent(title + ' ' + url)}`}
          >
            WhatsApp
          </a>
          <a
            className="button button--secondary"
            target="_blank"
            rel="noopener noreferrer"
            href={`https://t.me/share/url?url=${encodeURIComponent(url)}&text=${encodeURIComponent(title)}`}
          >
            Telegram
          </a>
        </div>
        {copied && <p role="status">{t('details.copied')}</p>}
        {copyError && <p role="alert">{t('details.copyError')}</p>}
        {qr.data && (
          <img
            className="share-qr"
            src={qr.data}
            alt={t('details.qrAlt')}
            width="240"
            height="240"
          />
        )}
        {qr.isError && <p role="alert">{t('details.qrError')}</p>}
      </dialog>
    </>
  );
}
