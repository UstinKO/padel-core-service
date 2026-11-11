import { render, screen, cleanup } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { Field } from './Field';

afterEach(cleanup);
it('keeps the field name stable and exposes validation as an accessible description', () => {
  render(
    <Field label="Current password" type="password" error="The current password is incorrect." />,
  );
  const input = screen.getByLabelText('Current password', { exact: true });
  expect(input).toHaveAccessibleName('Current password');
  expect(input).toHaveAccessibleDescription('The current password is incorrect.');
  expect(input).toHaveAttribute('aria-invalid', 'true');
});
