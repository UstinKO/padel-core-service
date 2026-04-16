/**
 * logger.js
 * Отключает console.log/debug/info в production.
 * Подключить ПЕРВЫМ скриптом в <head> через Thymeleaf fragments.
 *
 * В production (th:if="${!@environment.acceptsProfiles('dev','development','default')}"):
 *   console.log, console.debug, console.info → noop
 *   console.warn, console.error → остаются (нужны для реальных ошибок)
 */
(function() {
    'use strict';

    // Читаем профиль из meta тега (выставляется на сервере)
    const metaProfile = document.querySelector('meta[name="app-profile"]');
    const profile = metaProfile ? metaProfile.getAttribute('content') : 'prod';

    const isDev = ['dev', 'development', 'default', 'local'].includes(profile);

    if (!isDev) {
        // В production отключаем шумные логи
        const noop = function() {};
        console.log   = noop;
        console.debug = noop;
        console.info  = noop;
        console.trace = noop;
        // console.warn и console.error оставляем — они нужны для реальных проблем
    }
})();