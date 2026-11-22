(() => {
    const listPath = '/admin/tournaments';
    const storageKey = 'adminTournamentListFilters';
    const filterNames = ['from', 'to', 'format', 'clubId', 'gender', 'level', 'status', 'view', 'month', 'day'];

    function listFilters(search) {
        const source = new URLSearchParams(search);
        const filters = new URLSearchParams();
        for (const name of filterNames) {
            const value = source.get(name);
            if (value) {
                filters.set(name, value);
            }
        }
        return filters;
    }

    try {
        if (document.getElementById('tournamentListFilters')) {
            const remember = () => {
                try { sessionStorage.setItem(storageKey, listFilters(window.location.search).toString()); }
                catch (error) { if (!(error instanceof DOMException)) throw error; }
            };
            remember();
            window.addEventListener('pagehide', remember);
            return;
        }

        const filters = listFilters(sessionStorage.getItem(storageKey));
        if (filters.toString()) {
            filters.set('lang', document.documentElement.lang);
            for (const link of document.querySelectorAll('a[href="/admin/tournaments"]')) {
                link.href = listPath + '?' + filters.toString();
            }
        }
    } catch (error) {
        // Фильтры в URL продолжают работать при отключённом хранилище браузера.
        if (!(error instanceof DOMException)) {
            throw error;
        }
    }
})();
