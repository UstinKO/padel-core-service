import { adminPath, assert, evidence, expect, input, jsonPost, matches, playoff, snapshot, successful } from './support.mjs';
import { board, earlyState, prepareEarly } from './early-fixtures.mjs';

const pairKey = (first, second) => [first, second].sort().join(':');

async function confirmButton(user, scenario, pair, court) {
  await user.page.goto(input.origin + adminPath(scenario.tournamentId));
  const card = user.page.locator(`#earlySuggestions [data-match="${pairKey(pair.first.id, pair.second.id)}"]`);
  await expect(card).toBeVisible();
  await card.locator('select').selectOption(court === null ? '' : String(court));
  const response = user.page.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/early-playoff'));
  await card.locator('button[type="submit"]').click();
  const saved = await response;
  assert.equal(saved.status(), 200);
  const result = await saved.json();
  assert.equal(result.success, true);
  await evidence(user, scenario.key + (court === null ? '-queued' : '-confirmed'));
  await user.page.goto('about:blank');
  return result.matchId;
}

async function result(user, match, firstWins = true) {
  const query = new URLSearchParams({ expectedTeam1: match.team1Id, expectedTeam2: match.team2Id });
  return successful(user, `/tournaments/team-playoff/api/playoff-matches/${match.id}/result?${query}`,
    { team1Games: firstWins ? 6 : 3, team2Games: firstWins ? 3 : 6 });
}

async function playQueued(user, scenario, match, firstWins = true) {
  await successful(user, `/tournaments/team-playoff/api/matches/${match.id}/assign-court`, { courtNumber: 1 });
  await result(user, match, firstWins);
}

async function finishQueued(admin, manager, scenario) {
  for (let turn = 0; turn < 32; turn++) {
    const current = await board(manager, scenario.tournamentId);
    const active = current.courts.map(court => court.currentMatch).find(match => match?.playoffStage);
    if (active) await result(manager, active);
    else {
      const queued = current.queue.find(match => match.playoffStage);
      if (!queued) {
        const final = await snapshot(admin, scenario.tournamentId);
        assert.equal(final.metadata.phase, 'FINISHED');
        assert.equal(playoff(final).filter(match => match.completed).length, scenario.teamCount - 1);
        assert.equal(final.ranking.filter(team => team.tournamentStatus === 'CHAMPION').length, 1);
        return;
      }
      await playQueued(manager, scenario, queued);
    }
  }
  throw new Error('Ранняя сетка не завершилась за 32 операции');
}

async function earlySemifinal(admin, manager, scenario) {
  let current = await board(manager, scenario.tournamentId);
  while (current.queue.some(match => match.playoffStage === 'ROUND_OF_16')) {
    await playQueued(manager, scenario, current.queue.find(match => match.playoffStage === 'ROUND_OF_16'));
    current = await board(manager, scenario.tournamentId);
  }
  const quarters = current.queue.filter(match => match.playoffStage === 'QUARTER_FINAL');
  assert.equal(quarters.length, 4);
  const history = matches(await snapshot(admin, scenario.tournamentId)).filter(match => match.completed);
  const previous = new Set(history.map(match => pairKey(match.first.number, match.second.number)));
  const options = [quarters[0].team1Id, quarters[0].team2Id].flatMap(first =>
    [quarters[1].team1Id, quarters[1].team2Id].map(second => [first, second]));
  const chosen = options.find(([first, second]) => !previous.has(pairKey(
    scenario.teamIds.indexOf(first) + 1, scenario.teamIds.indexOf(second) + 1)));
  assert.ok(chosen, 'Есть два победителя без повторной встречи');
  await playQueued(manager, scenario, quarters[0], chosen[0] === quarters[0].team1Id);
  await playQueued(manager, scenario, quarters[1], chosen[1] === quarters[1].team1Id);
  const available = await earlyState(manager, scenario.tournamentId);
  assert.equal(available.semifinal, true);
  const pair = available.matches.find(item => pairKey(item.first.id, item.second.id) === pairKey(...chosen));
  assert.ok(pair);
  const id = await confirmButton(manager, scenario, pair, 2);
  for (const quarter of quarters.slice(2)) await playQueued(manager, scenario, quarter);
  const after = await board(manager, scenario.tournamentId);
  const retained = after.courts.find(court => court.currentMatch?.id === id);
  assert.equal(retained.courtNumber, 2);
  assert.deepEqual([retained.currentMatch.team1Id, retained.currentMatch.team2Id], [pair.first.id, pair.second.id]);
  assert.equal(after.queue.filter(match => match.playoffStage === 'SEMI_FINAL').length, 1);
  return { confirmedBeforeOtherQuarterfinals: true, pairAndCourtPreserved: true };
}

export async function verifyEarly(admin, manager, count) {
  const scenario = await prepareEarly(admin, manager, count);
  const id = scenario.tournamentId;
  const options = await earlyState(manager, id);
  assert.equal(options.available, true);
  assert.equal(options.finishedTeams, count - 2);
  assert.equal(options.remainingMatches, 1);
  assert.ok(options.matches.length >= (count === 9 ? 1 : 2));
  if (count === 9) assert.deepEqual([options.matches[0].first.number, options.matches[0].second.number].sort(), [6, 8]);
  const first = options.matches[0];
  const confirmed = await confirmButton(manager, scenario, first, 2);
  const repeated = await successful(manager, `/tournaments/team-playoff/api/${id}/early-playoff`, {
    firstId: first.second.id, secondId: first.first.id, court: 2, approvedOverride: false,
  });
  assert.equal(repeated.body.matchId, confirmed);
  if (count === 16) await confirmButton(manager, scenario, options.matches[1], null);
  const active = (await board(manager, id)).courts.find(court => court.currentMatch?.id === confirmed).currentMatch;
  await result(manager, active);
  assert.equal((await board(manager, id)).qualificationFinished, false);
  const before = await snapshot(admin, id);
  const invalid = await jsonPost(manager,
    `/tournaments/team-playoff/api/qual-matches/${scenario.pendingQualificationId}/result`, { team1Games: 5, team2Games: 4 });
  assert.equal(invalid.status(), 200);
  assert.equal((await invalid.json()).success, false);
  const unchanged = await snapshot(admin, id);
  assert.equal(unchanged.metadata.conductVersion, before.metadata.conductVersion);
  assert.deepEqual(unchanged.rounds, before.rounds);
  await successful(manager, `/tournaments/team-playoff/api/qual-matches/${scenario.pendingQualificationId}/result`,
    { team1Games: 5, team2Games: 6 });
  const qualified = await board(manager, id);
  assert.equal(qualified.qualificationFinished, true);
  const saved = playoff(await snapshot(admin, id)).find(match => match.id === confirmed);
  assert.deepEqual([saved.first.number, saved.second.number, saved.first.games, saved.second.games],
    [first.first.number, first.second.number, 6, 3]);
  if (count === 9) {
    const recipient = qualified.queue.find(match => match.id === saved.nextMatchId);
    assert.ok(recipient, 'Победитель раннего play-in получил четвертьфинал');
    assert.equal(saved.nextSlot === 1 ? recipient.team1Id : recipient.team2Id, first.first.id);
  }
  const semifinal = count === 16 ? await earlySemifinal(admin, manager, scenario) : null;
  await finishQueued(admin, manager, scenario);
  return { key: scenario.key, tournamentId: id, result: 'PASS', repeatedPairPreservedId: true,
    invalidQualificationRefused: true, lastQualificationPreservedPairAndResult: true,
    linkedWinner: count === 9, semifinal, finished: true };
}
