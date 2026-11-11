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

## Аккаунт UI v2

Маршруты `/preview/login`, `/preview/players/registro`, `/preview/recuperar-password/solicitar`, `/preview/recuperar-password?token=…`, `/preview/perfil` работают с серверной сессией, CSRF и [контрактом аккаунта](../docs/endpoints/account.md). Сессия и профиль хранятся в памяти TanStack Query; профиль разделён по ID пользователя, межвкладочное обновление — BroadcastChannel и focus/visibility. Пароли, CAPTCHA и auth state не пишутся в браузерное хранилище. Язык: URL `lang` → cookie `lang` → браузер; изменение сохраняет безопасные поля формы. Google — настоящая OAuth-навигация, кнопка скрыта при googleEnabled=false. Производственный preview остаётся SUPER_ADMIN. Для гостя/игрока проверять отдельный локальный frontend на loopback с same-origin proxy к тестовому backend, отдельной БД и SMTP-приёмником; не менять права production preview.

## Юридические и системные страницы

`/preview/terminos`, `/preview/privacidad`, `/preview/cookies` используют утверждённые тексты ES/RU/EN из `src/main/resources/templates/legal/`. Статическая копия — `frontend/src/legal/content.json`, генератор `python3 frontend/scripts/sync-legal-content.py`; после генерации выполнить Prettier. Девять тестов проверяют полное совпадение текста и ссылок. Legal загружается отдельным маршрутом: основной JS сохраняет бюджет 180 kB gzip, legal chunk проверяется отдельно. CookieConsent вызывает существующие `/api/cookies/accept`, `/reject`, `/customize` с CSRF и читает сохранённые сервером cookies; пустой 200 считается успешным ответом. При ошибке предыдущий выбор сохраняет действие. `applyConsent` обновляет существующие gtag/Clarity hooks и отправляет прежнее marketing-consent-changed, SDK самостоятельно не загружает: preview остаётся закрытым, активация внешних интеграций относится к отдельному контуру. SystemPage/AppErrorBoundary/RouteErrorPage показывают безопасные 401/403/offline/429/503/404; повторяются только чтения по явной кнопке. 401 переносит безопасный destination на вход; 403 не запускает login-цикл. `/preview/error/{401,403,429,503}` и `/preview/error/offline` позволяют проверить состояния, неизвестный маршрут получает реальный серверный 404. Production URL не переключены.

Общие ошибки форм переводятся по машинным кодам вместе с языком интерфейса. Резюме ошибок переводит фокус к соответствующему контролу; Field сохраняет отдельные доступное имя и описание ошибки. Параллельная отправка форм профиля и пароля блокируется на время запроса.

## LFPT-0911: пользовательские live-экраны UI v2

Реализованы закрытые preview-экраны King of Court, индивидуального Americano и Team Playoff поверх snapshot #903. Production live URL и allowlist не переключены; публикация остаётся отдельным этапом между турнирами.

| Preview URL                              | Источник                                                                 |
| ---------------------------------------- | ------------------------------------------------------------------------ |
| `/preview/torneo/:id/king-of-court`      | Snapshot по родительскому eventId                                        |
| `/preview/tournaments/king-of-court/:id` | Проверяемый alias по kingId; старый инстанс — 409 со ссылкой продолжения |
| `/preview/tournaments/americano/:id`     | Индивидуальный Americano; polling 20 с                                   |
| `/preview/tournaments/team-playoff/:id`  | Квалификация, очередь и серверные стадии плей-офф                        |

Поддерживаются `/ranking`, `/history` и query-параметры `tab`, `round=phase:number:id`, `player`, `display=board`. Ключ раунда включает номер волны: qualification waves имеют общий database ID. Неизвестные объекты после restore убираются из URL. Переход из React-карточки события ведёт в соответствующий preview; canonical/share/QR сохраняют публичный URL сервера.

Отдельные рейтинги отображают серверный порядок и итоги: у KoC раздельны очки матчей, бонусы и ручные корректировки по раундам; у Americano нет бонусов KoC; у Team Playoff есть номера команд и матчей команды, квалификационные геймы и положение. Корты, история и диалог участника используют те же матчи снимка. Самого игрока определяет session identity. У зрителя нет административных действий. Round-robin Team Americano предлагает прежний просмотр через 409.

Один lazy STOMP/SockJS-клиент обслуживает точные публичные topic-подписки, выдерживает StrictMode и дожидается асинхронного отключения перед новым клиентом. События объединяются и запускают refetch, без клиентских дельт. Reconnect/focus/visibility запускают чтение; состояние connected само по себе не убирает stale до успешного ответа. В видимой вкладке есть fallback polling 20 с, у Americano только polling. Один экран ограничивает GET интервалом минимум 3 с; 429 выдерживает Retry-After и при ручном refresh. AbortSignal отменяет ожидание и GET при уходе/смене события. После 403/404/409 прежний снимок убирается; при временных 5xx/сети остаётся с отметкой устаревания и временем последнего успешного чтения.

PNG сетки строится из серверного порядка раундов/матчей и явных связей; посев, победители и будущие пары в браузере не рассчитываются. Canvas-экспорт не содержит controls/контактов; ошибки показаны локализованно. Табличные итоги и сетка прокручиваются внутри области; длинные имена переносятся, доступны клавиатура, native dialog с возвратом фокуса, reduced motion и режим табло. ES/RU/EN используют общую cookie lang.

Проверки: format/lint/typecheck, 44 unit-теста и 52 Playwright-проверки desktop/mobile; все три формата, reconnect/coalescing/stale/404/409/429/503, большой bracket, 19 раундов, 320/390/1440 px, landscape и табло. Отдельная loopback-сборка с настоящим JAR/API и отдельной PostgreSQL проверяет guest/player, ranking parity, QR, PNG и цепочку «сохранение результата → безопасный WS → refetch». SUPER_ADMIN-защита preview проверяется отдельно. Эмуляция браузера не является проверкой физических iPhone/Android; отдельный развёртываемый контур #904 не реализуется этим блоком.

Live view загружается отдельным chunk; бюджет 75 kB gzip, основной JS — прежний бюджет 180 kB. Зависимости закреплены: @stomp/stompjs 7.5.0, sockjs-client 1.6.1, @types/sockjs-client 1.5.4. Ни fixtures UI 0.7, ни demo-формулы в production imports не используются.
