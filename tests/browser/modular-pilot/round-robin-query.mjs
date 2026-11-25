import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
const sample = JSON.parse(await readFile('src/test/resources/contracts/round-robin-query/points-initial.json', 'utf8'));
const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const browser = await chromium.launch();
const errors = [];
const createContext = async () => {
  const context = await browser.newContext({ httpCredentials: {
    origin: input.origin, username: 'preview', password: input.gatewayPassword,
  } });
  await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
    ? route.continue() : route.abort());
  await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
  return context;
};
const report = { result: 'PASS', cases: [], javaScriptErrors: 0 };

try {
  const anonymous = await createContext();
  const authenticated = await createContext();
  const page = await authenticated.newPage();
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(input.origin + '/login?lang=ru');
  await page.locator('#username').fill('preview-admin@example.test');
  await page.locator('#password').fill(input.password);
  await Promise.all([page.waitForURL(url => url.pathname !== '/login'),
    page.locator('form.auth-form').evaluate(form => form.requestSubmit())]);

  for (const fixture of input.cases) {
    const path = `/tournaments/team-americano/api/${fixture.tournamentId}/ranking`;
    const jsonDenied = await anonymous.request.get(input.origin + path, { headers: { Accept: 'application/json' } });
    assert.equal(jsonDenied.status(), 401);
    const htmlDenied = await anonymous.request.get(input.origin + path, {
      headers: { Accept: 'text/html' }, maxRedirects: 0,
    });
    assert.equal(htmlDenied.status(), 302);
    assert.equal(new URL(htmlDenied.headers().location, input.origin).pathname, '/login');
    const response = await authenticated.request.get(input.origin + path);
    assert.equal(response.status(), 200);
    const body = await response.json();
    assert.deepEqual(Object.keys(body).sort(), Object.keys(sample).sort());
    assert.deepEqual(Object.keys(body.ranking[0]).sort(), Object.keys(sample.ranking[0]).sort());
    assert.equal(body.scoringMode, fixture.mode);
    assert.equal(body.finished, true);
    assert.equal(body.roundRobinComplete, true);
    assert.equal(body.totalTeams, 4);
    assert.equal(body.additionalMatches.length, 1);
    assert.equal(body.finalRanking[0].teamNumber, 3);
    assert.equal(body.ranking[0].setDifference, null);
    assert.equal(body.ranking[0].registrationSource, null);
    const publicPage = await anonymous.newPage();
    publicPage.on('pageerror', error => errors.push(error.message));
    assert.equal((await publicPage.goto(input.origin + `/tournaments/team-americano/${fixture.tournamentId}?lang=ru`)).status(), 200);
    assert.equal(await publicPage.locator('.rr-table-scroll tbody tr').count(), 4);
    assert.equal((await page.goto(input.origin + `/admin/tournaments/${fixture.tournamentId}?lang=ru`)).status(), 200);
    await publicPage.close();
    report.cases.push({ mode: fixture.mode, anonymousJson: 401, anonymousHtml: 302,
      authenticatedJson: 200, anonymousPublicPage: 200, adminCard: 200,
      exactRootAndRowFields: true, finalAndCircleDifferent: true });
  }

  const missing = await authenticated.request.get(input.origin + '/tournaments/team-americano/api/-1375/ranking', {
    headers: { Accept: 'application/json' },
  });
  assert.equal(missing.status(), 500);
  report.missingTournament = { authenticatedStatus: 500, existingUnhandledResourceNotFound: true };
  assert.deepEqual(errors, []);
  await writeFile('target/round-robin-engine/query-browser.json', JSON.stringify(report, null, 2) + '\n');
  console.log('Круговая таблица: поля JSON, авторизация API, публичная страница, админ-карточка и прежняя ошибка — PASS');
} finally {
  await browser.close();
}
