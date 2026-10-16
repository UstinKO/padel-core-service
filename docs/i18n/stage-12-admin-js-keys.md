# Этап 12: Ключи для Admin JS файлов → messages-es.js

## Цель

Выявить все пользовательски видимые строки в 5 admin JS-файлах и перенести их в секцию `admin.*` в `messages-es.js`.

**Файлы:**
- `admin.js` (~12 строк)
- `admin-americano.js` (~18 строк)
- `king-of-court.js` (~29 строк)
- `payment-management.js` (~1 строка)
- `admin-menu.js` (~1 строка)

---

## Что переносим, что оставляем

**Переносим:**
- Тексты в `showToast(...)`, `showNotification(...)`, `showError(...)`
- Тексты в `confirm(...)` диалогах
- Динамически генерируемые UI-строки (innerHTML-шаблоны)
- Строки статусов, показываемые пользователю

**Оставляем:**
- CSS классы: `'btn-primary'`, `'modal-open'`
- URL и HTTP методы: `'/api/...'`, `'POST'`
- Значения `data-*` атрибутов: `'PENDING'`, `'IN_PROGRESS'`
- `console.log(...)` сообщения
- Имена событий и селекторов

---

## Новые ключи в `messages-es.js`

```javascript
// === ADMIN COMMON ===
'admin.common.success.deleted':     'Elemento eliminado correctamente',
'admin.common.success.status':      'Estado actualizado correctamente',
'admin.common.error.delete':        'Error al eliminar',
'admin.common.error.status':        'Error al actualizar estado',
'admin.common.error.connection':    'Error de conexión',

// === ADMIN AMERICANO (admin-americano.js) ===
'admin.americano.status.pending':       'Pendiente',
'admin.americano.status.in_progress':   'En curso',
'admin.americano.status.completed':     'Completado',
'admin.americano.status.cancelled':     'Cancelado',
'admin.americano.round.started':        'Ronda iniciada',
'admin.americano.round.completed':      'Ronda completada',
'admin.americano.result.saved':         'Resultado guardado',
'admin.americano.error.start_round':    'Error al iniciar ronda',
'admin.americano.error.complete_round': 'Error al completar ronda',
'admin.americano.error.save_result':    'Error al guardar resultado',

// === KING OF COURT (king-of-court.js) ===
'admin.koc.confirm.initialize':     'Inicializar torneo King of Court?',
'admin.koc.initialized':            'Torneo inicializado',
'admin.koc.round.next':             'Pasando a la siguiente ronda',
'admin.koc.result.saved':           'Resultado guardado',
'admin.koc.result.updated':         'Resultado actualizado',
'admin.koc.finished':               'Torneo finalizado',
'admin.koc.youtube.saved':          'Enlace de YouTube guardado',
'admin.koc.status.playing':         'EN JUEGO',
'admin.koc.status.completed':       'COMPLETADO',
'admin.koc.player.unknown':         'Jugador desconocido',
'admin.koc.victory':                'Victoria',
'admin.koc.defeat':                 'Derrota',
'admin.koc.error.initialize':       'Error de inicialización',
'admin.koc.error.fields':           'No todos los campos están completos',
'admin.koc.error.same_winners':     'Selecciona dos ganadores diferentes',
'admin.koc.error.score_invalid':    'El puntaje de los ganadores debe ser mayor que el de los perdedores',
'admin.koc.error.court_data':       'No se pudieron cargar los datos de la cancha',
'admin.koc.error.losers':           'Error al determinar los perdedores',
'admin.koc.error.save':             'Error al guardar',
'admin.koc.error.next_round':       'Error al pasar de ronda',
'admin.koc.error.finish':           'Error al finalizar el torneo',
'admin.koc.error.rollback':         'Error al revertir la ronda',
'admin.koc.error.connection':       'Error de conexión',
```

---

## Файл за файлом — замены

### `admin.js`

| Текущая строка | Ключ |
|---------------|------|
| `'Elemento eliminado correctamente'` | `admin.common.success.deleted` |
| `'Error al eliminar'` | `admin.common.error.delete` |
| `'Error de conexión'` | `admin.common.error.connection` |
| `'Estado actualizado correctamente'` | `admin.common.success.status` |
| `'Error al actualizar estado'` | `admin.common.error.status` |

### `admin-americano.js`

| Текущая строка | Ключ |
|---------------|------|
| `'Pendiente'` | `admin.americano.status.pending` |
| `'En curso'` | `admin.americano.status.in_progress` |
| `'Completado'` | `admin.americano.status.completed` |
| `'Cancelado'` | `admin.americano.status.cancelled` |
| `'Ronda iniciada'` | `admin.americano.round.started` |
| `'Ronda completada'` | `admin.americano.round.completed` |
| `'Resultado guardado'` | `admin.americano.result.saved` |
| `'Error al iniciar ronda'` | `admin.americano.error.start_round` |
| `'Error al completar ronda'` | `admin.americano.error.complete_round` |
| `'Error al guardar resultado'` | `admin.americano.error.save_result` |

### `king-of-court.js`

| Текущая строка | Ключ |
|---------------|------|
| `'Inicializar torneo King of Court?'` | `admin.koc.confirm.initialize` |
| `'Torneo inicializado'` | `admin.koc.initialized` |
| `'Pasando a la siguiente ronda'` | `admin.koc.round.next` |
| `'Resultado guardado'` | `admin.koc.result.saved` |
| `'Resultado actualizado'` | `admin.koc.result.updated` |
| `'Torneo finalizado'` (2 вхождения) | `admin.koc.finished` |
| `'Enlace de YouTube guardado'` | `admin.koc.youtube.saved` |
| `'EN JUEGO'` (шаблон карточки) | `admin.koc.status.playing` |
| `'COMPLETADO'` (шаблон карточки) | `admin.koc.status.completed` |
| `'Jugador desconocido'` | `admin.koc.player.unknown` |
| `'Victoria'` | `admin.koc.victory` |
| `'Derrota'` | `admin.koc.defeat` |
| `'Error de inicialización'` | `admin.koc.error.initialize` |
| `'No todos los campos están completos'` | `admin.koc.error.fields` |
| `'Selecciona dos ganadores diferentes'` | `admin.koc.error.same_winners` |
| `'El puntaje de los ganadores...'` | `admin.koc.error.score_invalid` |
| `'No se pudieron cargar los datos...'` | `admin.koc.error.court_data` |
| `'Error al determinar los perdedores'` | `admin.koc.error.losers` |
| `'Error al guardar'` | `admin.koc.error.save` |
| `'Error al pasar de ronda'` | `admin.koc.error.next_round` |
| `'Error al finalizar el torneo'` | `admin.koc.error.finish` |
| `'Error al revertir la ronda'` | `admin.koc.error.rollback` |

### `payment-management.js`, `admin-menu.js`

По 1 строке — уточнить при интеграции (этап 15).

---

## Чеклист этапа 12

- [ ] Новая секция `// === ADMIN COMMON ===` добавлена в `messages-es.js`
- [ ] Новая секция `// === ADMIN AMERICANO ===` добавлена
- [ ] Новая секция `// === KING OF COURT ===` добавлена
- [ ] Строки для `payment-management.js`, `admin-menu.js` добавлены (после осмотра файлов)
- [ ] Нет дублей с существующими ключами в `messages-es.js`
- [ ] Ключи с переменными (`{0}`) сохраняют плейсхолдер
