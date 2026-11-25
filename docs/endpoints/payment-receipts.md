# Чеки мероприятий

Доступ: OWNER, SUPER_ADMIN, ORGANIZER, ADMIN, CLUB_ADMIN плюс право управления конкретным турниром через TournamentAccessService. Изменения требуют сессии и CSRF.

| Метод | Путь | Результат |
|---|---|---|
| GET | `/api/admin/tournaments/{tournament}/receipts` | Список id, registrationId, registrationVersion, playerId, playerName, eventName, contentType, state, createdAt, reviewedAt, rejectionReason, paymentId, currentCycle |
| GET | `/api/admin/tournaments/{tournament}/receipts/{id}/file` | Файл вложением, no-store, nosniff; без Telegram URL |
| POST | `/api/admin/tournaments/{tournament}/receipts/{id}/accept` | `{amount,currency,method}` → обновлённый чек; ручное подтверждение существующим PaymentService |
| POST | `/api/admin/tournaments/{tournament}/receipts/{id}/reject` | `{reason}` → отклонённый чек, причина 1–500 символов |

Загрузка в бота всегда требует явного выбора мероприятия. Она не создаёт Payment. Повтор принятия возвращает тот же чек без нового платежа. Несовпадающая существующая оплата требует ручной сверки; файл старого цикла нельзя зачесть за повторную запись. Ошибки: RECEIPT_UNAVAILABLE, RECEIPT_INVALID_FILE, RECEIPT_PAYMENT_CONFLICT в ProblemDetail.

Приём закрыт по умолчанию (`TELEGRAM_PLAYERS_RECEIPTS_ENABLED=false`), сохраняет пилот и `/stop`. Поддерживаются фото JPEG/PNG и PDF до 5 МиБ. Выбор живёт в БД 20 минут, токен бота не попадает в ответы и исключения. После решения бот пишет игроку отдельное сообщение; блокировка/отключение бота не откатывает платёж.

В странице оплат чек открывается компактной кнопкой со статусом в строке игрока. Окно показывает
файлы конкретной регистрации и прежние действия проверки; таблица не раздвигается карточками.
Счётчик над таблицей включает фильтр игроков с ожидающими чеками текущего цикла. Кнопка общего
списка открывает историю, включая снятых участников. Фильтр сохраняет поля формы платежей;
несохранённые изменения блокируют принятие, а неопределённый ответ требует перезагрузки.
Закрытие окна и Escape возвращают фокус. Тексты и действия доступны на ES/RU/EN.
