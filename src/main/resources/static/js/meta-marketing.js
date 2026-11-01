/** Согласие, рекламный источник и события Meta без токенов и контактов. */
(function () {
    'use strict';
    if (window.metaMarketing) return;

    const originalFetch = window.fetch.bind(window);
    const landing = new URL(window.location.href);
    const publicRoute = /^\/(?:$|torneos$|clubs(?:\/\d+)?$|ranking$|torneo\/\d+$|tournaments\/(?:americano|team-americano|team-playoff)\/\d+(?:\/ranking)?$|tournaments\/king-of-court\/\d+$)/;
    const dashboard = landing.pathname === '/players/dashboard';
    const publicPage = publicRoute.test(landing.pathname);
    if (!publicPage && !dashboard) return;

    const campaignKeys = ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term'];
    const sent = new Set();
    const pageId = window.crypto.randomUUID();
    let config;
    let sdk;
    let initializedIdentity;
    let revoked = false;
    let pageTracked = false;
    let draining = Promise.resolve();
    let ready = Promise.resolve();
    let generation = 0;
    let contextReady = false;

    function cookie(name) {
        const pair = document.cookie.split(';').map(value => value.trim()).find(value => value.startsWith(name + '='));
        return pair ? decodeURIComponent(pair.slice(name.length + 1)) : '';
    }

    function consent() {
        return cookie('cookieConsent') === 'accepted'
            || cookie('cookieConsent') === 'customized' && cookie('marketingConsent') === 'true';
    }

    function label(value) {
        return /^[A-Za-z0-9_-]{1,100}$/.test(value || '') ? value : '';
    }

    function safeUrl(url) {
        if (url.username || url.password) return false;
        if (url.hash && !/^#[A-Za-z0-9_-]{0,50}$/.test(url.hash)) return false;
        for (const [key, value] of url.searchParams) {
            if (campaignKeys.includes(key) && (!value || label(value))) continue;
            if (key === 'lang' && ['es', 'ru', 'en'].includes(value)) continue;
            if (key === 'fbclid' && /^[A-Za-z0-9_-]{1,200}$/.test(value)) continue;
            return false;
        }
        return !/@|%40/i.test(url.pathname);
    }

    function safeReferrer() {
        if (!document.referrer) return true;
        try {
            return safeUrl(new URL(document.referrer));
        } catch (error) {
            return false;
        }
    }

    async function request(path, options = {}) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 2000);
        try {
            return await originalFetch(path, { ...options, credentials: 'same-origin', signal: controller.signal });
        } finally {
            clearTimeout(timer);
        }
    }

    async function capture(includeCampaign) {
        const values = campaignKeys.map(key => label(landing.searchParams.get(key)));
        const click = landing.searchParams.get('fbclid');
        const response = await request('/api/marketing/context', {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                path: publicPage ? landing.pathname : '/',
                utm: includeCampaign ? Object.fromEntries(['source', 'medium', 'campaign', 'content', 'term'].map((key, index) => [key, values[index]])) : null,
                fbclid: includeCampaign && /^[A-Za-z0-9_-]{1,200}$/.test(click || '') ? click : null,
                fbp: cookie('_fbp') || null
            })
        });
        if (!response.ok) return {};
        return response.json();
    }

    function loadPixel(identity) {
        if (sdk) return sdk;
        if (window.fbq) return Promise.reject(new Error('Pixel уже установлен другим кодом'));
        const queue = function () {
            if (queue.callMethod) queue.callMethod.apply(queue, arguments);
            else queue.queue.push(arguments);
        };
        queue.queue = [];
        queue.push = queue;
        queue.loaded = true;
        queue.version = '2.0';
        window.fbq = window._fbq = queue;
        window.fbq('set', 'autoConfig', false, config.pixelId);
        window.fbq('consent', 'grant');
        window.fbq('init', config.pixelId, { external_id: identity });
        initializedIdentity = identity;
        sdk = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            script.async = true;
            script.src = 'https://connect.facebook.net/en_US/fbevents.js';
            script.referrerPolicy = 'no-referrer';
            script.onload = resolve;
            script.onerror = reject;
            document.head.appendChild(script);
        });
        return sdk;
    }

    function trackPage() {
        if (!publicPage || pageTracked || !consent()) return;
        pageTracked = true;
        window.fbq('track', 'PageView', {}, { eventID: pageId });
        const event = /^\/torneo\/(\d+)$/.exec(landing.pathname);
        if (event) {
            window.fbq('track', 'ViewContent', { content_ids: [event[1]], content_type: 'product' }, { eventID: pageId + ':content' });
        }
    }

    function drain() {
        draining = draining.then(async () => {
            if (!sdk || revoked || !consent()) return;
            await sdk;
            if (!consent() || revoked) return;
            const response = await request('/api/marketing/events');
            if (!response.ok) return;
            for (const event of await response.json()) {
                if (!consent() || revoked) return;
                if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/.test(event.eventId) || sent.has(event.eventId)) continue;
                if (!['EventRegistration', 'EventWaitlist', 'EventRegistrationPending'].includes(event.eventName)) continue;
                if (!Number.isSafeInteger(event.tournamentId) || event.tournamentId <= 0) continue;
                const statuses = ['CONFIRMED', 'PARTICIPATED', 'WAITLIST', 'WAITLIST_INVITED', 'PENDING_PARTNER', 'PARTNER_INVITED', 'PAIR_REGISTERED', 'SOLO_ADD_LATER', 'SOLO_SEARCH'];
                if (!statuses.includes(event.registrationStatus)) continue;
                window.fbq('trackCustom', event.eventName, {
                    content_ids: [String(event.tournamentId)], content_type: 'product', registration_status: event.registrationStatus
                }, { eventID: event.eventId });
                sent.add(event.eventId);
                await request('/api/marketing/ack', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ eventId: event.eventId })
                });
            }
        }).catch(() => {});
        return draining;
    }

    async function start() {
        if (!consent()) return;
        const currentGeneration = generation;
        if (!config) {
            const response = await request('/api/marketing/config');
            if (!response.ok) return;
            config = await response.json();
        }
        if (!config.enabled || !/^\d{5,30}$/.test(config.pixelId) || !consent()) return;
        const identity = await capture(!pageTracked);
        if (!/^[a-f0-9]{64}$/.test(identity.externalId || '') || !consent() || currentGeneration !== generation) return;
        if (!safeUrl(landing) || !safeReferrer() || revoked) return;
        // Параметры кампании уже сохранены сервером; Pixel получает URL без query.
        history.replaceState(history.state, '', landing.pathname + landing.hash);
        if (identity.fbc && /^fb\.[0-9]+\.[0-9]{10,16}\.[A-Za-z0-9_-]{1,200}$/.test(identity.fbc)) {
            document.cookie = '_fbc=' + identity.fbc + '; Path=/; Max-Age=7776000; SameSite=Lax' + (location.protocol === 'https:' ? '; Secure' : '');
        }
        if (initializedIdentity && initializedIdentity !== identity.externalId) return;
        loadPixel(identity.externalId).then(async () => {
            if (!consent() || currentGeneration !== generation) return;
            await capture(false);
            trackPage();
            await drain();
        }).catch(() => {});
    }

    function registrationRequest(input, options) {
        try {
            const url = new URL(typeof input === 'string' ? input : input.url, location.href);
            const method = (options?.method || input?.method || 'GET').toUpperCase();
            return method === 'POST' && url.origin === location.origin
                && (/^\/players\/tournaments\/\d+\/register$/.test(url.pathname)
                    || /^\/api\/tournaments\/double\/(?:\d+\/(?:register|register-solo|add-partner|propose-pair\/\d+)|accept-pair|confirm)$/.test(url.pathname));
        } catch (error) {
            return false;
        }
    }

    window.fetch = async function (input, options) {
        const registration = registrationRequest(input, options);
        if (registration) await ready;
        const response = await originalFetch(input, options);
        if (registration && response.ok) void drain();
        return response;
    };

    window.addEventListener('marketing-consent-changed', function () {
        generation++;
        if (!consent()) {
            if (sdk) {
                revoked = true;
                window.fbq('consent', 'revoke');
            }
            for (const name of ['_fbp', '_fbc']) {
                document.cookie = name + '=; Max-Age=0; Path=/; SameSite=Lax';
                document.cookie = name + '=; Max-Age=0; Path=/; Domain=' + location.hostname + '; SameSite=Lax';
            }
        } else {
            prepare();
        }
    });

    function prepare() {
        contextReady = false;
        ready = ready.then(start).catch(() => {}).then(() => { contextReady = true; });
    }

    document.addEventListener('submit', function (event) {
        const form = event.target;
        if (!contextReady && form instanceof HTMLFormElement && registrationRequest(form.action, { method: form.method })) {
            event.preventDefault();
            event.stopImmediatePropagation();
            const submitter = event.submitter;
            void ready.then(() => form.requestSubmit(submitter || undefined));
        }
    }, true);

    window.metaMarketing = { flush: drain, ready: () => ready };
    prepare();
})();
