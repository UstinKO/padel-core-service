# Этап 3: Ключи для JS файлов → messages-es.js

## Цель

Выявить все **пользовательски видимые** строки в JS-файлах и перенести их в `messages-es.js`.  
JS-строки — это только то, что пользователь видит: уведомления, сообщения об ошибках, подтверждения.  
Внутренние строки (CSS-классы, URL, API пути, event names) **не переносим**.

---

## Что переносим, что оставляем

**Переносим:**
- Текст в `showNotification(...)` 
- Текст в `confirm(...)` диалогах
- Динамические сообщения об ошибках/успехе
- Placeholder-ы, устанавливаемые из JS

**Оставляем:**
- CSS классы: `'btn-primary'`, `'modal-open'`
- URL: `'/api/tournaments'`, `'/login'`
- HTTP методы: `'POST'`, `'GET'`
- Имена событий: `'click'`, `'submit'`
- Значения атрибутов: `'none'`, `'block'`
- Логи: `console.log('Debug: ...')`

---

## Структура `messages-es.js`

```javascript
window.APP_MESSAGES = {

    // === COMMON ===
    'common.loading':           'Cargando...',
    'common.error.generic':     'Ha ocurrido un error. Intenta de nuevo.',
    'common.error.network':     'Error de conexión. Verifica tu internet.',
    'common.copied':            'Copiado al portapapeles',
    'common.confirm.title':     '¿Estás seguro?',

    // === AUTH / REGISTRO ===
    'auth.password.mismatch':       'Las contraseñas no coinciden',
    'auth.password.weak':           'La contraseña debe tener al menos: 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial',
    'auth.terms.required':          'Debes aceptar los términos y condiciones',
    'auth.captcha.required':        'Por favor completa la verificación de seguridad',
    'auth.register.loading':        'Registrando...',
    'auth.register.success':        '¡Registro exitoso! Revisa tu email para confirmar tu cuenta',
    'auth.register.error.email':    'Este email ya está registrado',
    'auth.register.error.generic':  'Error al registrarse. Intenta de nuevo.',

    // === PASSWORD RESET ===
    'password_reset.email.sent':        'Si el email existe, recibirás instrucciones en breve',
    'password_reset.email.invalid':     'Introduce un email válido',
    'password_reset.password.mismatch': 'Las contraseñas no coinciden',
    'password_reset.password.weak':     'La contraseña no cumple los requisitos de seguridad',
    'password_reset.success':           '¡Contraseña actualizada! Ya puedes iniciar sesión',
    'password_reset.error.expired':     'El enlace ha expirado. Solicita uno nuevo.',
    'password_reset.error.generic':     'Error al actualizar la contraseña',
    'password_reset.loading':           'Actualizando...',

    // === DASHBOARD ===
    'dashboard.register.confirm':       '¿Confirmar inscripción en "{0}"?',
    'dashboard.register.success':       'Te has inscrito correctamente en {0}',
    'dashboard.register.error.contact': 'Necesitas añadir un teléfono o Telegram para inscribirte',
    'dashboard.register.error.full':    'El torneo está completo',
    'dashboard.register.error.generic': 'Error al procesar la inscripción',
    'dashboard.cancel.confirm':         '¿Cancelar tu inscripción en "{0}"?',
    'dashboard.cancel.success':         'Inscripción cancelada correctamente',
    'dashboard.cancel.error':           'Error al cancelar la inscripción',
    'dashboard.pair.cancel.confirm':    '¿Cancelar inscripción de pareja en "{0}"?',
    'dashboard.pair.cancel.success':    'Inscripción de pareja cancelada',
    'dashboard.loading':                'Cargando tus torneos...',

    // === PROFILE ===
    'profile.save.success':     'Perfil actualizado correctamente',
    'profile.save.error':       'Error al guardar el perfil',
    'profile.phone.invalid':    'Introduce un teléfono válido con formato +54...',
    'profile.telegram.invalid': 'El usuario de Telegram debe empezar con @',
    'profile.loading':          'Guardando...',

    // === CONTACT CHECK ===
    'contact.missing.title':    'Perfil incompleto',
    'contact.missing.text':     'Necesitas añadir un teléfono o Telegram en tu perfil para inscribirte en torneos',
    'contact.missing.btn':      'Completar perfil',
    'contact.missing.later':    'Más tarde',

    // === TOURNAMENT DETAILS ===
    'tournament.register.success':         'Te has inscrito correctamente',
    'tournament.register.waitlist':        'Añadido a la lista de espera',
    'tournament.register.error.login':     'Inicia sesión para inscribirte',
    'tournament.register.error.contact':   'Necesitas teléfono o Telegram para inscribirte',
    'tournament.register.error.full':      'El torneo está completo',
    'tournament.register.error.generic':   'Error al procesar la inscripción',
    'tournament.cancel.confirm':           '¿Cancelar tu inscripción?',
    'tournament.cancel.success':           'Inscripción cancelada',
    'tournament.cancel.error':             'Error al cancelar la inscripción',
    'tournament.share.copied':             'Enlace copiado al portapapeles',
    'tournament.loading':                  'Procesando...',

    // === TORNEOS (lista) ===
    'torneos.loading':          'Cargando torneos...',
    'torneos.empty':            'No se encontraron torneos con los filtros seleccionados',
    'torneos.error.load':       'Error al cargar los torneos',
    'torneos.filter.reset':     'Limpiar filtros',

    // === HOME ===
    'home.loading':             'Cargando torneos...',
    'home.empty':               'No hay torneos próximos disponibles',
    'home.error.load':          'Error al cargar los torneos',

    // === KING OF COURT VIEWER ===
    'koc.connecting':           'Conectando...',
    'koc.disconnected':         'Conexión perdida. Reconectando...',
    'koc.reconnected':          'Conexión restablecida',
    'koc.waiting':              'Esperando inicio del torneo...',
    'koc.round_started':        'Ronda {0} iniciada',
    'koc.finished':             'Torneo finalizado',
    'koc.error.load':           'Error al cargar el estado del torneo',

    // === RANKING ===
    'ranking.loading':          'Cargando ranking...',
    'ranking.empty':            'No hay datos de ranking disponibles',
    'ranking.error.load':       'Error al cargar el ranking',

    // === COOKIE CONSENT ===
    'cookie.saved':             'Preferencias de cookies guardadas',
    'cookie.error':             'Error al guardar las preferencias',

    // === SHARE ===
    'share.copied':             'Enlace copiado al portapapeles',
    'share.error':              'No se pudo copiar el enlace',
    'share.title':              'Compartir torneo',

    // === NOTIFICATIONS ===
    'notification.close':       'Cerrar',
};
```

---

## Строки с переменными в JS

Паттерн для замены `{0}` в JS:

```javascript
// helper (добавить в t.js или отдельным методом)
function t(key, ...args) {
    let msg = (window.APP_MESSAGES && window.APP_MESSAGES[key]) || key;
    args.forEach((arg, i) => { msg = msg.replace(`{${i}}`, arg); });
    return msg;
}

// Пример использования:
showNotification(t('dashboard.register.success', tournamentName), 'success');
// → "Te has inscrito correctamente en Copa de Verano"
```

---

## Файл по файлу: что менять

### `auth.js`
Строки: ~8 пользовательских
- Валидация пароля, совпадение паролей, принятие terms, captcha
- Статус кнопки (`Registrando...`)

### `password-reset.js`
Строки: ~7 пользовательских
- Отправка email, смена пароля, ошибки, статус загрузки

### `dashboard.js`
Строки: ~15 пользовательских
- Подтверждения регистрации/отмены, ошибки, статусы загрузки
- Самый сложный: есть динамические строки с именем турнира

### `tournament-details.js`
Строки: ~10 пользовательских
- Регистрация/отмена, ошибки, копирование ссылки

### `home.js`
Строки: ~4 пользовательских
- Загрузка, пустые состояния

### `torneos.js`
Строки: ~5 пользовательских
- Загрузка, пустые состояния, ошибки

### `profile.js`
Строки: ~6 пользовательских
- Сохранение профиля, валидация телефона/telegram

### `king-of-court-viewer.js`
Строки: ~7 пользовательских
- WebSocket статусы, состояние турнира

### `contact-check.js`
Строки: ~4 пользовательских
- Попап о неполном профиле

### `ranking.js`
Строки: ~3 пользовательских
- Загрузка, пустые состояния

### `cookie-consent.js`
Строки: ~3 пользовательских
- Сохранение настроек

### `share-panel.js`
Строки: ~3 пользовательских
- Копирование ссылки

### `main.js`, `notifications.js`
Строки: минимум, преимущественно UI-логика без текста

---

## Чеклист этапа 3

- [ ] `messages-es.js` — все пользовательские строки из JS перенесены
- [ ] `messages-ru.js` — создан, `window.APP_MESSAGES = {...}` со всеми ключами (значения пока как es)
- [ ] `messages-en.js` — создан аналогично
- [ ] `t.js` — функция поддерживает аргументы `t(key, ...args)` для замены `{0}`, `{1}`
- [ ] Нет дублирующихся ключей между template-ключами (messages.properties) и JS-ключами — это разные файлы и пути не пересекаются
- [ ] Задокументированы все строки с переменными (где используется `{0}`)
