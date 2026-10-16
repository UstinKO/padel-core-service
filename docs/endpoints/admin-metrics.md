# API: Метрики администратора — `/api/admin`

**Контроллер:** `AdminMetricsController`  
**Назначение:** Эндпоинты для мониторинга статистики email-рассылок. Используется дашбордом Grafana и при ручной диагностике.

---

## GET `/api/admin/email-stats`

**Аутентификация:** Не указана (`@PreAuthorize` отсутствует).

Аннотирован `@Timed` и `@Counted` — метрики времени и числа обращений уходят в Prometheus.

### Алгоритм

Делегирует в `EmailMetricsService.getEmailStats()` и возвращает агрегат.

### Контракт

**Response 200 OK** `Map<String, Object>`:
```json
{
  "dailyCount":      42,
  "dailyLimit":      300,
  "totalSent":       1250,
  "sentByType": {
    "BIENVENIDA":             10,
    "CONFIRMACION":           8,
    "TOURNAMENT_CONFIRMATION": 15,
    "VACANCY_INVITATION":     5,
    "RECUPERAR_PASSWORD":     4
  },
  "totalErrors":     2,
  "totalRejected":   1
}
```

(точная структура зависит от `EmailMetricsService.getEmailStats()` — нужно сверить с реализацией)

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")`. Исходное замечание: Нет авторизации. Статистика email-рассылок (типы писем, счётчики) доступна без аутентификации. Нужен `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")`.

---

## GET `/api/admin/email-daily-count`

**Аутентификация:** Не указана.

Аннотирован `@Timed`.

### Контракт

**Response 200 OK** `Map<String, Object>`:
```json
{
  "dailyCount":        42,
  "dailyLimit":        300,
  "remainingQuota":    258,
  "usagePercentage":   14.0
}
```

### Замечания (ревью)

> **[SECURITY — закрыто, сверено с кодом 2026-10-16]** `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")`. Исходное замечание: Нет авторизации — аналогично выше.

> **[MINOR]** `usagePercentage` считается как `double` деление: потенциальное `ArithmeticException` если `dailyLimit = 0` (division by zero). Нужна защита.
