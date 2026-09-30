# LFPT-0448: Всплывающее окно подтверждения регистрации — отдельный текст для индивидуального турнира и Cancha Abierta

## Статус
done — PR #449, деплой подтверждён

## Источник
`specs/requests/LFPT-0448-registration-confirmation-popup.md` (клиентский запрос через Telegram, `telegram_launch_message_id: 4694`, роль отправителя — customer). Issue: [#448](https://github.com/UstinKO/padel-core-service/issues/448).

Telegram-сообщение (для reply): 4694

## Контекст / зачем

Сейчас после успешной регистрации на турнир игрок видит только generic-уведомление («Регистрация подтверждена!» / «Вы добавлены в список ожидания»), которое автоматически закрывается через 3 секунды (`showResultModal(...)` в `tournament-details.js`/`dashboard.js`). Игрок не успевает прочитать и не видит важных условий участия (правило отмены за 24 часа, оплата при поздней отмене, штраф −10 очков за опоздание) — это приводит к недопониманию постфактум. Нужно показать полноценное информационное окно сразу после подтверждённой регистрации, с текстом, зависящим от типа мероприятия: у индивидуальных турниров (Americano, King of Court) есть турнирные санкции, у Cancha Abierta их нет.

## Связь с MASTER.md

Не меняет ни один инвариант `MASTER.md` (статусы регистрации, форматы турниров и т.д.) — чисто презентационная фича поверх уже существующего флоу регистрации (`RegistrationStatus.CONFIRMED`, раздел «Статусы регистрации на турнир»). `TournamentType.CANCHA_ABIERTA` уже описан как отдельный формат в `.claude/CLAUDE.md`.

## Требования

### Функциональные

1. После успешной **индивидуальной** регистрации игрока на турнир (`POST /players/tournaments/{tournamentId}/register`, ответ `{success:true, status:'CONFIRMED'}`) вместо текущего generic `showResultModal('success', ...)` показывается новое окно `#registrationConfirmModal` с содержанием, зависящим от `tournament.tipo`:
   - `tipo` = `AMERICANO` или `KING_OF_COURT` → вариант **A** (полный, с блоком «Важно знать»).
   - `tipo` = `CANCHA_ABIERTA` → вариант **B** (короткий, без санкций).
   - `tipo` = `AMERICANO_TEAMS` не встречается на этом пути (регистрация парная, использует отдельный endpoint/модалку — см. «Вне скоупа»).
2. Показывается на обеих существующих точках, где сегодня вызывается generic `showResultModal` после успеха этого endpoint'а:
   - `tournament-details.html` / `tournament-details.js` — обработчик клика `.btn-register` (сейчас строка ~374: `showResultModal('success', t('details.success.registered'), msg)`), тип турнира берётся из уже существующего `window.tournament.tipo`.
   - `players/dashboard.html` / `dashboard.js` — `handleRegistration()` (сейчас строка ~393: `showResultModal('success', msg)`), тип турнира берётся из уже резолвленной локальной переменной `tournament` (`tournaments.find(t => t.id === parseInt(tournamentId))`), поле `tournament.tipo`.
3. Если `status !== 'CONFIRMED'` (т.е. `WAITLIST`) — новое окно **не** показывается, вызывается прежний `showResultModal(...)` как сейчас (без изменений).
4. Контент варианта A (RU):
   ```
   Регистрация подтверждена!
   Вы успешно записались на турнир.

   Важно знать:
   — Отмена участия: вы можете отменить регистрацию без оплаты не позднее чем за 24 часа до начала турнира.
   — При отмене менее чем за 24 часа: участие оплачивается полностью, так как корты уже забронированы и подлежат оплате клубу.
   — Возврат возможен: если найдём другого игрока, который займёт ваше место и полностью оплатит участие, оплата с вас не взимается или возвращается.
   — Пожалуйста, приезжайте заранее: просим прибыть за 15 минут до начала турнира.
   — Опоздание на турнир: если вы приходите позже начала турнира, вы начинаете турнир со стартовым результатом −10 очков.

   [Понятно]
   ```
   ES — см. «Точный текст по языкам» ниже. Контент варианта B (RU):
   ```
   Регистрация подтверждена!
   Вы записались на Cancha Abierta.

   Пожалуйста, приезжайте заранее: просим прибыть примерно за 15 минут до начала.
   Если не сможете прийти: пожалуйста, заранее сообщите организатору, чтобы освободившееся место смог занять другой игрок.

   [Понятно]
   ```
5. Кнопка «Понятно»/«Entendido» закрывает окно и перезагружает страницу (`window.location.reload()`) — сохраняет существующее поведение (обновление состояния кнопки регистрации/отмены), но только по явному клику, без автозакрытия по таймеру (в отличие от текущего `showResultModal`, у которого 3-секундный авто-reload — этого времени недостаточно, чтобы прочитать текст).
6. Внешний вид: переиспользовать существующие CSS-классы модалок проекта (`.modal`, `.modal-dialog`, `.modal-content`, `.modal-body`, `.modal-footer`, стиль модалки `tournament-details.css`/`dashboard.css`) плюс один светлый информационный блок (фон в духе уже используемых в проекте `#fff7f4`/`#fff1eb`) для списка «Важно знать» — без новых иконок-картинок на пункт (упрощение относительно приложенного макета, макет — ориентир по структуре/тону, не пиксель-в-пиксель), большая иконка-галочка (`fa-check-circle`, зелёная, как в текущем `showResultModal`) в шапке окна. Не создавать ощущение «наказания» — не красный/не предупреждающий стиль.

### Нефункциональные

- **i18n**: новые строки — только в JS-i18n (`static/js/i18n/messages-{ru,es,en}.js`), т.к. окно строится динамически в JS (сложившийся в проекте паттерн для этого конкретного компонента — `showResultModal` тоже собирает HTML через `t()` в JS, а не через Thymeleaf `#{...}`, см. `.claude/CLAUDE.md` §i18n). Новые ключи — в неймспейсе `regConfirm.*`, дублируются в оба JS-файла, где нужны (`tournament-details.js` уже грузит `messages-{lang}.js` через `#locale.language`, `dashboard.js` — так же).
- Безопасность: новых точек входа данных от пользователя нет — окно строится из уже присутствующих на странице/в ответе API данных (`tournament.tipo`, `status`), новых полей на бэкенде не требуется.
- Производительность: не применимо, чисто клиентская фича.

### Точный текст по языкам

**Вариант A — заголовок и подзаголовок**

| Ключ | RU | ES | EN |
|---|---|---|---|
| `regConfirm.title` | Регистрация подтверждена! | ¡Inscripción confirmada! | Registration confirmed! |
| `regConfirm.individual.subtitle` | Вы успешно записались на турнир. | Ya estás inscripto en el torneo. | You're successfully registered for the tournament. |
| `regConfirm.individual.rulesTitle` | Важно знать | Información importante | Important to know |
| `regConfirm.individual.rule1` | **Отмена участия.** Вы можете отменить регистрацию без оплаты не позднее чем за 24 часа до начала турнира. | **Cancelación.** Podés cancelar tu participación sin cargo hasta 24 horas antes del inicio del torneo. | **Cancelling.** You can cancel free of charge up to 24 hours before the tournament starts. |
| `regConfirm.individual.rule2` | **При отмене менее чем за 24 часа** участие оплачивается полностью, так как корты уже забронированы и подлежат оплате клубу. | **Si cancelás después de ese plazo,** deberás abonar el valor total de la inscripción, ya que las canchas ya están reservadas. | **If you cancel after that,** the full fee applies, since the courts are already booked and paid to the club. |
| `regConfirm.individual.rule3` | **Возврат возможен.** Если найдём другого игрока, который займёт ваше место и полностью оплатит участие, оплата с вас не взимается или возвращается. | **Reintegro posible.** Si conseguimos otro jugador que ocupe tu lugar y abone la inscripción, no tendrás que pagar o se realizará el reintegro correspondiente. | **Refund possible.** If we find another player to take your spot and pay in full, you won't be charged, or you'll be refunded. |
| `regConfirm.individual.rule4` | **Приезжайте заранее.** Просим прибыть за 15 минут до начала турнира. | **Llegá antes.** Te pedimos llegar 15 minutos antes del inicio del torneo. | **Arrive early.** Please arrive 15 minutes before the tournament starts. |
| `regConfirm.individual.rule5` | **Опоздание.** Если вы приходите позже начала турнира, вы начинаете турнир со стартовым результатом −10 очков. | **Llegada tarde.** Si llegás tarde, comenzarás el torneo con −10 puntos. | **Late arrival.** If you arrive late, you'll start the tournament with −10 points. |
| `regConfirm.btn.ok` | Понятно | Entendido | Got it |

**Вариант B — Cancha Abierta**

| Ключ | RU | ES | EN |
|---|---|---|---|
| `regConfirm.cancha.subtitle` | Вы записались на Cancha Abierta. | Ya estás inscripto en la Cancha Abierta. | You're registered for Cancha Abierta. |
| `regConfirm.cancha.rule1` | **Приезжайте заранее.** Просим прибыть примерно за 15 минут до начала. | **Llegá antes.** Te pedimos llegar aproximadamente 15 minutos antes del inicio. | **Arrive early.** Please arrive about 15 minutes before the start. |
| `regConfirm.cancha.rule2` | **Если не сможете прийти,** пожалуйста, заранее сообщите организатору, чтобы освободившееся место смог занять другой игрок. | **Si finalmente no podés venir,** por favor avisá al organizador con anticipación, para que podamos liberar tu lugar para otro jugador. | **If you can't make it,** please let the organizer know in advance so someone else can take your spot. |

`regConfirm.title` и `regConfirm.btn.ok` общие для обоих вариантов.

## Вне скоупа

- Парная/командная регистрация (`AMERICANO_TEAMS`, `POST /api/tournaments/double/...` — прямая регистрация пары, «ищу пару», подтверждение партнёра) — в клиентском запросе описаны только два случая, «индивидуальный турнир» и «Cancha Abierta»; парная регистрация не упомянута и использует свой отдельный, уже существующий флоу сообщений (`details.success.partner_confirmed` и т.п.) — не трогаем.
- Статус `WAITLIST` (лист ожидания) — текущее сообщение остаётся как есть.
- Регистрация игрока администратором из админ-панели (`AmericanoViewController`/`AmericanoApiController`, `@PreAuthorize` только для ролей организатора) — не игрок регистрирует сам себя, окно не показывается.
- Изменение поведения общего `#resultModal`/`showResultModal(...)` для остальных сценариев (ошибки, лист ожидания, парная регистрация) — не меняем, добавляем отдельный новый модал только для описанных двух случаев.
- Пиксель-точное повторение приложенного макета (иконка-картинка ракетки/мяча, цветная иконка на каждый пункт правил) — используется как стилистический ориентир, не как точный дизайн-спек.

## Изменения в системе

### API
Нет новых/изменённых endpoint'ов — используется существующий ответ `POST /players/tournaments/{tournamentId}/register` (`{success, status, ...}`), `tournament.tipo` уже присутствует в данных, доступных на обеих страницах.

### БД
Нет.

### UI

- `src/main/resources/templates/tournament-details.html` — новый блок модалки `#registrationConfirmModal` рядом с существующим `#resultModal` (~строка 1028).
- `src/main/resources/static/js/tournament-details.js` — новая функция `showRegistrationConfirmModal(tournamentType)`; вызов вместо `showResultModal(...)` в ветке `data.status === 'CONFIRMED'` (~строка 372-374); кнопка OK — закрыть + `window.location.reload()`.
- `src/main/resources/templates/players/dashboard.html` — новый блок модалки `#registrationConfirmModal` рядом с существующим `#resultModal` (~строка 447).
- `src/main/resources/static/js/dashboard.js` — аналогичная функция `showRegistrationConfirmModal(tournamentType)`; вызов в `handleRegistration()` в ветке `data.status === 'CONFIRMED'` (~строка 391-393), используя уже резолвленный `tournament.tipo`.
- `src/main/resources/static/js/i18n/messages-ru.js`, `messages-es.js`, `messages-en.js` — новые ключи `regConfirm.*` (см. таблицы выше).
- `src/main/resources/static/css/tournament-details.css`, `src/main/resources/static/css/dashboard.css` — стили нового модала/информационного блока (переиспользовать существующие цвета/паттерны `.modal`, светлый фон блока).

## Критерии приёмки

- [ ] Индивидуальная регистрация (Americano) со страницы турнира, `status=CONFIRMED` → показывается окно варианта A с заголовком «Регистрация подтверждена!», подзаголовком и всеми 5 пунктами «Важно знать» (включая явное упоминание −10 очков за опоздание), кнопка «Понятно».
- [ ] То же для King of Court со страницы турнира — тот же вариант A.
- [ ] То же с дашборда (`/players/dashboard`) для Americano/King of Court — вариант A, тот же контент.
- [ ] Регистрация на Cancha Abierta (`status=CONFIRMED`) со страницы турнира и с дашборда → показывается вариант B (только 2 пункта: приехать заранее, предупредить при отмене), без упоминания оплаты/санкций/−10 очков.
- [ ] Регистрация со `status=WAITLIST` (любой формат) — новое окно не показывается, старое сообщение («Вы добавлены в список ожидания») — как было.
- [ ] Клик «Понятно» закрывает окно и перезагружает страницу (кнопка регистрации корректно меняется на «Отменить участие»/статус, как и раньше при reload).
- [ ] Переключение языка браузера/`Accept-Language` на ES/EN — весь текст окна (оба варианта) отображается на соответствующем языке без смешения языков.
- [ ] Парная регистрация (Team Americano, `add-partner`/`register` через `/api/tournaments/double/...`) — поведение не изменилось, показывается прежнее сообщение.
- [ ] `./mvnw compile -DskipTests` и `./mvnw verify` — зелёные.

## Edge cases

- Игрок регистрируется на Cancha Abierta, у которой (гипотетически) `Modalidad.DOBLES` — по коду эта задача затрагивает только путь через `POST /players/tournaments/{id}/register` (одиночная регистрация); если конкретный турнир Cancha Abierta регистрируется через парный флоу, новое окно не показывается (см. «Вне скоупа» — не наша ветка кода). Это не расхождение со спекой, а следствие того, что парный путь вне скоупа независимо от типа турнира.
- `tournament.tipo` на дашборде не резолвится (гонка данных/устаревший кэш `tournaments`) — в этом случае (крайне маловероятном, т.к. `tournament` уже резолвится синхронно до фактического запроса на регистрацию в существующем коде) достаточно fallback на вариант A (более полный, консервативный по умолчанию — лучше показать лишний важный пункт, чем скрыть реальные правила).
- Двойной клик/повторная отправка формы — не в скоупе этой задачи (существующая защита от повторного клика — `btn.disabled = true` — не меняется).

## Открытые вопросы

Нет открытых вопросов. Единственная неоднозначность исходного запроса (применимо ли к парной/командной регистрации) закрыта техническим решением: запрос описывает ровно два случая — «индивидуальный турнир» и «Cancha Abierta» — и оба они физически проходят через один и тот же одиночный endpoint регистрации; парная регистрация — отдельный, не упомянутый в запросе флоу с собственными сообщениями, оставлена как есть (см. «Вне скоупа»).
