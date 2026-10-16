import re, os, sys, json
from collections import OrderedDict

ROOT = sys.argv[1]  # src/main/resources
T = os.path.join(ROOT, 'templates')
res = OrderedDict()

def uniq(xs):
    seen = []
    for x in xs:
        x = re.sub(r'\s+', ' ', x.strip())
        if x and x not in seen:
            seen.append(x)
    return seen

for dp, _, fs in sorted(os.walk(T)):
    for f in sorted(fs):
        if not f.endswith('.html'):
            continue
        p = os.path.join(dp, f)
        rel = os.path.relpath(p, T)[:-5]
        s = open(p, encoding='utf-8').read()
        forms = []
        for m in re.finditer(r'<form\b([^>]*)>(.*?)</form>', s, re.S | re.I):
            attrs, inner = m.group(1), m.group(2)
            act = re.search(r'th:action="([^"]*)"', attrs) or re.search(r'\baction="([^"]*)"', attrs)
            meth = re.search(r'method="([^"]*)"', attrs, re.I)
            fid = re.search(r'\bid="([^"]*)"', attrs)
            names = uniq(re.findall(r'\bname="([^"]*)"', inner) + re.findall(r'th:field="\*\{([^}]*)\}"', inner))
            forms.append(dict(action=act.group(1) if act else '', method=(meth.group(1).upper() if meth else 'GET'),
                              id=fid.group(1) if fid else '', fields=[n for n in names if n != '_csrf']))
        scripts = uniq(re.findall(r'<script[^>]*\b(?:th:)?src="([^"]*)"', s))
        css = uniq(re.findall(r'<link[^>]*\b(?:th:)?href="([^"]*\.css[^"]*)"', s) + re.findall(r'<link[^>]*rel="stylesheet"[^>]*href="([^"]*)"', s))
        fetches = uniq(re.findall(r'fetch\(\s*([`\'"][^`\'"]*[`\'"]|[^,)]+)', s))
        inline_js_vars = uniq([a + '=' + b for a, b in re.findall(r'(?:const|let|var)\s+(\w+)\s*=\s*/\*\[\[\s*(\$\{[^}]*\})', s)])
        inline_js_vars += uniq(['[[' + x + ']]' for x in re.findall(r'\[\[\$\{([^}]*)\}\]\]', s)])
        fragments = uniq(re.findall(r'th:(?:replace|insert|include)="~?\{?\s*([\w/\-]+)\s*::\s*([\w\-]+)', s) and
                         [a + '::' + b for a, b in re.findall(r'th:(?:replace|insert|include)="~?\{?\s*([\w/\-]+)\s*::\s*([\w\-]+)', s)])
        defines = uniq(re.findall(r'th:fragment="([^"(]*)', s))
        sec = uniq(re.findall(r'sec:authorize="([^"]*)"', s))
        links = uniq(re.findall(r'th:href="@\{([^}(]*)', s))
        i18n = len(set(re.findall(r'#\{([\w.\-]+)', s)))
        hard_es = 0
        ws = bool(re.search(r'SockJS|Stomp|/ws\b', s))
        og = uniq(re.findall(r'<meta[^>]*property="(og:[^"]*)"', s))
        metas = uniq(re.findall(r'<meta[^>]*name="(description|robots|twitter:[^"]*)"', s))
        recaptcha = 'recaptcha' in s.lower()
        localstorage = uniq(re.findall(r'localStorage\.(?:getItem|setItem)\(\s*[\'"]([^\'"]+)', s))
        inline_script_lines = sum(len(m.group(1).split('\n')) for m in re.finditer(r'<script(?![^>]*src=)[^>]*>(.*?)</script>', s, re.S))
        res[rel] = dict(lines=s.count('\n') + 1, inline_js_lines=inline_script_lines, forms=forms, scripts=scripts, css=css,
                        fetches=fetches, js_vars=inline_js_vars, fragments=fragments, defines=defines, sec=sec,
                        links=links, i18n_keys=i18n, websocket=ws, og=og, meta=metas, recaptcha=recaptcha,
                        localStorage=localstorage)

json.dump(res, open(sys.argv[2], 'w'), ensure_ascii=False, indent=1)
print(len(res))
