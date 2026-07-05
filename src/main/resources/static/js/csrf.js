/**
 * Автоматически добавляет CSRF-токен (из cookie XSRF-TOKEN, устанавливается
 * Spring Security CookieCsrfTokenRepository) в заголовок X-XSRF-TOKEN для всех
 * fetch()-запросов на изменяющие методы. Позволяет не редактировать вручную
 * каждый существующий вызов fetch() в проекте.
 */
(function () {
    var COOKIE_NAME = 'XSRF-TOKEN';
    var HEADER_NAME = 'X-XSRF-TOKEN';
    var UNSAFE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

    function readCookie(name) {
        var match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[1]) : null;
    }

    function isSameOrigin(url) {
        try {
            return new URL(url, window.location.href).origin === window.location.origin;
        } catch (e) {
            return true;
        }
    }

    window.getCsrfToken = function () {
        return readCookie(COOKIE_NAME);
    };

    var originalFetch = window.fetch;
    if (typeof originalFetch === 'function') {
        window.fetch = function (input, init) {
            init = init || {};
            var method = (init.method || 'GET').toUpperCase();
            var url = typeof input === 'string' ? input : (input && input.url) || '';

            if (UNSAFE_METHODS.indexOf(method) !== -1 && isSameOrigin(url)) {
                var token = window.getCsrfToken();
                if (token) {
                    var headers = new Headers(init.headers || {});
                    headers.set(HEADER_NAME, token);
                    init = Object.assign({}, init, { headers: headers });
                }
            }

            return originalFetch(input, init);
        };
    }
})();
