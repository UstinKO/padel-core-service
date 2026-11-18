import { randomUUID } from 'node:crypto';
import { actor, adminPath, assert, clickAction, evidence, expect, formPost, input,
  jsonPost, matches, normalized, playoff, ready, snapshot, successful } from './support.mjs';

export async function security(admin, manager, scenario) {
  const id = scenario.tournamentId;
  const before = await snapshot(admin, id);
  const foreign = await actor('pilot-foreign@example.test');
  const anonymous = await actor();
  await anonymous.page.goto(input.origin + '/login');
  assert.equal((await manager.request.get(input.origin + adminPath(id))).status(), 200);
  assert.equal((await foreign.request.get(input.origin + adminPath(id))).status(), 403);
  const redirect = await anonymous.request.get(input.origin + adminPath(id), { maxRedirects: 0 });
  assert.equal(redirect.status(), 302);
  assert.match(redirect.headers().location, /\/login/);
  const match = matches(before)[0];
  const path = `/tournaments/team-playoff/api/qual-matches/${match.id}/result`;
  assert.equal((await jsonPost(foreign, path, { team1Games: 6, team2Games: 3 })).status(), 403);
  assert.equal((await manager.request.post(input.origin + path, { data: { team1Games: 6, team2Games: 3 } })).status(), 403);
  const after = await snapshot(admin, id);
  assert.equal(after.metadata.conductVersion, before.metadata.conductVersion);
  assert.deepEqual(normalized(after), normalized(before));
  assert.equal((await anonymous.request.get(input.origin + `/api/public/events/${id}`)).status(), 200);
  assert.equal((await snapshot(anonymous, id)).metadata.conductVersion, before.metadata.conductVersion);
  await foreign.context.close();
  await anonymous.context.close();
  return { ownAdmin: 200, foreignAdmin: 403, anonymousAdmin: 302, missingCsrf: 403,
    foreignMutation: 403, publicCard: 200, unchanged: true };
}

export async function staleAndRegenerate(admin, manager, scenario) {
  const id = scenario.tournamentId;
  const before = await snapshot(admin, id);
  const version = before.metadata.conductVersion;
  const stale = await formPost(manager, adminPath(id) + '/regenerate-playoff', { expectedConductVersion: version - 1 });
  assert.equal(stale.status(), 302);
  assert.equal(stale.headers()['x-conduct-stale'], 'true');
  assert.equal((await snapshot(admin, id)).metadata.conductVersion, version);
  const match = ready(before)[0];
  const wrong = await jsonPost(manager,
    `/tournaments/team-playoff/api/playoff-matches/${match.id}/result?expectedTeam1=-1&expectedTeam2=-2`,
    { team1Games: 6, team2Games: 3 });
  assert.equal(wrong.status(), 200);
  const refusal = await wrong.json();
  assert.equal(refusal.success, false);
  assert.equal(refusal.message,
    'Состояние турнира изменилось. Обновите страницу и проверьте актуальную версию.');
  const afterRefusal = await snapshot(admin, id);
  assert.equal(afterRefusal.metadata.conductVersion, version);
  assert.deepEqual(normalized(afterRefusal), normalized(before), 'Отказ сохраняет сетку и результаты');
  await clickAction(manager, id, 'regenerate-playoff');
  assert.deepEqual(normalized(await snapshot(admin, id)), normalized(before));
  return { staleVersion: { status: 302, staleHeader: true }, stalePair: { status: 200, success: false },
    unchangedAfterRefusal: true, regeneratePreservedPairs: true };
}

function watch(page, topic) {
  const count = { sent: 0, received: 0 };
  page.on('websocket', socket => {
    socket.on('framesent', frame => { if (String(frame.payload).includes(topic)) count.sent++; });
    socket.on('framereceived', frame => { if (String(frame.payload).includes(topic)) count.received++; });
  });
  return count;
}

export async function realtimeAndRestore(admin, manager, scenario) {
  const id = scenario.tournamentId;
  const before = await snapshot(admin, id);
  const first = ready(before)[0];
  const legacy = await actor();
  const react = await actor('preview-admin@example.test');
  const legacyFrames = watch(legacy.page, `/topic/team-playoff/${id}`);
  const publicFrames = watch(react.page, `/topic/public/live/${id}`);
  await legacy.page.goto(input.origin + `/tournaments/team-playoff/${id}`);
  await react.page.goto(input.origin + `/preview/tournaments/team-playoff/${id}?tab=schedule`);
  await expect.poll(() => legacyFrames.sent).toBeGreaterThan(0);
  await expect.poll(() => publicFrames.sent).toBeGreaterThan(0);
  await manager.page.goto(input.origin + `/admin/tournaments/${id}/conduct-history`);
  const previous = await manager.page.locator('a[href*="version="]').first().getAttribute('href');
  assert.ok(previous, 'В истории есть сохранённый снимок до результата');
  const key = { 'Idempotency-Key': randomUUID() };
  const secondClient = await actor('pilot-manager@example.test');
  const scorePath = `/tournaments/team-playoff/api/playoff-matches/${first.id}/result?expectedTeam1=${scenario.teamIds[first.first.number - 1]}&expectedTeam2=${scenario.teamIds[first.second.number - 1]}`;
  const attempts = await Promise.all([manager, secondClient].map(user => jsonPost(user, scorePath,
    { team1Games: 6, team2Games: 3 }, key)));
  const outcomes = await Promise.all(attempts.map(response => response.json()));
  assert.deepEqual(outcomes.map(body => body.success).sort(), [false, true], 'Два клиента сохраняют действие только один раз');
  assert.ok(attempts.some(response => response.headers()['x-conduct-replayed'] === 'true'));
  await secondClient.context.close();
  const saved = await snapshot(admin, id);
  const replay = await jsonPost(manager,
    `/tournaments/team-playoff/api/playoff-matches/${first.id}/result?expectedTeam1=${scenario.teamIds[first.first.number - 1]}&expectedTeam2=${scenario.teamIds[first.second.number - 1]}`,
    { team1Games: 6, team2Games: 3 }, key);
  assert.equal(replay.status(), 200);
  assert.equal((await replay.json()).success, false);
  assert.equal(replay.headers()['x-conduct-replayed'], 'true');
  assert.equal((await snapshot(admin, id)).metadata.conductVersion, saved.metadata.conductVersion);
  await expect.poll(() => legacyFrames.received).toBeGreaterThan(0);
  await expect.poll(() => publicFrames.received).toBeGreaterThan(0);
  await expect(react.page.locator(`#match-${first.id} .live-side strong`).first()).toHaveText('6');
  await expect(react.page.locator(`#match-${first.id} .live-side strong`).last()).toHaveText('3');
  await expect(legacy.page.locator('#bracketContent .score-display')).toHaveCount(1);
  await evidence(react, 'realtime-react');
  await manager.page.goto(input.origin + previous);
  const restore = manager.page.locator('form[action$="/restore"]');
  await expect(restore.locator('button')).toBeVisible();
  const response = manager.page.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/restore'));
  await restore.locator('button').click();
  assert.equal((await response).status(), 302);
  assert.deepEqual(normalized(await snapshot(admin, id)), normalized(before), 'История восстанавливает сетку и результат');
  await legacy.context.close();
  await react.context.close();
  return { legacySubscription: true, publicSubscription: true, legacyMessage: true,
    publicInvalidation: true, bothViewsUpdated: true, repeated: { status: 200, success: false, replayHeader: true },
    concurrentSameKey: { accepted: 1, rejected: 1 }, restorePreservedLinksAndResult: true };
}

export async function qualificationMatch(manager, scenario, first, second, score = 2) {
  const created = await successful(manager, `/tournaments/team-playoff/api/${scenario.tournamentId}/qual-matches`,
    { team1Id: scenario.teamIds[first], team2Id: scenario.teamIds[second], courtNumber: 4 });
  const id = created.body.match.id;
  await successful(manager, `/tournaments/team-playoff/api/qual-matches/${id}/result`, { team1Games: 6, team2Games: score });
  return id;
}

export async function qualificationBoundary(admin, manager, scenario) {
  const id = scenario.tournamentId;
  await clickAction(manager, id, 'init-qualification');
  await manager.page.goto('about:blank');
  for (const index of [0, 2, 4]) {
    await qualificationMatch(manager, scenario, index, index + 1);
    await qualificationMatch(manager, scenario, index, index + 1);
  }
  await qualificationMatch(manager, scenario, 6, 7);
  await manager.page.goto(input.origin + adminPath(id));
  await expect(manager.page.locator('form[action$="/init-playoff"]')).toHaveCount(0);
  const before = await snapshot(admin, id);
  assert.equal(before.metadata.phase, 'QUALIFICATION');
  assert.equal(playoff(before).length, 0, 'При незавершённой квалификации восьми пар сетки ещё нет');
  const refusal = await formPost(manager, adminPath(id) + '/init-playoff', {});
  assert.equal(refusal.status(), 302);
  const after = await snapshot(admin, id);
  assert.equal(after.metadata.conductVersion, before.metadata.conductVersion);
  assert.deepEqual(after.rounds, before.rounds, 'Прямой POST не обходит запрет ранней сетки');
  assert.deepEqual(after.ranking, before.ranking);
  await evidence(manager, 'qualification-boundary');
  const last = await successful(manager, `/tournaments/team-playoff/api/${id}/qual-matches`,
    { team1Id: scenario.teamIds[6], team2Id: scenario.teamIds[7], courtNumber: 4 });
  const matchId = last.body.match.id;
  await manager.page.goto(input.origin + adminPath(id));
  const start = manager.page.locator('#playoffStartSection form[action$="/init-playoff"] button');
  await expect(start).toHaveCount(0);
  const marker = await manager.page.evaluate(() => window.qualificationPageMarker = crypto.randomUUID());
  await manager.page.locator(`#qg1-${matchId}`).fill('6');
  await manager.page.locator(`#qg2-${matchId}`).fill('4');
  await manager.page.locator(`#qmatch-${matchId} button[onclick="submitQualScore(this)"]`).click();
  await expect(start).toBeVisible();
  assert.equal(await manager.page.evaluate(() => window.qualificationPageMarker), marker,
    'Кнопка старта появилась в той же странице без навигации');
  const started = manager.page.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/init-playoff'));
  await start.click();
  assert.equal((await started).status(), 302);
  await expect.poll(async () => (await snapshot(admin, id)).metadata.phase).toBe('PLAYOFF');
  const filled = await snapshot(admin, id);
  const quarters = playoff(filled).filter(match => match.stage === 'QUARTER_FINAL');
  assert.equal(quarters.length, 4);
  assert.deepEqual(quarters.flatMap(match => [match.first.number, match.second.number]).sort((a, b) => a - b),
    [1, 2, 3, 4, 5, 6, 7, 8], 'После квалификации все восемь пар посеяны ровно один раз');
  return { filled: normalized(filled) };
}

export async function noShow(admin, manager, scenario) {
  const id = scenario.tournamentId;
  const team = scenario.teamIds.at(-1);
  await manager.page.goto(input.origin + adminPath(id));
  const response = manager.page.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith(`/teams/${team}/no-show`));
  await manager.page.locator(`form[action$="/teams/${team}/no-show"] button`).click();
  assert.equal((await response).status(), 302);
  await clickAction(manager, id, 'init-qualification');
  const board = await manager.request.get(input.origin + `/tournaments/team-playoff/api/${id}/court-board`);
  const data = await board.json();
  assert.equal(data.availableTeams.length, 7);
  assert.ok(data.availableTeams.every(item => item.id !== team));
  await manager.page.goto('about:blank');
  for (const [first, second] of [[0, 1], [2, 3], [4, 5], [1, 6], [3, 5], [0, 4], [2, 6]]) {
    await qualificationMatch(manager, scenario, first, second);
  }
  const completed = await manager.request.get(input.origin + `/tournaments/team-playoff/api/${id}/court-board`);
  assert.equal((await completed.json()).teamGroups.qualificationDone.length, 7);
  await clickAction(manager, id, 'init-playoff');
  const live = await snapshot(admin, id);
  const seeded = new Set(playoff(live).flatMap(match => [match.first.number, match.second.number]).filter(Boolean));
  assert.equal(seeded.size, 4, 'При семи активных парах действующее правило берёт четыре лучшие');
  assert.ok(!seeded.has(8));
  return { activeTeams: 7, absentNumber: 8, excludedFromAvailableAndSeeding: true,
    publicRankingContainsAbsent: live.ranking.some(item => item.id === team), initial: normalized(live) };
}
