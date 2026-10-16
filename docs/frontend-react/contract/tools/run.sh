#!/usr/bin/env bash
# Повторная инвентаризация фронта. Запуск из корня репозитория:
#   bash docs/frontend-react/contract/tools/run.sh
# Пишет screens.md заново; таблицу существующих эндпоинтов — в $OUT/api-existing.md
# (её нужно вручную вклеить в api.md §1, остальные разделы api.md — ручные).
set -euo pipefail
T="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$(mktemp -d)}"
cp "$T"/*.py "$OUT"/
python3 "$OUT/ctrl.py" src/main/java/com/padle/core/padelcoreservice/controller "$OUT/ctrl.json"
python3 "$OUT/tpl.py" src/main/resources "$OUT/tpl.json"
python3 "$OUT/gen_screens.py" "$OUT" docs/frontend-react/contract/screens.md
python3 "$OUT/gen_api.py" "$OUT" "$OUT/api-existing.md"
echo "screens.md обновлён; таблица API: $OUT/api-existing.md"
echo "Словарь JS-вызовов и аннотации экранов (SCREENS, JS в gen_screens.py) — ручные, обновить при изменениях."
