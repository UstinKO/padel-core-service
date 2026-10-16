#!/usr/bin/env bash
# Выбор PR для автомержа (LFPT-0579, вызывается из pipeline-merge.yml, job "select").
#
# Окружение: GITHUB_REPOSITORY, GH_TOKEN; DRY_RUN=true — ничего не менять (только печать);
#            GITHUB_RUN_URL — ссылка на текущий прогон для комментариев.
# Вывод в $GITHUB_OUTPUT (или в stdout вне Actions): pr, title, head_sha, base_sha, deploy.
# Пустой pr — вливать нечего.
#
# Кандидат — открытый не-draft PR в master с метками pipeline + pipeline:ready,
# без pipeline:conflict / pipeline:deploy-failed, от старых к новым, у которого:
#   - успешный check "build" (PR Check) на текущем head-коммите;
#   - "Depends-On: #M" в описании выполнен: #M влит и (если он менял код) его деплой успешен;
#   - нет конфликта с master (конфликт → метка pipeline:conflict + комментарий, идём дальше).
# Глобально: открытый issue pipeline:halt или незавершённый деплой — ничего не выбираем.
set -euo pipefail

R="${GITHUB_REPOSITORY:?нужен GITHUB_REPOSITORY}"
DRY="${DRY_RUN:-false}"
RUN_URL="${GITHUB_RUN_URL:-}"
DOCS_RE='^(specs|\.claude|docs)/'

out() {
  if [ -n "${GITHUB_OUTPUT:-}" ]; then echo "$1" >> "$GITHUB_OUTPUT"; else echo "OUTPUT $1"; fi
}

# PR менял что-то кроме specs/.claude/docs → после мержа нужен деплой.
needs_deploy() {
  # Список целиком в переменную: grep -q в конвейере закрыл бы pipe раньше времени,
  # gh получил бы SIGPIPE, и под pipefail результат был бы ложным.
  local files
  files=$(gh pr view "$1" -R "$R" --json files --jq '.files[].path')
  grep -Eqv "$DOCS_RE" <<<"$files"
}

# Деплой влитого PR успешен: последний прогон deploy.yml, относящийся к PR (по имени
# «Deploy PR #M», по заголовку коммита «… (#M)» или по merge-коммиту), завершён с success.
deploy_succeeded() {
  local pr="$1" merge_sha="$2"
  local conclusion
  conclusion=$(gh run list -R "$R" --workflow deploy.yml --limit 50 \
      --json displayTitle,headSha,status,conclusion,createdAt |
    jq -r --arg pr "$pr" --arg sha "$merge_sha" '
      [ .[] | select((.displayTitle | test("#" + $pr + "([^0-9]|$)")) or .headSha == $sha) ]
      | sort_by(.createdAt) | last
      | if . == null then "none" elif .status != "completed" then "running" else .conclusion end')
  [ "$conclusion" = "success" ]
}

mark_conflict() {
  local pr="$1" reason="$2"
  echo "  #$pr: $reason → pipeline:conflict"
  [ "$DRY" = "true" ] && return 0
  gh pr edit "$pr" -R "$R" --add-label pipeline:conflict >/dev/null
  gh pr comment "$pr" -R "$R" --body "🤖 pipeline-merge: $reason. Автомерж для этого PR остановлен (метка \`pipeline:conflict\`). ${RUN_URL}" >/dev/null
}

halt=$(gh issue list -R "$R" --state open --label pipeline:halt --json number,title --jq '.[] | "#\(.number) \(.title)"')
if [ -n "$halt" ]; then
  echo "⛔ Автомерж остановлен открытым issue pipeline:halt: $halt"
  exit 0
fi

active_deploys=$(gh run list -R "$R" --workflow deploy.yml --limit 20 --json status \
  --jq '[.[] | select(.status != "completed")] | length')
if [ "$active_deploys" != "0" ]; then
  echo "⏳ Идёт деплой ($active_deploys) — мерж после его завершения."
  exit 0
fi

# Все открытые PR и фильтр меток в jq: gh pr list --label отдаёт данные с задержкой индекса,
# и прогон по событию labeled не видел PR с только что поставленной меткой (LFPT-0587).
candidates=$(gh pr list -R "$R" --state open --base master --limit 200 --json number,createdAt,isDraft,labels |
  jq -r '[ .[] | select(.isDraft | not)
               | ([.labels[].name]) as $l
               | select(($l | index("pipeline")) and ($l | index("pipeline:ready")))
               | select(($l | index("pipeline:conflict")) or ($l | index("pipeline:deploy-failed")) | not) ]
         | sort_by(.createdAt) | .[].number')

if [ -z "$candidates" ]; then
  echo "ℹ️ Нет PR с pipeline + pipeline:ready."
  exit 0
fi

for pr in $candidates; do
  info=$(gh pr view "$pr" -R "$R" --json title,body,headRefOid,mergeable)
  title=$(jq -r .title <<<"$info")
  head=$(jq -r .headRefOid <<<"$info")
  mergeable=$(jq -r .mergeable <<<"$info")

  build=$(gh api "repos/$R/commits/$head/check-runs?per_page=100" |
    jq -r '[ .check_runs[] | select(.name == "build") ] | sort_by(.started_at) | last | .conclusion // "pending"')
  if [ "$build" != "success" ]; then
    echo "  #$pr: PR Check (build) на $head — ${build}, ждём."
    continue
  fi

  dep=$(jq -r '.body // ""' <<<"$info" | sed -nE 's/^[[:space:]]*Depends-On:[[:space:]]*#([0-9]+).*/\1/p' | head -n1)
  if [ -n "$dep" ]; then
    dep_info=$(gh pr view "$dep" -R "$R" --json state,mergeCommit)
    dep_state=$(jq -r .state <<<"$dep_info")
    if [ "$dep_state" != "MERGED" ]; then
      echo "  #$pr: ждёт #$dep (состояние $dep_state)."
      continue
    fi
    dep_sha=$(jq -r '.mergeCommit.oid // ""' <<<"$dep_info")
    if needs_deploy "$dep" && ! deploy_succeeded "$dep" "$dep_sha"; then
      echo "  #$pr: #$dep влит, но его деплой ещё не успешен — ждём."
      continue
    fi
  fi

  case "$mergeable" in
    MERGEABLE) ;;
    CONFLICTING) mark_conflict "$pr" "конфликт с master"; continue ;;
    *) echo "  #$pr: GitHub ещё считает mergeable ($mergeable) — следующий прогон."; continue ;;
  esac

  deploy=false
  needs_deploy "$pr" && deploy=true
  base=$(gh api "repos/$R/commits/master" --jq .sha)

  echo "✅ Кандидат: #$pr «${title}» (head $head, master $base, деплой: $deploy)"
  out "pr=$pr"
  out "title=$title"
  out "head_sha=$head"
  out "base_sha=$base"
  out "deploy=$deploy"
  exit 0
done

echo "ℹ️ Готовых к мержу PR нет."
