"""
Движок задач Telegram-конвейера (LFPT-0581, docs/pipeline-autonomy.md §4.2–4.3).

Задача = сообщение (или собранная через кнопки пачка) в теме "AI Разработка". Её id —
message_id карточки задачи в чате (он же telegram_launch_message_id для роутины и
Pipeline-Task в GitHub). Движок:
  - держит очередь и лимит параллельных сессий роутины (MAX_PARALLEL);
  - запускает роутину (/fire) — единственный держатель её токена;
  - повторяет запуск на 429/503 по Retry-After (запуск не принят — повтор безопасен);
  - отслеживает задачу по следам в GitHub: issue/PR с "Pipeline-Task: <id>", их метки
    (pipeline:ready / needs-input / failed / conflict / deploy-failed), мерж и прогоны
    "Deploy to Production";
  - ведёт в чате одну карточку задачи (редактируется по ходу) и шлёт реплаем на неё
    только то, что требует внимания: вопросы, итог «влито на прод», сбои;
  - реплай на любое сообщение задачи = продолжение этой задачи;
  - прогон без своих issue/PR (вопрос о статусе, «делать нечего») оставляет комментарий
    "🤖 pipeline-reply <id>" — движок пересылает ответ в чат и закрывает задачу (LFPT-0641).

Состояние — SQLite (переживает рестарт). Только стандартная библиотека.
"""

import json
import logging
import re
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Callable, Optional

log = logging.getLogger("telegram-relay.engine")

# Статусы задачи
QUEUED = "queued"            # ждёт свободного слота / Retry-After / снятия паузы
RUNNING = "running"          # сессия роутины работает (занимает слот)
NEEDS_INPUT = "needs_input"  # роутина ждёт ответов (issue pipeline:needs-input)
MERGING = "merging"          # все PR задачи pipeline:ready — дальше оркестратор
CONFLICT = "conflict"        # оркестратор поставил pipeline:conflict
DEPLOY_FAILED = "deploy_failed"
FAILED = "failed"            # роутина поставила pipeline:failed или /fire не удался
STALLED = "stalled"          # нет движения в GitHub STALL_HOURS (без следов — traceless_stall_minutes)
DONE = "done"
ANSWERED = "answered"        # прогон без issue/PR ответил комментарием 🤖 pipeline-reply
CANCELLED = "cancelled"

TERMINAL = {DONE, ANSWERED, CANCELLED}
SLOT_STATUSES = {RUNNING}

READY = "pipeline:ready"
L_NEEDS_INPUT = "pipeline:needs-input"
L_FAILED = "pipeline:failed"
L_CONFLICT = "pipeline:conflict"
L_DEPLOY_FAILED = "pipeline:deploy-failed"
QUESTIONS_MARKER = "🤖 pipeline-questions"
REPLY_MARKER = "🤖 pipeline-reply"
DOCS_RE = re.compile(r"^((specs|\.agents|\.claude|docs)/|AGENTS\.md$)")


@dataclass
class EngineConfig:
    repo: str
    max_parallel: int = 2
    stall_hours: float = 3.0
    # Сессия роутины бывает обрывается раньше финального следа в GitHub (#5289, #5317, #5304).
    nudge_minutes: float = 30.0           # PR зелёный, а pipeline:ready нет — столько ждём и продолжаем задачу сами
    traceless_stall_minutes: float = 60.0  # прогон без issue/PR и без pipeline-reply — «нет движения» раньше
    summary_stalled_hours: float = 24.0    # старые «нет движения» в сводке очереди не показываем
    poll_interval: int = 60
    db_path: str = "/opt/padel-app/telegram-relay/engine.db"


# ─────────────────────────── GitHub (только чтение) ───────────────────────────

class GitHub:
    def __init__(self, token: str, repo: str) -> None:
        self.token = token
        self.repo = repo
        self._files_cache: dict[int, bool] = {}

    def _get(self, path: str) -> Any:
        req = urllib.request.Request(
            f"https://api.github.com{path}",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "padel-pipeline-engine",
            },
        )
        with urllib.request.urlopen(req, timeout=20) as resp:
            return json.loads(resp.read().decode("utf-8"))

    def _search(self, query: str, per_page: int = 50, kinds=("is:issue", "is:pull-request")) -> list[dict]:
        # Поиск требует явного is:issue / is:pull-request, иначе 422 — по запросу на каждый.
        items: list[dict] = []
        for kind in kinds:
            q = urllib.parse.quote(f"repo:{self.repo} {kind} {query}")
            items += self._get(f"/search/issues?q={q}&per_page={per_page}").get("items", [])
        return items

    def prs_closing(self, issue_number: int) -> list[dict]:
        """PR, закрывающие issue ("Closes #N"), — даже если роутина забыла в них Pipeline-Task (#5280 → #648)."""
        return [
            it for it in self._search(f'"#{issue_number}"', kinds=("is:pull-request",))
            if issue_number in {int(n) for n in CLOSES_RE.findall(it.get("body") or "")}
        ]

    def search_task(self, task_id: int) -> list[dict]:
        items = self._search(f'"Pipeline-Task: {task_id}"')
        marker = re.compile(rf"^\s*Pipeline-Task:\s*{task_id}\s*$", re.M)
        return [it for it in items if marker.search(it.get("body") or "")]

    def checks(self, sha: str) -> str:
        """Сводка check-runs коммита: success | pending | failure | none."""
        runs = self._get(f"/repos/{self.repo}/commits/{sha}/check-runs?per_page=100").get("check_runs", [])
        if not runs:
            return "none"
        if any(r.get("status") != "completed" for r in runs):
            return "pending"
        if all(r.get("conclusion") in ("success", "skipped", "neutral") for r in runs):
            return "success" if any(r.get("conclusion") == "success" for r in runs) else "none"
        return "failure"

    def pull(self, number: int) -> dict:
        return self._get(f"/repos/{self.repo}/pulls/{number}")

    def needs_deploy(self, number: int) -> bool:
        if number not in self._files_cache:
            files = self._get(f"/repos/{self.repo}/pulls/{number}/files?per_page=100")
            self._files_cache[number] = any(not DOCS_RE.match(f["filename"]) for f in files)
        return self._files_cache[number]

    def deploy_runs(self) -> list[dict]:
        return self._get(f"/repos/{self.repo}/actions/workflows/deploy.yml/runs?per_page=50").get("workflow_runs", [])

    def comments(self, number: int) -> list[dict]:
        return self._get(f"/repos/{self.repo}/issues/{number}/comments?per_page=100")

    def find_replies(self, task_id: int) -> list[tuple[int, float, str]]:
        """Комментарии "🤖 pipeline-reply <task_id>" (первая строка) в любых issue/PR: [(id, created_ts, текст)]."""
        head = re.compile(rf"^\s*{re.escape(REPLY_MARKER)}\s+{task_id}\s*$")
        found = []
        hits = {it["number"]: it for it in self._search(f'"pipeline-reply {task_id}" in:comments', per_page=10)}
        for it in hits.values():
            for c in self.comments(it["number"]):
                first, _, rest = (c.get("body") or "").lstrip().partition("\n")
                if head.match(first):
                    found.append((c["id"], _ts(c.get("created_at")), rest.strip()))
        return found

    def halt_issues(self) -> list[dict]:
        return self._get(f"/repos/{self.repo}/issues?state=open&labels=pipeline:halt&per_page=10")


def _ts(iso: Optional[str]) -> float:
    return datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp() if iso else 0.0


@dataclass
class PrInfo:
    number: int
    title: str
    url: str
    labels: set
    state: str             # open | merged | closed
    closes: set
    deploy: str = "none"   # none | running | success | failure | not_needed
    deploy_url: str = ""
    checks: str = "none"   # открытый PR: success | pending | failure | none


@dataclass
class IssueInfo:
    number: int
    title: str
    labels: set
    state: str
    questions: list = field(default_factory=list)  # [(comment_id, text)]


@dataclass
class Snapshot:
    issues: list
    prs: list

    def key(self) -> str:
        return json.dumps(
            {
                # id вопросов — в ключе: новый 🤖 pipeline-questions при той же метке тоже движение (LFPT-0641).
                "i": sorted((i.number, i.state, sorted(i.labels), sorted(c for c, _ in i.questions)) for i in self.issues),
                "p": sorted((p.number, p.state, sorted(p.labels), p.deploy) + ((p.checks,) if p.state == "open" else ())
                            for p in self.prs),
            }
        )


CLOSES_RE = re.compile(r"(?i)\b(?:closes|fixes|resolves)\s+#(\d+)")


def deploy_state_for(pr_number: int, merge_sha: str, runs: list[dict]) -> tuple[str, str]:
    """Последний прогон Deploy to Production, относящийся к PR: «Deploy PR #N» (оркестратор),
    заголовок коммита «… (#N)» (ручной мерж) или merge-коммит. GitHub обрезает display_title
    примерно до 70 символов — «(#N)» в длинном заголовке теряется, поэтому merge-коммит обязателен."""
    pat = re.compile(rf"#{pr_number}(?!\d)")
    matching = [r for r in runs if pat.search(r.get("display_title") or "") or r.get("head_sha") == merge_sha]
    if not matching:
        return "none", ""
    run = max(matching, key=lambda r: r.get("created_at") or "")
    if run.get("status") != "completed":
        return "running", run.get("html_url", "")
    return ("success" if run.get("conclusion") == "success" else "failure"), run.get("html_url", "")


def collect_snapshot(gh: GitHub, task_id: int, runs: list[dict]) -> Snapshot:
    issues, prs = [], []
    items = gh.search_task(task_id)
    # Issue задачи без PR с маркером: подтянуть PR, который её закрывает, — иначе задача
    # с влитым PR навсегда «в работе» (роутина забыла Pipeline-Task в описании PR).
    found = {it["number"] for it in items}
    closed_by_marked = {int(n) for it in items if "pull_request" in it for n in CLOSES_RE.findall(it.get("body") or "")}
    for it in [i for i in items if "pull_request" not in i and i["number"] not in closed_by_marked]:
        for pr in gh.prs_closing(it["number"]):
            if pr["number"] not in found:
                found.add(pr["number"])
                items.append(pr)
    for it in items:
        labels = {l["name"] for l in it.get("labels", [])}
        if "pull_request" in it:
            pr = gh.pull(it["number"])
            state = "merged" if pr.get("merged") else pr.get("state", "open")
            info = PrInfo(
                number=it["number"],
                title=it["title"],
                url=it["html_url"],
                labels=labels,
                state=state,
                closes={int(n) for n in CLOSES_RE.findall(it.get("body") or "")},
            )
            if state == "open":
                info.checks = gh.checks((pr.get("head") or {}).get("sha") or "")
            if state == "merged":
                if gh.needs_deploy(info.number):
                    info.deploy, info.deploy_url = deploy_state_for(info.number, pr.get("merge_commit_sha") or "", runs)
                else:
                    info.deploy = "not_needed"
            prs.append(info)
        else:
            questions = []
            if L_NEEDS_INPUT in labels:
                questions = [
                    (c["id"], c["body"])
                    for c in gh.comments(it["number"])
                    if (c.get("body") or "").lstrip().startswith(QUESTIONS_MARKER)
                ]
            issues.append(IssueInfo(it["number"], it["title"], labels, it.get("state", "open"), questions))
    return Snapshot(issues, prs)


def derive_status(snap: Snapshot) -> Optional[str]:
    """Статус задачи по следам в GitHub. None — следов нет."""
    if not snap.issues and not snap.prs:
        return None
    live_prs = [p for p in snap.prs if p.state != "closed"]
    open_items_labels = [i.labels for i in snap.issues if i.state == "open"] + [p.labels for p in live_prs if p.state == "open"]

    if any(L_DEPLOY_FAILED in p.labels or p.deploy == "failure" for p in live_prs):
        return DEPLOY_FAILED
    if any(L_FAILED in l for l in open_items_labels):
        return FAILED
    if any(L_NEEDS_INPUT in i.labels for i in snap.issues if i.state == "open"):
        return NEEDS_INPUT
    if any(L_CONFLICT in p.labels for p in live_prs if p.state == "open"):
        return CONFLICT

    # У каждой открытой issue задачи должен быть живой PR — иначе роутина ещё работает
    # (например, бэк-PR уже ready, а фронт ещё в разработке). Закрытая без PR issue
    # (закрыли руками как invalid) задачу не держит.
    covered = all(any(i.number in p.closes for p in live_prs) for i in snap.issues if i.state == "open")
    if not covered or not live_prs:
        return RUNNING
    if all(p.state == "merged" and p.deploy in ("success", "not_needed") for p in live_prs):
        return DONE
    if all(p.state == "merged" or READY in p.labels for p in live_prs):
        return MERGING
    return RUNNING


def ready_forgotten(snap: Snapshot) -> bool:
    """Все issue покрыты открытыми PR, pr-check у них зелёный, а pipeline:ready стоит не везде:
    сессия роутины закончилась до Шага 5.1 (#5289 → #652, #5317 → #654)."""
    open_prs = [p for p in snap.prs if p.state == "open"]
    if not open_prs or any(L_FAILED in p.labels or L_CONFLICT in p.labels for p in open_prs):
        return False
    if not all(any(i.number in p.closes for p in snap.prs if p.state != "closed") for i in snap.issues if i.state == "open"):
        return False
    waiting = [p for p in open_prs if READY not in p.labels]
    return bool(waiting) and all(p.checks == "success" for p in waiting)


def leftover_issues(snap: Snapshot) -> list:
    """Открытые issue без PR при том, что все PR задачи влиты и задеплоены.
    Обычно это побочные находки, ошибочно помеченные Pipeline-Task задачи (#5175 → #619, #620):
    без этой поправки задача навсегда в RUNNING → STALLED, и перезапуск не помогает."""
    live_prs = [p for p in snap.prs if p.state != "closed"]
    if not live_prs or not all(p.state == "merged" and p.deploy in ("success", "not_needed") for p in live_prs):
        return []
    return [i for i in snap.issues if i.state == "open" and not any(i.number in p.closes for p in live_prs)]


# ─────────────────────────────── Хранилище ───────────────────────────────

SCHEMA = """
CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY,          -- message_id карточки = telegram_launch_message_id = Pipeline-Task
    role TEXT NOT NULL,
    sender TEXT,
    orig_text TEXT NOT NULL,         -- исходная задача (для перезапуска, если GitHub-следов нет)
    next_text TEXT NOT NULL,         -- текст для следующего /fire
    status TEXT NOT NULL,
    created_at REAL NOT NULL,
    updated_at REAL NOT NULL,
    fire_after REAL NOT NULL DEFAULT 0,
    fired_at REAL,
    session_url TEXT,
    execution_id TEXT,
    launch_key TEXT,
    progress_at REAL,                -- последний раз, когда менялись следы в GitHub
    snapshot_key TEXT,
    fired_key TEXT,                  -- snapshot_key на момент последнего /fire
    snapshot_json TEXT,
    card_text TEXT,
    notified TEXT NOT NULL DEFAULT '[]',
    repair_count INTEGER NOT NULL DEFAULT 0,
    note TEXT
);
CREATE TABLE IF NOT EXISTS task_messages (
    message_id INTEGER PRIMARY KEY,
    task_id INTEGER NOT NULL
);
"""


class Store:
    def __init__(self, path: str) -> None:
        self.db = sqlite3.connect(path)
        self.db.row_factory = sqlite3.Row
        self.db.executescript(SCHEMA)
        columns = {row[1] for row in self.db.execute('PRAGMA table_info(tasks)')}
        if 'execution_id' not in columns:
            self.db.execute('ALTER TABLE tasks ADD COLUMN execution_id TEXT')
        if 'launch_key' not in columns:
            self.db.execute('ALTER TABLE tasks ADD COLUMN launch_key TEXT')
        self.db.commit()

    def add_task(self, task_id: int, role: str, sender: str, text: str, now: float) -> None:
        self.db.execute(
            "INSERT OR REPLACE INTO tasks (id, role, sender, orig_text, next_text, status, created_at, updated_at)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (task_id, role, sender, text, text, QUEUED, now, now),
        )
        self.link_message(task_id, task_id)
        self.db.commit()

    def get(self, task_id: int) -> Optional[sqlite3.Row]:
        return self.db.execute("SELECT * FROM tasks WHERE id = ?", (task_id,)).fetchone()

    def update(self, task_id: int, **fields: Any) -> None:
        fields["updated_at"] = time.time()
        cols = ", ".join(f"{k} = ?" for k in fields)
        self.db.execute(f"UPDATE tasks SET {cols} WHERE id = ?", (*fields.values(), task_id))
        self.db.commit()

    def active(self) -> list[sqlite3.Row]:
        marks = ",".join("?" * len(TERMINAL))
        return self.db.execute(
            f"SELECT * FROM tasks WHERE status NOT IN ({marks}) ORDER BY created_at", tuple(TERMINAL)
        ).fetchall()

    def link_message(self, message_id: int, task_id: int) -> None:
        self.db.execute("INSERT OR REPLACE INTO task_messages VALUES (?, ?)", (message_id, task_id))
        self.db.commit()

    def task_for_message(self, message_id: int) -> Optional[int]:
        row = self.db.execute("SELECT task_id FROM task_messages WHERE message_id = ?", (message_id,)).fetchone()
        return row["task_id"] if row else None


# ─────────────────────────────── Движок ───────────────────────────────

STATUS_LINE = {
    QUEUED: "🟡 в очереди",
    RUNNING: "🔵 в работе",
    NEEDS_INPUT: "❓ ждёт ответов",
    MERGING: "🟢 проверки пройдены — вливается",
    CONFLICT: "🟠 конфликт при вливании",
    DEPLOY_FAILED: "⛔ деплой упал",
    FAILED: "🔴 не удалось довести",
    STALLED: "⚪ нет движения",
    DONE: "✅ влито на прод",
    ANSWERED: "💬 ответ дан",
    CANCELLED: "✖️ отменена",
}

CANCEL_BTN = lambda tid: {"inline_keyboard": [[{"text": "❌ Отменить", "callback_data": f"task_cancel:{tid}"}]]}
RESTART_BTN = lambda tid: {"inline_keyboard": [[{"text": "🔁 Перезапустить", "callback_data": f"task_restart:{tid}"}]]}


@dataclass
class Telegram:
    """Адаптер к функциям relay.py (send/edit) — чтобы движок можно было тестировать без сети."""
    send: Callable[..., Optional[int]]     # send(text, reply_markup=None, reply_to=None) -> message_id
    edit: Callable[..., Optional[bool]]    # edit(message_id, text, reply_markup=None) -> False при сбое


class Engine:
    def __init__(
        self,
        cfg: EngineConfig,
        store: Store,
        tg: Telegram,
        gh: Optional[GitHub],
        fire: Callable[[str], tuple[str, Any, Optional[float]]],
        is_paused: Callable[[], bool],
        now: Callable[[], float] = time.time,
        execution_status: Optional[Callable[[str], str]] = None,
        launch_execution: Optional[Callable[[str, str], tuple[str, Any, Optional[float]]]] = None,
    ) -> None:
        self.cfg = cfg
        self.store = store
        self.tg = tg
        self.gh = gh
        self.fire = fire
        self.is_paused = is_paused
        self.now = now
        self.execution_status = execution_status
        self.launch_execution = launch_execution
        self._last_poll = 0.0

    # ── приём задач ──

    def submit(self, role: str, sender: str, body: str) -> Optional[int]:
        """Новая задача: карточка в чате (её id = id задачи) и постановка в очередь."""
        card_id = self.tg.send("🟡 Принято, ставлю в очередь…")
        if not card_id:
            return None
        header = f"[отправитель: {role}]\n[telegram_launch_message_id: {card_id}]"
        self.store.add_task(card_id, role, sender, f"{header}\n{body}", self.now())
        self.tick(force_github=False)
        return card_id

    def continue_task(self, task_id: int, role: str, sender: str, body: str, message_id: int) -> str:
        """Реплай на сообщение задачи — продолжение этой задачи (ответы на вопросы, уточнение).
        Возвращает текст ответа для чата ("" — ответ не нужен)."""
        task = self.store.get(task_id)
        if task is None:
            return ""
        self.store.link_message(message_id, task_id)
        if task["status"] == RUNNING:
            return f"Задача #{task_id} сейчас в работе — вторую сессию не запускаю. Напишите уточнение реплаем, когда она остановится."
        if task["status"] == QUEUED:
            self.store.update(task_id, next_text=f"{task['next_text']}\n\n[дополнение от {role}]\n{body}")
            return f"Добавил к задаче #{task_id} в очереди."
        text = (
            f"[отправитель: {role}]\n[telegram_launch_message_id: {task_id}]\n"
            f"[продолжение задачи #{task_id}] Ответ/уточнение по задаче с Pipeline-Task: {task_id}. "
            f"Найди её issue/ветку/спеку и продолжи по develop-feature.md:\n{body}"
        )
        questions = [k for k in json.loads(task["notified"]) if k.startswith(("q:", "reply:"))]
        self.store.update(task_id, next_text=text, status=QUEUED, fire_after=0, note=None, launch_key=None, notified=json.dumps(questions))
        self.render(task_id)
        self.tick(force_github=False)
        return ""

    def cancel(self, task_id: int) -> str:
        task = self.store.get(task_id)
        if task is None or task["status"] != QUEUED:
            return "Отменить можно только задачу в очереди"
        self.store.update(task_id, status=CANCELLED)
        self.render(task_id)
        return "Задача отменена"

    def restart(self, task_id: int) -> str:
        task = self.store.get(task_id)
        if task is None or task["status"] not in (FAILED, STALLED, CONFLICT):
            return "Перезапуск доступен только для задачи со сбоем"
        if task["snapshot_key"]:
            text = (
                f"[отправитель: {task['role']}]\n[telegram_launch_message_id: {task_id}]\n"
                f"[продолжение задачи #{task_id}] Перезапуск после статуса «{STATUS_LINE[task['status']]}». "
                f"Найди issue/PR с Pipeline-Task: {task_id}, разберись, что не так, и доведи до pipeline:ready "
                f"по develop-feature.md (включая починку pipeline:conflict / снятие pipeline:failed)."
            )
        else:
            text = task["orig_text"]
        self.store.update(task_id, next_text=text, status=QUEUED, fire_after=0, note=None, launch_key=None)
        self.render(task_id)
        self.tick(force_github=False)
        return "Задача поставлена в очередь заново"

    # ── основной цикл ──

    def tick(self, force_github: bool = True) -> None:
        now = self.now()
        if force_github and self.gh and now - self._last_poll >= self.cfg.poll_interval:
            self._last_poll = now
            self._track_all()
        self._check_executions()
        self._schedule()

    def _check_executions(self) -> None:
        if not self.execution_status:
            return
        for task in self.store.active():
            if task['execution_id'] and task['status'] in (RUNNING, STALLED):
                if self.execution_status(task['execution_id']) == 'failed':
                    self.store.update(task['id'], status=FAILED, note='Codex-воркер завершился с ошибкой; проверьте закрытый журнал')
                    self.render(task['id'])

    def _track_all(self) -> None:
        tracked = [t for t in self.store.active() if t["status"] != QUEUED]
        if not tracked:
            return
        try:
            runs = self.gh.deploy_runs()
        except Exception:
            log.exception("GitHub: не удалось получить прогоны деплоя")
            return
        for task in tracked:
            try:
                snap = collect_snapshot(self.gh, task["id"], runs)
            except Exception:
                log.exception("GitHub: не удалось собрать состояние задачи #%s", task["id"])
                continue
            self._apply(task, snap)

    def _apply(self, task: sqlite3.Row, snap: Snapshot) -> None:
        tid, now = task["id"], self.now()
        key = snap.key()
        fields: dict[str, Any] = {}
        if key != task["snapshot_key"]:
            fields.update(snapshot_key=key, progress_at=now, snapshot_json=json.dumps(_snap_json(snap)))
        progress_at = fields.get("progress_at") or task["progress_at"] or task["fired_at"] or now

        derived = derive_status(snap)
        status = task["status"]
        stalled = now - progress_at > self.cfg.stall_hours * 3600
        if status == RUNNING and _without_checks(key) == _without_checks(task["fired_key"]) and derived in (CONFLICT, FAILED, NEEDS_INPUT, DEPLOY_FAILED):
            # Сессию только что запустили чинить/продолжать — старые метки снимет она сама.
            # Пока следы в GitHub не изменились, задача в работе.
            derived = RUNNING
        if derived is None:
            if status == RUNNING and self._deliver_reply(task):
                status = ANSWERED
            elif status == RUNNING and now - (task["fired_at"] or now) > self.cfg.traceless_stall_minutes * 60:
                status = STALLED
        elif derived == RUNNING and stalled and leftover_issues(snap):
            # Всё влито и задеплоено, а оставшиеся issue без PR не меняются STALL_HOURS —
            # роутина над ними не работает: задача готова, остаток перечисляем в итоге.
            status = DONE
        elif derived == RUNNING and status in (STALLED, FAILED) and not fields:
            pass  # ничего не поменялось — остаёмся в сбое до перезапуска
        elif derived == RUNNING and stalled:
            status = STALLED
        else:
            status = derived

        notified = set(json.loads(task["notified"]))
        if (status in (RUNNING, STALLED) and derived == RUNNING and "nudge:ready" not in notified
                and now - progress_at > self.cfg.nudge_minutes * 60 and ready_forgotten(snap)):
            prs = ", ".join(f"#{p.number}" for p in snap.prs if p.state == "open" and READY not in p.labels)
            fields.update(
                next_text=(
                    f"[отправитель: architect]\n[telegram_launch_message_id: {tid}]\n"
                    f"[продолжение задачи #{tid}] PR {prs} открыты, pr-check зелёный, но pipeline:ready не поставлен — "
                    f"сессия, похоже, оборвалась. Продолжи по develop-feature.md: проверь, что Шаг 4 пройден "
                    f"и критерии приёмки выполнены, затем Шаг 5.1 (pipeline:ready). Если что-то не доделано — доделай."
                ),
                notified=json.dumps(sorted(notified | {"nudge:ready"})),
                fire_after=0,
                launch_key=None,
            )
            status = QUEUED

        if status == CONFLICT and task["status"] != CONFLICT and task["repair_count"] == 0:
            # Первая ступень починки — сама роутина (GIT_WORKFLOW.md §10, «Конфликты»).
            prs = ", ".join(f"#{p.number}" for p in snap.prs if L_CONFLICT in p.labels)
            fields.update(
                next_text=(
                    f"[отправитель: architect]\n[telegram_launch_message_id: {tid}]\n"
                    f"[продолжение задачи #{tid}] Оркестратор поставил pipeline:conflict на {prs} "
                    f"(причина — в последнем комментарии 🤖 pipeline-merge в PR). Почини по develop-feature.md: "
                    f"подтяни master, разреши конфликт, зелёный pr-check, сними pipeline:conflict, поставь pipeline:ready."
                ),
                repair_count=task["repair_count"] + 1,
                fire_after=0,
                launch_key=None,
            )
            status = QUEUED

        if status != task["status"]:
            log.info("Задача #%s: %s → %s", tid, task["status"], status)
            fields["status"] = status
        if fields:
            self.store.update(tid, **fields)
        self._notify(tid, snap)
        self.render(tid, snap)

    def _deliver_reply(self, task: sqlite3.Row) -> bool:
        """Прогон без своих issue/PR оставил 🤖 pipeline-reply <id> — переслать ответ в чат.
        Уже доставленные ответы (reply:<id> в notified) пропускаем — у продолжения задачи они уже в чате.
        True — ответ доставлен, задача закрыта; при сбое отправки повторим на следующем проходе."""
        tid = task["id"]
        sent = set(json.loads(task["notified"]))
        try:
            replies = [r for r in self.gh.find_replies(tid) if f"reply:{r[0]}" not in sent]
        except Exception:
            log.exception("GitHub: не удалось проверить pipeline-reply задачи #%s", tid)
            return False
        if not replies:
            return False
        cid, _, text = max(replies, key=lambda r: r[1])
        mid = self.tg.send(f"💬 Задача #{tid}:\n\n{text}\n\nЕсли нужно продолжить — ответьте реплаем на это сообщение.", reply_to=tid)
        if not mid:
            return False
        self.store.link_message(mid, tid)
        self.store.update(tid, notified=json.dumps(sorted(sent | {f"reply:{cid}"})))
        return True

    def _schedule(self) -> None:
        active = self.store.active()
        def executing(task):
            return bool(self.execution_status and task['execution_id'] and
                        self.execution_status(task['execution_id']) in ('accepted', 'running'))

        running = sum(1 for t in active if t["status"] in SLOT_STATUSES or executing(t))
        now = self.now()
        for task in active:
            if running >= self.cfg.max_parallel:
                break
            if task["status"] != QUEUED or task["fire_after"] > now:
                continue
            if executing(task):
                continue
            if self.is_paused():
                if task["note"] != "paused":
                    self.store.update(task["id"], note="paused")
                    self.render(task["id"])
                break
            if self.launch_execution:
                launch_key = task['launch_key'] or uuid.uuid4().hex
                # Сначала durable-заявка, потом побочный эффект. Повтор после падения
                # между запуском процесса и записью RUNNING использует тот же ключ.
                self.store.update(task['id'], launch_key=launch_key)
                outcome, payload, retry_after = self.launch_execution(task['next_text'], launch_key)
            else:
                outcome, payload, retry_after = self.fire(task["next_text"])
            if outcome == "ok":
                self.store.update(
                    task["id"],
                    status=RUNNING,
                    fired_at=now,
                    progress_at=now,
                    fired_key=task["snapshot_key"],
                    session_url=payload.get("session_url", ""),
                    execution_id=payload.get("execution_id"),
                    note=None,
                )
                running += 1
            elif outcome == "retry":
                self.store.update(task["id"], fire_after=now + (retry_after or 60), note=f"лимит воркера, повтор {payload}")
            else:
                self.store.update(task["id"], status=FAILED, note=f"воркер не запустился: {payload}")
            self.render(task["id"])
        # Позиции в очереди сдвигаются — перерисовать карточки ожидающих (render не трогает чат без изменений).
        for task in self.store.active():
            if task["status"] == QUEUED:
                self.render(task["id"])

    # ── чат ──

    def _notify(self, tid: int, snap: Snapshot) -> None:
        task = self.store.get(tid)
        sent = set(json.loads(task["notified"]))
        status = task["status"]
        out = []

        for issue in snap.issues:
            for cid, body in issue.questions:
                if f"q:{cid}" not in sent and status == NEEDS_INPUT:
                    text = body.lstrip()[len(QUESTIONS_MARKER):].strip()
                    out.append((f"q:{cid}", f"❓ Задача #{tid} — нужны ответы, чтобы продолжить:\n\n{text}\n\nОтветьте реплаем на это сообщение.", None))

        if status == DONE and "done" not in sent:
            lines = [f"✅ Задача #{tid} реализована и влита на прод — можно проверять."]
            for p in sorted(snap.prs, key=lambda p: p.number):
                if p.state == "merged":
                    lines.append(f"• {p.title} — {p.url}")
            deploys = [p.deploy_url for p in snap.prs if p.deploy_url]
            if deploys:
                lines.append(f"Деплой: {deploys[-1]}")
            leftover = leftover_issues(snap)
            if leftover:
                lines.append("Открытые issue с маркером задачи, но без PR — в задачу не вошли:")
                lines += [f"• #{i.number} {i.title}" for i in leftover]
            out.append(("done", "\n".join(lines), None))
        if status == DEPLOY_FAILED and "deploy_failed" not in sent:
            urls = " ".join(p.deploy_url for p in snap.prs if p.deploy == "failure" and p.deploy_url)
            out.append(("deploy_failed", f"⛔ Задача #{tid} — деплой упал, автомерж остановлен (issue pipeline:halt). Нужен человек. {urls}".strip(), None))
        if status == CONFLICT and "conflict" not in sent:
            out.append(("conflict", f"🟠 Задача #{tid} — конфликт при вливании, автопочинка не помогла. Нужен человек или «Перезапустить».", RESTART_BTN(tid)))
        if status == FAILED and f"failed:{task['note']}" not in sent:
            out.append((f"failed:{task['note']}", f"🔴 Задача #{tid} — не удалось довести{': ' + task['note'] if task['note'] else ''}. Подробности — в issue/PR задачи.", RESTART_BTN(tid)))
        if status == STALLED and "stalled" not in sent:
            out.append(("stalled", f"⚪ Задача #{tid} — нет движения в GitHub. Проверьте сессию или перезапустите.", RESTART_BTN(tid)))

        for key, text, markup in out:
            mid = self.tg.send(text, reply_markup=markup, reply_to=tid)
            if mid:
                self.store.link_message(mid, tid)
                sent.add(key)
        if out:
            self.store.update(tid, notified=json.dumps(sorted(sent)))

    def render(self, tid: int, snap: Optional[Snapshot] = None) -> None:
        task = self.store.get(tid)
        status = task["status"]
        lines = [f"Задача #{tid} — {STATUS_LINE[status]}"]
        if status == FAILED and task['note']:
            lines.append(task['note'])
        if status == QUEUED:
            if task["note"] == "paused":
                lines[0] += " (конвейер на паузе)"
            elif task["note"]:
                lines[0] += f" ({task['note']})"
            else:
                pos = [t["id"] for t in self.store.active() if t["status"] == QUEUED].index(tid) + 1
                lines[0] += f", позиция {pos}"
        snap_data = _snap_json(snap) if snap else json.loads(task["snapshot_json"] or "{}")
        for p in sorted(snap_data.get("prs", []), key=lambda p: p["number"]):
            mark = {"merged": "✅" if p["deploy"] in ("success", "not_needed") else "🚀", "closed": "✖️"}.get(
                p["state"], "🟢" if READY in p["labels"] else "⏳"
            )
            lines.append(f"{mark} {p['title']} (#{p['number']})")
        if task["session_url"] and status not in TERMINAL:
            lines.append(f"Сессия: {task['session_url']}")
        text = "\n".join(lines)

        markup = None
        if status == QUEUED:
            markup = CANCEL_BTN(tid)
        elif status in (FAILED, STALLED, CONFLICT):
            markup = RESTART_BTN(tid)
        render_key = text + json.dumps(markup)
        if render_key != task["card_text"]:
            # Запоминаем только успешно отрисованную карточку — иначе при сбое сети повторим на следующем проходе.
            if self.tg.edit(tid, text, reply_markup=markup or {"inline_keyboard": []}) is not False:
                self.store.update(tid, card_text=render_key)

    def queue_summary(self) -> str:
        active = self.store.active()
        running = [t for t in active if t["status"] in SLOT_STATUSES]
        lines = [f"📋 Конвейер: в работе {len(running)} из {self.cfg.max_parallel}"]
        if self.is_paused():
            lines.append("⏸️ Приём задач на паузе (админ-панель)")
        if self.gh:
            try:
                halts = self.gh.halt_issues()
                if halts:
                    lines.append(f"⛔ Автомерж остановлен: #{halts[0]['number']} {halts[0]['title']}")
            except Exception:
                log.exception("GitHub: не удалось проверить pipeline:halt")
        if not active:
            lines.append("Активных задач нет.")
        old = self.now() - self.cfg.summary_stalled_hours * 3600
        for t in active:
            if t["status"] == STALLED and max(t["progress_at"] or 0, t["fired_at"] or 0) < old:
                continue
            first = t["orig_text"].split("\n")
            # Пропускаем служебные строки: [заголовки], «--- Сообщение N ---» у пачки сообщений.
            summary = next((l for l in first if l and not l.startswith(("[", "---"))), "")[:70]
            lines.append(f"• #{t['id']} {STATUS_LINE[t['status']]} — {summary}")
        return "\n".join(lines)


def _without_checks(key: Optional[str]) -> Optional[str]:
    """Ключ снимка без статуса pr-check. Запущенная чинить сессия сначала пушит фикс (pr-check
    pending → success), а метку снимает потом — смена проверок ещё не итог её работы (#5384)."""
    if not key:
        return key
    data = json.loads(key)
    data["p"] = [p[:4] for p in data["p"]]
    return json.dumps(data)


def _snap_json(snap: Snapshot) -> dict:
    return {
        "issues": [{"number": i.number, "title": i.title, "labels": sorted(i.labels), "state": i.state} for i in snap.issues],
        "prs": [
            {"number": p.number, "title": p.title, "labels": sorted(p.labels), "state": p.state, "deploy": p.deploy}
            for p in snap.prs
        ],
    }
