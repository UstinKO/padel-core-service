# LFPT-374: Автогенерация названия турнира + статус мест на карточке

## Статус
approved

## Источник
[GitHub issue #374](https://github.com/UstinKO/padel-core-service/issues/374) — клиентский запрос (клуб Black Padel, доработка клубных аккаунтов и создания турниров), пункт 1 "Убрать ручное название турнира" + блок "Отображение наличия мест на карточке турнира", заведён архитектором напрямую как issue с технической постановкой, без промежуточного файла `specs/requests/`. По смыслу связан с #373 (полный список уровней турнира нужен для качественной генерации заголовка), но #373 уже задеплоен (`specs/done/LFPT-373-ampliar-niveles-torneo.md`), деплоится отдельно.

Telegram-сообщение (для reply): 4310

## Контекст / зачем

- `Tournament.nombre` — свободный текст, вводится организатором вручную на форме `admin/tournaments/form.html`. Валидации нет вообще: `TournamentDto` без Bean Validation аннотаций, хотя контроллеры используют `@Valid` — `bindingResult.hasErrors()` фактически никогда не сработает на пустое/некорректное `nombre`.
- Организаторы хотят убрать этот ручной ввод и генерировать заголовок автоматически из уже обязательных полей `generoFormato` + `categoriaNivel` (пример из issue — «Masculino · C6»).
- Публичная карточка турнира (`static/js/torneos.js`, `renderTournamentCard()`) не показывает `modalidad` (Парный/Индивидуальный) отдельным полем и не показывает адрес клуба (`clubDireccion` уже есть в `TournamentDto`/JSON, но не рендерится ни на карточке, ни на `tournament-details.html`).
- Количество мест на публичной карточке — голая дробь `Inscritos: X/Y`, без статуса "есть/заканчиваются/нет".
- Подсчёт занятых мест уже ветвится по `Modalidad` в `TournamentService.mapToDtoWithDetails()`/`mapToDtoWithBatchedData()`: для `DOBLES` считаются уникальные подтверждённые пары, для `INDIVIDUAL` — участники. Оба значения (`inscritosActuales`, `cupoMax`) уже присутствуют в `TournamentDto`/JSON, которым пользуется `torneos.js` — новых серверных вычислений для статуса не требуется, схема тем же данным.
- В админке точные цифры (`inscritosActuales`, `cupoMax`, `disponibles`) уже считаются и отображаются — эту часть трогать не нужно.

## Анализ (что реально происходит в коде)

- `Tournament.nombre` — `@Column(name = "nombre", nullable = false, length = 255)`, обычная строка, без CHECK-constraint. Схему менять не нужно: генерация происходит в Java-коде при создании/обновлении, старые записи с уже сохранённым вручную `nombre` не трогаются и продолжают отображаться как есть (не перезаписываются массово, миграция данных не нужна).
- `TournamentService.createTournament()` — маппит `TournamentDto` → `Tournament` через `tournamentMapper.toEntity()` и сохраняет; `updateTournamentFields()` (приватный метод, используется `updateTournament()`) копирует поля из DTO в существующую entity, включая `existing.setNombre(dto.getNombre())` — обе точки нужно поменять на автогенерацию.
- `AdminTournamentCopyController.copyTournament()` предзаполняет форму копии, включая `newTournament.setNombre(sourceTournament.getNombre() + " (copia)")` — это значение уходит только в `<h1>` шаблона копии (`${tournament.nombre}` в заголовке "Copiar Torneo: X", не в форму-поле, поле убирается этой же спекой) и не отправляется на сервер как значение поля формы — при сабмите заголовок всё равно перегенерируется из выбранных в форме `generoFormato`/`categoriaNivel`. Трогать не нужно.
- `admin/tournaments/form.html` — `<input th:field="*{nombre}">` (form-group, строки 88-99) с `required` — единственное место ручного ввода названия. `TournamentDto` не валидирует `nombre` ни на пустоту, ни на длину — удаление поля не требует правки `BindingResult`/контроллеров по валидации (нечего снимать).
- `Nivel.getDisplay()` возвращает `name()` (не человекочитаемый `orden`, известная особенность, зафиксированная как "не в скоупе" ещё в LFPT-373) — это уже используемое на форме турнира представление уровня (`th:text="${nivel.display}"`). Пример из issue «Masculino · C6» — `GenderFormat.MASCULINO.getValue()` ("Masculino") + `Nivel.C6.getDisplay()` ("C6") ровно это и даёт. Для составных уровней (`C9_C8` и т.п.) `getDisplay()` вернёт технический enum-код (`"C9_C8"`, не `"C9/C8"`) — существующее поведение, не в скоупе этой задачи (никто не просил чинить `getDisplay()`).
- `torneos.js renderTournamentCard()` уже использует `tournament.clubNombre`, но не `tournament.clubDireccion` (поле есть в JSON, просто не рендерится). `Modalidad` в JSON — `tournament.modalidad` (`"DOBLES"`/`"INDIVIDUAL"`), i18n-ключи `enum.modalidad.individual`/`enum.modalidad.doubles` уже существуют во всех трёх JS-файлах (`static/js/i18n/messages-{es,ru,en}.js`) и используются в `dashboard.js`/`home.js` — переиспользуем те же ключи, не дублируем.
- `tournament-details.html` — блок бейджей `.tournament-badges` уже показывает `generoFormato`, `categoriaNivel`, `tipo` (формат: KING_OF_COURT/AMERICANO/…), `estado`; `Modalidad` там не показан отдельным бейджем. Адрес клуба (`tournament.clubDireccion`) не показан нигде на странице — только `tournament.clubNombre` в info-grid.
- **Точная область замены "голой дроби" на статус — только публичная карточка (`torneos.js`), не `tournament-details.html`.** В "Что нужно сделать" issue формулировка "Убрать ручное название" и "Добавить на карточку" (п.2) явно перечисляют обе локации через `(публичную и на странице деталей)`, а формулировка п.3 "Статус мест" такую вторую локацию не называет — только "Контекст" явно цитирует голую дробь `torneos.js`. `tournament-details.html` и так не показывает голую дробь на карточке-превью — там отдельная страница с уже подробной информацией (включая список зарегистрированных игроков/пар, `#{tournament.participants.*}` секция) и отдельный `spots-available` виджет сайдбара, который использует точное число `availableSpots` для управления кнопкой регистрации (`register` vs `waitlist`) — это не "голая дробь на карточке", а функциональный элемент интерфейса регистрации. **Техническое решение** (см. `GIT_WORKFLOW.md` п.3 — решение оставлено на усмотрение реализации, кратко обосновано здесь): статус мест заменяет дробь только на публичной карточке `torneos.js`; `tournament-details.html` (info-grid "Inscritos actuales" и sidebar "N lugares disponibles") продолжает показывать точные цифры без изменений — они и так входят в явно перечисленное issue исключение "точное число видно только внутри турнира" (страница конкретного турнира — это и есть "внутри турнира").
- В проекте нет JS-тестовой инфраструктуры (`package.json`/jest отсутствуют) — вся клиентская логика (`torneos.js`, `home.js`, `dashboard.js` и т.д.) исторически не покрыта юнит-тестами, только браузерной QA-проверкой руками/Playwright. Логика статуса мест реализуется в `torneos.js` в этом же стиле (не заводится отдельная Java-абстракция ради in JS-only потребителя — YAGNI).

## Требования

### Функциональные

1. **Автогенерация названия турнира.**
   - Убрать `<input>` поле `nombre` (form-group, `admin/tournaments/form.html`, создание/редактирование/копирование — единая форма).
   - При создании (`TournamentService.createTournament()`) и обновлении (`TournamentService.updateTournamentFields()`) — `nombre` турнира генерируется на сервере из `generoFormato` + `categoriaNivel` по шаблону `"<GenderFormat.value> · <Nivel.display>"` (например, «Masculino · C6»); если один из компонентов отсутствует — используется только второй; если оба отсутствуют — служебное значение `"Torneo"` (защитный случай, на практике недостижим — оба поля обязательны на форме).
   - `Tournament.nombre` в БД не удаляется и не переименовывается, схема не меняется — генерация происходит в коде перед сохранением; уже существующие турниры с вручную сохранённым `nombre` не перезаписываются массово и продолжают открываться и отображаться как есть (обновление их `nombre` произойдёт только если организатор явно отредактирует и сохранит этот турнир через форму — естественное следствие того, что `updateTournamentFields` тоже регенерирует `nombre`, не бизнес-требование issue, но неизбежное и безвредное побочное поведение путём того же кода).
2. **Тип турнира и адрес клуба на карточке и странице деталей.**
   - Публичная карточка (`torneos.js`, `renderTournamentCard()`): добавить строку с `Modalidad` (использовать существующие i18n-ключи `enum.modalidad.individual`/`enum.modalidad.doubles`, не заводить новые) и строку с адресом клуба (`tournament.clubDireccion`), только если оно непустое.
   - `tournament-details.html`: добавить бейдж `Modalidad` в `.tournament-badges` (новые i18n-ключи `tournament.modality.individual`/`tournament.modality.doubles`, т.к. это server-rendered Thymeleaf, не JS) и новый `info-item` с адресом клуба (`tournament.clubDireccion`) в info-grid рядом с существующим `Club`, только если оно непустое (`th:if`).
3. **Статус мест на публичной карточке** (`torneos.js`) вместо голой дроби `Inscritos: X/Y`:
   - «Есть места» (`card.spots.available`) — по умолчанию.
   - «Últimos cupos» / «Последние места» / «Last spots» (`card.spots.limited`) — для `DOBLES`: `inscritosActuales >= 10`; для `INDIVIDUAL`: `inscritosActuales >= 15` (и ещё не заполнено полностью — см. следующий пункт, приоритет выше). Визуально выделено (цветной фон/текст, не просто нейтральная строка).
   - «Sin cupos» / «Мест нет» / «Sold out» (`card.spots.full`) — когда `inscritosActuales >= cupoMax` (приоритет над "последние места" — полностью заполненный турнир показывает "нет мест", даже если формально проходит и порог "последние места"). Визуально выделено.
   - `tournament-details.html` — без изменений (см. "Анализ" — точные цифры сохраняются, это отдельная, не карточная, локация).
   - В админке (`admin/tournaments/details.html`, списки) — без изменений, точные цифры остаются.
4. i18n: новые пользовательские строки — во все три языка проекта.
   - Java-свойства (`tournament-details.html`, бейдж Modalidad + метка адреса): `src/main/resources/i18n/messages_{es,ru,en}.properties` **и** `messages.properties` (дефолтный бандл, зеркалит `es`, судя по существующему содержимому).
   - JS-строки (карточка `torneos.js`, статус мест): `src/main/resources/static/js/i18n/messages-{es,ru,en}.js`. `Modalidad` на карточке переиспользует уже существующие `enum.modalidad.individual`/`.doubles` — новых JS-ключей для этого не требуется, только для статуса мест (`card.spots.available`/`.limited`/`.full`).

### Нефункциональные
- Без изменений схемы БД (нет новой Liquibase-миграции — `nombre` остаётся тем же `VARCHAR(255) NOT NULL`, никакого CHECK-constraint на него нет и не появляется).
- Без изменений REST/JSON-контракта `TournamentDto` — `nombre` остаётся полем DTO (сервер теперь сам его выставляет перед сохранением, JSON-ответ как и раньше содержит финальное значение).
- Безопасность: новых точек ввода данных нет; `nombre` перестаёт быть пользовательским вводом вообще (генерируется из уже провалидированных на уровне enum значений `generoFormato`/`categoriaNivel`) — площадь для XSS/некорректных данных в этом поле уменьшается, не увеличивается.

## Вне скоупа
- Починка `Nivel.getDisplay()` (возвращает технический `name()`, не человекочитаемый `orden` для составных уровней типа `C9_C8`) — существующая, задокументированная в LFPT-373 особенность, не входит в эту issue.
- Замена голой дроби статусом на `tournament-details.html` (info-grid "Inscritos actuales", sidebar "N lugares disponibles") — см. "Анализ", техническое решение оставить как есть.
- Изменение подсчёта `inscritosActuales`/`cupoMax`/`disponibles` в `TournamentService` — пороги "10 пар / 15 участников" ложатся на уже существующие вычисленные поля без изменений самого подсчёта.
- Расширение `Nivel`/`GenderFormat` — уже сделано в LFPT-373.
- Массовая миграция/перегенерация `nombre` у уже существующих турниров.
- Изменения в `home.js`/`dashboard.js` (у них свои варианты карточек турнира с похожей "голой дробью", но issue называет только `torneos.js` `renderTournamentCard()` как "публичную карточку").

## Изменения в системе

### API
Нет изменений в REST/JSON-контракте. `TournamentDto.nombre` как было, так и остаётся строкой в ответах — просто теперь всегда сгенерировано сервером, а не введено вручную.

### БД
Нет изменений, миграция не требуется.

### Backend
- `src/main/java/.../model/Tournament.java` — новый статический метод `generateNombre(GenderFormat generoFormato, Nivel categoriaNivel)`.
- `src/main/java/.../service/TournamentService.java`:
  - `createTournament()` — после маппинга DTO→entity выставлять `nombre` через `Tournament.generateNombre(...)`.
  - `updateTournamentFields()` — вместо `existing.setNombre(dto.getNombre())` генерировать `nombre` из уже выставленных на этом же вызове `generoFormato`/`categoriaNivel`.
- `src/main/java/.../controller/admin/AdminController.java` — убрать использование `tournamentDto.getNombre()` в предсохраняющем лог-сообщении (`createTournament()`, будет `null` после удаления поля формы) — заменить на что-то осмысленное (например, клуб/уровень) или убрать это конкретное упоминание из строки лога.

### UI
- `src/main/resources/templates/admin/tournaments/form.html` — убрать `form-group` с полем `nombre` (создание/редактирование/копирование — общий шаблон).
- `src/main/resources/templates/tournament-details.html` — новый бейдж `Modalidad` в `.tournament-badges`; новый `info-item` с адресом клуба (`th:if` на непустое значение).
- `src/main/resources/static/css/tournament-details.css` — стиль для нового `.badge-modalidad` (по аналогии с `.badge-level`/`.badge-type`, в обоих существующих блоках объявления бейджей).
- `src/main/resources/static/js/torneos.js` — `renderTournamentCard()`: добавить строки Modalidad + адрес клуба (только если непустой); заменить "Inscritos: X/Y" на статус-пилюлю (новый метод `getSpotsStatus(tournament)` с порогами по `Modalidad`).
- `src/main/resources/static/css/torneos.css` — стили `.torneo-spots-status--available/limited/full`.
- i18n: `messages_{es,ru,en}.properties` + `messages.properties` (новые ключи `tournament.info.address`, `tournament.modality.individual`, `tournament.modality.doubles`); `static/js/i18n/messages-{es,ru,en}.js` (новые ключи `card.spots.available`/`.limited`/`.full`).

## Критерии приёмки

- [ ] Форма создания/редактирования/копирования турнира (`admin/tournaments/form.html`) не содержит поля ручного ввода названия.
- [ ] Создание турнира с `generoFormato = MASCULINO`, `categoriaNivel = C6` через форму сохраняет `nombre = "Masculino · C6"` (юнит-тест на `Tournament.generateNombre`, плюс сквозная проверка через `TournamentService.createTournament`/репозиторий).
- [ ] Редактирование существующего турнира со сменой `categoriaNivel` (без изменения `generoFormato`) пересчитывает и сохраняет новый `nombre` в соответствии с новым уровнем.
- [ ] Существующий (созданный до этой фичи) турнир с вручную сохранённым `nombre` открывается на публичной странице/карточке/админке без ошибок и без принудительного изменения его `nombre`, пока его никто не отредактирует.
- [ ] На публичной карточке (`/torneos`) отображается тип турнира (Парный/Индивидуальный) и адрес клуба (когда он заполнен у клуба).
- [ ] На странице деталей турнира (`/torneo/{id}`) отображается бейдж типа (Парный/Индивидуальный) и адрес клуба (когда заполнен).
- [ ] На публичной карточке турнир с `Modalidad.DOBLES` и `inscritosActuales` от 0 до 9 показывает статус «Есть места»; от 10 (и не полностью занят) — «Últimos cupos»/«Последние места»/«Last spots», визуально выделенный.
- [ ] На публичной карточке турнир с `Modalidad.INDIVIDUAL` и `inscritosActuales` от 0 до 14 показывает «Есть места»; от 15 (и не полностью занят) — «Últimos cupos»/«Последние места»/«Last spots».
- [ ] На публичной карточке турнир с `inscritosActuales >= cupoMax` показывает «Sin cupos»/«Мест нет»/«Sold out» независимо от того, проходит ли он также порог "последние места".
- [ ] В админке (`admin/tournaments/list.html`, `admin/tournaments/details.html`) точные цифры (`inscritosActuales`/`cupoMax`/`disponibles`) не изменились — по-прежнему точное число, не статус.
- [ ] Новые тексты (модальность, адрес, статус мест) переведены и корректно отображаются на `es`/`ru`/`en` (проверка через `Accept-Language`).
- [ ] `./mvnw verify` — зелёный.

## Edge cases
- Клуб без заполненного адреса (`Club.direccion == null` и `zonaCiudad == null`, `getDireccionCompleta()` возвращает `null`) — строка адреса не рендерится ни на карточке, ни на странице деталей (не показывать пустую строку/`null`/`"undefined"`).
- Турнир с `categoriaNivel` в устаревшем (legacy) значении без привязки к полу (`PRINCIPIANTES`/`TODOS`, см. LFPT-373) — генерация названия по-прежнему работает (`getDisplay()` возвращает `name()` независимо от `generoAplicable`), просто в заголовке это будет читаться как «Masculino · TODOS» — не более странно, чем текущее отображение этого же значения на форме (`nivel.display`), не является регрессией.
- `inscritosActuales == cupoMax` — граница «Sin cupos», не «Últimos cupos» (`>=`, не `>`).
- Копирование турнира (`GET /admin/tournaments/{id}/copy`) — заголовок исходного турнира в `<h1>` копии ("Copiar Torneo: X (copia)") продолжает отображать старое (возможно, вручную введённое до этой фичи) название источника как справочную метку; само поле формы отсутствует, при сабмите сохраняется новое сгенерированное имя из выбранных в форме `generoFormato`/`categoriaNivel` (по умолчанию — те же, что у турнира-источника, если организатор их не менял).

## Открытые вопросы
Нет — оба пункта issue («убрать ручное название», «статус мест») однозначно описывают целевое поведение и пороги; единственная область, оставленная issue на усмотрение реализации (замена дроби статусом только на карточке или также на странице деталей), — техническое решение, обоснованное в разделе "Анализ" (не бизнес-вопрос — сама формулировка issue уже даёт достаточно сигнала через избирательное использование "(публичную и на странице деталей)" в п.2, но не в п.3, и явное перечисление "внутри турнира" как исключения).
