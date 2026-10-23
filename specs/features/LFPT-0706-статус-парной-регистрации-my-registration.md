# LFPT-0706: Статус парной регистрации в 1Padel.App — расширить GET my-registration

## Статус
approved

## Источник
Часть клиентского запроса [specs/requests/LFPT-0707-парная-запись-americano-1padel-app.md](../requests/LFPT-0707-парная-запись-americano-1padel-app.md) (Telegram, отправитель — customer, переслано от Evgeny_754, `telegram_launch_message_id: 5463`), разбита на бэкенд-задачу (эта спека) и фронтенд-задачу (LFPT-0707, зависит от этой). Issue: [#706](https://github.com/UstinKO/padel-core-service/issues/706).

## Контекст / зачем

Фронтенд-задача LFPT-0707 подключает парную запись Americano/Team Americano внутри 1Padel.App. Экрану парной записи нужен, по аналогии с индивидуальной записью (LFPT-0660), способ узнать **свой** статус регистрации на конкретное событие одним запросом: подтверждена/в листе ожидания/позиция/дата/признак парной регистрации — не только булев факт "зарегистрирован".

Эндпоинт `GET /players/tournaments/{tournamentId}/my-registration` (`PlayerDashboardController.getMyRegistration`, используется сайтом) уже загружает `TournamentRegistrationDto` со всеми нужными полями (`id`, `status`, `waitlistPosition`, `registrationDate`, `isDoubleRegistration`), но отдаёт в JSON только часть: `registered`, `partnerId`, `partnerFirstName`, `partnerLastName`, `hasRegisteredPartner`. Нужно добавить оставшиеся поля в тот же ответ — без новых запросов к БД, без изменения существующих полей.

## Связь с MASTER.md

Новых инвариантов не вводится. Используются существующие:
- Статусы регистрации (`RegistrationStatus`, раздел «Статусы регистрации на турнир») — отдаются как есть, строкой enum, без изменения семантики.
- Контактные данные партнёра (`telefono`/`telegramUsername`) по-прежнему не публикуются этим эндпоинтом — ограничение видимости не меняется.

MASTER.md не меняется.

## Требования

### Функциональные

1. `GET /players/tournaments/{tournamentId}/my-registration` при `registered: true` дополнительно возвращает:
   - `registrationId` — `TournamentRegistrationDto.getId()`
   - `status` — `TournamentRegistrationDto.getStatus()` (сериализуется как строка имени enum, как и везде в проекте)
   - `waitlistPosition` — `TournamentRegistrationDto.getWaitlistPosition()` (может быть `null`)
   - `registrationDate` — `TournamentRegistrationDto.getRegistrationDate()` (может быть `null`, хотя на практике всегда заполнена при создании регистрации)
   - `isDoubleRegistration` — `TournamentRegistrationDto.getIsDoubleRegistration()` (может быть `null` → трактуется потребителем как `false`, не меняем существующую семантику поля)
2. При `registered: false` ответ не меняется — `{"registered": false}`, без новых полей (ветка `registration.isEmpty()` возвращает раньше, новых полей там взять не из чего).
3. Существующие поля (`partnerId`, `partnerFirstName`, `partnerLastName`, `hasRegisteredPartner`) и их семантика не меняются — только добавление новых ключей в тот же `Map`.
4. Контактные данные партнёра (`partnerPhone`/`partnerTelegram` из `TournamentRegistrationDto`) в ответ не добавляются — сознательное ограничение видимости, как и сейчас.

### Нефункциональные

- **Производительность**: не применимо — ни одного нового запроса к БД, поля берутся из уже загруженного `registration.get()`.
- **Совместимость**: аддитивное изменение (новые ключи в JSON-объекте) — существующий потребитель (JS сайта, если он читает этот ответ) не может сломаться от появления новых полей, которые он не читает.

## Вне скоупа

- Любые изменения `GET /api/players/me/tournaments` (список "Мои мероприятия") — не трогается, фронтенд-задача LFPT-0707 работает по конкретному турниру через этот эндпоинт.
- Публикация контактов партёра — не входит, намеренное ограничение сохраняется.
- Сам экран парной записи и серверные мутации регистрации — предмет LFPT-0707 и уже существующих эндпоинтов, не этой задачи.

## Изменения в системе

### API
Изменён (аддитивно, без breaking changes): `GET /players/tournaments/{tournamentId}/my-registration`.

### БД
Нет изменений.

### Код
- `src/main/java/com/padle/core/padelcoreservice/controller/player/PlayerDashboardController.java` — метод `getMyRegistration`, добавить 5 полей в `Map<String, Object> response`.

### Документация
- `docs/endpoints/player-actions.md` (или аналогичный файл с описанием `my-registration`, если заведён отдельно — сверить по карте `docs/Home.md § Как поддерживать актуальность`) — обновить описание формы ответа.

## Критерии приёмки

- [ ] `GET /players/tournaments/{tournamentId}/my-registration` для зарегистрированного игрока возвращает `registrationId`, `status`, `waitlistPosition`, `registrationDate`, `isDoubleRegistration` вместе с уже существующими полями
- [ ] Для игрока без активной регистрации ответ — `{"registered": false}`, без новых полей
- [ ] `partnerPhone`/`partnerTelegram` в ответе отсутствуют (как и раньше)
- [ ] Новый юнит/интеграционный тест проверяет форму ответа для `CONFIRMED`, `WAITLIST` (с позицией) и для отсутствующей регистрации
- [ ] `./mvnw verify` зелёный
- [ ] `docs/endpoints/...` обновлён, `python3 .github/bin/docs-drift.py` — 0 расхождений

## Edge cases

- `waitlistPosition`/`registrationDate`/`isDoubleRegistration` равны `null` в БД (старые/нетипичные записи) — сериализуются как JSON `null`, не отбрасываются и не превращаются в ошибку.
- Регистрация существует, но принадлежит легаси-статусу (`PARTNER_INVITED`) — поля отдаются как есть, без специальной обработки; интерпретация статуса — задача клиента (LFPT-0707), не этого эндпоинта.

## Открытые вопросы

Нет. Чисто аддитивное расширение существующего, уже протестированного эндпоинта на основе уже загруженных данных.
