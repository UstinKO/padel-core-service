(function () {
    'use strict';
    const picker = document.getElementById('calendarPicker');
    if (!picker) return;
    const toggle = document.getElementById('addToCalendarBtn');
    const panel = document.getElementById('calendarOptions');
    const status = document.getElementById('calendarStatus');
    const links = Array.from(panel.querySelectorAll('[data-calendar]'));
    const language = ['ru', 'es', 'en'].includes(picker.dataset.language) ? picker.dataset.language : 'es';
    const endpoint = '/api/public/tournaments/' + encodeURIComponent(picker.dataset.eventId) + '/calendar';
    const pageLink = document.getElementById('calendarPageLink');
    pageLink.value = window.location.origin + '/torneo/' + encodeURIComponent(picker.dataset.eventId);
    pageLink.addEventListener('click', () => pageLink.select());
    let checking = false;
    let ready = false;

    function close(restoreFocus) {
        panel.hidden = true;
        toggle.setAttribute('aria-expanded', 'false');
        if (restoreFocus) toggle.focus();
    }

    function enableLinks(enabled) {
        links.forEach(link => {
            link.setAttribute('aria-disabled', String(!enabled));
            link.tabIndex = enabled ? 0 : -1;
        });
    }

    async function check() {
        if (checking) return;
        checking = true;
        enableLinks(false);
        panel.setAttribute('aria-busy', 'true');
        status.hidden = true;
        try {
            const response = await fetch(endpoint + '?lang=' + language, { credentials: 'same-origin', cache: 'no-store' });
            if (!response.ok) {
                status.textContent = t(response.status === 422 ? 'calendar.unavailable' : 'calendar.load_error');
                status.hidden = false;
                return;
            }
            // Ссылки читают актуальные данные повторно при выборе календаря.
            ready = true;
            enableLinks(true);
        } catch (error) {
            status.textContent = t('calendar.load_error');
            status.hidden = false;
        } finally {
            checking = false;
            panel.removeAttribute('aria-busy');
        }
    }

    links.forEach(link => link.addEventListener('click', event => {
        if (!ready || link.getAttribute('aria-disabled') === 'true') event.preventDefault();
    }));
    toggle.addEventListener('click', () => {
        if (!panel.hidden) return close(false);
        panel.hidden = false;
        toggle.setAttribute('aria-expanded', 'true');
        ready = false;
        check();
    });
    document.addEventListener('click', event => {
        if (!picker.contains(event.target)) close(false);
    });
    picker.addEventListener('keydown', event => {
        if (event.key === 'Escape' && !panel.hidden) {
            event.preventDefault();
            close(true);
        }
    });
    picker.addEventListener('focusout', () => {
        setTimeout(() => { if (!picker.contains(document.activeElement)) close(false); }, 0);
    });
})();
