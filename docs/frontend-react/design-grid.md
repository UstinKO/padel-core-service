# Сетка страниц нового дизайна (от заказчика)

> **Источник:** заказчик, 2026-10-26. Кликабельные демо-страницы 1-PADEL — вход для этапа «Варианты дизайна» и для макета экрана в спеке перевода ([ADR-0013](adr/0013-redesign-and-react-together.md) п. 2, «Последствия»).
>
> **Это сетка контента, а не готовый дизайн** (решение пользователя, 2026-10-26).
> - **Обязательно, как в демо:** состав блоков на экране, их расположение и порядок, состояния экрана (свободные места / резерв / зарегистрирован / турнир идёт), какие данные и действия есть в каждом блоке.
> - **Не берём из демо:** визуальный стиль — цвета, шрифты, радиусы, тени, иконки, плотность. Демо выглядит плоско и несовременно; стиль задаёт направление, согласованное по ADR-0013 этап 2.1, и фиксируется в `.agents/DESIGN.md`.
>
> **Хостинг внешний** (`*.evgeny-1800.chatgpt.site`), под нашим контролем не находится: ссылки могут измениться или пропасть. Все открывались 2026-10-26. Перед переводом экрана сохранять скриншоты нужного состояния в спеку экрана, а не полагаться только на ссылку.

**Граница свободы (решение пользователя, 2026-10-26):** фиксированы состав блоков, их порядок и данные в каждом блоке; форма компонентов (вид hero, фильтров, карточки, число колонок) — свободна и задаётся стилем.

**Выбор стиля (этап 2.1):** мудборд из 5 направлений; выбраны 01 Трансляция, 02 Спортивный журнал, 03 Стеклянный корт. По ним собраны варианты главной, у каждого светлая и тёмная тема; заказчику отправлены внешней ссылкой. (Первый раунд вариантов был перекраской стиля заказчика и заменён.)

Макеты — рабочие материалы и в git не входят (`/design/` в `.gitignore`): мудборд — `design/moodboard/index.html`, варианты главной — `design/variants/home/index.html` (локально переключатель `?__variant=broadcast|editorial|glass&__theme=light|dark`, во внешней ссылке — якорь `#glass-dark` и т. п.). Итог выбора фиксируется в `.agents/DESIGN.md`.

Колонка «Сейчас» — какой текущий маршрут и шаблон заменяет экран (по [contract/screens.md](contract/screens.md) и навигационной карте в `.agents/PROJECT.md`). Состояния демо (`?state=…`, `?availability=…`, `?view=…`) — параметры самого демо, не будущие URL сайта: боевые URL при переводе не меняются (ADR-0013 п. 4).

## Основные страницы

| Экран | Демо | Сейчас |
|---|---|---|
| Главная — расписание турниров | [открыть](https://padel-design-demo.evgeny-1800.chatgpt.site/) | `GET /` → `index.html` |
| Вход | [открыть](https://padel-design-demo.evgeny-1800.chatgpt.site/login.html?lang=ru) | `GET /login` → `login.html` |
| Регистрация | [открыть](https://padel-design-demo.evgeny-1800.chatgpt.site/register.html?lang=ru) | `GET /players/registro` → `registro.html` |
| Кабинет игрока | [открыть](https://one-padel-dashboard-demo.evgeny-1800.chatgpt.site/?lang=ru) | `GET /players/dashboard` → `players/dashboard.html` |

## King of Court — страница игрока

Первые четыре — состояния одной страницы турнира `GET /torneo/{id}` → `tournament-details.html` (тип `KING_OF_COURT`).

| Экран | Демо | Сейчас |
|---|---|---|
| Есть свободные места | [открыть](https://one-padel-king-of-court-demo.evgeny-1800.chatgpt.site/?state=available&lang=ru) | `/torneo/{id}`, регистрация открыта |
| Турнир заполнен — запись в резерв | [открыть](https://one-padel-king-of-court-demo.evgeny-1800.chatgpt.site/?state=full&lang=ru) | `/torneo/{id}`, мест нет → лист ожидания |
| Игрок уже зарегистрирован | [открыть](https://one-padel-king-of-court-demo.evgeny-1800.chatgpt.site/?state=registered&lang=ru) | `/torneo/{id}`, своя регистрация |
| Турнир начался — переход к сетке и рейтингу | [открыть](https://one-padel-king-of-court-demo.evgeny-1800.chatgpt.site/?state=full&lang=ru&live=ready) | `/torneo/{id}`, турнир идёт |
| Экран игрока во время турнира — корты, рейтинг, история матчей | [открыть](https://one-padel-king-of-court-player-live-demo.evgeny-1800.chatgpt.site/) | `GET /torneo/{id}/king-of-court`, `/tournaments/king-of-court/{kingId}` → `king-of-court-view.html` (WebSocket) |

## King of Court — администратор

| Экран | Демо | Сейчас |
|---|---|---|
| Проведение турнира — ввод результатов, рейтинг, история, раунды | [открыть](https://one-padel-king-of-court-admin-demo.evgeny-1800.chatgpt.site/) | `GET /admin/tournaments/king-of-court/{kingId}` → `admin/tournaments/king-of-court.html` (WebSocket) |
| Окно ввода результата | [открыть](https://one-padel-king-of-court-admin-demo.evgeny-1800.chatgpt.site/?view=result&court=2) | модалка того же пульта |

## Americano (индивидуальный) — страница игрока

| Экран | Демо | Сейчас |
|---|---|---|
| Турнир заполнен — запись в резерв | [открыть](https://one-padel-americano-player-demo.evgeny-1800.chatgpt.site/) | `GET /torneo/{id}` → `tournament-details.html` (тип `AMERICANO`), мест нет |
| Есть свободные места — запись | [открыть](https://one-padel-americano-player-demo.evgeny-1800.chatgpt.site/?availability=open) | `/torneo/{id}`, регистрация открыта |
| Турнир идёт — корты, сетка всех раундов, рейтинг, история матчей | [открыть](https://one-padel-americano-player-demo.evgeny-1800.chatgpt.site/live.html) | `GET /tournaments/americano/{id}` → `tournaments/americano/view.html` (+ `ranking.html`) |

## Americano (индивидуальный) — администратор

| Экран | Демо | Сейчас |
|---|---|---|
| Информация о турнире, настройки формата, участники | [открыть](https://one-padel-americano-admin-demo.evgeny-1800.chatgpt.site/) | `GET /admin/tournaments/{id}` → `admin/tournaments/details.html` (участники, конфигурация) и `GET /tournaments/americano/admin/{id}` → `admin/americano/tournament.html` (пульт) |
| Окно ввода результата | [открыть](https://one-padel-americano-admin-demo.evgeny-1800.chatgpt.site/?view=result) | модалка пульта `admin/americano/tournament.html` |

## Чего в сетке нет

Экраны из [contract/screens.md](contract/screens.md), для которых макета пока нет — при переводе их нужно либо получить от заказчика, либо вывести из утверждённого дизайна по ближайшему аналогу:

- **Форматы:** Team Americano и Team Playoff (`AMERICANO_TEAMS`) — страницы игрока, пульты, парная регистрация; `CANCHA_ABIERTA` и `PADEL_CLINIC` на `/torneo/{id}`; предпросмотр раундов Americano (`preview-rounds`, `preview-double-rounds`).
- **Публичные:** `/torneos`, `/clubs`, `/clubs/{id}`, `/ranking`, legal-страницы, статусные страницы (`waitlist-confirmation.html`, `reserve-cancel-confirm.html`, `accept-pair.html`), `error/rate-limit.html`.
- **Игрок:** профиль `/perfil`, восстановление пароля.
- **Админка:** `/admin`, списки/формы/детали турниров, клубов и игроков, платежи `/admin/tournaments/{id}/payments`.
