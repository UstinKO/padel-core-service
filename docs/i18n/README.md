# Мультиязычность: план реализации

## Контекст

- Основной язык — испанский (Аргентина)
- Добавляем: русский (`ru`) и английский (`en`)
- Механизм: **auto-detect** через `Accept-Language` заголовок браузера — без кнопки-переключателя
- Переводится: пользовательская сторона + admin-панель + email-шаблоны

## Этапы

### UI (пользовательская сторона) ✅ Этапы выполнены (покрытие — см. «Текущее состояние»)

| № | Этап | Файл | Оценка |
|---|------|------|--------|
| 1 | Инфраструктура Spring + структура файлов | [stage-1-infra.md](stage-1-infra.md) | 1-2 ч |
| 2 | Ключи: шаблоны → messages.properties | [stage-2-template-keys.md](stage-2-template-keys.md) | 3-4 ч |
| 3 | Ключи: JS → messages-xx.js | [stage-3-js-keys.md](stage-3-js-keys.md) | 2-3 ч |
| 4 | Переводы: ru + en (UI) | [stage-4-translations.md](stage-4-translations.md) | 3-4 ч |
| 5 | Интеграция: замена строк в HTML шаблонах | [stage-5-templates.md](stage-5-templates.md) | 4-5 ч |
| 6 | Интеграция: замена строк в JS файлах | [stage-6-js.md](stage-6-js.md) | 2-3 ч |
| 7 | QA и тестирование UI | [stage-7-qa.md](stage-7-qa.md) | 1-2 ч |

### Email (письма игрокам) ✅ Этапы выполнены (покрытие — см. «Текущее состояние»)

| № | Этап | Файл | Оценка |
|---|------|------|--------|
| 8 | Инфраструктура email i18n: поле `preferredLocale` в профиле, EmailService | [stage-8-email-infra.md](stage-8-email-infra.md) | 2-3 ч |
| 9 | Ключи email шаблонов + переводы ru/en | [stage-9-email-keys.md](stage-9-email-keys.md) | 3-4 ч |
| 10 | Интеграция: замена строк в email шаблонах | [stage-10-email-templates.md](stage-10-email-templates.md) | 2-3 ч |

### Admin-панель (16 шаблонов + 5 JS файлов) ✅ Этапы выполнены (покрытие — см. «Текущее состояние»)

| № | Этап | Файл | Оценка |
|---|------|------|--------|
| 11 | Ключи: 16 admin HTML шаблонов → messages_es.properties (`admin.*`) | [stage-11-admin-html-keys.md](stage-11-admin-html-keys.md) | 3-4 ч |
| 12 | Ключи: 5 admin JS файлов → messages-es.js | [stage-12-admin-js-keys.md](stage-12-admin-js-keys.md) | 1-2 ч |
| 13 | Переводы: ru + en для всех `admin.*` ключей | [stage-13-admin-translations.md](stage-13-admin-translations.md) | 3-4 ч |
| 14 | Интеграция: замена строк в 16 admin HTML шаблонах | [stage-14-admin-templates.md](stage-14-admin-templates.md) | 4-5 ч |
| 15 | Интеграция: замена строк в 5 admin JS файлах | [stage-15-admin-js.md](stage-15-admin-js.md) | 1-2 ч |
| 16 | QA: тестирование admin-панели в трёх языках | [stage-16-admin-qa.md](stage-16-admin-qa.md) | 1-2 ч |

**Итого UI + Email + Admin: ~36-52 часа (выполнено)**

## Текущее состояние

Все 16 этапов реализованы — но перевод делался по списку файлов на момент плана. **Полного покрытия нет**: UI, добавленный позже (Team Americano, Team Playoff, клубные аккаунты, доработки King of Court, email-напоминания), шёл с хардкодом на испанском, и при `ru`/`en` видно смешение языков.

**Аудит 2026-10-09 (LFPT-396)** — `specs/i18n-audit-2026-10.md` (в git): полный список файл:строка:текст и задачи на исправление:

| Раздел | Строк | Задача |
|---|---|---|
| Публичные страницы и кабинет игрока | 72 | #532 |
| Admin-панель | 85 | #533 |
| Americano / Team Americano / Team Playoff / King of Court | 72 | #534 |
| JS-файлы `static/js` | 23 | #535 |
| Email-шаблоны | 78 | #536 |
| Сообщения из Java (flash, JSON, исключения) | 257 | #537 (зависит от #504) |

**Как поддерживается полнота дальше:** правило «новый пользовательский текст = ключ в es/ru/en в том же PR» — в `.claude/CODE_STYLE.md` §6.4 и чек-листе `.claude/CODE_REVIEW.md` §7; автоматическая проверка шаблонов тестом с baseline — #538. Пока #538 не влита, полнота держится только на ревью.

- `src/main/resources/i18n/messages_es.properties` / `_ru` / `_en` — публичный UI + email + `admin.*` ключи
- `src/main/resources/static/js/i18n/messages-{es,ru,en}.js` — JS строки публичного UI и admin-панели
- Известный пробел: `test/tournaments.html` (dev-инструмент) и `legal/*.html` намеренно не переведены (см. "Что НЕ переводим" ниже)

---

## Структура файлов (целевое состояние)

```
src/main/resources/
└── i18n/
    ├── messages.properties        ← fallback (дублирует es)
    ├── messages_es.properties     ← испанский (основной)
    ├── messages_ru.properties     ← русский
    └── messages_en.properties     ← английский

src/main/resources/static/js/
└── i18n/
    ├── t.js                       ← helper функция t(key)
    ├── messages-es.js             ← испанские строки для JS
    ├── messages-ru.js             ← русские строки для JS
    └── messages-en.js             ← английские строки для JS
```

## Scope: что переводим

### Admin HTML шаблоны (16 файлов, ~472 строки текста)

| Шаблон | Строк |
|--------|-------|
| `admin/tournaments/details.html` | ~75 |
| `admin/americano/tournament-playoff.html` | ~65 |
| `admin/tournaments/form.html` | ~47 |
| `admin/americano/tournament.html` | ~32 |
| `admin/players/details.html` | ~29 |
| `admin/tournaments/payments.html` | ~27 |
| `admin/americano/tournament-double.html` | ~27 |
| `admin/tournaments/list.html` | ~26 |
| `admin/americano/preview-double-rounds.html` | ~24 |
| `admin/americano/preview-rounds.html` | ~22 |
| `admin/clubs/list.html` | ~21 |
| `admin/players/list.html` | ~20 |
| `admin/clubs/details.html` | ~17 |
| `admin/panel.html` | ~16 |
| `admin/clubs/form.html` | ~13 |
| `admin/tournaments/king-of-court.html` | ~11 |

### Admin JS файлы (5 файлов, ~61 строка)

| Файл | Строк |
|------|-------|
| `king-of-court.js` | ~29 |
| `admin-americano.js` | ~18 |
| `admin.js` | ~12 |
| `payment-management.js` | ~1 |
| `admin-menu.js` | ~1 |

### Публичный UI HTML шаблоны (17 файлов, ~436 строк текста)

| Шаблон | Строк |
|--------|-------|
| `index.html` | ~69 |
| `tournament-details.html` | ~69 |
| `players/dashboard.html` | ~57 |
| `tournaments/team-playoff/view.html` | ~36 |
| `tournaments/team-americano/view.html` | ~27 |
| `ranking.html` | ~25 |
| `registro.html` | ~22 |
| `tournaments/americano/view.html` | ~23 |
| `torneos.html` | ~29 |
| `players/perfil.html` | ~24 |
| `king-of-court-view.html` | ~16 |
| `waitlist-confirmation.html` | ~9 |
| `clubs/view.html` | ~9 |
| `clubs/list.html` | ~7 |
| `login.html` | ~10 |
| `accept-pair.html` | ~3 |
| `error/rate-limit.html` | ~1 |

### JS файлы (14 файлов, ~100-120 реальных UI-строк)

| Файл | Назначение |
|------|------------|
| `dashboard.js` | уведомления, ошибки регистрации на турнир |
| `tournament-details.js` | статусы матчей, ошибки |
| `home.js` | фильтрация, загрузка |
| `torneos.js` | фильтры, пагинация |
| `auth.js` | валидация формы регистрации |
| `password-reset.js` | сброс пароля |
| `profile.js` | обновление профиля |
| `king-of-court-viewer.js` | статусы игры |
| `contact-check.js` | проверка контакта |
| `cookie-consent.js` | баннер cookie |
| `ranking.js` | загрузка рейтинга |
| `main.js` | общие UI-сообщения |
| `notifications.js` | тосты |
| `share-panel.js` | шаринг |

### Что НЕ переводим

- `test/tournaments.html` — dev-инструмент, только для организатора
- `legal/*.html` — огромный объём текста, deferred

## Соглашения по именованию ключей

### Публичный UI
```
common.*        — общие кнопки/лейблы (Guardar, Cancelar, Cargando...)
nav.*           — навигация
login.*         — страница логина
registro.*      — регистрация
dashboard.*     — дашборд игрока
perfil.*        — профиль
torneos.*       — список турниров
tournament.*    — детали турнира
koc.*           — king of court (viewer)
americano.*     — americano view
ranking.*       — рейтинг
clubs.*         — клубы
waitlist.*      — лист ожидания
error.*         — страницы ошибок
cookie.*        — cookie consent
```

### Admin-панель
```
admin.common.*           — общие элементы admin (кнопки, колонки таблиц)
admin.nav.*              — admin навигация
admin.panel.*            — главная панель (/admin)
admin.tournaments.*      — управление турнирами (list/form/details)
admin.koc.*              — King of Court admin
admin.payments.*         — управление платежами
admin.americano.*        — Americano admin
admin.playoff.*          — Playoff admin
admin.preview.*          — Preview раундов
admin.clubs.*            — управление клубами
admin.players.*          — управление игроками
```

Пример: `admin.tournaments.form.field.name`, `admin.koc.error.save`, `admin.common.col.status`
