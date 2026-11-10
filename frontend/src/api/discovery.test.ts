import { describe, expect, it } from 'vitest';
import { buenosAiresToday, catalogParams } from './discovery';

describe('catalog URL contract', () => {
  it('uses the Buenos Aires date across the UTC day boundary', () => {
    expect(buenosAiresToday(new Date('2026-11-11T01:00:00Z'))).toBe('2026-11-10');
  });
  it('passes server filters and pagination without demo query parameters', () => {
    const result = catalogParams(
      new URLSearchParams('clubId=12&type=AMERICANO&from=2026-12-01&page=2&state=demo&size=999'),
    );
    expect(result.get('clubId')).toBe('12');
    expect(result.get('type')).toBe('AMERICANO');
    expect(result.get('page')).toBe('2');
    expect(result.get('size')).toBe('12');
    expect(result.has('state')).toBe(false);
  });
  it('normalizes unsafe page values', () => {
    for (const page of ['-1', '1e4', '2147483648', 'abc']) {
      expect(catalogParams(new URLSearchParams({ page })).get('page')).toBe('0');
    }
  });
});
