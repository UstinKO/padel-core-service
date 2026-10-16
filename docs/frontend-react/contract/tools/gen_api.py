import json, sys, re, fnmatch

S = sys.argv[1]
ctrl = json.load(open(f'{S}/ctrl.json'))
sys.path.insert(0, S)
import importlib.util
spec = importlib.util.spec_from_file_location('gs', f'{S}/gen_screens.py')
src = open(f'{S}/gen_screens.py').read()
# вытащить JS-словарь и SCREENS без выполнения генерации
ns = {}
exec(src.split('def ctrl_for')[0].replace("S = sys.argv[1]", f"S = '{S}'"), ns)
JS, SCREENS = ns['JS'], ns['SCREENS']
tpl = json.load(open(f'{S}/tpl.json'))

# Какие экраны используют какой JS
screen_js = {}
for sid, title, access, t, *_ in SCREENS:
    for tt in [x.strip() for x in t.split(',')]:
        if tt in tpl:
            for s_ in tpl[tt]['scripts']:
                m = re.match(r"@\{/js/(.*)\}", s_)
                if m:
                    screen_js.setdefault(m.group(1), set()).add(sid)
            for f in tpl[tt]['fetches']:
                screen_js.setdefault('inline:' + sid, set()).add(sid)

# Нормализованные вызовы: (METHOD, path-regex) -> screens
calls = []
def norm(p):
    p = p.split('?')[0].strip('`\'" ')
    p = re.sub(r'\$\{[^}]*\}', '{x}', p)
    return p
for js, items in JS.items():
    for it in items:
        if 'эндпоинта нет' in it:
            continue
        m = re.match(r'(GET|POST|PUT|PATCH|DELETE) (\S+)', it)
        if m:
            calls.append((m.group(1), norm(m.group(2)), screen_js.get(js, set())))
for sid, title, access, t, *_ in SCREENS:
    for tt in [x.strip() for x in t.split(',')]:
        if tt in tpl:
            for f in tpl[tt]['fetches']:
                calls.append(('*', norm(f), {sid}))

def to_re(path):
    return '^' + re.sub(r'\\\{[^}]*\\\}', '[^/]+', re.escape(path)) + '$'

def used_by(verb, path):
    out = set()
    rx = re.compile(to_re(path))
    for m, p, sids in calls:
        pp = re.sub(r'\{[^}]*\}', 'X', p)
        if (m == verb or m == '*') and (rx.match(pp) or (p.endswith('/') and path.startswith(p))):
            out |= sids
    return sorted(out)

PERMIT = ["/api/auth/**", "/api/players/registro", "/api/players/confirmar-email/**", "/api/cookies/**", "/test/telegram/**",
          "/recuperar-password/**", "/recuperar-password"]
PERMIT_GET = ["/api/king-of-court/tournaments/*/state", "/api/king-of-court/tournaments/*/ranking",
              "/api/king-of-court/tournaments/*/players/*/history", "/api/king-of-court/ping",
              "/api/tournaments/double/*/looking-for-partner", "/api/internal/telegram-pipeline/status"]
PERMIT_POST = ["/api/tournaments/double/accept-pair"]

def ant(pattern, path):
    rx = '^' + re.escape(pattern).replace(r'\*\*', '.*').replace(r'\*', '[^/]+') + '$'
    return re.match(rx, path)

def access(x):
    path = re.sub(r'\{[^}]*\}', 'X', x['paths'][0])
    pre = x['pre']
    rule = None
    if any(ant(p, path) for p in PERMIT) or (x['verb'] == 'GET' and any(ant(p, path) for p in PERMIT_GET)) or (x['verb'] == 'POST' and any(ant(p, path) for p in PERMIT_POST)):
        rule = 'публичный'
    elif path.startswith('/admin/'):
        rule = 'админ-роли (URL)'
    elif path.startswith('/test/tournaments'):
        rule = 'SUPER_ADMIN (URL)'
    else:
        rule = 'вход обязателен'
    if pre:
        roles = re.findall(r"'(\w+)'", pre[0])
        return f"{rule} + {'/'.join(roles)}"
    return rule

groups = [
    ('Auth', lambda p: p.startswith('/api/auth')),
    ('Турниры (общие)', lambda p: p.startswith('/api/tournaments') and not p.startswith(('/api/tournaments/double', '/api/tournaments/americano'))),
    ('Парная регистрация', lambda p: p.startswith('/api/tournaments/double')),
    ('Действия игрока (не под /api)', lambda p: p.startswith('/players/tournaments')),
    ('Игроки', lambda p: p.startswith(('/api/players', '/players/api'))),
    ('Сброс пароля (не под /api)', lambda p: p.startswith('/recuperar-password')),
    ('Клубы', lambda p: p.startswith('/api/clubs')),
    ('Владельцы', lambda p: p.startswith('/api/owners')),
    ('King of Court', lambda p: p.startswith('/api/king-of-court')),
    ('Americano', lambda p: p.startswith('/api/tournaments/americano')),
    ('Team Americano (не под /api)', lambda p: p.startswith('/tournaments/team-americano/api')),
    ('Team Playoff (не под /api)', lambda p: p.startswith('/tournaments/team-playoff/api')),
    ('Админ: JSON под /admin', lambda p: p.startswith('/admin/')),
    ('Админ-метрики', lambda p: p.startswith('/api/admin')),
    ('Cookies', lambda p: p.startswith('/api/cookies')),
    ('Служебные', lambda p: p.startswith(('/api/internal', '/test/telegram'))),
]

js_eps = [x for x in ctrl if x['json'] and not x['paths'][0].startswith('/test/tournaments')]
lines = []
w = lines.append
w('## 1. Существующие JSON-эндпоинты\n')
w(f'Сгенерировано из кода (коммит `eeee06c`): **{len(js_eps)}** JSON-эндпоинтов вне `/test/tournaments/**` (у тестового инструмента ещё {len([x for x in ctrl if x["json"] and x["paths"][0].startswith("/test/tournaments")])} — он не переносится, ADR-0007).\n')
w('«Доступ» — **итоговый**: URL-правило `SecurityConfig` + `@PreAuthorize`. «Используется» — какие экраны (ID из [screens.md](screens.md)) вызывают эндпоинт сейчас; «—» = текущий UI его не вызывает (кандидат в неиспользуемые или API «на вырост»).\n')
seen = set()
for gname, pred in groups:
    rows = [x for x in js_eps if pred(x['paths'][0])]
    if not rows:
        continue
    w(f'### {gname}\n')
    w('| Метод | Путь | Доступ | Тело / параметры | Ответ | Используется |')
    w('|---|---|---|---|---|---|')
    for x in sorted(rows, key=lambda x: x['paths'][0]):
        seen.add(id(x))
        ret = re.search(r'(ResponseEntity<.*?>|ResponseEntity)\s+' + x['method'] + r'\s*\(', x['sig'])
        ret = ret.group(1).replace('ResponseEntity<', '').rstrip('>') if ret else '?'
        ret = ret.replace('ResponseEntity', '?')
        body = re.search(r'@RequestBody\s+(?:@Valid\s+)?([\w<>,]+)', x['sig'])
        params = [p.split(':')[1] + ('?' if p.endswith('?') else '') for p in x['params'] if p.startswith('RequestParam')]
        bp = []
        if body:
            bp.append('body: `' + body.group(1).rstrip(',') + '`')
        if params:
            bp.append('query: ' + ', '.join(f'`{p}`' for p in params))
        u = used_by(x['verb'], x['paths'][0])
        w(f"| {x['verb']} | `{x['paths'][0]}` | {access(x)} | {'; '.join(bp) or '—'} | `{ret}` | {', '.join(u) or '—'} |")
    w('')
rest = [x for x in js_eps if id(x) not in seen]
if rest:
    w('### Прочее\n')
    for x in rest:
        w(f"- `{x['verb']} {x['paths'][0]}`")

out = '\n'.join(lines) + '\n'
# косметика: дописать обрезанные '>' в generic-типах, пометить нетипизированные ответы/тела
out = out.replace('body: `Map<String`', 'body: `Map` (нетипизированный)')
out = re.sub(r'`((?:List|Map|Page)<[^`]*)`', lambda m: '`' + m.group(1) + '>' * (m.group(1).count('<') - m.group(1).count('>')) + '`', out)
out = out.replace('| `?` |', '| `?` (нетипизированный) |')
# guest-credentials вызывается со страницы деталей игрока (fetch по переменной guestCredentialsUrl)
out = out.replace('`/admin/players/{id}/guest-credentials` | админ-роли (URL) | — | `Map<String, Object>` | A-16 |', '`/admin/players/{id}/guest-credentials` | админ-роли (URL) | — | `Map<String, Object>` | A-17 |')
open(sys.argv[2], 'w').write(out)
print('ok', len(js_eps))
