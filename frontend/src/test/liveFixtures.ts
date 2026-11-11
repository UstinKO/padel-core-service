import type { King, Americano, Playoff, LiveMatch, LiveRound } from '../api/live';
const metadata: King['metadata'] = {
  eventId: 42,
  name: 'Copa de nombres largos',
  phase: 'LIVE',
  eventStatus: 'EN_CURSO',
  instanceId: 77,
  conductVersion: 1,
  generatedAt: '2026-11-11T12:00:00Z',
  canonicalUrl: 'https://public.example.test/torneo/42/king-of-court',
  showLevels: false,
  topic: '/topic/public/live/42',
  pollSeconds: 20,
};
function name(id: number) {
  return (
    (id < 3 ? 'Александра Константинопольская — María de los Ángeles del Río ' : 'Jugador ') + id
  );
}
function match(number: number): LiveMatch {
  return {
    id: 100 + number,
    number: 1,
    court: 1,
    phase: number === 1 ? 'CALIBRATION' : 'MAIN',
    stage: null,
    status: number < 2 ? 'COMPLETED' : number === 2 ? 'IN_PROGRESS' : 'PENDING',
    first: {
      teamId: 1,
      number: 1,
      name: '',
      players: [1, 2].map((id) => ({ id, name: name(id), level: null })),
      score: 21,
      games: null,
      matchOrdinal: null,
    },
    second: {
      teamId: 2,
      number: 2,
      name: '',
      players: [3, 4].map((id) => ({ id, name: name(id), level: null })),
      score: 15,
      games: null,
      matchOrdinal: null,
    },
    completed: number < 2,
    firstWinner: number < 2 ? true : null,
    nextMatchId: null,
    nextSlot: null,
    priority: false,
    queuePosition: null,
    waitingMinutes: null,
  };
}
const rounds: LiveRound[] = Array.from({ length: 19 }, (_, index) => {
  const number = index + 1;
  const value = match(number);
  return {
    id: number,
    number,
    phase: value.phase,
    status: value.status,
    courts: 1,
    matches: [value],
    byePlayerNames: [],
  };
});
export const king: King = {
  metadata,
  format: 'KING_OF_COURT',
  currentRound: 2,
  calibrationRounds: 1,
  rounds,
  ranking: [1, 2, 3, 4].map((id) => ({
    playerId: id,
    playerName: name(id),
    playerNivel: null,
    rank: id,
    totalPoints: 31 - id,
    matchPoints: 21 - id,
    bonusPoints: 3,
    additionalPoints: { total: 7, stages: [{ roundNumber: 1, points: 7 }] },
    gamesPlayed: 1,
    wins: 1,
    losses: 0,
  })),
};
export function americano(): Americano {
  return {
    metadata: {
      ...king.metadata,
      topic: '',
      canonicalUrl: 'https://public.example.test/tournaments/americano/42',
    },
    format: 'AMERICANO',
    formatType: 'PURE',
    rounds: king.rounds.map((round) => ({ ...round, phase: 'MAIN' })),
    ranking: king.ranking.map((row) => ({
      playerId: row.playerId,
      playerName: row.playerName,
      playerNivel: row.playerNivel,
      position: row.rank,
      totalScore: row.totalPoints,
      matchPoints: row.matchPoints,
      additionalPoints: row.additionalPoints,
      matchesPlayed: 1,
      matchesWon: 1,
      matchesLost: 0,
      matchesDrawn: 0,
      byeCount: 0,
      pointsScored: 21,
      pointsConceded: 15,
      pointDifference: 6,
    })),
  };
}
export function playoff(): Playoff {
  const rounds = king.rounds.slice(0, 4).map((round, index) => ({
    ...round,
    phase: index === 0 ? 'QUALIFICATION' : 'PLAYOFF',
    matches: round.matches.map((match) => ({
      ...match,
      phase: index === 0 ? 'QUALIFICATION' : 'PLAYOFF',
      stage:
        index === 0 ? null : index === 1 ? 'QUARTER_FINAL' : index === 2 ? 'SEMI_FINAL' : 'FINAL',
      nextMatchId: index === 1 ? 103 : null,
      nextSlot: index === 1 ? 1 : null,
      first: { ...match.first, games: 6, score: null, matchOrdinal: 1 },
      second: { ...match.second, games: 3, score: null, matchOrdinal: 1 },
    })),
  }));
  return {
    metadata: {
      ...king.metadata,
      phase: 'PLAYOFF',
      canonicalUrl: 'https://public.example.test/tournaments/team-playoff/42',
    },
    format: 'TEAM_PLAYOFF',
    rounds,
    ranking: [
      {
        id: 1,
        teamNumber: 1,
        displayName: '№1 Equipo',
        player1: { id: 1, name: 'Alice', level: null },
        player2: { id: 2, name: 'Bea', level: null },
        currentPosition: 1,
        setsWon: 2,
        setsLost: 0,
        gamesWon: 12,
        gamesLost: 6,
        matchesPlayed: 2,
        matchesWon: 2,
        matchesLost: 0,
        matchesDrawn: 0,
        match1GamesWon: 6,
        match1GamesLost: 3,
        match2GamesWon: 6,
        match2GamesLost: 3,
        qualStatus: '2-0',
        tournamentStatus: 'PLAYING',
        eliminatedStage: null,
        queuePosition: null,
        queueOpponentName: null,
      },
    ],
    queue: [],
  };
}
