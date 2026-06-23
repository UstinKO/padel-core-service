/**
 * Возвращает переведённую строку по ключу.
 * Аргументы подставляются вместо {0}, {1}, ...
 *
 * Примеры:
 *   t('common.loading')                             → 'Cargando...'
 *   t('dashboard.register.success', 'Copa de Vera') → 'Te has inscrito en Copa de Vera'
 */
function t(key) {
    var messages = window.APP_MESSAGES || {};
    var msg = messages[key];
    if (msg === undefined) {
        console.warn('[i18n] Missing translation key:', key);
        return key;
    }
    for (var i = 1; i < arguments.length; i++) {
        msg = msg.replace('{' + (i - 1) + '}', arguments[i]);
    }
    return msg;
}
