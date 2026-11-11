# 1-Padel Web

Production-каркас UI v2. Сборка публикуется Spring Boot под `/web/**`, закрытый предпросмотр открывается через `/preview/**`. Публичные маршруты Phase 0 не переключает.

## Требования

- Node.js 24.12+
- pnpm 11.25.0 (версия закреплена в `packageManager`)

Maven устанавливает собственные Node/pnpm автоматически, поэтому для `./mvnw package` глобальные инструменты не нужны.

## Локальная разработка

```bash
pnpm install --frozen-lockfile
pnpm dev
```

Vite проксирует `/api` на `http://127.0.0.1:8080`. Приложение использует `BrowserRouter`; в preview basename определяется как `/preview`.

## Проверки

```bash
pnpm check
pnpm exec playwright install chromium
pnpm test:e2e
```

`pnpm check` запускает Prettier, ESLint, typecheck, Vitest, production build и size-limit. Playwright проверяет shell в desktop и mobile viewport; API сессии в изолированном smoke-тесте перехватывается типизированным ответом. Backend routing/RBAC проверяются интеграционными тестами LFPT-0826.

## API и безопасность

- запросы только same-origin с `credentials: include`;
- язык передаётся в `Accept-Language`;
- мутирующие запросы получают `X-XSRF-TOKEN` из cookie `XSRF-TOKEN`;
- auth/session state живёт только в памяти TanStack Query;
- `localStorage` используется только для выбранного языка;
- ошибки `application/problem+json` разбираются как типизированный `ApiProblem`.

## Maven

Обычный `./mvnw package` собирает frontend и включает `static/web/**` в jar. Для локальной backend-итерации сборку можно пропустить:

```bash
./mvnw -Dskip.frontend=true package
```

## Главная и каталог

Закрытые маршруты `/preview/` и `/preview/torneos` используют `/api/public/home`, `/api/public/catalog` и `/api/public/clubs` из #898. Фильтры и пагинация сохраняются в query URL, сортировка и вместимость поступают с сервера. Время отображается в зоне Буэнос-Айреса. Карточка ведёт на действующую `/torneo/{id}`. Служебный фундамент доступен на `/preview/foundation`, компоненты — `/preview/components`.

`pnpm test:e2e` проверяет desktop/mobile, три языка, URL/reload/back, загрузку, ошибку, retry, пустой ответ и отсутствие overflow. Тестовые данные находятся только в e2e/unit-тестах; production-код не использует fixtures.

## Мероприятия и клубы

Закрытые страницы `/preview/torneo/{id}`, `/preview/clubs`, `/preview/clubs/{id}` используют публичный контракт backend #944. Карточки каталога теперь ведут в React preview; канонические ссылки шаринга сохраняют публичные `/torneo/{id}` и `/clubs/{id}` из API. Состав показывает только публичные имена, формат/фаза/действия поступают с сервера. Запись и live пока ведут на действующие разрешённые сервером страницы.

Share-диалог использует локальную генерацию QR (`qrcode`), clipboard с ручным fallback и ссылки WhatsApp/Telegram. QR не отправляется внешним сервисам. Метаданные присутствуют в исходном серверном HTML; клиент обновляет title/canonical при SPA-навигации. Preview остаётся закрытым/noindex, production allowlist не меняется.
