# LFPT-0428: Fix страница входа — сообщение о просроченной ссылке сброса пароля

## Статус
approved

## Источник
Issue [#428](https://github.com/UstinKO/padel-core-service/issues/428) (прямой технический вход, задача от архитектора/пользователя). Зависит от backend-редиректа из [#425](https://github.com/UstinKO/padel-core-service/issues/425), который закрыт PR [#500](https://github.com/UstinKO/padel-core-service/pull/500) (LFPT-0499) — `GET /recuperar-password?token=...` с невалидным/просроченным/использованным токеном уже редиректит на `/login?error=reset_expired`.

Telegram-сообщение (для reply): 4918

## Контекст / зачем

После LFPT-0499 просроченная, уже использованная или несуществующая ссылка сброса пароля из письма ведёт на `/login?error=reset_expired` вместо падения 500. Но `login.html` сейчас не различает этот случай: любой `error`, кроме `not_confirmed` и `rate-limit`, попадает в общую ветку «Email o contraseña incorrectos» — пользователь, перешедший по просроченной ссылке, видит сообщение о неверном пароле, хотя он даже не пытался войти, и не понимает, что нужно запросить новую ссылку.

## Связь с MASTER.md

Не затрагивает инварианты `specs/MASTER.md` — флоу сброса пароля там не описан отдельно. Чисто фронтенд-доработка отображения, без изменения бизнес-правил.

## Требования

### Функциональные

1. `templates/login.html`: для `?error=reset_expired` — отдельный алерт (в том же визуальном стиле, что `rate-limit`/`not_confirmed`: иконка + заголовок + текст), а не общий «неверный email или пароль».
2. В этом алерте — кнопка/ссылка «Запросить новую» (`login.alert.reset_expired.btn`), которая открывает уже существующую модалку запроса сброса пароля (`showPasswordResetModal()` из `password-reset.js`) — ту же, что открывается по ссылке «¿Olvidaste tu contraseña?».
3. Общий алерт «неверный email или пароль» (`param.error[0] != 'not_confirmed' and != 'rate-limit'`) должен исключать `reset_expired`, чтобы для этого случая не показывались оба алерта одновременно.
4. Остальные значения `?error=` (включая отсутствующие/неизвестные) — поведение не меняется, продолжают попадать в общий алерт.

### Нефункциональные

- i18n: новые ключи `login.alert.reset_expired.title`, `login.alert.reset_expired.msg`, `login.alert.reset_expired.btn` — в `messages.properties` (дефолт, значение = es), `messages_es.properties`, `messages_ru.properties`, `messages_en.properties`. Без хардкода текста в шаблоне.
- Безопасность: новой точки входа данных от пользователя нет (кнопка просто открывает уже существующую форму email для запроса сброса).

## Вне скоупа

- Любые изменения `password-reset.js` сверх exposing `showPasswordResetModal` в `window` (нужно только чтобы кнопка в алерте могла её вызвать — сейчас это приватная функция модуля, вызываемая только из клика по `.forgot-password`).
- Поведение самого backend-редиректа (`PasswordResetController`) — уже реализовано в #425/LFPT-0499, не трогаем.
- Любые другие значения `?error=` кроме `reset_expired`, `not_confirmed`, `rate-limit`.

## Изменения в системе

### API
Без изменений — чисто фронтенд-шаблон и JS.

### БД
Без миграций.

### UI

- `src/main/resources/templates/login.html`: новая ветка `th:if="${param.error != null and param.error[0] == 'reset_expired'}"` (по аналогии с существующими блоками `rate-limit`/`not_confirmed`, та же CSS-разметка inline-стиля), с кнопкой, вызывающей `showPasswordResetModal()`. Условие общего алерта расширяется третьим исключением.
- `src/main/resources/static/js/password-reset.js`: `showPasswordResetModal` становится доступной глобально (`window.showPasswordResetModal = showPasswordResetModal;`), по аналогии с уже существующими `window.hidePasswordResetModal`/`window.sendPasswordResetRequest` и т.д.
- `src/main/resources/i18n/messages{,_es,_ru,_en}.properties`: три новых ключа (см. выше).

## Критерии приёмки

- [ ] `GET /login?error=reset_expired` с `Accept-Language: es` показывает только новый алерт (заголовок + текст про устаревшую/использованную ссылку + кнопку «Solicitar nueva»), без алерта «Email o contraseña incorrectos».
- [ ] То же с `Accept-Language: ru` — текст на русском.
- [ ] То же с `Accept-Language: en` — текст на английском.
- [ ] Клик по кнопке в новом алерте открывает модалку запроса сброса пароля (`#passwordResetModal`), ту же, что открывается по «¿Olvidaste tu contraseña?».
- [ ] `GET /login?error=not_confirmed` и `GET /login?error=rate-limit` — поведение не изменилось (прежние алерты, без нового блока).
- [ ] `GET /login?error=<любое другое значение>` (например `bad_credentials`) — по-прежнему общий алерт «неверный email/пароль», без нового блока.
- [ ] `GET /login` без `error` — ни один из трёх условных алертов не показывается.

## Edge cases

- `error` присутствует, но без значения (`?error`) — Spring Security передаёт параметр `error` всегда со значением при стандартном `failureUrl`; специальный тест не нужен, покрывается общим случаем "другое значение".
- Повторный переход по той же просроченной ссылке (второй `GET /recuperar-password?token=...`) — каждый раз просто редиректит на `/login?error=reset_expired` заново (read-only проверка токена на backend, не меняет его статус) — поведение не новое для этой задачи, фиксируется как наблюдение.

## Открытые вопросы

Нет открытых бизнес-вопросов — формулировка сообщения и его UI-паттерн уже заданы в issue (используем существующий визуальный паттерн алертов `rate-limit`/`not_confirmed` и существующую модалку сброса пароля), решения принимаются по аналогии с уже реализованным кодом.
