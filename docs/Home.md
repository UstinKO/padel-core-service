# padel-core-service — Wiki

Spring Boot монолит для управления падель-турнирами. Целевой рынок — Аргентина.

---

## Документация

| Раздел | Описание |
|--------|----------|
| [Архитектура базы данных](database-architecture) | Все 20 таблиц, столбцы, типы, enum-справочники, ERD, история миграций |
| [Доменный обзор](domain-overview) | 10 доменов, каждый HTTP-эндпоинт с описанием, анализ для декомпозиции в микросервисы |
| [Auth Flow](auth-flow) | Form Login, JWT, Google OAuth2, rate limiting, защита регистрации |
| [Архитектура приложения](architecture) | Слои, AOP observability, Prometheus/Loki/Grafana, Docker, CI/CD |
| [Инфраструктурная безопасность](infrastructure-security) | Nginx, CrowdSec, UFW, Docker port isolation, SSL, runbook инцидентов. **Приватный документ — не в репозитории** (детали защиты сервера) |
| [План перехода на микросервисы](microservices-migration-plan) | Поэтапный план: Swarm-масштабирование, выделение сервисов, мобильные API, новые продукты |
| [Дизайн-токены as-is](design-tokens-as-is) | Аудит текущих цветов/типографики/отступов из 14 CSS-файлов перед переносом фронтенда на React |
| [Архетипы экранов](screen-archetypes) | 56 Thymeleaf-шаблонов сгруппированы в 13 layout-архетипов для захвата в Figma |
| [Переход на React](frontend-react/README) | **Актуально (2026-09-28):** ADR, поэтапный план, контракт текущего UI (экраны, API, сквозные механизмы, находки) |
| [План переноса фронтенда](frontend-migration-plan) | *Заменён [frontend-react/](frontend-react/README) — исторический, июль 2026* |
| [Telegram → AI-конвейер](telegram-ai-pipeline) | Как работает Telegram-вход в `/develop-feature` (relay → роутина), runbook восстановления после отключения роутины (истёкшая подписка). **Приватный документ — не в репозитории** (доступы к серверу, идентификаторы роутины) |
| [Автономный конвейер](pipeline-autonomy) | **Актуально (2026-10-16):** дорожная карта «Telegram → прод без ревью»: движок задач, автомерж, порядок бэк→фронт, UI в чате |

---

## Стек

```
Java 17 (source) / Java 21 (Docker)
Spring Boot 3.5.14
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
