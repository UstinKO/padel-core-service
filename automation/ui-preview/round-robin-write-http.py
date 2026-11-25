#!/usr/bin/env python3
"""Проверяет реальные POST кругового проведения на своей синтетической POINTS-фикстуре."""
import importlib.util
import json
from pathlib import Path
import urllib.error
import urllib.parse
import urllib.request
from uuid import uuid4


def load_acceptance():
    path = Path(__file__).with_name('round-robin-acceptance.py')
    spec = importlib.util.spec_from_file_location('rr_acceptance', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class KeepRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, message, headers, url):
        return None


def post(client, path, payload, *, form=False, csrf=True, headers=None):
    opener = urllib.request.build_opener(KeepRedirect(), urllib.request.HTTPCookieProcessor(client.cookies))
    request_headers = {'Authorization': client.authorization, **(headers or {})}
    if csrf:
        token = next(cookie.value for cookie in client.cookies if cookie.name == 'XSRF-TOKEN')
        request_headers['X-XSRF-TOKEN'] = urllib.parse.unquote(token)
    request_headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
    encoded = urllib.parse.urlencode(payload) if form else json.dumps(payload)
    request = urllib.request.Request(client.origin + path, data=encoded.encode(), headers=request_headers)
    try:
        with opener.open(request, timeout=8) as response:
            return response.status, response.headers, response.read()
    except urllib.error.HTTPError as error:
        with error:
            return error.code, error.headers, error.read()


def assert_redirect(response, admin_path):
    status, headers, _ = response
    assert status == 302, status
    assert urllib.parse.urlsplit(headers['Location']).path == admin_path


def action_count(sql, tournament_id, action):
    return int(sql('SELECT count(*) FROM tournament_conduct_history '
                   f"WHERE tournament_id={tournament_id} AND action='{action}';"))


def check_score_routes(client, sql, tournament_id, admin_path):
    match_id, first, second = map(int, sql(
        'SELECT m.id, m.team1_score, m.team2_score FROM americano_matches_db m '
        'JOIN americano_rounds_db r ON r.id=m.round_id '
        f"WHERE m.tournament_id={tournament_id} AND r.phase IS NULL AND m.status='COMPLETED' "
        'ORDER BY m.id LIMIT 1;').split('|'))
    assert first + second == 32
    before = json.loads(client.request(f'/tournaments/team-americano/api/{tournament_id}/ranking'))
    assert before['scoringMode'] == 'POINTS' and before['totalTeams'] == 6
    original = (first, second)
    result_count = action_count(sql, tournament_id, 'RESULT')
    edits = [score for score in [(18, 14), (16, 16), (14, 18)] if score != original]
    base = '/tournaments/team-americano'
    for prefix, score, form in zip(['/matches', '/api/matches', '/api/round-robin/matches'],
                                   [*edits[:2], original], [True, False, False]):
        response = post(client, f'{base}{prefix}/{match_id}/result',
                        dict(team1Score=score[0], team2Score=score[1]), form=form)
        if form:
            assert_redirect(response, admin_path)
        else:
            assert response[0] == 200 and json.loads(response[2])['success'] is True
        assert sql(f'SELECT team1_score, team2_score FROM americano_matches_db WHERE id={match_id};') == (
            f'{score[0]}|{score[1]}')
    assert json.loads(client.request(f'{base}/api/{tournament_id}/ranking')) == before
    assert action_count(sql, tournament_id, 'RESULT') == result_count + 3


def check_finish_rejections(acceptance, client, sql, fixture, admin_path):
    tournament_id = int(fixture['tournamentId'])
    finish_path = f'{admin_path}/finish'

    def revision():
        return int(sql('SELECT COALESCE(max(id), 0) FROM tournament_conduct_history '
                       f'WHERE tournament_id={tournament_id};'))

    version = revision()
    finish_count = action_count(sql, tournament_id, 'FINISH')
    payload = {'expectedConductVersion': version}
    key = {'Idempotency-Key': str(uuid4())}
    assert_redirect(post(client, finish_path, payload, form=True, headers=key), admin_path)
    assert revision() > version
    assert action_count(sql, tournament_id, 'FINISH') == finish_count + 1
    assert sql(f'SELECT estado FROM tournaments_db WHERE id={tournament_id};') == 'FINALIZADO'
    after_finish = acceptance.persisted(sql, [fixture])

    replay = post(client, finish_path, payload, form=True, headers=key)
    assert_redirect(replay, admin_path)
    assert replay[1]['X-Conduct-Replayed'] == 'true'
    stale = post(client, finish_path, payload, form=True, headers={'Idempotency-Key': str(uuid4())})
    assert_redirect(stale, admin_path)
    assert stale[1]['X-Conduct-Stale'] == 'true'
    assert_redirect(post(client, finish_path, {}, form=True, csrf=False), '/error/403')
    denied = post(client, finish_path, {}, form=True, csrf=False, headers={'Accept': 'application/json'})
    assert denied[0] == 403
    assert acceptance.persisted(sql, [fixture]) == after_finish


def main():
    acceptance = load_acceptance()
    state, client, _, sql = acceptance.environment()
    fixtures = json.loads(acceptance.FIXTURES.read_text())
    if fixtures['instance'] != state['instance']:
        raise ValueError('Фикстуры принадлежат другому preview')
    fixture = next(case for case in fixtures['cases'] if case['key'] == 'extended-points')
    tournament_id = int(fixture['tournamentId'])
    admin_path = f'/tournaments/team-americano/admin/{tournament_id}'
    assert sql(f'SELECT estado FROM tournaments_db WHERE id={tournament_id};') == 'FINALIZADO'
    check_score_routes(client, sql, tournament_id, admin_path)
    check_finish_rejections(acceptance, client, sql, fixture, admin_path)
    report = dict(result='PASS', scoreRoutes=['form', 'legacyJson', 'roundRobinJson'],
                  scoreEditsReverted=True, rankingRestored=True, resultHistoryRows=3, finishHistoryRows=1,
                  finishRedirect=302, replayRedirect=302,
                  staleRedirect=302, noCsrf=403, noCsrfHtmlRedirect='/error/403', rejectedRowsUnchanged=True)
    output = acceptance.preview.ROOT / 'target/round-robin-acceptance/write-http.json'
    output.write_text(json.dumps(report, indent=2) + '\n')
    print('Круговой write-срез: три POST счёта, finish, replay/stale и CSRF — PASS')


if __name__ == '__main__':
    main()
