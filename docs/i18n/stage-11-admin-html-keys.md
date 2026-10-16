# Этап 11: Ключи для HTML шаблонов Admin → messages_es.properties

## Цель

Выписать все хардкоженные испанские строки из 16 admin-шаблонов как ключи в `messages_es.properties`.  
**Шаблоны ещё не меняем** — только составляем inventory ключей.

---

## Соглашения

**Пространство имён**: все admin-ключи начинаются с `admin.`  
**Структура**: `admin.<секция>.<элемент>`  
**Общие ключи** (используются на нескольких страницах): `admin.common.*`  
**Строки с HTML** (`<strong>`, `<a>`): помечены `# [HTML]` — потребуют `th:utext` при интеграции  
**Строки с переменными**: формат `{0}`, `{1}` (MessageFormat)

---

## Общие admin-ключи (`admin.common.*`)

Используются на нескольких страницах — выносим в `admin.common`:

```properties
# Кнопки (часть общих из common.* переиспользуем напрямую)
admin.common.btn.new_tournament=Nuevo torneo
admin.common.btn.new_club=Nuevo club
admin.common.btn.export=Exportar Excel
admin.common.btn.view=Ver
admin.common.btn.manage=Gestionar
admin.common.btn.activate=Activar
admin.common.btn.deactivate=Desactivar
admin.common.btn.toggle=Cambiar estado

# Колонки таблиц (разделяемые)
admin.common.col.id=ID
admin.common.col.name=Nombre
admin.common.col.club=Club
admin.common.col.date=Fecha
admin.common.col.status=Estado
admin.common.col.actions=Acciones
admin.common.col.email=Email
admin.common.col.phone=Teléfono

# Пустые состояния
admin.common.empty=Sin datos disponibles

# Ошибка в поле формы
admin.common.form.field_error=Error en el campo
admin.common.form.select_placeholder=Seleccione...
```

---

## Навигация / admin-header (`admin.nav.*`)

Шаблон: `fragments/admin-header.html` (~1 строка)

```properties
admin.nav.panel=Panel Admin
admin.nav.tournaments=Torneos
admin.nav.clubs=Clubes
admin.nav.players=Jugadores
admin.nav.export=Exportar
admin.nav.site=Ver sitio
admin.nav.logout=Salir
```

---

## Panel principal (`admin.panel.*`)

Шаблон: `admin/panel.html` (~16 строк)

```properties
admin.panel.title=Panel de Administración
admin.panel.subtitle=Gestión de jugadores y torneos 1-Padel

# Статистика
admin.panel.stat.players=Total jugadores
admin.panel.stat.admins=Administradores
admin.panel.stat.active_tournaments=Torneos activos
admin.panel.stat.waitlist=Lista de espera

# Виджеты KoC
admin.panel.koc.rounds=Rondas calibración:
admin.panel.koc.courts=Canchas:

# Пустые состояния
admin.panel.players.empty=No hay jugadores registrados
admin.panel.tournaments.empty=No hay torneos creados
```

---

## Lista de torneos (`admin.tournaments.list.*`)

Шаблон: `admin/tournaments/list.html` (~26 строк)

```properties
admin.tournaments.list.title=Administración de Torneos
admin.tournaments.list.subtitle=Administra todos los torneos de la plataforma

# Колонки таблицы
admin.tournaments.list.col.info=Info
admin.tournaments.list.col.registered=Inscritos

# Пустое состояние
admin.tournaments.list.empty.title=No hay torneos
admin.tournaments.list.empty.text=Comienza creando tu primer torneo
```

---

## Formulario de torneo (`admin.tournaments.form.*`)

Шаблон: `admin/tournaments/form.html` (~47 строк)

```properties
admin.tournaments.form.title.create=Crear Torneo
admin.tournaments.form.title.edit=Editar Torneo
admin.tournaments.form.subtitle=Complete los detalles del torneo a continuación

# Поля
admin.tournaments.form.field.club=Club
admin.tournaments.form.field.club.select=Seleccione un club
admin.tournaments.form.field.name=Nombre del Torneo
admin.tournaments.form.field.name.placeholder=Ej: Torneo Open Verano 2026
admin.tournaments.form.field.date_start=Fecha de Inicio
admin.tournaments.form.field.time_start=Hora de Inicio
admin.tournaments.form.field.duration=Duración Estimada
admin.tournaments.form.field.duration.placeholder=Ej: 4 horas, 2 días
admin.tournaments.form.field.gender=Formato de Género
admin.tournaments.form.field.gender.select=Seleccione formato
admin.tournaments.form.field.level=Categoría / Nivel
admin.tournaments.form.field.level.select=Seleccione nivel
admin.tournaments.form.field.type=Tipo de Torneo
admin.tournaments.form.field.type.select=Seleccione tipo
admin.tournaments.form.field.type.koc=Rey de Cancha
admin.tournaments.form.field.type.americano=Americano (Individual)
admin.tournaments.form.field.type.americano_teams=Americano Parejas (Calificación + Playoff)
admin.tournaments.form.field.type.cancha_abierta=Cancha Abierta
admin.tournaments.form.field.modality=Modalidad de Juego
admin.tournaments.form.field.modality.select=Seleccione modalidad
admin.tournaments.form.field.capacity=Cupo Máximo
admin.tournaments.form.field.price=Precio
admin.tournaments.form.field.currency=Moneda
admin.tournaments.form.field.currency.ars=ARS (Peso Argentino)
admin.tournaments.form.field.currency.usd=USD (Dólar)
admin.tournaments.form.field.currency.usdt=USDT (Tether)
admin.tournaments.form.field.currency.eur=EUR (Euro)
admin.tournaments.form.field.status=Estado del Torneo
admin.tournaments.form.field.deadline=Fecha Límite de Cancelación
admin.tournaments.form.field.description=Descripción Detallada
admin.tournaments.form.field.description.placeholder=Información adicional sobre el torneo...
admin.tournaments.form.field.contact=Contacto del Organizador
admin.tournaments.form.field.contact.placeholder=Ej: Juan Pérez - 123456789
admin.tournaments.form.field.faq=URL de Preguntas Frecuentes
admin.tournaments.form.field.faq.placeholder=https://ejemplo.com/faq
```

---

## Detalles del torneo (`admin.tournaments.details.*`)

Шаблон: `admin/tournaments/details.html` (~75 строк — самый объёмный)

```properties
# Информационные метки
admin.tournaments.details.info.cancel_deadline=Cancelación hasta
admin.tournaments.details.info.club=Club
admin.tournaments.details.info.duration=Duración
admin.tournaments.details.info.date=Fecha de inicio

# Статистика
admin.tournaments.details.stat.attended=Asistieron:
admin.tournaments.details.stat.paid=Pagados:
admin.tournaments.details.stat.total_paid=Total Pagado:

# Колонки таблицы регистраций
admin.tournaments.details.col.pair=Pareja
admin.tournaments.details.col.pair_contact=Contacto pareja

# Пустое состояние
admin.tournaments.details.empty_participants=No hay participantes confirmados

# === Блок Americano Teams (Team Playoff) ===
admin.tournaments.details.teams.lbl.bye=Bye por ronda:
admin.tournaments.details.teams.lbl.courts_suggested=Canchas sugeridas:
admin.tournaments.details.teams.lbl.teams_loaded_manual=Equipos cargados (manual):
admin.tournaments.details.teams.lbl.format_expected=Formato esperado:
admin.tournaments.details.teams.lbl.confirmed=Inscriptos confirmados:
admin.tournaments.details.teams.lbl.players_confirmed=Jugadores confirmados:
admin.tournaments.details.teams.lbl.players_per_round=Jugadores por ronda:
admin.tournaments.details.teams.lbl.pairs=Parejas inscritas:
admin.tournaments.details.teams.lbl.rounds_suggested=Rondas sugeridas:
admin.tournaments.details.teams.lbl.teams_loaded=Equipos cargados:
admin.tournaments.details.teams.lbl.min_required=Mínimo requerido:
admin.tournaments.details.teams.min_teams=Se necesitan <strong>mínimo 4 equipos</strong> cargados.    # [HTML]
admin.tournaments.details.teams.step1=Paso 1 — Cargar equipos
admin.tournaments.details.teams.step2=Paso 2 — Iniciar calificación
admin.tournaments.details.teams.calibration_rounds=Rondas de calibración
admin.tournaments.details.teams.courts_count=Número de canchas
admin.tournaments.details.teams.status.playoff=Playoff en curso
admin.tournaments.details.teams.status.qualification=Calificación en curso
admin.tournaments.details.teams.format.desc=<strong>Formato Americano Parejas</strong>: cada equipo juega   # [HTML]
admin.tournaments.details.teams.format.playoff_suffix=ej. 6:4). Los mejores equipos pasan al <strong>playoff eliminatorio</strong>.    # [HTML]
admin.tournaments.details.teams.format.playoff_plus=+ <strong>Playoff eliminatorio</strong>.    # [HTML]
admin.tournaments.details.teams.format.full=<strong>Full</strong>: cada par juega juntos exactamente 1 vez, sin repeticiones.    # [HTML]
admin.tournaments.details.teams.format.flex=<strong>Flex</strong>: formato flexible, minimiza repeticiones.    # [HTML]
admin.tournaments.details.teams.format.qualification=<strong>Calificación</strong> (2 partidos por equipo, puntuación en games: 6:4)    # [HTML]
admin.tournaments.details.teams.option.auto=Auto (Full si posible, Flex si no)
admin.tournaments.details.teams.option.flex=Flex Americano — máxima variedad
```

---

## King of Court admin (`admin.koc.*`)

Шаблон: `admin/tournaments/king-of-court.html` (~11 строк)

```properties
admin.koc.title=King of Court — Gestión
admin.koc.court=Cancha {0}
admin.koc.round=Ronda {0}
admin.koc.status.playing=En juego
admin.koc.status.completed=Completado
admin.koc.btn.next_round=Siguiente ronda
admin.koc.btn.rollback=Revertir ronda
admin.koc.btn.finish=Finalizar torneo
admin.koc.btn.youtube=Guardar YouTube
admin.koc.btn.save_result=Guardar resultado
admin.koc.empty=Sin rondas activas
```

---

## Pagos (`admin.payments.*`)

Шаблон: `admin/tournaments/payments.html` (~27 строк)

```properties
admin.payments.title=Gestión de Pagos
admin.payments.subtitle=Administra los pagos del torneo

# Колонки
admin.payments.col.player=Jugador
admin.payments.col.amount=Monto
admin.payments.col.status=Estado
admin.payments.col.date=Fecha de pago
admin.payments.col.method=Método

# Кнопки
admin.payments.btn.confirm=Confirmar pago
admin.payments.btn.reject=Rechazar

# Статистика
admin.payments.stat.total=Total recaudado
admin.payments.stat.pending=Pendientes
admin.payments.stat.confirmed=Confirmados

# Пустое состояние
admin.payments.empty=No hay pagos registrados
```

---

## Americano admin (`admin.americano.*`)

Шаблоны: `admin/americano/tournament.html`, `tournament-double.html` (~32 + 27 строк)

```properties
admin.americano.title=Americano — Gestión
admin.americano.double.title=Team Americano — Gestión

# Кнопки управления
admin.americano.btn.initialize=Inicializar torneo
admin.americano.btn.start_round=Iniciar ronda
admin.americano.btn.complete_round=Completar ronda
admin.americano.btn.save_result=Guardar resultado
admin.americano.btn.finish=Finalizar torneo
admin.americano.btn.preview=Preview de rondas
admin.americano.btn.dropout=Dar de baja

# Секции
admin.americano.section.rounds=Rondas
admin.americano.section.matches=Partidos
admin.americano.section.ranking=Clasificación

# Статусы матчей (используются как значения в JS и HTML)
admin.americano.status.pending=Pendiente
admin.americano.status.in_progress=En curso
admin.americano.status.completed=Completado
admin.americano.status.cancelled=Cancelado

# Пустые состояния
admin.americano.empty.rounds=No hay rondas disponibles
admin.americano.empty.matches=No hay partidos en esta ronda
admin.americano.empty.ranking=Sin clasificación

# Колонки
admin.americano.col.round=Ronda
admin.americano.col.court=Cancha
admin.americano.col.team1=Equipo 1
admin.americano.col.team2=Equipo 2
admin.americano.col.result=Resultado
admin.americano.col.points=Puntos
admin.americano.col.played=PJ
admin.americano.col.won=PG
admin.americano.col.lost=PP
```

---

## Americano Playoff admin (`admin.playoff.*`)

Шаблон: `admin/americano/tournament-playoff.html` (~65 строк — второй по объёму)

```properties
admin.playoff.title=Playoff — Gestión
admin.playoff.btn.generate=Generar bracket
admin.playoff.btn.start=Iniciar playoff
admin.playoff.btn.finish=Finalizar

# Сетка (bracket)
admin.playoff.bracket.title=Cuadro eliminatorio
admin.playoff.bracket.round=Ronda {0}
admin.playoff.bracket.winner=Ganador
admin.playoff.bracket.tbd=Por determinar

# Квалификация
admin.playoff.qualification.title=Clasificación de calificación
admin.playoff.qualification.col.team=Equipo
admin.playoff.qualification.col.points=Puntos
admin.playoff.qualification.col.qualify=Clasifica

# Статусы
admin.playoff.status.pending=Pendiente
admin.playoff.status.in_progress=En curso
admin.playoff.status.finished=Finalizado

# Пустое состояние
admin.playoff.empty=No hay datos de playoff disponibles
```

---

## Preview rondas (`admin.preview.*`)

Шаблоны: `admin/americano/preview-rounds.html`, `preview-double-rounds.html` (~22 + 24 строки)

```properties
admin.preview.title=Preview de rondas
admin.preview.double.title=Preview de rondas (parejas)
admin.preview.col.round=Ronda
admin.preview.col.court=Cancha
admin.preview.col.team_a=Equipo A
admin.preview.col.team_b=Equipo B
admin.preview.col.bye=Bye
admin.preview.btn.confirm=Confirmar e inicializar
admin.preview.btn.back=Volver
admin.preview.stat.rounds=Total rondas:
admin.preview.stat.courts=Canchas:
admin.preview.stat.players=Jugadores:
admin.preview.empty=No hay rondas para mostrar
```

---

## Lista de clubes (`admin.clubs.list.*`)

Шаблон: `admin/clubs/list.html` (~21 строка)

```properties
admin.clubs.list.title=Gestión de Clubes
admin.clubs.list.subtitle=Administración de clubes 1-Padel

# Колонки
admin.clubs.list.col.city=Ciudad/Zona
admin.clubs.list.col.phone=Teléfono

# Пустое состояние
admin.clubs.list.empty.title=No hay clubes
admin.clubs.list.empty.text=Comienza creando tu primer club
```

---

## Formulario de club (`admin.clubs.form.*`)

Шаблон: `admin/clubs/form.html` (~13 строк)

```properties
admin.clubs.form.title.create=Crear Club
admin.clubs.form.title.edit=Editar Club
admin.clubs.form.field.name=Nombre del Club
admin.clubs.form.field.city=Ciudad
admin.clubs.form.field.zone=Zona
admin.clubs.form.field.address=Dirección
admin.clubs.form.field.phone=Teléfono
admin.clubs.form.field.email=Email
admin.clubs.form.field.courts=Número de canchas
admin.clubs.form.field.description=Descripción
admin.clubs.form.field.website=Sitio web
admin.clubs.form.field.active=Activo
```

---

## Detalles del club (`admin.clubs.details.*`)

Шаблон: `admin/clubs/details.html` (~17 строк)

```properties
admin.clubs.details.title=Detalles del Club
admin.clubs.details.field.city=Ciudad
admin.clubs.details.field.zone=Zona
admin.clubs.details.field.address=Dirección
admin.clubs.details.field.phone=Teléfono
admin.clubs.details.field.email=Email
admin.clubs.details.field.courts=Canchas
admin.clubs.details.field.website=Sitio web
admin.clubs.details.tournaments.title=Torneos del club
admin.clubs.details.tournaments.empty=No hay torneos en este club
```

---

## Lista de jugadores (`admin.players.list.*`)

Шаблон: `admin/players/list.html` (~20 строк)

```properties
admin.players.list.title=Gestión de Jugadores
admin.players.list.subtitle=Administra todos los jugadores de la plataforma

# Колонки
admin.players.list.col.registered=Registro
admin.players.list.col.email_confirmed=Email Confirmado

# Статистика
admin.players.list.stat.total=Total Jugadores
admin.players.list.stat.email_confirmed=Email Confirmado

# Пустое состояние
admin.players.list.empty.title=No hay jugadores
admin.players.list.empty.text=Los jugadores aparecerán aquí cuando se registren
```

---

## Detalles del jugador (`admin.players.details.*`)

Шаблон: `admin/players/details.html` (~29 строк)

```properties
admin.players.details.title=Detalles del Jugador
admin.players.details.field.email=Email
admin.players.details.field.phone=Teléfono
admin.players.details.field.telegram=Telegram
admin.players.details.field.level=Nivel
admin.players.details.field.registered=Registrado
admin.players.details.field.email_confirmed=Email confirmado
admin.players.details.field.status=Estado
admin.players.details.field.google=Google OAuth
admin.players.details.tournaments.title=Torneos del jugador
admin.players.details.tournaments.col.tournament=Torneo
admin.players.details.tournaments.col.status=Estado inscripción
admin.players.details.tournaments.col.date=Fecha
admin.players.details.tournaments.empty=Sin torneos registrados
admin.players.details.ranking.title=Posición en ranking
admin.players.details.ranking.col.points=Puntos
admin.players.details.ranking.col.position=Posición
admin.players.details.ranking.col.wins=Victorias
```

---

## Итого новых ключей

| Файл | Ключей |
|------|-------:|
| `admin.common.*` | ~14 |
| `admin.nav.*` | ~7 |
| `admin.panel.*` | ~12 |
| `admin.tournaments.list.*` | ~8 |
| `admin.tournaments.form.*` | ~26 |
| `admin.tournaments.details.*` | ~30 |
| `admin.koc.*` | ~11 |
| `admin.payments.*` | ~13 |
| `admin.americano.*` | ~23 |
| `admin.playoff.*` | ~14 |
| `admin.preview.*` | ~13 |
| `admin.clubs.list.*` | ~7 |
| `admin.clubs.form.*` | ~12 |
| `admin.clubs.details.*` | ~10 |
| `admin.players.list.*` | ~10 |
| `admin.players.details.*` | ~17 |
| **Итого** | **~227** |

> Плюс часть уже покрывается переиспользованием `common.btn.*`, `common.status.*` из публичной части — фактически новых ключей меньше.

---

## Чеклист этапа 11

- [ ] Все секции `admin.*` добавлены в конец `messages_es.properties`
- [ ] `messages.properties` (fallback) обновлён — копия es
- [ ] Ключи с `# [HTML]` помечены для `th:utext` на этапе 14
- [ ] Строки с `{0}` сохранены как MessageFormat-плейсхолдеры
- [ ] Нет дублей с существующими `common.*` ключами
