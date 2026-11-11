import { useEffect } from 'react';

export function usePageMetadata(title: string | undefined, canonical: string | null) {
  useEffect(() => {
    if (!title) return;
    const previous = document.title;
    document.title = `${title} · 1-Padel`;
    let link = document.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    const previousCanonical = link?.href;
    const created = !link;
    if (canonical) {
      if (!link) {
        link = document.createElement('link');
        link.rel = 'canonical';
        document.head.append(link);
      }
      link.href = canonical;
    }
    return () => {
      document.title = previous;
      if (created) link?.remove();
      else if (link && previousCanonical) link.href = previousCanonical;
    };
  }, [title, canonical]);
}
