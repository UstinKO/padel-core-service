# Отзывы о рейтинге Elo

- `POST /api/rating/feedback` принимает CSRF-токен и поля формы `requestKey` (UUID), `source` (DASHBOARD или RANKING), `topic` (QUESTION, IDEA, ERROR или OTHER), `message` (до 2000 символов). Сервер определяет игрока из сессии и проверяет допуск к пилоту. Успешная запись и повтор того же запроса возвращают 204.
- `GET /admin/rating-feedback` показывает последние 50 обращений и статус отправки письма. Доступ — SUPER_ADMIN. Параметр `before` открывает более ранние записи.

Таблица `elo_pilot_feedback_db` (v1.77) сохраняет текст, время, источник, тему и имя автора на момент отправки. Уникальная пара ID игрока и ключа запроса защищает от двойной записи.

Функция включается настройкой `app.rating.pilot.feedback-enabled=true` (`APP_RATING_PILOT_FEEDBACK_ENABLED=true`). По умолчанию выключена.
