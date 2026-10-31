# API: Управление платежами — `/admin/tournaments/{tournamentId}/payments`

**Контроллер:** `PaymentManagementController`  
**Назначение:** Просмотр и ручное управление платёжными данными участников турнира (отметка оплаты, посещения, подтверждение участия). Работает через Thymeleaf-форму — не REST.

---

## GET `/admin/tournaments/{tournamentId}/payments`

**Тип:** Thymeleaf view.  
**Аутентификация:** Требуется (admin-панель), предположительно `ROLE_OWNER` / `ROLE_SUPER_ADMIN`.

### Алгоритм

1. Загружает `TournamentDto` по `tournamentId`.
2. Загружает список участников с платёжными данными через `PaymentService.getPaymentManagementData(tournamentId)`.
3. Рендерит `admin/tournaments/payments.html`.

### Атрибуты модели

| Атрибут | Тип | Описание |
|---------|-----|----------|
| `tournament` | TournamentDto | Данные турнира |
| `players` | `List<PaymentManagementViewDto>` | Участники с платёжным статусом |
| `paymentMethods` | `PaymentMethod[]` | Допустимые методы: CASH, TRANSFER, MERCADO_PAGO, etc. |
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
| `paymentMethod` | PaymentMethod | CASH / TRANSFER / MERCADO_PAGO |
| `transactionId` | String | Номер транзакции |
| `notes` | String | Заметки |

---

> Путей `/payments/{registrationId}` и `/payments/{registrationId}/checkout` в коде нет: платёжного шлюза нет, оплаты фиксирует администратор (сверено 2026-10-16).

## Измерение рекламы

При включённой интеграции Meta положительное увеличение накопительной суммы PAID записывает денежное событие в транзакционную очередь. Цена турнира не заменяет сумму оплаты. Повторы старой формы без paymentId переиспользуют активную платёжную строку под блокировкой регистрации. Частичная/полная оплата, пары, валюты, возвраты, дедупликация и экспорт — [контракт Meta](meta-marketing.md).
