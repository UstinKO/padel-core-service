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

    document.addEventListener('click', function (event) {
        const target = event.target instanceof Element
            ? event.target.closest('[data-registration-intent]') : null;
        if (!target || typeof window.gtag !== 'function') {
            return;
        }

        const cookies = document.cookie.split(';').map(cookie => cookie.trim());
        const analyticsAllowed = cookies.includes('cookieConsent=accepted')
            || (cookies.includes('cookieConsent=customized') && cookies.includes('analyticsConsent=true'));
        const id = target.dataset.registrationIntent;
        if (!analyticsAllowed || !/^[1-9][0-9]*$/.test(id) || recordedIntents.has(id)) {
            return;
        }

        const key = 'tournament-registration-intent:' + id;
        try {
            if (window.sessionStorage.getItem(key)) {
                return;
            }
            window.sessionStorage.setItem(key, '1');
        } catch {
            // В браузере без доступа к sessionStorage остаётся дедупликация на странице.
        }
        recordedIntents.add(id);
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
