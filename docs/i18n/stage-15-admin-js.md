# Этап 15: Интеграция — замена строк в Admin JS файлах

## Цель

Заменить хардкоженные испанские строки в 5 admin JS-файлах на вызовы `t(key)`.  
Все ключи уже добавлены в `messages-*.js` на этапе 12.

---

## Предварительная проверка: i18n уже подключён

`admin.js`, `admin-americano.js`, `admin-menu.js` уже используют `t()` — проверить что скрипты загружаются в правильном порядке в admin шаблонах:

```html
<!-- Порядок важен: t.js → messages-xx.js → admin.js -->
<script th:src="@{/js/i18n/t.js}"></script>
<script th:with="lang=..." th:src="@{'/js/i18n/messages-' + ${lang} + '.js'}"></script>
<script th:src="@{/js/admin.js}"></script>
```

`king-of-court.js` и `payment-management.js` — проверить, подключены ли `t.js` и `messages-xx.js` **до** них в соответствующих шаблонах (`king-of-court.html`, `payments.html`).

---

## Файл за файлом

### `admin.js`

```javascript
// БЫЛО
this.showNotification('Elemento eliminado correctamente', 'success');
this.showNotification(data.message || 'Error al eliminar', 'error');
this.showNotification('Error de conexión', 'error');
this.showNotification('Estado actualizado correctamente', 'success');
this.showNotification(data.message || 'Error al actualizar estado', 'error');

// СТАЛО
this.showNotification(t('admin.common.success.deleted'), 'success');
this.showNotification(data.message || t('admin.common.error.delete'), 'error');
this.showNotification(t('admin.common.error.connection'), 'error');
this.showNotification(t('admin.common.success.status'), 'success');
this.showNotification(data.message || t('admin.common.error.status'), 'error');
```

---

### `admin-americano.js`

```javascript
// Статусы матчей — используются как отображаемый текст
// БЫЛО
const map = { PENDING: 'Pendiente', IN_PROGRESS: 'En curso', COMPLETED: 'Completado', CANCELLED: 'Cancelado' };

// СТАЛО
const map = {
    PENDING:     t('admin.americano.status.pending'),
    IN_PROGRESS: t('admin.americano.status.in_progress'),
    COMPLETED:   t('admin.americano.status.completed'),
    CANCELLED:   t('admin.americano.status.cancelled'),
};

// Toast уведомления
// БЫЛО
showToast('success', 'Ronda iniciada');
showToast('error', data.message || 'Error al iniciar ronda');
showToast('success', 'Ronda completada');
showToast('error', data.message || 'Error al completar ronda');
showToast('success', 'Resultado guardado');
showToast('error', data.message || 'Error al guardar resultado');
showToast('error', 'Error de conexión');

// СТАЛО
showToast('success', t('admin.americano.round.started'));
showToast('error', data.message || t('admin.americano.error.start_round'));
showToast('success', t('admin.americano.round.completed'));
showToast('error', data.message || t('admin.americano.error.complete_round'));
showToast('success', t('admin.americano.result.saved'));
showToast('error', data.message || t('admin.americano.error.save_result'));
showToast('error', t('admin.common.error.connection'));
```

---

### `king-of-court.js`

```javascript
// Confirm диалог
// БЫЛО
if (!confirm('Inicializar torneo King of Court?')) return;
// СТАЛО
if (!confirm(t('admin.koc.confirm.initialize'))) return;

// Статусы в шаблоне карточки корта (innerHTML)
// БЫЛО
${court.hasResult ? 'COMPLETADO' : 'EN JUEGO'}
// СТАЛО
${court.hasResult ? t('admin.koc.status.completed') : t('admin.koc.status.playing')}

// Имя игрока
// БЫЛО
${player.playerName || 'Jugador desconocido'}
// СТАЛО
${player.playerName || t('admin.koc.player.unknown')}

// Victory/Defeat в шаблоне истории
// БЫЛО
<strong>${isWinner ? 'Victoria' : 'Derrota'}</strong>
// СТАЛО
<strong>${isWinner ? t('admin.koc.victory') : t('admin.koc.defeat')}</strong>

// Уведомления об успехе
// БЫЛО
this.showNotification('Torneo inicializado', 'success');
this.showNotification('Pasando a la siguiente ronda', 'success');
this.showNotification(resultId ? 'Resultado actualizado' : 'Resultado guardado', 'success');
this.showNotification('Torneo finalizado', 'success');
this.showNotification('Enlace de YouTube guardado', 'success');

// СТАЛО
this.showNotification(t('admin.koc.initialized'), 'success');
this.showNotification(t('admin.koc.round.next'), 'success');
this.showNotification(resultId ? t('admin.koc.result.updated') : t('admin.koc.result.saved'), 'success');
this.showNotification(t('admin.koc.finished'), 'success');
this.showNotification(t('admin.koc.youtube.saved'), 'success');

// Ошибки валидации
// БЫЛО
this.showError('No todos los campos están completos');
this.showError('Selecciona dos ganadores diferentes');
this.showError('El puntaje de los ganadores debe ser mayor que el de los perdedores');
this.showError('No se pudieron cargar los datos de la cancha');
this.showError('Error al determinar los perdedores');
// СТАЛО
this.showError(t('admin.koc.error.fields'));
this.showError(t('admin.koc.error.same_winners'));
this.showError(t('admin.koc.error.score_invalid'));
this.showError(t('admin.koc.error.court_data'));
this.showError(t('admin.koc.error.losers'));

// Ошибки API
// БЫЛО
this.showError(error.message || 'Error de inicialización');
this.showError(error.message || 'Error al guardar');
this.showError(error.message || 'Error al pasar de ronda');
this.showError(error.message || 'Error al finalizar el torneo');
this.showError(error.message || 'Error al revertir la ronda');
this.showError('Error de conexión');
// СТАЛО
this.showError(error.message || t('admin.koc.error.initialize'));
this.showError(error.message || t('admin.koc.error.save'));
this.showError(error.message || t('admin.koc.error.next_round'));
this.showError(error.message || t('admin.koc.error.finish'));
this.showError(error.message || t('admin.koc.error.rollback'));
this.showError(t('admin.koc.error.connection'));
```

---

### `payment-management.js`, `admin-menu.js`

1-2 строки — найти при чтении файлов, заменить по тому же паттерну `t('admin.*')`.

---

## Особенность: `t()` внутри шаблонных литералов (template literals)

Когда строки используются в HTML-шаблонах внутри JS:

```javascript
// Паттерн: t() вызывается при построении строки — работает корректно
const html = `
    <div class="court-card">
        <span class="status">${court.hasResult ? t('admin.koc.status.completed') : t('admin.koc.status.playing')}</span>
        <span class="player">${player.playerName || t('admin.koc.player.unknown')}</span>
    </div>
`;
```

> `t()` вызывается в момент построения шаблона — язык уже загружен к этому времени (скрипты загружены синхронно до страничного JS).

---

## Чеклист этапа 15

- [ ] `admin.js` — все 5 строк заменены на `t()`
- [ ] `admin-americano.js` — все строки (статусы + тосты) заменены
- [ ] `king-of-court.js` — confirm, статусы корта, имя игрока, victory/defeat, все уведомления
- [ ] `payment-management.js` — проверен и обновлён
- [ ] `admin-menu.js` — проверен и обновлён
- [ ] Порядок загрузки скриптов проверен в каждом admin-шаблоне (`t.js` → `messages-xx.js` → `admin-*.js`)
- [ ] Ни один `t()` вызов не вызывается до загрузки `window.APP_MESSAGES`
