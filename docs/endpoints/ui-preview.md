# Диагностика закрытого контура UI v2

Маршруты существуют только в профиле `preview`, за внешним gateway Basic Auth. Дополнительно требуется сессия SUPER_ADMIN приложения. В обычных профилях контроллер отсутствует. Инструкция запуска и границы изоляции: [закрытый контур](../ui-v2-preview.md).

| Метод | Путь | Результат |
|---|---|---|
| GET | `/api/internal/ui-preview` | Типизированный `Status`: `environment=preview`, commit `revision`, ID seed-клуба, владельца, игроков и мероприятий. Только чтение, `Cache-Control: no-store`. |
| POST | `/api/internal/ui-preview/reset` | После проверки роли, CSRF, имени БД и UUID-маркера пересоздаёт только seed-мероприятия; возвращает актуальный `Status`. |
| PUT | `/api/internal/ui-preview/elo/historical-score` | При подключённой истории Elo заменяет счёт проверенного исторического матча. Тело содержит `matchKey`, `left`, `right` и `reason`. Возвращает 204. Поправка применяется при следующем чтении рейтинга или кабинета. |
| GET | `/api/internal/ui-preview/playoff-pilot` | Типизированные `accounts` и `scenarios`; до подготовки — 404, после — 200, `Cache-Control: no-store`. Только чтение. |
| POST | `/api/internal/ui-preview/playoff-pilot` | Идемпотентная подготовка пяти сценариев Team Playoff; существующие ID сохраняются. |
| POST | `/api/internal/ui-preview/playoff-pilot/reset` | Атомарно удаляет только турниры пилота и пересоздаёт сценарии; аккаунты сохраняются, ID турниров/команд меняются. |

Пароли, JWT, UUID-маркер, адрес БД и данные внешних интеграций в ответы не входят. Контроллер не принимает произвольный datasource, имя проекта, идентификаторы удаления или actor. Guest/player не могут читать административную диагностику или выполнять reset. GET не создаёт seed и не запускает reset.

Liveness/readiness доступны в контуре по `/actuator/health/liveness` и `/actuator/health/readiness`, защищены gateway и не раскрывают детали компонентов. `/__preview` — маркированная точка входа, `/__preview/mail` — защищённое чтение синтетической почты из локального Mailpit.

`stage` и `teamCount` описывают начальный набор сценария, а не текущее состояние после действий оператора. Актуальная сетка/история читаются штатными endpoints турнира. Детали сценариев: [backend baseline](../modular-monolith/phase-1/backend-baseline.md).
