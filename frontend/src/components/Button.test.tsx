import { render, screen } from '@testing-library/react';

import { Button } from './Button';

describe('Button', () => {
  it('renders an accessible secondary action', () => {
    render(<Button variant="secondary">Continue</Button>);

    expect(screen.getByRole('button', { name: 'Continue' })).toHaveClass('button--secondary');
  });
});
