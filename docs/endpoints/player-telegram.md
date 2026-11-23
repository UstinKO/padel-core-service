# Telegram для игроков

Сервер #1294, интерфейс #1295. Мутации требуют сессию/CSRF либо действующий допустимый Bearer-контракт. Ответы Cache-Control: no-store. Вход Telegram — существующий poller, публичный webhook не добавляется.

| Метод | Маршрут | Права | Результат |
|---|---|---|---|
| GET | `/api/players/me/telegram` | PLAYER | available, connected |
| POST | `/api/players/me/telegram/link` | PLAYER | url, expiresAt; прежняя ссылка отзывается |
| DELETE | `/api/players/me/telegram` | PLAYER | 204, отключение и отзыв ссылки |
| GET | `/api/admin/tournaments/{id}/telegram` | Управляющий событием | tournamentId, name, participants: registrationId/playerId/name/response/connected/delivery |
| POST | `/api/admin/tournaments/{id}/telegram/confirmations` | Управляющий событием | Запрос активной основе и свежая сводка |
| POST | `/api/admin/telegram/summary` | Настроенный SUPER_ADMIN | 204, персональная сводка |

response: NOT_REQUIRED/PENDING/CONFIRMED/DECLINED. delivery: NOT_SENT/IN_FLIGHT/SENT/FAILED/BLOCKED/UNKNOWN. SENT означает принятие Telegram API, а не прочтение. UNKNOWN автоматически не повторяется. Ключ содержит регистрацию, версию, получателя, вид и время старта. Старая кнопка после переноса не подтверждает участие.

## Настройка выпуска

Используется существующий TELEGRAM_BOT_TOKEN, отдельно от токена SDD-конвейера. TELEGRAM_PLAYERS_ENABLED=true переключает poller и напоминания на новую реализацию. При выключенном флаге сохранено прежнее поведение. TELEGRAM_PLAYERS_BOT_USERNAME — без @. Прежние связи по username не переносятся автоматически: игрок подключает профиль новой ссылкой.

TELEGRAM_PLAYERS_AUTOMATIC_ENABLED по умолчанию false. Интервалы TELEGRAM_PLAYERS_CONFIRMATION_HOURS и TELEGRAM_PLAYERS_REMINDER_HOURS по умолчанию 24/3 часа. Дата и время интерпретируются в IANA-зоне мероприятия. Скрытые события получают уведомления только своим участникам.

Для сводки задаются telegram.players.organizer-owner-id и telegram.players.organizer-chat-id либо environment-имена TELEGRAM_PLAYERS_ORGANIZER_OWNER_ID и TELEGRAM_PLAYERS_ORGANIZER_CHAT_ID. Проверяются положительный личный chatId и существующий включённый SUPER_ADMIN. Команда /summary принимает только этот Telegram ID. Ежедневная сводка требует TELEGRAM_PLAYERS_DAILY_SUMMARY_ENABLED=true; cron по умолчанию 10:00 America/Argentina/Buenos_Aires.

Команды игрока: /start с одноразовым токеном, /help, /stop. Обычный /start не связывает профиль по username. Тексты ES/RU/EN по профилю, сводка RU. Оплаты не читаются и не меняются.

## Надёжность

Связи, хеши токенов, уведомления и polling offset хранятся в PostgreSQL. Polling сериализуется арендой. Отметка уведомления фиксируется до внешнего вызова. Ограничена скорость одного процесса. 403 отключает соединение; таймаут и 5xx дают UNKNOWN. Авария после внешнего вызова может оставить IN_FLIGHT; спустя две минуты сводка показывает UNKNOWN. Exactly-once не заявляется. Общая координация cron/Swarm остаётся #1213. Первое включение согласовывается на всех экземплярах, без параллельного старого poller того же токена.
