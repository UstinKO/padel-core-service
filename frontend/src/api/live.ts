import { apiFetch } from './client';

export type Metadata = {
  eventId: number;
  name: string | null;
  phase: 'NOT_STARTED' | 'LIVE' | 'QUALIFICATION' | 'PLAYOFF' | 'FINISHED' | 'CANCELLED';
  eventStatus: string | null;
  instanceId: number | null;
  conductVersion: number;
  generatedAt: string | null;
  canonicalUrl: string | null;
  showLevels: boolean;
  topic: string | null;
  pollSeconds: number;
};
export type Participant = { id: number | null; name: string | null; level: string | null };
export type Side = {
  teamId: number | null;
  number: number | null;
  name: string | null;
  players: Participant[];
  score: number | null;
  games: number | null;
  matchOrdinal: number | null;
};
export type Match = {
  id: number | null;
  number: number | null;
  court: number | null;
  phase: string | null;
  stage: string | null;
  status: string | null;
  first: Side;
  second: Side;
  completed: boolean;
  firstWinner: boolean | null;
  nextMatchId: number | null;
  nextSlot: number | null;
  priority: boolean;
  queuePosition: number | null;
  waitingMinutes: number | null;
};
export type Round = {
  id: number | null;
  number: number | null;
  phase: string | null;
  status: string | null;
  courts: number | null;
  matches: Match[];
  byePlayerNames: (string | null)[];
};
export type KingRank = {
  playerId: number | null;
  playerName: string | null;
  playerNivel: string | null;
  rank: number | null;
  totalPoints: number | null;
  matchPoints: number | null;
  bonusPoints: number | null;
  additionalPoints: AdditionalPointsDto;
  gamesPlayed: number | null;
  wins: number | null;
  losses: number | null;
};
export type AmericanoRank = {
  playerId: number | null;
  playerName: string | null;
  playerNivel: string | null;
  position: number | null;
  totalScore: number | null;
  matchPoints: number | null;
  additionalPoints: AdditionalPointsDto;
  matchesPlayed: number | null;
  matchesWon: number | null;
  matchesLost: number | null;
  matchesDrawn: number | null;
  byeCount: number | null;
  pointsScored: number | null;
  pointsConceded: number | null;
  pointDifference: number | null;
};
export type TeamRank = {
  id: number | null;
  teamNumber: number | null;
  displayName: string | null;
  player1: Participant;
  player2: Participant;
  currentPosition: number | null;
  setsWon: number | null;
  setsLost: number | null;
  gamesWon: number | null;
  gamesLost: number | null;
  matchesPlayed: number | null;
  matchesWon: number | null;
  matchesLost: number | null;
  matchesDrawn: number | null;
  match1GamesWon: number | null;
  match1GamesLost: number | null;
  match2GamesWon: number | null;
  match2GamesLost: number | null;
  qualStatus: string | null;
  tournamentStatus: string | null;
  eliminatedStage: string | null;
  queuePosition: number | null;
  queueOpponentName: string | null;
};
export type King = {
  metadata: Metadata;
  format: 'KING_OF_COURT';
  currentRound: number | null;
  calibrationRounds: number | null;
  rounds: Round[];
  ranking: KingRank[];
};
export type Americano = {
  metadata: Metadata;
  format: 'AMERICANO';
  formatType: string | null;
  rounds: Round[];
  ranking: AmericanoRank[];
};
export type Playoff = {
  metadata: Metadata;
  format: 'TEAM_PLAYOFF';
  rounds: Round[];
  ranking: TeamRank[];
  queue: Match[];
};
export type Pending = { metadata: Metadata; format: 'TEAM_PAIRS'; rounds: Round[] };
export type AdditionalPointsDto = {
  total: number;
  stages: { roundNumber: number; points: number }[];
};
export type Snapshot = King | Americano | Playoff | Pending;

export type LiveSnapshot = Snapshot;
export type LiveFormat = 'KING_OF_COURT' | 'AMERICANO' | 'TEAM_PLAYOFF';
export type LiveRound = Round;
export type LiveMatch = Match;
export type LiveSide = Side;
export function getLive(id: string, alias: boolean, signal: AbortSignal) {
  return apiFetch<LiveSnapshot>(
    alias ? `/api/public/king-of-court/${id}/live` : `/api/public/events/${id}/live`,
    { signal },
  );
}
export function previewLivePath(value: string): string | null {
  try {
    const path = new URL(value, window.location.origin).pathname;
    return /^\/torneo\/[1-9]\d*\/king-of-court\/?$/.test(path) ||
      /^\/tournaments\/(americano|team-playoff)\/[1-9]\d*\/?$/.test(path)
      ? path
      : null;
  } catch {
    return null;
  }
}
export const roundKey = (round: LiveRound) => `${round.phase}:${round.number}:${round.id}`;
export const sideNames = (side: LiveSide) =>
  side.players
    .map((player) => player.name)
    .filter(Boolean)
    .join(' / ') ||
  side.name ||
  '';
export const allMatches = (snapshot: LiveSnapshot) =>
  snapshot.rounds.flatMap((round) => round.matches);
export function playerName(snapshot: LiveSnapshot, id: string | null): string | null {
  if (!id) return null;
  const participant = allMatches(snapshot)
    .flatMap((match) => [...match.first.players, ...match.second.players])
    .find((player) => String(player.id) === id);
  if (participant?.name) return participant.name;
  if (snapshot.format === 'TEAM_PAIRS') return null;
  if (snapshot.format === 'TEAM_PLAYOFF')
    return (
      snapshot.ranking
        .flatMap((team) => [team.player1, team.player2])
        .find((player) => String(player.id) === id)?.name ?? null
    );
  return snapshot.ranking.find((player) => String(player.playerId) === id)?.playerName ?? null;
}
