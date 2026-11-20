"""Собирает проверочный срез из действующих контрактов только в target."""

import hashlib
import json
from pathlib import Path


PROBE = Path(__file__).resolve().parent
ROOT = PROBE.parents[1]
CONTRACTS = ROOT / "docs/frontend-react/contract"
SOURCES = {
    "Discovery": "public-discovery",
    "Account": "account",
    "Player": "player-events",
    "Live": "public-live",
}


def rewrite_refs(value, prefix):
    if isinstance(value, list):
        return [rewrite_refs(item, prefix) for item in value]
    if not isinstance(value, dict):
        return value
    result = {}
    for key, item in value.items():
        if key == "$ref":
            anchor = "#/components/schemas/"
            if not item.startswith(anchor):
                raise ValueError(f"Неожиданная внешняя ссылка: {item}")
            result[key] = anchor + prefix + item.removeprefix(anchor)
        else:
            result[key] = rewrite_refs(item, prefix)
    return result


def prepare():
    schemas = {}
    manifest = {}
    for prefix, name in SOURCES.items():
        path = CONTRACTS / f"{name}.openapi.json"
        raw = path.read_bytes()
        document = json.loads(raw)
        if document["openapi"] != "3.1.0":
            raise ValueError(f"Изменилась версия OAS: {name}")
        manifest[str(path.relative_to(ROOT))] = hashlib.sha256(raw).hexdigest()
        schemas.update({prefix + key: rewrite_refs(value, prefix)
                        for key, value in document["components"]["schemas"].items()})

    target = PROBE / "target"
    target.mkdir(exist_ok=True)
    (target / "upstream-templates").mkdir(exist_ok=True)
    base = {"openapi": "3.1.0", "info": {"title": "Проба моделей", "version": "1"},
            "paths": {}, "components": {"schemas": schemas}}
    (target / "source-schemas.json").write_text(json.dumps(base, indent=2) + "\n")
    # У исходных контрактов нет allOf/readOnly/writeOnly: эти случаи явно синтетические.
    schemas["ProbeFeatures"] = {
        "type": "object",
        "properties": {
            "amount": {"type": "number"},
            "day": {"type": "string", "format": "date"},
            "moment": {"type": "string", "format": "date-time"},
            "timeZone": {"type": "string"},
            "wire": {"type": "string", "enum": ["in-progress", "done"]},
            "serverValue": {"type": "string", "readOnly": True},
            "password": {"type": "string", "writeOnly": True},
            "optionalValue": {"type": ["string", "null"]},
            "tags": {"type": "array", "items": {"type": "string"}},
        },
    }
    schemas["ProbeExtendedClub"] = {"allOf": [
        {"$ref": "source-schemas.json#/components/schemas/DiscoveryClub"},
        {"type": "object", "properties": {"marker": {"type": "string"}}},
    ]}
    (target / "probe.openapi.json").write_text(json.dumps(base, indent=2) + "\n")
    (target / "input-sha256.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    prepare()
