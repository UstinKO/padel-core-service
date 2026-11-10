import en from './en.json';
import es from './es.json';
import ru from './ru.json';

function keys(value: unknown, prefix = ''): string[] {
  if (!value || typeof value !== 'object') return [prefix];
  return Object.entries(value).flatMap(([key, child]) =>
    keys(child, prefix ? `${prefix}.${key}` : key),
  );
}

describe('translations', () => {
  it('provides capacity units for each language plural category', () => {
    for (const [language, dictionary] of Object.entries({ es, en, ru })) {
      for (const category of new Intl.PluralRules(language).resolvedOptions().pluralCategories) {
        for (const unit of ['PLAYERS', 'PAIRS']) {
          expect(dictionary.capacityUnit).toHaveProperty(`${unit}_${category}`);
        }
      }
    }
  });
  it('keeps ES, RU and EN dictionaries structurally complete', () => {
    const normalized = (value: unknown) =>
      [
        ...new Set(keys(value).map((key) => key.replace(/_(one|few|many|other|two|zero)$/, ''))),
      ].sort();
    const baseline = normalized(es);
    expect(normalized(ru)).toEqual(baseline);
    expect(normalized(en)).toEqual(baseline);
  });
});
