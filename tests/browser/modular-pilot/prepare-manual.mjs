import { writeFile } from 'node:fs/promises';
import { actor, adminPath, assert, browser, clickAction, input, successful, write } from './support.mjs';
import { prepareQualificationBoundary } from './checks.mjs';
import { createScenario, prepareEarly } from './early-fixtures.mjs';

try {
  const admin = await actor('preview-admin@example.test');
  const manager = await actor('pilot-manager@example.test');
  const scenarios = Object.fromEntries(input.fixtures.scenarios.map(scenario => [scenario.key, scenario]));
  const boundary = scenarios['roster-8'];
  await prepareQualificationBoundary(manager, boundary);
  const last = await successful(manager, `/tournaments/team-playoff/api/${boundary.tournamentId}/qual-matches`, {
    team1Id: boundary.teamIds[6], team2Id: boundary.teamIds[7], courtNumber: 4,
  });
  boundary.pendingQualificationId = last.body.match.id;
  for (const count of [9, 16]) {
    scenarios['manual-early-' + count] = await prepareEarly(admin, manager, count, 'manual-early-' + count);
  }
  scenarios['manual-no-show'] = await createScenario(admin, manager, 8, 'manual-no-show');
  const technical = await createScenario(admin, manager, 8, 'manual-technical');
  await clickAction(manager, technical.tournamentId, 'init-qualification');
  await manager.page.goto('about:blank');
  const plan = [[1, 2, 6, 0], [3, 4, 6, 1], [5, 6, 6, 2], [7, 8, 6, 3],
    [1, 3, 6, 4], [5, 7, 6, 5], [2, 4, 6, 0], [6, 8, 6, 1]];
  for (const [left, right, first, second] of plan) {
    const added = await successful(manager, `/tournaments/team-playoff/api/${technical.tournamentId}/qual-matches`, {
      team1Id: technical.teamIds[left - 1], team2Id: technical.teamIds[right - 1], courtNumber: 1,
    });
    await successful(manager, `/tournaments/team-playoff/api/qual-matches/${added.body.match.id}/result`,
      { team1Games: first, team2Games: second });
  }
  await clickAction(manager, technical.tournamentId, 'init-playoff');
  scenarios[technical.key] = technical;
  const cases = {
    M01: 'qualified-8', M02: 'qualified-9', M03: 'qualified-16', M04A: 'roster-8',
    M04B: 'manual-early-9', M04C: 'manual-early-16', M05: 'manual-no-show',
    M06: 'manual-technical', M07: 'manual-technical', M08: 'manual-technical', M09: 'qualified-8',
  };
  const planOutput = { sourceRevision: input.sourceRevision, jarSha256: input.jarSha256,
    manual: 'NOT_RUN', preparedAutomatically: true, cases: {} };
  for (const [key, scenarioKey] of Object.entries(cases)) {
    const scenario = scenarios[scenarioKey];
    assert.ok(scenario);
    const id = scenario.tournamentId;
    planOutput.cases[key] = { ...scenario, result: 'NOT_RUN', adminPath: adminPath(id),
      publicPath: `/tournaments/team-playoff/${id}?lang=ru`,
      reactPath: `/preview/tournaments/team-playoff/${id}?tab=schedule`,
      historyPath: `/admin/tournaments/${id}/conduct-history` };
  }
  await write('manual-plan', planOutput);
  const lines = Object.entries(planOutput.cases).map(([key, value]) =>
    `| ${key} | [Управление](${input.origin}${value.adminPath}?lang=ru) | [Просмотр](${input.origin}${value.publicPath}) | NOT_RUN |`);
  await writeFile('target/modular-pilot/manual-plan.md',
    `# Ручной стенд #1045\n\nАртефакт: ${input.jarSha256}. Подготовка данных выполнена автоматически; ручные проверки ещё не выполнялись.\n\n`
    + '| Кейс | Управление | Просмотр | Ручной итог |\n|---|---|---|---|\n' + lines.join('\n') + '\n');
  console.log('Подготовлены независимые данные M01–M09; ручной результат NOT_RUN');
} finally {
  await browser.close();
}
