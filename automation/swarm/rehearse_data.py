#!/usr/bin/env python3
"""LFPT-1209: backup/restore и Compose ↔ Swarm только на собственных копиях данных."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid
from urllib.parse import urlencode

import lab
from data_volumes import LABEL, ROLES, VolumeStore, digest, external_volumes, mount_inventory, tar_fingerprint, verify_archive

DATABASE = "padel_ui_v2_preview"
USER = "preview"


def identifier(name):
    return '"' + name.replace('"', '""') + '"'


def literal(value):
    return "'" + value.replace("'", "''") + "'"


class Rehearsal:
    def __init__(self, images, owner=None):
        self.images = images
        self.owner = owner or uuid.uuid4().hex
        if len(self.owner) != 32 or any(char not in "0123456789abcdef" for char in self.owner):
            raise ValueError("Неверный ID репетиции")
        self.prefix = "padel-data-" + self.owner[:12]
        self.project = self.prefix + "-compose"
        self.stack = self.prefix + "-swarm"
        self.local = lab.RUNTIME / self.prefix
        self.remote = "/lab/" + self.prefix
        self.store = VolumeStore(lab.engine, images, self.owner, self.remote)
        self.report = {"run": self.owner, "scope": "synthetic-only", "phases": {}, "backups": {}}
        self.source = {role: self.prefix + "-source-" + role for role in ROLES}
        self.target = {role: self.prefix + "-restored-" + role for role in ROLES}

    def save(self):
        (self.local / "report.json").write_text(json.dumps(self.report, indent=2), encoding="utf-8")

    def put(self, filename, content):
        path = self.local / filename
        path.write_text(content, encoding="utf-8")
        path.chmod(0o600)
        lab.host("cp", str(path), lab.ENGINE + ":" + self.remote + "/" + filename)

    def fetch(self, filename):
        path = self.local / filename
        lab.host("cp", lab.ENGINE + ":" + self.remote + "/" + filename, str(path))
        path.chmod(0o600)
        return path

    def query(self, container, sql):
        return lab.engine("exec", "-e", "PGOPTIONS=-c default_transaction_read_only=off", container,
                          "psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-U", USER, "-d", DATABASE, "-c", sql)

    def database_ready(self, container):
        lab.wait_for(lambda: lab.engine("exec", container, "pg_isready", "-h", "127.0.0.1", "-U", USER, "-d", DATABASE).endswith("accepting connections"), 120)

    def snapshot(self, container):
        tables = json.loads(self.query(container, "SELECT coalesce(json_agg(t ORDER BY table_schema,table_name),'[]') FROM "
            "(SELECT table_schema,table_name FROM information_schema.tables WHERE table_type='BASE TABLE' "
            "AND table_schema NOT IN ('pg_catalog','information_schema')) t"))
        statements = []
        for table in tables:
            name = identifier(table["table_schema"]) + "." + identifier(table["table_name"])
            statements.append("SELECT json_build_object('table_schema'," + literal(table["table_schema"]) +
                ",'table_name'," + literal(table["table_name"]) + ",'data',coalesce(json_agg(row_to_json(t) "
                "ORDER BY row_to_json(t)::text COLLATE \"C\"),'[]')) FROM " + name + " t")
        records = []
        # Небольшие batches не превышают Windows command-line limit и не требуют
        # отдельного docker exec на каждую таблицу. Сырые строки в отчёт не попадают.
        for begin in range(0, len(statements), 20):
            for line in self.query(container, ";".join(statements[begin:begin + 20])).split("\n"):
                item = json.loads(line)
                data = item.pop("data")
                # Хэшируем исходный PostgreSQL JSON, не округляя decimal в float
                # и не смешивая JSON-строки с числовыми значениями.
                records.append(dict(item, rows=len(data), sha256=hashlib.sha256(line.encode("utf-8")).hexdigest()))
        sequences = json.loads(self.query(container, "SELECT coalesce(json_agg(t ORDER BY schemaname,sequencename),'[]') "
            "FROM (SELECT schemaname,sequencename FROM pg_sequences WHERE schemaname NOT LIKE 'pg_%') t"))
        statements = ["SELECT json_build_object('schemaname'," + literal(item["schemaname"]) +
            ",'sequencename'," + literal(item["sequencename"]) + ",'state',json_build_array(last_value,is_called)) FROM " +
            identifier(item["schemaname"]) + "." + identifier(item["sequencename"]) for item in sequences]
        sequences = []
        for begin in range(0, len(statements), 20):
            sequences.extend(json.loads(line) for line in self.query(container, ";".join(statements[begin:begin + 20])).split("\n"))
        metadata = {}
        for key, sql in {
            "version": "SHOW server_version",
            "database": "SELECT pg_encoding_to_char(encoding),datcollate,datctype FROM pg_database WHERE datname=current_database()",
            "extensions": "SELECT extname,extversion FROM pg_extension ORDER BY extname",
            "owners": "SELECT schemaname,tablename,tableowner FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY 1,2",
            "constraints": "SELECT conrelid::regclass,conname,pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace IN (SELECT oid FROM pg_namespace WHERE nspname NOT LIKE 'pg_%' AND nspname<>'information_schema') ORDER BY conrelid::regclass::text,conname",
            "columns": "SELECT table_schema,table_name,column_name,udt_name,is_nullable,column_default FROM information_schema.columns WHERE table_schema NOT IN ('pg_catalog','information_schema') ORDER BY table_schema,table_name,ordinal_position",
            "indexes": "SELECT schemaname,tablename,indexname,indexdef FROM pg_indexes WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY 1,2,3",
        }.items():
            metadata[key] = self.query(container, sql)
        return {"tables": records, "sequences": sequences, "metadata": metadata}

    def dump(self, container, filename):
        started = time.monotonic()
        temporary = "/tmp/" + self.prefix + ".dump"
        lab.engine("exec", container, "pg_dump", "-U", USER, "-d", DATABASE,
                   "--format=custom", "--no-owner", "--no-acl", "--file=" + temporary)
        try:
            lab.engine("cp", container + ":" + temporary, self.remote + "/" + filename)
        finally:
            lab.engine("exec", container, "rm", "--", temporary)
        path = self.fetch(filename)
        self.report["backups"][filename] = {"bytes": path.stat().st_size, "sha256": digest(path),
                                                "seconds": round(time.monotonic() - started, 3)}
        self.save()
        return path

    def restore_database(self, volume, filename):
        expected = self.report["backups"][filename]["sha256"]
        verify_archive(self.local / filename, expected)
        if lab.host("exec", lab.ENGINE, "sha256sum", self.remote + "/" + filename).split()[0] != expected:
            raise ValueError("Backup внутри Engine повреждён; restore запрещён")
        self.store.empty(volume)
        name = self.prefix + "-restore"
        started = time.monotonic()
        lab.engine("run", "-d", "--name", name, "--label", LABEL + "=" + self.owner,
                   "--network", "none", "--memory", "384m", "--mount", "type=volume,source=" + volume + ",target=" + ROLES["database"],
                   "--mount", "type=bind,source=" + self.remote + "/password,target=/run/secrets/password,readonly",
                   "-e", "POSTGRES_PASSWORD_FILE=/run/secrets/password", "-e", "POSTGRES_USER=" + USER,
                   "-e", "POSTGRES_DB=" + DATABASE, self.images["postgres"])
        try:
            self.database_ready(name)
            lab.engine("cp", self.remote + "/" + filename, name + ":/tmp/restore.dump")
            lab.engine("exec", name, "pg_restore", "--exit-on-error", "--single-transaction", "--no-owner", "--no-acl",
                       "-U", USER, "-d", DATABASE, "/tmp/restore.dump")
            return self.snapshot(name), round(time.monotonic() - started, 3)
        finally:
            self.owned_container(name)
            lab.engine("rm", "-f", "-v", name)

    def owned_container(self, name):
        data = json.loads(lab.engine("inspect", name))[0]
        if (data["Config"].get("Labels") or {}).get(LABEL) != self.owner:
            raise ValueError("Чужой контейнер: " + name)
        return data

    def compose(self, *args):
        return lab.engine("compose", "-p", self.project, "-f", self.remote + "/compose.json", *args)

    def compose_containers(self):
        return lab.engine("ps", "-aq", "--filter", "label=com.docker.compose.project=" + self.project).splitlines()

    def down_compose(self):
        for name in self.compose_containers():
            self.owned_container(name)
        self.compose("down", "--timeout", "40")

    def stack_services(self):
        return lab.engine("service", "ls", "-q", "--filter", "label=com.docker.stack.namespace=" + self.stack).splitlines()

    def down_stack(self):
        ids = self.stack_services()
        if not ids:
            return
        for item in json.loads(lab.engine("service", "inspect", *ids)):
            if item["Spec"].get("Labels", {}).get(LABEL) != self.owner:
                raise ValueError("Чужой stack не удаляется")
        lab.engine("stack", "rm", self.stack)
        lab.wait_for(lambda: not self.stack_services(), 90)

    def wait_quiescent(self, volumes):
        def ready():
            try:
                for name in volumes.values():
                    self.store.quiescent(name)
            except ValueError:
                return False
            return True
        lab.wait_for(ready, 90)

    def config(self, volumes, swarm=False):
        base = json.loads((lab.DIRECTORY / "stack.json").read_text(encoding="utf-8"))
        names = {"database": "preview-db", "grafana": "grafana", "prometheus": "prometheus", "loki": "loki"}
        services = {}
        for role, original in names.items():
            service = base["services"][original]
            service["image"] = self.images["postgres" if role == "database" else role]
            service.pop("configs", None)
            service.pop("secrets", None)
            service["networks"] = ["private"]
            service["labels"] = {LABEL: self.owner}
            service["volumes"] = [role + ":" + ROLES[role]]
            if role == "database":
                service["environment"]["POSTGRES_PASSWORD_FILE"] = "/run/secrets/password"
                service["volumes"].append(self.remote + "/password:/run/secrets/password:ro")
            elif role == "grafana":
                service["environment"]["GF_SECURITY_ADMIN_PASSWORD__FILE"] = "/run/secrets/password"
                service["volumes"].append(self.remote + "/password:/run/secrets/password:ro")
            elif role == "prometheus":
                service["volumes"].append(self.remote + "/prometheus.yml:/etc/prometheus/prometheus.yml:ro")
            if swarm:
                service["deploy"]["labels"] = {LABEL: self.owner}
                node = lab.engine("info", "--format", "{{.Swarm.NodeID}}")
                service["deploy"]["placement"] = {"constraints": ["node.id==" + node]}
                service["stop_grace_period"] = "40s"
            else:
                service["mem_limit"] = service["deploy"]["resources"]["limits"]["memory"]
                service.pop("deploy")
                service["restart"] = "no"
                service["stop_grace_period"] = "40s"
            services[original] = service
        result = {"version": "3.8", "services": services, "volumes": {role: {} for role in ROLES},
                  "networks": {"private": {"driver": "overlay" if swarm else "bridge", "internal": True}}}
        return external_volumes(result, volumes)

    def pg_compose(self):
        return self.compose("ps", "-q", "preview-db")

    def pg_stack(self):
        return lab.engine("ps", "-q", "--filter", "label=com.docker.swarm.service.name=" + self.stack + "_preview-db")

    def grafana_container(self, swarm):
        return (lab.engine("ps", "-q", "--filter", "label=com.docker.swarm.service.name=" + self.stack + "_grafana")
                if swarm else self.compose("ps", "-q", "grafana"))

    def http(self, grafana, url, payload=None, authenticated=False):
        # Пароль читается только внутри собственного Grafana-контейнера и не
        # попадает в argv внешнего процесса, логи или report.
        auth_prefix = ('auth=$(printf "lab:%s" "$(cat /run/secrets/password)" | base64 | tr -d "\\n"); '
                       if authenticated else '')
        script = auth_prefix + 'mode=$1; body=$2; url=$3; shift 3; ' + (
            'set -- --header "Authorization: Basic $auth"; ' if authenticated else '') + (
            'if [ "$mode" = post ]; then exec wget -qO- --header "Content-Type: application/json" --post-data "$body" "$@" "$url"; fi; exec wget -qO- "$@" "$url"')
        return lab.engine("exec", grafana, "sh", "-ec", script, "--", "post" if payload is not None else "get",
                          json.dumps(payload) if payload is not None else "", url)

    def monitoring_ready(self, grafana):
        def ready():
            self.http(grafana, "http://127.0.0.1:3000/api/health")
            self.http(grafana, "http://loki:3100/ready")
            return json.loads(self.http(grafana, "http://prometheus:9090/api/v1/query?query=prometheus_build_info"))["data"]["result"]
        lab.wait_for(ready, 90)

    def seed_monitoring(self):
        grafana = self.grafana_container(False)
        self.monitoring_ready(grafana)
        stamp = time.time_ns()
        self.report["monitoring_probe"] = {"timestamp_ns": stamp, "dashboard_uid": "lfpt1209-" + self.owner[:12]}
        self.http(grafana, "http://127.0.0.1:3000/api/dashboards/db", {"dashboard": {
            "uid": self.report["monitoring_probe"]["dashboard_uid"], "title": "Synthetic volume retention",
            "schemaVersion": 39, "panels": []}, "overwrite": False}, authenticated=True)
        self.http(grafana, "http://loki:3100/loki/api/v1/push", {"streams": [{
            "stream": {"job": "lfpt1209", "run": self.owner},
            "values": [[str(stamp), "synthetic-volume-retention"]]}]})
        self.http(grafana, "http://loki:3100/flush", {})

    def verify_monitoring(self, swarm):
        grafana = self.grafana_container(swarm)
        self.monitoring_ready(grafana)
        probe = self.report["monitoring_probe"]
        dashboard = json.loads(self.http(grafana, "http://127.0.0.1:3000/api/dashboards/uid/" + probe["dashboard_uid"], authenticated=True))
        if dashboard["dashboard"]["uid"] != probe["dashboard_uid"]:
            raise ValueError("Grafana потеряла dashboard")
        metric_url = "http://prometheus:9090/api/v1/query?" + urlencode({
            "query": "prometheus_build_info", "time": probe["timestamp_ns"] / 10**9})
        if not json.loads(self.http(grafana, metric_url))["data"]["result"]:
            raise ValueError("Prometheus потерял исторические samples")
        log_url = "http://loki:3100/loki/api/v1/query_range?" + urlencode({
            "query": '{job="lfpt1209",run="' + self.owner + '"}',
            "start": probe["timestamp_ns"] - 10**9, "end": probe["timestamp_ns"] + 10**9})
        streams = json.loads(self.http(grafana, log_url))["data"]["result"]
        if not any(value[1] == "synthetic-volume-retention" for stream in streams for value in stream.get("values", [])):
            raise ValueError("Loki потерял записанную строку")

    def assert_mounts(self, containers, volumes):
        items = mount_inventory(lab.engine, containers)
        mounts = [mount for item in items for mount in item["mounts"] if mount["Destination"] in ROLES.values()]
        actual = {mount["Destination"]: mount.get("Name") for mount in mounts}
        if len(mounts) != len(ROLES) or actual != {ROLES[role]: name for role, name in volumes.items()}:
            raise ValueError("Фактические mounts отличаются от карты")
        return items

    def stack_ready(self):
        rows = lab.engine("stack", "services", self.stack, "--format", "{{.Replicas}}").splitlines()
        return len(rows) == 4 and all(row == "1/1" for row in rows)

    def running_postgres_task(self):
        ids = lab.engine("service", "ps", "--no-trunc", "-q", "--filter", "desired-state=running",
                         self.stack + "_preview-db").splitlines()
        if not ids:
            return None
        running = [item["ID"] for item in json.loads(lab.engine("inspect", *ids))
                   if item["DesiredState"] == "running" and item["Status"]["State"] == "running"]
        return running[0] if len(running) == 1 else None

    def recreate_postgres(self):
        before = self.running_postgres_task()
        if before is None:
            raise ValueError("Не найдена единственная работающая task PostgreSQL")
        lab.engine("service", "update", "--force", "--detach=true", self.stack + "_preview-db")

        def replaced():
            after = self.running_postgres_task()
            if after is None or after == before or not self.stack_ready():
                return False
            service = json.loads(lab.engine("service", "inspect", self.stack + "_preview-db"))[0]
            return service.get("UpdateStatus", {}).get("State") == "completed"

        lab.wait_for(replaced, 180)
        after = self.running_postgres_task()
        if after is None or after == before:
            raise ValueError("Пересоздание PostgreSQL не подтверждено новым task ID")
        self.database_ready(self.pg_stack())
        return {"before": before, "after": after}

    def acquire_lease(self):
        lab.owned_engine()
        lease = "padel-data-lock-" + lab.state()["instance"].replace("-", "")[:12]
        # Atomic name reservation сериализует репетиции даже из разных процессов.
        lab.engine("create", "--name", lease, "--network", "none", "--tmpfs", "/var/lib/postgresql/data",
                   "--label", LABEL + "=" + self.owner, self.images["postgres"], "true")
        return lease

    def release_lease(self, lease):
        self.owned_container(lease)
        lab.engine("rm", "-v", lease)

    def stop_restore(self):
        name = self.prefix + "-restore"
        if lab.engine("ps", "-aq", "--filter", "name=^/" + name + "$").strip():
            self.owned_container(name)
            lab.engine("rm", "-f", "-v", name)

    def stop_writers_and_confirm(self, volumes):
        errors = []
        actions = [self.down_stack, self.stop_restore]
        if (self.local / "compose.json").exists():
            actions.insert(1, self.down_compose)
        for action in actions:
            try:
                action()
            except Exception as error:
                # Cleanup продолжает независимые остановки; lease не освобождается
                # по одному факту завершения finally, только после проверки томов.
                errors.append(error)
        try:
            self.wait_quiescent(volumes)
        except Exception as error:
            raise RuntimeError("Освобождение томов не подтверждено; lease сохранён для безопасного recover") from error
        return errors

    def finalize_run(self, lease, writers_may_exist, collect_metadata=False):
        if not writers_may_exist:
            self.release_lease(lease)
            return
        volumes = {**{"source-" + role: name for role, name in self.source.items()},
                   **{"target-" + role: name for role, name in self.target.items()}}
        errors = self.stop_writers_and_confirm(volumes)
        try:
            if collect_metadata and not errors:
                self.report["volume_metadata"] = {
                    group: {role: self.store.helper(name, "stat -c '%u:%g %a' /data") for role, name in mapping.items()}
                    for group, mapping in [("source", self.source), ("restored", self.target)]}
        finally:
            self.release_lease(lease)
        if errors:
            raise RuntimeError("Были ошибки остановки; тома проверены свободными, lease освобождён") from errors[0]

    def cleanup(self):
        lease = self.acquire_lease()
        confirmed = False
        try:
            existing = set(lab.engine("volume", "ls", "--format", "{{.Name}}").splitlines())
            names = set(lab.engine("volume", "ls", "-q", "--filter", "label=" + LABEL + "=" + self.owner).splitlines())
            names.update(existing.intersection([*self.source.values(), *self.target.values()]))
            for name in names:
                self.store.owned(name)
            errors = self.stop_writers_and_confirm({name: name for name in names})
            confirmed = True
            if errors:
                raise RuntimeError("Остановка завершена с ошибками; удаление томов отменено") from errors[0]
            for name in sorted(names):
                self.store.quiescent(name)
                lab.engine("volume", "rm", name)
        finally:
            if confirmed:
                self.release_lease(lease)

    def run(self):
        lease = self.acquire_lease()
        writers_may_exist = False
        completed = False
        try:
            self.local.mkdir(mode=0o700, exist_ok=False)
            lab.host("exec", lab.ENGINE, "mkdir", "-p", self.remote)
            self.put("password", secrets.token_urlsafe(32))
            lab.host("exec", lab.ENGINE, "chmod", "0444", self.remote + "/password")
            self.save()
            self.put("prometheus.yml", "global:\n  scrape_interval: 1s\nscrape_configs:\n  - job_name: rehearsal\n    static_configs:\n      - targets: ['127.0.0.1:9090']\n")
            for name in [*self.source.values(), *self.target.values()]:
                self.store.create(name)
            self.report["volume_map"] = {"compose_source": self.source, "swarm_and_return": self.target}
            self.put("volume-map.json", json.dumps(self.target, indent=2))
            root_pg = lab.engine("ps", "-q", "--filter", "label=com.docker.swarm.service.name=padel-lab_preview-db")
            if not root_pg:
                raise ValueError("Сначала запустите синтетический стенд #1208")
            self.database_ready(root_pg)
            if self.query(root_pg, "SELECT instance_id FROM ui_preview.control") != lab.state()["instance"]:
                raise ValueError("Не совпал UUID синтетической БД; backup запрещён")
            self.dump(root_pg, "seed.dump")
            writers_may_exist = True
            self.restore_database(self.source["database"], "seed.dump")
            self.put("compose.json", json.dumps(self.config(self.source), indent=2))
            self.compose("up", "-d", "--wait", "--wait-timeout", "120")
            pg = self.pg_compose()
            self.database_ready(pg)
            self.seed_monitoring()
            self.query(pg, "CREATE SCHEMA swarm_rehearsal; CREATE TABLE swarm_rehearsal.probe(id integer PRIMARY KEY, value text NOT NULL); "
                          "INSERT INTO swarm_rehearsal.probe VALUES(1,'before-cutover');")
            started = time.monotonic()
            self.query(pg, "ALTER DATABASE " + DATABASE + " SET default_transaction_read_only=on")
            self.report["source_inventory"] = self.assert_mounts(self.compose_containers(), self.source)
            baseline = self.snapshot(pg)
            self.report["source_snapshot"] = baseline
            self.dump(pg, "final.dump")
            # Перед любым файловым архивом мониторинга его процессы остановлены.
            self.down_compose()
            self.wait_quiescent(self.source)
            fingerprints = {}
            for role in ["grafana", "prometheus", "loki"]:
                filename = role + ".tar"
                self.store.helper(self.source[role], "printf '%s\\n' '" + self.owner + "' > /data/lfpt-1209-marker; "
                                  "chown --reference=/data /data/lfpt-1209-marker; chmod 0640 /data/lfpt-1209-marker", writable=True)
                self.store.archive(self.source[role], filename)
                path = self.fetch(filename)
                fingerprints[role] = tar_fingerprint(path)
                self.report["backups"][filename] = {"bytes": path.stat().st_size, "sha256": digest(path),
                                                     "fingerprint": fingerprints[role]}
                verify_archive(path, self.report["backups"][filename]["sha256"])
                self.store.restore(self.target[role], filename, self.report["backups"][filename]["sha256"])
                self.store.archive(self.target[role], role + "-restored.tar")
                if tar_fingerprint(self.fetch(role + "-restored.tar")) != fingerprints[role]:
                    raise ValueError("Содержимое/UID/GID/права тома не совпали: " + role)
            restored, restore_seconds = self.restore_database(self.target["database"], "final.dump")
            if restored != baseline:
                raise ValueError("Контрольные данные PostgreSQL после restore не совпали")
            self.report["phases"]["restore_seconds"] = restore_seconds
            self.report["monitoring_fingerprints"] = fingerprints
            self.wait_quiescent(self.target)
            self.put("stack.json", json.dumps(self.config(self.target, swarm=True), indent=2))
            lab.engine("stack", "config", "-c", self.remote + "/stack.json")
            lab.engine("stack", "deploy", "--resolve-image", "never", "-c", self.remote + "/stack.json", self.stack)
            lab.wait_for(self.stack_ready, 180)
            pg = self.pg_stack()
            self.database_ready(pg)
            if self.snapshot(pg) != baseline:
                raise ValueError("После переключения Swarm изменились данные")
            self.verify_monitoring(True)
            self.report["swarm_inventory"] = self.assert_mounts(lab.engine("ps", "-q", "--filter",
                "label=com.docker.stack.namespace=" + self.stack).splitlines(), self.target)
            try:
                self.store.quiescent(self.target["database"])
            except ValueError:
                self.report["active_writer_rejected"] = True
            else:
                raise ValueError("Проверка не обнаружила активного писателя PostgreSQL")
            self.query(pg, "INSERT INTO swarm_rehearsal.probe VALUES(2,'written-in-swarm')")
            after_write = self.snapshot(pg)
            lab.engine("stack", "deploy", "--resolve-image", "never", "-c", self.remote + "/stack.json", self.stack)
            lab.wait_for(self.stack_ready, 180)
            if self.snapshot(self.pg_stack()) != after_write:
                raise ValueError("Повторный deploy изменил данные")
            self.report["postgres_recreation"] = self.recreate_postgres()
            self.report["recreated_inventory"] = self.assert_mounts(lab.engine("ps", "-q", "--filter",
                "label=com.docker.stack.namespace=" + self.stack).splitlines(), self.target)
            if self.snapshot(self.pg_stack()) != after_write:
                raise ValueError("Пересоздание PostgreSQL потеряло запись из Swarm")
            self.report["phases"]["compose_to_swarm_seconds"] = round(time.monotonic() - started, 3)
            self.query(self.pg_stack(), "ALTER DATABASE " + DATABASE + " SET default_transaction_read_only=on")
            self.dump(self.pg_stack(), "before-return.dump")
            started = time.monotonic()
            self.down_stack()
            self.wait_quiescent(self.target)
            # Возвращаем актуальные тома, а не старую копию до записи id=2.
            self.put("compose.json", json.dumps(self.config(self.target), indent=2))
            self.compose("up", "-d", "--wait", "--wait-timeout", "120")
            pg = self.pg_compose()
            self.database_ready(pg)
            if self.snapshot(pg) != after_write:
                raise ValueError("Возврат Compose потерял подтверждённые записи")
            self.verify_monitoring(False)
            self.report["return_inventory"] = self.assert_mounts(self.compose_containers(), self.target)
            self.query(pg, "ALTER DATABASE " + DATABASE + " RESET default_transaction_read_only")
            self.report["phases"]["swarm_to_compose_seconds"] = round(time.monotonic() - started, 3)
            self.report["tables_verified"] = len(baseline["tables"])
            self.report["sequences_verified"] = len(baseline["sequences"])
            completed = True
        finally:
            self.finalize_run(lease, writers_may_exist, collect_metadata=completed)
        self.report["status"] = "PASS"
        self.save()
        print("PASS: restore, повторный deploy и Compose ↔ Swarm; отчёт " + str(self.local / "report.json"))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["rehearse", "inventory", "cleanup"])
    parser.add_argument("--context", default="desktop-linux" if os.name == "nt" else "default")
    parser.add_argument("--container", action="append", default=[])
    parser.add_argument("--run", help="32 hex символа из report.json для cleanup")
    args = parser.parse_args()
    lab.DOCKER[:] = ["docker", "--context", args.context]
    endpoint = json.loads(lab.host("context", "inspect", args.context))[0]["Endpoints"]["docker"]["Host"]
    if not endpoint.startswith(("npipe://", "unix://")):
        parser.error("Разрешён только локальный context собственного стенда")
    lab.owned_engine()
    images = json.loads((lab.DIRECTORY / "images.lock.json").read_text(encoding="utf-8"))
    if args.action == "inventory":
        if not args.container:
            parser.error("inventory требует --container (имя внутри тестового Engine)")
        path = lab.RUNTIME / "data-inventory.json"
        path.write_text(json.dumps(mount_inventory(lab.engine, args.container), indent=2), encoding="utf-8")
        path.chmod(0o600)
        print(path)
    elif args.action == "rehearse":
        Rehearsal(images).run()
    else:
        if not args.run:
            parser.error("cleanup требует --run")
        rehearsal = Rehearsal(images, args.run)
        rehearsal.cleanup()
        print("Удалены только собственные volumes репетиции. Backup и отчёт сохранены.")


if __name__ == "__main__":
    main()
