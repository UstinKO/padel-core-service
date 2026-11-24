#!/usr/bin/env python3
"""Swarm-стенд: переносимый stack или изолированный локальный вложенный Engine."""
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import secrets
import shutil
import socket
import ssl
import subprocess
import time
import urllib.request
import urllib.error
import uuid
from data_volumes import external_volumes, validate_mapping

DIRECTORY = Path(__file__).resolve().parent
ROOT = DIRECTORY.parents[1]
RUNTIME = DIRECTORY / "runtime"
ENGINE = "padel-swarm-lab-engine"
STACK = "padel-lab"
LABEL = "com.padel.swarm-lab"
DOCKER = []


def run(arguments, **kwargs):
    return subprocess.check_output(arguments, text=True, encoding="utf-8", **kwargs).strip()


def host(*arguments, **kwargs):
    return run([*DOCKER, *arguments], **kwargs)


def engine(*arguments, **kwargs):
    return host("exec", ENGINE, "docker", *arguments, **kwargs)


def state():
    return json.loads((RUNTIME / "state.json").read_text(encoding="utf-8"))


def owned_engine():
    data = json.loads(host("inspect", ENGINE))[0]
    if data["Config"]["Labels"].get(LABEL) != state()["instance"]:
        raise ValueError("Контейнер Engine не принадлежит этому стенду")
    return data


def initialize():
    RUNTIME.mkdir(mode=0o700, exist_ok=False)
    identity = str(uuid.uuid4())
    (RUNTIME / "state.json").write_text(json.dumps({"instance": identity}), encoding="utf-8")
    values = {
        "database_password": secrets.token_urlsafe(32),
        "fixture_password": secrets.token_urlsafe(32),
        "jwt_secret": secrets.token_urlsafe(48),
        "redis_password": secrets.token_urlsafe(32),
        "rest_peer_key": secrets.token_urlsafe(32),
        "grafana_password": secrets.token_urlsafe(32),
        "instance_id": identity,
    }
    for name, value in values.items():
        path = RUNTIME / name
        path.write_text(value, encoding="utf-8")
        path.chmod(0o600)
    executable = shutil.which("openssl")
    if executable is None and os.name == "nt":
        executable = "C:/Program Files/Git/usr/bin/openssl.exe"
    subprocess.run([
        executable or "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes",
        "-days", "30", "-keyout", str(RUNTIME / "tls_key"),
        "-out", str(RUNTIME / "tls_certificate"), "-subj", "/CN=localhost",
        "-addext", "subjectAltName=IP:127.0.0.1,DNS:localhost",
    ], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    (RUNTIME / "tls_key").chmod(0o600)


def wait_for(check, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (subprocess.CalledProcessError, OSError):
            pass
        time.sleep(3)
    raise TimeoutError("Стенд не достиг ожидаемого состояния")


def start(images):
    existing = host("ps", "-aq", "--filter", "name=^/" + ENGINE + "$")
    if existing:
        owned_engine()
        host("start", ENGINE)
    else:
        host("run", "-d", "--privileged", "--name", ENGINE,
             "--label", LABEL + "=" + state()["instance"],
             "--memory", "4g", "--cpus", "2",
             "-p", "127.0.0.1:18080:80", "-p", "127.0.0.1:18443:443",
             "-e", "DOCKER_TLS_CERTDIR=", images["engine"],
             "dockerd", "--host=unix:///var/run/docker.sock")
    wait_for(lambda: bool(engine("info", "--format", "{{.ServerVersion}}")), 90)
    if engine("info", "--format", "{{.Swarm.LocalNodeState}}") == "inactive":
        engine("swarm", "init", "--advertise-addr", "eth0")
    prepare_rest_secret()
    host("exec", ENGINE, "mkdir", "-p", "/lab/runtime")
    host("cp", str(RUNTIME) + "/.", ENGINE + ":/lab/runtime")
    for name in ["nginx.conf", "prometheus.yml", "datasources.yml"]:
        host("cp", str(DIRECTORY / name), ENGINE + ":/lab/" + name)
    host("cp", str(ROOT / "automation/ui-preview/init-db.sql"), ENGINE + ":/lab/init-db.sql")


def prepare_rest_secret():
    """Добавить резерв ключа при обновлении прежнего локального стенда."""
    state()
    path = RUNTIME / "rest_peer_key"
    if not path.exists():
        with path.open("x", encoding="utf-8") as stream:
            stream.write(secrets.token_urlsafe(32))
        path.chmod(0o600)


def file_source(kind, name, item):
    if kind == "secrets":
        return RUNTIME / name
    if name == "init_db":
        return ROOT / "automation/ui-preview/init-db.sql"
    return DIRECTORY / Path(item["file"]).name


def render(images, app_image, revision, local_files=False, volume_map=None):
    data = json.loads((DIRECTORY / "stack.json").read_text(encoding="utf-8"))
    replacements = dict(images, app=app_image, revision=revision)

    def replace(value):
        if isinstance(value, dict):
            return {key: replace(item) for key, item in value.items()}
        if isinstance(value, list):
            return [replace(item) for item in value]
        if isinstance(value, str) and value.startswith("${"):
            return replacements[value[2:-1]]
        return value

    data = replace(data)
    instance = state()["instance"].replace("-", "")[:12]
    for kind in ["configs", "secrets"]:
        for name, item in data[kind].items():
            source = file_source(kind, name, item)
            content = source.read_bytes()
            item["name"] = "padel-lab-" + instance + "-" + name + "-" + hashlib.sha256(content).hexdigest()[:12]
            if local_files:
                item["file"] = source.resolve().as_posix()
    if local_files:
        for port in data["services"]["gateway"]["ports"]:
            port["published"] = {80: 18080, 443: 18443}[port["target"]]
    return external_volumes(data, volume_map) if volume_map is not None else data


def deploy(images, jar):
    owned_engine()
    revision = run(["git", "-C", str(ROOT), "rev-parse", "HEAD"])
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    shutil.copyfile(jar, RUNTIME / "app.jar")
    shutil.copyfile(DIRECTORY / "Dockerfile", RUNTIME / "Dockerfile")
    (RUNTIME / ".dockerignore").write_text("*\n!app.jar\n!Dockerfile\n", encoding="utf-8")
    host("cp", str(RUNTIME) + "/.", ENGINE + ":/lab/runtime")
    tag = "padel-swarm-lab-app:" + digest[:16]
    engine("build", "--provenance=false", "-t", tag, "--build-arg", "JAR_SHA256=" + digest, "/lab/runtime")
    image = engine("image", "inspect", tag, "--format", "{{.Id}}")
    for key, reference in images.items():
        if key != "engine":
            try:
                engine("image", "inspect", reference, stderr=subprocess.DEVNULL)
            except subprocess.CalledProcessError:
                print("Загрузка " + key, flush=True)
                engine("pull", reference)
    data = render(images, image, revision)
    path = RUNTIME / "stack.json"
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
    host("cp", str(path), ENGINE + ":/lab/stack.json")
    engine("stack", "config", "-c", "/lab/stack.json")
    engine("stack", "deploy", "--prune", "--resolve-image", "never", "-c", "/lab/stack.json", STACK)
    wait_for(ready, 420)
    wait_for(endpoint_ready, 180)
    print("Все сервисы готовы, приложение имеет одну реплику")


def ready():
    expected = json.loads((DIRECTORY / "stack.json").read_text(encoding="utf-8"))["services"]
    rows = engine("stack", "services", STACK, "--format", "{{.Name}} {{.Replicas}}").splitlines()
    if set(rows) != {STACK + "_" + name + " 1/1" for name in expected}:
        return False
    services = json.loads(engine("service", "inspect", *engine("stack", "services", STACK, "-q").splitlines()))
    return all(service.get("UpdateStatus", {}).get("State", "completed") == "completed"
               for service in services)


def endpoint_ready():
    context = ssl.create_default_context(cafile=str(RUNTIME / "tls_certificate"))
    with urllib.request.urlopen("https://127.0.0.1:18443/__lab/ready", context=context, timeout=5) as response:
        return response.status == 200


def check():
    owned_engine()
    wait_for(ready, 420)
    wait_for(endpoint_ready, 180)
    context = ssl.create_default_context(cafile=str(RUNTIME / "tls_certificate"))
    with urllib.request.urlopen("https://127.0.0.1:18443/__lab/ready", context=context, timeout=10) as response:
        assert response.status == 200
    with urllib.request.urlopen("https://127.0.0.1:18443/", context=context, timeout=10) as response:
        assert response.status == 200 and b"<html" in response.read().lower()
    with urllib.request.urlopen("https://127.0.0.1:18443/api/public/tournaments", context=context, timeout=10) as response:
        assert response.status == 200 and isinstance(json.load(response), (dict, list))
    request = urllib.request.Request("https://127.0.0.1:18443/__lab/edge",
                                    headers={"X-Forwarded-For": "203.0.113.99"})
    with urllib.request.urlopen(request, context=context, timeout=10) as response:
        edge = response.read().decode()
        assert "203.0.113.99" not in edge and edge.endswith("|https")
    with urllib.request.urlopen("https://127.0.0.1:18443/ws/info", context=context, timeout=10) as response:
        assert json.load(response)["websocket"]
    for path in ["/internal", "/internal/events", "/internal/events?probe=1", "/%69nternal/events"]:
        try:
            urllib.request.urlopen("https://127.0.0.1:18443" + path, context=context, timeout=10)
        except urllib.error.HTTPError as error:
            assert error.code == 404, path
            assert error.headers.get("X-Lab-Internal-Blocked") == "1", path
        else:
            raise AssertionError("Внутренний маршрут открыт через proxy: " + path)
    with context.wrap_socket(socket.create_connection(("127.0.0.1", 18443), timeout=10),
                             server_hostname="127.0.0.1") as stream:
        stream.sendall(b"GET /ws/lab/123/websocket HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\nSec-WebSocket-Version: 13\r\n\r\n")
        assert b" 101 " in stream.recv(4096)
    for service in json.loads(engine("service", "inspect", *engine("stack", "services", STACK, "-q").splitlines())):
        published = service.get("Endpoint", {}).get("Ports", [])
        if service["Spec"]["Name"] != STACK + "_gateway":
            assert not published, service["Spec"]["Name"]
        assert not service["Spec"]["TaskTemplate"]["ContainerSpec"].get("Mounts", []) or all(
            mount.get("Source") != "/var/run/docker.sock"
            for mount in service["Spec"]["TaskTemplate"]["ContainerSpec"].get("Mounts", []))
    grafana = engine("ps", "-q", "--filter", "label=com.docker.swarm.service.name=" + STACK + "_grafana")
    def metrics_ready():
        targets = json.loads(engine("exec", grafana, "wget", "-qO-",
                                    "http://prometheus:9090/api/v1/targets"))["data"]["activeTargets"]
        return bool(targets) and all(target["health"] == "up" for target in targets)
    wait_for(metrics_ready, 90)
    check_discovery()
    print("TLS, readiness, forwarded IP, WS/SockJS, метрики, internal guard и task discovery проверены")


def check_discovery():
    """Проверка всех task IP без запуска второй JVM и без Docker API в приложении."""
    app = engine("ps", "-q", "--filter", "label=com.docker.swarm.service.name=" + STACK + "_padel-app")
    own = engine("exec", app, "getent", "ahostsv4", "tasks.padel-app")
    assert len({line.split()[0] for line in own.splitlines()}) == 1
    probe = "padel-lab-discovery-" + uuid.uuid4().hex[:12]
    image = json.loads(engine("service", "inspect", STACK + "_padel-app"))[0]["Spec"]["TaskTemplate"]["ContainerSpec"]["Image"]
    try:
        engine("service", "create", "--detach=true", "--no-resolve-image", "--name", probe,
               "--label", LABEL + "=" + state()["instance"],
               "--network", STACK + "_events", "--replicas", "2",
               "--limit-memory", "16M", "--entrypoint", "sleep", image, "300")
        def two_addresses():
            result = engine("exec", app, "getent", "ahostsv4", "tasks." + probe,
                            stderr=subprocess.DEVNULL)
            return len({line.split()[0] for line in result.splitlines()}) == 2
        wait_for(two_addresses, 90)
    finally:
        existing = engine("service", "ls", "-q", "--filter", "name=" + probe)
        if existing:
            data = json.loads(engine("service", "inspect", probe))[0]
            if data["Spec"]["Labels"].get(LABEL) != state()["instance"]:
                raise ValueError("Чужой discovery probe не удаляется")
            engine("service", "rm", probe)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["init", "render", "start", "deploy", "check", "status", "stop", "purge"])
    parser.add_argument("--context", default="desktop-linux" if os.name == "nt" else "default")
    parser.add_argument("--jar", type=Path, default=ROOT / "padel-bootstrap/target/padel-core-service-0.0.1-SNAPSHOT.jar")
    parser.add_argument("--app-image", help="Неизменяемый образ для render: repository@sha256:... либо sha256:...")
    parser.add_argument("--volume-map", type=Path, help="Карта четырёх существующих томов для переносимого render")
    args = parser.parse_args()
    if args.volume_map and args.action != "render":
        parser.error("--volume-map разрешён только для render; локальный deploy не подключает внешние данные")
    images = json.loads((DIRECTORY / "images.lock.json").read_text(encoding="utf-8"))
    if args.action == "init":
        initialize()
        return
    if args.action == "render":
        if not args.app_image or not re.fullmatch(r"(?:[^\s]+@)?sha256:[0-9a-f]{64}", args.app_image):
            parser.error("render требует --app-image с sha256 digest")
        mapping = json.loads(args.volume_map.read_text(encoding="utf-8")) if args.volume_map else None
        if args.volume_map:
            validate_mapping(mapping)
        prepare_rest_secret()
        revision = run(["git", "-C", str(ROOT), "rev-parse", "HEAD"])
        data = render(images, args.app_image, revision, local_files=True, volume_map=mapping)
        output = RUNTIME / "stack.local.json"
        output.write_text(json.dumps(data, indent=2), encoding="utf-8")
        print(output)
        return
    DOCKER.extend(["docker", "--context", args.context])
    endpoint = json.loads(host("context", "inspect", args.context))[0]["Endpoints"]["docker"]["Host"]
    if not endpoint.startswith(("npipe://", "unix://")):
        raise ValueError("Требуется локальный Docker context")
    if args.action == "start":
        start(images)
    elif args.action == "deploy":
        deploy(images, args.jar.resolve())
    elif args.action == "check":
        check()
    elif args.action == "status":
        owned_engine()
        print(engine("stack", "services", STACK))
    elif args.action == "stop":
        owned_engine()
        host("stop", ENGINE)
    elif args.action == "purge":
        owned_engine()
        host("rm", "-f", "-v", ENGINE)
        print("Удалён только собственный вложенный Engine и его данные. Локальные секреты сохранены.")


if __name__ == "__main__":
    main()
