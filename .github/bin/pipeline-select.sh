#!/usr/bin/env bash
# Выбор PR для автомержа (LFPT-0579, вызывается из pipeline-merge.yml, job "select").
#
# Окружение: GITHUB_REPOSITORY, GH_TOKEN; DRY_RUN=true — ничего не менять (только печать);
#            GITHUB_RUN_URL — ссылка на текущий прогон для комментариев.
# Вывод в $GITHUB_OUTPUT (или в stdout вне Actions): pr, title, head_sha, base_sha, deploy, verify_ref.
# Пустой pr — вливать нечего.
# Запускать из полного клона репозитория (нужен для перестройки стека, LFPT-0590).
#
# Кандидат — открытый не-draft PR в master с метками pipeline + pipeline:ready,
# без pipeline:conflict / pipeline:deploy-failed, от старых к новым, у которого:
#   - успешный check "build" (PR Check) на текущем head-коммите;
#   - "Depends-On: #M" в описании выполнен: #M влит и (если он менял код) его деплой успешен;
#   - нет конфликта с master (конфликт → метка pipeline:conflict + комментарий, идём дальше).
# Перестройка стека (LFPT-0590): фронт-ветка создаётся от бэк-ветки, бэк вливается squash-ом —
# исходные бэк-коммиты во фронт-ветке дублируют squash-коммит в master и конфликтуют, если фронт
# правит те же строки. Если #M из Depends-On влит, а его последний коммит ещё в истории PR —
# переносим на master только фронтовые коммиты: git rebase --onto origin/master <head #M>.
# Ветку, перестроенную оркестратором (push от GITHUB_TOKEN не запускает PR Check), проверяет verify.
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

BOT_COMMITTER="github-actions[bot]"

# Перестроить ветку PR на свежий master, выбросив коммиты влитого #M. Печатает новый head.
# В отдельном worktree: переключение веток в текущей папке подменило бы и этот выполняемый скрипт.
restack() {
  local pr="$1" branch="$2" head="$3" dep_head="$4" wt rc=0
  git fetch -q origin master "+refs/heads/${branch}:refs/remotes/origin/${branch}"
  wt=$(mktemp -d)
  git worktree add -q --detach "$wt" "$head" >&2
  if git -C "$wt" -c user.name="$BOT_COMMITTER" -c user.email="41898282+github-actions[bot]@users.noreply.github.com" \
      rebase -q --onto origin/master "$dep_head" >&2; then
    git -C "$wt" push -q --force-with-lease="${branch}:${head}" origin "HEAD:refs/heads/${branch}" >&2
    git -C "$wt" rev-parse HEAD
  else
    git -C "$wt" rebase --abort >&2 || true
    rc=1
  fi
  git worktree remove --force "$wt" >&2
  return "$rc"
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
  info=$(gh pr view "$pr" -R "$R" --json title,body,headRefOid,headRefName,mergeable)
  title=$(jq -r .title <<<"$info")
  head=$(jq -r .headRefOid <<<"$info")
  branch=$(jq -r .headRefName <<<"$info")
  mergeable=$(jq -r .mergeable <<<"$info")

  # Ветку, перестроенную оркестратором, PR Check не проверял — её проверит verify.
  committer=$(gh api "repos/$R/commits/$head" --jq '.commit.committer.name')
  if [ "$committer" != "$BOT_COMMITTER" ]; then
    build=$(gh api "repos/$R/commits/$head/check-runs?per_page=100" |
      jq -r '[ .check_runs[] | select(.name == "build") ] | sort_by(.started_at) | last | .conclusion // "pending"')
    if [ "$build" != "success" ]; then
      echo "  #$pr: PR Check (build) на $head — ${build}, ждём."
      continue
    fi
  fi

  dep=$(jq -r '.body // ""' <<<"$info" | sed -nE 's/^[[:space:]]*Depends-On:[[:space:]]*#([0-9]+).*/\1/p' | head -n1)
  if [ -n "$dep" ]; then
    dep_info=$(gh pr view "$dep" -R "$R" --json state,mergeCommit,headRefOid)
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

  verify_ref="refs/pull/${pr}/merge"
  restacked=false
  if [ -n "$dep" ]; then
    dep_head=$(jq -r .headRefOid <<<"$dep_info")
    git fetch -q origin "+refs/heads/${branch}:refs/remotes/origin/${branch}"
    if git cat-file -e "${dep_head}^{commit}" 2>/dev/null && git merge-base --is-ancestor "$dep_head" "$head"; then
      if [ "$DRY" = "true" ]; then
        echo "  #$pr: перестроил бы ветку ${branch} на master без коммитов #$dep (dry run)."
      elif new_head=$(restack "$pr" "$branch" "$head" "$dep_head"); then
        echo "  #$pr: ветка ${branch} перестроена на master без коммитов #$dep: ${head::7} → ${new_head::7}."
        gh pr comment "$pr" -R "$R" --body "🤖 pipeline-merge: #$dep влит — ветка перестроена на master без его коммитов (\`${head::7}\` → \`${new_head::7}\`), дальше проверка на свежем master и мерж. ${RUN_URL}" >/dev/null
        head="$new_head"
        verify_ref="$new_head"
        restacked=true
      else
        mark_conflict "$pr" "не удалось перенести коммиты PR на master после мержа #$dep (git rebase --onto: конфликт)"
        continue
      fi
    fi
  fi

  if [ "$restacked" = "false" ]; then
    case "$mergeable" in
      MERGEABLE) ;;
      CONFLICTING) mark_conflict "$pr" "конфликт с master"; continue ;;
      *) echo "  #$pr: GitHub ещё считает mergeable ($mergeable) — следующий прогон."; continue ;;
    esac
  fi

  deploy=false
  needs_deploy "$pr" && deploy=true
  base=$(gh api "repos/$R/commits/master" --jq .sha)

  echo "✅ Кандидат: #$pr «${title}» (head $head, master $base, деплой: $deploy)"
  out "pr=$pr"
  out "title=$title"
  out "head_sha=$head"
  out "base_sha=$base"
  out "deploy=$deploy"
  out "verify_ref=$verify_ref"
  exit 0
done

echo "ℹ️ Готовых к мержу PR нет."
