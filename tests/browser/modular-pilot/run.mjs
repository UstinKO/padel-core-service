import { readFile } from 'node:fs/promises';
import { actor, assert, browser, clickAction, csrf, evidence, finish, input, normalized,
  snapshot, write } from './support.mjs';
import { early, noShow, realtimeAndRestore, security, staleAndRegenerate } from './checks.mjs';

const report = { sourceRevision: input.sourceRevision, jarSha256: input.jarSha256,
  automated: true, manual: 'NOT_RUN', cases: [],
  knownDefects: ['https://github.com/UstinKO/padel-core-service/issues/1067'] };
const actual = {};
try {
  const admin = await actor('preview-admin@example.test');
  const manager = await actor('pilot-manager@example.test');
  for (const key of ['qualified-8', 'qualified-9', 'qualified-16']) {
    const scenario = input.fixtures.scenarios.find(item => item.key === key);
    const id = scenario.tournamentId;
    assert.equal((await snapshot(admin, id)).ranking.length, scenario.teamCount);
    if (key === 'qualified-8') {
      report.security = await security(admin, manager, scenario);
      console.log('Доступ своего/чужого клуба, anonymous и CSRF: проверены');
    }
    await clickAction(manager, id, 'init-playoff');
    if (key === 'qualified-8') {
      report.stale = await staleAndRegenerate(admin, manager, scenario);
      console.log('Stale pair/version и пересборка: проверены');
      report.realtime = await realtimeAndRestore(admin, manager, scenario);
      console.log('Два зрителя, WS, повтор и восстановление: проверены');
    }
    const initial = await snapshot(admin, id);
    actual[key] = { initial: normalized(initial), ...await finish(manager, scenario, { button: true }) };
    await manager.page.goto(input.origin + `/tournaments/team-playoff/${id}`);
    await evidence(manager, key);
    const reopened = await snapshot(admin, id);
    assert.deepEqual(normalized(reopened), actual[key].final, 'Повторное открытие сохраняет результат');
    report.cases.push({ key, result: 'PASS', knownDefect: key === 'qualified-9' ? 1067 : null });
    console.log(key + ': завершён и повторно прочитан');
  }
  const roster = input.fixtures.scenarios.find(item => item.key === 'roster-8');
  actual.early = await early(admin, manager, roster);
  actual.early.finished = await finish(manager, roster);
  console.log('Ранняя сетка/TBD/backfill и финал: проверены');
  const reset = await admin.request.post(input.origin + '/api/internal/ui-preview/playoff-pilot/reset', {
    headers: await csrf(admin),
  });
  assert.equal(reset.status(), 200);
  const fresh = await reset.json();
  report.noShow = await noShow(admin, manager, fresh.scenarios.find(item => item.key === 'roster-8'));
  actual.noShow = report.noShow.initial;
  delete report.noShow.initial;
  console.log('NO_SHOW до квалификации: проверен');
  await write('actual', actual);
  if (input.record) await write('expectations-candidate', actual);
  else {
    const expected = JSON.parse(await readFile('docs/modular-monolith/phase-1/browser-expectations.json', 'utf8'));
    assert.deepEqual(actual, expected, 'Исходные пары/слоты/результаты сохранены');
  }
  report.result = 'PASS';
} catch (error) {
  report.result = 'FAIL';
  const authorization = Buffer.from('preview:' + input.gatewayPassword).toString('base64');
  report.failure = [input.password, input.gatewayPassword, authorization].reduce(
    (message, secret) => message.replaceAll(secret, '[REDACTED]'), error.message);
  console.error(report.failure);
  process.exitCode = 1;
} finally {
  await write('report', report);
  await browser.close();
}
