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
  it('keeps ES, RU and EN dictionaries structurally complete', () => {
    const baseline = keys(es).sort();
    expect(keys(ru).sort()).toEqual(baseline);
    expect(keys(en).sort()).toEqual(baseline);
  });
});
