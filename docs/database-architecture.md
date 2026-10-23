# Архитектура базы данных — padel-core-service

> **СУБД:** PostgreSQL 15  
> **Версия схемы:** v1.57  
> **Миграции:** Liquibase (YAML), файлы в `src/main/resources/db/changelog/versions/`  
> **ddl-auto:** `none` — схема управляется только через Liquibase

---

## Содержание

1. [Обзор доменов](#1-обзор-доменов)
2. [Схема связей (ERD)](#2-схема-связей-erd)
3. [Пользователи и авторизация](#3-пользователи-и-авторизация)
   - [player_padel_db](#player_padel_db)
   - [owners_db](#owners_db)
   - [password_reset_tokens_db](#password_reset_tokens_db)
4. [Клубы](#4-клубы)
   - [club_db](#club_db)
5. [Турниры и регистрации](#5-турниры-и-регистрации)
   - [tournaments_db](#tournaments_db)
   - [tournament_registrations_db](#tournament_registrations_db)
   - [payments_db](#payments_db)
6. [Рейтинг и матчи (bracket)](#6-рейтинг-и-матчи-bracket)
   - [matches_db](#matches_db)
   - [ranking_db](#ranking_db)
7. [King of Court](#7-king-of-court)
   - [tournament_king_of_court_db](#tournament_king_of_court_db)
   - [king_of_court_round_db](#king_of_court_round_db)
   - [king_of_court_court_db](#king_of_court_court_db)
   - [court_players_db](#court_players_db)
   - [court_teams_db](#court_teams_db)
   - [king_of_court_player_stats_db](#king_of_court_player_stats_db)
   - [king_of_court_match_result_db](#king_of_court_match_result_db)
8. [Americano](#8-americano)
   - [americano_rounds_db](#americano_rounds_db)
   - [americano_matches_db](#americano_matches_db)
   - [americano_players_db](#americano_players_db)
   - [americano_teams_db](#americano_teams_db)
8a. [Служебные таблицы](#8a-служебные-таблицы)
   - [telegram_pipeline_state_db](#telegram_pipeline_state_db)
9. [Справочник enum-значений](#9-справочник-enum-значений)
10. [Ключевые бизнес-правила и ограничения](#10-ключевые-бизнес-правила-и-ограничения)
11. [История миграций](#11-история-миграций)

---

## 1. Обзор доменов

База данных разбита на смысловые домены:

| Домен | Таблицы | Описание |
|-------|---------|----------|
| **Пользователи** | `player_padel_db`, `owners_db`, `password_reset_tokens_db` | Два типа пользователей: игроки и администраторы |
| **Клубы** | `club_db` | Площадки, где проводятся турниры |
| **Турниры** | `tournaments_db`, `tournament_registrations_db`, `payments_db` | Ядро системы: создание, регистрация, оплата |
| **Bracket / Рейтинг** | `matches_db`, `ranking_db` | Матчи плей-офф и накопленный рейтинг игроков |
| **King of Court** | 7 таблиц (`tournament_king_of_court_db`, `king_of_court_*`, `court_players_db`, `court_teams_db`) | Отдельный формат турнира с поротным перемещением |
| **Americano** | 4 таблицы `americano_*` | Форматы Americano, Team Americano и плей-офф |
| **Служебные** | `telegram_pipeline_state_db` | Выключатель Telegram-входа в конвейер |

---

## 2. Схема связей (ERD)

```
player_padel_db ──────────────────────────────────────────────────────────┐
  │                                                                        │
  │ 1:N  tournament_registrations_db  N:1  tournaments_db  N:1  club_db   │
  │        │                                                               │
  │        │ 1:N  payments_db                                              │
  │                                                                        │
  │ 1:1  ranking_db                                                        │
  │                                                                        │
  │ 1:N  password_reset_tokens_db                                          │
  │                                                                        │
  │  [King of Court]                                                       │
  │  tournaments_db ──1:1── tournament_king_of_court_db                   │
  │                              │                                         │
  │                              ├─1:N── king_of_court_round_db            │
  │                              │           │                             │
  │                              │           └─1:N── king_of_court_court_db│
  │                              │                       │                 │
  │                              │                       ├─M:N── player_padel_db (court_players_db)
  │                              │                       └─1:1── king_of_court_match_result_db
  │                              │                                   └─ winner1/2, loser1/2 → player_padel_db
  │                              └─1:N── king_of_court_player_stats_db    │
  │                                          └─ player → player_padel_db  │
  │                                                                        │
  │  [Americano]                                                           │
  │  tournaments_db ──1:N── americano_rounds_db                           │
  │                              └─1:N── americano_matches_db             │
  │                                          └─ team1/2_player1/2 → player_padel_db
  │                                                                        │
  │  tournaments_db ──1:N── americano_players_db → player_padel_db        │
  │  tournaments_db ──1:N── americano_teams_db → player1/2 → player_padel_db
  │                                                                        │
owners_db ────────────────────────────────────────────────────────────────┘
  (created_by в tournament_db, club_db — денормализованный FK на owners_db.id)
```

---

## 3. Пользователи и авторизация

### `player_padel_db`

Основная таблица зарегистрированных игроков. Реализует `UserDetails` Spring Security с ролью `ROLE_PLAYER`.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `nombre` | `VARCHAR(100)` | NOT NULL | — | Имя |
| `apellido` | `VARCHAR(100)` | NOT NULL | — | Фамилия |
| `email` | `VARCHAR(150)` | NOT NULL | — | Уникальный, используется как логин |
| `telefono` | `VARCHAR(20)` | NULL | — | Телефон. Для регистрации на турнир нужен `+54` или Telegram |
| `telegram_username` | `VARCHAR(64)` | NULL | — | Ник в Telegram (без `@`), уникальный (v1.41) |
| `telegram_chat_id` | `BIGINT` | NULL | — | Chat id из `/start` боту — для личных DM-напоминаний |
| `password_hash` | `VARCHAR(255)` | NOT NULL | — | BCrypt-хэш пароля |
| `fecha_registro` | `TIMESTAMP` | NOT NULL | now() | Дата создания аккаунта (immutable) |
| `fecha_actualizacion` | `TIMESTAMP` | NULL | — | Дата последнего обновления |
| `email_confirmado` | `BOOLEAN` | NOT NULL | `false` | Флаг подтверждения email |
| `fecha_confirmacion_email` | `TIMESTAMP` | NULL | — | Когда подтвердил email |
| `codigo_confirmacion` | `VARCHAR(50)` | NULL | — | Временный токен подтверждения, обнуляется после подтверждения |
| `activo` | `BOOLEAN` | NOT NULL | `true` | Мягкая деактивация аккаунта |
| `oauth2_user` | `BOOLEAN` | NOT NULL | `false` | `true` — вошёл через Google OAuth2 |
| `provider` | `VARCHAR(50)` | NULL | — | OAuth2-провайдер (`google`) |
| `password_changed_at` | `TIMESTAMP` | NULL | — | Момент последней смены пароля |
| `nivel_jugador` | `VARCHAR(20)` | NOT NULL | `SIN_ESPECIFICAR` | Уровень игрока (enum `Nivel`; NOT NULL с v1.44) |
| `preferred_locale` | `VARCHAR(5)` | NULL | `es` | Язык писем (`es`/`ru`/`en`) |

**Индексы / ограничения:**
- `UNIQUE (email)`
- Аккаунт считается активным (`isEnabled()`) только если `activo = true AND email_confirmado = true`

---

### `owners_db`

Администраторы и организаторы. Реализует `UserDetails` с ролью из поля `role`.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `email` | `VARCHAR(255)` | NOT NULL | — | Уникальный логин |
| `password` | `VARCHAR(255)` | NOT NULL | — | BCrypt-хэш |
| `first_name` | `VARCHAR(255)` | NOT NULL | — | Имя |
| `last_name` | `VARCHAR(255)` | NOT NULL | — | Фамилия |
| `phone` | `VARCHAR(255)` | NULL | — | Телефон |
| `role` | `VARCHAR(30)` | NOT NULL | `ORGANIZER` | Роль (enum `OwnerRole`) |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | Дата создания |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | Дата обновления |
| `last_login_at` | `TIMESTAMP` | NULL | — | Последний вход |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | Активен ли аккаунт |
| `club_id` | `BIGINT` | NULL | — | Клуб `CLUB_ADMIN` (без FK, как `tournaments_db.club_id`); доступ только к турнирам этого клуба (LFPT-375/376) |

**Индексы / ограничения:**
- `UNIQUE (email)`

---

### `password_reset_tokens_db`

Одноразовые токены для сброса пароля игроков.

| Колонка | Тип | Nullable | Описание |
|---------|-----|----------|----------|
| `id` | `BIGSERIAL` | NOT NULL | PK |
| `token` | `VARCHAR(255)` | NOT NULL | Уникальный токен сброса |
| `player_id` | `BIGINT` | NOT NULL | FK → `player_padel_db.id` |
| `expiry_date` | `TIMESTAMP` | NOT NULL | Дата истечения токена |
| `used` | `BOOLEAN` | NOT NULL | `false` если ещё не использован |
| `created_at` | `TIMESTAMP` | NOT NULL | Дата создания |
| `updated_at` | `TIMESTAMP` | NULL | Дата обновления |

**Индексы / ограничения:**
- `UNIQUE (token)`

---

## 4. Клубы

### `club_db`

Площадки для проведения турниров.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `nombre` | `VARCHAR(255)` | NOT NULL | — | Название клуба (уникальное) |
| `direccion` | `VARCHAR(500)` | NULL | — | Адрес |
| `zona_ciudad` | `VARCHAR(100)` | NULL | — | Район / город |
| `telefono_contacto` | `VARCHAR(50)` | NULL | — | Контактный телефон |
| `email_contacto` | `VARCHAR(255)` | NULL | — | Контактный email |
| `mapa_url` | `VARCHAR(500)` | NULL | — | Ссылка на Google Maps |
| `website_url` | `VARCHAR(500)` | NULL | — | Сайт клуба |
| `descripcion` | `TEXT` | NULL | — | Описание |
| `logo_url` | `VARCHAR(500)` | NULL | — | Ссылка на логотип |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | Дата создания |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | Дата обновления |
| `created_by` | `BIGINT` | NULL | — | ID владельца (owners_db), создавшего клуб |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | Мягкое удаление |

**Индексы / ограничения:**
- `UNIQUE (nombre)`

---

## 5. Турниры и регистрации

### `tournaments_db`

Центральная таблица системы. Хранит все форматы турниров.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `club_id` | `BIGINT` | NOT NULL | — | FK → `club_db.id` |
| `nombre` | `VARCHAR(255)` | NOT NULL | — | Название турнира |
| `fecha_inicio` | `DATE` | NOT NULL | — | Дата начала |
| `hora_inicio` | `TIME` | NOT NULL | — | Время начала |
| `duracion` | `VARCHAR(50)` | NULL | — | Продолжительность (свободный текст) |
| `genero_formato` | `VARCHAR(20)` | NOT NULL | — | Формат по полу: `MASCULINO` / `FEMENINO` / `MIXTO` |
| `categoria_nivel` | `VARCHAR(30)` | NULL | — | Уровень игроков (enum `Nivel`) |
| `tipo` | `VARCHAR(20)` | NOT NULL | — | Тип турнира (enum `TournamentType`) |
| `modalidad` | `VARCHAR(20)` | NOT NULL | — | Режим: `DOBLES` / `INDIVIDUAL` |
| `cupo_max` | `INTEGER` | NOT NULL | — | Максимальное количество участников |
| `precio` | `DECIMAL(10,2)` | NOT NULL | — | Взнос |
| `moneda` | `VARCHAR(10)` | NOT NULL | `ARS` | Валюта (ISO 4217, ARS по умолчанию) |
| `estado` | `VARCHAR(30)` | NOT NULL | `REGISTRO_ABIERTO` | Статус (enum `TournamentStatus`) |
| `deadline_cancelacion` | `TIMESTAMP` | NULL | — | Дедлайн отмены регистрации. Если NULL — автоматически за 24 ч до старта |
| `info_detallada` | `TEXT` | NULL | — | Подробное описание турнира |
| `contacto_organizador` | `VARCHAR(100)` | NOT NULL | — | Контакт организатора |
| `faq_url` | `VARCHAR(500)` | NULL | — | Ссылка на FAQ |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | Дата создания |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | Дата обновления |
| `created_by` | `BIGINT` | NULL | — | ID создателя (owners_db) |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | Мягкое удаление |
| `owner_id` | `BIGINT` | NULL | — | ID владельца-организатора |
| `pair_reminder_sent_at` | `TIMESTAMP` | NULL | — | Когда ушло email-напоминание за 5 ч до старта парного турнира; не NULL — повторно не шлётся (LFPT-437) |
| `start_reminder_sent_at` | `TIMESTAMP` | NULL | — | То же для индивидуальных форматов (AMERICANO, KING_OF_COURT) и Cancha Abierta (LFPT-443) |
| `mostrar_nivel` | `BOOLEAN` | NOT NULL | `false` | Показывать уровень игроков в публичном списке участников (LFPT-483) |
| `visibilidad` | `VARCHAR(30)` | NOT NULL | `PUBLICO` | Видимость (enum `TournamentVisibility`): `SOLO_POR_ENLACE` — нет в списках, доступен по ссылке `/torneo/{id}` |

---

### `tournament_registrations_db`

Регистрации игроков на турниры. Поддерживает как одиночную, так и парную регистрацию.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `player_id` | `BIGINT` | NOT NULL | — | FK → `player_padel_db.id` (главный игрок) |
| `registration_date` | `TIMESTAMP` | NOT NULL | now() | Дата регистрации |
| `status` | `VARCHAR(20)` | NOT NULL | — | Статус регистрации (enum `RegistrationStatus`) |
| `position` | `INTEGER` | NULL | — | Позиция в основном составе |
| `partner_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (партнёр-участник сайта) |
| `cancellation_date` | `TIMESTAMP` | NULL | — | Дата отмены |
| `cancellation_reason` | `VARCHAR(255)` | NULL | — | Причина отмены |
| `waitlist_position` | `INTEGER` | NULL | — | Позиция в листе ожидания |
| `notified_about_vacancy` | `BOOLEAN` | NULL | — | Получил ли уведомление об освободившемся месте |
| `invitation_expires_at` | `TIMESTAMP` | NULL | — | Дедлайн подтверждения приглашения из лейста ожидания (5 мин) |
| `invitation_attempts` | `INTEGER` | NOT NULL | `0` | Счётчик попыток приглашения (максимум 2, затем CANCELLED) |
| `waitlist_confirmation_token` | `VARCHAR(64)` | NULL | — | Одноразовый токен ссылки `/waitlist/confirm` вместо угадываемого id (issue #194), индекс |
| `updated_at` | `TIMESTAMP` | NULL | — | Дата последнего обновления |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | Мягкое удаление |
| `attended` | `BOOLEAN` | NOT NULL | `false` | Игрок явился на турнир |
| `participation_confirmed` | `BOOLEAN` | NOT NULL | `false` | Участие подтверждено организатором |
| `is_double_registration` | `BOOLEAN` | NOT NULL | `false` | Флаг парной регистрации |
| `main_player_id` | `BIGINT` | NULL | — | ID инициатора парной регистрации |
| `partner_first_name` | `VARCHAR(255)` | NULL | — | Имя незарегистрированного партнёра |
| `partner_last_name` | `VARCHAR(255)` | NULL | — | Фамилия незарегистрированного партнёра |
| `partner_phone` | `VARCHAR(255)` | NULL | — | Телефон незарегистрированного партнёра |
| `partner_email` | `VARCHAR(255)` | NULL | — | Email незарегистрированного партнёра |
| `partner_telegram` | `VARCHAR(255)` | NULL | — | Telegram-юзернейм незарегистрированного партнёра (LFPT-0687) |
| `partner_registration_token` | `VARCHAR(255)` | NULL | — | Токен подтверждения для второго игрока — легаси-путь для записей, созданных до LFPT-0687 (с неё пара без аккаунта регистрируется сразу, без ожидания подтверждения) |
| `partner_token_expiry` | `TIMESTAMP` | NULL | — | Дата истечения токена партнёра |
| `share_contacts` | `BOOLEAN` | NOT NULL | `false` | Соло-регистрация: согласие показать контакты другим ищущим пару |
| `solo_reminder_sent` | `BOOLEAN` | NOT NULL | `false` | Соло-регистрация: напоминание добавить партнёра отправлено |
| `pair_proposer_reg_id` | `BIGINT` | NULL | — | Регистрация игрока, предложившего объединиться в пару (`SOLO_SEARCH`) |
| `late_arrival` | `BOOLEAN` | NOT NULL | `false` | Опоздал к началу индивидуального турнира — штраф −10 к результату (LFPT-443) |
| `final_reminder_sent` | `BOOLEAN` | NOT NULL | `false` | Отправлено повторное напоминание за 3ч до старта (резервная заявка без партнёра) (LFPT-0668) |
| `partner_action_token` | `VARCHAR(64)` | NULL | — | Токен self-service действий из email (подтвердить/отменить) для резервной заявки без партнёра (LFPT-0668) |

**Флоу статусов регистрации:**

```
Одиночная:
  → CONFIRMED          — основной состав
  → WAITLIST           — лист ожидания
  → WAITLIST_INVITED   — получил приглашение (5 мин)
  → CANCELLED          — отменён (сам или после 2 неответов)

Парная:
  PARTNER_INVITED      — партнёр приглашён (незарегистрированный)
  PENDING_PARTNER      — ожидаем подтверждения зарегистрированного партнёра
  PAIR_REGISTERED      — оба игрока подтверждены
  → CONFIRMED          — после завершения парной регистрации

Соло на парный турнир:
  SOLO_ADD_LATER       — добавит партнёра позже (напоминание, затем SOLO_EXPIRED)
  SOLO_SEARCH          — ищет пару через сайт (предложения пары, автосведение)
  SOLO_EXPIRED         — срок добавления партнёра истёк
```

---

### `payments_db`

Платежи за регистрацию. Один платёж привязан к одной регистрации; возможно несколько попыток (повторная оплата после FAILED).

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `registration_id` | `BIGINT` | NOT NULL | — | FK → `tournament_registrations_db.id` |
| `amount` | `DECIMAL(10,2)` | NOT NULL | — | Сумма |
| `currency` | `VARCHAR(10)` | NOT NULL | `ARS` | Валюта (ISO 4217, поддерживает USDT и др.) |
| `status` | `VARCHAR(20)` | NOT NULL | `PENDING` | Статус (enum `PaymentStatus`) |
| `payment_method` | `VARCHAR(30)` | NULL | — | Способ оплаты (enum `PaymentMethod`) |
| `payment_date` | `TIMESTAMP` | NULL | — | Дата успешного платежа |
| `transaction_id` | `VARCHAR(100)` | NULL | — | ID транзакции платёжного провайдера |
| `payment_provider` | `VARCHAR(50)` | NULL | — | Провайдер (MercadoPago, Stripe, …) |
| `notes` | `TEXT` | NULL | — | Комментарии / причина возврата |
| `created_by` | `BIGINT` | NULL | — | Кто создал/подтвердил платёж |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | Дата создания записи |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | Дата обновления |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | Мягкое удаление |

---

## 6. Рейтинг и матчи (bracket)

### `matches_db`

Матчи для bracket-форматов (плей-офф, одиночный/парный турнир).

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `ronda` | `INTEGER` | NOT NULL | — | Номер раунда (1 = первый раунд, 4 = финал) |
| `partido_numero` | `INTEGER` | NOT NULL | — | Номер матча в раунде |
| `tipo` | `VARCHAR(20)` | NOT NULL | — | `INDIVIDUAL` / `PAREJAS` |
| `player1_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `player2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `player3_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (пары: 2-й игрок команды 1) |
| `player4_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (пары: 2-й игрок команды 2) |
| `equipo1_nombre` | `VARCHAR(255)` | NULL | — | Отображаемое название команды 1 |
| `equipo2_nombre` | `VARCHAR(255)` | NULL | — | Отображаемое название команды 2 |
| `set1_equipo1` / `set1_equipo2` | `INTEGER` | NULL | — | Счёт первого сета |
| `set2_equipo1` / `set2_equipo2` | `INTEGER` | NULL | — | Счёт второго сета |
| `set3_equipo1` / `set3_equipo2` | `INTEGER` | NULL | — | Счёт третьего сета |
| `super_set_equipo1` / `super_set_equipo2` | `INTEGER` | NULL | — | Супер тай-брейк |
| `ganador_id` | `BIGINT` | NULL | — | ID победителя |
| `perdedor_id` | `BIGINT` | NULL | — | ID проигравшего |
| `puntos_equipo1` / `puntos_equipo2` | `INTEGER` | NULL | — | Итоговые очки команды |
| `estado` | `VARCHAR(20)` | NOT NULL | `PROGRAMADO` | Статус матча (enum `MatchStatus`) |
| `fecha_programada` | `TIMESTAMP` | NULL | — | Запланированное время |
| `fecha_inicio` | `TIMESTAMP` | NULL | — | Фактическое начало |
| `fecha_fin` | `TIMESTAMP` | NULL | — | Фактическое завершение |
| `cancha` | `VARCHAR(50)` | NULL | — | Номер / название корта |
| `arbitro` | `VARCHAR(255)` | NULL | — | Судья |
| `observaciones` | `TEXT` | NULL | — | Наблюдения |
| `created_by` | `BIGINT` | NULL | — | Создатель записи |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `is_active` | `BOOLEAN` | NOT NULL | `true` | — |

---

### `ranking_db`

Накопленный рейтинг игрока по всем турнирам. Один ряд на игрока (`1:1` с `player_padel_db`).

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `player_id` | `BIGINT` | NOT NULL | — | FK → `player_padel_db.id` (уникальный) |
| `puntos` | `INTEGER` | NOT NULL | — | Накопленные очки |
| `torneos_jugados` | `INTEGER` | NOT NULL | — | Количество сыгранных турниров |
| `torneos_ganados` | `INTEGER` | NOT NULL | — | Количество выигранных турниров |
| `partidos_ganados` | `INTEGER` | NOT NULL | — | Победы в матчах |
| `partidos_perdidos` | `INTEGER` | NOT NULL | — | Поражения в матчах |
| `sets_ganados` | `INTEGER` | NOT NULL | — | Выигранных сетов |
| `sets_perdidos` | `INTEGER` | NOT NULL | — | Проигранных сетов |
| `nivel_actual` | `VARCHAR(10)` | NULL | — | Текущий уровень игрока |
| `posicion_actual` | `INTEGER` | NULL | — | Текущая позиция в рейтинге |
| `posicion_anterior` | `INTEGER` | NULL | — | Позиция в предыдущем расчёте |
| `mejor_posicion` | `INTEGER` | NULL | — | Лучшая позиция за всё время; NULL — ещё не было (DEFAULT 0 убран в v1.54) |
| `rachas_actual` | `INTEGER` | NULL | — | Текущая серия побед |
| `rachas_maxima` | `INTEGER` | NULL | — | Максимальная серия побед |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `last_calculated_at` | `TIMESTAMP` | NULL | — | Когда пересчитывался рейтинг |

**Индексы / ограничения:**
- `UNIQUE (player_id)`

---

## 7. King of Court

Формат, где игроки поротно меняются между кортами по результатам. Real-time обновления через WebSocket `/topic/king-of-court/{tournamentId}`.

```
tournament_king_of_court_db
  └── 1:N king_of_court_round_db
              └── 1:N king_of_court_court_db
                          ├── M:N player_padel_db  (через court_players_db)
                          ├── 1:N court_teams_db → player1/2 → player_padel_db
                          └── 1:1 king_of_court_match_result_db
  └── 1:N king_of_court_player_stats_db
```

---

### `tournament_king_of_court_db`

Конфигурация King of Court турнира. Один к одному с `tournaments_db`.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` (уникальный) |
| `max_courts` | `INTEGER` | NOT NULL | `5` | Максимальное количество кортов |
| `calibration_rounds` | `INTEGER` | NOT NULL | `3` | Калибровочные раунды (без подъёма/спуска) |
| `current_round` | `INTEGER` | NOT NULL | `0` | Текущий номер раунда |
| `is_active` | `BOOLEAN` | NOT NULL | `false` | Турнир запущен |
| `is_finished` | `BOOLEAN` | NOT NULL | `false` | Турнир завершён |
| `youtube_link` | `VARCHAR(500)` | NULL | — | Ссылка на трансляцию |
| `started_at` | `TIMESTAMP` | NULL | — | Время старта |
| `finished_at` | `TIMESTAMP` | NULL | — | Время завершения |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |

---

### `king_of_court_round_db`

Один раунд (все корты играют одновременно).

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_king_id` | `BIGINT` | NOT NULL | — | FK → `tournament_king_of_court_db.id` |
| `round_number` | `INTEGER` | NOT NULL | — | Порядковый номер раунда |
| `is_completed` | `BOOLEAN` | NOT NULL | `false` | Все корты сыграли |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `completed_at` | `TIMESTAMP` | NULL | — | Время завершения раунда |

---

### `king_of_court_court_db`

Конкретный корт в конкретном раунде.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `round_id` | `BIGINT` | NOT NULL | — | FK → `king_of_court_round_db.id` |
| `court_number` | `INTEGER` | NOT NULL | — | Номер корта (от 1 до max_courts) |

---

### `court_players_db`

Связующая таблица M:N: какие игроки играли на каком корте.

| Колонка | Тип | Описание |
|---------|-----|----------|
| `court_id` | `BIGINT` | FK → `king_of_court_court_db.id` |
| `player_id` | `BIGINT` | FK → `player_padel_db.id` |

---

### `court_teams_db`

Команда (пара игроков) на конкретном корте в раунде. Сущность `CourtTeam`.

| Колонка | Тип | Nullable | Описание |
|---------|-----|----------|----------|
| `id` | `BIGSERIAL` | NOT NULL | PK |
| `court_id` | `BIGINT` | NOT NULL | FK → `king_of_court_court_db.id` |
| `team_number` | `INTEGER` | NOT NULL | Номер команды на корте (1 или 2) |
| `player1_id` | `BIGINT` | NULL | FK → `player_padel_db.id` |
| `player2_id` | `BIGINT` | NULL | FK → `player_padel_db.id` |

---

### `king_of_court_player_stats_db`

Накопленная статистика игрока в конкретном King of Court турнире.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_king_id` | `BIGINT` | NOT NULL | — | FK → `tournament_king_of_court_db.id` |
| `player_id` | `BIGINT` | NOT NULL | — | FK → `player_padel_db.id` |
| `total_points` | `INTEGER` | NOT NULL | `0` | Очки за победы |
| `bonus_points` | `INTEGER` | NOT NULL | `0` | Бонусные очки |
| `games_played` | `INTEGER` | NOT NULL | `0` | Сыгранных матчей |
| `wins` | `INTEGER` | NOT NULL | `0` | Победы |
| `losses` | `INTEGER` | NOT NULL | `0` | Поражения |

---

### `king_of_court_match_result_db`

Результат матча на одном корте в одном раунде. Один к одному с `king_of_court_court_db`.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `court_id` | `BIGINT` | NOT NULL | — | FK → `king_of_court_court_db.id` (уникальный) |
| `winner1_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `winner2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `loser1_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `loser2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `winners_score` | `INTEGER` | NOT NULL | — | Счёт победителей |
| `losers_score` | `INTEGER` | NOT NULL | — | Счёт проигравших |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |

---

## 8. Americano

Поддерживает три подформата через поля `is_doubles` (раунды) и `tipo` (турнир):

| Подформат | `TournamentType` | Основная сущность |
|-----------|-----------------|-------------------|
| Americano (индивидуальный) | `AMERICANO` | `AmericanoPlayer` |
| Americano (парный) | `AMERICANO` | `AmericanoPlayer` |
| Team Americano | `AMERICANO_TEAMS` | `AmericanoTeam` |

```
tournaments_db
  ├── 1:N americano_rounds_db
  │           └── 1:N americano_matches_db
  ├── 1:N americano_players_db   (для AMERICANO)
  └── 1:N americano_teams_db     (для AMERICANO_TEAMS)
```

---

### `americano_rounds_db`

Раунд Americano турнира. Каждый раунд — все матчи идут параллельно.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `round_number` | `INTEGER` | NOT NULL | — | Номер раунда |
| `status` | `VARCHAR(20)` | NOT NULL | `PENDING` | `PENDING` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED` |
| `started_at` | `TIMESTAMP` | NULL | — | Время начала |
| `completed_at` | `TIMESTAMP` | NULL | — | Время завершения |
| `points_per_match` | `INTEGER` | NOT NULL | — | Общий пул очков за матч (например, 32) |
| `is_doubles` | `BOOLEAN` | NOT NULL | — | `true` — парный раунд |
| `courts` | `INTEGER` | NULL | — | Количество активных кортов в раунде |
| `note` | `VARCHAR(255)` | NULL | — | Заметка |
| `phase` | `VARCHAR(20)` | NULL | — | Фаза: `QUALIFICATION` / `PLAYOFF` |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |

---

### `americano_matches_db`

Один матч в раунде Americano.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `round_id` | `BIGINT` | NOT NULL | — | FK → `americano_rounds_db.id` |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `match_number` | `INTEGER` | NOT NULL | — | Номер матча в раунде |
| `team1_player1_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `team1_player2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (парный) |
| `team2_player1_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` |
| `team2_player2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (парный) |
| `team1_score` | `INTEGER` | NULL | — | Очки команды 1 |
| `team2_score` | `INTEGER` | NULL | — | Очки команды 2 |
| `team1_games` | `INTEGER` | NULL | — | Геймы команды 1 (Team Americano сетовый формат) |
| `team2_games` | `INTEGER` | NULL | — | Геймы команды 2 |
| `is_doubles` | `BOOLEAN` | NOT NULL | — | Парный матч |
| `priority` | `BOOLEAN` | NOT NULL | `false` | Play-in (1/8 финала) — запускать как можно раньше |
| `next_match_id` | `BIGINT` | NULL | — | Play-in: матч четвертьфинала, куда сразу проходит победитель |
| `next_match_slot` | `INTEGER` | NULL | — | Play-in: слот (1/2) в `next_match_id` |
| `status` | `VARCHAR(20)` | NOT NULL | `PENDING` | Статус матча |
| `started_at` | `TIMESTAMP` | NULL | — | — |
| `completed_at` | `TIMESTAMP` | NULL | — | — |
| `court_number` | `INTEGER` | NULL | — | Номер корта |
| `note` | `VARCHAR(255)` | NULL | — | Заметка |
| `team1_id` | `BIGINT` | NULL | — | FK → `americano_teams_db.id` (Team Americano) |
| `team2_id` | `BIGINT` | NULL | — | FK → `americano_teams_db.id` |
| `playoff_stage` | `VARCHAR(20)` | NULL | — | Стадия плей-офф: `QUARTER_FINAL`, `SEMI_FINAL`, `FINAL` |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |

---

### `americano_players_db`

Участник Americano турнира с накопленной статистикой.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `player_id` | `BIGINT` | NOT NULL | — | FK → `player_padel_db.id` |
| `initial_position` | `INTEGER` | NOT NULL | — | Начальная позиция (посев) |
| `current_position` | `INTEGER` | NULL | — | Текущая позиция в рейтинге |
| `status` | `VARCHAR(20)` | NOT NULL | `ACTIVE` | `ACTIVE` / `DROPPED_OUT` |
| `total_score` | `INTEGER` | NOT NULL | `0` | Сумма набранных очков |
| `matches_played` | `INTEGER` | NOT NULL | `0` | — |
| `matches_won` | `INTEGER` | NOT NULL | `0` | — |
| `matches_lost` | `INTEGER` | NOT NULL | `0` | — |
| `matches_drawn` | `INTEGER` | NOT NULL | `0` | — |
| `points_scored` | `INTEGER` | NOT NULL | `0` | Набранных очков (детально) |
| `points_conceded` | `INTEGER` | NOT NULL | `0` | Пропущенных очков |
| `bye_count` | `INTEGER` | NOT NULL | `0` | Пропущенных раундов |
| `last_bye_round` | `INTEGER` | NULL | — | Последний раунд с bye (для детекции подряд) |
| `unique_partners` | `INTEGER` | NOT NULL | `0` | Уникальных партнёров |
| `unique_opponents` | `INTEGER` | NOT NULL | `0` | Уникальных соперников |
| `dropped_out_at` | `TIMESTAMP` | NULL | — | Когда выбыл |
| `dropped_out_reason` | `VARCHAR(255)` | NULL | — | Причина выбывания |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |

---

### `americano_teams_db`

Фиксированная пара игроков в Team Americano турнире. Статистика накапливается на уровне команды.

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGSERIAL` | NOT NULL | auto | PK |
| `tournament_id` | `BIGINT` | NOT NULL | — | FK → `tournaments_db.id` |
| `player1_id` | `BIGINT` | NOT NULL | — | FK → `player_padel_db.id` (инициатор) |
| `player2_id` | `BIGINT` | NULL | — | FK → `player_padel_db.id` (партнёр на сайте) |
| `player2_name` | `VARCHAR(200)` | NULL | — | Имя партнёра вне сайта |
| `player2_phone` | `VARCHAR(30)` | NULL | — | Телефон партнёра вне сайта |
| `team_number` | `INTEGER` | NOT NULL | — | Порядковый номер команды |
| `current_position` | `INTEGER` | NULL | — | Текущая позиция |
| `status` | `VARCHAR(20)` | NOT NULL | `ACTIVE` | `ACTIVE` / `DROPPED_OUT` |
| `total_score` | `INTEGER` | NOT NULL | `0` | Сумма очков |
| `matches_played` | `INTEGER` | NOT NULL | `0` | — |
| `matches_won` | `INTEGER` | NOT NULL | `0` | — |
| `matches_lost` | `INTEGER` | NOT NULL | `0` | — |
| `matches_drawn` | `INTEGER` | NOT NULL | `0` | — |
| `points_scored` | `INTEGER` | NOT NULL | `0` | — |
| `points_conceded` | `INTEGER` | NOT NULL | `0` | — |
| `sets_won` | `INTEGER` | NOT NULL | `0` | Для сетового формата |
| `sets_lost` | `INTEGER` | NOT NULL | `0` | — |
| `games_won` | `INTEGER` | NOT NULL | `0` | Геймов выиграно |
| `games_lost` | `INTEGER` | NOT NULL | `0` | — |
| `registration_source` | `VARCHAR(20)` | NULL | — | `ONLINE` / `ADMIN` |
| `has_paid` | `BOOLEAN` | NOT NULL | `false` | — |
| `attended` | `BOOLEAN` | NOT NULL | `false` | — |
| `admin_comment` | `VARCHAR(500)` | NULL | — | Заметка администратора |
| `created_at` | `TIMESTAMP` | NOT NULL | now() | — |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | — |

**Индексы / ограничения:**
- `UNIQUE (tournament_id, player1_id)` — игрок может быть в одной команде на турнир

---

## 8a. Служебные таблицы

### `telegram_pipeline_state_db`

Выключатель Telegram-входа в SDD-конвейер (одна строка, admin-страница `/admin/telegram-pipeline`).

| Колонка | Тип | Nullable | По умолчанию | Описание |
|---------|-----|----------|--------------|----------|
| `id` | `BIGINT` | NOT NULL | auto | PK |
| `paused` | `BOOLEAN` | NOT NULL | `false` | Конвейер на паузе — relay не передаёт сообщения |
| `updated_at` | `TIMESTAMP` | NOT NULL | now() | Момент последнего переключения |
| `updated_by` | `VARCHAR(255)` | NULL | — | Кто переключил (email владельца) |

---

## 9. Справочник enum-значений

### `TournamentType` — тип турнира
| Значение | Описание |
|----------|----------|
| `KING_OF_COURT` | King of Court (поротное движение по кортам) |
| `AMERICANO` | Americano (одиночный или парный, меняются партнёры) |
| `AMERICANO_TEAMS` | Team Americano (фиксированные пары, квалификация + плей-офф) |
| `CANCHA_ABIERTA` | Открытая сетка (bracket) |
| `PADEL_CLINIC` | Клиника (тренировка), без турнирного движка |

### `TournamentStatus` — статус турнира
| Значение | Описание |
|----------|----------|
| `BORRADOR` | Черновик |
| `PUBLICADO` | Опубликован, регистрация ещё не открыта |
| `REGISTRO_ABIERTO` | Открыта регистрация |
| `CERRADO` | Регистрация закрыта, турнир идёт |
| `FINALIZADO` | Завершён |
| `CANCELADO` | Отменён |

### `RegistrationStatus` — статус регистрации
| Значение | Описание |
|----------|----------|
| `CONFIRMED` | Подтверждён, основной состав |
| `WAITLIST` | Лист ожидания |
| `WAITLIST_INVITED` | Получил приглашение (5 мин на подтверждение) |
| `CANCELLED` | Отменён |
| `PARTICIPATED` | Участвовал (архивный) |
| `PENDING_PARTNER` | Парная: ожидание зарегистрированного партнёра |
| `PARTNER_INVITED` | Парная: незарегистрированный партнёр приглашён |
| `PAIR_REGISTERED` | Парная: оба игрока подтверждены |
| `SOLO_ADD_LATER` | Соло на парный турнир: добавит партнёра позже |
| `SOLO_SEARCH` | Соло на парный турнир: ищет пару через сайт |
| `SOLO_EXPIRED` | Соло на парный турнир: срок добавления партнёра истёк |

### `Modalidad` — режим
| Значение | Описание |
|----------|----------|
| `DOBLES` | Парный |
| `INDIVIDUAL` | Одиночный |

### `GenderFormat` — гендерный формат
| Значение | Описание |
|----------|----------|
| `MASCULINO` | Мужской |
| `FEMENINO` | Женский |
| `MIXTO` | Смешанный |

### `Nivel` — уровень игроков
Значения с привязкой к гендерному формату турнира (`Nivel.genderFormat`):

- мужские (`MASCULINO`): `C9`, `C8`, `C7`, `C6`, `C5`, `C4`, `C9_C8`, `C8_C7`, `C7_C6`, `C6_C5`, `C5_C4`
- женские (`FEMENINO`): `D9`, `D8`, `D7`, `D6`, `D5`, `D4`, `D9_D8`, `D7_D8`, `D7_D6`, `D6_D5`, `D5_D4`
- микст, сумма категорий пары (`MIXTO`): `SUMA_16`, `SUMA_15`, `SUMA_14`, `SUMA_13`, `SUMA_12`, `SUMA_11`, `SUMA_10`
- любой формат: `TODOS`, `PRINCIPIANTES`, `SIN_ESPECIFICAR` (дефолт `player_padel_db.nivel_jugador`)

### `TournamentVisibility` — видимость турнира
`PUBLICO` (в списках и календаре), `SOLO_POR_ENLACE` (только по прямой ссылке)

### `MatchStatus` — статус матча (bracket)
| Значение | Описание |
|----------|----------|
| `PROGRAMADO` | Запланирован |
| `EN_CURSO` | Идёт |
| `FINALIZADO` | Завершён |
| `SUSPENDIDO` | Приостановлен |
| `CANCELADO` | Отменён |
| `WALKOVER` | Победа за неявку соперника |

### `PaymentStatus` — статус платежа
| Значение | Описание |
|----------|----------|
| `PENDING` | Ожидает |
| `PROCESSING` | Обрабатывается |
| `PAID` | Оплачено |
| `FAILED` | Ошибка |
| `REFUNDED` | Возврат |
| `CANCELLED` | Отменён |

### `OwnerRole` — роль администратора
| Значение | Права |
|----------|-------|
| `SUPER_ADMIN` | Полные права |
| `ORGANIZER` | Управление своими турнирами |
| `ADMIN` | Администратор admin-панели (v1.38, заводится вручную через SQL); в `@PreAuthorize` идёт наравне с `ORGANIZER` |
| `CLUB_ADMIN` | Клубный администратор: только турниры/регистрации/оплаты/матчи клуба `owners_db.club_id` (LFPT-375/376) |

---

## 10. Ключевые бизнес-правила и ограничения

### Регистрация на турнир
- Игрок может зарегистрироваться только если `PlayerPadel.hasValidContact() == true`: нужен телефон `+54` **или** Telegram username.
- Если мест нет — попадает в `WAITLIST`.
- Лист ожидания: при освобождении места cron (`0 */10 * * * *`) приглашает первого в очереди (`WAITLIST_INVITED`). На подтверждение — 5 минут.
- Максимум 2 попытки приглашения (`invitation_attempts`). После второго отказа — `CANCELLED`.

### Парная регистрация
- `partner_id` — если оба игрока зарегистрированы на сайте.
- `partner_first_name/last_name/phone/email` — если партнёр вне сайта.
- Токен (`partner_registration_token`) действует до `partner_token_expiry`.

### Мягкое удаление
Все основные сущности имеют `is_active = true`. Физическое удаление применяется только через специальные "hard delete" endpoint-ы (только для клубов). Везде используйте фильтр `is_active = true` при запросах.

### Денормализованные FK
`tournaments_db.club_id`, `tournaments_db.created_by`, `tournaments_db.owner_id`, `club_db.created_by`, `matches_db.tournament_id` — хранятся как `BIGINT` без JPA `@ManyToOne` (намеренно, для упрощения кэширования и избежания N+1).

---

## 11. История миграций

| Версия | Файл | Что изменено |
|--------|------|-------------|
| v1.0 | `v1.0-create-player-table` | Создана таблица `player_padel_db` |
| v1.1 | `v1.1-create-owner-table` | Создана таблица `owners_db` |
| v1.2 | `v1.2-create-tournament-table` | Создана таблица `tournaments_db` |
| v1.3 | `v1.3-create-club-table` | Создана таблица `club_db` |
| v1.4 | `v1.4-create-tournament-registration-table` | Создана `tournament_registrations_db` |
| v1.5 | `v1.5-create-ranking-table` | Создана `ranking_db` |
| v1.6 | `v1.6-create-matches-table` | Создана `matches_db` |
| v1.7 | `v1.7-create-king-of-court-tables` | Созданы все таблицы King of Court |
| v1.8 | `v1.8-alter-tournament-categoria-nivel` | Изменён тип поля категории уровня |
| v1.9 | `v1.9-add-waitlist-fields` | Добавлены поля листа ожидания в регистрации |
| v1.10 | `v1.10-add-nivel-tournament` | Добавлен уровень к турниру |
| v1.11 | `v1.11-add-last-calculated-to-ranking` | `last_calculated_at` в рейтинге |
| v1.12 | `v1.12-add-mejor-posicion-to-ranking` | `mejor_posicion` в рейтинге |
| v1.13 | `v1.13-add-missing-ranking-columns` | Дополнительные столбцы рейтинга |
| v1.14 | `v1.14-update-owner-credential` | Обновлена модель credentials у owner |
| v1.15 | `v1.15-add-modalidad-to-tournament` | Добавлено поле `modalidad` к турниру |
| v1.16 | `v1.16-update-categoria-nivel-constraint` | Обновлён CHECK constraint уровня |
| v1.17 | `v1.17-create-payments-table` | Создана `payments_db` |
| v1.18 | `v1.18-add-attended-to-registration` | Поле `attended` в регистрациях |
| v1.19 | `v1.19-create-password-reset-token` | Создана `password_reset_tokens_db` |
| v1.20 | `v1.20-add-oauth2-fields-to-player` | Поля `oauth2_user`, `provider` у игрока |
| v1.21 | `v1.21-add-double-tournament-fields` | Поля парной регистрации |
| v1.22 | `v1.22-update-nivel-constraint` | Расширен список значений уровня |
| v1.23 | `v1.23-create-americano-tables` | Созданы `americano_rounds_db`, `americano_matches_db`, `americano_players_db` |
| v1.24 | `v1.24-add-contact-fields-to-player` | Поле `telegram_username` у игрока |
| v1.25 | `v1.25-add-nivel-c4-suma14` | Добавлены уровни `C4`, `SUMA_14` |
| v1.26 | `v1.26-create-americano-teams` | Создана `americano_teams_db` |
| v1.27 | `v1.27-americano-teams-playoff` | Поля плей-офф для Team Americano |
| v1.28 | `v1.28-add-participation-confirmed-to-registration` | Поле `participation_confirmed` |
| v1.29 | `v1.29-drop-old-categoria-nivel-constraint` | Удалён устаревший CHECK |
| v1.30 | `v1.30-add-invitation-attempts` | Счётчик попыток приглашения |
| v1.31 | `v1.31-extend-currency-field` | Расширено поле `currency` до `VARCHAR(10)` |
| v1.32 | `v1.32-add-nivel-todos` | Добавлен уровень `TODOS` |
| v1.33 | `v1.33-fix-confirmed-null-positions` | Фикс NULL-позиций у подтверждённых регистраций |
| v1.34 | `v1.34-add-telegram-chat-id` | Поле `telegram_chat_id` у игрока (для Telegram-бота) |
| v1.35 | `v1.35-add-nivel-jugador-to-player` | Уровень игрока (`nivel_jugador`) хранится в профиле, не только в турнире |
| v1.36 | `v1.36-add-solo-registration-fields` | Поля одиночной регистрации без пары в `tournament_registrations_db` |
| v1.37 | `v1.37-add-pair-proposal-field` | Поле предложения пары в `tournament_registrations_db` |
| v1.38 | `v1.38-add-admin-role-users` | Роль admin для пользователей |
| v1.39 | `v1.39-add-preferred-locale` | Поле `preferred_locale` у игрока (для i18n email) |
| v1.40 | `v1.40-add-waitlist-confirmation-token` | Токен подтверждения приглашения из листа ожидания |
| v1.41 | `v1.41-unique-index-telegram-username` | Уникальный индекс на `telegram_username` |
| v1.42 | `v1.42-add-playin-match-fields` | Play-in в `americano_matches_db`: `priority`, `next_match_id`, `next_match_slot` |
| v1.43 | `v1.43-add-nivel-d9` | Значение `D9` в `Nivel` (данные) |
| v1.44 | `v1.44-nivel-jugador-not-null` | `nivel_jugador` NOT NULL, DEFAULT `SIN_ESPECIFICAR` |
| v1.45 | `v1.45-add-nivel-suma16` | Значение `SUMA_16` в `Nivel` (данные) |
| v1.46 | `v1.46-create-telegram-pipeline-state` | Таблица `telegram_pipeline_state_db` |
| v1.47 | `v1.47-add-nivel-categories` | Новые категории `Nivel` (данные) |
| v1.48 | `v1.48-add-club-id-to-owner` | `owners_db.club_id` для `CLUB_ADMIN` |
| v1.49 | `v1.49-add-pair-reminder-sent-at-to-tournament` | `tournaments_db.pair_reminder_sent_at` |
| v1.50 | `v1.50-add-start-reminder-sent-at-to-tournament` | `tournaments_db.start_reminder_sent_at` |
| v1.51 | `v1.51-add-late-arrival-to-registration` | `tournament_registrations_db.late_arrival` |
| v1.52 | `v1.52-add-mostrar-nivel-to-tournament` | `tournaments_db.mostrar_nivel` |
| v1.53 | `v1.53-add-visibilidad-to-tournament` | `tournaments_db.visibilidad` |
| v1.54 | `v1.54-ranking-mejor-posicion-drop-default` | `ranking_db.mejor_posicion`: убран DEFAULT 0, нули → NULL |
| v1.55 | `v1.55-add-partner-reserve-fields` | `tournament_registrations_db.final_reminder_sent`, `partner_action_token` |
| v1.56 | `v1.56-add-partner-telegram` | `tournament_registrations_db.partner_telegram` |
| v1.57 | `v1.57-unaccent-extension` | Расширение Postgres `unaccent` (диакритика в поиске партнёра, LFPT-0720) — не таблица/колонка |
