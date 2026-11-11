import { describe, expect, it } from 'vitest';
import { canonicalUrl, safeWebUrl, validEntityId } from './eventDetails';

describe('public entity links', () => {
  it('accepts only positive identifiers in the server range', () => {
    expect(validEntityId('42')).toBe(true);
    expect(validEntityId('9223372036854775807')).toBe(true);
    for (const id of ['0', '-1', '1e3', 'abc', '9223372036854775808'])
      expect(validEntityId(id)).toBe(false);
  });
  it('rejects executable URLs and embedded credentials', () => {
    for (const url of [
      'javascript:alert(1)',
      'data:text/html,bad',
      'https://user:pass@example.test',
    ])
      expect(safeWebUrl(url)).toBeNull();
    expect(safeWebUrl('/clubs/7')).toContain('/clubs/7');
  });
  it('keeps canonical links independent of preview and tracking parameters', () => {
    expect(
      canonicalUrl('https://public.example.test/torneo/42?track=1#preview', '/torneo/42'),
    ).toBe('https://public.example.test/torneo/42');
    expect(canonicalUrl('https://public.example.test/preview/torneo/42', '/torneo/42')).toBeNull();
  });
});
