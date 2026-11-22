# LFPT-1208 · Перенос Compose → тестовый Stack

Источник — корневой `docker-compose.yml`; результат — `automation/swarm/stack.json`.
Синтетический stand не является production-конфигурацией. Имена существующих
production volumes и приватные mounts переносятся только после #1209.

| Compose | Stand Stack | Проверка / граница |
|---|---|---|
| `build: .` | JAR/image собирается до deploy; app запускается по image ID/digest | Локальный DinD builds JAR-образ; переносимый render принимает готовый digest |
| `container_name` | Удалён; identity service/task | Сервисы имеют stable DNS, задача может менять IP |
| `bridge padel-network` | Overlay `web`, `private`, `events` internal; `edge` для proxy | App без внешнего egress, discovery `tasks.<service>`; две sleep-tasks проверяются без второй JVM |
| `depends_on: service_healthy` | Удалён | PG healthcheck, app health/readiness, ожидание точного состава и завершения rollout; Java может перезапускаться до готовности БД |
| `restart: unless-stopped` | `deploy.restart_policy.condition: any` | Восстанавливается и штатно остановленная task после запуска Engine |
| Публичные app/PG/monitoring ports | Только Nginx host ports | DinD публикует loopback 18080/18443; native Swarm требует выделенной изолированной ВМ/периметра |
| `.env`, env passwords | Случайные Swarm secrets; у app configtree | Production `.env` не загружается; файл ключа REST пока резерв для #1211 |
| TLS/bind config | Swarm secrets/configs с UUID и content hash | Старые версии сохраняются для rollback; пароли существующей БД не меняются env автоматически |
| PG `postgres_data` | Собственный `padel-lab_database`, preview DB + UUID | Seed и Liquibase только синтетической БД; production migration/restore — #1209 |
| `logs`, `logs/archive` | Локальная ФС отдельной app task | Не общий bind двух JVM; долговечный сбор и retention — #1216 |
| Том Loki | Собственный `padel-lab_loki` | Контейнер готов, ingestion приложения ещё не настроен (#1216) |
| Том Prometheus | Собственный `padel-lab_prometheus`, retention 2 d | DNS task discovery, `target=up` в check |
| Том Grafana | Собственный `padel-lab_grafana`, datasource configs | UI не публикуется; пароль через file secret |
| `json-file` ротация | Стандартный daemon logging тестового Engine | Не переносить private production log paths; task labels/сбор — #1216 |
| Production integrations | Preview + локальный Mailpit | OAuth/Telegram/Meta/schedulers отключены; проверка настоящих интеграций — отдельный допуск |
| Нет Redis в Compose | Один Redis внутри `private` | Только подготовка session/quota store; интеграции #1210/#1212 |
| Старый RabbitMQ stand | Service/image/secret/volume удалены из нового описания | `deploy --prune` снимает старый service своего stack; прежние локальные volume/image/secret автоматически не стираются |
| Будущий межрепличный REST | Сеть `events`, `cluster-events-key`, запрет `/internal/**` | Endpoint, service-auth, DTO, retry/dedup/reconciliation — #1211 |
| Host Nginx / защита | Stand Nginx TLS, forwarded IP overwrite, rate limit, WS Upgrade | CrowdSec/CDN CIDR/ACME/private vhost parity пока не проверен; блокирует production #1218 |
| Production retained-assets/rating mounts | Не подключаются к synthetic preview | Инвентаризация и перенос при необходимости — #1209/#1215; не выдавать отсутствие mount за доказанный production-паритет |

Update/rollback — stop-first, parallelism=1. Две реальные JVM и их общие
состояния/фоновые процессы проверяются #1210–#1214; нагрузочный допуск — #1217.
