# LFPT-1209 · Тома и восстановление данных

Задача [#1209](https://github.com/UstinKO/padel-core-service/issues/1209) продолжает
[стенд #1208](lab.md). Production-переключение остаётся #1218. Результат — явная
карта физических volumes, проверяемый backup/restore и повторяемая репетиция
Compose → Swarm → Compose с одним писателем и сохранением новых записей.

## Карта существующих томов

Логические имена Compose и Stack не являются именами существующих данных.
Префикс проекта может измениться. Для PostgreSQL, Grafana, Prometheus и Loki
нужно зафиксировать фактические mounts, image ID/digest, major version, driver,
UID/GID/права и узел хранения. Источник — работающий контейнер, а не предположение
по docker-compose.yml. Фактические production имена/пути остаются в закрытом файле.

| Логический ключ | Data directory | Что сохраняем |
|---|---|---|
| database | /var/lib/postgresql/data | Подтверждённые данные, schema/constraints/indexes, sequences, extensions и права |
| grafana | /var/lib/grafana | SQLite, dashboards, настройки, plugins; secret_key и provisioning отдельно |
| prometheus | /prometheus | TSDB, WAL и история метрик; scrape config отдельно |
| loki | /loki | Данные согласно действующему storage config; tenant/auth/config отдельно |

Для инвентаризации внутри собственного Engine:

```shell
python automation/swarm/rehearse_data.py inventory --container <container-name>
```

Команда сохраняет только безопасные поля inspect в игнорируемый runtime/data-inventory.json,
не копирует Config.Env или значения секретов. Для production оператор делает аналогичный
read-only сбор на нужном Engine и сохраняет результат приватно. В этой задаче production
не подключался; новая фактическая карта production и проверка offsite остаются допуском #1218.

Приватная JSON-карта должна содержать ровно database/grafana/prometheus/loki с
реальными физическими именами. Перед включением карты проверить `docker volume inspect`
каждого имени, node placement, отсутствие пустого PGDATA и соответствие PG_VERSION.
Неверная карта или отсутствующий volume — STOP, без автоматического создания замены.

```shell
python automation/swarm/lab.py render --app-image <repository@sha256:digest> --volume-map <private-volume-map.json>
docker stack config -c automation/swarm/runtime/stack.local.json
```

Render явно задаёт external/name для четырёх хранилищ и сохраняет прочие volumes
без изменений. Отвергает неполную карту, подстановки, пути и повторное назначение
одного volume разным хранилищам. Render не проверяет удалённый Engine и не разрешает
production deploy: оператор обязан сверить существование/содержимое томов до переключения.
Локальный `lab.py deploy --volume-map ...` запрещён, чтобы карта не игнорировалась молча.

## Синтетическая репетиция

Нужен готовый запущенный [стенд #1208](lab.md) с синтетической БД, JAR с миграцией
v1.88 и локальный Docker Desktop / Linux context. CLI использует только собственный
вложенный Engine с совпадающим UUID; Swarm хоста и соседние проекты не меняются.

```shell
python automation/swarm/lab.py start
python automation/swarm/lab.py deploy
python automation/swarm/rehearse_data.py rehearse
```

Репетиция выполняет следующие действия:

1. Занимает атомарный lease по имени контейнера внутри Engine. Другой процесс не
   может одновременно начать rehearsal/cleanup. Создаёт восемь собственных volumes
   с UUID labels: четыре для исходного Compose, четыре для восстановленной копии.
2. Проверяет UUID в ui_preview.control исходного стенда, делает custom pg_dump и
   восстанавливает его в исходный собственный volume. Действующий PGDATA не копируется
   как горячие файлы. Создаёт контрольную таблицу только в отдельной копии.
3. Запускает изолированный Compose PostgreSQL/Grafana/Prometheus/Loki без публичных
   портов. Создаёт dashboard Grafana, строку Loki и исторический sample Prometheus.
   Фиксирует фактические mounts. Останавливает записи, получает финальный
   dump, сверяет все пользовательские таблицы по числу строк и SHA-256, sequences
   по last_value/is_called, constraints, indexes, columns, owners, locale и extensions.
4. Останавливает исходные процессы. Архивирует monitoring volumes только после
   остановки, сохраняет UID/GID/mode и содержимое файлов. Проверяет checksum и
   безопасность архива, восстанавливает в пустые отдельные volumes и сверяет
   нормализованные fingerprints файлов/metadata перед запуском сервисов.
5. Восстанавливает PostgreSQL с pg_restore --exit-on-error --single-transaction.
   Инициализация нового PGDATA выполняется закреплённым образом с ожидаемым владельцем;
   версия/extensions и данные сравниваются. SHA-256 backup проверяется и локально,
   и внутри Engine до записи в целевой volume.
6. Подключает восстановленные external volumes к Swarm. Проверяет данные, явный
   placement на текущем узле и одного писателя. Проверка занятого тома должна отказать.
   Записывает marker после перехода; повторный deploy не должен обнулить данные.
   Отдельно выполняет `service update --force` для PostgreSQL: ждёт завершения rollout
   и нового работающего task ID. Проверяет прежние физические volumes и сохранность
   всех данных, включая запись, сделанную уже в Swarm. Старый task с `1/1` не подходит.
7. Перед возвратом делает ещё один dump, снимает Swarm и ждёт освобождения томов.
   Compose подключает **актуальные восстановленные тома**, содержащие запись из Swarm,
   а не прежнюю копию до переключения. Данные повторно сверяются, запрет записей снимается.
   После обоих переходов API проверяют сохранность dashboard, исторического sample
   и строки журнала, а не только наличие файлов в monitoring volumes.
8. Независимо пытается остановить свои Stack, Compose и временный restore-контейнер,
   даже если одна из остановок завершилась ошибкой. Подтверждает отсутствие writers
   для всех восьми volumes; фиксирует UID/GID/mode, пока lease ещё удерживается.
   Сохраняет volumes/backup/report для проверки. Если остановка не подтверждена,
   сохраняет lease для восстановления оператором. Ошибка до первого запуска writer
   освобождает lease, в том числе при невозможности создать каталог отчёта.

Отчёт, исходная/целевая карта, dumps и tar находятся в runtime/padel-data-<id>/.
Они не публикуются: dump содержит синтетические аккаунты и хэши паролей.
`--no-owner --no-acl` допустим здесь только для проверяемой одно-владельческой
preview БД. Это не инструкция восстанавливать production без владельцев/ACL.

Защиты проверяют и реальные контейнеры, и Swarm services с replicas > 0, включая
pending tasks. Чужой UUID, non-local driver/options, существующее имя нового volume,
непустая цель, занятой volume или несовпадающий checksum прекращают операцию.
Сервисы после снятия Stack больше не должны автоматически создавать писателя.
Все команды переключения должны соблюдать lease; прямые административные Docker
команды способны обойти программный протокол и в репетиции не допускаются.

## Очистка и прерывание

```shell
python automation/swarm/rehearse_data.py cleanup --run <32-hex-run-id>
```

Cleanup занимает тот же lease, проверяет UUID services/контейнеров/volumes,
останавливает только ресурсы этой репетиции и удаляет только её незанятые volumes.
Остановки независимы; при неподтверждённом отсутствии writers lease сохраняется.
При ошибках остановки удаление отменяется даже после подтверждения свободных томов;
в этом случае lease можно освободить, а cleanup повторить после устранения ошибки.
Backup и отчёт остаются. Для других run ID выполняется отдельная команда.
Нельзя использовать system/volume prune или удалять объекты по одному префиксу имени.

При аварийном завершении процесса lease может остаться. Сначала оператор сверяет
его label com.padel.swarm-data, run ID и отсутствие живого процесса репетиции,
останавливает именно её writers и проверяет pending services. Только затем удаляет
подтверждённый оставшийся lease и запускает cleanup. Автоматического перехвата lease
по таймеру нет: это могло бы допустить второго писателя.

## Production runbook для последующих этапов

1. До окна проверить закрытую карту volumes/node, pinned major/minor PostgreSQL,
   extensions, роли/ownership/ACL, locale, UID/GID, свободный диск для копий и архивов.
   Проверить действующий backup timer, retention, offsite-доставку и доступ к ключу age.
   Зашифрованный архив без подтверждённой расшифровки и restore не считается backup.
2. Объявить окно и закрыть мутации на proxy/API; остановить фоновые writers, polling,
   runner и внешние импорты. Дождаться завершения активных транзакций. Проверить запрет
   записей отдельным клиентом. Настройка default_transaction_read_only — дополнительная
   страховка, не замена остановке writers: привилегированная сессия может её обойти.
3. Сделать финальную согласованную копию. Для логического PostgreSQL dump включить
   требуемые роли/globals отдельно, хранить их зашифрованно (могут содержать секреты),
   восстановить роли до объектов и сохранить ownership/ACL. Остановить monitoring
   перед файловой копией SQLite/TSDB/WAL/Loki, сохранить config/provisioning/Grafana secret_key
   и нужные TLS/Swarm secrets отдельным закрытым каналом.
4. Полностью остановить старый PostgreSQL и Compose writers, проверить отсутствие
   restart/pending tasks. На одной ВМ старый и новый PostgreSQL не монтируют один
   data directory одновременно. Не выполнять up --force-recreate до сверки volume-map.
5. Подключить проверенные existing external volumes либо подготовленные восстановленные
   копии. Запустить один PostgreSQL на закреплённом узле, сверить версии/данные/права,
   затем мониторинг и app по #1215. Включить записи только после принятого smoke.
6. При ошибке до открытия записей снять новый stack и вернуть прежний Compose с
   проверенными актуальными томами. После открытия записей старая копия может
   потерять новые данные: откат app выполняется на текущей БД при совместимой схеме.
   Restore более ранней БД — отдельное решение владельца с учётом RPO и сверкой записей.

Цели стенда: первичное окно ≤15 min, возврат app ≤5 min, restore БД ≤30 min;
аварийный RPO ≤24 h только при исправном ежедневном backup и offsite-доставке.
Измерение малой синтетической БД не подтверждает production RTO или RPO.
Полная репетиция нагрузки/браузеров/REST и отказа общей БД — #1211/#1217.

## Исправление SQL функции перед backup

Первый реальный pg_restore упал на idx_player_claim_phone: функция player_claim_phone
обращалась к player_claim_phone_argentina без схемы при ограниченном search_path.
v1.88 квалифицирует helper именем public, сохраняя AR/+7-нормализацию и совместимость
предыдущего JAR. При SQL rollback квалификация намеренно остаётся: дефект не возвращается.
Новый dump после миграции восстанавливается обычным pg_restore целиком.

Старые backup до v1.88 требуют отдельной репетиции восстановления: нельзя игнорировать
ошибку и запускать частично восстановленную БД. В закрытом recovery-контуре оператор
восстанавливает pre-data/data, применяет проверенное определение функции v1.88,
затем post-data и сверяет constraints/indexes/данные. Это не меняет исходный архив;
результат не считается принятым до полной проверки. Production в этой задаче не менялся.

## Проверки

```shell
python -m unittest discover -s automation/swarm -p "test_*.py"
```

Unit-тесты охватывают карту, ownership, активный/pending writer, повреждение backup,
небезопасный tar, metadata и lease; они включены в PR Check. Регрессионный Java-тест
проверяет выражение индекса и AR/+7 при search_path pg_catalog.
[Результаты и измерения 23.11.2026](data-recovery-validation.md);
отчёты с приватными картами остаются runtime.

Официальные основания: [PostgreSQL SQL dump](https://www.postgresql.org/docs/15/backup-dump.html),
[Docker volumes](https://docs.docker.com/engine/storage/volumes/).
