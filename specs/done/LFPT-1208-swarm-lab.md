# LFPT-1208 · Swarm — изолированный стенд полного стека

## Источник и статус

[Issue #1208](https://github.com/UstinKO/padel-core-service/issues/1208),
родительская #1040, предшественник #1207. Редакция 20.11.2026 по замечанию техлида:
RabbitMQ исключён, выбран внутренний REST, gRPC отложен. Предыдущая реализация
не принята; доработка в feature/LFPT-1208 по поручению владельца без commit/push.
[Runbook](../../docs/swarm/lab.md), [матрица Compose](../../docs/swarm/compose-matrix.md).

## Реализация

Восемь сервисов: app, PostgreSQL, Nginx, Redis, Prometheus/Grafana/Loki, Mailpit.
Одна app replica; 4 GiB / 2 CPU. Overlay events и резерв REST-ключа только для app,
блокировка /internal/** на proxy. Digest-образы, UUID/content-hash secrets/configs,
synthetic preview DB и устойчивый stop-first lifecycle. Старый RabbitMQ service
снимается deploy --prune; чужие данные/сервисы не затрагиваются.
Переносимый render принимает готовый app digest и не требует Docker-in-Docker;
для занятого локального Docker Desktop проверяется свой вложенный Engine.

## Критерии проверки

- Точный состав восьми services 1/1, readiness, завершение update, repeat deploy.
- TLS/forwarded IP, SockJS, WS Upgrade, metrics target up, закрытые внутренние порты.
- Nginx сам блокирует internal routes; приложение не получает Docker socket/API.
- DNS из app видит оба task IP временного диагностического сервиса без второй JVM.
- Ownership/secret rotation/переносимые пути/restart invariants покрыты тестами.
- Stop/start сохраняет данные; cleanup ограничен своим UUID/Engine.
- Ресурсный срез отделён от доказательства достаточности двух JVM и ОС.

## Границы

Бизнес-инварианты [MASTER.md](../MASTER.md) и клиентский WS/STOMP не меняются.
Endpoint, service-auth, ротация ключа, DTO/fan-out/retry/dedup и восстановление
живого табло при пропусках — #1211. Redis интегрируется в #1210/#1212;
Loki ingestion/алерты — #1216. Production proxy parity, нагрузка двух JVM,
backup/restore и общий допуск остаются #1209–#1218. Native lifecycle выделенной
ВМ в этой сессии не проверен. Результаты текущих проверок записываются в runbook.
