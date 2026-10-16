#!/usr/bin/env bash
# Архивация спек задачи после успешного деплоя (вызывается из deploy.yml, job "archive-specs").
#
# Использование: archive-specs.sh "<заголовок коммита>"
# Запускать из корня репозитория. Номера задач берутся из всех вхождений LFPT-XXXX в заголовке.
#
# - specs/features/LFPT-<N>-*.md → specs/done/, строка под "## Статус" → done;
# - specs/requests/LFPT-*.md → specs/done/requests/, если запрос относится к задаче
#   (тот же номер или на него ссылается заархивированная спека) и больше не нужен
#   ни одной спеке, оставшейся в specs/features/ (например, запрос LFPT-0572 ждёт,
#   пока задеплоится и фронтовая задача LFPT-0573, которая на него ссылается);
# - ссылки ../requests/X в specs/done/*.md → requests/X для перенесённых запросов.
#
# Печатает перенесённые файлы (по одному на строку); пустой вывод — архивировать нечего.
set -euo pipefail

subject="${1:?нужен заголовок коммита}"
nums=$(grep -oE 'LFPT-[0-9]+' <<<"$subject" | sed -E 's/^LFPT-0*//' | sort -u || true)
[ -z "$nums" ] && exit 0

num_of() { basename "$1" | sed -nE 's/^LFPT-0*([0-9]+)-.*/\1/p'; }
has_num() { local n; for n in $nums; do [ "$(num_of "$1")" = "$n" ] && return 0; done; return 1; }

archived=()
moved_features=()

for f in specs/features/LFPT-*.md; do
  [ -e "$f" ] || continue
  has_num "$f" || continue
  dst="specs/done/$(basename "$f")"
  git mv "$f" "$dst"
  perl -0pi -e 's/(## Статус\n)[^\n]*\n/${1}done\n/' "$dst"
  archived+=("$dst")
  moved_features+=("$dst")
done

mkdir -p specs/done/requests
for f in specs/requests/LFPT-*.md; do
  [ -e "$f" ] || continue
  b=$(basename "$f")
  wanted=false
  has_num "$f" && wanted=true
  for a in ${moved_features[@]+"${moved_features[@]}"}; do
    grep -qF "$b" "$a" && wanted=true
  done
  $wanted || continue
  # Запрос ещё нужен спеке в работе — оставляем до её деплоя.
  if grep -lF "$b" specs/features/*.md >/dev/null 2>&1; then continue; fi
  git mv "$f" "specs/done/requests/$b"
  archived+=("specs/done/requests/$b")
done

for f in specs/done/*.md; do
  [ -e "$f" ] || continue
  perl -pi -e 's{\]\(\.\./requests/(LFPT-[^)]+\.md)\)}{-e "specs/done/requests/$1" ? "](requests/$1)" : $&}ge' "$f"
done

[ ${#archived[@]} -eq 0 ] || printf '%s\n' "${archived[@]}"
