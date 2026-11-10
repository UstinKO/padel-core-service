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
