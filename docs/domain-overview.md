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
| `POST` | `/admin/players/{id}/toggle-status` | Form → redirect | ADMIN | Активация / деактивация аккаунта игрока. |

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
Парная:        → PARTNER_INVITED → PENDING_PARTNER → PAIR_REGISTERED → CONFIRMED
```

---

### Эндпоинты Registrations

#### Лист ожидания

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/waitlist/confirm` | HTML | — | Подтверждение участия по ссылке из email (`?registrationId=...`). Переводит `WAITLIST_INVITED → CONFIRMED`. |

#### Одиночная регистрация (ROLE_PLAYER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/players/tournaments/{tournamentId}/register` | JSON | PLAYER | Записаться на турнир. Проверяет `hasValidContact()`. Если мест нет — попадает в WAITLIST. |
| `POST` | `/players/tournaments/{tournamentId}/cancel` | JSON | PLAYER | Отменить одиночную регистрацию. Проверяет `deadline_cancelacion`. |

#### Парная регистрация (ROLE_PLAYER)

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/api/tournaments/double/{tournamentId}/register` | JSON | PLAYER | Зарегистрировать пару. Принимает данные партнёра (зарегистрированного или нет). Отправляет партнёру email с приглашением. |
| `POST` | `/api/tournaments/double/confirm` | JSON | — | Подтверждение партнёра по токену из email. |
| `POST` | `/api/tournaments/double/complete` | JSON | PLAYER | Завершение парной регистрации (для зарегистрированных партнёров через форму). |
| `GET` | `/double-registration/complete` | redirect | — | Точка входа для незарегистрированного партнёра из email-ссылки. Обрабатывает токен и редиректит на главную. |
| `POST` | `/players/tournaments/{tournamentId}/cancel-double` | JSON | PLAYER | Отмена парной регистрации. Три варианта: `self` (только себя), `full` (всю пару), `replace` (заменить себя другим игроком). |
| `GET` | `/players/tournaments/{tournamentId}/my-registration` | JSON | PLAYER | Получить детали текущей регистрации (partnerID, partnerName, isPartnerRegistered). |

#### Admin: управление регистрациями

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `POST` | `/admin/tournaments/{tournamentId}/move-to-waitlist/{playerId}` | JSON | ADMIN | Переместить игрока из основного состава в лист ожидания вручную. |
| `POST` | `/admin/tournaments/{tournamentId}/move-to-main/{playerId}` | JSON | ADMIN | Переместить из листа ожидания в основной состав (обходит автоматическую очередь). |

---

## 6. Домен: Payments

**Ответственность:** учёт оплаты участия в турнире. Поддерживает ручной ввод платежей администратором (наличные, перевод) и онлайн-оплату через внешний провайдер. Фиксирует сумму, валюту, метод, транзакцию.

**Таблицы:** `payments_db`

**Сервисы:** `PaymentService`

**Зависимости:** `tournament_registrations_db` (один платёж → одна регистрация).

---

### Эндпоинты Payments

#### Публичная страница

| Метод | URL | Тип | Auth | Описание |
|-------|-----|-----|------|----------|
| `GET` | `/payments/{registrationId}` | HTML | — | Страница оплаты для игрока: детали регистрации и форма оплаты. |
| `POST` | `/payments/{registrationId}/checkout` | redirect | PLAYER | Инициировать онлайн-оплату через провайдера. |

#### Admin UI (SUPER_ADMIN / ORGANIZER)

| Метод | URL | Тип | Описание |
|-------|-----|-----|----------|
| `GET` | `/admin/tournaments/{tournamentId}/payments` | HTML | Страница управления оплатами турнира: список всех регистраций, статус оплаты каждого, форма для ручного ввода. |
| `POST` | `/admin/tournaments/{tournamentId}/payments/save` | Form → redirect | Массовое сохранение платёжных данных: статус, метод, сумма, валюта, transaction ID, посещение, подтверждение участия. |

#### Admin: метрики email

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `GET` | `/api/admin/email-stats` | ADMIN | Статистика отправки писем по типам (welcome, confirmation, invitation, …). |
| `GET` | `/api/admin/email-daily-count` | ADMIN | Количество писем за текущий день + лимит. |

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
| `POST` | `/tournaments/team-americano/{tournamentId}/preview-double-rounds` | Предпросмотр командных раундов — та же логика. |

#### REST API — регистрация

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/tournaments/americano/{tournamentId}/register/{playerId}` | ADMIN | Зарегистрировать игрока на Americano (для управляемой регистрации из Admin). |
| `DELETE` | `/api/tournaments/americano/{tournamentId}/cancel/{playerId}` | ADMIN | Отменить регистрацию игрока. |

#### REST API — инициализация

| Метод | URL | Auth | Описание |
|-------|-----|------|----------|
| `POST` | `/api/tournaments/americano/{tournamentId}/initialize` | ADMIN | Инициализировать Americano: задать количество раундов, формат, очки. Генерирует все раунды и матчи. |
| `GET` | `/api/tournaments/americano/{tournamentId}/initialized` | — | Проверить, инициализирован ли турнир. |
| `GET` | `/api/tournaments/americano/{tournamentId}/preview-rounds` | ADMIN | Предпросмотр расстановки раундов без сохранения в БД. |

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

**Ответственность:** отправка транзакционных email-писем через SMTP. Не имеет собственных HTTP-эндпоинтов — вызывается внутри других доменов.

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
