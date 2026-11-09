import { useEffect, useRef, type ReactNode } from 'react';
import { Icon } from './Icon';
export function Modal({ open, title, kicker, children, onClose }: {
    open: boolean;
    title: string;
    kicker?: string;
    children: ReactNode;
    onClose: () => void;
}) { const ref = useRef<HTMLDialogElement>(null); useEffect(() => { if (open) {
    ref.current?.showModal();
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { ref.current?.close(); document.body.style.overflow = previous; };
} }, [open]); if (!open)
    return null; return <dialog className="react-dialog" ref={ref} onCancel={e => { e.preventDefault(); onClose(); }} onClick={e => { if (e.target === e.currentTarget)
    onClose(); }} aria-labelledby="dialog-title"><section className="modal"><div className="modal-header"><div>{kicker && <div className="eyebrow muted">{kicker}</div>}<h2 id="dialog-title">{title}</h2></div><button className="close-btn" onClick={onClose} aria-label="Закрыть окно"><Icon name="close"/></button></div><div className="modal-body">{children}</div></section></dialog>; }
