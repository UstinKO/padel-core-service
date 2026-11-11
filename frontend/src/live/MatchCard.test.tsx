import { render, screen } from '@testing-library/react';
import { expect, it, vi } from 'vitest';
import { MatchCard } from './MatchCard';
import { king } from '../test/liveFixtures';
it('ничья без server winner не выделяет победившую сторону', () => {
  const match = structuredClone(king.rounds[0].matches[0]);
  match.firstWinner = null;
  match.first.score = 16;
  match.second.score = 16;
  const view = render(<MatchCard match={match} openPlayer={vi.fn()} />);
  expect(view.container.querySelectorAll('.live-winner')).toHaveLength(0);
});
it('выделение определяется server winner, без сравнения счёта в браузере', () => {
  const match = structuredClone(king.rounds[0].matches[0]);
  match.firstWinner = false;
  const view = render(<MatchCard match={match} openPlayer={vi.fn()} />);
  expect(view.container.querySelectorAll('.live-winner')).toHaveLength(1);
  expect(view.container.querySelectorAll('.live-side')[1]).toHaveClass('live-winner');
  expect(screen.getByText('Jugador 3')).toBeVisible();
});
