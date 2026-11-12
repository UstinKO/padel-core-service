/**
 * События GA4 и Clarity для записи на турнир.
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

    const clarityVisitsKey = 'clarity-registration-visits';
    const visitLifetime = 7 * 24 * 60 * 60 * 1000;
    const claritySteps = new Set(['intent', 'form_opened', 'contact_form_opened', 'submit_attempt']);
    const clarityErrors = new Set([
        'required_fields', 'level_required', 'registration_rejected', 'request_failed', 'terms_changed',
        'contact_required', 'contact_invalid', 'contact_save_rejected', 'contact_request_failed'
    ]);
    const clarityResults = new Map([
        ['CONFIRMED', 'confirmed'],
        ['WAITLIST', 'waitlisted'],
        ['PARTNER_INVITED', 'partner_pending'],
        ['SEARCH', 'solo_requested'],
        ['ADD_LATER', 'solo_requested']
    ]);

    function emitClarity(event, id) {
        window.clarity('set', 'registration_' + event + '_tournament', id);
        window.clarity('event', 'registration_' + event);
    }

    function updateClarityVisit(id, sessionId, event) {
        if (!analyticsAllowed() || typeof sessionId !== 'string' || !sessionId) {
            return;
        }
        try {
            const now = Date.now();
            const saved = JSON.parse(window.localStorage.getItem(clarityVisitsKey) || '{}');
            const visits = Object.fromEntries(Object.entries(saved || {}).filter(([key, visit]) =>
                /^[1-9][0-9]*$/.test(key) && typeof visit?.session === 'string'
                && visit.expires > now && visit.expires <= now + visitLifetime));
            const previous = visits[id];

            if (event === 'confirmed') {
                delete visits[id];
                if (previous && previous.session !== sessionId) {
                    emitClarity('confirmed_after_return', id);
                }
            } else if (claritySteps.has(event) && !previous) {
                visits[id] = { session: sessionId, expires: now + visitLifetime };
            }

            // Храним только ID сеанса Clarity в браузере, без идентификатора игрока.
            const recent = Object.entries(visits).sort((a, b) => b[1].expires - a[1].expires).slice(0, 30);
            if (recent.length) {
                window.localStorage.setItem(clarityVisitsKey, JSON.stringify(Object.fromEntries(recent)));
            } else {
                window.localStorage.removeItem(clarityVisitsKey);
            }
        } catch {
            // Блокировка хранилища или Clarity не должна влиять на запись.
        }
    }

    function trackClarity(tournamentId, event, errorCode) {
        const id = String(tournamentId);
        if (!/^[1-9][0-9]*$/.test(id) || !analyticsAllowed() || typeof window.clarity !== 'function') {
            return;
        }
        try {
            if (errorCode) {
                window.clarity('set', 'registration_error_code', errorCode);
            }
            emitClarity(event, id);
            if (errorCode) {
                window.clarity('event', 'registration_error_' + errorCode);
            }
            if (claritySteps.has(event) || event === 'confirmed') {
                window.clarity('metadata', data => updateClarityVisit(id, data?.sessionId, event), false);
            }
        } catch {
            // Сбой аналитики не меняет поведение кнопок или ответов регистрации.
        }
    }

    window.trackClarityRegistrationStep = function (tournamentId, step) {
        if (claritySteps.has(step)) {
            trackClarity(tournamentId, step);
        }
    };

    window.trackClarityRegistrationError = function (tournamentId, code) {
        if (clarityErrors.has(code)) {
            trackClarity(tournamentId, 'error', code);
        }
    };

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
        if (!target || event.defaultPrevented) {
            return;
        }

        const id = target.dataset.registrationIntent;
        trackClarity(id, 'intent');
        if (typeof window.gtag !== 'function') {
            return;
        }
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
        trackClarity(tournamentId, 'submit_attempt');
        track('tournament_registration_start', {
            tournament_id: tournamentId != null ? String(tournamentId) : undefined
        });
    };

    window.trackTournamentRegistrationComplete = function (tournamentId, registrationStatus) {
        const result = clarityResults.get(registrationStatus);
        if (result) {
            trackClarity(tournamentId, result);
        }
        track('tournament_registration_complete', {
            tournament_id: tournamentId != null ? String(tournamentId) : undefined,
            registration_status: registrationStatus
        });
    };
})();
