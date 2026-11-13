# padel-core-service — Wiki

Spring Boot монолит для управления падель-турнирами. Целевой рынок — Аргентина.

---

## Документация

Переход среды разработки на Codex: [правила, запуск и активация конвейера](codex-migration.md).
Точка входа для агента — корневой `AGENTS.md`, подробные правила — `.agents/`.

| Раздел | Описание |
|--------|----------|
| [Организация бэклога](../.agents/ISSUE_ORGANIZATION.md) | Выбор Milestone при создании задачи, новый раздел при необходимости; [закреплённый индекс #1055](https://github.com/UstinKO/padel-core-service/issues/1055) |
| [Архитектура базы данных](database-architecture) | Все 21 таблица, столбцы, типы, enum-справочники, ERD, история миграций |
| [Доменный обзор](domain-overview) | 10 доменов, каждый HTTP-эндпоинт с описанием, анализ для декомпозиции в микросервисы |
| [Auth Flow](auth-flow) | Form Login, JWT, Google OAuth2, rate limiting, защита регистрации |
| [Архитектура приложения](architecture) | Слои, AOP observability, Prometheus/Loki/Grafana, Docker, CI/CD |
| [Многомодульный монолит](modular-monolith/README.md) | **Предварительный анализ LFPT-1041:** семь согласованных модулей, ADR и первая фаза PlayoffMatchingEngine с пулом задач/e2e с сохранением работоспособности после каждой задачи |
| [Инфраструктурная безопасность](infrastructure-security) | Nginx, CrowdSec, UFW, Docker port isolation, SSL, runbook инцидентов. **Приватный документ — не в репозитории** (детали защиты сервера) |
| [Исторический план микросервисов](microservices-migration-plan) | Справочный материал связей и рисков; текущий план — многомодульный монолит, Swarm исследуется отдельно |
| [Дизайн-токены as-is](design-tokens-as-is) | Аудит текущих цветов/типографики/отступов из 14 CSS-файлов перед переносом фронтенда на React |
| [Архетипы экранов](screen-archetypes) | 56 Thymeleaf-шаблонов сгруппированы в 13 layout-архетипов для захвата в Figma |
| [Переход на React](frontend-react/README) | **Актуально (2026-09-28):** ADR, поэтапный план, контракт текущего UI (экраны, API, сквозные механизмы, находки) |
| [Сетка нового дизайна](frontend-react/design-grid) | Демо-страницы нового дизайна от заказчика (2026-10-26) ↔ текущие маршруты и шаблоны; экраны без макета |
| [План переноса фронтенда](frontend-migration-plan) | *Заменён [frontend-react/](frontend-react/README) — исторический, июль 2026* |
| [Telegram → AI-конвейер](telegram-ai-pipeline) | Как работает Telegram-вход в `/develop-feature` (relay → роутина), runbook восстановления после отключения роутины (истёкшая подписка). **Приватный документ — не в репозитории** (доступы к серверу, идентификаторы роутины) |
| [Клиент 1Padel.App](../mobile-app/README.md) | Expo/React Native: исходники `mobile-app/`, веб-сборка на `/app/` (`static/app/`), какие API сервера использует |
| [Аварийный режим турнира](emergency-mode-analysis) | **Анализ (2026-10-23), ждёт ответов заказчика:** офлайн-проведение Team Playoff при недоступном сервере, непрерывные бэкапы, этапы и открытые вопросы |
| [Автономный конвейер](pipeline-autonomy) | **Актуально (2026-10-16):** дорожная карта «Telegram → прод без ревью»: движок задач, автомерж, порядок бэк→фронт, UI в чате |
| [Движок конвейера: баги и грабли](pipeline-engine-lessons) | Разобранные инциденты движка задач, роутины и автомержа (19–23.10.2026), проверенные и опровергнутые гипотезы, как диагностировать «задача висит», открытые задачи. **Читать перед правкой движка** |

---

## Как поддерживать актуальность

`docs/` — контекст для автономного конвейера: агенты читают его перед задачей, поэтому устаревший документ хуже отсутствующего. Правило: **кто меняет код — правит документ в том же PR** (роли конвейера — `.agents/roles/*.md`, проверка — Шаг 4 `.agents/workflows/develop-feature.md`). Технические правила и навигация Codex описаны в корневом `AGENTS.md` и `.agents/PROJECT.md`.

| Изменение в коде | Что обновить |
|------------------|--------------|
| Новый, изменённый или удалённый HTTP-эндпоинт | Таблица домена в [domain-overview](domain-overview); подробности — в `endpoints/<файл>.md`, если эндпоинт там описан; таблицы URL в `.agents/PROJECT.md` |
| Liquibase-миграция, поле entity, значение enum | [database-architecture](database-architecture): колонка, enum, «История миграций», версия схемы в шапке; «Текущая версия» в [architecture](architecture) и в `.agents/PROJECT.md` |
| `SecurityConfig`, `@PreAuthorize`, фильтры, JWT | [auth-flow](auth-flow), строка «Аутентификация» в `endpoints/*.md` |
| Email-шаблон | Таблица шаблонов в [domain-overview](domain-overview) § Notifications и в `.agents/PROJECT.md` |
| Деплой, CI, Docker, мониторинг | [architecture](architecture); конвейер — [pipeline-autonomy](pipeline-autonomy) |
| i18n | [i18n/README](i18n/README) |
| Клиент 1Padel.App (`mobile-app/**`) | `mobile-app/README.md` (контракт с сервером); пересобрать и закоммитить `static/app/` (`pnpm build:site`) |
| Исправлен баг из пометки `[BUG]` / `[MINOR]` / `[SECURITY — актуально]` | Заменить пометку на `[закрыто, сверено с кодом <дата>]` с одной фразой, как исправлено |

Расхождения, которые ловятся механически (версия схемы, таблицы/колонки/enum, неописанные и несуществующие эндпоинты, ссылки на несуществующие классы), проверяет скрипт:

```bash
python3 .github/bin/docs-drift.py   # код возврата 1 — есть расхождения, список в выводе
```

Смысл описаний скрипт не проверяет — за ним следит тот, кто меняет код. Исторические документы (`i18n/stage-*`, ADR в `frontend-react/adr/`, `frontend-migration-plan`) не актуализируются: это журнал решений на свою дату.

---

## Стек

```
Java 17 (source) / Java 21 (Docker)
Spring Boot 4.1 (Spring Framework 7, Security 7, Hibernate 7, Jackson 3)
PostgreSQL 15 + Liquibase
Thymeleaf (SSR)
Spring Security — JWT + Form Login + Google OAuth2
Spring WebSocket (STOMP/SockJS)
Micrometer → Prometheus → Grafana
Loki (structured JSON logs)
Bucket4j, MapStruct, Lombok
```

---

## Быстрый старт

```bash
# Требует PostgreSQL на localhost:5432/player_padel_db
./mvnw spring-boot:run

# Полный стек: приложение + postgres + loki + prometheus + grafana
docker compose up -d

# Сборка без тестов
./mvnw clean package -DskipTests
```

Обязательные env-переменные (если не используются дефолты):

```
DB_URL, DB_USERNAME, DB_PASSWORD
JWT_SECRET
MAIL_USERNAME, MAIL_PASSWORD
GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
SITE_KEY, SECRET_KEY
```

---

## Два типа пользователей

| Тип | Таблица | Роли |
|-----|---------|------|
| `PlayerPadel` | `player_padel_db` | `ROLE_PLAYER` |
| `Owner` | `owners_db` | `ROLE_OWNER`, `ROLE_ORGANIZER`, `ROLE_SUPER_ADMIN` |

---

## Форматы турниров

| Формат | Сервис | Real-time |
|--------|--------|-----------|
| King of Court | `KingOfCourtService` | WebSocket |
| Americano | `AmericanoService` | — |
| Team Americano | `TeamAmericanoService` | — |
| Bracket (сетка) | `BracketService` | — |

---

## Prod-окружение

| Параметр | Значение |
|---------|---------|
| Домен | `epadel.org` |
| Сервер | Ubuntu 22.04 (адрес не публикуется) |
| Grafana | `https://epadel.org/grafana/` |
| Deploy | Push в `master` → GitHub Actions → Docker |

Измерение рекламы: [Meta — события, согласие, атрибуция и CAPI](endpoints/meta-marketing.md).

- [Выпуск и восстановление UI v2](ui-v2-release.md) — артефакт, проверки, снимки и rollback.
- [Закрытый тестовый контур UI v2](ui-v2-preview.md) — локальный запуск, синтетический seed, изоляция, проверка и отключение.
- [Диагностика и reset preview](endpoints/ui-preview.md) — контракт только для SUPER_ADMIN закрытого контура.
