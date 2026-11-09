import { names } from '../data/events.ts';
/** UI fixtures only. Production schedules, standings and progression come from the API. */
export type LiveFormat = 'king' | 'americano' | 'pairs';
export interface LiveMatch {
    id: string;
    court: number;
    ids: number[];
    score: number;
    other: number;
    label?: string;
    homeLabel?: string;
    awayLabel?: string;
}
export interface LiveRound {
    number: number;
    title: string;
    phase: 'calibration' | 'points' | 'qualification' | 'playoff';
    matches: LiveMatch[];
}
export interface LiveStanding {
    id: number;
    name: string;
    points: number;
    bonus: number;
    wins: number;
    games: number;
    against: number;
}
export const liveConfig = {
    king: { title: 'Король корта', level: 'C7', eventId: 'king', date: '28 октября', time: '19:00', total: 21, rounds: 9, current: 5, unit: 'очков', intro: 'Индивидуальный рейтинг · партнёры меняются', calibration: 3 },
    americano: { title: 'Американо', level: 'C6 / C5', eventId: 'americano', date: '31 октября', time: '14:00', total: 15, rounds: 19, current: 4, unit: 'очков', intro: '20 игроков · 19 раундов · каждый с каждым в паре', calibration: 0 },
    pairs: { title: 'Американо · пары', level: 'C7', eventId: 'pairs', date: '30 октября', time: '19:00', total: 0, rounds: 6, current: 2, unit: 'геймов', intro: '10 постоянных пар · квалификация и плей-офф', calibration: 0 }
} as const;
function result(round: number, court: number, total: number) { const a = Math.floor(total / 2) + 1 + (round + court) % 4; return { score: a, other: total - a }; }
function kingRounds(): LiveRound[] {
    let groups = Array.from({ length: 5 }, (_, court) => Array.from({ length: 4 }, (_, p) => court * 4 + p));
    const rounds: LiveRound[] = [];
    for (let round = 1; round <= 9; round++) {
        const matches = groups.map((ids, court) => ({ id: `king-${round}-${court + 1}`, court: court + 1, ids, ...result(round, court, 21) }));
        rounds.push({ number: round, title: `Раунд ${round}`, phase: round <= 3 ? 'calibration' : 'points', matches });
        const winners = groups.map(ids => ids.slice(0, 2)), losers = groups.map(ids => ids.slice(2));
        groups = Array.from({ length: 5 }, (_, court) => {
            const a = court === 0 ? winners[0] : losers[court - 1];
            const b = court === 4 ? losers[4] : winners[court + 1];
            return [a[0], b[0], a[1], b[1]];
        });
    }
    return rounds;
}
function americanoRounds(): LiveRound[] {
    let circle = Array.from({ length: 20 }, (_, i) => i);
    const rounds: LiveRound[] = [];
    for (let round = 1; round <= 19; round++) {
        const partners = Array.from({ length: 10 }, (_, i) => [circle[i], circle[19 - i]]);
        rounds.push({ number: round, title: `Раунд ${round}`, phase: 'points', matches: Array.from({ length: 5 }, (_, court) => ({ id: `americano-${round}-${court + 1}`, court: court + 1, ids: [...partners[court * 2], ...partners[court * 2 + 1]], ...result(round, court, 15) })) });
        circle = [circle[0], circle[19], ...circle.slice(1, 19)];
    }
    return rounds;
}
const kingFixture = kingRounds();
const americanoFixture = americanoRounds();
function teamIds(team: number) { return [team * 2, team * 2 + 1]; }
const qualification: LiveRound[] = [
    { number: 1, title: 'Квалификация · матч 1', phase: 'qualification', matches: Array.from({ length: 5 }, (_, court) => ({ id: `pairs-1-${court}`, court: court + 1, ids: [...teamIds(court * 2), ...teamIds(court * 2 + 1)], score: 6, other: 2 + court % 3 })) },
    { number: 2, title: 'Квалификация · матч 2', phase: 'qualification', matches: [[0, 3], [2, 5], [4, 7], [6, 9], [8, 1]].map(([a, b], court) => ({ id: `pairs-2-${court}`, court: court + 1, ids: [...teamIds(a), ...teamIds(b)], score: court % 2 ? 4 : 6, other: court % 2 ? 6 : 3 })) }
];
export function pairRanking(completed = 2): LiveStanding[] {
    const rows = Array.from({ length: 10 }, (_, id) => ({ id, name: teamIds(id).map(x => names[x]).join(' / '), points: 0, bonus: 0, wins: 0, games: 0, against: 0 }));
    for (const round of qualification.slice(0, completed))
        for (const m of round.matches) {
            for (const [id, score, against] of [[Math.floor(m.ids[0] / 2), m.score, m.other], [Math.floor(m.ids[2] / 2), m.other, m.score]]) {
                rows[id].points += score;
                rows[id].against += against;
                rows[id].wins += Number(score > against);
                rows[id].games++;
            }
        }
    return rows.sort((a, b) => b.wins - a.wins || (b.points - b.against) - (a.points - a.against) || b.points - a.points || a.id - b.id);
}
function pairRounds(): LiveRound[] {
    const seeds = pairRanking().map(r => r.id);
    const game = (round: number, court: number, a: number, b: number, score = 6, other = 3): LiveMatch => ({ id: `pairs-${round}-${court}`, court, ids: [...teamIds(a), ...teamIds(b)], score, other });
    const playIn: LiveRound = { number: 3, title: '1/8 финала · play-in', phase: 'playoff', matches: [game(3, 1, seeds[6], seeds[9]), game(3, 2, seeds[7], seeds[8], 6, 4)] };
    const qf: LiveRound = { number: 4, title: '1/4 финала', phase: 'playoff', matches: [game(4, 1, seeds[0], seeds[7]), game(4, 2, seeds[1], seeds[6], 6, 2), game(4, 3, seeds[2], seeds[5], 6, 4), game(4, 4, seeds[3], seeds[4], 3, 6)] };
    const sf: LiveRound = { number: 5, title: '1/2 финала', phase: 'playoff', matches: [game(5, 1, seeds[0], seeds[4]), game(5, 2, seeds[1], seeds[2], 6, 4)] };
    const final: LiveRound = { number: 6, title: 'Финал', phase: 'playoff', matches: [game(6, 1, seeds[0], seeds[1], 6, 4)] };
    return [...qualification, playIn, qf, sf, final];
}
const pairsFixture = pairRounds();
export function fixtureRounds(format: LiveFormat): LiveRound[] { return format === 'king' ? kingFixture : format === 'americano' ? americanoFixture : pairsFixture; }
export function courtBonus(court: number, won: boolean, round: number) { return round <= 3 ? 0 : won ? Math.max(0, 4 - court) : court <= 2 ? 1 : 0; }
export function individualRanking(format: 'king' | 'americano', completed: number): LiveStanding[] {
    const rows = names.map((name, id) => ({ id, name, points: 0, bonus: 0, wins: 0, games: 0, against: 0 }));
    for (const round of fixtureRounds(format).slice(0, completed))
        for (const m of round.matches)
            m.ids.forEach((id, j) => {
                const score = j < 2 ? m.score : m.other, against = j < 2 ? m.other : m.score, won = score > against;
                rows[id].points += score;
                rows[id].against += against;
                rows[id].wins += Number(won);
                rows[id].games++;
                rows[id].bonus += format === 'king' ? courtBonus(m.court, won, round.number) : 0;
            });
    return rows.sort((a, b) => (b.points + b.bonus) - (a.points + a.bonus) || b.wins - a.wins || a.name.localeCompare(b.name, 'ru'));
}
/** Backward-compatible examples used by the original pilot and its checks. */
export function roundMatches(round: number) { return kingFixture[Math.max(0, Math.min(round - 1, 8))].matches; }
export function ranking() { return individualRanking('king', 3); }

/** Completed matches only; score is always from the selected player's side. */
export function playerMatches(format: LiveFormat, playerId: number, completed: number) {
    return fixtureRounds(format).slice(0, completed).flatMap(round => round.matches.filter(m => m.ids.includes(playerId)).map(match => {
        const first = match.ids.indexOf(playerId) < 2;
        const team = first ? match.ids.slice(0, 2) : match.ids.slice(2);
        const opponents = first ? match.ids.slice(2) : match.ids.slice(0, 2);
        const score = first ? match.score : match.other, against = first ? match.other : match.score;
        const bonus = format === 'king' ? courtBonus(match.court, score > against, round.number) : 0;
        return { id: match.id, round: round.number, title: round.title, phase: round.phase, court: match.court, team, opponents, score, against, bonus, won: score > against };
    }));
}
export function pairStats(pairId: number, completed: number) {
    const matches = playerMatches('pairs', pairId * 2, Math.min(completed, 2));
    const setsWon = matches.filter(m => m.won).length, setsLost = matches.length - setsWon;
    const gamesWon = matches.reduce((n, m) => n + m.score, 0), gamesLost = matches.reduce((n, m) => n + m.against, 0);
    return { matches, setsWon, setsLost, gamesWon, gamesLost, setDifference: setsWon - setsLost, gameDifference: gamesWon - gamesLost, matchesPlayed: matches.length };
}
