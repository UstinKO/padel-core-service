"""Проверки карты томов, целостности архивов и единственного писателя."""
import copy
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import tarfile

LABEL = "com.padel.swarm-data"
ROLES = {"database": "/var/lib/postgresql/data", "grafana": "/var/lib/grafana",
         "prometheus": "/prometheus", "loki": "/loki"}


def validate_mapping(mapping):
    if not isinstance(mapping, dict) or set(mapping) != set(ROLES):
        raise ValueError("Карта должна содержать database, grafana, prometheus, loki")
    if any(not isinstance(name, str) or not re.fullmatch(r"[a-zA-Z0-9][a-zA-Z0-9_.-]+", name)
           for name in mapping.values()):
        raise ValueError("Недопустимое физическое имя volume")
    if len(set(mapping.values())) != len(mapping):
        raise ValueError("Один volume нельзя назначить разным хранилищам")


def external_volumes(stack, mapping):
    validate_mapping(mapping)
    if not set(ROLES).issubset(stack.get("volumes", {})):
        raise ValueError("В stack отсутствуют ожидаемые логические тома")
    result = copy.deepcopy(stack)
    for role, name in mapping.items():
        result["volumes"][role] = {"external": True, "name": name}
    return result


def stream_digest(stream):
    hasher = hashlib.sha256()
    for chunk in iter(lambda: stream.read(1024 * 1024), b""):
        hasher.update(chunk)
    return hasher.hexdigest()


def digest(path):
    with Path(path).open("rb") as stream:
        return stream_digest(stream)


def verify_archive(path, expected):
    if digest(path) != expected:
        raise ValueError("Контрольная сумма backup не совпадает; восстановление запрещено")


def tar_fingerprint(path):
    records = []
    seen = set()
    with tarfile.open(path) as archive:
        for member in archive.getmembers():
            name = PurePosixPath(member.name)
            if str(name) in seen:
                raise ValueError("Повторяющийся путь в архиве")
            seen.add(str(name))
            if name.is_absolute() or ".." in name.parts or not (
                    member.isfile() or member.isdir() or member.issym() or member.islnk()):
                raise ValueError("Небезопасный объект в архиве")
            if member.issym() or member.islnk():
                target = PurePosixPath(member.linkname)
                if target.is_absolute() or ".." in target.parts:
                    raise ValueError("Небезопасная ссылка в архиве")
            checksum = ""
            if member.isfile():
                with archive.extractfile(member) as stream:
                    checksum = stream_digest(stream)
            records.append([str(name), member.type.decode("ascii"), member.uid, member.gid,
                            member.mode, member.size, member.linkname, checksum])
    return hashlib.sha256(json.dumps(sorted(records), sort_keys=True).encode()).hexdigest()


class VolumeStore:
    def __init__(self, engine, images, owner, directory):
        self.engine = engine
        self.images = images
        self.owner = owner
        self.directory = directory

    def owned(self, name):
        info = json.loads(self.engine("volume", "inspect", name))[0]
        if ((info.get("Labels") or {}).get(LABEL) != self.owner or info["Driver"] != "local"
                or info.get("Options")):
            raise ValueError("Чужой или неподдерживаемый volume: " + name)
        return info

    def quiescent(self, name):
        """Проверить не только процессы, но и отложенные Swarm tasks."""
        self.owned(name)
        ids = self.engine("ps", "-aq", "--filter", "volume=" + name).splitlines()
        if ids:
            for item in json.loads(self.engine("inspect", *ids)):
                writable = any(mount.get("Name") == name and mount.get("RW") for mount in item["Mounts"])
                restart = item["HostConfig"]["RestartPolicy"]["Name"]
                if writable and (item["State"]["Running"] or restart not in ("no", "")):
                    raise ValueError("Том занят писателем или перезапускаемым контейнером: " + name)
        services = self.engine("service", "ls", "-q").splitlines()
        if services:
            for item in json.loads(self.engine("service", "inspect", *services)):
                spec = item["Spec"]
                active = spec["Mode"].get("Replicated", {}).get("Replicas", 1) > 0
                if active and any(mount.get("Source") == name for mount in
                                  spec["TaskTemplate"]["ContainerSpec"].get("Mounts", [])):
                    raise ValueError("Swarm всё ещё может запустить писателя: " + name)

    def create(self, name):
        # Docker volume create иначе молча переиспользует существующее имя.
        if name in self.engine("volume", "ls", "--format", "{{.Name}}").splitlines():
            raise ValueError("Volume уже существует: " + name)
        self.engine("volume", "create", "--label", LABEL + "=" + self.owner, name)
        self.owned(name)

    def helper(self, name, command, writable=False):
        self.quiescent(name)
        return self.engine("run", "--rm", "--network", "none", "--label", LABEL + "=" + self.owner,
                           "--tmpfs", "/var/lib/postgresql/data",
                           "--mount", "type=volume,source=" + name + ",target=/data" + ("" if writable else ",readonly"),
                           "--mount", "type=bind,source=" + self.directory + ",target=/archive",
                           self.images["postgres"], "sh", "-ec", command)

    def empty(self, name):
        self.helper(name, 'test -z "$(ls -A /data)"')

    def archive(self, name, filename):
        if not re.fullmatch(r"[a-z0-9-]+\.tar", filename):
            raise ValueError("Недопустимое имя архива")
        self.helper(name, "tar --sort=name --numeric-owner -cpf /archive/" + filename + " -C /data .")

    def restore(self, name, filename, expected_digest):
        if not re.fullmatch(r"[a-z0-9-]+\.tar", filename):
            raise ValueError("Недопустимое имя архива")
        if self.helper(name, "sha256sum /archive/" + filename).split()[0] != expected_digest:
            raise ValueError("Архив внутри Engine повреждён; запись в том запрещена")
        self.empty(name)
        self.helper(name, "tar --numeric-owner --same-owner --preserve-permissions -xpf /archive/" + filename + " -C /data", writable=True)


def mount_inventory(engine, containers):
    """Никогда не сохранять Config.Env и полный docker inspect."""
    result = []
    for name in containers:
        item = json.loads(engine("inspect", name))[0]
        image = json.loads(engine("image", "inspect", item["Image"]))[0]
        result.append({"container": item["Name"].lstrip("/"), "image_id": item["Image"],
                       "repo_digests": image.get("RepoDigests", []),
                       "mounts": [{key: mount.get(key) for key in
                                   ["Type", "Name", "Source", "Destination", "RW", "Driver"]}
                                  for mount in item["Mounts"]]})
    return result
