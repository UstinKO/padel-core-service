# LFPT-0468: Feature подключение Google Analytics 4 на сайте 1-padel.com

## Статус
done — PR #470, деплой подтверждён (run 37197974503)

## Источник
`specs/requests/LFPT-0468-google-analytics.md` (Telegram, пересылка от Evgeny_754, telegram_launch_message_id: 4760) → issue [#468](https://github.com/UstinKO/padel-core-service/issues/468).

## Контекст / зачем
Заказчику нужна веб-аналитика посещаемости сайта 1-padel.com (какие страницы смотрят, сколько визитов, источники трафика) через Google Analytics 4, Measurement ID `G-X5G4N3ND26`. Сейчас в проекте нет ни GA4, ни Google Tag Manager — проверено `grep` по `src/main/resources` (templates + static/js) на `gtag|googletagmanager|google-analytics|GTM-`.

## Связь с MASTER.md
Не затрагивает бизнес-инварианты MASTER.md — чисто техническая интеграция стороннего веб-аналитического сервиса в публичный UI. Новых изменений в MASTER.md не требуется.

## Важные архитектурные находки (определяют реализацию)

1. **Нет единого layout/head-фрагмента.** В проекте 60+ Thymeleaf-шаблонов, у каждого свой независимый `<head>` (есть только `fragments/public-header.html` и `fragments/admin-header.html` — это фрагменты навигационной шапки `<body>`, не `<head>`). "Установить тег один раз" технически реализуется как **один новый фрагмент** `fragments/analytics.html` (единственный источник кода тега) **+ его `th:replace`-подключение в `<head>` каждого нужного шаблона** — другого способа избежать копипасты самого кода тега в этой архитектуре нет.
2. **CSP блокирует gtag.js без доработки.** `SecurityConfig.java` задаёт строгий `Content-Security-Policy` (`script-src`/`connect-src` — explicit allowlist без wildcard). Без добавления `https://www.googletagmanager.com` (script-src) и `https://www.google-analytics.com` / `https://*.google-analytics.com` / `https://www.googletagmanager.com` (connect-src) браузer молча заблокирует загрузку тега — фича не будет работать физически. Это **единственная правка в `src/main/java`** в рамках задачи: без неё фронтенд-изменение не имеет смысла (нечего тестировать), поэтому она включена в эту же задачу/PR, а не выносится в отдельную `backend`-задачу по правилу `GIT_WORKFLOW.md §1.4` — присвоение заводить вторую задачу ради одной строки CSP, обязательной для работы основной фичи, было бы процессным оверхедом без практической пользы (решение архитектора, см. `GIT_WORKFLOW.md §3` — технический выбор, не бизнес-вопрос).
3. **Сайт — классический multi-page Thymeleaf-рендеринг, SPA-переходов нет.** Проверено `grep` по `static/js/**` на `pushState|replaceState|popstate|hashchange` — единственное совпадение (`password-reset.js`) не меняет "страницу", а только убирает query-параметр `token` из URL после рендера (`history.replaceState`), без смены контента. Каждый переход между страницами — полная перезагрузка документа. Поэтому автоматический `page_view` от `gtag.js` (срабатывает один раз на загрузку документа) **не нуждается** в дополнительной защите от двойного учёта — условие из клиентского запроса ("если сайт переключает страницы без перезагрузки...") технически не применимо к текущей архитектуре. Зафиксировано явно, чтобы не городить не нужный код.
4. **Cookie-consent уже есть, но неполно подключён.** `/api/cookies/accept|reject|customize` (`CookieController`) уже выставляют `cookieConsent` (`accepted`/`rejected`/`customized`) и, при кастомизации, `analyticsConsent`/`marketingConsent` (`true`/`false`) — обычные (не `httpOnly`) cookie, читаемые на клиенте. Баннер cookie-consent (`cookie-consent.js`) сейчас подключён **только на `index.html`** (grep по всем шаблонам на `cookie-consent.js` — одно совпадение) — то есть на остальных страницах сайта согласие никогда не запрашивается явно. Это существующий, не связанный с текущей задачей пробел; не устраняется в этой задаче (см. "Вне скоупа"), но учтён при проектировании: тег должен безопасно работать и в отсутствие осознанного выбора пользователя (дефолт — согласие не дано, пока явно не получено).
5. **Риск PII/токенов через адрес страницы — подтверждён, не гипотетический.** Несколько публичных GET-маршрутов рендерят HTML-страницу (не redirect) с чувствительным токеном в query-string:
   - `GET /double-registration/accept-pair?token=...` → `accept-pair.html` (`PartnerRegistrationController`);
   - `GET /waitlist/confirm?token=...` → `waitlist-confirmation.html` (`WaitlistController`, GET сознательно не подтверждает, только показывает превью — но токен остаётся в URL рендерящейся страницы);
   - `GET /recuperar-password?token=...` → 302 redirect на `/?token=...` → `index.html` рендерится с токеном в URL; `password-reset.js` вычищает его через `history.replaceState`, но **после** полной загрузки страницы — `gtag`, если не предусмотреть защиту, успеет отправить `page_view` с токеном в `page_location` до этой очистки.

   Остальные GET с токенами (`/players/confirmar-email?codigo=...`, `/double-registration/complete?token=...`) **всегда** отвечают 302-редиректом без рендера тела — браузер не выполняет JS на этой "странице", поэтому `gtag` там не успевает сработать, риска нет.

## Требования

### Функциональные

- Новый Thymeleaf-фрагмент `fragments/analytics.html` (`th:fragment="ga4"`) — единственное место с кодом тега `gtag.js` (Measurement ID `G-X5G4N3ND26` выносится в `application.yml`/`application-*.yml` как `app.analytics.ga4-id`, не хардкодится в шаблоне — по аналогии с другими внешними ID вроде `app.recaptcha.site-key`).
- Фрагмент реализует **Google Consent Mode**: до загрузки `gtag.js` — `gtag('consent', 'default', {analytics_storage: 'denied', ad_storage: 'denied', ad_user_data: 'denied', ad_personalization: 'denied'})`; если на клиенте уже есть cookie `cookieConsent=accepted` или (`cookieConsent=customized` и `analyticsConsent=true`) — сразу `analytics_storage: 'granted'` (маркетинговые consent-поля остаются `denied` — в задаче только про аналитику, `marketingConsent` не запрашивался заказчиком и не трогается).
- `gtag('config', 'G-X5G4N3ND26', {...})` вызывается с явно переданными `page_location`/`page_path`, вычисленными из `window.location` **с вырезанными** query-параметрами `token` и `codigo` (конкретный, подтверждённый находками выше список, не общая эвристика — см. "Важные архитектурные находки" п.5). Тем самым устраняется подтверждённый риск попадания токена в `page_location` на `accept-pair`, `waitlist-confirmation` и `index` (после редиректа из `/recuperar-password`).
- `cookie-consent.js` дополняется: при `acceptCookies()` и при `saveCookieSettings()` (если `analytics === true`) — вызов `gtag('consent', 'update', {analytics_storage: 'granted'})`; при `rejectCookies()` и при `saveCookieSettings()` с `analytics === false` — `gtag('consent', 'update', {analytics_storage: 'denied'})`. Это даёт эффект без перезагрузки страницы сразу после выбора пользователя на `index.html` (единственной странице, где баннер сейчас показывается).
- Фрагмент `fragments/analytics.html` подключается (`th:replace="~{fragments/analytics :: ga4}"`) в `<head>` следующих публичных/игровых шаблонов (полный список — выбран как "нужные страницы" в терминах клиентского запроса: публичный сайт и личный кабинет игрока, без админки и внутренних тестовых страниц, см. "Вне скоупа"):
  - `index.html`, `torneos.html`, `tournament-details.html`, `king-of-court-view.html`,
  - `tournaments/americano/view.html`, `tournaments/americano/ranking.html`, `tournaments/team-americano/view.html`, `tournaments/team-playoff/view.html`,
  - `clubs/list.html`, `clubs/view.html`, `ranking.html`, `registro.html`, `login.html`,
  - `players/dashboard.html`, `players/perfil.html`,
  - `legal/cookies.html`, `legal/privacidad.html`, `legal/terminos.html`,
  - `waitlist-confirmation.html`, `accept-pair.html`,
  - `error/403.html`, `error/rate-limit.html`, `error/tournament-not-found.html`.
- `SecurityConfig.java`: CSP `script-src` дополняется `https://www.googletagmanager.com`; `connect-src` дополняется `https://www.google-analytics.com https://*.google-analytics.com https://www.googletagmanager.com`.

### Нефункциональные
- i18n: новых пользовательских строк нет (тег работает без видимого UI-текста) — пункт не применим.
- Безопасность: новая точка данных, уходящих во внешний сервис (Google) — закрыта явным списком исключаемых query-параметров (см. выше) и Consent Mode по умолчанию `denied`. Персональные данные (имя/email/телефон) нигде не передаются в `gtag`-вызовы явно (не добавляем кастомных событий с такими полями) и не присутствуют в затронутых URL/title (проверено по шаблонам — `<title>` на всех указанных страницах использует только публичные бизнес-данные: название турнира/клуба, статические ключи i18n).
- Конфигурация ID через `application.yml` — не per-профильный секрет (Measurement ID GA4 не секретен, виден в исходном коде любой публичной страницы), но вынесен из шаблона для единообразия с другими внешними идентификаторами проекта.

## Вне скоупа
- Распространение баннера cookie-consent (`cookie-consent.js`) на страницы, где он сейчас не подключён (только `index.html`) — существующий, самостоятельный пробел, не создан и не усугублён этой задачей. Заводится отдельной находкой (issue) по итогам этой задачи, без реализации здесь.
- `/admin/**` (админ-панель) и `/test/**` (внутренние тестовые страницы) — не инструментируются: это не "посещения сайта" в смысле запроса заказчика (внутренний инструмент клубов/организаторов, не аудитория для веб-аналитики), что также минимизирует объём бизнес-данных (названия турниров/клубов из admin-заголовков), уходящих к Google, без необходимости.
- Email-шаблоны (`templates/email/**`) — не веб-страницы, GA4 (браузерный tracking) к ним неприменим.
- Google Tag Manager — заказчик прямо просил `gtag.js`, не GTM-контейнер.
- `marketingConsent`/`ad_storage` — заказчик просил только аналитику; рекламные consent-поля остаются `denied` по умолчанию и нигде не включаются.
- Кастомные GA4-события (конверсии регистрации на турнир и т.п.) — заказчик просил только базовую установку тега и учёт визитов, не событийную аналитику.

## Изменения в системе

### API
Нет новых/изменённых REST-эндпоинтов.

### БД
Нет изменений схемы.

### Конфигурация
- `application.yml` (и, при необходимости, `application-cloud.yml`/др. профили, если ID должен отличаться — не отличается, добавляется один раз в базовый `application.yml`): новое свойство `app.analytics.ga4-id: G-X5G4N3ND26`.
- `SecurityConfig.java`: расширение CSP (см. выше).

### UI
- Новый файл `templates/fragments/analytics.html`.
- Правка `<head>` 23 шаблонов, перечисленных в разделе "Требования".
- Правка `static/js/cookie-consent.js` (вызовы `gtag('consent', 'update', ...)`).

## Критерии приёмки
- [ ] В коде нет других интеграций GA/GTM, которые могли бы задублироваться (подтверждено анализом на этапе спеки — переисследуется разработчиком/тестировщиком на актуальном состоянии ветки).
- [ ] На каждой из 23 перечисленных страниц при открытии в браузере с уже выданным согласием (`cookieConsent=accepted` cookie) в сетевых запросах присутствует обращение к `googletagmanager.com/gtag/js?id=G-X5G4N3ND26` и последующий запрос-"collect" к `google-analytics.com` (аналитика действительно включается).
- [ ] На `/admin/**` и `/test/tournaments` тег не загружается (grep по финальному HTML/Network — нет `googletagmanager.com`).
- [ ] При открытой странице без cookie `cookieConsent` — `gtag('consent','default', {analytics_storage:'denied', ...})` вызван раньше `gtag('config', ...)` (порядок вызовов в `dataLayer`), и запрос-"collect" с данными о событии либо не уходит, либо уходит в cookieless-режиме Google (без установки `_ga`-cookie) — в любом случае cookie `_ga`/`_gid` не создаётся до согласия.
- [ ] После клика "Принять все" на баннере (на `index.html`) — `_ga`/`_gid` cookie появляются без перезагрузки страницы (эффект `consent update` виден сразу).
- [ ] Открытие `GET /double-registration/accept-pair?token=XXX`, `GET /waitlist/confirm?token=XXX`, переход `GET /recuperar-password?token=XXX` → `/?token=XXX` — ни в одном зафиксированном сетевом запросе к `google-analytics.com`/`googletagmanager.com` параметр `token`/`codigo` не присутствует ни в URL запроса, ни в теле.
- [ ] `./mvnw compile -DskipTests` проходит (CSP-правка — валидный Java-код).
- [ ] Ручная/browser-проверка (см. "Edge cases") пройдена тестировщиком, скриншот/лог сетевых запросов приложен к отчёту.

## Edge cases
- Пользователь уже отклонил cookies (`cookieConsent=rejected`) на предыдущем визите → при следующем визите на любой инструментированной странице `gtag('consent','default',...)` сразу выставляется в `denied` без ожидания баннера (баннер и не покажется повторно — `hasCookieConsent()` в `cookie-consent.js` не считает его нужным).
- Страница открыта напрямую (без визита на `index.html` ранее) и cookie `cookieConsent` отсутствует вовсе → дефолт `denied`, баннер на этой странице не появится (вне скоупа, см. выше), визит не попадёт в "гранулярную" аналитику с хранением cookie, но Google Consent Mode всё равно позволяет агрегированное cookieless-моделирование на стороне Google — ожидаемое, стандартное поведение интеграции, не баг.
- Query-параметры `token`/`codigo` вырезаются только из того, что отправляется в `gtag` (`page_location`/`page_path`), сам URL в адресной строке браузера и поведение соответствующих контроллеров не меняются (не в скоупе).

## Открытые вопросы
Нет открытых бизнес-вопросов — все решения в разделе "Важные архитектурные находки" технические (конкретный список страниц/параметров, способ подключения через фрагмент, включение CSP-правки в тот же PR), обоснованы и не требуют уточнения у заказчика перед реализацией.
