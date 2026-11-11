import { render, screen, cleanup } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AppErrorBoundary, ReadError } from './SystemState';
import { ApiError } from '../api/client';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
it('component failure renders a safe fallback without exception details', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {});
  function Broken(): never {
    throw new Error('private database stack');
  }
  render(
    <AppErrorBoundary>
      <Broken />
    </AppErrorBoundary>,
  );
  expect(screen.getByRole('heading')).toHaveTextContent('The page could not be opened');
  expect(screen.queryByText(/private database/)).not.toBeInTheDocument();
});
it('429 keeps retry disabled until the actual server deadline', () => {
  const error = new ApiError(429, {
    type: '/problems/rate-limit',
    title: 'Rate limit',
    status: 429,
    detail: 'wait',
    instance: '/api/example',
    code: 'RATE_LIMITED',
    blockedUntil: new Date(Date.now() + 37_000).toISOString(),
  });
  render(
    <MemoryRouter>
      <ReadError error={error} retry={vi.fn()} />
    </MemoryRouter>,
  );
  expect(screen.getByRole('button', { name: 'Try again' })).toBeDisabled();
  expect(screen.getByRole('status')).toHaveTextContent('37');
  expect(document.querySelector('meta[name="robots"]')).toHaveAttribute(
    'content',
    'noindex, nofollow',
  );
});
