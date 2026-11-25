import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import { describe, expect, it } from 'vitest';
import en from '../i18n/en.json';
import es from '../i18n/es.json';
import ru from '../i18n/ru.json';

const window: { PadelLevels: { matches: (event: string | null, selected: string) => boolean } } = {
  PadelLevels: { matches: () => false },
};
runInNewContext(readFileSync('../src/main/resources/static/js/level-utils.js', 'utf8'), { window });

describe('legacy level filters and translations', () => {
  it('matches C3 and either endpoint of C4/C3 without matching C5 or SUMA', () => {
    for (const [event, selected, expected] of [
      ['C3', 'C3', true],
      ['C4_C3', 'C3', true],
      ['C4_C3', 'C4', true],
      ['C4_C3', 'C5', false],
      ['SUMA_13', 'C3', false],
      [null, 'C3', false],
      ['SUMA_13', 'SUMA_13', true],
      ['PRINCIPIANTES', 'Principiante', true],
    ] as const)
      expect(window.PadelLevels.matches(event, selected)).toBe(expected);
  });
  it('shows the same category in every supported language', () => {
    for (const dictionary of [es, en, ru]) {
      expect(dictionary.level.C3).toBe('C3');
      expect(dictionary.level.C4_C3).toBe('C4/C3');
    }
  });
});
