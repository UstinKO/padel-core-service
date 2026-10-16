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

1. Принимает `PaymentFormWrapper` (список параллельных массивов из form inputs).
2. Итерирует по `registrationIds` — восстанавливает `PaymentManagementViewDto` из разрозненных массивов по индексу.
3. Определяет `attended` и `participationConfirmed` по наличию `registrationId` в соответствующих списках (чекбоксы в HTML-форме).
4. Парсит сумму в `BigDecimal`, статусы и методы в enum.
5. Передаёт в `PaymentService.savePaymentManagementData(tournamentId, updates, ownerId)`.

### Контракт (form params)

| Поле | Тип | Описание |
|------|-----|----------|
| `registrationIds` | `List<Long>` | ID регистраций (anchor для остальных полей) |
| `paymentIds` | `List<String>` | ID существующих платежей (null если нового) |
| `hasPayments` | `List<String>` | `"true"/"false"` — есть ли платёж |
| `partnerRows` | `List<String>` | `"true"/"false"` — строка партнёра в парном турнире |
| `attended` | `List<Long>` | ID регистраций отметивших посещение (чекбоксы) |
| `participationConfirmed` | `List<Long>` | ID регистраций с подтверждённым участием |
| `amounts` | `List<String>` | Суммы платежей |
| `currencies` | `List<String>` | Валюты (ARS, etc.) |
| `paymentStatuses` | `List<String>` | `PaymentStatus` enum-строки |
| `paymentMethods` | `List<String>` | `PaymentMethod` enum-строки |
| `transactionIds` | `List<String>` | Номера транзакций |
| `notes` | `List<String>` | Заметки |

**Ответ**: редирект на `/admin/tournaments/{tournamentId}/payments` с flash `successMessage` или `errorMessage`.

### Замечания (ревью)

> **[DESIGN — сложность]** Форма использует паттерн параллельных массивов — хрупкий и трудно поддерживаемый способ передачи данных. При добавлении нового поля нужно синхронизировать индексы во всей форме и контроллере. Лучше использовать `List<PaymentUpdateDto>` с вложенными объектами (Spring умеет их биндить в Thymeleaf через `th:field`).

> **[BUG]** Если `paymentIds`, `paymentStatuses` и другие массивы имеют разную длину с `registrationIds` (возможно при ошибках формы), логика по индексу `i < formWrapper.getXxx().size()` тихо пропустит данные без ошибки.

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

## Эндпоинты из CLAUDE.md (существуют но не в PaymentManagementController)

### GET `/payments/{registrationId}`

По CLAUDE.md рендерит `admin/tournaments/payments.html`. По коду — этот путь не найден в `PaymentManagementController` (здесь пути `/admin/tournaments/{tournamentId}/payments`). Возможно, старый/устаревший маппинг.

### POST `/payments/{registrationId}/checkout`

Аналогично — не найден в исследованных контроллерах. Вероятно, отдельный контроллер оплаты или неработающий эндпоинт.

### Замечания (ревью)

> **[MINOR]** Несоответствие между документацией в CLAUDE.md и фактическими путями. Нужно найти или задокументировать `/payments/{registrationId}` контроллер.
