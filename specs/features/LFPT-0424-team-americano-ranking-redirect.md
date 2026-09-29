# LFPT-0424: Fix ошибка 500 на странице рейтинга Team Americano — редирект на страницу турнира

## Статус
approved

## Источник
GitHub issue: https://github.com/UstinKO/padel-core-service/issues/424. Клиентского запроса в `specs/requests/` нет — спека написана напрямую по issue. Находка из анализа фронта: `docs/frontend-react/contract/findings.md`, F-01.

## Контекст / зачем
- `GET /tournaments/team-americano/{id}/ranking` → `TeamAmericanoViewController#viewRanking` (строки 89–101) возвращает view `tournaments/team-americano/ranking`. Такого шаблона нет, поэтому `TemplateInputException` и 500.
- Роут публичный: он есть в `permitAll` в `SecurityConfig.java:90`, то есть падает и у анонимов.
- Рейтинг команд уже показывается на `/tournaments/team-americano/{id}` (`view.html`, блок `ranking-card`). Отдельный экран не нужен.
- **Технический выбор:** редирект на страницу турнира вместо создания нового шаблона.
- Родственные задачи: #330, #335 (тот же класс бага — несуществующий шаблон; этот роут в них не упомянут).

## Связь с MASTER.md
Инварианты не меняются. Правка чисто навигационная, бизнес-логика `TeamAmericanoService.getRanking` не трогается. Изменений в MASTER.md не требуется.

## Требования

### Функциональные
1. `viewRanking` возвращает `redirect:/tournaments/team-americano/{id}` (HTTP 302). Модель не собирается, в БД и сервисы метод не ходит.
2. Существование турнира в методе не проверяется — этим занимается целевая страница `/{id}`, отсюда и поведение «как у `/{id}`» для несуществующего турнира.

### Нефункциональные
- i18n: новых строк нет.
- Производительность: запросов к БД становится меньше (метод больше не читает турнир и рейтинг).
- Безопасность: новых точек входа нет, правило `permitAll` для `/tournaments/team-americano/*/ranking` остаётся как есть (без него аноним получил бы редирект на `/login` вместо редиректа на турнир).

## Вне скоупа
- JSON-эндпоинт `GET /tournaments/team-americano/api/{id}/ranking` — не меняется. Используется в `admin/americano/tournament-double.html:495`.
- Создание шаблона `tournaments/team-americano/ranking.html` — осознанно отклонённый вариант (см. «Контекст»).
- Обработка несуществующего турнира на `/{id}`: сейчас там `IllegalArgumentException("Torneo no encontrado")`, `GlobalExceptionHandler` его не ловит — будет 500. Для `/ranking` поведение станет «как у `/{id}`», то есть тоже 500, но уже после редиректа. Это не регрессия этой задачи.

## Изменения в системе

### API
- `GET /tournaments/team-americano/{id}/ranking` — было 500, стало 302 на `/tournaments/team-americano/{id}`. URL, метод и path-параметр не меняются.

### БД
Нет изменений. Новая Liquibase-миграция не требуется.

### UI
Новых шаблонов нет. Ссылок на `/tournaments/team-americano/{id}/ranking` как на страницу нет ни в шаблонах, ни в JS (проверено `grep` по `src/main/resources/templates` и `src/main/resources/static/js`).

## Критерии приёмки
- [ ] `GET /tournaments/team-americano/{id}/ranking` (существующий турнир) → 302, `Location` = `/tournaments/team-americano/{id}`, в логах нет `TemplateInputException`.
- [ ] Тот же запрос анонимно → тот же 302, без редиректа на `/login`.
- [ ] Несуществующий `id` → 302 на `/tournaments/team-americano/{id}`, дальше поведение идентично прямому заходу на `/{id}`.
- [ ] MockMvc-тест на `viewRanking` (сейчас тестов на этот метод нет).
- [ ] JSON `GET /tournaments/team-americano/api/{id}/ranking` работает как раньше (регресс).

## Edge cases
- Нечисловой `id` (`/abc/ranking`) → `MethodArgumentTypeMismatchException`, его ловит `GlobalExceptionHandler` и делает `redirect:/`. Поведение прежнее.
- Турнир другого типа (например, обычный Americano): редирект на `/tournaments/team-americano/{id}`, который рендерит team-americano view без проверки типа турнира — поведение идентично прямому заходу на `/{id}`, в рамках задачи не меняется.
- Конкурентный доступ не применим: запрос только на чтение, без состояния.

## Открытые вопросы
Нет. Выбран 302 (как в LFPT-314/316): 301 кэшируется браузером навсегда, а 302 оставляет возможность в будущем вернуть отдельную страницу рейтинга.
