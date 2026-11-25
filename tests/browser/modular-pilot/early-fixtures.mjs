import { adminPath, assert, clickAction, formPost, input, successful, write } from './support.mjs';

export const qualificationPlans = {
  9: [[1, 2, 6, 5], [2, 3, 5, 6], [3, 4, 6, 5], [4, 5, 5, 6],
    [5, 6, 6, 0], [6, 7, 0, 6], [7, 8, 6, 0], [8, 9, 0, 6]],
  16: [[1, 2, 4, 6], [2, 3, 2, 6], [3, 4, 6, 3], [4, 5, 0, 6], [5, 6, 6, 1],
    [6, 7, 2, 6], [7, 8, 6, 4], [8, 9, 6, 2], [9, 10, 1, 6], [10, 11, 6, 2],
    [11, 12, 0, 6], [12, 13, 6, 5], [13, 14, 4, 6], [14, 15, 4, 6], [15, 16, 6, 3]],
};

export async function readApi(user, path) {
  const response = await user.request.get(input.origin + path);
  assert.equal(response.status(), 200, 'Чтение своего турнира доступно');
  return response.json();
}

export const board = (user, id) => readApi(user, `/tournaments/team-playoff/api/${id}/court-board`);
export const earlyState = (user, id) => readApi(user, `/tournaments/team-playoff/api/${id}/early-playoff`);

export async function createScenario(admin, manager, count, key) {
  assert.equal(new URL(input.origin).hostname, '127.0.0.1');
  assert.equal((await readApi(admin, '/api/internal/ui-preview')).environment, 'preview');
  const template = input.fixtures.scenarios.find(item => item.key === 'qualified-16');
  await manager.page.goto(input.origin + `/admin/tournaments/${template.tournamentId}/edit`);
  const form = await manager.page.locator('form[action$="/edit"]').evaluate(element =>
    Object.fromEntries(new FormData(element).entries()));
  delete form.id;
  form.nombre = 'QA 1045 ' + key;
  form.categoriaNivel = 'C9';
  form.cupoMax = String(count);
  const created = await formPost(manager, '/admin/tournaments', form);
  assert.equal(created.status(), 302);
  const location = new URL(created.headers().location, input.origin);
  assert.equal(location.origin, input.origin);
  assert.match(location.pathname, /^\/admin\/tournaments\/\d+$/);
  const id = Number(location.pathname.split('/').at(-1));
  assert.ok(input.fixtures.scenarios.every(item => item.tournamentId !== id));
  for (let index = 0; index < count; index++) {
    const added = await formPost(manager, adminPath(id) + '/teams/add', {
      player1Id: String(input.fixtures.accounts.playerIds[index * 2]),
      player2Id: String(input.fixtures.accounts.playerIds[index * 2 + 1]),
      hasPaid: 'true', attended: 'true', registrationSource: 'ADMIN_MANUAL',
    });
    assert.equal(added.status(), 302);
  }
  const ranking = await readApi(manager, `/tournaments/team-playoff/api/${id}/ranking`);
  const teams = [...ranking.ranking].sort((a, b) => a.teamNumber - b.teamNumber);
  assert.deepEqual(teams.map(team => team.teamNumber), Array.from({ length: count }, (_, index) => index + 1));
  return { key, tournamentId: id, teamCount: count, teamIds: teams.map(team => team.id) };
}

export async function prepareEarly(admin, manager, count, key = 'early-' + count) {
  const scenario = await createScenario(admin, manager, count, key);
  const id = scenario.tournamentId;
  await clickAction(manager, id, 'init-qualification');
  await manager.page.goto('about:blank');
  for (const [first, second, left, right] of qualificationPlans[count]) {
    const created = await successful(manager, `/tournaments/team-playoff/api/${id}/qual-matches`, {
      team1Id: scenario.teamIds[first - 1], team2Id: scenario.teamIds[second - 1], courtNumber: 1,
    });
    await successful(manager, `/tournaments/team-playoff/api/qual-matches/${created.body.match.id}/result`,
      { team1Games: left, team2Games: right });
  }
  const last = await successful(manager, `/tournaments/team-playoff/api/${id}/qual-matches`, {
    team1Id: scenario.teamIds[count - 1], team2Id: scenario.teamIds[0], courtNumber: 1,
  });
  scenario.pendingQualificationId = last.body.match.id;
  await write('prepared-' + key, scenario);
  return scenario;
}
