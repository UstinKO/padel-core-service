/**
 * GA4-события воронки регистрации на турнир.
 * Без PII — только числовой ID турнира и enum-статус регистрации.
 */
(function () {
    'use strict';

    function track(eventName, params) {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, params);
        }
    }

    const recordedIntents = new Set();
    const pendingIntentKey = 'pending-tournament-registration-intent';
    let pendingTournament;
    let pendingTimer;

    function analyticsAllowed() {
        const cookies = document.cookie.split(';').map(cookie => cookie.trim());
        return cookies.includes('cookieConsent=accepted')
            || (cookies.includes('cookieConsent=customized') && cookies.includes('analyticsConsent=true'));
    }

    function readPendingIntent() {
        let pending;
        try {
            pending = window.sessionStorage.getItem(pendingIntentKey);
            window.sessionStorage.removeItem(pendingIntentKey);
        } catch {
            return;
        }
        if (!pending || !analyticsAllowed()) {
            return;
        }
        const [id, destination] = pending.split('|', 2);
        if (/^[1-9][0-9]*$/.test(id) && destination === window.location.pathname + window.location.search) {
            return id;
        }
    }

    function flushPendingIntent() {
        window.clearTimeout(pendingTimer);
        const id = pendingTournament;
        pendingTournament = undefined;
        if (id && analyticsAllowed()) {
            track('tournament_registration_click', { tournament_id: id });
        }
    }

    function sameTabDestination(target, event) {
        if (target instanceof HTMLAnchorElement && target.origin === window.location.origin
            && (!target.target || target.target === '_self') && !target.hasAttribute('download')
            && event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey) {
            return target.pathname + target.search;
        }
    }

    function storePendingIntent(id, destination) {
        window.sessionStorage.setItem(pendingIntentKey, id + '|' + destination);
    }

    pendingTournament = readPendingIntent();
    if (pendingTournament && document.querySelector('a.btn-login[data-registration-intent="' + pendingTournament + '"]')) {
        // У гостя карточка может быть промежуточной страницей перед входом.
        pendingTimer = window.setTimeout(flushPendingIntent, 2500);
    } else {
        flushPendingIntent();
    }

    document.addEventListener('click', function (event) {
        const target = event.target instanceof Element
            ? event.target.closest('[data-registration-intent]') : null;
        if (!target || event.defaultPrevented || typeof window.gtag !== 'function') {
            return;
        }

        const id = target.dataset.registrationIntent;
        if (!analyticsAllowed() || !/^[1-9][0-9]*$/.test(id) || recordedIntents.has(id)) {
            return;
        }

        const destination = sameTabDestination(target, event);
        if (id === pendingTournament) {
            if (destination) {
                try {
                    storePendingIntent(id, destination);
                    window.clearTimeout(pendingTimer);
                    pendingTournament = undefined;
                    return;
                } catch {
                    // Если сохранить переход нельзя, отправляем с текущей страницы.
                }
            }
            flushPendingIntent();
            return;
        }

        recordedIntents.add(id);
        const key = 'tournament-registration-intent:' + id;
        try {
            if (window.sessionStorage.getItem(key)) {
                return;
            }
            window.sessionStorage.setItem(key, '1');
            if (destination) {
                // Уход со страницы может прервать пакет GA4. Передаём клик странице назначения.
                storePendingIntent(id, destination);
                return;
            }
        } catch {
            // В браузере без доступа к sessionStorage остаётся дедупликация на странице.
        }
        track('tournament_registration_click', { tournament_id: id });
    }, true);

    window.trackTournamentRegistrationStart = function (tournamentId) {
        track('tournament_registration_start', {
            tournament_id: tournamentId != null ? String(tournamentId) : undefined
        });
    };

    window.trackTournamentRegistrationComplete = function (tournamentId, registrationStatus) {
        track('tournament_registration_complete', {
            tournament_id: tournamentId != null ? String(tournamentId) : undefined,
            registration_status: registrationStatus
        });
    };
})();
