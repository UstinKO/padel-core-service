# Архетипы экранов — padel-core-service

> **Назначение:** сгруппировать 56 Thymeleaf-шаблонов по реальным layout-паттернам перед захватом в Figma (`html.to.design`), чтобы не мокапить дубли. Методология — структурный анализ (наличие таблиц/форм/WebSocket, число строк, повторяемость по табличным CRUD-паттернам admin-раздела), не только имена файлов.
> Связанные документы: [дизайн-токены as-is](design-tokens-as-is.md), [план перехода на микросервисы](microservices-migration-plan.md).

---

## 0. Итог: сколько реально нужно мокапить

Из 56 файлов:
- **11 — email-шаблоны**, не веб-экраны вообще (см. §6) — не участвуют в React-переносе.
- **6 — layout-фрагменты** (шапка/подвал/модалки), не отдельные экраны — станут shared-компонентами (`<Header>`, `<Footer>`, `<Layout>`), не отдельными Figma-фреймами.
- **1 — внутренний dev-инструмент** (`test/tournaments.html`, 3158 строк, работает напрямую через JDBC, только для организатора) — по аналогии с решением из i18n ("не переводим") предлагаю не переносить в React вообще, оставить как есть на Thymeleaf, либо перенести последним, если станет нужен.

Итого **38 файлов** реально нужно захватить как экраны — но они укладываются всего в **13 архетипов**. Мокапить в Figma нужно по одному представителю каждого архетипа (обычно — самый полный/сложный пример группы), остальные — вариации теми же данными.

---

## 1. Публичная часть (гость, не авторизован)

| Архетип | Файлы | Что мокапить (репрезентативный) |
|---|---|---|
| **Landing** | `index.html` (615 строк) | Уникальный, единственный экземпляр |
| **Публичный список с фильтрами** | `torneos.html` (330), `clubs/list.html` (65), `ranking.html` (173) | `torneos.html` — самый полный (фильтры + карточки + пагинация) |
| **Публичная карточка сущности** | `clubs/view.html` (77), `tournament-details.html` (1108) | `tournament-details.html` — самый полный (описание + регистрация + участники + Google Calendar кнопка) |
| **Auth-формы** | `login.html` (230), `registro.html` (363) | Оба разные по полям, но одна форма-архитектура — можно взять оба, это недорого |
| **Статический контент (legal)** | `legal/cookies.html`, `legal/privacidad.html`, `legal/terminos.html` (278-374 строк) | Один — остальные просто текст того же layout'а |
| **Confirmation/статусное сообщение** (мультистейт: pending/success/warning/error/notfound в одном шаблоне) | `waitlist-confirmation.html` (187), `accept-pair.html` (117) | `waitlist-confirmation.html` — там 5 состояний одного экрана, важно захватить все |
| **Error page** | `error/rate-limit.html` (156) | Уникальный |

## 2. Публичный просмотр турнира (live / near-real-time, доступно без регистрации)

| Архетип | Файлы | Особенность |
|---|---|---|
| **Live-табло формата** | `king-of-court-view.html` (240, **WebSocket**), `tournaments/americano/view.html` (463), `tournaments/team-americano/view.html` (369), `tournaments/team-playoff/view.html` (292), `tournaments/americano/ranking.html` (211) | Общая архитектура "раунды/матчи/счёт", отличается версткой конкретного формата. Только KoC — на WebSocket, остальные обновляются перезагрузкой/поллингом — стоит явно решить в React, все ли переводим на единый realtime-подход (см. открытый вопрос ниже) |

## 3. Личный кабинет игрока (авторизован, ROLE_PLAYER)

| Архетип | Файлы | Комментарий |
|---|---|---|
| **Dashboard (логин-хоум)** | `players/dashboard.html` (557) | Уникальный, набор виджетов (мои турниры, статус регистраций) |
| **Форма профиля** | `players/perfil.html` (316) | Форма редактирования личных данных |

## 4. Админ-панель — CRUD-паттерн (ROLE_OWNER/ORGANIZER/SUPER_ADMIN)

Самая регулярная часть — три файла на сущность, один и тот же паттерн list→form→details повторяется для Clubs/Tournaments/Players:

| Архетип | Файлы | Репрезентативный |
|---|---|---|
| **Admin: список с таблицей** | `admin/clubs/list.html` (173), `admin/tournaments/list.html` (270), `admin/players/list.html` (278) | `admin/tournaments/list.html` |
| **Admin: форма создания/редактирования** | `admin/clubs/form.html` (208), `admin/tournaments/form.html` (459) | `admin/tournaments/form.html` — самая полная форма в системе |
| **Admin: детали сущности** | `admin/clubs/details.html` (195), `admin/players/details.html` (241), `admin/tournaments/details.html` (**1841**, самый большой файл в проекте — регистрации + статусы + связанные действия) | `admin/tournaments/details.html` — по объёму это фактически "мега-дашборд одного турнира", закладывать больше всего времени именно на него |
| **Admin: домашняя панель** | `admin/panel.html` (238) | Уникальный |
| **Admin: управление платежами** | `admin/tournaments/payments.html` (297) | Структурно близко к "список с таблицей", но с формой ручной фиксации платежа внутри — отдельный архетип, не сводится к обычному list |

## 5. Админ-панель — управление live-турниром по формату

Отдельный архетип от обычного CRUD — это не "редактирование записи", а **пульт управления состоянием турнира** (раунды, счёт, переход дальше):

| Архетип | Файлы | Особенность |
|---|---|---|
| **Live-контроль King of Court** | `admin/tournaments/king-of-court.html` (261, **WebSocket**) | Прогресс раундов + ввод результатов в реальном времени |
| **Live-контроль Americano-семейства** | `admin/americano/tournament.html` (1121), `admin/americano/tournament-double.html` (529), `admin/americano/tournament-playoff.html` (715) | Тот же архетип "пульт управления", вариации на 3 формата (одиночный/парный/плей-офф). `tournament.html` (1121 строк) — самый полный |
| **Preview/симуляция раундов до старта** | `admin/americano/preview-rounds.html` (539), `admin/americano/preview-double-rounds.html` (302) | Отдельный от "live-контроля" архетип — показывает сгенерированную сетку до фактического запуска турнира, без live-состояния |

## 6. Email-шаблоны (не веб-экраны, отдельный трек)

11 файлов в `templates/email/`: `bienvenida`, `confirmacion`, `tournament-confirmation`, `new-partner-invitation`, `looking-for-partner-notification`, `pair-proposal`, `partner-data-reminder`, `vacancy-invitation`, `waitlist-notification`, `no-spots-left`, `admin-solo-registration`, `recuperar-password`. Останутся server-side HTML вне зависимости от фронтенд-стека — email-клиенты не рендерят React. Не нужно захватывать в Figma тем же процессом, разве что для консистентности бренда (используют ли те же цвета — см. `docs/design-tokens-as-is.md`).

## 7. Layout-фрагменты (не экраны — компоненты)

`fragments/public-header.html`, `fragments/public-footer.html`, `fragments/admin-header.html`, `fragments/admin-footer.html`, `fragments/king-of-court-modals.html`, `fragments/nivel-options.html` — переиспользуемые куски разметки. В React это `<PublicLayout>`, `<AdminLayout>`, переиспользуемые модалки/дропдауны, не отдельные экраны для мокапа.

---

## Открытые вопросы (стоит решить до/во время захвата в Figma)

1. **Единый realtime-подход для "живых" экранов** (§2, §5): сейчас только King of Court использует WebSocket, Americano-семейство — вероятно, поллинг/перезагрузка. При переносе на React стоит решить: все live-экраны переводим на единый WebSocket/SSE-подход, или оставляем смешанную модель как есть?
2. **`test/tournaments.html`** — подтвердить: не переносим в React вообще (остаётся Thymeleaf-инструмент для организатора) или всё же нужен где-то в новой архитектуре?
3. **Email-шаблоны** — сверять ли их с `design-tokens-as-is.md` сейчас (чтобы бренд был консистентным) или оставить как отдельную, более мелкую задачу на потом?
