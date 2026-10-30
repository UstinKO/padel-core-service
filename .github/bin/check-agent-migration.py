#!/usr/bin/env python3
"""Проверяет ссылки правил, защиту приватных файлов и классификацию деплоя."""

from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]


def check():
    errors = []
    required = ['AGENTS.md', '.agents/PROJECT.md', '.agents/GIT_WORKFLOW.md',
                '.agents/CODE_STYLE.md', '.agents/CODE_REVIEW.md',
                '.agents/workflows/develop-feature.md',
                '.agents/skills/develop-feature/SKILL.md', '.agents/skills/new-feature/SKILL.md',
                '.agents/roles/analyst.md', '.agents/roles/developer.md', '.agents/roles/tester.md']
    for name in required:
        if not (ROOT / name).is_file():
            errors.append(f'Нет обязательного файла {name}')

    private = {'.agents/SECURITY.md', '.agents/THREATS.md',
               '.agents/archive/claude-settings.json', '.claude/settings.local.json',
               'docs/infrastructure-security.md', 'docs/telegram-ai-pipeline.md',
               '.codex/auth.json', 'automation/telegram-relay/.env'}
    for name in sorted(private):
        result = subprocess.run(['git', 'check-ignore', '-q', '--', name], cwd=ROOT)
        if result.returncode:
            errors.append(f'Приватный файл не защищён Git ignore: {name}')

    # Только действующие инструкции: архив не должен диктовать текущие правила.
    instructions = [ROOT / 'AGENTS.md'] + [p for p in (ROOT / '.agents').rglob('*.md')
                    if 'archive' not in p.parts and p.name not in ('SECURITY.md', 'THREATS.md')]
    private_targets = {(ROOT / name).resolve() for name in private}
    for path in instructions:
        text = path.read_text()
        for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)', text):
            target = target.split('#', 1)[0]
            if not target or re.match(r'[a-z]+://', target):
                continue
            resolved = (path.parent / target).resolve()
            if resolved not in private_targets and not resolved.exists():
                errors.append(f'{path.relative_to(ROOT)}: сломана ссылка {target}')
        if 'mcp__claude-in-chrome__' in text or 'claude.ai/code/routines' in text:
            errors.append(f'{path.relative_to(ROOT)}: действующая инструкция требует инструмент Claude')

    selector = (ROOT / '.github/bin/pipeline-select.sh').read_text()
    match = re.search(r"^DOCS_RE='([^']+)'", selector, re.M)
    if not match:
        errors.append('Не найден DOCS_RE оркестратора')
    else:
        pattern = re.compile(match.group(1))
        sys.path.insert(0, str(ROOT / 'automation/telegram-relay'))
        from engine import DOCS_RE
        samples = {'AGENTS.md': True, '.agents/PROJECT.md': True,
                   '.agents/skills/develop-feature/SKILL.md': True, '.claude/CLAUDE.md': True,
                   'docs/Home.md': True, 'specs/MASTER.md': True,
                   'automation/telegram-relay/relay.py': False, 'pom.xml': False,
                   'src/main/java/Example.java': False, 'mobile-app/src/auth.ts': False,
                   'AGENTS.md.java': False, 'docs-in-code.java': False}
        for name, expected in samples.items():
            if bool(pattern.match(name)) != expected or bool(DOCS_RE.match(name)) != expected:
                errors.append(f'Оркестратор и движок неверно классифицируют {name}')
    deploy = (ROOT / '.github/workflows/deploy.yml').read_text()
    for rule in ("      - '.agents/**'", "      - 'AGENTS.md'"):
        if rule not in deploy:
            errors.append(f'Нет исключения деплоя {rule.strip()}')
    return errors


if __name__ == '__main__':
    problems = check()
    for problem in problems:
        print(problem)
    print(f'agent-migration: расхождений {len(problems)}')
    raise SystemExit(bool(problems))
