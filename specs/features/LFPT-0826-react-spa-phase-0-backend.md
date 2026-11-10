# LFPT-0826: Feature backend — инфраструктура React SPA Phase 0

## Статус
approved

## Источник
[GitHub issue #826](https://github.com/UstinKO/padel-core-service/issues/826), техническая постановка без отдельного клиентского запроса.

Pipeline-Task: manual-20261105T112045Z

Связанная frontend-задача: [#827](https://github.com/UstinKO/padel-core-service/issues/827), зависит от этой задачи.

## Контекст / зачем
UI v2 должен подключаться к существующему Spring Boot монолиту постепенно, не переключая действующие Thymeleaf-маршруты одним релизом. Backend Phase 0 создаёт безопасную границу для будущей React-сборки: отдельную статику `/web/**`, закрытый предпросмотр `/preview/**`, выключенный по умолчанию allowlist production-маршрутов и типизированный контракт текущей web-сессии.

В текущем коде `/app/**` уже занят анонимно доступной Expo-сборкой 1Padel.App (`MobileAppViewController`, `static/app/**`) и используется Google OAuth-флоу мобильного клиента. Старые предложенные `docs/frontend-react/README.md`, `plan.md` и ADR-0003 всё ещё назначают этот префикс будущей SPA; для этой задачи это документационный drift. Принятое в issue решение `/web/**` и `/preview/**` имеет приоритет и должно быть отражено в актуальной документации.

## Связь с MASTER.md
Фича использует раздел MASTER «Роли пользователей»: доступ к предпросмотру имеет только `ROLE_SUPER_ADMIN`. Существующие способы web-входа (серверная сессия и remember-me) и мобильной аутентификации (JWT) сохраняются раздельными.

Бизнес-правила турниров, регистраций, резервов, платежей и уведомлений не затрагиваются. Инварианты `specs/MASTER.md` не меняются, обновление MASTER не требуется.

## Требования

### Функциональные

#### 1. Конфигурация и маршрутизация SPA

- Добавить типизированную конфигурацию `app.spa.routes` как список Spring path patterns. Значение по умолчанию во всех версионируемых конфигурациях — пустой список.
- Пустой `app.spa.routes` не должен регистрировать SPA-forward для `/`, `/torneos`, `/torneo/**`, `/clubs/**`, `/admin/**`, `/players/**` или любого другого действующего production-маршрута. Не должно быть глобального fallback «любой неизвестный URL → SPA».
- Каждый непустой элемент allowlist должен быть абсолютным непустым path pattern, начинающимся с `/`. Конфигурация должна отклонять catch-all `/**` и маршруты из зарезервированных серверных пространств `/api/**`, `/ws/**`, `/actuator/**`, `/oauth2/**`, `/login/oauth2/**`, `/logout`, `/app/**`, `/web/**` и `/preview/**`.
- Только `GET` и `HEAD` явно включённого SPA-маршрута могут быть направлены на `/web/index.html`. Query string не участвует в выборе маршрута. Остальные HTTP-методы продолжают обрабатываться существующими контроллерами и правилами Spring Security.
- Авторизация включённого production-маршрута определяется его существующими серверными правилами. Allowlist не создаёт `permitAll` и не ослабляет защиту `/admin/**`, `/players/**` и других закрытых URL.
- Добавить forwarding `GET|HEAD /preview` и `GET|HEAD /preview/**` на `/web/index.html`, включая deep links. Произвольные URL вне `/preview/**` и allowlist не форвардятся.

#### 2. Статические ресурсы React

- Зарезервировать `/web/**` для статических файлов React-сборки из `classpath:/static/web/`. Создание `frontend/`, сборка pnpm/Vite и упаковка артефактов в этот каталог относятся к #827.
- `/web/index.html` должен отдаваться без долгого кеширования (`Cache-Control: no-cache`), а versioned-файлы из `/web/assets/**` — с долгим immutable-кешированием. Отсутствующий файл возвращает обычный `404`, а не SPA fallback.
- `/web/**` может быть анонимно доступен как статический код, но сборка не должна содержать пользовательские данные, session/JWT-токены, секреты, приватные адреса или runtime-конфигурацию сервера. Защита `/preview/**` является защитой маршрута и API-данных, а не попыткой скрыть загружаемый JavaScript.
- Существующие `MobileAppViewController`, `/app`, `/app/**`, `static/app/**`, OAuth redirect мобильного клиента и их текущие права не изменяются.
- Статические запросы `/web/**` не должны расходовать application rate-limit bucket.

#### 3. Доступ к preview

- `/preview` и `/preview/**` доступны только пользователю с фактическим authority `ROLE_SUPER_ADMIN`.
- Анонимный запрос проходит существующий browser login flow и не получает содержимое `/web/index.html` через preview URL.
- Аутентифицированные `ROLE_PLAYER`, `ROLE_ORGANIZER`, `ROLE_ADMIN` и `ROLE_CLUB_ADMIN` получают существующий отказ для browser-страниц и не получают содержимое preview.
- Правило preview должно находиться в `SecurityConfig` до более широких matcher-правил. Проверка роли выполняется сервером независимо от клиентских route guards.
- Статические assets не являются каналом данных: любые будущие персональные данные preview получает только через отдельно авторизованные API.

#### 4. Контракт текущей web-сессии

- Добавить `GET /api/auth/session` для same-origin React-клиента. Эндпоинт читает текущий `SecurityContext`/remember-me сессию и не выпускает JWT.
- Ответ всегда типизирован одним Java `record` и имеет поля:
  - `authenticated: boolean`;
  - `userType: PLAYER | OWNER | null`;
  - `id: long | null`;
  - `fullName: string | null`;
  - `email: string | null`;
  - `roles: string[]` — фактические authority в формате `ROLE_*`;
  - `clubId: long | null` — только для `Owner`, включая `CLUB_ADMIN`;
  - `hasValidContact: boolean | null` — только для `PlayerPadel`;
  - `preferredLocale: es | ru | en | null` — только для `PlayerPadel`.
- Для анонимного запроса возвращается `200 OK`, `authenticated=false`, пустой `roles`, остальные nullable-поля равны `null`. Это позволяет SPA загрузить публичный shell без обработки аутентификации как сетевой ошибки.
- Для `PlayerPadel` и `Owner` возвращается `200 OK` с данными соответствующего principal. Роль `ROLE_OWNER`, которой нет в `OwnerRole`, искусственно не добавляется.
- Ответ содержит `Cache-Control: no-store`. В нём не должно быть пароля/хеша, телефона, Telegram, JWT access/refresh token, remember-me token, `JSESSIONID`, CSRF-токена или полей JPA-сущности сверх указанного контракта.
- Существующие JWT-эндпоинты `/api/auth/login`, `/api/auth/refresh`, `/api/auth/me` и их мобильные потребители не меняют контракт в рамках этой задачи.

#### 5. CSRF и типизированные ошибки нового API

- Сохранить double-submit контракт: сервер выдаёт cookie `XSRF-TOKEN` с `HttpOnly=false`, same-origin клиент передаёт её значение в `X-XSRF-TOKEN` для `POST`, `PUT`, `PATCH` и `DELETE`.
- Чтение `GET /api/auth/session` должно инициировать выдачу/обновление `XSRF-TOKEN` существующим `csrfCookieFilter`; токен не дублируется в JSON-ответе.
- Не добавлять новое CSRF-исключение для SPA. Текущее широкое исключение `/api/auth/**` сузить до реально stateless JWT-операций `/api/auth/login` и `/api/auth/refresh`; существующее исключение запросов с Bearer `Authorization` и `/ws/**` сохранить.
- Session-cookie мутации, включая существующий `POST /logout`, без корректного CSRF-токена продолжают отклоняться с `403`; с парой cookie/header проходят CSRF-проверку.
- Добавить общий контракт ошибок для новых SPA API на основе Spring `ProblemDetail` (`application/problem+json`) с полями `type`, `title`, `status`, `detail`, `instance` и стабильным расширением `code`. Технический stack trace, имя Java-исключения и секретные данные в ответ не попадают.
- В этой задаче не переводить все существующие legacy API на `ProblemDetail`: их миграция выполняется вместе с соответствующими экранами. Новый session endpoint и последующие SPA API должны использовать только новый контракт, если у них появляется ошибочный ответ.

#### 6. Тесты

- Добавить unit-тесты биндинга и валидации `app.spa.routes`: отсутствующее свойство и пустой список безопасны; валидный pattern принимается; catch-all и зарезервированные пространства отклоняются при старте контекста.
- Добавить MVC/integration-тест с пустым allowlist, доказывающий, что действующие Thymeleaf-маршруты не форвардятся на `/web/index.html`, а произвольный неизвестный URL остаётся `404`/обрабатывается текущей error-механикой.
- Добавить MVC/integration-тест с отдельным тестовым SPA-pattern: только явно включённый `GET`/deep link форвардится на `/web/index.html`; соседний URL, `/api/**`, `/app/**` и мутирующий запрос не перехватываются.
- Проверить матрицу preview: аноним, `ROLE_PLAYER`, все существующие owner-роли без SUPER_ADMIN и `ROLE_SUPER_ADMIN`; отдельно проверить `/preview` и deep link `/preview/path/to/screen`.
- Проверить одновременную доступность независимых ресурсов `/app/index.html` и тестового `/web/index.html` без подмены одного другим.
- Проверить session endpoint для anonymous, `PlayerPadel`, `Owner`/`CLUB_ADMIN`: точную форму JSON, фактические roles, `clubId` и player-флаги, `Cache-Control: no-store`, отсутствие токенов/пароля/контактов.
- Проверить, что ответ session endpoint устанавливает `XSRF-TOKEN`, а session-cookie мутация без CSRF получает `403` и проходит с валидной парой cookie/header.
- Проверить `ProblemDetail` нового API: media type, HTTP status, стабильный `code`, `instance` и отсутствие stack trace.

### Нефункциональные

- **Безопасность:** deny-by-default для production SPA-маршрутов; серверная проверка `ROLE_SUPER_ADMIN` на preview; отсутствие секретов в статике и session DTO; неизменная CSRF-защита cookie-сессии.
- **Совместимость:** существующие Thymeleaf-страницы, Expo `/app/**`, JWT mobile API, form login, Google OAuth2 и входящие ссылки не меняют поведение.
- **Кеширование:** HTML shell нельзя закреплять долгим кешем; хешированные assets допускают immutable cache. Session response — только `no-store`.
- **Производительность:** маршрутизация использует неизменяемую конфигурацию, загруженную при старте, без запросов к БД на каждый route lookup. Статические assets обслуживаются стандартным resource chain.
- **Масштабирование:** новая маршрутизация не хранит пользовательское состояние в памяти приложения. Текущее session-хранилище не изменяется.
- **Java:** source/release 17; новые request/response/config value objects — `record`, где это совместимо с Spring; без `Map<String, Object>` и `ResponseEntity<?>` для нового контракта.
- **i18n:** новых пользовательских строк UI нет. `ProblemDetail.code` является машинным кодом; локализованный UI-текст добавит #827 на ES/RU/EN.

## Вне скоупа

- Создание `frontend/`, перенос UI 0.7, pnpm/Vite, React Router, i18n, TanStack Query, browser UI-тесты и интеграция frontend-сборки в Maven/Docker/CI — задача #827.
- Переключение хотя бы одного production-маршрута на React и добавление непустого `app.spa.routes` в production-конфигурацию.
- Изменение или удаление Thymeleaf-контроллеров и шаблонов.
- Изменение Expo-приложения, `/app/**`, его OAuth/JWT-флоу или артефактов `static/app/**`.
- JSON-ответы form login `/login`, подключение OpenAPI/springdoc, генерация TypeScript-клиента и массовый перевод legacy API/ошибок на `ProblemDetail`, если это отдельно не включено в #827 или следующую backend-задачу.
- Изменение бизнес-правил, ролей, регистрации, резервов, турниров, платежей, БД или Liquibase.
- Публичные SEO/OG-маршруты и динамическая серверная подстановка meta-тегов.

## Изменения в системе

### API

- Новый `GET /api/auth/session` — типизированное состояние текущей same-origin web-сессии; анонимный и авторизованный варианты имеют одну схему, ответ `no-store`, JWT отсутствует.
- Общий `ProblemDetail`-контракт и стабильные коды для новых SPA API без массового изменения legacy endpoints.
- CSRF-контракт документируется и покрывается тестами; исключение `/api/auth/**` сужается до JWT login/refresh.

### БД

Изменений entity, таблиц, enum и Liquibase нет.

### UI

- Backend-forward `/preview` и `/preview/**` на будущий `/web/index.html` только для `ROLE_SUPER_ADMIN`.
- Backend-forward только явно включённых `app.spa.routes`; список по умолчанию пуст.
- Зарезервирована независимая статическая зона `/web/**`; `/app/**` остаётся Expo-приложением без изменений.

### Конфигурация и безопасность

- Новая типизированная конфигурация `app.spa.routes` с безопасным пустым default и валидацией зарезервированных путей.
- `SecurityConfig`: role matcher preview, анонимная статика `/web/**`, сужение CSRF-исключения без ослабления существующих защищённых URL.
- `RateLimitFilter`: `/web/**` добавляется к исключённым статическим ресурсам.

### Документация

- `docs/frontend-react/README.md`, `plan.md` и актуальное ADR перехода: заменить устаревшее назначение `/app/**` на `/web/**`, описать `/preview/**`, пустой allowlist и то, что `/app/**` занят Expo. ADR-0003 пометить заменённым принятым решением, не оставлять два действующих взаимоисключающих контракта.
- `docs/frontend-react/contract/api.md` и `cross-cutting.md`: точная схема `/api/auth/session`, CSRF и границы ProblemDetail для Phase 0.
- `docs/auth-flow.md`: исправить drift относительно кода (CSRF включён), добавить session endpoint и preview access; не менять документированный мобильный JWT-флоу `/app/**`.
- `docs/endpoints/auth.md`: добавить `/api/auth/session`, исправить устаревшие замечания о текущем `/api/auth/me`, если они расходятся с кодом.
- `docs/domain-overview.md`, `docs/endpoints/view-controllers.md` и URL-таблицы `.agents/PROJECT.md`: добавить `/web/**`, `/preview/**`, `/api/auth/session`, свойства и ограничения маршрутизации.
- `docs/architecture.md`: описать coexistence Thymeleaf/React и безопасный route switch; build pipeline остаётся предметом #827.

## Критерии приёмки

- [ ] При отсутствии свойства или пустом `app.spa.routes` ни `/`, ни один действующий пользовательский/admin URL, ни неизвестный URL не форвардится на `/web/index.html`.
- [ ] При тестовом allowlist только явно указанный `GET`/deep link форвардится на `/web/index.html`; catch-all и зарезервированные `/api/**`, `/app/**`, `/web/**`, `/preview/**` не могут быть настроены как production SPA-route.
- [ ] Аноним и любой пользователь без `ROLE_SUPER_ADMIN` не получает содержимое preview; `ROLE_SUPER_ADMIN` открывает `/preview` и `/preview/**` deep link через forward на `/web/index.html`.
- [ ] `/web/**` и `/app/**` обслуживаются независимо; существующие `/app`, `/app/**`, Expo assets и OAuth/JWT mobile flow не изменены.
- [ ] `/web/index.html` имеет `Cache-Control: no-cache`, `/web/assets/**` — immutable cache, session response — `Cache-Control: no-store`; `/web/**` исключён из application rate limiting.
- [ ] `GET /api/auth/session` возвращает `200` и одну типизированную схему для anonymous, `PlayerPadel` и `Owner`; поля соответствуют разделу требований и фактическим authorities.
- [ ] Session response не содержит JWT/refresh/remember-me/session/CSRF token, пароль/хеш, телефон, Telegram или лишние поля entity.
- [ ] `GET /api/auth/session` выдаёт CSRF-cookie `XSRF-TOKEN`; session-cookie мутация без `X-XSRF-TOKEN` отклоняется `403`, с корректным токеном проходит; JWT login/refresh и Bearer-клиенты сохраняют текущую совместимость.
- [ ] Ошибки нового SPA API имеют `application/problem+json`, корректный HTTP status, стабильный `code` и не раскрывают stack trace/Java exception/секреты; legacy API массово не изменены.
- [ ] Обновлены перечисленные технические документы: в актуальном контракте нет утверждения, что React SPA занимает `/app/**`, а `docs/auth-flow.md` соответствует фактически включённому CSRF.
- [ ] `python3 .github/bin/docs-drift.py`, `git diff --check` и `./mvnw verify` проходят.

## Edge cases

- Пустой, пробельный, относительный или зарезервированный route pattern останавливает запуск с понятной ошибкой конфигурации; он не игнорируется молча.
- Дубли в `app.spa.routes` не создают дублирующиеся mappings и не приводят к ambiguous handler; конфигурация нормализуется в уникальный неизменяемый список.
- `/preview`, `/preview/` и произвольная глубина deep link ведут в один shell; файл с точкой под `/web/**` ищется как ресурс и при отсутствии получает `404`.
- Query string и fragment не расширяют allowlist. Fragment браузер не отправляет серверу.
- Если `/web/index.html` ещё не упакован, preview/allowlisted route не должен случайно перейти на Thymeleaf или `/app/index.html`; до выполнения #827 допустим контролируемый `404` для отсутствующего ресурса.
- Remember-me аутентификация даёт тот же session DTO, что и обычная `JSESSIONID`-сессия.
- Неизвестный или неподдерживаемый principal не сериализуется как entity и не раскрывает `principal.toString()`; применяется безопасный ProblemDetail либо анонимный контракт согласно выбранной серверной политике.
- Две реплики приложения читают одинаковый route allowlist из конфигурации; новая логика не требует синхронизации runtime-состояния.

## Открытые вопросы

Нет блокирующих бизнес-вопросов. Решения `/web/**`, `/preview/**`, пустого production allowlist и сохранения `/app/**` за Expo явно приняты в issue #826.

Технические детали регистрации MVC mappings, реализации cache headers и размещения общего ProblemDetail factory выбирает Developer при соблюдении проверяемых контрактов этой спеки. Если при реализации обнаружится вопрос, меняющий пользовательское поведение, права доступа или публичный API сверх описанного, он добавляется в issue #826 до реализации затронутой части.

## Риски

- Старый ADR-0003 и README противоречат issue и реальному `/app/**`; без синхронного обновления документации следующий этап может снова собрать React в каталог Expo.
- Слишком широкий SPA fallback может перехватить API, OAuth callback, actuator или серверные action URLs. Поэтому allowlist пуст по умолчанию, резервные префиксы валидируются, глобальный 404 fallback запрещён.
- Публичность `/web/**` легко ошибочно принять за утечку preview. Реальная граница безопасности — серверная авторизация route/API и отсутствие данных/секретов в bundle.
- Изменение общего формата ошибок всех legacy API в одном PR может сломать Thymeleaf JS и Expo. Эта задача вводит контракт только для новых SPA API.
- `SecurityConfig` сейчас исключает из CSRF весь `/api/auth/**`; если оставить это без сужения, будущая session-based мутация в этом namespace окажется незащищённой.
