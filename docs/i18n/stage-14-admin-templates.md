# Этап 14: Интеграция — замена строк в admin HTML шаблонах

## Цель

Заменить хардкоженные испанские строки в 16 admin-шаблонах на `#{key}`.  
Все ключи уже определены в этапах 11-13.

---

## Паттерн: подключение i18n в admin шаблоны

В `<head>` каждого admin-шаблона добавить **перед закрывающим `</head>`**:

```html
<!-- i18n -->
<script th:src="@{/js/i18n/t.js}"></script>
<script th:with="lang=${#locale.language == 'ru' ? 'ru' : (#locale.language == 'en' ? 'en' : 'es')}"
        th:src="@{'/js/i18n/messages-' + ${lang} + '.js'}"></script>
```

Обновить `<html>` тег:
```html
<html lang="es" th:lang="${#locale.language}" xmlns:th="http://www.thymeleaf.org">
```

> Если шаблоны подключают `admin-header.html` через `th:replace`, достаточно добавить скрипты один раз в fragment — не в каждый шаблон отдельно.

---

## Thymeleaf синтаксис (напоминание)

```html
<!-- Текст тега -->
<h1 th:text="#{admin.panel.title}">Panel de Administración</h1>

<!-- Текст с HTML (ключи помеченные # [HTML] в этапе 11) -->
<p th:utext="#{admin.tournaments.details.teams.min_teams}">...</p>

<!-- Placeholder атрибут -->
<input th:placeholder="#{admin.tournaments.form.field.name.placeholder}">

<!-- Строка с переменной -->
<span th:text="#{admin.koc.court(${courtNumber})}">Cancha 1</span>

<!-- Условный перевод -->
<span th:text="${tournament.id == null} ? #{admin.tournaments.form.title.create} : #{admin.tournaments.form.title.edit}">...</span>

<!-- title / aria-label -->
<button th:title="#{admin.common.btn.edit}">✏️</button>
```

---

## Шаблон за шаблоном

### Приоритет (от простого к сложному)

| Приоритет | Файл | Строк | Риск |
|-----------|------|------:|------|
| 1 | `admin/panel.html` | 16 | Низкий |
| 2 | `admin/clubs/list.html` | 21 | Низкий |
| 3 | `admin/clubs/form.html` | 13 | Низкий |
| 4 | `admin/clubs/details.html` | 17 | Низкий |
| 5 | `admin/players/list.html` | 20 | Низкий |
| 6 | `admin/tournaments/king-of-court.html` | 11 | Низкий |
| 7 | `admin/tournaments/list.html` | 26 | Низкий |
| 8 | `admin/americano/preview-rounds.html` | 22 | Низкий |
| 9 | `admin/americano/preview-double-rounds.html` | 24 | Низкий |
| 10 | `admin/players/details.html` | 29 | Средний |
| 11 | `admin/tournaments/payments.html` | 27 | Средний |
| 12 | `admin/americano/tournament.html` | 32 | Средний |
| 13 | `admin/americano/tournament-double.html` | 27 | Средний |
| 14 | `admin/tournaments/form.html` | 47 | Средний |
| 15 | `admin/americano/tournament-playoff.html` | 65 | Высокий |
| 16 | `admin/tournaments/details.html` | 75 | Высокий |

---

### `admin/panel.html`

```html
<!-- БЫЛО -->
<title>Panel de Administración - 1-Padel</title>
<p class="admin-subtitle">Gestión de jugadores y torneos 1-Padel</p>
<h3 class="stat-label">Total jugadores</h3>
<h3 class="stat-label">Administradores</h3>
<h3 class="stat-label">Torneos activos</h3>
<h3 class="stat-label">Lista de espera</h3>
<span class="name">Rondas calibración:</span>
<span class="name">Canchas:</span>
<p>No hay jugadores registrados</p>
<p>No hay torneos creados</p>

<!-- СТАЛО -->
<title th:text="#{admin.panel.title} + ' - 1-Padel'">Panel de Administración - 1-Padel</title>
<p class="admin-subtitle" th:text="#{admin.panel.subtitle}">Gestión de jugadores y torneos 1-Padel</p>
<h3 class="stat-label" th:text="#{admin.panel.stat.players}">Total jugadores</h3>
<h3 class="stat-label" th:text="#{admin.panel.stat.admins}">Administradores</h3>
<h3 class="stat-label" th:text="#{admin.panel.stat.active_tournaments}">Torneos activos</h3>
<h3 class="stat-label" th:text="#{admin.panel.stat.waitlist}">Lista de espera</h3>
<span class="name" th:text="#{admin.panel.koc.rounds}">Rondas calibración:</span>
<span class="name" th:text="#{admin.panel.koc.courts}">Canchas:</span>
<p th:text="#{admin.panel.players.empty}">No hay jugadores registrados</p>
<p th:text="#{admin.panel.tournaments.empty}">No hay torneos creados</p>
```

---

### `admin/clubs/list.html`

```html
<!-- Заголовок страницы -->
<title th:text="#{admin.clubs.list.title} + ' - 1-Padel Admin'">Gestión de Clubes - 1-Padel Admin</title>
<p class="admin-subtitle" th:text="#{admin.clubs.list.subtitle}">Administración de clubes 1-Padel</p>

<!-- Колонки таблицы — часть из admin.common.*, часть из admin.clubs.list.col.* -->
<th th:text="#{admin.common.col.id}">ID</th>
<th th:text="#{admin.common.col.name}">Nombre</th>
<th th:text="#{admin.clubs.list.col.city}">Ciudad/Zona</th>
<th th:text="#{admin.clubs.list.col.phone}">Teléfono</th>
<th th:text="#{admin.common.col.email}">Email</th>
<th th:text="#{admin.common.col.status}">Estado</th>
<th th:text="#{admin.common.col.actions}">Acciones</th>

<!-- Пустое состояние -->
<h3 th:text="#{admin.clubs.list.empty.title}">No hay clubes</h3>
<p th:text="#{admin.clubs.list.empty.text}">Comienza creando tu primer club</p>
```

---

### `admin/tournaments/form.html`

Особенность: `<title>` зависит от `tournament.id`.

```html
<!-- БЫЛО -->
<title th:if="${tournament.id == null}">Crear Torneo - 1-Padel Admin</title>
<title th:unless="${tournament.id == null}">Editar Torneo - 1-Padel Admin</title>

<!-- СТАЛО -->
<title th:text="${tournament.id == null} ? #{admin.tournaments.form.title.create} + ' - 1-Padel Admin' : #{admin.tournaments.form.title.edit} + ' - 1-Padel Admin'">
    Crear Torneo - 1-Padel Admin
</title>

<!-- Labels -->
<label for="clubId" class="required" th:text="#{admin.tournaments.form.field.club}">Club</label>
<option value="" th:text="#{admin.tournaments.form.field.club.select}">Seleccione un club</option>

<!-- Placeholders -->
<input th:placeholder="#{admin.tournaments.form.field.name.placeholder}" ...>

<!-- Опции select — статические значения -->
<option value="KING_OF_COURT" th:text="#{admin.tournaments.form.field.type.koc}">Rey de Cancha</option>
<option value="AMERICANO" th:text="#{admin.tournaments.form.field.type.americano}">Americano (Individual)</option>
<option value="AMERICANO_TEAMS" th:text="#{admin.tournaments.form.field.type.americano_teams}">...</option>
<option value="CANCHA_ABIERTA" th:text="#{admin.tournaments.form.field.type.cancha_abierta}">...</option>

<!-- Сообщение об ошибке поля -->
<div th:errors="*{nombre}" th:text="#{admin.common.form.field_error}">Error en el campo</div>
```

---

### `admin/tournaments/details.html`

Самый сложный файл. Обратить внимание на:
1. Строки с `# [HTML]` — использовать `th:utext`, не `th:text`
2. Динамические статусы: `th:if="${isPlayoffStarted == true}"` — оба варианта переводить

```html
<!-- Статусы блока команд -->
<span th:if="${isPlayoffStarted == true}"
      th:text="#{admin.tournaments.details.teams.status.playoff}">Playoff en curso</span>
<span th:unless="${isPlayoffStarted == true}"
      th:text="#{admin.tournaments.details.teams.status.qualification}">Calificación en curso</span>

<!-- HTML-строки (th:utext!) -->
<p th:utext="#{admin.tournaments.details.teams.min_teams}">
    Se necesitan <strong>mínimo 4 equipos</strong> cargados.
</p>
<p th:utext="#{admin.tournaments.details.teams.format.full}">
    <strong>Full</strong>: cada par juega...
</p>
```

---

### `admin/americano/tournament-playoff.html`

Второй по объёму. Особенности:
- Статусы матчей могут генерироваться в JS (пересечение с этапом 15)
- Bracket секция с динамическими раундами: `#{admin.playoff.bracket.round(roundNumber)}`

```html
<span th:text="#{admin.playoff.bracket.round(${round.number})}">Ronda 1</span>
<span th:text="#{admin.playoff.bracket.tbd}">Por determinar</span>
```

---

## Обработка `th:errors` — унификация

Во всех формах заменить хардкоженный fallback:
```html
<!-- БЫЛО -->
<div th:errors="*{campo}">Error en el campo</div>

<!-- СТАЛО -->
<div th:errors="*{campo}" th:text="#{admin.common.form.field_error}">Error en el campo</div>
```

> Примечание: `th:errors` уже содержит текст ошибки валидации от Spring. `th:text` на этом элементе — fallback когда ошибок нет и элемент скрыт. Это не конфликтует.

---

## Чеклист этапа 14

### Общее
- [ ] `<html th:lang="${#locale.language}">` добавлен во все 16 шаблонов
- [ ] i18n скрипты подключены (через fragment или напрямую)

### Шаблоны (по одному)
- [ ] `admin/panel.html`
- [ ] `admin/clubs/list.html`
- [ ] `admin/clubs/form.html`
- [ ] `admin/clubs/details.html`
- [ ] `admin/players/list.html`
- [ ] `admin/tournaments/king-of-court.html`
- [ ] `admin/tournaments/list.html`
- [ ] `admin/americano/preview-rounds.html`
- [ ] `admin/americano/preview-double-rounds.html`
- [ ] `admin/players/details.html`
- [ ] `admin/tournaments/payments.html`
- [ ] `admin/americano/tournament.html`
- [ ] `admin/americano/tournament-double.html`
- [ ] `admin/tournaments/form.html`
- [ ] `admin/americano/tournament-playoff.html`
- [ ] `admin/tournaments/details.html`

### Проверка после каждого шаблона
- [ ] Нет хардкоженных испанских строк (кроме: имена собственные, технические коды)
- [ ] `th:utext` используется для ключей с `# [HTML]`
- [ ] Placeholder атрибуты заменены через `th:placeholder`
- [ ] Страница открывается без `NoSuchMessageException`
