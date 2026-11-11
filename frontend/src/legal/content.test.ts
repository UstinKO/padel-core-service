import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import content from './content.json';

type Node = string | { tag: string; children: Node[]; href?: string };
const text = (node: Node): string =>
  typeof node === 'string' ? node : node.children.map(text).join('');
const normalize = (value: string) => value.replace(/\s+/g, ' ').trim();
const links = (nodes: Node[]): string[] =>
  nodes.flatMap((node) =>
    typeof node === 'string' ? [] : [...(node.href ? [node.href] : []), ...links(node.children)],
  );
describe('approved legal copy', () => {
  for (const page of ['terminos', 'privacidad', 'cookies'] as const) {
    const source = readFileSync(
      resolve(process.cwd(), `../src/main/resources/templates/legal/${page}.html`),
      'utf8',
    );
    const document = new DOMParser().parseFromString(source, 'text/html');
    for (const language of ['es', 'ru', 'en'] as const) {
      it(`${page} preserves all ${language} wording and links`, () => {
        const branch = Array.from(document.querySelectorAll('div')).find(
          (node) => node.getAttribute('th:case') === (language === 'es' ? '*' : `'${language}'`),
        );
        expect(branch).toBeDefined();
        expect(normalize((content[page][language] as Node[]).map(text).join(''))).toBe(
          normalize(branch?.textContent ?? ''),
        );
        expect(links(content[page][language] as Node[])).toEqual(
          Array.from(branch?.querySelectorAll('a') ?? []).map((link) => link.getAttribute('href')),
        );
      });
    }
  }
});
