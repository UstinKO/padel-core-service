#!/usr/bin/env python3
"""Telegram-вход в SDD-конвейер Codex; состояние очереди хранит engine.py.

Исполнение идёт в отдельном checkout через codex_executor.py. Telegram polling
не ждёт завершения агента. Anthropic routine и её токены не используются.
"""

import http.client
import json
import logging
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Optional

import engine as eng
from codex_executor import CodexExecutor

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    stream=sys.stdout,
)
log = logging.getLogger("telegram-relay")


def _require_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        log.error("Отсутствует обязательная переменная окружения %s", name)
        sys.exit(1)
    return value


class Config:
    def __init__(self) -> None:
        self.bot_token = _require_env("TELEGRAM_BOT_TOKEN")
        self.allowed_chat_id = int(_require_env("ALLOWED_CHAT_ID"))
        self.allowed_thread_id = int(_require_env("ALLOWED_THREAD_ID"))
        # архитектор — прямой технический вход; заказчик — всегда через
        # мягкий specs/requests/. Промпт роутины не может отличить одно
        # от другого сам по себе, поэтому релей передаёт роль явно вместе
        # с текстом (см. handle_message/fire_codex).
        self.architect_user_id = int(_require_env("ARCHITECT_USER_ID"))
        self.customer_user_ids = {
            int(x) for x in os.environ.get("CUSTOMER_USER_IDS", "").split(",") if x.strip()
        }
        self.executor = CodexExecutor(
            spool=os.environ.get("CODEX_SPOOL_DIR", "/var/lib/padel-codex/jobs"),
            repository=_require_env("CODEX_REPOSITORY"),
            timeout=int(os.environ.get("CODEX_TIMEOUT_SECONDS", "6000")),
            binary=os.environ.get("CODEX_BINARY", "codex"),
        )
        self.state_file = os.environ.get(
            "RELAY_STATE_FILE", "/opt/padel-app/telegram-relay/state.json"
        )
        # между long-poll запросами движок опрашивает GitHub и раздаёт слоты — держим цикл коротким
        self.poll_timeout = int(os.environ.get("POLL_TIMEOUT_SECONDS", "25"))
        # emergency kill-switch: админ-панель padel-core-service переключает
        # состояние в БД, релей опрашивает его перед каждым fire. По умолчанию
        # localhost, т.к. relay всегда живёт на том же хосте, что и padel-app.
        self.pipeline_status_url = os.environ.get(
            "PIPELINE_STATUS_URL", "http://127.0.0.1:8080/api/internal/telegram-pipeline/status"
        )
        self.pipeline_shared_secret = _require_env("PIPELINE_SHARED_SECRET")
        # движок задач (LFPT-0581)
        self.github_token = _require_env("GITHUB_TOKEN_RO")  # fine-grained PAT только на чтение
        self.github_repo = os.environ.get("GITHUB_REPO", "UstinKO/padel-core-service")
        self.max_parallel = int(os.environ.get("MAX_PARALLEL", "1"))
        if self.max_parallel != 1:
            raise ValueError("CLI-воркер с общей тестовой БД требует MAX_PARALLEL=1")
        self.stall_hours = float(os.environ.get("STALL_HOURS", "3"))
        self.engine_db = os.environ.get("ENGINE_DB", os.path.join(os.path.dirname(self.state_file), "engine.db"))


MAX_FIRE_TEXT_CHARS = 65536


def load_state(path: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return {"offset": 0}
    except json.JSONDecodeError:
        log.warning("Файл состояния %s повреждён, начинаю с offset=0", path)
        return {"offset": 0}


def save_state(path: str, state: dict) -> None:
    tmp_path = path + ".tmp"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(tmp_path, "w", encoding="utf-8") as f:
        json.dump(state, f)
    os.replace(tmp_path, path)


def _http_post_json(
    url: str, headers: dict, body: Optional[dict], timeout: int
) -> tuple[int, dict]:
    """Для вызовов Telegram Bot API: сетевой сбой (обрыв, таймаут) не исключение, а status=0 —
    вызывающий код просто логирует его, проход движка не прерывается (пилот #582: RemoteDisconnected
    при обновлении карточки обрывал engine.tick)."""
    try:
        status, _retry_after, parsed = _http_post_json_full(url, headers, body, timeout)
    except (urllib.error.URLError, OSError, http.client.HTTPException) as e:
        return 0, {"error": f"{type(e).__name__}: {e}"}
    return status, parsed


def _http_post_json_full(
    url: str, headers: dict, body: Optional[dict], timeout: int
) -> tuple[int, Optional[str], dict]:
    """Как _http_post_json, но дополнительно возвращает заголовок Retry-After
    (используется только для /fire — 429 приходит с этим заголовком, не в теле)."""
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            retry_after = resp.headers.get("Retry-After")
            return resp.status, retry_after, json.loads(resp.read().decode("utf-8") or "{}")
    except urllib.error.HTTPError as e:
        retry_after = e.headers.get("Retry-After") if e.headers else None
        payload = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(payload)
        except json.JSONDecodeError:
            parsed = {"raw": payload}
        return e.code, retry_after, parsed


def format_retry_after(retry_after: Optional[str]) -> str:
    """Retry-After по HTTP-спеке — либо число секунд, либо HTTP-дата. Возвращает
    человеко-читаемую оценку "когда можно повторить"."""
    if not retry_after:
        return "через какое-то время (точное время не сообщено)"
    try:
        seconds = int(retry_after)
    except ValueError:
        try:
            from email.utils import parsedate_to_datetime
            import datetime

            when = parsedate_to_datetime(retry_after)
            seconds = int((when - datetime.datetime.now(datetime.timezone.utc)).total_seconds())
        except Exception:
            return "через какое-то время (не удалось разобрать Retry-After)"
    seconds = max(seconds, 0)
    if seconds < 90:
        return f"примерно через {seconds} сек"
    minutes = (seconds + 59) // 60
    if minutes < 90:
        return f"примерно через {minutes} мин"
    hours = round(minutes / 60, 1)
    return f"примерно через {hours} ч"


def _http_get_json(url: str, timeout: int) -> dict:
    with urllib.request.urlopen(url, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_updates(cfg: Config, offset: int) -> list[dict]:
    url = (
        f"https://api.telegram.org/bot{cfg.bot_token}/getUpdates"
        f"?offset={offset}&timeout={cfg.poll_timeout}"
        f"&allowed_updates=%5B%22message%22%2C%22callback_query%22%5D"
    )
    # long-poll timeout на стороне Telegram + небольшой запас на нашей стороне
    result = _http_get_json(url, timeout=cfg.poll_timeout + 10)
    if not result.get("ok"):
        log.error("getUpdates вернул ok=false: %s", result)
        return []
    return result.get("result", [])


def get_telegram_file_url(cfg: Config, file_id: str) -> Optional[str]:
    """getFile + прямая ссылка на скачивание. Живёт около часа (лимит Telegram) —
    достаточно, роутина скачивает файл сразу в начале сессии. Ссылка содержит
    токен бота, но роутина и так знает этот токен (шлёт им сообщения обратно),
    новой утечки секрета нет."""
    url = f"https://api.telegram.org/bot{cfg.bot_token}/getFile?file_id={file_id}"
    try:
        result = _http_get_json(url, timeout=15)
    except Exception:
        log.exception("getFile не удался для file_id=%s", file_id)
        return None
    if not result.get("ok"):
        log.warning("getFile вернул ok=false для file_id=%s: %s", file_id, result)
        return None
    file_path = result["result"]["file_path"]
    return f"https://api.telegram.org/file/bot{cfg.bot_token}/{file_path}"


START_BUTTON = {
    "inline_keyboard": [[
        {"text": "▶️ Начать задачу", "callback_data": "batch_start"},
        {"text": "📋 Очередь", "callback_data": "queue"},
    ]]
}
STOP_BUTTON = {
    "inline_keyboard": [
        [
            {"text": "⏹ Отправить в работу", "callback_data": "batch_stop"},
            {"text": "❌ Отмена", "callback_data": "batch_cancel"},
        ]
    ]
}


def send_telegram_message(
    cfg: Config, text: str, reply_markup: Optional[dict] = None, reply_to: Optional[int] = None
) -> Optional[int]:
    """Возвращает message_id отправленного сообщения (нужен для ensure_control_message
    и карточек задач), либо None при ошибке. reply_to — реплай (уведомление по задаче)."""
    url = f"https://api.telegram.org/bot{cfg.bot_token}/sendMessage"
    body = {
        "chat_id": cfg.allowed_chat_id,
        "message_thread_id": cfg.allowed_thread_id,
        "text": text,
        "link_preview_options": {"is_disabled": True},
    }
    if reply_markup is not None:
        body["reply_markup"] = reply_markup
    if reply_to is not None:
        body["reply_parameters"] = {"message_id": reply_to, "allow_sending_without_reply": True}
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=15)
    if status != 200:
        log.error("Не удалось отправить сообщение в Telegram (status=%s): %s", status, resp)
        return None
    return resp.get("result", {}).get("message_id")


def edit_telegram_message(cfg: Config, message_id: int, text: str, reply_markup: Optional[dict] = None) -> bool:
    url = f"https://api.telegram.org/bot{cfg.bot_token}/editMessageText"
    body = {
        "chat_id": cfg.allowed_chat_id,
        "message_id": message_id,
        "text": text,
        "link_preview_options": {"is_disabled": True},
    }
    if reply_markup is not None:
        body["reply_markup"] = reply_markup
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=15)
    if status == 200 or "message is not modified" in str(resp):
        return True
    log.error("editMessageText не удался (status=%s): %s", status, resp)
    return False


def delete_telegram_message(cfg: Config, message_id: int) -> None:
    url = f"https://api.telegram.org/bot{cfg.bot_token}/deleteMessage"
    body = {"chat_id": cfg.allowed_chat_id, "message_id": message_id}
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=15)
    if status != 200:
        # не критично (например, сообщение уже удалено или старше 48ч) — просто лог
        log.warning("deleteMessage не удался (status=%s): %s", status, resp)


def pin_telegram_message(cfg: Config, message_id: int) -> None:
    url = f"https://api.telegram.org/bot{cfg.bot_token}/pinChatMessage"
    body = {"chat_id": cfg.allowed_chat_id, "message_id": message_id, "disable_notification": True}
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=15)
    if status != 200:
        log.warning("pinChatMessage не удался (status=%s): %s", status, resp)


def answer_callback_query(cfg: Config, callback_query_id: str, text: str = "") -> None:
    url = f"https://api.telegram.org/bot{cfg.bot_token}/answerCallbackQuery"
    body = {"callback_query_id": callback_query_id, "text": text}
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=10)
    if status != 200:
        log.error("answerCallbackQuery не удался (status=%s): %s", status, resp)


def react_to_message(cfg: Config, message_id: int, emoji: str = "👍") -> None:
    """Лёгкое подтверждение "добавлено в буфер" без спама новыми сообщениями."""
    url = f"https://api.telegram.org/bot{cfg.bot_token}/setMessageReaction"
    body = {
        "chat_id": cfg.allowed_chat_id,
        "message_id": message_id,
        "reaction": [{"type": "emoji", "emoji": emoji}],
    }
    status, resp = _http_post_json(url, {"Content-Type": "application/json"}, body, timeout=10)
    if status != 200:
        # не критично — просто не будет "галочки" на сообщении, задача всё равно в буфере
        log.warning("setMessageReaction не удался (status=%s): %s", status, resp)


CONTROL_TEXT = (
    "Пульт конвейера.\n"
    "▶️ Начать задачу — собрать несколько сообщений/скринов в одну задачу (под сборкой появится «Отправить в работу»). "
    "Без нажатия каждое сообщение — отдельная задача.\n"
    "📋 Очередь — что в работе и что ждёт.\n"
    "Ответ реплаем на сообщение задачи — уточнение или ответы на вопросы по этой задаче."
)


def ensure_control_message(cfg: Config, state: dict) -> None:
    """Постоянная кнопка "Начать задачу" — постится один раз при первом старте
    релея (сохраняем message_id в state.json), дальше просто переиспользуется:
    inline-кнопки у старого сообщения кликабельны сколько угодно долго.
    Закрепляем (pin) — Telegram держит закреплённое сообщение отдельной
    плашкой сверху чата, не нужно искать его в истории."""
    if state.get("control_message_id"):
        if state.get("control_markup") != START_BUTTON:
            # пульт уже закреплён — обновляем на месте (появилась кнопка «Очередь», LFPT-0581)
            edit_telegram_message(cfg, state["control_message_id"], CONTROL_TEXT, reply_markup=START_BUTTON)
            state["control_markup"] = START_BUTTON
            save_state(cfg.state_file, state)
        return
    message_id = send_telegram_message(
        cfg,
        CONTROL_TEXT,
        reply_markup=START_BUTTON,
    )
    if message_id:
        state["control_message_id"] = message_id
        state["control_markup"] = START_BUTTON
        save_state(cfg.state_file, state)
        pin_telegram_message(cfg, message_id)


def retry_after_seconds(retry_after: Optional[str]) -> int:
    if not retry_after:
        return 60
    try:
        return max(int(retry_after), 1)
    except ValueError:
        try:
            from email.utils import parsedate_to_datetime
            import datetime

            when = parsedate_to_datetime(retry_after)
            return max(int((when - datetime.datetime.now(datetime.timezone.utc)).total_seconds()), 1)
        except Exception:
            return 60


def fire_codex(cfg: Config, text: str) -> tuple[str, Any, Optional[float]]:
    return cfg.executor.launch(text)


def is_pipeline_paused(cfg: Config) -> bool:
    """Fail-closed: любая неопределённость (сеть недоступна, padel-app не отвечает,
    неверный секрет) трактуется как "приостановлен" — это kill-switch, ошибаться
    нужно в сторону "не запускать", а не в сторону "запустить, несмотря ни на что"."""
    req = urllib.request.Request(
        cfg.pipeline_status_url,
        headers={"X-Internal-Secret": cfg.pipeline_shared_secret},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status != 200:
                log.warning("Проверка статуса конвейера вернула %s — считаю паузу активной", resp.status)
                return True
            body = json.loads(resp.read().decode("utf-8"))
            return bool(body.get("paused", True))
    except Exception:
        log.exception("Не удалось проверить статус конвейера — считаю паузу активной")
        return True


def resolve_role(cfg: Config, sender_id: Optional[int]) -> Optional[str]:
    if sender_id == cfg.architect_user_id:
        return "architect"
    if sender_id in cfg.customer_user_ids:
        return "customer"
    return None


RICH_MEDIA_BLOCKS = {"photo", "video", "animation", "audio", "document", "voice_note", "map"}


def rich_text_plain(node) -> str:
    """RichText (Bot API 10.1) → простой текст: строка, массив или объект с вложенным text."""
    if isinstance(node, str):
        return node
    if isinstance(node, list):
        return "".join(rich_text_plain(part) for part in node)
    if isinstance(node, dict):
        for key in ("text", "expression", "alternative_text"):
            if key in node:
                return rich_text_plain(node[key])
    return ""


def rich_blocks_lines(blocks: list, media: set) -> list[str]:
    lines = []
    for block in blocks:
        kind = block.get("type")
        if kind in RICH_MEDIA_BLOCKS:
            media.add(kind)
        for key in ("summary", "text", "expression"):
            if key in block:
                lines.append(rich_text_plain(block[key]))
        for item in block.get("items", []):
            item_lines = rich_blocks_lines(item.get("blocks", []), media) or [""]
            label = item.get("label")
            lines.append(f"{label} {item_lines[0]}" if label else item_lines[0])
            lines.extend(item_lines[1:])
        lines.extend(rich_blocks_lines(block.get("blocks", []), media))
        for row in block.get("cells", []):
            lines.append(" | ".join(rich_text_plain(cell.get("text", "")) for cell in row))
        for key in ("caption", "credit"):
            if key in block:
                lines.append(rich_text_plain(block[key]))
    return [line for line in lines if line.strip()]


def rich_message_text(rich_message: Optional[dict]) -> tuple[str, set]:
    """Текст сообщения с форматированием (поле rich_message): такие приходят без text/caption."""
    media: set = set()
    if not rich_message:
        return "", media
    return "\n".join(rich_blocks_lines(rich_message.get("blocks", []), media)), media


def build_message_fragment(cfg: Config, message: dict) -> Optional[str]:
    """Текст + пометки (медиа/пересылка) одного сообщения, без роли-заголовка —
    роль общая на весь batch/фиксированный fire, добавляется один раз снаружи."""
    rich_text, rich_media = rich_message_text(message.get("rich_message"))
    text = message.get("text") or message.get("caption") or rich_text
    if not text:
        return None

    notes = []
    if rich_media:
        notes.append(
            "в сообщении с форматированием есть вложения (" + ", ".join(sorted(rich_media)) + ")"
            " — они НЕ переданы роутине, доступен только текст; если они нужны, попроси прислать их отдельным сообщением"
        )
    file_id = None
    if message.get("photo"):
        file_id = max(message["photo"], key=lambda p: p.get("width", 0))["file_id"]
    elif message.get("document"):
        file_id = message["document"]["file_id"]

    if file_id:
        file_url = get_telegram_file_url(cfg, file_id)
        if file_url:
            notes.append(
                f"к сообщению приложено изображение/файл, ссылка на скачивание (действительна ~час): {file_url}"
                " — скачай (curl) и посмотри через Read перед тем, как разбираться в задаче, если это может быть релевантно"
            )
        else:
            notes.append("к сообщению было приложено изображение/файл, но не удалось получить ссылку на скачивание (см. лог relay) — доступен только текст")
    elif message.get("video"):
        notes.append("к сообщению приложено видео — оно НЕ передано роутине (нет смысла: Read показывает только статичные изображения), доступен только текст")

    forward_origin = message.get("forward_origin") or {}
    forward_from = message.get("forward_from")
    forwarded_name = None
    if forward_origin.get("type") == "user":
        sender_user = forward_origin.get("sender_user", {})
        forwarded_name = sender_user.get("username") or sender_user.get("first_name")
    elif forward_origin.get("type") == "hidden_user":
        forwarded_name = forward_origin.get("sender_user_name")
    elif forward_from:
        forwarded_name = forward_from.get("username") or forward_from.get("first_name")
    if forwarded_name:
        notes.append(f"переслано от {forwarded_name} — это не обязательно собственные слова отправителя")

    fragment = text
    if notes:
        fragment = "[" + "; ".join(notes) + "]\n" + text
    return fragment


def submit_fragments(cfg: Config, engine: "eng.Engine", role: str, sender_name: str, fragments: list[str], batched: bool) -> bool:
    """Задача → движок: карточка в чате (её id — номер задачи и telegram_launch_message_id),
    очередь, запуск, когда освободится слот (LFPT-0581)."""
    if batched:
        body = f"[собрано из {len(fragments)} сообщений через кнопки Начать/Отправить]\n" + "\n\n".join(
            f"--- Сообщение {i} ---\n{frag}" for i, frag in enumerate(fragments, 1)
        )
    else:
        body = fragments[0]
    return engine.submit(role, sender_name, body) is not None


def describe_message(message: dict) -> str:
    """Что пришло, без текста: по этой строке видно, почему сообщение не стало задачей."""
    text = message.get("text") or message.get("caption") or ""
    sender = message.get("from") or {}
    return (
        f"message_id={message.get('message_id')} chat={message.get('chat', {}).get('id')}"
        f" thread={message.get('message_thread_id')} from={sender.get('id')} is_bot={sender.get('is_bot')}"
        f" reply_to={(message.get('reply_to_message') or {}).get('message_id')}"
        f" text_len={len(text)} keys={sorted(message)}"
    )


def handle_message(cfg: Config, engine: "eng.Engine", batches: dict, message: dict) -> None:
    chat = message.get("chat", {})
    if chat.get("id") != cfg.allowed_chat_id:
        log.info("Пропускаю сообщение: чужой чат; %s", describe_message(message))
        return
    if message.get("message_thread_id") != cfg.allowed_thread_id:
        log.info("Пропускаю сообщение: чужая тема (ждём %s); %s", cfg.allowed_thread_id, describe_message(message))
        return

    sender = message.get("from", {})
    if sender.get("is_bot"):
        return

    sender_id = sender.get("id")
    role = resolve_role(cfg, sender_id)
    if role is None:
        log.info("Игнорирую сообщение от неразрешённого user_id=%s", sender_id)
        return

    fragment = build_message_fragment(cfg, message)
    if fragment is None:
        # системные сообщения (создание темы, вход/выход участников и т.п.),
        # либо медиа без подписи — не задача
        log.info("Пропускаю сообщение: нет text/caption; %s", describe_message(message))
        return

    sender_name = sender.get("username") or sender.get("first_name") or str(sender_id)

    # Реплай на сообщение задачи — продолжение этой задачи (ответы на вопросы, уточнение).
    # В темах форума reply_to_message есть и у обычных сообщений (корень темы) — поэтому
    # продолжением считаем только реплай на сообщение, известное движку.
    reply_to = (message.get("reply_to_message") or {}).get("message_id")
    task_id = engine.store.task_for_message(reply_to) if reply_to else None
    if task_id is not None:
        log.info("Продолжение задачи #%s от %s (%s): %s", task_id, sender_name, role, fragment[:200])
        answer = engine.continue_task(task_id, role, sender_name, fragment, message.get("message_id") or 0)
        if answer:
            send_telegram_message(cfg, answer, reply_to=message.get("message_id"))
        else:
            react_to_message(cfg, message.get("message_id"), "👌")
        return

    if sender_id in batches:
        # идёт сборка через кнопку "Начать задачу" — буферизуем, не стреляем
        batch = batches[sender_id]
        batch["fragments"].append(fragment)
        log.info("Добавлено в буфер %s (%s), сейчас %d фрагмент(ов)", sender_name, role, len(batch["fragments"]))
        message_id = message.get("message_id")
        if message_id:
            react_to_message(cfg, message_id, "👍")
        # кнопки "едут" вниз — удаляем старое сообщение со статусом/кнопками
        # и постим новое, чтобы оно всегда было последним в чате, а не выше
        # только что написанного
        old_status_id = batch.get("status_message_id")
        if old_status_id:
            delete_telegram_message(cfg, old_status_id)
        batch["status_message_id"] = send_telegram_message(
            cfg,
            f"🟢 Собираю сообщения для задачи ({len(batch['fragments'])} шт.) — жми кнопку ниже, когда закончишь.",
            reply_markup=STOP_BUTTON,
        )
        return

    log.info("Задача от %s (%s): %s", sender_name, role, fragment[:200])
    # Пауза (рубильник) не отбрасывает задачу: движок держит её в очереди до снятия паузы.
    submit_fragments(cfg, engine, role, sender_name, [fragment], batched=False)


# Когда пользователь последний раз отправил сборку кнопкой «Отправить» (user_id → time.time()).
# Ответ на нажатие иногда приходит с опозданием, кнопку жмут повторно — второе нажатие
# видит пустой буфер. Это не ошибка пользователя, «⚠️ Буфер был пуст» его только путает (#5325).
RECENT_SUBMIT_SECONDS = 180
SLOW_STEP_SECONDS = 8
recent_submits: dict = {}


def handle_callback_query(cfg: Config, engine: "eng.Engine", batches: dict, callback_query: dict) -> None:
    message = callback_query.get("message") or {}
    chat = message.get("chat", {})
    if chat.get("id") != cfg.allowed_chat_id:
        return

    callback_id = callback_query["id"]
    sender = callback_query.get("from", {})
    sender_id = sender.get("id")
    role = resolve_role(cfg, sender_id)
    if role is None:
        answer_callback_query(cfg, callback_id, "Недоступно")
        log.info("Игнорирую нажатие кнопки от неразрешённого user_id=%s", sender_id)
        return

    data = callback_query.get("data") or ""
    sender_name = sender.get("username") or sender.get("first_name") or str(sender_id)

    if data == "queue":
        answer_callback_query(cfg, callback_id)
        send_telegram_message(cfg, engine.queue_summary())
        return
    if data.startswith("task_cancel:") or data.startswith("task_restart:"):
        action, _, raw_id = data.partition(":")
        task_id = int(raw_id)
        result = engine.cancel(task_id) if action == "task_cancel" else engine.restart(task_id)
        log.info("%s (%s): %s #%s → %s", sender_name, role, action, task_id, result)
        answer_callback_query(cfg, callback_id, result)
        return

    if data == "batch_start":
        if sender_id in batches:
            answer_callback_query(cfg, callback_id, "Уже собираю — продолжай писать или нажми «Отправить в работу»")
            return
        answer_callback_query(cfg, callback_id, "Начал собирать сообщения")
        log.info("%s (%s) начал сборку batch", sender_name, role)
        status_message_id = send_telegram_message(
            cfg,
            "🟢 Собираю сообщения для задачи (0 шт.) — пиши текст, кидай скрины сколько нужно. Когда закончишь — жми кнопку ниже.",
            reply_markup=STOP_BUTTON,
        )
        batches[sender_id] = {"fragments": [], "status_message_id": status_message_id}
        return

    if data == "batch_cancel":
        batch = batches.pop(sender_id, None)
        if batch:
            status_id = batch.get("status_message_id")
            if status_id:
                delete_telegram_message(cfg, status_id)
            answer_callback_query(cfg, callback_id, "Отменено, ничего не отправлено")
            log.info("%s (%s) отменил сборку batch", sender_name, role)
            send_telegram_message(cfg, "❌ Сборка отменена, ничего не отправлено в работу.", reply_markup=START_BUTTON)
        else:
            answer_callback_query(cfg, callback_id, "Нечего отменять")
        return

    if data == "batch_stop":
        batch = batches.pop(sender_id, None)
        fragments = batch["fragments"] if batch else None
        old_status_id = batch.get("status_message_id") if batch else None

        if not fragments and time.time() - recent_submits.get(sender_id, 0) < RECENT_SUBMIT_SECONDS:
            answer_callback_query(cfg, callback_id, "Уже отправлено ✅")
            log.info("%s (%s): повторное «Отправить» после отправки — игнорирую", sender_name, role)
            return
        if not fragments:
            answer_callback_query(cfg, callback_id, "Буфер пуст — сначала напиши хотя бы одно сообщение")
            if old_status_id:
                delete_telegram_message(cfg, old_status_id)
            send_telegram_message(cfg, "⚠️ Буфер был пуст, ничего не отправлено.", reply_markup=START_BUTTON)
            return

        if old_status_id:
            delete_telegram_message(cfg, old_status_id)
        answer_callback_query(cfg, callback_id, f"Отправляю {len(fragments)} сообщений в работу")
        log.info("%s (%s) завершил сборку batch: %d фрагмент(ов)", sender_name, role, len(fragments))

        recent_submits[sender_id] = time.time()
        if not submit_fragments(cfg, engine, role, sender_name, fragments, batched=True):
            recent_submits.pop(sender_id, None)
            # не удалось даже создать карточку задачи — не теряем работу пользователя, возвращаем кнопки
            new_status_id = send_telegram_message(
                cfg,
                f"🟢 Собираю сообщения для задачи ({len(fragments)} шт.) — жми кнопку ниже, когда закончишь.",
                reply_markup=STOP_BUTTON,
            )
            batches[sender_id] = {"fragments": fragments, "status_message_id": new_status_id}
            log.info("Fire не удался, буфер %s (%s) восстановлен: %d фрагмент(ов)", sender_name, role, len(fragments))
        return

    log.warning("Неизвестный callback_data: %s", data)
    answer_callback_query(cfg, callback_id)


def main() -> None:
    cfg = Config()
    state = load_state(cfg.state_file)
    offset = state.get("offset", 0)
    log.info(
        "Релей запущен. chat_id=%s thread_id=%s architect_user_id=%s customer_user_ids=%s offset=%s",
        cfg.allowed_chat_id,
        cfg.allowed_thread_id,
        cfg.architect_user_id,
        cfg.customer_user_ids or "(пока не задан)",
        offset,
    )
    ensure_control_message(cfg, state)

    engine = eng.Engine(
        eng.EngineConfig(
            repo=cfg.github_repo,
            max_parallel=cfg.max_parallel,
            stall_hours=cfg.stall_hours,
            db_path=cfg.engine_db,
        ),
        eng.Store(cfg.engine_db),
        eng.Telegram(
            send=lambda text, reply_markup=None, reply_to=None: send_telegram_message(cfg, text, reply_markup, reply_to),
            edit=lambda message_id, text, reply_markup=None: edit_telegram_message(cfg, message_id, text, reply_markup),
        ),
        eng.GitHub(cfg.github_token, cfg.github_repo),
        fire=lambda text: fire_codex(cfg, text),
        execution_status=cfg.executor.status,
        launch_execution=cfg.executor.launch,
        is_paused=lambda: is_pipeline_paused(cfg),
    )
    log.info("Движок задач: MAX_PARALLEL=%s, STALL_HOURS=%s, db=%s", cfg.max_parallel, cfg.stall_hours, cfg.engine_db)

    # buffer сборки по кнопке "Начать задачу" — ключ user_id, значение
    # {"fragments": [...], "status_message_id": int|None} (id последнего
    # сообщения с кнопками Отправить/Отмена — пересоздаётся на каждое новое
    # сообщение, чтобы кнопки всегда были внизу чата, а не выше сборки).
    # Сохраняется в state.json — переживает рестарт релея (LFPT-0581).
    batches: dict = {int(k): v for k, v in state.get("batches", {}).items()}

    while True:
        started = time.monotonic()
        try:
            engine.tick()
        except Exception:
            log.exception("Ошибка в движке задач")
        if time.monotonic() - started > SLOW_STEP_SECONDS:
            # Пока идёт проход движка, нажатия кнопок ждут — Telegram через ~15 с считает их устаревшими.
            log.warning("Проход движка занял %.1f с", time.monotonic() - started)
        try:
            updates = get_updates(cfg, offset)
        except Exception:
            log.exception("Ошибка при опросе Telegram, пауза 5с")
            time.sleep(5)
            continue

        for update in updates:
            started = time.monotonic()
            offset = update["update_id"] + 1
            message = update.get("message")
            callback_query = update.get("callback_query")
            log.info("Обновление update_id=%s: %s", update["update_id"], ",".join(k for k in update if k != "update_id"))
            if not message and not callback_query:
                log.info("Пропускаю обновление update_id=%s: тип не обрабатывается", update["update_id"])
            try:
                if message:
                    handle_message(cfg, engine, batches, message)
                elif callback_query:
                    handle_callback_query(cfg, engine, batches, callback_query)
            except Exception:
                log.exception("Ошибка при обработке update_id=%s", update["update_id"])
            if time.monotonic() - started > SLOW_STEP_SECONDS:
                log.warning("Обработка update_id=%s заняла %.1f с", update["update_id"], time.monotonic() - started)
            state["offset"] = offset
            state["batches"] = {str(k): v for k, v in batches.items()}
            save_state(cfg.state_file, state)


if __name__ == "__main__":
    main()
