#!/usr/bin/env python3
"""Расхождения docs/ с кодом (LFPT-0615).

Проверяет то, что можно сверить механически:
  1. версия схемы в шапке docs/database-architecture.md = последняя миграция Liquibase;
  2. каждая таблица и колонка JPA-сущностей упомянута в docs/database-architecture.md;
     у описанных там enum (### `Имя`) перечислены все значения из кода;
  3. упомянутые в docs классы (`*Service`, `*Controller`, `*Repository`, ...) существуют в коде;
  4. каждый HTTP-эндпоинт контроллеров упомянут в docs/endpoints/*.md или docs/domain-overview.md,
     и каждый эндпоинт из таблиц docs/domain-overview.md существует в коде.

Запуск из корня репозитория: python3 .github/bin/docs-drift.py
Код возврата 1, если найдены расхождения. Смысловую точность описаний скрипт не проверяет.
"""
import glob
import os
import re
import sys

JAVA = "src/main/java"
MIGRATIONS = "src/main/resources/db/changelog/versions"
DB_DOC = "docs/database-architecture.md"
ARCH_DOC = "docs/architecture.md"
# документы о текущем состоянии кода (планы, ADR и журналы этапов i18n сюда не входят)
CURRENT_DOCS = [DB_DOC, ARCH_DOC, "docs/auth-flow.md", "docs/domain-overview.md"] + sorted(glob.glob("docs/endpoints/*.md"))
CLASS_REF = re.compile(r"`([A-Z]\w*(?:Service|Controller|Repository|Filter|Config|Mapper|Scheduler|Handler|Aspect|Poller))\b")
ENDPOINT_DOCS = sorted(glob.glob("docs/endpoints/*.md")) + ["docs/domain-overview.md"]

MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping(?:\((.*?)\))?\s*\n", re.S)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def snake(name):
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def check_schema_version():
    versions = [re.match(r"v1\.(\d+)", f) for f in os.listdir(MIGRATIONS)]
    latest = max(int(m.group(1)) for m in versions if m)
    problems = []
    for path, pattern in [(DB_DOC, r"Версия схемы:\*\*\s*v1\.(\d+)"), (ARCH_DOC, r"Текущая версия: \*\*v1\.(\d+)")]:
        doc = re.search(pattern, read(path))
        documented = int(doc.group(1)) if doc else None
        if documented != latest:
            problems.append(f"{path}: версия схемы v1.{documented}, последняя миграция v1.{latest}")
    return problems


# классы фреймворков, на которые ссылаются docs
EXTERNAL_CLASSES = {"UserDetailsService", "ObjectMapper", "OncePerRequestFilter", "SecurityFilterChain",
                     "OAuth2AuthorizationRequestRedirectFilter"}
# строки с предложениями рефакторинга называют классы, которых ещё нет
PROPOSAL = re.compile(r"разделить|переносится|выделить|предлага", re.I)


def check_class_references():
    classes = {os.path.basename(p)[:-5] for p in glob.glob(f"{JAVA}/**/*.java", recursive=True)} | EXTERNAL_CLASSES
    problems = []
    for path in CURRENT_DOCS:
        names = {n for line in read(path).splitlines() if not PROPOSAL.search(line) for n in CLASS_REF.findall(line)}
        for name in sorted(names - classes):
            problems.append(f"{path}: класса {name} нет в коде")
    return problems


def entity_columns(source):
    """Колонки сущности: @Column/@JoinColumn(name=...), иначе имя поля в snake_case (SpringPhysicalNamingStrategy)."""
    columns = []
    for m in re.finditer(r"((?:@[\w.]+(?:\([^;{]*?\))?\s*)*)(?:private|protected)\s+(?!static)[\w<>, ?\[\]]+\s+(\w+)\s*[;=]", source):
        annotations, field = m.group(1), m.group(2)
        if re.search(r"@(Transient|OneToMany|ManyToMany)\b|mappedBy", annotations):
            continue
        explicit = re.search(r"@(?:Join)?Column\([^)]*?name\s*=\s*\"(\w+)\"", annotations)
        if explicit:
            columns.append(explicit.group(1))
        elif re.search(r"@(ManyToOne|OneToOne)\b", annotations):
            columns.append(snake(field) + "_id")
        else:
            columns.append(snake(field))
    return columns


def check_schema_coverage():
    doc = read(DB_DOC)
    problems = []
    for path in glob.glob(f"{JAVA}/**/*.java", recursive=True):
        source = read(path)
        table = re.search(r"@Table\(\s*name\s*=\s*\"(\w+)\"", source)
        if "@Entity" not in source or not table:
            continue
        name = table.group(1)
        section = re.search(rf"### `{name}`(.*?)(?=\n### |\n## |\Z)", doc, re.S)
        if not section:
            problems.append(f"таблица {name} ({os.path.basename(path)}) не описана в {DB_DOC}")
            continue
        missing = [c for c in entity_columns(source) if f"`{c}`" not in section.group(1)]
        if missing:
            problems.append(f"{name}: не описаны колонки {', '.join(missing)}")
    return problems


def check_enum_coverage():
    doc = read(DB_DOC)
    problems = []
    for path in glob.glob(f"{JAVA}/**/model/enums/*.java", recursive=True):
        name = os.path.basename(path)[:-5]
        section = re.search(rf"### `{name}`(.*?)(?=\n### |\n## |\Z)", doc, re.S)
        if not section:
            continue
        body = read(path).split("{", 1)[1].split(";", 1)[0]
        constants = re.findall(r"^\s*([A-Z][A-Z0-9_]*)\s*(?:\(|,|$)", body, re.M)
        missing = [c for c in constants if f"`{c}`" not in section.group(1)]
        if missing:
            problems.append(f"enum {name}: не описаны значения {', '.join(missing)}")
    return problems


def controller_endpoints():
    for path in glob.glob(f"{JAVA}/**/*.java", recursive=True):
        source = read(path)
        if "@Controller" not in source and "@RestController" not in source:
            continue
        head, _, body = source.partition(" class ")
        prefix = re.search(r"@RequestMapping\(\s*(?:value\s*=\s*|path\s*=\s*)?\{?\s*\"([^\"]*)\"", head)
        prefix = prefix.group(1) if prefix else ""
        for m in MAPPING.finditer(body):
            args = re.split(r"\b(?:produces|consumes|params|headers)\s*=", m.group(2) or "")[0]
            for path_part in re.findall(r"\"([^\"]*)\"", args) or [""]:
                yield (prefix + path_part) or "/", os.path.basename(path)[:-5]


def normalize(text):
    return re.sub(r"\{[^}]*\}", "{}", text)


def check_endpoint_coverage():
    docs = normalize("\n".join(read(p) for p in ENDPOINT_DOCS))
    problems = []
    for url, controller in sorted(set(controller_endpoints())):
        if normalize(url).rstrip("/") not in docs:
            problems.append(f"эндпоинт {url} ({controller}) не описан в docs/endpoints/ или docs/domain-overview.md")
    return problems


DOC_ROW = re.compile(r"`(?:GET|POST|PUT|PATCH|DELETE)`\s*\|\s*`(/[^`?]*)")


def check_stale_endpoints():
    code = {normalize(url).rstrip("/") or "/" for url, _ in controller_endpoints()}
    problems = []
    for url in sorted(set(DOC_ROW.findall(read("docs/domain-overview.md")))):
        if (normalize(url).rstrip("/") or "/") not in code:
            problems.append(f"docs/domain-overview.md: эндпоинта {url} нет в коде")
    return problems


def main():
    problems = check_schema_version() + check_schema_coverage() + check_enum_coverage() + check_class_references() + check_endpoint_coverage() + check_stale_endpoints()
    for p in problems:
        print(p)
    print(f"docs-drift: расхождений {len(problems)}")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
