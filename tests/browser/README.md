# Проверка источников в настоящем браузере

CI поднимает отдельный PostgreSQL `padel_browser_qa` и Spring Boot на случайном
локальном порту. Тестовые аккаунты и мероприятие существуют только в этой
одноразовой БД. Email и уведомления заменены тестовыми объектами; Meta, GA и
Clarity отключены. Рабочий сайт и его данные не используются.

Playwright 1.64.1 проверяет согласие, переход Reby → Instagram → реальную запись
через интерфейс, обе модели отчёта, фильтры/нули/ошибки, ES/RU/EN и права доступа
в Chromium. Дополнительно WebKit с эмуляцией iPhone 13 Pro проверяет мобильный
русский отчёт. Это проверка движка WebKit, а не физического iPhone.

Обычный Maven-прогон пропускает браузерный тест. Отдельный CI job устанавливает
браузеры и запускает `mvn -Dbrowser.qa=true -Dtest=RegistrationSourceBrowserTest test`.
Нужны Node и PLAYWRIGHT_MODULE_PATH, указывающий на index.mjs установленного
Playwright. Тест жёстко использует локальную БД с отдельным именем.

Артефакт CI содержит JSON результата и скриншоты вымышленных тестовых данных;
пароли, cookies и файл начальных данных в него не включаются.

После перехода на reactor отчёты приложения находятся в `padel-bootstrap/target/surefire-reports/`, unit/architecture движка — в `padel-tournament-engines/target/surefire-reports/`. Рабочий каталог JVM приложения остаётся корнем, поэтому browser evidence по-прежнему пишется в `target/browser-qa/`. Корневой `-Dtest` приложения проходит через engines-модуль; отсутствие указанного теста в bootstrap остаётся ошибкой. Перенесённый engine-набор запускается отдельно: `./mvnw -pl padel-tournament-engines -am -Dtest=PlayoffMatchingEngineTest test`.

## Объединение гостевых профилей

Проверка `guest-claim-workflow.browser.mjs` запускается на локальном 1‑Padel
с одноразовой PostgreSQL. После миграций загрузите `guest-claim-fixtures.sql`
через `psql -v ON_ERROR_STOP=1`. Передайте в окружении `GUEST_CLAIM_QA_PASSWORD`
свежий пароль синтетических аккаунтов для psql и Node, `GUEST_CLAIM_QA_URL`
с локальным адресом 127.0.0.1 и `PLAYWRIGHT_MODULE_PATH` с путём к Playwright.
Для теста настройте локальное приложение на `https://wa.me/12025550100`.
Сценарий блокирует внешние запросы. По умолчанию скриншоты сохраняются в
`target/guest-claim-browser`; путь можно изменить через `GUEST_CLAIM_QA_EVIDENCE`.

Браузер проверяет ошибку занятого телефона, повтор заявки, сохранение номера
и ссылки после обновления, очередь, блокировку конфликта, объединение по заявке
и ручное объединение. Проверяются ES, RU и EN в Chromium на компьютере и в
эмуляции iPhone. Запуск: `node tests/browser/guest-claim-workflow.browser.mjs`.
