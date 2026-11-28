/** Сохраняет выбранную категорию формы только на переходе между языками. */
(function () {
    'use strict';
    const form = document.getElementById('tournamentForm');
    if (!form) return;
    const fields = ['tipo', 'modalidad', 'generoFormato', 'categoriaNivel'];
    const key = 'tournament-form-language:' + location.pathname;
    // Читаем до обработчиков DOMContentLoaded, которые фильтруют категории формы.
    try {
        const raw = sessionStorage.getItem(key);
        sessionStorage.removeItem(key);
        if (raw) {
            const draft = JSON.parse(raw);
            if (Date.now() - draft.savedAt < 60000) {
                fields.forEach(id => {
                    const select = document.getElementById(id);
                    if (select && [...select.options].some(option => option.value === draft[id])) {
                        select.value = draft[id];
                    }
                });
            }
        }
    } catch (_) { /* При недоступном storage остаётся обычная серверная форма. */ }
    document.addEventListener('click', event => {
        const button = event.target.closest('.lang-switcher-option[data-lang]');
        if (!button || !['ru', 'es', 'en'].includes(button.dataset.lang)) return;
        const draft = {savedAt: Date.now()};
        fields.forEach(id => { draft[id] = document.getElementById(id)?.value || ''; });
        try { sessionStorage.setItem(key, JSON.stringify(draft)); } catch (_) { /* storage необязателен */ }
    }, true);
})();
