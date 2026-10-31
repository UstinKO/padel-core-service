# Доменная карта — padel-core-service

> Документ описывает логические домены монолита, их ответственность, зависимости и все эндпоинты.  
> Цель — подготовка к декомпозиции на микросервисы.

---

## Содержание

1. [Карта доменов и зависимостей](#1-карта-доменов-и-зависимостей)
2. [Домен: Identity (Идентификация)](#2-домен-identity)
3. [Домен: Clubs (Клубы)](#3-домен-clubs)
4. [Домен: Tournaments (Турниры)](#4-домен-tournaments)
5. [Домен: Registrations (Регистрации)](#5-домен-registrations)
6. [Домен: Payments (Платежи)](#6-домен-payments)
7. [Домен: King of Court (Формат турнира)](#7-домен-king-of-court)
8. [Домен: Americano (Формат турнира)](#8-домен-americano)
9. [Домен: Ranking (Рейтинг)](#9-домен-ranking)
10. [Домен: Notifications (Уведомления)](#10-домен-notifications)
11. [Аналитика связей для декомпозиции](#11-аналитика-связей-для-декомпозиции)

---

## 1. Карта доменов и зависимостей

```
┌────────────────────────────────────────────────────────────────────────┐
│                          padel-core-service                            │
│                                                                        │
│  ┌───────────────┐     ┌───────────────┐     ┌───────────────────────┐│
│  │   Identity    │◄────│ Registrations │────►│     Tournaments       ││
│  │ (Players +    │     │               │     │ (CRUD + статусы)      ││
│  │  Owners +     │     │ waitlist,      │     │                       ││
│  │  Auth + OAuth2│     │ pair,cancel)  │     │                       ││
│  └───────────────┘     └───────┬───────┘     └──────────┬────────────┘│
│          ▲                     │                        │             │
│          │                     ▼                        ▼             │
│          │              ┌──────────────┐    ┌───────────────────────┐ │
│          │              │   Payments   │    │   Clubs               │ │
│          │              │              │    │                       │ │
│          │              └──────────────┘    └───────────────────────┘ │
│          │                                                             │
│          │         ┌──────────────┐  ┌──────────────┐                │
│          └─────────│ King of Court│  │  Americano   │                │
│                    │              │  │              │                 │
│                    └──────────────┘  └──────────────┘                │
│                                                                        │
│  ┌──────────────┐     ┌─────────────────────────────────────────────┐ │
│  │   Ranking    │     │         Notifications (Email)               │ │
│  │              │     │  (внутренний, вызывается другими доменами)  │ │
│  └──────────────┘     └─────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

### Легенда зависимостей

| Домен | Читает из | Пишет в | Вызывает |
|-------|-----------|---------|----------|
| **Identity** | — | `player_padel_db`, `owners_db`, `password_reset_tokens_db` | Notifications |
| **Clubs** | Identity (created_by) | `club_db` | — |
| **Tournaments** | Identity, Clubs | `tournaments_db` | Notifications |
| **Registrations** | Identity, Tournaments | `tournament_registrations_db` | Notifications, Payments |
| **Payments** | Registrations | `payments_db` | — |
| **King of Court** | Identity, Tournaments | `tournament_king_of_court_db`, 5 KoC-таблиц | — |
| **Americano** | Identity, Tournaments | 4 Americano-таблицы | — |
| **Ranking** | Identity, Tournaments | `ranking_db` | — |
| **Notifications** | Identity | — | SMTP |

---

## 2. Домен: Identity

**Ответственность:** регистрация и аутентификация двух типов пользователей (игроки и администраторы), выдача JWT-токенов, OAuth2 через Google, сброс пароля, управление профилем.

**Таблицы:** `player_padel_db`, `owners_db`, `password_reset_tokens_db`

**Сервисы:** `PlayerService`, `OwnerService`, `PasswordResetService`, `JwtService`, `CompositeUserDetailsService`, `CustomOAuth2UserService`, `RateLimitFilter`

**Важные особенности:**
- Два независимых типа пользователей (`PlayerPadel` и `Owner`) — оба реализуют `UserDetails`
- `CompositeUserDetailsService` пробует `OwnerUserService` первым, затем `PlayerUserDetailsService`
- Защита регистрации: reCAPTCHA v3 → honeypot → disposable email → IP rate limit (Bucket4j + Guava)
- Пароль хэшируется через BCrypt; телефон должен начинаться с `+54` (Аргентина)
- `hasValidContact()` — обязательная проверка перед регистрацией на турнир (телефон `+54` или Telegram)

---

### Эндпоинты Identity

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/` | HTML | Главная страница |
| `GET` | `/login` | HTML | Форма входа (Spring Security form login) |
| `GET` | `/players/registro` | HTML | Форма регистрации нового игрока |
| `GET` | `/players/confirmar-email` | HTML | Страница ожидания подтверждения email |

#### Регистрация и подтверждение

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/players/registro` | Form → redirect | — | Регистрация игрока через форму. Проверяет honeypot, reCAPTCHA, валидацию. После успеха — авто-логин и редирект на главную. |
| `POST` | `/players/api/registro` | JSON | — | То же, но REST API без reCAPTCHA (для внешних клиентов / тестов). |
| `GET` | `/players/api/confirmar-email` | JSON | — | Подтверждение email по токену (`?codigo=...`). Активирует аккаунт. |

#### Auth API (JWT)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/api/auth/login` | JSON | — | Аутентификация игрока по email/password. Возвращает `accessToken` (24ч) и `refreshToken` (7д), данные профиля. |
| `POST` | `/api/auth/refresh` | JSON | — | Обновление access-токена по refresh-токену. Выдаёт новую пару токенов. |
| `GET` | `/api/auth/me` | JSON | JWT | Возвращает данные текущего авторизованного игрока из SecurityContext. |

#### Профиль и пароль (ROLE_PLAYER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/perfil` | HTML | PLAYER | Страница профиля: имя, телефон, Telegram, смена пароля. |
| `POST` | `/perfil/actualizar` | Form → redirect | PLAYER | Обновление профиля: имя, фамилия, телефон (+54), Telegram, смена пароля. Валидирует текущий пароль перед сменой. |
| `PATCH` | `/api/players/me/contact` | JSON | PLAYER | Быстрое обновление контактов (телефон / Telegram). Вызывается из модального окна перед регистрацией на турнир. |
| `GET` | `/api/players/me/contact-status` | JSON | PLAYER | Проверяет, есть ли у игрока валидные контактные данные. Возвращает `hasContact`, `hasPhone`, `hasTelegram`. |

#### Сброс пароля

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/recuperar-password` | redirect | — | Принимает токен сброса, проверяет его валидность и редиректит на главную с `?token=...` (JS откроет модальное окно). |
| `POST` | `/recuperar-password/solicitar` | JSON | — | Запрос письма для сброса пароля по email. Отвечает успехом даже если email не найден (защита от перебора). |
| `POST` | `/recuperar-password/confirmar` | JSON | — | Применение нового пароля по токену. Токен становится недействительным после использования. |

#### Список игроков (API)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/players/api` | JSON | — | Список всех игроков (без пагинации). |
| `GET` | `/players/api/{id}` | JSON | — | Игрок по ID. |

#### Admin: управление владельцами (ORGANIZER / SUPER_ADMIN)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/api/owners/super-admin` | JSON | ADMIN | Данные суперадмина (первый SUPER_ADMIN в системе). |
| `GET` | `/api/owners/{id}` | JSON | ADMIN | Владелец по ID. |
| `PUT` | `/api/owners/{id}` | JSON | ADMIN | Обновление данных владельца. |

#### Admin: управление игроками (SUPER_ADMIN / ORGANIZER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/admin/players` | HTML | ADMIN | Список всех игроков с фильтрацией. |
| `GET` | `/admin/players/{id}` | HTML | ADMIN | Детальный профиль игрока: данные, регистрации, история. |
| `POST` | `/admin/players/{id}/nivel` | Form → redirect / JSON | SUPER_ADMIN | Задать уровень игрока (`Nivel`; «пусто» = `SIN_ESPECIFICAR`). Со списка игроков — fetch с `X-Requested-With` (автосейв без перезагрузки, LFPT-218). |
| `POST` | `/admin/players/{id}/guest-credentials` | JSON | SUPER_ADMIN | Выдать/перевыпустить временный пароль гостевого аккаунта (issue #217). |
| `POST` | `/admin/players/{id}/delete` | Form → redirect | SUPER_ADMIN | Удалить игрока. |

#### Admin: экспорт

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/admin/export` | Excel file | ADMIN | Экспорт всех данных в Excel (игроки, турниры, регистрации). |

---

## 3. Домен: Clubs

**Ответственность:** CRUD клубов — площадок, на которых проводятся турниры. Хранит адрес, контакты, карту, логотип. Мягкое удаление через `is_active`.

**Таблицы:** `club_db`

**Сервисы:** `ClubService`

**Зависимости:** создатель клуба сохраняется как `created_by` (денормализованный ID из `owners_db`). Турниры ссылаются на клуб через `club_id`.

---

### Эндпоинты Clubs

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/clubs` | HTML | Публичный список активных клубов. |
| `GET` | `/clubs/{id}` | HTML | Публичная страница клуба: описание, турниры, карта. |

#### REST API (публичный, read-only)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/clubs` | — | Все клубы (включая неактивные). |
| `GET` | `/api/clubs/active` | — | Только активные клубы. |
| `GET` | `/api/clubs/{id}` | — | Клуб по ID. |
| `GET` | `/api/clubs/nombre/{nombre}` | — | Клуб по точному названию. |
| `GET` | `/api/clubs/zona/{zona}` | — | Клубы по зоне / району города. |
| `GET` | `/api/clubs/search?q=...` | — | Полнотекстовый поиск по названию и адресу. |

#### REST API (запись, ROLE_OWNER)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/clubs` | OWNER | Создать клуб (JSON). |
| `PUT` | `/api/clubs/{id}` | OWNER | Обновить клуб (JSON). |
| `DELETE` | `/api/clubs/{id}` | OWNER | Мягкое удаление (`is_active = false`). |
| `DELETE` | `/api/clubs/{id}/hard` | OWNER | Физическое удаление из БД. |

#### Admin UI (SUPER_ADMIN / ORGANIZER)

| Метод | URL | Описание |
|-------|-----|----------|
| `GET` | `/admin/clubs` | Список всех клубов с управлением. |
| `GET` | `/admin/clubs/new` | Форма создания клуба. |
| `POST` | `/admin/clubs` | Сохранить новый клуб (form submit). |
| `GET` | `/admin/clubs/{id}` | Детальный просмотр клуба. |
| `GET` | `/admin/clubs/{id}/edit` | Форма редактирования клуба. |
| `POST` | `/admin/clubs/{id}/edit` | Сохранить изменения клуба. |
| `POST` | `/admin/clubs/{id}/toggle-status` | Включить / выключить клуб. |
| `POST` | `/admin/clubs/{id}/delete` | Удалить клуб (мягкое). |

---

## 4. Домен: Tournaments

**Ответственность:** жизненный цикл турнира — создание, редактирование, публикация, смена статуса, копирование, деактивация, удаление. Турнир — агрегат, к которому привязаны все форматы.

**Таблицы:** `tournaments_db`

**Сервисы:** `TournamentService`, `BracketService`

**Зависимости:**
- Читает `club_db` (список клубов для форм)
- Создатель/владелец — запись из `owners_db` (денормализован)
- Форматы турниров (King of Court, Americano) создают свои таблицы при инициализации

**Статусная машина турнира:**
```
BORRADOR → PUBLICADO → REGISTRO_ABIERTO → CERRADO → FINALIZADO
                                                   → CANCELADO
```

---

### Эндпоинты Tournaments

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/torneos` | HTML | Публичный список предстоящих турниров с фильтрами. |
| `GET` | `/torneo/{id}` | HTML | Публичная карточка турнира: описание, регистрация, список участников. |

#### REST API (публичный, read-only)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/tournaments` | — | Все турниры. |
| `GET` | `/api/tournaments/upcoming` | — | Предстоящие турниры (дата > сегодня). |
| `GET` | `/api/tournaments/{id}` | — | Турнир по ID. |
| `GET` | `/api/tournaments/club/{clubId}` | — | Турниры конкретного клуба. |
| `GET` | `/api/tournaments/status/{status}` | — | Турниры по статусу. |
| `GET` | `/api/tournaments/search` | — | Поиск по: `clubId`, `genero`, `nivel`, `tipo`, `estado`. |
| `GET` | `/api/tournaments/my` | ADMIN | Турниры текущего организатора (для себя — все, если SUPER_ADMIN). |

#### REST API (запись, SUPER_ADMIN / ORGANIZER)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/tournaments` | ADMIN | Создать турнир (JSON). |
| `PUT` | `/api/tournaments/{id}` | ADMIN | Полное обновление турнира. ORGANIZER может только свои турниры. |
| `PATCH` | `/api/tournaments/{id}/status` | ADMIN | Изменить статус турнира. |
| `DELETE` | `/api/tournaments/{id}` | ADMIN | Удалить турнир (мягкое). ORGANIZER — только свои. |

#### Admin UI (SUPER_ADMIN / ORGANIZER)

| Метод | URL | Описание |
|-------|-----|----------|
| `GET` | `/admin` | Главная панель: счётчики, последние турниры, матчи в процессе. |
| `GET` | `/admin/tournaments` | Список всех турниров с флагом `isOwner` для каждого. |
| `GET` | `/admin/tournaments/new` | Форма создания турнира (выбор клуба, типа, уровня и т.д.). |
| `POST` | `/admin/tournaments` | Сохранить новый турнир. |
| `GET` | `/admin/tournaments/{id}` | Детальный просмотр турнира + список регистраций + статус KoC/Americano. |
| `GET` | `/admin/tournaments/{id}/edit` | Форма редактирования (только для владельца или SUPER_ADMIN). |
| `POST` | `/admin/tournaments/{id}/edit` | Сохранить изменения. |
| `POST` | `/admin/tournaments/{id}/status` | Изменить статус (select из выпадающего списка). |
| `POST` | `/admin/tournaments/{id}/delete` | Удалить турнир. |
| `POST` | `/admin/tournaments/{id}/deactivate` | Деактивировать (`is_active = false`), чтобы потом удалить. |
| `GET` | `/admin/tournaments/{id}/copy` | Открыть форму создания с предзаполненными данными из исходного турнира (поле `nombre` += ` (copia)`). |

#### Bracket (сетка матчей)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/admin/tournaments/{tournamentId}/matches/generate` | ADMIN | Сгенерировать первоначальную сетку матчей на основе списка игроков. |
| `POST` | `/admin/tournaments/{tournamentId}/matches/{matchId}` | ADMIN | Обновить результат матча. Автоматически продвигает победителя в следующий раунд. |

---

## 5. Домен: Registrations

**Ответственность:** регистрация / отмена участия игроков, управление листом ожидания, парная регистрация (в том числе с незарегистрированным партнёром), подтверждение участия через email-ссылки.

**Таблицы:** `tournament_registrations_db`

**Сервисы:** `TournamentService` (базовые операции), `DoubleTournamentRegistrationService` (парная логика), `TournamentNotificationService` (отправка писем)

**Фоновые задачи:**
- Cron `0 */10 * * * *` → `TournamentService.checkExpiredInvitations()` — переводит просроченные `WAITLIST_INVITED` обратно в `WAITLIST`, приглашает следующего. После 2 попыток → `CANCELLED`.

**Статусная машина:**
```
Одиночная:     → CONFIRMED | WAITLIST → WAITLIST_INVITED → CONFIRMED | CANCELLED
Парная:        → CONFIRMED | WAITLIST (сразу — партнёр, с аккаунтом или без, LFPT-0687)
Парная (легаси, регистрации до LFPT-0687): → PARTNER_INVITED → WAITLIST | CONFIRMED
                 (email-подтверждение по ссылке, см. docs/endpoints/double-registration.md)
```

---

### Эндпоинты Registrations

#### Лист ожидания

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/waitlist/confirm` | HTML | — | Страница подтверждения по ссылке из email (`?token=...` — одноразовый `waitlist_confirmation_token`, issue #194). Переводит `WAITLIST_INVITED → CONFIRMED`. |

#### Одиночная регистрация (ROLE_PLAYER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/players/tournaments/{tournamentId}/register` | JSON | PLAYER | Записаться на турнир. Проверяет `hasValidContact()` и уровень игрока против `categoriaNivel` турнира — правило "±1 ступень" по единой расчётной шкале мужских (C) и женских (D) категорий (`TournamentLevelPolicy`, LFPT-0303/LFPT-0744: `Cn→n`, `Dn→n+1`, пол сам по себе не блокирует регистрацию), не применяется к `CANCHA_ABIERTA`/`PADEL_CLINIC` и к категориям, не входящим в `Nivel.isPlayerLevel` (`TODOS`, `PRINCIPIANTES`, диапазоны, `SUMA_*`, `D5`/`D4`). Уровень не указан → 400 + `needLevel: true`. Если мест нет — попадает в WAITLIST. |
| `POST` | `/players/tournaments/{tournamentId}/cancel` | JSON | PLAYER | Отменить одиночную регистрацию. Проверяет `deadline_cancelacion`. |

#### Парная регистрация (ROLE_PLAYER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/api/tournaments/double/{tournamentId}/register` | JSON | PLAYER | Зарегистрировать пару. Принимает данные партнёра (зарегистрированного или нет). Отправляет партнёру email с приглашением. |
| `POST` | `/api/tournaments/double/confirm` | JSON | — | Подтверждение партнёра по токену из email. |
| `GET` | `/double-registration/complete` | redirect | — | Точка входа для незарегистрированного партнёра из email-ссылки. Обрабатывает токен и редиректит на главную. |
| `POST` | `/players/tournaments/{tournamentId}/cancel-double` | JSON | PLAYER | Отмена парной регистрации. Три варианта: `self` (только себя), `full` (всю пару), `replace` (заменить себя другим игроком). |
| `GET` | `/players/tournaments/{tournamentId}/my-registration` | JSON | PLAYER | Получить детали текущей регистрации (partnerID, partnerName, isPartnerRegistered). |

#### Соло-регистрация на парный турнир (ROLE_PLAYER)

Статусы `SOLO_ADD_LATER` / `SOLO_SEARCH` / `SOLO_EXPIRED`; напоминания и автосведение — `SoloRegistrationSchedulerService`.

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/api/tournaments/double/{tournamentId}/register-solo` | JSON | PLAYER | Записаться без пары: «добавлю партнёра позже» (`SOLO_ADD_LATER`) или «ищу пару» (`SOLO_SEARCH`, с согласием показать контакты). |
| `POST` | `/api/tournaments/double/{tournamentId}/add-partner` | JSON | PLAYER | Добавить партнёра к своей соло-регистрации. |
| `GET` | `/api/tournaments/double/{tournamentId}/looking-for-partner` | JSON | — | Игроки турнира, ищущие пару. |
| `POST` | `/api/tournaments/double/{tournamentId}/propose-pair/{targetRegistrationId}` | JSON | PLAYER | Предложить другому игроку без пары объединиться — письмо `pair-proposal`. |
| `GET` | `/double-registration/accept-pair` | HTML | — | Страница из письма-предложения (`?token=...`), шаблон `accept-pair.html`. |
| `POST` | `/api/tournaments/double/accept-pair` | JSON | — | Принять предложение пары по токену. |

#### Admin: управление регистрациями

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/admin/tournaments/{tournamentId}/move-to-waitlist/{playerId}` | JSON | ADMIN | Переместить игрока из основного состава в лист ожидания вручную. |
| `POST` | `/admin/tournaments/{tournamentId}/move-to-main/{playerId}` | JSON | ADMIN | Переместить из листа ожидания в основной состав (обходит автоматическую очередь). |
| `POST` | `/admin/tournaments/{tournamentId}/players/{playerId}/late-arrival` | JSON | ADMIN | Отметить/снять опоздание (`lateArrival`) в AMERICANO/KING_OF_COURT — штраф −10 (LFPT-443). |
| `POST` | `/admin/tournaments/{tournamentId}/pair-solo` | redirect | ADMIN | Вручную свести в пару две соло-регистрации (`regId1`, `regId2`). |

---

## 6. Домен: Payments

**Ответственность:** учёт оплаты участия в турнире. Платёжного шлюза нет — платежи фиксирует вручную администратор (наличные, перевод). Фиксирует сумму, валюту, метод, транзакцию.

**Таблицы:** `payments_db`

**Сервисы:** `PaymentService`

**Зависимости:** `tournament_registrations_db` (один платёж → одна регистрация).

---

### Эндпоинты Payments

#### Admin UI (SUPER_ADMIN / ORGANIZER)

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/admin/tournaments/{tournamentId}/payments` | HTML | Страница управления оплатами турнира: список всех регистраций, статус оплаты каждого, форма для ручного ввода. |
| `POST` | `/admin/tournaments/{tournamentId}/payments/save` | Form → redirect | Массовое сохранение платёжных данных: статус, метод, сумма, валюта, transaction ID, посещение, подтверждение участия. |
| `POST` | `/admin/tournaments/{tournamentId}/payments/add-team` | Form → redirect | Добавить пару (`registrationId`) в командный плей-офф (AMERICANO_TEAMS) прямо со страницы оплат. |

#### Admin: метрики email

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/admin/email-stats` | ADMIN | Статистика отправки писем по типам (welcome, confirmation, invitation, …). |
| `GET` | `/api/admin/email-daily-count` | ADMIN | Количество писем за текущий день + лимит. |

#### Admin: аудит дублей игроков (LFPT-0677)

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/admin/players/duplicates-audit` | SUPER_ADMIN | Read-only отчёт о потенциальных дублях `PlayerPadel` (точные/вероятные совпадения, подозреваемые дубли из флоу приглашения партнёра, разбивка по источнику) — см. [admin-player-audit.md](endpoints/admin-player-audit.md). |

---

## 7. Домен: King of Court

**Ответственность:** управление турниром формата "King of Court" — поротное перемещение игроков между кортами по результатам. Real-time обновления через WebSocket.

**Таблицы:** `tournament_king_of_court_db`, `king_of_court_round_db`, `king_of_court_court_db`, `court_players_db`, `king_of_court_player_stats_db`, `king_of_court_match_result_db`

**Сервисы:** `KingOfCourtService`, `WebSocketService`

**WebSocket:** клиент подписывается на `/topic/king-of-court/{tournamentId}` — получает обновления состояния после каждого результата или перехода раунда.

**Зависимости:**
- Привязан к конкретному `tournaments_db` (1:1)
- Игроки берутся из `tournament_registrations_db` при инициализации

---

### Эндпоинты King of Court

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/tournaments/king-of-court/{kingId}` | HTML | Публичный экран турнира: текущие корты, рейтинг в реальном времени. |

#### REST API

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/king-of-court/tournaments/{tournamentId}/initialize` | ADMIN | Инициализировать KoC: задать `maxCourts` и `calibrationRounds`. Создаёт запись в `tournament_king_of_court_db` и первый раунд. |
| `GET` | `/api/king-of-court/tournaments/{kingId}/state` | — | Полное текущее состояние: номер раунда, корты с игроками, рейтинг. Используется для WebSocket-реконнекта. |
| `GET` | `/api/king-of-court/tournaments/{kingId}/ranking` | — | Только рейтинговая таблица (список `PlayerStatsDTO`). |
| `POST` | `/api/king-of-court/matches/result` | ADMIN | Сохранить результат матча на корте. Обновляет статистику игроков, шлёт WebSocket-сообщение. |
| `PUT` | `/api/king-of-court/matches/result/{resultId}` | ADMIN | Исправить ранее сохранённый результат. Откатывает старые очки и применяет новые. |
| `POST` | `/api/king-of-court/tournaments/{kingId}/next-round` | ADMIN | Перейти к следующему раунду. Рассчитывает расстановку игроков по кортам на основе текущего рейтинга. |
| `POST` | `/api/king-of-court/tournaments/{kingId}/rollback` | ADMIN | Откатить последний раунд. Удаляет раунд и возвращает очки игрокам. |
| `POST` | `/api/king-of-court/tournaments/{kingId}/finish` | ADMIN | Завершить турнир. Фиксирует финальный рейтинг, меняет статус. |
| `POST` | `/api/king-of-court/tournaments/{kingId}/youtube` | ADMIN | Сохранить ссылку на YouTube трансляцию для публичной страницы. |
| `GET` | `/api/king-of-court/tournaments/{kingId}/players/{playerId}/history` | — | История матчей конкретного игрока в этом турнире. |
| `GET` | `/api/king-of-court/ping` | — | Health check эндпоинта. |

#### Admin UI

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/admin/tournaments/king-of-court/{kingId}` | HTML | Управление KoC: корты, результаты, рейтинг, кнопки управления раундами. |
| `POST` | `/admin/tournaments/king-of-court/start` | Form → redirect | Запустить KoC из деталей турнира. |
| `POST` | `/admin/tournaments/king-of-court/{kingId}/finish` | Form → redirect | Завершить турнир. |
| `POST` | `/admin/tournaments/king-of-court/{kingId}/next-round` | Form → redirect | Перейти к следующему раунду. |
| `POST` | `/admin/tournaments/king-of-court/{kingId}/youtube` | Form → redirect | Сохранить ссылку на YouTube. |

---

## 8. Домен: Americano

**Ответственность:** три формата на одной кодовой базе — Americano (одиночный/парный с ротацией партнёров) и Team Americano (фиксированные пары, квалификация + плей-офф). Управление раундами, матчами, рейтингом, выбыванием.

**Таблицы:** `americano_rounds_db`, `americano_matches_db`, `americano_players_db`, `americano_teams_db`

**Сервисы:** `AmericanoService`, `TeamAmericanoService`, `TeamPlayoffService`

**Зависимости:**
- Привязан к `tournaments_db` (1:N раундов)
- Игроки из `player_padel_db`, команды из `americano_teams_db`

---

### Эндпоинты Americano

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/tournaments/americano/{tournamentId}` | HTML | Публичная страница Americano: сетка раундов, рейтинг. |
| `GET` | `/tournaments/team-americano/{tournamentId}` | HTML | Публичная страница Team Americano. |
| `GET` | `/tournaments/team-playoff/{tournamentId}` | HTML | Публичная страница плей-офф Team Americano. |

#### Admin UI

| Метод | URL | Описание |
|-------|-----|----------|
| `GET` | `/tournaments/americano/admin/{tournamentId}` | Управление Americano турниром: инициализация, раунды, матчи. |
| `GET` | `/tournaments/team-americano/admin/{tournamentId}` | Управление командным Americano (Round Robin). |
| `GET` | `/tournaments/team-playoff/admin/{tournamentId}` | Управление плей-офф командного Americano. |
| `POST` | `/tournaments/americano/{tournamentId}/preview-rounds` | Предпросмотр расстановки раундов до инициализации — только по сабмиту формы, не прямой GET. |
| `POST` | `/tournaments/team-americano/preview` | Предпросмотр командного Round Robin (`tournamentId` в форме) — та же логика. |

#### Americano — form-роуты `AmericanoViewController`

Инициализация — только через form-POST (REST-эндпоинта `initialize` нет). Write-роуты — роли управления (`SUPER_ADMIN`/`ADMIN`/`ORGANIZER`/`CLUB_ADMIN`) + проверка доступа к клубу турнира.

| Метод | URL | Описание |
|-------|-----|----------|
| `POST` | `/tournaments/americano/initialize` | Инициализация из карточки турнира (`tournamentId` в форме); уже инициализирован — редирект без ошибки. |
| `POST` | `/tournaments/americano/{tournamentId}/initialize` | Инициализация с конфигурацией раундов. |
| `POST` | `/tournaments/americano/admin/{tournamentId}/initialize` | То же из админ-страницы предпросмотра. |
| `GET` | `/tournaments/americano/{tournamentId}/initialize` | Не страница: редирект на `/admin/tournaments/{id}` или на турнир (LFPT-316). |
| `GET` | `/tournaments/americano/admin/{tournamentId}/preview` | Мёртвый роут, редирект (LFPT-314). |
| `POST` | `/tournaments/americano/{tournamentId}/register` | Записать игрока (`playerId`) администратором; мест нет — лист ожидания. |
| `POST` | `/tournaments/americano/{tournamentId}/cancel` | Отменить запись игрока. |
| `POST` | `/tournaments/americano/rounds/{roundId}/start` | Начать раунд. |
| `POST` | `/tournaments/americano/rounds/{roundId}/complete` | Завершить раунд. |
| `POST` | `/tournaments/americano/matches/{matchId}/result` | Сохранить результат матча. |
| `POST` | `/tournaments/americano/{tournamentId}/players/{playerId}/dropout` | Игрок выбыл. |
| `POST` | `/tournaments/americano/{tournamentId}/finish` | Завершить турнир. |
| `GET` | `/tournaments/americano/{tournamentId}/ranking` | Страница рейтинга. |
| `GET` | `/tournaments/americano/{tournamentId}/register` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |
| `GET` | `/tournaments/americano/{tournamentId}/rounds` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |
| `GET` | `/tournaments/americano/rounds/{roundId}` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |
| `GET` | `/tournaments/americano/matches/{matchId}` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |
| `GET` | `/tournaments/americano/matches/{matchId}/result` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |
| `GET` | `/tournaments/americano/{tournamentId}/players/{playerId}` | Шаблона нет — редирект на страницу турнира/рейтинга (LFPT-330). |

#### Team Americano — `TeamAmericanoViewController`

| Метод | URL | Описание |
|-------|-----|----------|
| `POST` | `/tournaments/team-americano/initialize` | Инициализация из карточки турнира (`tournamentId` + конфигурация). |
| `GET` | `/tournaments/team-americano/{tournamentId}/ranking` | Редирект на публичную страницу (блок рейтинга). |
| `GET` | `/tournaments/team-americano/matches/{matchId}/result` | Шаблона нет — редирект на админку турнира (LFPT-0335). |
| `POST` | `/tournaments/team-americano/matches/{matchId}/result` | Сохранить результат матча (форма). |
| `POST` | `/tournaments/team-americano/api/matches/{matchId}/result` | То же, JSON — из JS без перезагрузки. |
| `GET` | `/tournaments/team-americano/api/{tournamentId}/ranking` | Рейтинг команд, JSON. |
| `POST` | `/tournaments/team-americano/admin/{tournamentId}/finish` | Завершить турнир. |

#### Командный плей-офф (AMERICANO_TEAMS) — `TeamPlayoffViewController`

Квалификация + плей-офф с play-in. Все `admin/*` и `api/*` write-роуты — роли управления + проверка доступа к клубу турнира.

| Метод | URL | Описание |
|-------|-----|----------|
| `POST` | `/tournaments/team-playoff/admin/{tournamentId}/teams/add` | Добавить команду (пару). |
| `GET` | `/tournaments/team-playoff/admin/{tournamentId}/players/search` | Автодополнение игроков для формы «Добавить команду» (LFPT-0561). |
| `POST` | `/tournaments/team-playoff/admin/teams/{teamId}/update` | Изменить команду. |
| `POST` | `/tournaments/team-playoff/admin/teams/{teamId}/delete` | Удалить команду. |
| `POST` | `/tournaments/team-playoff/admin/teams/{teamId}/no-show` | Отметить команду статусом «не пришла» (`AmericanoPlayerStatus.NO_SHOW`, LFPT-0729) — исключается из квалификации/посева плей-офф; блокируется, если у команды уже есть матч квалификации. |
| `POST` | `/tournaments/team-playoff/admin/teams/{teamId}/no-show/cancel` | Отменить отметку «не пришла» — команда возвращается в `ACTIVE` (LFPT-0729). |
| `POST` | `/tournaments/team-playoff/admin/{tournamentId}/import-registrations` | Импортировать пары из регистраций турнира. |
| `POST` | `/tournaments/team-playoff/admin/{tournamentId}/init-qualification` | Сгенерировать квалификацию. |
| `POST` | `/tournaments/team-playoff/admin/{tournamentId}/init-playoff` | Сгенерировать сетку плей-офф. |
| `POST` | `/tournaments/team-playoff/admin/{tournamentId}/regenerate-playoff` | Пересобрать плей-офф после правки результата квалификации (issue #298). |
| `POST` | `/tournaments/team-playoff/admin/qual-matches/{matchId}/result` | Результат матча квалификации (форма). |
| `POST` | `/tournaments/team-playoff/admin/playoff-matches/{matchId}/result` | Результат матча плей-офф (форма). |
| `POST` | `/tournaments/team-playoff/api/qual-matches/{matchId}/result` | Результат матча квалификации, JSON. |
| `POST` | `/tournaments/team-playoff/api/playoff-matches/{matchId}/result` | Результат матча плей-офф, JSON. |
| `POST` | `/tournaments/team-playoff/api/{tournamentId}/qual-matches` | Создать матч квалификации на корте. |
| `POST` | `/tournaments/team-playoff/api/{tournamentId}/qual-matches/queue` | Поставить пару в очередь без корта (LFPT-367). |
| `POST` | `/tournaments/team-playoff/api/matches/{matchId}/assign-court` | Назначить свободный корт матчу из очереди → в игре. |
| `POST` | `/tournaments/team-playoff/api/matches/{matchId}/queue/remove` | Убрать матч из очереди. |
| `POST` | `/tournaments/team-playoff/api/matches/{matchId}/court` | Сменить корт незавершённого матча. |
| `POST` | `/tournaments/team-playoff/api/matches/{matchId}/team` | Заменить команду в слоте незавершённого матча (реванш — только предупреждение). |
| `GET` | `/tournaments/team-playoff/api/matches/{matchId}/swap-candidates` | Кандидаты на обмен командами (соседние матчи стадии, issue #298). |
| `POST` | `/tournaments/team-playoff/api/matches/{matchId}/swap-team` | Обменять команды в двух незавершённых матчах плей-офф. |
| `GET` | `/tournaments/team-playoff/api/{tournamentId}/ranking` | Рейтинг квалификации, JSON. |
| `GET` | `/tournaments/team-playoff/api/{tournamentId}/court-board` | Доска кортов: занятость, пул команд, предложенные пары 2-го тура. |

#### REST API — регистрация

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/tournaments/americano/{tournamentId}/register/{playerId}` | ADMIN | Зарегистрировать игрока на Americano (для управляемой регистрации из Admin). |
| `DELETE` | `/api/tournaments/americano/{tournamentId}/cancel/{playerId}` | ADMIN | Отменить регистрацию игрока. |

#### REST API — инициализация

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/tournaments/americano/{tournamentId}/initialized` | — | Проверить, инициализирован ли турнир. |

#### REST API — раунды

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/tournaments/americano/{tournamentId}/rounds` | — | Все раунды турнира. |
| `GET` | `/api/tournaments/americano/rounds/{roundId}` | — | Раунд по ID. |
| `POST` | `/api/tournaments/americano/rounds/{roundId}/start` | ADMIN | Начать раунд (`PENDING → IN_PROGRESS`). |
| `POST` | `/api/tournaments/americano/rounds/{roundId}/complete` | ADMIN | Завершить раунд (`IN_PROGRESS → COMPLETED`). |
| `PUT` | `/api/tournaments/americano/rounds/{roundId}/points-limit` | ADMIN | Изменить лимит очков раунда (допустимые значения: 15, 21, 24, 32, 40). |

#### REST API — матчи

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/tournaments/americano/{tournamentId}/matches` | — | Все матчи турнира. |
| `GET` | `/api/tournaments/americano/rounds/{roundId}/matches` | — | Матчи конкретного раунда. |
| `GET` | `/api/tournaments/americano/matches/{matchId}` | — | Матч по ID. |
| `POST` | `/api/tournaments/americano/matches/{matchId}/result` | ADMIN | Сохранить результат матча. Обновляет статистику игрока/команды. |
| `GET` | `/api/tournaments/americano/{tournamentId}/players/{playerId}/matches` | — | Все матчи конкретного игрока в турнире. |

#### REST API — рейтинг и статистика

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/tournaments/americano/{tournamentId}/ranking` | — | Детальный рейтинг с сортировкой (`sortBy`: score, wins, pointDiff). |
| `GET` | `/api/tournaments/americano/{tournamentId}/ranking/simple` | — | Упрощённый рейтинг (список с базовой статистикой). |
| `GET` | `/api/tournaments/americano/{tournamentId}/players/{playerId}/stats` | — | Статистика конкретного игрока: очки, матчи, win rate. |
| `GET` | `/api/tournaments/americano/{tournamentId}/players/count` | — | Количество активных игроков турнира. |

#### REST API — управление игроками

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/tournaments/americano/{tournamentId}/players/{playerId}/dropout` | ADMIN | Отметить игрока как выбывшего. Указывается причина. Игрок исключается из следующих раундов. |
| `POST` | `/api/tournaments/americano/{tournamentId}/finish` | ADMIN | Завершить турнир. Фиксирует финальный рейтинг, меняет статус турнира на `FINALIZADO`. |

---

## 9. Домен: Ranking

**Ответственность:** накопленный рейтинг игрока по всем bracket-турнирам. Одна запись на игрока: очки, победы, поражения, позиция, тренд.

**Таблицы:** `ranking_db`

**Сервисы:** `RankingService`

**Особенности:** рейтинг пересчитывается после завершения bracket-матчей (`@Timed`, `@Counted`). Текущая реализация — базовая заглушка в дашборде (`rankingPosition = 42`, `puntosRanking = 1250`), полная логика расчёта живёт в `RankingService`.

---

### Эндпоинты Ranking

#### Публичные страницы

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/ranking` | HTML | Публичная таблица рейтинга всех игроков. |

---

## 10. Домен: Notifications

**Ответственность:** транзакционные email через SMTP и Telegram (DM-напоминания игрокам, алерты в рабочую группу — `TelegramService`, `TelegramBotPoller`, `TelegramReminderScheduler`). Своих HTTP-эндпоинтов почти нет — вызывается из других доменов; служебные эндпоинты — в разделе 10a.

**Сервисы:** `EmailService`, `TournamentNotificationService`, `EmailMetricsService`

**Шаблоны:** Thymeleaf в `src/main/resources/templates/email/`

| Шаблон | Триггер |
|--------|---------|
| `bienvenida.html` | Успешная регистрация нового игрока |
| `confirmacion.html` | Ожидание подтверждения email |
| `tournament-confirmation.html` | Регистрация на турнир подтверждена |
| `new-partner-invitation.html` | Приглашение партнёра в пару |
| `vacancy-invitation.html` | Приглашение из листа ожидания (с дедлайном 5 мин) |
| `waitlist-notification.html` | Попал в лист ожидания |
| `no-spots-left.html` | Мест нет совсем |
| `recuperar-password.html` | Ссылка для сброса пароля |
| `pair-tournament-reminder.html` | Напоминание за 5 ч до старта парного турнира (LFPT-437) |
| `individual-tournament-reminder.html` | Напоминание за 5 ч до старта AMERICANO / KING_OF_COURT (LFPT-443) |
| `cancha-abierta-reminder.html` | Напоминание за 5 ч до Cancha Abierta (LFPT-443) |
| `partner-data-reminder.html` | Соло-регистрация `SOLO_ADD_LATER`: напоминание добавить партнёра |
| `looking-for-partner-notification.html` | Соло-регистрация: уведомление игрокам, ищущим пару |
| `pair-proposal.html` | Предложение объединиться в пару (ссылка `/double-registration/accept-pair`) |
| `admin-solo-registration.html` | Администратору: новая соло-регистрация |
| `registration-cancelled.html` | Отмена регистрации на турнир — независимо от инициатора (LFPT-0752) |
| `registration-moved-to-reserve.html` | Перевод из основного состава в резерв администратором (LFPT-0752) |

**Метрики Prometheus (Grafana):**
- `email_daily` / `email_daily_limit` — дневной расход лимита
- `email_sent_total{type}` — отправлено по типу
- `email_errors_total` — ошибки отправки
- `email_rejected_total` — отклонено (превышен лимит)

**Cookie и согласие:**

| Метод | URL | Описание |
|-------|-----|----------|
| `POST` | `/api/cookies/accept` | Принять все cookies. |
| `POST` | `/api/cookies/reject` | Отклонить необязательные cookies. |
| `POST` | `/api/cookies/customize` | Сохранить кастомные настройки cookies. |

---

## 10a. Служебные и внутренние эндпоинты

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/error/403`, `/error/rate-limit` | — | Страницы ошибок доступа и превышения лимита (`HomeController`). |
| `GET` | `/oct-tg`, `/oct-wa`, `/oct-ig`, `/ig-bio` | — | Короткие маркетинговые ссылки → `1-padel.com/?utm_...` (LFPT-0472, LFPT-0489). |
| `GET` | `/ig-story` | — | Постоянная ссылка сторис и репостов → `302 https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story` (LFPT-0779). |
| `GET` | `/app`, `/app/` | — | Веб-версия клиента 1Padel.App: `/app` → `302 /app/`, `/app/` → `forward:/app/index.html` (статика `static/app/`), анонимно (`MobileAppViewController`, LFPT-0643). |
| `GET` | `/admin/telegram-pipeline` | SUPER_ADMIN | Страница выключателя Telegram-входа в конвейер (`telegram_pipeline_state_db`). |
| `POST` | `/admin/telegram-pipeline/toggle` | SUPER_ADMIN | Поставить конвейер на паузу / снять. |
| `GET` | `/api/internal/telegram-pipeline/status` | заголовок `X-Internal-Secret` (`pipeline.shared-secret`) | Статус паузы для relay; без сессии. |
| `POST` | `/test/telegram/ping` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Тестовое сообщение в рабочую группу. |
| `POST` | `/test/telegram/error` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Тестовый ERROR-алерт. |
| `POST` | `/test/telegram/attacks` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Тестовый алерт об атаке. |
| `POST` | `/test/telegram/reminders` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Прогнать рассылку напоминаний. |
| `POST` | `/test/telegram/reminders/force/{tournamentId}` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Принудительно отправить напоминание по турниру. |
| `GET` | `/test/telegram/status` | SUPER_ADMIN + CSRF | Telegram (LFPT-0423): Состояние бота и планировщика. |
| `GET` | `/test/tournaments` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Страница `test/tournaments.html`. |
| `POST` | `/test/tournaments/{tournamentId}/register-test-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Массово записать тестовых игроков. |
| `POST` | `/test/tournaments/{tournamentId}/clear-test-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Удалить тестовые регистрации турнира. |
| `POST` | `/test/tournaments/{tournamentId}/register-real-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записать реальных игроков по ID. |
| `POST` | `/test/tournaments/{tournamentId}/register-pair` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записать пару из базы. |
| `POST` | `/test/tournaments/{tournamentId}/register-pair-with-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записать пару, выбранную вручную. |
| `POST` | `/test/tournaments/{tournamentId}/register-guest-pair` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записать пару, где один/оба не в базе. |
| `POST` | `/test/tournaments/{tournamentId}/register-guest-player` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записать гостя на одиночный турнир. |
| `POST` | `/test/tournaments/{tournamentId}/remove-player/{playerId}` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Убрать игрока с турнира. |
| `GET` | `/test/tournaments/{tournamentId}/stats` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Статистика турнира. |
| `GET` | `/test/tournaments/{tournamentId}/stats-enhanced` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Статистика с парами. |
| `GET` | `/test/tournaments/stats-all` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Статистика всех турниров одним запросом (вместо N параллельных `/stats`, исчерпывавших пул HikariCP). |
| `GET` | `/test/tournaments/{tournamentId}/players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Записанные игроки, сгруппированные по парам. |
| `GET` | `/test/tournaments/available-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Игроки, доступные для записи. |
| `GET` | `/test/tournaments/available-players-for-pairs` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Игроки для составления пар. |
| `GET` | `/test/tournaments/available-pairs` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Уже записанные пары. |
| `GET` | `/test/tournaments/search-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Поиск по имени/email. |
| `POST` | `/test/tournaments/create-test-player` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Создать тестового игрока. |
| `POST` | `/test/tournaments/create-test-players-bulk` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Массово создать тестовых игроков. |
| `POST` | `/test/tournaments/delete-test-players-bulk` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Удалить тестовых игроков. |
| `GET` | `/test/tournaments/test-players-count` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Число тестовых игроков. |
| `GET` | `/test/tournaments/debug/registrations/{tournamentId}` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Отладка: регистрации турнира. |
| `GET` | `/test/tournaments/debug/player-count` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Отладка: число игроков. |
| `GET` | `/test/tournaments/debug/first-players` | SUPER_ADMIN | Тестовые данные, JDBC (`TestTournamentController`): Отладка: первые 5 игроков. |

---

## 11. Аналитика связей для декомпозиции

### Приоритет выноса в микросервисы

| Приоритет | Домен | Причина |
|-----------|-------|---------|
| 1 | **Identity** | Нет зависимостей от других доменов (только отправляет письма). Чёткая граница, стандартная задача. |
| 2 | **Clubs** | Минимальные зависимости (только `created_by` от Identity). Read-heavy. |
| 3 | **King of Court** | Изолированный формат, слабо связан с Registration. Единственная связь — список зарегистрированных игроков при старте. |
| 4 | **Americano** | Аналогично KoC. Более сложная логика, но изолирован. |
| 5 | **Ranking** | Подписчик на события из Tournaments/Matches. После выноса — event-driven. |
| 6 | **Notifications** | Сейчас встроен в другие сервисы. Нужно переделать на async (очередь). |
| 7 | **Payments** | Зависит от Registrations. Выносить только вместе или после. |
| 8 | **Tournaments + Registrations** | Самые связанные — выносить последними или как единый сервис. |

### Текущие проблемные связи (coupling points)

| Проблема | Где | Решение при декомпозиции |
|----------|-----|--------------------------|
| `TournamentService` содержит логику регистраций | `TournamentService.registerPlayer()`, `cancelRegistration()` | Разделить на `TournamentService` (CRUD турнира) и `RegistrationService` |
| `AdminController` напрямую использует 6 сервисов | `AdminController.java` | Fanout через API gateway или BFF-слой |
| `PlayerDashboardController` дублирует `/api/players/me/contact` | `PlayerDashboardController` vs `PlayerContactController` | Удалить дубликат, оставить один эндпоинт |
| `tournament_id` в americano/koc — direct FK, не event | `AmericanoRound`, `KingOfCourtRound` | При выносе — передавать tournamentId как reference, подтягивать через API |
| `created_by` / `owner_id` в `tournaments_db` — денормализованный ID | `Tournament` | При выносе Identity — заменить на async lookup или кешировать |

### WebSocket

Текущий WebSocket `/ws` (SockJS) используется только доменом King of Court.  
При декомпозиции — выносить вместе с King of Court сервисом или через отдельный WebSocket gateway.
