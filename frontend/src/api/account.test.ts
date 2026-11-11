import { describe, expect, it } from 'vitest';
import { safeDestination } from './account';

describe('safeDestination', () => {
  it.each([
    'https://evil.test',
    '//evil.test',
    '/%2fevil.test',
    '/%252fevil.test',
    '/%5cevil.test',
    '/%0dLocation:x',
  ])('rejects unsafe target %s', (target) => expect(safeDestination(target)).toBeNull());
  it.each(['/torneo/42#tournament-registration', '/preview/perfil'])(
    'retains internal target %s',
    (target) => expect(safeDestination(target)).toBe(target),
  );
});
