# LFPT-1208 · Стенд без отдельного брокера

Редакция 20.11.2026 заменяет прежний стенд с RabbitMQ.
Восемь сервисов: PostgreSQL, padel-app, Nginx, Redis, Prometheus, Grafana, Loki,
Mailpit. Одна app replica. Redis пока только подготовлен для #1210/#1212;
межрепличный REST на существующем HTTP-сервере приложения реализуется в #1211.
Браузеры сохраняют WS/STOMP/SockJS, gRPC runtime не добавляется.
[ADR и сравнение вариантов](adr/0001-single-vm.md), [матрица Compose](compose-matrix.md).

## Требования

Python 3.10+, Git, OpenSSL, Linux Docker Engine; JAR собирается Java 21
(Maven source/release 17). Профиль preview использует синтетические данные,
отдельную БД с UUID-маркером; scheduling, OAuth/Telegram/Meta выключены,
почта идёт в локальный Mailpit. Production .env не читается.

Цель — выделенный стенд 4 GiB RAM / 2 CPU. Проверка одной JVM не доказывает
достаточность двух JVM вместе с ОС. Production/соседние проекты не используются.

## Выделенный тестовый Engine без вложенного Docker

Основной переносимый артефакт — Compose 3.8 JSON. Подготовьте отдельную тестовую
Linux-ВМ/Engine, доступную через изолированную сеть, с ресурсом 4 GiB / 2 CPU.
На неё доставляются репозиторий и заранее собранный образ по digest
(из registry или проверяемого image archive), production volumes не подключаются.
`render` не обращается к Docker и не меняет состояние Engine:

```shell
python automation/swarm/lab.py init
python automation/swarm/lab.py render --app-image <repository@sha256:64-hex-digest>
docker stack config -c automation/swarm/runtime/stack.local.json
```

Если runtime уже существует, `init` повторно не запускать. Только на выделенном
пустом тестовом Engine оператор проверяет context/ID и `docker info`; при inactive
Swarm выполняет `docker swarm init --advertise-addr <private-interface>`.
Затем на этом же Engine:

```shell
docker stack deploy --prune --resolve-image never -c automation/swarm/runtime/stack.local.json padel-lab
docker stack services padel-lab
curl --cacert automation/swarm/runtime/tls_certificate https://127.0.0.1:18443/__lab/ready
```

Native Swarm публикует host ports 18080/18443 на интерфейсах узла, не привязывает
их к loopback. Изолированная ВМ/периметр обязательны; не применять на публичном
узле без проверенного firewall Docker-трафика. Доступ с рабочего ПК — private
network/SSH tunnel, сертификат предназначен для localhost/127.0.0.1.
Остановка native stand — `docker stack rm padel-lab`; после завершения tasks
повторный deploy сохраняет данные. Полная очистка требует сверить labels каждого
тома `padel-lab_database`, `padel-lab_redis`, `padel-lab_prometheus`, `padel-lab_loki`,
`padel-lab_grafana` с `com.docker.stack.namespace=padel-lab`, убедиться, что тома
не используются, затем удалить именно эти тома через `docker volume rm`.
Не выполнять system prune/swarm leave или удаление чужих volumes.
Native lifecycle на отдельной ВМ здесь не проверен; в этой сессии проверен локальный вариант ниже.

## Локальная проверка на Windows / занятом Docker Engine

CLI start/deploy/check/stop/purge управляет собственным Docker-in-Docker Engine
с UUID ownership; Swarm хоста остаётся inactive. Этот вариант выбран для локальной
проверки, потому что Docker Desktop уже содержит другие проекты. Он не обязателен
для выделенной ВМ: дополнительный daemon и `--privileged` не нужны native stack.
Использовать только доверенную машину; DinD — изоляция данных/конфигурации,
а не sandbox для вредоносного кода. Socket хоста и TCP Docker API не публикуются.
Лимит вложенного Engine 4 GiB / 2 CPU не включает ОС хоста и Docker Desktop VM.

Из корня репозитория:

```shell
mvn -B -ntp -DskipTests -Dskip.frontend=true package
python automation/swarm/lab.py init
python automation/swarm/lab.py start
python automation/swarm/lab.py deploy
python automation/swarm/lab.py check
python automation/swarm/lab.py status
```

`skip.frontend` — инфраструктурная проверка без изменения frontend, не полный
release build. Готовый JAR можно передать `deploy --jar <путь>`.
Context по умолчанию desktop-linux на Windows/default на Linux; допускаются
только локальные unix/npipe contexts (`--context`). При обновлении старого стенда
с существующим runtime выполнить start/deploy, без init: резерв REST-ключа
добавляется, прочие credentials не меняются. `--prune` убирает прежний RabbitMQ
service только своего stack. Старые данные/версии secret/образы автоматически
не стираются; удаляются лишь при полной очистке своего Engine.

[HTTPS](https://127.0.0.1:18443) опубликован на loopback; HTTP 18080 перенаправляется.
Сертификат self-signed на 30 дней, SAN localhost/127.0.0.1. Check доверяет только
созданному сертификату, не отключая TLS-проверку. Fixture-пароль в runtime/fixture_password;
пользователи — [UI preview](../ui-v2-preview.md). Не публиковать стенд через tunnel
в Интернет. Production CrowdSec/CDN real-IP/ACME parity этим тестом не подтверждается.

```shell
python automation/swarm/lab.py stop
python automation/swarm/lab.py start
python automation/swarm/lab.py check
```

Stop сохраняет данные. **Удалить данные только своего вложенного Engine**:

```shell
python automation/swarm/lab.py purge
```

Перед stop/purge проверяется UUID; чужой Engine не трогается. Purge удаляет его
anonymous volume, но оставляет локальный runtime. Для нового UUID после purge
удалите именно runtime вручную и повторите init. Не используйте system prune.

## REST, образы и секреты

App подключён к internal overlay web/private/events, gateway не подключён к events.
Discovery — tasks.padel-app, а не VIP. Check создаёт две временные sleep-tasks
в events и из app проверяет два DNS-адреса; это не две JVM и не тест доставки событий.
Probe удаляется в finally только после проверки UUID, socket/API приложению не выдаётся.
`/internal` и `/internal/**`, включая нормализованный encoded URL, Nginx блокирует;
проверяется специальный ответ proxy, чтобы не принять app 404 за доказательство защиты.

Случайный rest_peer_key монтируется только app как `/run/secrets/cluster-events-key`,
UID 10001, mode 0400. Сейчас это **резерв**: Java не читает ключ и endpoint ещё
не реализован. Service-auth, ротация с ограниченной совместимостью двух ключей,
DTO, after-commit fan-out, retry/dedup и сверка открытого табло с БД — #1211.
Сеть и запрет proxy не заменяют аутентификацию endpoint.

Инфраструктурные образы закреплены digest в images.lock.json, app — image ID/digest
и SHA-256 JAR. Runtime игнорируется Git, не публиковать его и секреты.
Config/secret names содержат UUID и content hash; после изменения локального файла
повторить start/deploy (native — render/deploy). Старые версии сохраняются для rollback.
Ротация PostgreSQL сначала требует изменить пароль в самой БД: initialization env
не меняет существующего пользователя. JWT-ротация завершает старые сессии.
Обновления/откаты: stop-first, parallelism=1; restart policy any восстанавливает
штатно остановленные tasks. Deploy ждёт точного состава, завершения rollout и readiness.

## Проверки и ограничения

```shell
python -m unittest discover -s automation/swarm -p "test_*.py"
```

Check: TLS/readiness, forwarded IP, SockJS, WS 101, внутренние порты, Docker socket,
internal guard, Prometheus target up, DNS всех двух диагностических tasks.
Loki пока не получает app logs; сбор, dashboards/алерты — #1216.
Подключение Redis — #1210/#1212; межрепличные гарантии — #1211;
две реальные JVM, отказ, нагрузка 4 GiB с ОС, restore/rollback и цели
15/5/30 min / RPO 24 h — #1209–#1217. Production-переключение — #1218.

Предыдущий прогон 19.11 с RabbitMQ не является приёмкой новой схемы.
В этой доработке Java/UI не меняются; используется ранее собранный JAR.
Полный backend verify 19.11: 1095 тестов, 28 падений существующего
LokiLoggingConfigurationTest из-за ClassNotFoundException дочерней JVM на Windows,
2 skipped. Этот старый прогон не выдаётся за зелёную проверку текущего release.

### Текущие результаты · 20.11.2026

- Docker Desktop Engine 29.5.3, собственный DinD с лимитом 4294967296 bytes / 2 CPU.
  Swarm внешнего Engine остался inactive, соседние проекты не менялись.
- Восемь services 1/1 без RabbitMQ; выполнены deploy с prune старого service,
  повторный deploy, stop/start с сохранением preview-данных и повторный check.
- TLS/readiness, HTML главной страницы, публичный JSON API турниров,
  forwarded IP, SockJS, WS 101, Prometheus target up, отсутствие служебных
  публикаций/socket, блокировка internal/encoded URL и discovery двух sleep-tasks — PASS.
  Диагностические services удалены. REST-ключ фактически UID 10001 / mode 0400.
- Переносимый render принят `docker stack config`; native lifecycle отдельной ВМ
  не выполнялся. Проверка REST endpoint/service-auth/fan-out ещё относится к #1211.
- 13 выборок docker stats при повторном запуске: наблюдавшийся максимум 853.9 MiB,
  CPU 203.55% (Docker считает 100% за одно ядро; старт насыщает две vCPU).
  Последующий idle-срез: 849.3 MiB / 4 GiB, CPU 2.08%.
  Это working-set вложенного Engine, без ОС хоста/Desktop VM, не P95/P99
  и не доказательство производительности двух JVM/REST под нагрузкой.
- Внутренний `docker system df`: images 2.813 GB, local volumes 52.85 MB,
  build cache 744.6 MB. Логические категории могут разделять слои; legacy cache
  и прежний неиспользуемый RabbitMQ volume сохранены, действующий stack их не использует.
- Пять unit-тестов CLI, docs-drift, check-agent-migration и diff check — PASS.
  Полный Maven verify повторно не выполнялся: Java/UI не менялись, использован
  ранее собранный JAR и живые инфраструктурные проверки.
