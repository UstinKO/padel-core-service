# LFPT-323 — Этап 0: валидация облачного окружения routines

## Статус
done — смержено и задеплоено, [PR #325](https://github.com/UstinKO/padel-core-service/pull/325), деплой подтверждён успешным. Среда пригодна для Developer/Tester, с тремя обязательными поправками к плану (см. "Итог" в конце файла).

## Источник
Часть плана [telegram-remote-pipeline.md](telegram-remote-pipeline.md), Этап 0 (блокирующий). Issue: https://github.com/UstinKO/padel-core-service/issues/323

Это не обычная фиче-спека (нет кода, нет критериев приёмки в привычном виде) — рабочий документ для конкретного разового прогона-валидации. После получения результата — либо переходим к Этапу 1 (см. родительский план), либо фиксируем, что облачная среда не годится, и рассматриваем план Б (self-hosted вместо Anthropic-managed routine).

## Что нужно сделать (тебе, в вебе)

1. Зайти на `claude.ai/code/routines`, создать новую роутину.
2. Подключить репозиторий `UstinKO/padel-core-service` (через GitHub App, роутина сама предложит).
3. Настроить окружение: сетевой доступ — минимум "Trusted" (нужен доступ к Docker Hub для образа `postgres:15` и к Maven Central для зависимостей).
4. В промпт роутины вставить текст из раздела "Промпт для роутины" ниже — дословно.
5. Нажать "Run now".
6. Прислать архитектору (мне) ссылку на сессию/лог прогона — либо просто вставить в Telegram/чат текст финального результата, если ссылка не нужна.

## Промпт для роутины

```
Ты в одноразовой облачной песочнице, репозиторий padel-core-service уже склонирован в текущую директорию. Задача — проверить, что здесь можно собрать и протестировать проект.

Выполни по порядку и не пропускай шаги:

1. Проверь, что Docker доступен: `docker --version && docker compose version`.
2. Подними Postgres для тестов (не весь docker-compose.yml — там ещё app/loki/prometheus/grafana, они не нужны для этой проверки):
   docker run -d --name test-postgres \
     -e POSTGRES_DB=player_padel_db \
     -e POSTGRES_USER=epadel_user \
     -e POSTGRES_PASSWORD=test \
     -p 5432:5432 \
     postgres:15
3. Подожди, пока Postgres реально готов принимать соединения (не просто "контейнер запущен" — дождись healthy, например через `pg_isready -h localhost -p 5432` в цикле с таймаутом ~60 секунд).
4. Прогони полный набор тестов:
   DB_URL=jdbc:postgresql://localhost:5432/player_padel_db DB_USERNAME=epadel_user DB_PASSWORD=test ./mvnw clean verify
5. Проверь доступность headless-браузера: установи Playwright (`npm init -y && npm install playwright && npx playwright install --with-deps chromium`), открой любую простую страницу (например `https://example.com`) headless-Chromium и убедись, что получаешь HTML в ответ — это симуляция того, что понадобится Tester-агенту для QA.

В конце пришли отчёт:
- Сколько тестов прогнано, сколько упало, полный текст любых ошибок (не сокращай).
- Сработал ли Docker без дополнительных прав/обходных путей.
- Сработал ли Playwright headless-запуск, сколько времени заняла установка браузера.
- Любые warning/ограничения, которые встретились по пути (лимиты памяти, таймауты, сетевые блокировки).
- Не удаляй и не изменяй ничего в самом репозитории — это чисто диагностический прогон, никаких коммитов/PR тут не нужно.
```

## Что делаю я после получения результата

Разбираю лог/отчёт, сверяю с рисками из `telegram-remote-pipeline.md` (особенно риск №1 и №3), и говорю однозначно: годится среда для Developer/Tester-агентов конвейера или нет, и если не годится — что конкретно не работает.

## Результат раунда 1 (2026-08-19, session cse_012sApuihh586kQUT1rWBjUC)

- **Docker Hub заблокирован намеренно** — политика egress-прокси (`gateway answered 403 to CONNECT (policy denial)` для `production.cloudfront.docker.com`), не временный сбой. `docker pull`/`docker run <любой не закешированный образ>` не работает в этой песочнице.
- **Postgres — работает через предустановленный локальный сервис** (PostgreSQL 16, не Docker): `service postgresql start` + `CREATE ROLE epadel_user` + `CREATE DATABASE player_padel_db`.
- **`./mvnw clean verify` — 50/50 тестов, BUILD SUCCESS, 1m29s.** Критерий готовности Этапа 0 выполнен, но другим способом, чем планировали.
- **Playwright** — браузер установлен и механически исправен (headless-рендер `data:` URL — мгновенно), но весь **внешний** интернет с браузера заблокирован той же политикой (`example.com` → `ERR_TUNNEL_CONNECTION_FAILED`). Не проверено: доступ на `localhost` (реальный сценарий Tester-а) — `no_proxy` в окружении явно включает `localhost`/`127.0.0.1`, есть основания полагать, что сработает, но не подтверждено.

## Промпт раунда 2 — проверка Playwright против localhost (закрывает последний открытый вопрос)

Заменить весь текст промпта в роутине (Edit routine → карандаш) на этот, затем "Run now":

```
Ты в той же облачной песочнице, репозиторий padel-core-service уже склонирован. Задача — проверить последний открытый вопрос: работает ли headless-браузер (Playwright) против localhost, если само приложение запущено в этой же песочнице. Docker здесь не работает (заблокирован политикой) — не пытайся его использовать.

Выполни по порядку:

1. Подними Postgres как в прошлый раз (предустановленный локальный сервис, не Docker):
   service postgresql start
   sudo -u postgres psql -c "DROP DATABASE IF EXISTS player_padel_db;"
   sudo -u postgres psql -c "DROP ROLE IF EXISTS epadel_user;"
   sudo -u postgres psql -c "CREATE ROLE epadel_user WITH LOGIN PASSWORD 'test';"
   sudo -u postgres psql -c "CREATE DATABASE player_padel_db OWNER epadel_user;"
   Дождись готовности через pg_isready -h localhost -p 5432 (цикл с таймаутом ~30 секунд).

2. Запусти само приложение в фоне (профиль dev — у него мягкие дефолты для локального запуска, без реальных email/OAuth/recaptcha кредов):
   DB_URL=jdbc:postgresql://localhost:5432/player_padel_db \
   DB_USERNAME=epadel_user \
   DB_PASSWORD=test \
   SPRING_PROFILES_ACTIVE=dev \
   nohup ./mvnw spring-boot:run > /tmp/app.log 2>&1 &

3. Дождись реальной готовности (не просто "процесс стартовал"): опроси в цикле с таймаутом ~90 секунд:
   curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
   Должен вернуть 200. Если за 90 секунд не поднялось — покажи полный /tmp/app.log, не сокращай.

4. Установи Playwright (если ещё не установлен с прошлого раза) и напиши скрипт, который headless-Chromium открывает:
   - http://localhost:8080/actuator/health — ожидаем JSON с "status":"UP"
   - http://localhost:8080/ (главная страница) — ожидаем 200 и непустой HTML (например, наличие тега <html> и какого-то заметного текста/title)
   Замерь время загрузки каждой страницы.

5. Останови приложение (kill фонового процесса), проверь git status --short — должно быть пусто (кроме target/, он в gitignore). Не коммить, не пушить, не менять ничего в репозитории.

В конце пришли чёткий отчёт: заработал ли Playwright против localhost для обеих проверок (health и главная страница), сколько заняла загрузка приложения (от старта до первого 200 на /actuator/health), сколько заняла загрузка каждой страницы в браузере, и любые ошибки текстом целиком, если что-то не получилось.
```

## Результат раунда 2 (2026-08-19, session cse_01NkguHWWFzXg284obZjadqQ)

- **Приложение стартует за 15.55 сек** (`Started PadelCoreServiceApplication in 15.553 seconds`), Postgres — за ~2 сек.
- **`/actuator/health` стабильно 503**, не флуктуация. `mail`-компонент виснет на `smtp-relay.brevo.com:587` (реальный внешний хост из `application.yml`) и падает по таймауту — сеть песочницы блокирует внешний трафик. `db`/`diskSpace`/`livenessState`/`readinessState`/`ssl` — все `UP`.
- **Playwright → localhost — РАБОТАЕТ.** Headless-Chromium реально открыл обе страницы: `/actuator/health` (получил 503 + JSON тело — доказывает, что сеть до localhost открыта, проблема чисто в приложении, не в браузере/сети), `/` — 200, валидный HTML, `<title>1-Padel - Padel tournaments</title>`, тело 10582 символа. Это закрывает последний открытый вопрос Этапа 0.
- **Найдена причина 503, не только симптом**: `application-dev.yml` (в котором уже стоит `management.health.mail.enabled: false`) — **сам файл в `.gitignore`** (`application-*.yml` игнорируется, кроме `application.yml`/`application-prod.yml`), то есть при клонировании `master` в облачную песочницу этого файла там просто нет — весь dev-профиль в облаке фактически пустой, приложение падает обратно на базовый `application.yml` с реальным SMTP-хостом. Тот же класс проблемы, что мы уже решали с `.claude/` для worktree — только на этот раз для профиля Spring, не для конфигурации Claude Code.

## Итог

Среда пригодна для Developer/Tester-конвейера. Три обязательные поправки к плану:

1. **Postgres — через `service postgresql start` + `createdb`/`createuser`, не Docker.** Docker Hub заблокирован сетевой политикой намеренно (подтверждено дважды через `__agentproxy/status`), не временный сбой.
2. **Готовность приложения — не через агрегированный `/actuator/health`.** В облаке он всегда 503 из-за недостижимого SMTP. Использовать grep по логу на `"Started PadelCoreServiceApplication"` (уже есть готовый паттерн в `deploy.yml`) или новый профиль `cloud` (см. ниже) вместо агрегированного статуса.
3. **Новый Spring-профиль `application-cloud.yml`** (создан и **закоммичен в git** — в отличие от `application-dev.yml`, специально не игнорируется) — мокает SMTP на `localhost:1025` (никто не слушает, ошибка глушится существующим catch-блоком EmailService, как и в dev) и явно отключает `management.health.mail.enabled`, чтобы `/actuator/health` не зависел от недостижимого внешнего хоста. Developer/Tester в облачных прогонах должны использовать `SPRING_PROFILES_ACTIVE=cloud`, не `dev`.
4. **Playwright для QA — подтверждён рабочим**, headless-Chromium свободно достаёт localhost, замены/донастройки не требуются кроме уже известного (не `claude-in-chrome`, а `npx playwright`).
