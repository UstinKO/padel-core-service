import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { setTimeout } from 'node:timers/promises';

const { chromium, expect } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
export { assert, expect };
export const input = JSON.parse(await readFile(process.argv[2], 'utf8'));
export const output = 'target/modular-pilot';
export const browser = await chromium.launch({ headless: true });
const cdn = new Set([
  'https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js',
  'https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js',
]);

export async function actor(email) {
  const context = await browser.newContext({ locale: 'ru-RU', httpCredentials: {
    origin: input.origin, username: 'preview', password: input.gatewayPassword,
  } });
  await context.route('**/*', route => new URL(route.request().url()).origin === input.origin
    || cdn.has(route.request().url()) ? route.continue() : route.abort());
  await context.addCookies([{ name: 'cookieConsent', value: 'rejected', url: input.origin }]);
  const page = await context.newPage();
  page.setDefaultTimeout(12000);
  page.on('dialog', dialog => dialog.accept());
  if (email) {
    await page.goto(input.origin + '/login?lang=ru');
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(input.password);
    await page.locator('form.auth-form button[type="submit"]').click();
    await page.waitForURL(url => url.pathname !== '/login');
  }
  return { context, page, request: context.request };
}

export async function csrf(user) {
  const cookie = (await user.context.cookies()).find(item => item.name === 'XSRF-TOKEN');
  assert.ok(cookie, 'В сессии должна быть CSRF cookie');
  return { 'X-XSRF-TOKEN': decodeURIComponent(cookie.value) };
}

export async function snapshot(user, id) {
  const response = await user.request.get(input.origin + `/api/public/events/${id}/live`);
  assert.equal(response.status(), 200, 'Публичный snapshot доступен');
  assert.match(response.headers()['cache-control'], /no-store/);
  return response.json();
}

export const matches = live => live.rounds.flatMap(round => round.matches);
export const playoff = live => matches(live).filter(match => match.stage !== null);
export const ready = live => playoff(live).filter(match => !match.completed
  && match.status === 'IN_PROGRESS' && match.first.number && match.second.number);
export const adminPath = id => `/tournaments/team-playoff/admin/${id}`;

export async function jsonPost(user, path, data, headers = {}) {
  return user.request.post(input.origin + path, { data, headers: { ...await csrf(user), ...headers } });
}

export async function successful(user, path, data, headers) {
  const response = await jsonPost(user, path, data, headers);
  assert.equal(response.status(), 200, 'API должно ответить 200');
  const body = await response.json();
  assert.equal(body.success, true, `Операция отклонена: ${body.message}`);
  await setTimeout(150);
  return { response, body };
}

export async function formPost(user, path, form) {
  return user.request.post(input.origin + path, { form, headers: await csrf(user), maxRedirects: 0 });
}

export async function clickAction(user, id, action) {
  await user.page.goto(input.origin + adminPath(id));
  const before = (await snapshot(user, id)).metadata.conductVersion;
  if (action === 'init-qualification') await user.page.locator('#qualCourtsInput').fill('8');
  const button = user.page.locator(`form[action$="/${action}"] button[type="submit"]`).first();
  await expect(button).toBeVisible();
  await expect(button).toBeEnabled();
  const response = user.page.waitForResponse(item => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/' + action));
  await button.click();
  assert.equal((await response).status(), 302, 'MVC возвращает redirect');
  await user.page.waitForLoadState('domcontentloaded');
  await expect.poll(async () => (await snapshot(user, id)).metadata.conductVersion,
    { message: `После ${action} должна появиться новая версия состояния` }).not.toBe(before);
  if (action === 'init-playoff') {
    await expect.poll(async () => (await snapshot(user, id)).metadata.phase).toBe('PLAYOFF');
  }
}

export async function score(user, scenario, match, headers) {
  const query = new URLSearchParams({ expectedTeam1: scenario.teamIds[match.first.number - 1],
    expectedTeam2: scenario.teamIds[match.second.number - 1] });
  return successful(user, `/tournaments/team-playoff/api/playoff-matches/${match.id}/result?${query}`,
    { team1Games: 6, team2Games: 3 }, headers);
}

export async function scoreButton(user, scenario, match) {
  await user.page.goto(input.origin + adminPath(scenario.tournamentId));
  await user.page.locator(`#pg1-${match.id}`).fill('6');
  await user.page.locator(`#pg2-${match.id}`).fill('3');
  const result = user.page.waitForResponse(response => response.request().method() === 'POST'
    && new URL(response.url()).pathname.endsWith(`/playoff-matches/${match.id}/result`));
  await user.page.locator(`#pmatch-${match.id} .save-btn[onclick="submitPlayoffScore(this)"]`).click();
  const response = await result;
  assert.equal(response.status(), 200);
  await expect.poll(async () => {
    const saved = matches(await snapshot(user, scenario.tournamentId)).find(item => item.id === match.id);
    return [saved.completed, saved.first.games, saved.second.games];
  }).toEqual([true, 6, 3]);
}

export function normalized(live) {
  const keys = new Map();
  live.rounds.forEach(round => round.matches.forEach(match =>
    keys.set(match.id, `${round.phase}/${round.number}/${match.number}`)));
  return {
    phase: live.metadata.phase,
    format: live.format,
    ranking: (live.ranking ?? []).map(team => [team.teamNumber, team.currentPosition, team.qualStatus,
      team.tournamentStatus, team.eliminatedStage]),
    matches: playoff(live).map(match => ({ key: keys.get(match.id), stage: match.stage,
      pair: [match.first.number, match.second.number], status: match.status,
      games: [match.first.games, match.second.games], winner: match.firstWinner,
      next: match.nextMatchId === null ? null : [keys.get(match.nextMatchId), match.nextSlot] })),
  };
}

export async function finish(user, scenario, { button = false } = {}) {
  const stages = [];
  for (let pass = 0; pass < 8; pass++) {
    const live = await snapshot(user, scenario.tournamentId);
    const current = ready(live);
    if (!current.length) {
      assert.equal(live.metadata.phase, 'FINISHED', 'Турнир дошёл до чемпиона');
      return { stages, final: normalized(live) };
    }
    stages.push(current.map(match => [match.stage, match.first.number, match.second.number]));
    for (let i = 0; i < current.length; i++) {
      if (button && pass === 0 && i === 0) {
        await scoreButton(user, scenario, current[i]);
        await user.page.goto('about:blank');
      }
      else await score(user, scenario, current[i]);
    }
  }
  throw new Error('Турнир не завершился за восемь стадий');
}

export async function evidence(user, name) {
  await user.page.screenshot({ path: `${output}/${name}.png`, fullPage: true });
}

export async function write(name, value) {
  await writeFile(`${output}/${name}.json`, JSON.stringify(value, null, 2) + '\n');
}
