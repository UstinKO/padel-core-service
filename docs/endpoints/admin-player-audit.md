# API: Аудит дублей игроков — `/api/admin/players`

**Контроллер:** `AdminPlayerAuditController`
**Назначение:** разовая read-only диагностика потенциальных дублей записей `PlayerPadel` (LFPT-0677). Ничего не меняет и не удаляет в данных — только считает и возвращает отчёт.

---

## GET `/api/admin/players/duplicates-audit`

**Аутентификация:** `@PreAuthorize("hasRole('SUPER_ADMIN')")` — строже, чем у соседних admin-эндпоинтов (`AdminMetricsController` пускает ещё и `ORGANIZER`), потому что ответ отдаёт персональные данные (имя, email, телефон, Telegram) всех игроков, у которых нашлось совпадение.

Делегирует в `PlayerDuplicatesAuditService.buildReport()`. Аннотирован `@Timed`/`@Counted` (`admin.players.duplicates.audit.*`).

### Алгоритм

1. Загружает всех игроков (`PlayerRepository.findAll()`) и id игроков, чья собственная регистрация на парный турнир создана как партнёрская сторона приглашения (`TournamentRegistrationRepository.findPartnerInviteCreatedPlayerIds()` — `isDoubleRegistration = true AND mainPlayerId <> player.id`; единственный путь, создающий такое сочетание — `DoubleTournamentRegistrationService.confirmPartnerRegistration()`).
2. **Точные дубли** (`exactDuplicates`) — группы из 2+ записей с совпадающим:
   - телефоном после нормализации (убрать все нецифровые символы, взять последние 10 цифр — чтобы `+54 9 11 2233-4455` и `011 2233-4455` считались одним номером; эвристика, не полноценный парсер E.164 — libphonenumber не добавлялся, см. спеку);
   - либо `telegramUsername` без учёта регистра.
3. **Вероятные дубли** (`probableDuplicates`) — группы из 2+ записей с совпадающим именем+фамилией (lowercase, схлопнутые пробелы), **за исключением** групп, целиком покрытых каким-либо `exactDuplicates`-группой (чтобы одна и та же пара не попадала в обе категории).
4. **`partnerFlowSuspects`** — все игроки из п.1 (созданные через флоу подтверждения партнёра), независимо от п.2/3.
5. **`sourceBreakdown`** — каждый игрок классифицируется ровно в одну категорию по приоритету: `ADMIN_GUEST` (email на `@1padel.guest`) → `OAUTH2` (`oauth2User = true`) → `PARTNER_INVITE` (из п.1) → `SELF_REGISTERED` (всё остальное — не подтверждённый факт самостоятельной регистрации, а категория "не распознано иначе").
6. **`summary`** — `totalRecords` (все строки `player_padel_db`), `duplicateGroupsCount`/`uniquePlayersEstimate`/`recordsPerPlayerDistribution` считаются по связным компонентам среди всех `exactDuplicates` ∪ `probableDuplicates` (если A совпал с B по телефону и с C по имени, A/B/C — один компонент из 3 записей, а не два отдельных пересекающихся дубля).

### Контракт

**Response 200 OK** `DuplicatesAuditReportDto`:
```json
{
  "summary": {
    "totalRecords": 540,
    "duplicateGroupsCount": 12,
    "uniquePlayersEstimate": 525,
    "recordsPerPlayerDistribution": { "2": 10, "3": 1, "4+": 1 }
  },
  "exactDuplicates": [
    {
      "matchType": "PHONE",
      "matchKey": "1122334455",
      "members": [
        { "id": 101, "nombre": "Juan", "apellido": "Perez", "email": "juan@mail.com",
          "telefono": "+5491122334455", "telegramUsername": null,
          "guestAccount": false, "oauth2User": false },
        { "id": 204, "nombre": "Juan", "apellido": "Perez", "email": "juan.p@mail.com",
          "telefono": "01122334455", "telegramUsername": null,
          "guestAccount": false, "oauth2User": false }
      ]
    }
  ],
  "probableDuplicates": [
    {
      "matchType": "NAME",
      "matchKey": "maria lopez",
      "members": [ "..." ]
    }
  ],
  "partnerFlowSuspects": [ "..." ],
  "sourceBreakdown": {
    "SELF_REGISTERED": 480,
    "PARTNER_INVITE": 40,
    "ADMIN_GUEST": 15,
    "OAUTH2": 5
  }
}
```

**Response 403 Forbidden** — аутентифицирован, но без роли `SUPER_ADMIN`:
```json
{ "success": false, "message": "Acceso denegado" }
```

Анонимный запрос — редирект на `/login` (стандартное поведение form-login цепочки Spring Security для `/api/**` без отдельной `AuthenticationEntryPoint`).

### Ограничения

- Разовый расчёт без кэша и без пагинации — на текущем масштабе данных проекта (сотни-тысячи игроков) не требуется; если объём вырастет на порядки, нужна отдельная задача.
- Нормализация телефона и имени — эвристики, не идеальны (см. "Алгоритм" и edge cases спеки `specs/features/LFPT-0677-аудит-дублей-игроков.md`).
- `sourceBreakdown.SELF_REGISTERED` — это "не распознано как другая категория", а не подтверждённый факт самостоятельной регистрации.
- Эндпоинт только читает — не меняет и не удаляет данные (явное ограничение заказчика на этом этапе, см. спеку).

### Замечания (ревью)

> **[закрыто, сверено с кодом 2026-10-20]** Эндпоинт новый (LFPT-0677) — выше описана вся его логика, расхождений с кодом на дату написания нет.
