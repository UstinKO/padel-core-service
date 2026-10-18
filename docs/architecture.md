# Архитектура приложения — padel-core-service

---

## Содержание

1. [Общий обзор](#1-общий-обзор)
2. [Слои приложения](#2-слои-приложения)
3. [Конфигурация и среды запуска](#3-конфигурация-и-среды-запуска)
4. [База данных и миграции](#4-база-данных-и-миграции)
5. [Observability: метрики, логи, трейсинг](#5-observability)
6. [AOP: сквозные аспекты](#6-aop-сквозные-аспекты)
7. [Email и уведомления](#7-email-и-уведомления)
8. [WebSocket](#8-websocket)
9. [Docker и инфраструктура](#9-docker-и-инфраструктура)
10. [CI/CD и деплой](#10-cicd-и-деплой)

---

## 1. Общий обзор

**padel-core-service** — Spring Boot 3.x монолит с серверным рендерингом через Thymeleaf. REST API выставлен параллельно для мобильных / SPA клиентов.

```
┌──────────────────────────────────────────────────────────────────┐
│                        Браузер / API Client                      │
└────────────────────────────┬─────────────────────────────────────┘
                             │ HTTPS
                             ▼
                    ┌────────────────┐
                    │  Nginx 1.18    │  rate limiting, SSL termination,
                    │  :443          │  proxy → :8080, /grafana/ → :3000
                    └───────┬────────┘
                            │
                            ▼
             ┌──────────────────────────────┐
             │   Spring Boot (Docker :8080) │
             │                              │
             │  ┌──────────────────────┐    │
             │  │   Filter Chain       │    │
             │  │  RateLimit → JWT     │    │
             │  │  → FormLogin / OAuth │    │
             │  └──────────┬───────────┘    │
             │             │                │
             │  ┌──────────▼───────────┐    │
             │  │     Controllers      │    │
             │  │  (MVC + REST)        │    │
             │  └──────────┬───────────┘    │
             │             │                │
             │  ┌──────────▼───────────┐    │
             │  │      Services        │    │
             │  │  (@Transactional)    │    │
             │  └──────────┬───────────┘    │
             │             │                │
             │  ┌──────────▼───────────┐    │
             │  │    Repositories      │    │
             │  │   (Spring Data JPA)  │    │
             │  └──────────┬───────────┘    │
             └─────────────┼────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  PostgreSQL 15         │
              │  (Docker :5432)        │
              └────────────────────────┘
```

**Стек:**
- Java 17 (source) / Java 21 (Docker/CI)
- Spring Boot 3.5.14
- PostgreSQL 15 + Liquibase
- Thymeleaf (SSR)
- Spring Security (JWT + Form Login + Google OAuth2)
- Spring WebSocket (STOMP/SockJS)
- Micrometer + Prometheus + Grafana + Loki
- Bucket4j, MapStruct, Lombok

---

## 2. Слои приложения

### Структура пакетов

```
com.padle.core.padelcoreservice
├── config/           ← Spring конфиги (Security, WebSocket, Beans)
├── controller/
│   ├── view/         ← Thymeleaf-страницы (публичные)
│   ├── player/       ← Дашборд, профиль, сброс пароля
│   ├── admin/        ← Панель администратора
│   ├── api/          ← REST JSON endpoints
│   │   └── americano/
│   └── test/         ← Только для тестирования организатором
├── service/          ← Бизнес-логика
│   └── americano/
├── repository/       ← Spring Data JPA
│   └── americano/
├── model/            ← JPA entities
│   ├── americano/
│   └── enums/
├── dto/              ← Data Transfer Objects
│   └── americano/
├── mapper/           ← MapStruct mappers
│   └── americano/
├── security/         ← JWT, фильтры, OAuth2
│   └── oauth2/
├── aspect/           ← AOP (Timed, Counted, TrackErrors)
├── annotation/       ← Кастомные аннотации
├── metrics/          ← MetricsService, EmailMetricsService
├── validation/       ← @NoBotPattern, @ValidDomain
├── exception/        ← Кастомные исключения
├── logging/          ← MaskingConverter (маскировка PII в логах)
└── util/             ← SecurityUtils, PasswordGenerator
```

### Слой Controller

Два типа контроллеров живут рядом:

| Тип | Аннотация | Возвращает | Пример |
|-----|-----------|-----------|--------|
| MVC (Thymeleaf) | `@Controller` | `String` (имя шаблона) или `redirect:` | `AdminController`, `TorneosController` |
| REST API | `@RestController` | `ResponseEntity<T>` / JSON | `TournamentController`, `AmericanoApiController` |

**Правило:** контроллеры не содержат бизнес-логики — только маппинг запрос → сервис → ответ.

### Слой Service

Все сервисы по умолчанию `@Transactional(readOnly = true)` на уровне класса. Методы записи явно помечены `@Transactional`.

Ключевые сервисы:

| Сервис | Ответственность |
|--------|----------------|
| `TournamentService` | CRUD турниров + регистрация/отмена + waitlist |
| `DoubleTournamentRegistrationService` | Парная регистрация, токены партнёра |
| `KingOfCourtService` | Логика формата King of Court, расчёт очков |
| `AmericanoService` | Americano: генерация раундов, статистика |
| `TeamAmericanoService` | Round Robin для фиксированных пар |
| `TeamPlayoffService` | Плей-офф квалификация + сетка |
| `BracketService` | Bracket-турниры, продвижение победителей |
| `PlayerService` | CRUD игроков, регистрация, подтверждение email |
| `EmailService` | Отправка всех транзакционных писем через Brevo SMTP |
| `PaymentService` | Учёт оплат |
| `RankingService` | Пересчёт рейтинга |
| `JwtService` | Генерация/валидация JWT |
| `WebSocketService` | Публикация событий в STOMP-топики |

### Слой Repository

Чистые Spring Data JPA репозитории. Кастомные JPQL-запросы через `@Query`.

**Важно:** `TestTournamentController` работает напрямую через `DataSource` (JDBC), минуя JPA — только для внутреннего тестирования.

### Слой Mapper

Все преобразования entity ↔ DTO через **MapStruct** (`componentModel = "spring"`). Ручной маппинг в контроллерах и сервисах запрещён.

```
Entity → DTO:  mapper.toDto(entity)
DTO → Entity:  mapper.toEntity(dto)
```

Lombok отрабатывает до MapStruct — обеспечивается `lombok-mapstruct-binding` в POM.

---

## 3. Конфигурация и среды запуска

### Переменные окружения

Все секреты вынесены в env-переменные с дефолтами для dev:

| Переменная | Дефолт | Описание |
|-----------|--------|----------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/player_padel_db` | JDBC URL |
| `DB_USERNAME` | `epadel_user` | Пользователь БД |
| `DB_PASSWORD` | _(из yml)_ | Пароль БД |
| `JWT_SECRET` | _(из yml)_ | HMAC-ключ подписи JWT |
| `JWT_EXPIRATION` | `86400000` | TTL access token (мс) |
| `JWT_REFRESH_EXPIRATION` | `604800000` | TTL refresh token (мс) |
| `MAIL_USERNAME` | _(из yml)_ | SMTP логин Brevo |
| `MAIL_PASSWORD` | _(из yml)_ | SMTP пароль |
| `GOOGLE_CLIENT_ID` | _(из yml)_ | OAuth2 Google |
| `GOOGLE_CLIENT_SECRET` | _(из yml)_ | OAuth2 Google |
| `SITE_KEY` / `SECRET_KEY` | _(из yml)_ | reCAPTCHA v3 |
| `LOKI_ENABLED` | `false` (dev) / `true` (prod) | Включить отправку логов в Loki |
| `LOKI_URL` | `http://loki:3100/...` | Адрес Loki |
| `APP_BASE_URL` | `http://localhost:5173` | Базовый URL для всех пользовательских ссылок на сайт в письмах (`EmailService`/`TournamentService`/`DoubleTournamentRegistrationService` и др. — единая точка, в шаблонах `templates/email/**` домен не хардкодится, LFPT-0618). На проде должен быть публичным доменом сайта (`https://1-padel.com`), а не серверным доменом из раздела «Сервер» ниже — это два разных адреса. |

### Локальный запуск

```bash
# Требует PostgreSQL на localhost:5432/player_padel_db
./mvnw spring-boot:run

# Полный стек с observability
docker compose up -d

# Сборка без тестов
./mvnw clean package -DskipTests
```

### Конфигурация подключений (HikariCP)

```yaml
hikari:
  maximum-pool-size: 10    # env: DB_POOL_SIZE
  minimum-idle: 5          # env: DB_MIN_IDLE
  connection-timeout: 30000
```

### Важные флаги

| Флаг | Dev | Prod |
|------|-----|------|
| `JPA_DDL_AUTO` | `none` | `validate` |
| `THYMELEAF_CACHE` | `false` | `true` |
| `AUTO_CONFIRM_EMAIL` | `false` | `false` |
| `LOKI_ENABLED` | `false` | `true` |
| `JPA_SHOW_SQL` | `false` | `false` |

---

## 4. База данных и миграции

### Liquibase

- **Только Liquibase** управляет схемой (`ddl-auto = none`).
- Файлы: `src/main/resources/db/changelog/versions/vX.XX-<description>.yaml`
- Мастер-файл: `changelog-master.yaml` — порядок include определяет порядок выполнения.
- Текущая версия: **v1.54**

**Добавление новой миграции:**
```
1. Создать файл: src/main/resources/db/changelog/versions/v1.55-<описание>.yaml
2. Добавить в конец changelog-master.yaml:
   - include:
       file: versions/v1.55-<описание>.yaml
       relativeToChangelogFile: true
```

### Именование таблиц

Все таблицы с суффиксом `_db`:
- `player_padel_db`, `owners_db`, `club_db`
- `tournaments_db`, `tournament_registrations_db`, `payments_db`
- `matches_db`, `ranking_db`
- `tournament_king_of_court_db`, `king_of_court_*_db`
- `americano_*_db`, `court_players_db`

### Транзакционность

- Read-only операции: `@Transactional(readOnly = true)` (дефолт на классе)
- Write операции: `@Transactional` на методе (переопределяет дефолт)
- `open-in-view: false` — соединение с БД не держится открытым в слое представления

---

## 5. Observability

### Стек

```
Spring Boot → Micrometer → Prometheus (scrape /actuator/prometheus)
                                    │
                                    ▼
                             Prometheus :9090
                                    │
                               Grafana :3000 (/grafana/)
                                    │
                    ┌──────────────┴──────────────┐
                    │                             │
              JVM дашборд                  APM дашборд

Spring Boot → logstash-logback-encoder → Loki :3100
                                              │
                                        Grafana Loki datasource
                                              │
                                        Application Logs дашборд

CrowdSec → Prometheus scrape :6060 → Grafana CrowdSec дашборд
```

### Grafana дашборды

| Дашборд | Что смотреть |
|---------|-------------|
| **JVM Micrometer** | Heap, GC, thread pool, CPU load |
| **SpringBoot APM** | HTTP latency, error rate, throughput |
| **Application Logs** | Лог-стримы в реальном времени (фильтр по уровню, тексту) |
| **Email Business Metrics** | Дневной лимит писем, ошибки, распределение по типам |
| **CrowdSec Security** | Активные блокировки IP, алерты, атаки |

Grafana: `https://epadel.org/grafana/`

### Prometheus метрики приложения

#### Автоматические (Spring Boot Actuator)

| Метрика | Описание |
|---------|----------|
| `http.server.requests` | Latency, count, error rate по эндпоинтам |
| `hikari.connections.*` | Состояние пула БД |
| `jvm.memory.*` | Heap, non-heap |
| `jvm.gc.*` | GC паузы, коллекции |
| `system.cpu.usage` | CPU нагрузка |

#### Кастомные (Email)

| Метрика | Тип | Описание |
|---------|-----|----------|
| `email.daily.total` | Gauge | Отправлено писем сегодня |
| `email.daily.limit` | Gauge | Дневной лимит (300) |
| `email.send.attempts{type}` | Counter | Попыток отправки по типу |
| `email.sent.total{type}` | Counter | Успешно отправлено |
| `email.errors.total{type,error}` | Counter | Ошибок при отправке |
| `email.rejected.total{type,reason}` | Counter | Отклонено (превышен лимит) |

Типы писем в тегах: `REGISTRATION_CONFIRMATION`, `TOURNAMENT_CONFIRMATION`, `VACANCY_INVITATION`, `WAITLIST_NOTIFICATION`, `NEW_PARTNER_INVITATION`, `PASSWORD_RESET`

#### Кастомные (через AOP-аннотации)

Метрики создаются автоматически для методов, помеченных `@Timed`, `@Counted`, `@TrackErrors` — подробнее в разделе 6.

### Логирование

Формат — структурированный JSON через `logstash-logback-encoder`.

```yaml
logging:
  level:
    com.padle.core: INFO       # env: LOG_LEVEL
    org.springframework.security: WARN
    org.hibernate: WARN
```

**Маскировка PII:** `MaskingConverter` скрывает чувствительные данные (email, телефоны) в логах перед отправкой в Loki.

**Ротация файлов:**
- `max-file-size: 10MB`
- `max-history: 30` дней
- `total-size-cap: 1GB`

### Actuator endpoints

Эндпоинты (`/actuator/*`, LFPT-0604):

| Endpoint | Доступ |
|---------|----------|
| `/actuator/health`, `/actuator/health/{liveness,readiness}` | Анонимно; снаружи через Nginx — тоже (мониторинг, readiness деплоя) |
| `/actuator/prometheus` | Анонимно, но только изнутри Docker-сети (скрейп Prometheus `padel-app:8080`); снаружи Nginx отдаёт 404 |
| `/actuator/metrics`, `/actuator/info`, `/actuator/loggers` | Только после логина; в prod не выставлены (`exposure: health,prometheus`) |

Снаружи Nginx пропускает из `/actuator` только `health`, остальное — 404.

---

## 6. AOP: сквозные аспекты

Три кастомных аннотации для инструментирования методов сервисного слоя:

### `@Timed`

Измеряет latency метода и пишет в Prometheus `Timer`.

```java
@Timed(name = "tournament.register", description = "Player registration time")
public TournamentRegistrationDto registerPlayer(Long tournamentId, Long playerId) { ... }
```

**Поведение:**
- При успехе → `Timer.stop(sample)` с тегами
- При ошибке → `Timer.stop(sample)` + добавляет тег `error=ExceptionClassName`
- Имя метрики: если `name` не задан → `ClassName.methodName`
- Теги в формате `"key=value"` в массиве `tags`

### `@Counted`

Инкрементирует `Counter` при каждом вызове метода.

```java
@Counted(name = "player.registration.attempts", description = "Registration attempts")
public PlayerResponseDto registrarJugador(RegistroRequestDto request) { ... }
```

**Поведение:**
- Срабатывает **до** выполнения (`@Before`)
- Дополнительно считает по типу результата (`AfterReturning`)

### `@TrackErrors`

Инкрементирует `Counter` только при выбросе исключения.

```java
@TrackErrors(name = "email.send.errors", exceptions = {MailException.class})
public void sendEmail(...) { ... }
```

**Поведение:**
- Срабатывает только при `@AfterThrowing`
- Если `exceptions = {}` — трекает все исключения
- Автоматически добавляет тег `exception=ExceptionClassName`

### Принцип работы

```
Вызов метода сервиса
        │
        ▼
   TimedAspect.measure()      ← @Around
        │
        ▼
   CountedAspect.count()      ← @Before
        │
        ▼
   [метод выполняется]
        │
        ├── успех → CountedAspect.countWithResult() [@AfterReturning]
        │            TimedAspect завершает таймер
        │
        └── ошибка → TrackErrorsAspect.trackError() [@AfterThrowing]
                      TimedAspect завершает таймер с тегом error=...
```

---

## 7. Email и уведомления

### Провайдер

**Brevo (ex Sendinblue)** через SMTP-relay:
- Host: `smtp-relay.brevo.com:587`
- STARTTLS обязателен
- Дневной лимит: **300 писем** (отслеживается через `EmailMetricsService`)

### Шаблоны

Все письма — Thymeleaf HTML-шаблоны в `src/main/resources/templates/email/`:

| Шаблон | Событие-триггер |
|--------|----------------|
| `bienvenida.html` | Регистрация нового игрока |
| `confirmacion.html` | Ожидание подтверждения email |
| `tournament-confirmation.html` | Регистрация на турнир подтверждена |
| `new-partner-invitation.html` | Приглашение партнёра в пару |
| `vacancy-invitation.html` | Приглашение из листа ожидания |
| `waitlist-notification.html` | Попал в лист ожидания |
| `no-spots-left.html` | Мест нет совсем |
| `recuperar-password.html` | Сброс пароля |

### Защита от переспама

При превышении лимита (`email.daily.total >= email.daily.limit`):
- Письмо не отправляется
- `EmailMetricsService.recordEmailRejected()` инкрементирует счётчик

### Cron-задача: лист ожидания

```
0 */10 * * * *  →  TournamentService.checkExpiredInvitations()
```
Каждые 10 минут: находит просроченные `WAITLIST_INVITED`, переводит их в `WAITLIST` или `CANCELLED`, приглашает следующего.

---

## 8. WebSocket

Используется только для формата **King of Court** — real-time обновления состояния турнира на экране организатора и зрителей.

### Конфигурация

```java
// WebSocketConfig
registry.addEndpoint("/ws").withSockJS();  // SockJS fallback
registry.enableSimpleBroker("/topic");
registry.setApplicationDestinationPrefixes("/app");
```

### Топики

| Топик | Кто подписывается | Когда публикуется |
|-------|------------------|------------------|
| `/topic/king-of-court/{tournamentId}` | Браузер организатора, зрители | После сохранения результата матча или перехода раунда |

### WebSocketService

```java
simpMessagingTemplate.convertAndSend(
    "/topic/king-of-court/" + tournamentId,
    stateDTO
);
```

Публикует полный `KingOfCourtStateDTO` — клиент перерисовывает весь экран.

---

## 9. Docker и инфраструктура

### Сервисы docker-compose

| Сервис | Image | Порт (host) | Описание |
|--------|-------|-------------|----------|
| `padel-app` | custom build | `8080` | Spring Boot приложение |
| `postgres` | `postgres:15` | `5432` | База данных |
| `loki` | `grafana/loki:3.0.0` | `3100` | Агрегатор логов |
| `prometheus` | `prom/prometheus:2.53.0` | `9090` | Сбор метрик |
| `grafana` | `grafana/grafana:11.0.0` | `3000` | Визуализация |

Все сервисы в одной Docker-сети `padel-network` (bridge). В prod все порты привязаны к `127.0.0.1` — снаружи доступен только Nginx на 443.

### Зависимости запуска

```
postgres (healthcheck: pg_isready)
    └── padel-app (depends_on: postgres healthy)

loki
prometheus
    └── grafana (depends_on: loki, prometheus)
```

### Volumes

| Volume | Монтируется в | Содержимое |
|--------|--------------|-----------|
| `postgres_data` | `/var/lib/postgresql/data` | Данные PostgreSQL |
| `loki-data` | `/loki` | Индексы и чанки логов |
| `prometheus-data` | `/prometheus` | TSDB метрик (retention 30d) |
| `grafana-data` | `/var/lib/grafana` | Дашборды, datasources |
| `./logs` | `/app/logs` | Логи приложения (bind mount) |

### Конфигурация Prometheus scrape

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'padel-core-service'
    static_configs:
      - targets: ['padel-app:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s

  - job_name: 'crowdsec'
    static_configs:
      - targets: ['172.17.0.1:6060']   # host network для CrowdSec
    metrics_path: '/metrics'
```

---

## 10. CI/CD и деплой

### GitHub Actions

Пуш в `master` → `.github/workflows/deploy.yml`:

```
git push master
      │
      ▼
GitHub Actions Runner
      │
      ├── ./mvnw clean package -DskipTests
      │           ↓
      │       target/*.jar
      │
      ├── SCP → сервер /opt/padel-app/temp/app.jar
      │
      ├── SSH на сервере:
      │   cp app.jar app.jar.backup          ← резервная копия
      │   mv temp/app.jar app.jar
      │   docker compose build --no-cache padel-app
      │   docker compose up -d --no-deps padel-app
      │
      ├── Health check: GET /actuator/health
      │   статус = "UP"?
      │   ┌── YES → деплой завершён ✅
      │   └── NO  → rollback:
      │             cp app.jar.backup app.jar
      │             docker compose up -d --no-deps padel-app
      │
      └── Done
```

### Сервер

| Параметр | Значение |
|---------|---------|
| IP | не публикуется (приватная заметка, `.claude/SECURITY.md`) |
| Домен | `epadel.org` (инфраструктурный — SSH, Grafana, имена сервисных учёток; не публичный домен сайта, см. `APP_BASE_URL` выше) |
| ОС | Ubuntu 22.04.5 LTS |
| Docker Compose | `/opt/padel-app/docker-compose.yml` |
| Резервная копия JAR | `/opt/padel-app/app.jar.backup` |

### Локальная сборка для диагностики

```bash
# Сборка образа
docker compose build --no-cache padel-app

# Запуск полного стека
docker compose up -d

# Логи приложения
docker compose logs -f padel-app

# Статус контейнеров
docker compose ps
```

### Тесты

Тесты поднимают полный Spring-контекст и требуют реальный PostgreSQL.

В CI (GitHub Actions) поднимается service container:
```yaml
services:
  postgres:
    image: postgres:15
    env:
      POSTGRES_USER: epadel_user
      POSTGRES_PASSWORD: test
      POSTGRES_DB: player_padel_db
```

Запуск локально:
```bash
./mvnw clean verify   # требует PostgreSQL на localhost:5432
```
