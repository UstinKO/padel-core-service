#!/usr/bin/env python3
"""Обновляет параметры Meta и ссылку WhatsApp в существующем dotenv без вывода значений."""
import os
from pathlib import Path
import re
import stat
import sys
import tempfile


def configure(path, token, whatsapp_url=""):
    if not re.fullmatch(r"[A-Za-z0-9._|:-]{20,8192}", token):
        raise ValueError("Некорректный или отсутствующий секрет Meta")
    if whatsapp_url and not re.fullmatch(r"https://wa\.me/[1-9][0-9]{6,14}", whatsapp_url):
        raise ValueError("Некорректная ссылка WhatsApp")
    if path.is_symlink() or not path.is_file():
        raise ValueError("Ожидается обычный существующий файл .env")
    values = {
        "META_ENABLED": "true",
        "META_PIXEL_ID": "930260823500064",
        "META_CAPI_ACCESS_TOKEN": "'" + token + "'",
        "META_API_VERSION": "v26.0",
        "META_TEST_MODE": "false",
        "META_TEST_EVENT_CODE": "",
    }
    if whatsapp_url:
        values["GUEST_CLAIM_WHATSAPP_URL"] = whatsapp_url
    original = path.read_text()
    lines = []
    for line in original.splitlines(keepends=True):
        match = re.match(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=", line)
        if not match or match[1] not in values:
            lines.append(line)
    content = "".join(lines)
    if content and not content.endswith("\n"):
        content += "\n"
    content += "".join(key + "=" + value + "\n" for key, value in values.items())
    metadata = path.stat()
    descriptor, temporary = tempfile.mkstemp(prefix=".meta-env-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w") as target:
            os.fchmod(target.fileno(), stat.S_IMODE(metadata.st_mode) & 0o600)
            os.fchown(target.fileno(), metadata.st_uid, metadata.st_gid)
            target.write(content)
            target.flush()
            os.fsync(target.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


if __name__ == "__main__":
    try:
        configure(Path(sys.argv[1]), os.environ.get("META_CAPI_ACCESS_TOKEN", ""),
                  os.environ.get("GUEST_CLAIM_WHATSAPP_URL", ""))
    except (ValueError, OSError, IndexError):
        sys.exit("Не удалось обновить параметры окружения; значения не выводятся")
