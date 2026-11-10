# LFPT-0827: Feature frontend — фундамент UI v2 Phase 0

## Статус
done

## Источник
[GitHub issue #827](https://github.com/UstinKO/padel-core-service/issues/827).

Pipeline-Task: manual-20261105T112045Z

Depends-On: LFPT-0826 / GitHub issue #826.

## Контекст / зачем
UI v2 переносится на React поэкранно, без одномоментной замены действующих Thymeleaf-маршрутов. Эта задача создаёт production-каркас SPA: воспроизводимую сборку, закрытый preview, общие компоненты, локализацию и безопасный same-origin API client. Визуальный baseline — утверждённый пакет `docs/frontend-react/1Padel-User-Interface-0.7/`: Manrope, тёплый светлый фон, тёмно-зелёный и лаймовый акцент.

Демо-данные, `HashRouter`, фиктивные пользователи, клиентские бизнес-расчёты и `localStorage` как источник auth/session state из пакета 0.7 в production не переносятся.

## Связь с MASTER.md
Бизнес-инварианты турниров и регистраций не меняются. Web-клиент использует существующую серверную сессию и роли только для отображения интерфейса; сервер остаётся источником авторизации. Контракт сессии, CSRF и маршруты `/web/**` / `/preview/**` поставляет LFPT-0826.

## Scope

### Входит
- самостоятельный pnpm-проект `frontend/` на React 19, TypeScript и Vite;
- `BrowserRouter` без hash URL, basename `/preview` для закрытого контура;
- `react-i18next`, полные словари ES/RU/EN и переключатель языка;
- нормализованные токены UI 0.7, self-hosted Manrope и mark;
- public layout, навигация, кнопка, поле, карточка и loading/empty/error/success состояния;
- TanStack Query и bootstrap `GET /api/auth/session`;
- same-origin fetch client с `credentials: include`, `Accept-Language`, `X-XSRF-TOKEN` и типизированным `ProblemDetail`;
- Vitest/Testing Library, ESLint/Prettier, Playwright smoke и size-limit;
- Maven/jar, Docker и CI интеграция; сборка публикуется только под `/web/`;
- документация локального запуска и архитектурных решений.

### Не входит
- переключение production-маршрутов и заполнение `app.spa.routes`;
- миграция прикладных экранов, admin/live/SEO;
- OpenAPI codegen, STOMP и новые backend API;
- тёмная тема: Phase 0 использует согласованный светлый baseline 0.7, но семантические токены не блокируют её добавление позже.

## Контракты

### Сессия
`GET /api/auth/session` всегда возвращает типизированный объект LFPT-0826. Ответ не сохраняется в `localStorage` и кэшируется TanStack Query только в памяти.

### CSRF
Для `POST|PUT|PATCH|DELETE` клиент читает cookie `XSRF-TOKEN` и отправляет decoded значение в `X-XSRF-TOKEN`. Все запросы same-origin и используют `credentials: include`.

### Ошибки
Новые API ошибки читаются как `application/problem+json`. В UI отображаются локализованный безопасный заголовок и `detail`; неизвестное тело или HTML не выводится пользователю. Технический stack trace не рендерится.

### Маршрутизация
- production assets: `/web/**`;
- preview routes: `/preview/**`, доступ проверяет backend;
- Vite `base=/web/`, чтобы assets загружались одинаково для любого deep link;
- production allowlist остаётся пустым.

## Критерии приёмки
- `pnpm install --frozen-lockfile`, `pnpm check` и `pnpm test:e2e` проходят;
- Maven package содержит `BOOT-INF/classes/static/web/index.html` и hashed assets, `/app/` не изменён;
- preview shell проверен в Chromium на desktop и mobile viewport;
- все новые пользовательские строки присутствуют в ES/RU/EN;
- в исходниках нет JWT, секретов, demo auth и session state в `localStorage`;
- `./mvnw verify` и docs drift проходят;
- production allowlist пуст.

## Риски и откат
- Рост времени Maven-сборки ограничен кэшем pnpm и `-Dskip.frontend=true` для локальных backend-итераций.
- Bundle контролируется size-limit; прикладные экраны добавляются лениво в следующих фазах.
- Откат: удалить frontend Maven execution и generated resource; действующие Thymeleaf и `/app/**` от SPA не зависят.

