import type { ReactNode } from 'react';

type StatusTone = 'loading' | 'empty' | 'error' | 'success';

interface StatusViewProps {
  tone: StatusTone;
  title: string;
  text: string;
  action?: ReactNode;
}

export function StatusView({ tone, title, text, action }: StatusViewProps) {
  return (
    <div className={`status status--${tone}`} role={tone === 'error' ? 'alert' : 'status'}>
      <span className="status__mark" aria-hidden="true" />
      <div>
        <h2>{title}</h2>
        <p>{text}</p>
      </div>
      {action && <div className="status__action">{action}</div>}
    </div>
  );
}
