# API: Управление платежами — `/admin/tournaments/{tournamentId}/payments`

**Контроллер:** `PaymentManagementController`  
**Назначение:** Просмотр и ручное управление платёжными данными участников турнира (отметка оплаты, посещения, подтверждение участия). Работает через Thymeleaf-форму — не REST.

---

## GET `/admin/tournaments/{tournamentId}/payments`

**Тип:** Thymeleaf view.  
**Аутентификация:** Требуется (admin-панель), `SUPER_ADMIN`, `ADMIN`, `ORGANIZER`, `CLUB_ADMIN` с правом управления конкретным турниром.

### Алгоритм

1. Загружает `TournamentDto` по `tournamentId`.
2. Загружает список участников с платёжными данными через `PaymentService.getPaymentManagementData(tournamentId)`.
3. Рендерит `admin/tournaments/payments.html`.

Верхняя сводка по валютам учитывает только платежи со статусом `PAID`. Условия показа карточек валют также проверяют этот статус: неоплаченная сумма не создаёт карточку. Отдельный оплаченный платёж партнёра учитывается наравне с основным; пустая валюта считается ARS.

### Атрибуты модели

| Атрибут | Тип | Описание |
|---------|-----|----------|
| `tournament` | TournamentDto | Данные турнира |
| `players` | `List<PaymentManagementViewDto>` | Участники с платёжным статусом |
| `paymentMethods` | `PaymentMethod[]` | Допустимые методы: CASH, BANK_TRANSFER, BY_BIT, BINANCE, TARJETA, TRANSFERENCIA, OTHER. |
| `paymentStatuses` | `PaymentStatus[]` | PENDING / PAID / REFUNDED |

---

## POST `/admin/tournaments/{tournamentId}/payments/save`

**Тип:** Form submit.  
**Аутентификация:** `@AuthenticationPrincipal Owner` — только Owner.

### Алгоритм

1. Проверяет доступ к турниру (`TournamentAccessService.assertCanManageTournament`).
2. Принимает `PaymentUpdateForm` — список строк `payments[i].*` (`PaymentRowForm`, по объекту на строку таблицы).
3. Парсит сумму в `BigDecimal`, id, статусы и методы в enum; ошибки парсинга логируются с номером строки.
5. Передаёт в `PaymentService.savePaymentManagementData(tournamentId, updates, ownerId)`.

### Контракт (form params)

Каждая строка — `payments[i].<поле>`:

| Поле | Тип | Описание |
|------|-----|----------|
| `registrationId` | Long | ID регистрации |
| `paymentId` | String | ID существующего платежа (пусто — новый) |
| `hasPayment` | boolean | Есть ли платёж |
| `partnerRow` | boolean | Строка партнёра в парном турнире |
| `attended` | boolean | Посетил |
| `participationConfirmed` | boolean | Участие подтверждено |
| `amount` | String | Сумма |
| `currency` | String | Валюта (ARS, …) |
| `paymentStatus` | String | `PaymentStatus` |
| `paymentMethod` | String | `PaymentMethod` |
| `transactionId` | String | Номер транзакции |
| `notes` | String | Заметки |

**Ответ**: редирект на `/admin/tournaments/{tournamentId}/payments` с flash `successMessage` или `errorMessage`.

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-16]** Параллельные массивы заменены списком объектов `PaymentRowForm`.

> **[закрыто, сверено с кодом 2026-10-16]** Рассинхронизации длин больше нет — каждая строка формы самодостаточна.

> **[MINOR]** `NumberFormatException` при парсинге суммы логируется как warn и тихо игнорируется — платёж сохранится без суммы.

---

## Модель `PaymentManagementViewDto`

| Поле | Тип | Описание |
|------|-----|----------|
| `registrationId` | Long | ID регистрации |
| `paymentId` | Long | ID платежа (null если нет) |
| `hasPayment` | boolean | Создан ли платёж |
| `partnerRow` | boolean | Строка относится к партнёру |
| `attended` | boolean | Присутствовал на турнире |
| `participationConfirmed` | boolean | Подтверждено участие |
| `amount` | BigDecimal | Сумма |
| `currency` | String | Валюта |
| `paymentStatus` | PaymentStatus | PENDING / PAID / REFUNDED |
| `paymentMethod` | PaymentMethod | CASH / BANK_TRANSFER / BY_BIT / BINANCE / TARJETA / TRANSFERENCIA / OTHER |
| `transactionId` | String | Номер транзакции |
| `notes` | String | Заметки |

---

> Путей `/payments/{registrationId}` и `/payments/{registrationId}/checkout` в коде нет: платёжного шлюза нет, оплаты фиксирует администратор (сверено 2026-10-16).

## Измерение рекламы

При включённой интеграции Meta положительное увеличение накопительной суммы PAID записывает денежное событие в транзакционную очередь. Цена турнира не заменяет сумму оплаты. Повторы старой формы без paymentId переиспользуют активную платёжную строку под блокировкой регистрации. Для наличных на месте (CASH) фиксируется physical_store, для переводов/прочих/неизвестного способа — other; источник уже созданного поступления не меняется при редактировании платежа. Частичная/полная оплата, пары, валюты, возвраты, дедупликация и экспорт — [контракт Meta](meta-marketing.md).

## Одиночные заявки — `/admin/tournaments/{eventId}/participants`

Контроллер `SoloRosterController`, сервис `SoloRosterService`. Все запросы проверяют действующий аккаунт Owner и полномочия на конкретный турнир; запись требует CSRF. Назначения координаторов не вводятся.

| Метод | Маршрут | Результат |
|---|---|---|
| GET | `/admin/tournaments/{eventId}/participants` | Активные неполные заявки, снятые заявки с оплатами, журнал |
| GET | `/admin/tournaments/{eventId}/participants/search` | Поиск по `q`: имя, фамилия, телефон, Telegram, текущая заявка/пара |
| POST | `/admin/tournaments/{eventId}/participants/preview` | Участники, исходные оплаты/явка, итоговый статус, предупреждение об уровне, контроль состояния |
| POST | `/admin/tournaments/{eventId}/participants` | Выполнение ADD/PAIR/REMOVE |

Команда `SoloRoster.Command`: UUID `requestId`, действие, `registrationId`, необязательный `partnerRegistrationId` или `player` (id существующего либо имя/фамилия/телефон/Telegram гостя), `kind` SEARCH/ADD_LATER, `overrideLevel`, `expectedState` из превью. Повтор того же UUID и полезной нагрузки возвращает прежний результат. Изменённая нагрузка или устаревшее превью — 409 ProblemDetail с локализованной причиной.

Операция блокирует турнир; формирование пары сохраняет регистрационные ID, платежи, валюты, подтверждения и явку. Меняется связь текущих платежей с booking ID; исторические платежи других составов сохраняются. Неполная заявка не становится командой для игры. Завершённое круговое расписание и плей-офф, а также затронутые назначенные/сыгранные матчи защищены. Ответ содержит номера записей матчей и отсылает к штатной корректировке состава. Снятие сохраняет профиль, платёж и историю отмены; возврат автоматически не создаётся. Журнал `solo_roster_operations_db` фиксирует автора и состояние до/после.

Таблица оплат включает активные SOLO_SEARCH/SOLO_ADD_LATER, резерв и сохранённую неполную основу. Поля `solo`, `registrationStatus`, `playerLevel` позволяют показать неполный состав. Для старого текстового гостя явка сохраняется отдельно в `partner_attended`.

### Обычный интерфейс одиночных заявок

Блок «Игроки без пары» при первом открытии свёрнут, заголовок показывает количество заявок. Выбранное состояние сохраняется для данного турнира в текущей вкладке, в том числе после сохранения оплат. Это настройка отображения без изменения регистраций, платежей и явки.

Блок «Игроки без пары» доступен на той же странице оплат всем действующим управляющим ролям для разрешённого турнира. Отдельная техническая панель не требуется. Поиск предлагает существующий профиль, одиночную заявку турнира или гостя без обязательного email. Превью показывает обе оплаты, явку и итоговый статус пары; при допустимом ручном исключении по уровню необходимо явное подтверждение.

Текущие несохранённые отметки таблицы сначала нужно сохранить. При ошибке поля остаются, при повторном запросе сохраняется UUID. Снятые участники и журнал доступны в сворачиваемом разделе. Новые блоки и сообщения переведены RU/ES/EN; текст кнопок переносится на узких экранах. Явка обозначается как индивидуальная.
