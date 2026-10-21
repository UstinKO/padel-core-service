# LFPT-0688: Форма парной регистрации: Telegram вместо email, подсказки партнёра у активного поля на мобильном (frontend)

## Статус
done

## Источник
Клиентский запрос [specs/requests/LFPT-0687-partner-telegram-bez-email.md](requests/LFPT-0687-partner-telegram-bez-email.md) (Telegram, `telegram_launch_message_id: 5407`, отправитель customer) — тот же запрос, что и у бэкенд-задачи. GitHub issue [#688](https://github.com/UstinKO/padel-core-service/issues/688). Зависит от бэкенда [#687](https://github.com/UstinKO/padel-core-service/issues/687) (поле `partnerTelegram`, немедленная регистрация без ожидания подтверждения) — реализуется в том же прогоне конвейера, веткой от бэк-ветки.

## Контекст / зачем

Модалка парной регистрации (`tournament-details.html` и идентичная по разметке/ID модалка в `players/dashboard.html`), режим «Tengo compañero» / `WITH_PARTNER`, при ручном вводе данных партнёра показывает поле «Email del compañero (opcional)» и не показывает Telegram — хотя у координатора нет реального способа связаться с партнёром по email: связь всегда идёт по телефону (WhatsApp) или Telegram. Подсказки «Возможно, ваш партнёр уже зарегистрирован» (LFPT-0680, только в `tournament-details.html` — `dashboard.html` этой фичи не получал) выводятся одним блоком под всеми тремя полями ручного ввода — на мобильном блок уходит ниже видимой области экрана.

Ответ заказчика на открытые вопросы бэкенд-спеки (#687) закрыл и пункт 4 клиентского запроса про текст статуса "Compañero Invitado": отдельная кнопка подтверждения координатором не нужна, пара теперь формируется мгновенно и получает обычный статус `CONFIRMED`/`WAITLIST` — отдельного текста для промежуточного состояния не требуется, фронтенд-изменение по этому пункту не нужно (см. «Вне скоупа»).

## Связь с MASTER.md

Не меняет инвариантов напрямую — наследует обновление `specs/MASTER.md` § «Флоу парной регистрации», сделанное в #687 (мгновенная регистрация без ожидания). Эта спека — чисто UI-слой над уже изменённым бэкендом.

## Требования

### Функциональные

1. **Убрать поле «Email del compañero»** из `manualPartnerFields` в обоих шаблонах (`tournament-details.html`, `players/dashboard.html`) — `#partnerEmail` и его использование в JS (`tournament-details.js`, `dashboard.js`).
2. **Добавить поле «Telegram del compañero (opcional)»** на его месте — по аналогии с `partnerPhone`: необязательное, без строгой валидации формата (как у `PlayerContactController.updateContact`). `selectPartner`/`dashSelectPartner` — заполнять это поле из `player.telegramUsername`, если партнёр найден по поиску.
3. **Подсказки ручного ввода рядом с активным полем** (только `tournament-details.html` — в `dashboard.html` этой подсказки нет вообще, портировать её туда не входит в скоуп). `manualPartnerSuggestions` становится плавающим блоком (`position:fixed`, как `partnerSearchResults`), позиционируется JS-функцией `positionManualDropdown()` относительно поля, которое пользователь сейчас редактирует (`manualActiveInput`, обновляется на `input`/`focus` трёх полей ручного ввода), а не статично под всеми тремя полями.
4. **Payload регистрации** (`POST /api/tournaments/double/{tournamentId}/register`) — отправлять `telegramUsername` вместо `email` (поле `email` в бэкенд-DTO остаётся, но форма его больше не собирает и не отправляет).

### Нефункциональные

- **i18n**: новые ключи `modal.partner.field.telegram`, `.placeholder`, `.hint` — во все три `messages_{es,ru,en}.properties`, заменяют удалённые `modal.partner.field.email*`.
- **Безопасность**: без изменений — поле передаётся через тот же авторизованный путь, что и `partnerPhone` сейчас.
- **Мобильная вёрстка**: `positionManualDropdown()` зеркалит уже проверенную логику `positionDropdown()` (расчёт доступного места сверху/снизу от `getBoundingClientRect()`), включая репозиционирование при скролле модалки.

## Вне скоупа

- Текст статуса "Compañero Invitado"/`PARTNER_INVITED` — ответ заказчика на #687 показал, что отдельный текст/кнопка не нужны (пара формируется мгновенно, получает обычный `CONFIRMED`/`WAITLIST`). Существующий легаси-блок с этим статусом в `admin/tournaments/details.html` и `tournament-details.html` остаётся как есть — он корректен для уже существующих legacy-регистраций и больше не создаётся для новых.
- Чекбокс «Я согласовал участие с партнёром» — заказчик предложил как необязательное дополнение ("если нужно"); решение — не добавлять: без серверного принуждения он не несёт функциональной нагрузки, а лишний шаг в форме противоречит самой цели задачи (убрать трение при регистрации уже согласованной пары).
- Перенос подсказок LFPT-0680 в `players/dashboard.html` — там их не было и до этой задачи; добавление новой функциональности не входит в скоуп (убираем email/добавляем Telegram там же, где они уже есть сейчас — только 2 поля, без подсказок).
- Удаление поля `partnerEmail`/`partner_email` из бэкенда — решено в #687, поле остаётся в модели для обратной совместимости.
- Секция `searchSection`/`addLaterSection` (SEARCH/ADD_LATER режимы) — не затронуты этой задачей.

## Изменения в системе

### API
Без изменений контракта эндпоинтов — `telegramUsername` уже добавлено в `PartnerRegistrationDto` в #687; эта задача — только то, что отправляет браузер.

### БД
Нет (чисто фронтенд).

### UI
- `src/main/resources/templates/tournament-details.html` — замена поля email→Telegram, плавающий блок подсказок.
- `src/main/resources/templates/players/dashboard.html` — замена поля email→Telegram.
- `src/main/resources/static/js/tournament-details.js` — `selectPartner`, `clearPartnerBtn`, `handleDoubleRegistration`, новая `positionManualDropdown()`, трекинг активного поля.
- `src/main/resources/static/js/dashboard.js` — `dashSelectPartner`, `dashClearBtn`, `openPartnerModal`, `submitPartnerRegistration`.
- `src/main/resources/i18n/messages_{es,ru,en}.properties` — новые ключи `modal.partner.field.telegram*`.

## Критерии приёмки

- [ ] `tournament-details.html`: модалка парной регистрации, ручной ввод — показывается поле «Telegram del compañero (opcional)», поля email нет.
- [ ] То же для модалки на `players/dashboard.html`.
- [ ] Регистрация нового партнёра (телефон + Telegram, без email) через UI завершается успешно одним действием — без промежуточного экрана ожидания.
- [ ] Поиск существующего партнёра по имени/телефону/Telegram, выбор из списка — поле Telegram в скрытой форме заполняется из `telegramUsername` найденного игрока (регрессия на уже работающий поиск не допущена).
- [ ] На мобильном вьюпорте (375×667) подсказки «Возможно, ваш партнёр уже зарегистрирован» при вводе в любое из трёх полей появляются рядом с этим полем (видны без прокрутки), а не уходят за экран.
- [ ] Подписи полей/плейсхолдеры/хинты — на RU/ES/EN (`Accept-Language`).
- [ ] `python3 .github/bin/docs-drift.py` — 0 расхождений (документация не затронута — чисто UI).

## Edge cases

- Партнёр указал и телефон, и Telegram вручную — оба отправляются на бэкенд (независимо, как и раньше с телефоном/email).
- Партнёр без Telegram — поле пустое, `telegramUsername: null` в payload, бэкенд это уже поддерживает (#687).
- Подсказки открыты у одного поля, пользователь переключается на другое поле того же блока — дропдаун должен переместиться к новому активному полю, а не остаться у старого.
- Скролл модалки, когда подсказки открыты — позиция пересчитывается (как и у `partnerSearchResults`).

## Открытые вопросы

Нет — все вопросы, относящиеся к этой задаче, были частью открытых вопросов #687 и сняты ответом заказчика (см. #687, раздел «Открытые вопросы»).
