import type { HTMLAttributes, PropsWithChildren } from 'react';

export function Card({ className = '', ...props }: PropsWithChildren<HTMLAttributes<HTMLElement>>) {
  return <section className={`card ${className}`.trim()} {...props} />;
}
