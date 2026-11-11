# LFPT-0944: Feature backend UI v2 — публичная веб-карточка мероприятия и серверные метаданные

## Статус
done

## Источник
[Issue #944](https://github.com/UstinKO/padel-core-service/issues/944), напрямую из согласованного анализа #907.

## Контекст / зачем
Публичному React-экрану нужны состояние проведения, точный вариант парного формата, организатор и разрешённые ссылки. Мобильный DTO и прежние ответы ошибок сохраняются.

## Связь с MASTER.md
Никаких изменений правил регистрации, вместимости, резерва и оплаты. Фаза читается из сохранённого проведения; контакты участников не раскрываются. До инициализации парного проведения, когда вариант ещё не сохранён, API явно возвращает TEAM_PAIRS, не угадывает будущий round-robin или playoff.

## Требования
- GET `/api/public/events/{id}`: `{event, organizer, format, phase, actions, canonicalUrl, calendarUrl, pngUrl}`. `event` — прежний безопасный публичный DTO с именами участников. Organizer содержит только уже публичный контакт события и проверенную WhatsApp-ссылку, без Owner/Player entity.
- Форматы KING_OF_COURT, AMERICANO, TEAM_PAIRS (не определён), TEAM_AMERICANO (round-robin), TEAM_PLAYOFF, CANCHA_ABIERTA, PADEL_CLINIC. Для пар использовать сохранённые фазы раундов; phase=null у round-robin не смешивать с QUALIFICATION/PLAYOFF.
- Фазы NOT_STARTED, LIVE, QUALIFICATION, PLAYOFF, FINISHED, CANCELLED; финальные статусы имеют приоритет. Не определять проведение по наступлению даты.
- Действия только переходы чтения: условия участия на действующей карточке при открытой регистрации, live/результаты при существующем проведении, экспорт календаря. Серверные ссылки не дают права на мутацию; регистрацию и eligibility по-прежнему проверяют существующие операции.
- `pngUrl` отсутствует/null до появления публичного серверного контракта экспорта. Никаких админских PNG URL и YouTube.
- Серверный HTML для preview-события/клуба содержит title, description, canonical, OG, noindex и X-Robots-Tag; ссылки основаны на app.base-url, не на Host запроса. Клиентские данные не сериализуются в DOM.
- Сохранить загрузочные script/link теги Vite. Неизвестный id — реальный 404, сбой хранилища — 503; HTML-экранирование защищает метаданные от инъекции. Остальные SPA-маршруты сохраняют forward-поведение.
- Production allowlist остаётся пустым; поддержка метаданных для боевых URL активируется только отдельным переключением. Preview всегда SUPER_ADMIN.
- Ошибки нового API — локализованный ProblemDetail; API мобильного расписания не меняется.

## Вне скоупа
Live snapshots/ranking/history (#903), новые правила участия, новые экспорты, миграции БД, React-экраны (#907), переключение production URL.

## Изменения в системе
### API
Новый PublicEventController/типизированный DTO и сервис публичного чтения; точечный Security allowlist. Новый контракт описывается в OpenAPI.
### БД
Новых таблиц нет; bounded read/exists запросы к сохранённым раундам и KingOfCourt.
### UI
Серверные head-метаданные для shell; сама карточка остаётся #907.
### Документация
`docs/endpoints/public-schedule.md`, `docs/frontend-react/contract/api.md`, `docs/frontend-react/adr/0008-seo-public-pages.md` (статус решения с подтверждённым планом), `docs/auth-flow.md`, `docs/architecture.md`, `docs/domain-overview.md`, `.agents/PROJECT.md`.

## Критерии приёмки
- [x] Анонимный GET нового API возвращает только безопасные поля; прежние мобильные тесты проходят.
- [x] Различаются round-robin/playoff и ещё не определённое проведение; live/finished/cancelled не угадываются по дате.
- [x] Нет контактов игроков, Owner/createdBy/paymentInstructions и YouTube.
- [x] Исходный HTML содержит корректные canonical/OG и экранированные строки; head assets сохранены, preview noindex.
- [x] 404/503, права preview, скрытые события, типы/фазы и read-only actions покрыты тестами.
- [x] `./mvnw verify`, docs-drift, git diff --check проходят на отдельной тестовой БД.

## Edge cases
Неизвестный/неактивный/слишком большой id, отсутствие необязательного контакта, нецифровой контакт, скрытое мероприятие, отсутствие frontend shell, символы HTML/кавычки в названии, заголовок Host от клиента, незапущенное парное проведение.

## Открытые вопросы
Нет новых бизнес-решений: используются существующие публичные поля и сохранённые состояния. Технический порядок backend → frontend подтверждён пользователем.

## Проверка
Полный `./mvnw verify`: 864 теста, без ошибок и сбоев, 1 пропущен. Выделенная тестовая PostgreSQL; Surefire постоянно ограничивает тестовый Hikari pool до 4 соединений (minimum-idle=0) и кеш до 8 Spring-контекстов. Проверены API/совместимость, фазы, права preview, метаданные события/клуба и HEAD, экранирование/Host, 404/503. docs-drift и git diff --check проходят.

К PublicClubResponse обратно совместимо добавлен canonicalUrl из app.base-url; внешний websiteUrl клуба сохраняет прежний смысл.

После исправления CI выполнен обычный `clean verify` с профилем test, без временных pool/cache-настроек runner: 864 теста, 0 ошибок/сбоев, 1 пропущен. `logback-test.xml` исключает Loki/Telegram из тестового логирования, сохраняя консоль и маскирование; production logging не меняется. Проверки автоматики: .github/bin — 6, relay — 48, worker — 5 (1 пропущен), agent-migration и docs-drift без расхождений.
