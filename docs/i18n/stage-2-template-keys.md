# Этап 2: Ключи для HTML шаблонов → messages_es.properties

## Цель

Пройтись по каждому user-facing шаблону, выписать все текстовые строки как ключи в `messages_es.properties`.  
**Шаблоны ещё не меняем** — только составляем inventory ключей.

---

## Соглашения

**Ключ**: `страница.секция.элемент`  
**Строки с переменными**: используем `{0}`, `{1}` — формат MessageFormat  
**Пример**: `dashboard.registration.confirm=¿Confirmar inscripción en {0}?`

В Thymeleaf: `#{dashboard.registration.confirm(tournamentName)}`  
В JS (через `t()`): `t('dashboard.registration.confirm').replace('{0}', name)`

---

## Общие ключи (`common.*`)

Используются на нескольких страницах — выносим в `common`:

```properties
# Кнопки
common.btn.save=Guardar
common.btn.cancel=Cancelar
common.btn.back=Volver
common.btn.close=Cerrar
common.btn.confirm=Confirmar
common.btn.delete=Eliminar
common.btn.edit=Editar
common.btn.register=Registrarse
common.btn.login=Iniciar sesión
common.btn.retry=Intentar de nuevo
common.btn.home=Inicio

# Лейблы
common.label.optional=(opcional)
common.label.required=Campo obligatorio
common.label.loading=Cargando...
common.label.nodata=Sin datos disponibles
common.label.error=Error
common.label.success=Éxito

# Уровни игры (используются на нескольких страницах)
common.level.all=Todos los niveles
common.level.select=Selecciona tu nivel

# Статусы турнира
common.status.upcoming=Próximo
common.status.active=Activo
common.status.finished=Finalizado
common.status.cancelled=Cancelado

# Статусы регистрации
common.registration.confirmed=Confirmado
common.registration.waitlist=Lista de espera
common.registration.cancelled=Cancelado
common.registration.pair_registered=Pareja registrada

# Форматы турниров
common.format.americano=Americano
common.format.king_of_court=King of Court
common.format.playoff=Playoff
common.format.team_americano=Team Americano
```

---

## Навигация (`nav.*`)

```properties
nav.inicio=Inicio
nav.torneos=Torneos
nav.ranking=Ranking
nav.clubes=Clubes
nav.mi_cuenta=Mi cuenta
nav.dashboard=Dashboard
nav.perfil=Perfil
nav.cerrar_sesion=Cerrar sesión
nav.iniciar_sesion=Iniciar sesión
nav.registrarse=Registrarse
nav.admin=Panel admin
```

---

## Login (`login.*`)

Шаблон: `login.html` (~10 строк)

```properties
login.title=Iniciar sesión
login.subtitle=¿No tienes cuenta?
login.subtitle.link=Regístrate gratis
login.email.label=Email
login.email.placeholder=tu@email.com
login.password.label=Contraseña
login.password.placeholder=Tu contraseña
login.remember=Recordarme
login.forgot=¿Olvidaste tu contraseña?
login.submit=Iniciar sesión
login.or=o continúa con
login.google=Google
login.safe=Tus datos están seguros con nosotros
login.logout_success=Has cerrado sesión correctamente
login.registered_success=¡Registro exitoso! Ya puedes iniciar sesión.
login.confirmed_success=¡Email confirmado! Ya puedes iniciar sesión.

# Ошибки
login.error.rate_limit.title=Demasiados intentos
login.error.rate_limit.text=Has superado el límite de intentos. Por favor, espera <strong>1 minuto</strong> antes de intentarlo de nuevo.
login.error.not_confirmed.title=Email no confirmado
login.error.not_confirmed.text=Abre tu bandeja de entrada y haz clic en el enlace que te enviamos al registrarte.
login.error.not_confirmed.spam=Si no lo ves, revisa la carpeta <strong>Spam</strong> o <strong>Correo no deseado</strong>.
login.error.bad_credentials=Email o contraseña incorrectos
```

---

## Registro (`registro.*`)

Шаблон: `registro.html` (~22 строки)

```properties
registro.title=Crear cuenta
registro.subtitle=¿Ya tienes cuenta?
registro.subtitle.link=Inicia sesión
registro.google=Google
registro.google.safe=Tus datos están seguros con Google
registro.divider.email=o crea una cuenta con email
registro.nombre.label=Nombre
registro.nombre.placeholder=Tu nombre
registro.apellido.label=Apellido
registro.apellido.placeholder=Tu apellido
registro.email.label=Email
registro.email.placeholder=tu@email.com
registro.telefono.label=Teléfono
registro.telefono.placeholder=+54 9 11 0000 0000
registro.nivel.label=Nivel de juego
registro.nivel.placeholder=Selecciona tu nivel
registro.password.label=Contraseña
registro.password.placeholder=Mínimo 8 caracteres
registro.password.hint=Mínimo 8 caracteres, 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial
registro.confirm_password.label=Confirmar contraseña
registro.confirm_password.placeholder=Repite la contraseña
registro.terms=Acepto los <a href="/terminos" target="_blank">Términos y Condiciones</a> y la <a href="/privacidad" target="_blank">Política de Privacidad</a>
registro.submit=Registrarse
registro.error.rate_limit.title=Demasiadas solicitudes
registro.error.rate_limit.text=Has superado el límite de intentos. Por favor, espera <strong>1 minuto</strong> antes de intentarlo de nuevo.
```

---

## Index / Home (`home.*`)

Шаблон: `index.html` (~69 строк)

```properties
home.hero.title=La plataforma de pádel
home.hero.title.highlight=más completa
home.hero.subtitle=Gestiona torneos, inscripciones y resultados en tiempo real
home.hero.cta.register=Crear cuenta gratis
home.hero.cta.tournaments=Ver torneos
home.hero.cta.login=Inicia sesión
home.hero.stat.tournaments=Torneos activos
home.hero.stat.players=Jugadores registrados
home.hero.stat.clubs=Clubes asociados

home.upcoming.title=Próximos Torneos
home.upcoming.subtitle=Inscríbete y compite en los mejores torneos de pádel
home.upcoming.filter.all=Todos
home.upcoming.empty=No hay torneos próximos disponibles
home.upcoming.view_all=Ver todos los torneos

home.features.title=¿Por qué elegir 1-Padel?
home.features.tournaments.title=Gestión de torneos
home.features.tournaments.desc=Organiza y participa en torneos de diferentes formatos
home.features.realtime.title=Resultados en tiempo real
home.features.realtime.desc=Sigue los partidos y clasificaciones al instante
home.features.ranking.title=Sistema de ranking
home.features.ranking.desc=Consulta tu posición y evolución en el ranking

home.cta.title=¿Listo para jugar?
home.cta.subtitle=Únete a miles de jugadores en la plataforma líder de pádel
home.cta.btn=Crear cuenta gratis
```

---

## Torneos / Lista de torneos (`torneos.*`)

Шаблон: `torneos.html` (~29 строк)

```properties
torneos.title=Torneos
torneos.subtitle=Encuentra y participa en los mejores torneos de pádel
torneos.search.placeholder=Buscar torneo...
torneos.filter.format=Formato
torneos.filter.level=Nivel
torneos.filter.status=Estado
torneos.filter.club=Club
torneos.filter.all_formats=Todos los formatos
torneos.filter.all_levels=Todos los niveles
torneos.filter.all_statuses=Todos los estados
torneos.filter.all_clubs=Todos los clubes
torneos.empty=No se encontraron torneos con los filtros seleccionados
torneos.card.spots=plazas disponibles
torneos.card.full=Completo
torneos.card.waitlist=Lista de espera
torneos.card.view=Ver torneo
torneos.card.register=Inscribirse
torneos.card.date=Fecha
torneos.card.club=Club
torneos.card.level=Nivel
torneos.card.format=Formato
torneos.card.price=Precio
torneos.card.free=Gratis
```

---

## Detalles del torneo (`tournament.*`)

Шаблон: `tournament-details.html` (~69 строк) — самый объёмный

```properties
tournament.btn.register=Inscribirse
tournament.btn.cancel=Cancelar inscripción
tournament.btn.registered=Inscrito ✓
tournament.btn.waitlist=En lista de espera
tournament.btn.login_to_register=Inicia sesión para inscribirte

tournament.info.date=Fecha
tournament.info.club=Club
tournament.info.format=Formato
tournament.info.level=Nivel
tournament.info.price=Precio
tournament.info.free=Gratis
tournament.info.spots=Plazas
tournament.info.available=disponibles
tournament.info.full=Completo
tournament.info.description=Descripción

tournament.participants.title=Participantes
tournament.participants.empty=Aún no hay participantes inscritos
tournament.participants.confirmed=Confirmados
tournament.participants.waitlist=Lista de espera

tournament.bracket.title=Cuadro de partidos
tournament.bracket.round=Ronda
tournament.bracket.tbd=Por determinar

tournament.status.upcoming=Próximo
tournament.status.active=En curso
tournament.status.finished=Finalizado

tournament.register.success=Te has inscrito correctamente
tournament.register.cancel_confirm=¿Estás seguro que quieres cancelar tu inscripción?
tournament.register.cancelled=Inscripción cancelada
tournament.register.full=El torneo está completo. Te añadiremos a la lista de espera
tournament.register.no_contact=Necesitas añadir un teléfono o Telegram para inscribirte
```

---

## Dashboard del jugador (`dashboard.*`)

Шаблон: `players/dashboard.html` (~57 строк) — второй по объёму

```properties
dashboard.title=Mi Dashboard
dashboard.welcome=Bienvenido, {0}
dashboard.subtitle=Gestiona tus inscripciones y sigue tu actividad

dashboard.stats.active=Torneos activos
dashboard.stats.played=Torneos jugados
dashboard.stats.ranking=Posición ranking
dashboard.stats.wins=Victorias

dashboard.my_tournaments.title=Mis torneos
dashboard.my_tournaments.empty=No tienes torneos próximos
dashboard.my_tournaments.browse=Ver torneos disponibles

dashboard.upcoming.title=Torneos disponibles
dashboard.upcoming.empty=No hay torneos próximos disponibles

dashboard.registration.confirm=¿Confirmar inscripción en {0}?
dashboard.registration.cancel_confirm=¿Cancelar inscripción en {0}?
dashboard.registration.cancelled=Inscripción cancelada correctamente
dashboard.registration.error=Error al procesar la inscripción

dashboard.contact.missing.title=Completa tu perfil
dashboard.contact.missing.text=Para inscribirte en torneos necesitas un teléfono o Telegram
dashboard.contact.missing.btn=Completar perfil

dashboard.pair.title=Mis parejas registradas
dashboard.pair.partner=Compañero
dashboard.pair.cancel_confirm=¿Cancelar inscripción de pareja en {0}?
```

---

## Perfil del jugador (`perfil.*`)

Шаблон: `players/perfil.html` (~24 строки)

```properties
perfil.title=Mi Perfil
perfil.subtitle=Gestiona tu información personal
perfil.nombre.label=Nombre
perfil.apellido.label=Apellido
perfil.email.label=Email
perfil.telefono.label=Teléfono
perfil.telefono.placeholder=+54 9 11 0000 0000
perfil.telegram.label=Telegram
perfil.telegram.placeholder=@usuario
perfil.nivel.label=Nivel de juego
perfil.submit=Guardar cambios
perfil.success=Perfil actualizado correctamente
perfil.error=Error al actualizar el perfil
perfil.contact.title=Contacto para torneos
perfil.contact.hint=Necesitas al menos un método de contacto para inscribirte en torneos
```

---

## Ranking (`ranking.*`)

Шаблон: `ranking.html` (~25 строк)

```properties
ranking.title=Ranking de jugadores
ranking.subtitle=Clasificación general de la temporada
ranking.col.position=Pos.
ranking.col.player=Jugador
ranking.col.points=Puntos
ranking.col.tournaments=Torneos
ranking.col.wins=Victorias
ranking.col.level=Nivel
ranking.empty=No hay jugadores en el ranking todavía
ranking.loading=Cargando ranking...
ranking.filter.level=Filtrar por nivel
ranking.filter.all=Todos los niveles
```

---

## King of Court viewer (`koc.*`)

Шаблон: `king-of-court-view.html` (~16 строк)

```properties
koc.title=King of Court
koc.round=Ronda {0}
koc.court=Cancha {0}
koc.score=Marcador
koc.ranking=Clasificación
koc.player=Jugador
koc.points=Puntos
koc.won=Ganados
koc.lost=Perdidos
koc.status.waiting=Esperando inicio...
koc.status.playing=En juego
koc.status.finished=Finalizado
koc.vs=vs
koc.live=En vivo
```

---

## Americano viewer (`americano.*`)

Шаблоны: `tournaments/americano/view.html`, `team-americano/view.html`, `team-playoff/view.html` (~86 строк суммарно)

```properties
americano.title=Americano
americano.round=Ronda {0}
americano.match=Partido {0}
americano.score=Resultado
americano.ranking.title=Clasificación
americano.ranking.col.player=Jugador
americano.ranking.col.points=Puntos
americano.ranking.col.played=PJ
americano.ranking.col.won=PG
americano.ranking.col.lost=PP
americano.ranking.empty=Sin clasificación disponible
americano.status.not_started=Sin iniciar
americano.status.in_progress=En curso
americano.status.finished=Finalizado
americano.no_rounds=No hay rondas disponibles
americano.limit=Límite de puntos: {0}

# Team americano
team_americano.title=Team Americano
team_americano.col.team=Equipo

# Playoff
playoff.title=Playoff
playoff.bracket=Cuadro de eliminación
playoff.winner=Ganador
playoff.tbd=Por determinar
```

---

## Clubes (`clubs.*`)

Шаблоны: `clubs/list.html`, `clubs/view.html` (~16 строк)

```properties
clubs.list.title=Clubes
clubs.list.subtitle=Encuentra los mejores clubes de pádel
clubs.list.empty=No hay clubes disponibles
clubs.list.view=Ver club
clubs.view.tournaments=Torneos del club
clubs.view.tournaments.empty=No hay torneos en este club
clubs.view.address=Dirección
clubs.view.zone=Zona
clubs.view.courts=Canchas
```

---

## Waitlist y aceptación de pareja (`waitlist.*`, `pair.*`)

```properties
waitlist.title=Lista de espera
waitlist.confirmed.title=¡Lugar confirmado!
waitlist.confirmed.text=Te has unido al torneo {0}
waitlist.expired.title=Invitación expirada
waitlist.expired.text=El tiempo para confirmar tu lugar ha expirado

pair.accept.title=Invitación de pareja
pair.accept.tournament=Torneo: {0}
pair.accept.partner=Tu compañero/a: {0}
pair.accept.btn=Aceptar inscripción
pair.accept.success=¡Inscripción de pareja confirmada!
```

---

## Página de error rate-limit (`error.*`)

```properties
error.rate_limit.title.blocked=Acceso bloqueado temporalmente
error.rate_limit.title.limit=Demasiados intentos
error.rate_limit.desc.blocked=Tu dirección IP ha sido bloqueada por actividad sospechosa. Por favor, espera 15 minutos antes de intentarlo de nuevo.
error.rate_limit.desc.limit=Has superado el límite de solicitudes permitidas. Espera un momento y vuelve a intentarlo.
error.rate_limit.countdown=Podrás intentarlo de nuevo en
error.rate_limit.ready=¡Ya puedes intentarlo de nuevo!
error.rate_limit.footer=Esta protección evita el abuso automatizado del sistema
```

---

## Cookie consent (`cookie.*`)

```properties
cookie.banner.text=Utilizamos cookies para mejorar tu experiencia
cookie.btn.accept=Aceptar todo
cookie.btn.reject=Rechazar
cookie.btn.customize=Personalizar
```

---

## Чеклист этапа 2

- [ ] Создан `messages_es.properties` со всеми ключами (~200-250 ключей)
- [ ] Создан `messages.properties` — копия es (fallback)
- [ ] Созданы пустые `messages_ru.properties` и `messages_en.properties` (ключи без значений или с заглушками)
- [ ] Ключи сгруппированы по секциям с комментариями
- [ ] Строки с переменными используют `{0}`, `{1}` формат
- [ ] HTML в значениях (`<strong>`, `<a>`) помечены — потребуют `th:utext` вместо `th:text`
