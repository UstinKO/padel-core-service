# Этап 6: Интеграция — замена строк в JS файлах

## Цель

Заменить хардкоженные строки во всех user-facing JS файлах на вызов `t('key')`.  
К этому моменту `messages-es.js`, `messages-ru.js`, `messages-en.js` уже заполнены (этап 3-4).

---

## Функция `t()` (финальная версия в `t.js`)

```javascript
/**
 * Перевод строки по ключу.
 * Аргументы подставляются вместо {0}, {1}, ...
 *
 * Примеры:
 *   t('common.loading')                          → 'Cargando...'
 *   t('dashboard.register.success', 'Copa Vera') → 'Te has inscrito en Copa Vera'
 */
function t(key) {
    const messages = window.APP_MESSAGES || {};
    let msg = messages[key];
    if (msg === undefined) {
        console.warn('[i18n] Missing translation key:', key);
        return key;  // fallback: возвращаем сам ключ — видно при тестировании
    }
    for (let i = 1; i < arguments.length; i++) {
        msg = msg.replace('{' + (i - 1) + '}', arguments[i]);
    }
    return msg;
}
```

---

## Паттерны замены в JS

### Простая строка
```javascript
// До
showNotification('Cargando...', 'info');

// После
showNotification(t('common.loading'), 'info');
```

### Строка с переменной
```javascript
// До
showNotification('Te has inscrito correctamente en ' + tournamentName, 'success');

// После
showNotification(t('dashboard.register.success', tournamentName), 'success');
```

### Строка в confirm()
```javascript
// До
if (!confirm('¿Cancelar tu inscripción en "' + name + '"?')) return;

// После
if (!confirm(t('dashboard.cancel.confirm', name))) return;
```

### Строка в fetch error handler
```javascript
// До
showNotification('Error al procesar la inscripción', 'error');

// После
showNotification(t('dashboard.register.error.generic'), 'error');
```

### Кнопка submit: смена текста
```javascript
// До
submitBtn.textContent = 'Registrando...';

// После
submitBtn.textContent = t('auth.register.loading');
```

### Динамический innerHTML (если нет XSS-рисков)
```javascript
// До
element.textContent = 'No hay torneos disponibles';

// После
element.textContent = t('torneos.empty');
```

---

## Файл за файлом

### `auth.js`
**Строки для замены:**
```javascript
// Валидация
'Debes aceptar los términos y condiciones'  → t('auth.terms.required')
'Las contraseñas no coinciden'              → t('auth.password.mismatch')
'La contraseña debe contener...'            → t('auth.password.weak')
'Por favor completa la verificación...'     → t('auth.captcha.required')
// Кнопка
'Registrando...'                            → t('auth.register.loading')
// Оригинальное значение кнопки хранить отдельно и восстанавливать через t()
```

### `password-reset.js`
**Строки для замены:**
```javascript
'Si el email existe, recibirás instrucciones...'  → t('password_reset.email.sent')
'Las contraseñas no coinciden'                    → t('password_reset.password.mismatch')
'La contraseña no cumple los requisitos'          → t('password_reset.password.weak')
'¡Contraseña actualizada correctamente!...'       → t('password_reset.success')
'El enlace ha expirado...'                        → t('password_reset.error.expired')
'Error al actualizar la contraseña'               → t('password_reset.error.generic')
'Actualizando...'                                 → t('password_reset.loading')
```

### `dashboard.js`
**Самый объёмный файл:**
```javascript
'¿Confirmar inscripción en "' + name + '"?'       → t('dashboard.register.confirm', name)
'Te has inscrito correctamente en ' + name        → t('dashboard.register.success', name)
'Necesitas añadir un teléfono o Telegram...'      → t('dashboard.register.error.contact')
'El torneo está completo'                         → t('dashboard.register.error.full')
'Error al procesar la inscripción'                → t('dashboard.register.error.generic')
'¿Cancelar tu inscripción en "' + name + '"?'     → t('dashboard.cancel.confirm', name)
'Inscripción cancelada correctamente'             → t('dashboard.cancel.success')
'Error al cancelar la inscripción'                → t('dashboard.cancel.error')
'¿Cancelar inscripción de pareja en "' + name    → t('dashboard.pair.cancel.confirm', name)
'Inscripción de pareja cancelada'                 → t('dashboard.pair.cancel.success')
'Cargando tus torneos...'                         → t('dashboard.loading')
```

### `tournament-details.js`
```javascript
'Te has inscrito correctamente'                   → t('tournament.register.success')
'Añadido a la lista de espera'                    → t('tournament.register.waitlist')
'Inicia sesión para inscribirte'                  → t('tournament.register.error.login')
'Necesitas teléfono o Telegram...'                → t('tournament.register.error.contact')
'El torneo está completo'                         → t('tournament.register.error.full')
'Error al procesar la inscripción'                → t('tournament.register.error.generic')
'¿Cancelar tu inscripción?'                       → t('tournament.cancel.confirm')
'Inscripción cancelada'                           → t('tournament.cancel.success')
'Error al cancelar la inscripción'                → t('tournament.cancel.error')
'Enlace copiado al portapapeles'                  → t('tournament.share.copied')
'Procesando...'                                   → t('tournament.loading')
```

### `home.js`
```javascript
'Cargando torneos...'                             → t('home.loading')
'No hay torneos próximos disponibles'             → t('home.empty')
'Error al cargar los torneos'                     → t('home.error.load')
```

### `torneos.js`
```javascript
'Cargando torneos...'                             → t('torneos.loading')
'No se encontraron torneos...'                    → t('torneos.empty')
'Error al cargar los torneos'                     → t('torneos.error.load')
```

### `profile.js`
```javascript
'Perfil actualizado correctamente'                → t('profile.save.success')
'Error al guardar el perfil'                      → t('profile.save.error')
'Introduce un teléfono válido...'                 → t('profile.phone.invalid')
'El usuario de Telegram debe empezar con @'       → t('profile.telegram.invalid')
'Guardando...'                                    → t('profile.loading')
```

### `contact-check.js`
```javascript
// Текст в модальном окне / попапе
'Perfil incompleto'                               → t('contact.missing.title')
'Necesitas añadir un teléfono o Telegram...'      → t('contact.missing.text')
'Completar perfil'                                → t('contact.missing.btn')
'Más tarde'                                       → t('contact.missing.later')
```

### `king-of-court-viewer.js`
```javascript
'Conectando...'                                   → t('koc.connecting')
'Conexión perdida. Reconectando...'               → t('koc.disconnected')
'Conexión restablecida'                           → t('koc.reconnected')
'Esperando inicio del torneo...'                  → t('koc.waiting')
'Ronda ' + n + ' iniciada'                        → t('koc.round_started', n)
'Torneo finalizado'                               → t('koc.finished')
```

### `ranking.js`
```javascript
'Cargando ranking...'                             → t('ranking.loading')
'No hay datos de ranking disponibles'             → t('ranking.empty')
'Error al cargar el ranking'                      → t('ranking.error.load')
```

### `cookie-consent.js`
```javascript
'Preferencias de cookies guardadas'               → t('cookie.saved')
'Error al guardar las preferencias'               → t('cookie.error')
```

### `share-panel.js`
```javascript
'Enlace copiado al portapapeles'                  → t('share.copied')
'No se pudo copiar el enlace'                     → t('share.error')
```

### `main.js`, `notifications.js`
Проверить на наличие пользовательских строк. Если есть — заменить по аналогии.

---

## Что НЕ трогаем в JS

```javascript
// Внутренние строки — не трогаем:
fetch('/api/tournaments/' + id, { method: 'POST' })
document.querySelector('.btn-primary')
localStorage.setItem('theme', 'dark')
console.log('Debug: user clicked')
element.classList.add('active')
socket.subscribe('/topic/king-of-court/' + id)
```

---

## Порядок изменений в каждом JS файле

1. `t.js` уже загружен и `messages-xx.js` уже загружен ДО этого файла (за счёт порядка в `<head>` шаблона)
2. Найти строку через поиск по файлу
3. Убедиться что это пользовательски видимый текст (не CSS класс, не URL)
4. Заменить на `t('key')`
5. Если строка содержит переменную — использовать `t('key', var)`
6. Проверить в браузере что текст отображается корректно

---

## Чеклист этапа 6

- [ ] `t.js` поддерживает аргументы и логирует missing keys
- [ ] `auth.js` — все пользовательские строки заменены
- [ ] `password-reset.js` — все строки заменены
- [ ] `dashboard.js` — все строки заменены (самый важный)
- [ ] `tournament-details.js` — все строки заменены
- [ ] `home.js` — все строки заменены
- [ ] `torneos.js` — все строки заменены
- [ ] `profile.js` — все строки заменены
- [ ] `contact-check.js` — все строки заменены
- [ ] `king-of-court-viewer.js` — все строки заменены
- [ ] `ranking.js` — все строки заменены
- [ ] `cookie-consent.js` — все строки заменены
- [ ] `share-panel.js` — все строки заменены
- [ ] `main.js`, `notifications.js` — проверены, строки заменены если есть
- [ ] В консоли браузера нет `[i18n] Missing translation key:` при работе с функционалом
- [ ] При языке `ru` все уведомления и confirm-диалоги на русском
