# ADR-0014. React SPA использует `/web/**`, закрытый `/preview/**` и явный allowlist маршрутов

- **Статус:** Принято
- **Дата:** 2026-11-05
- **Заменяет:** [ADR-0003](0003-serve-spa-from-spring-boot.md)
- **Связано:** [ADR-0005](0005-auth-session-same-origin.md), [ADR-0007](0007-incremental-migration.md), [ADR-0013](0013-redesign-and-react-together.md), [план](../plan.md)

## Контекст

После подготовки первоначального ADR префикс `/app/**` занял клиент 1Padel.App: его Expo web-сборка
лежит в `static/app/`, а Google OAuth мобильного клиента возвращает пользователя на `/app/`.
React UI v2 должен сосуществовать с Expo и Thymeleaf, не менять действующие URL и не включать
глобальный SPA fallback.

## Решение

1. React-сборка размещается в `classpath:/static/web/` и доступна как `/web/**`.
   `/web/index.html` получает `Cache-Control: no-cache`, а хешированные файлы
   `/web/assets/**` — долгий `immutable` cache.
2. `/preview` и `/preview/**` направляются на `/web/index.html` только после серверной проверки
   `ROLE_SUPER_ADMIN`. Статический код `/web/**` публичен и не содержит данных или секретов;
   данные preview доступны только через авторизованные API.
3. Production-страницы переключаются по типизированному списку `app.spa.routes`. Список пуст в production, базовой конфигурации и изолированном preview. Forward разрешён только для `GET` и `HEAD` явно
   перечисленных patterns.
4. Глобальный fallback запрещён. Конфигурация отклоняет catch-all и patterns, пересекающие
   `/api/**`, `/ws/**`, `/actuator/**`, `/oauth2/**`, `/login/oauth2/**`, `/logout`,
   `/app/**`, `/web/**` и `/preview/**`.
5. Allowlist не меняет правила Spring Security: закрытый URL остаётся закрытым по исходному пути.
   Откат экрана выполняется удалением pattern из `app.spa.routes`.
6. `/app`, `/app/**`, `static/app/**`, Expo OAuth redirect и JWT-контракты не изменяются.

## Последствия

- Thymeleaf, Expo и React работают в одном Spring Boot приложении без пересечения статических зон.
- До появления frontend-сборки forward может вернуть обычный `404`; он не подменяется Thymeleaf
  или Expo shell.
- Сборка `frontend/` и упаковка файлов в `static/web/` выполняются отдельной frontend-задачей.
- При будущем выносе фронтенда остаются те же URL страниц и API; меняется только доставка статики
  и маршрутизация перед Spring Boot.

## Отвергнутые альтернативы

| Вариант | Почему нет |
|---|---|
| Переиспользовать `/app/**` | Префикс уже является публичным контрактом Expo-клиента и участвует в OAuth/JWT-флоу |
| Глобальный fallback неизвестных URL | Перехватывает API, OAuth callback, служебные URL и скрывает реальные `404` |
| Защищать весь `/web/**` ролью | Браузер не сможет независимо загрузить assets; безопасность обеспечивают route/API authorization и отсутствие данных в bundle |
