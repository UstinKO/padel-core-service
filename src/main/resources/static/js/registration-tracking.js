/**
 * GA4-события воронки регистрации на турнир (LFPT-0522).
 * Без PII — только числовой ID турнира и enum-статус регистрации.
 */
(function () {
    'use strict';

    function track(eventName, params) {
        if (typeof window.gtag === 'function') {
            window.gtag('event', eventName, params);
        }
    }

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
