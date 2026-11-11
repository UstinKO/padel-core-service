import { createElement, useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import content from '../legal/content.json';
import { usePageMetadata } from '../components/PageMetadata';

type LegalNode = string | { tag: string; children: LegalNode[]; href?: string };
function render(node: LegalNode, key: number): React.ReactNode {
  if (typeof node === 'string') return node;
  const allowed = new Set([
    'div',
    'section',
    'h1',
    'h2',
    'h3',
    'h4',
    'p',
    'ul',
    'ol',
    'li',
    'strong',
    'em',
    'a',
    'br',
    'table',
    'thead',
    'tbody',
    'tr',
    'th',
    'td',
  ]);
  const tag = allowed.has(node.tag) ? node.tag : 'span';
  const href =
    node.href && /^(https?:\/\/|mailto:|\/(?!\/))/.test(node.href) ? node.href : undefined;
  return createElement(
    tag,
    { key, ...(tag === 'a' ? { href } : {}) },
    ...node.children.map(render),
  );
}
export function LegalPage({ page }: { page: keyof typeof content }) {
  const { t, i18n } = useTranslation();
  const article = useRef<HTMLElement>(null);
  useEffect(() => {
    const heading = article.current?.querySelector('h1');
    if (heading) {
      heading.tabIndex = -1;
      heading.focus();
    }
  }, [page]);
  const language = i18n.language === 'ru' || i18n.language === 'en' ? i18n.language : 'es';
  usePageMetadata(t(`legal.${page}`), `${window.location.origin}/${page}`);
  return (
    <article className="legal-content" ref={article}>
      {(content[page][language] as LegalNode[]).map(render)}
      <Link to="/">{t('legal.back')}</Link>
    </article>
  );
}
