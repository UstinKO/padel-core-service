(function () {
    const estimate = document.getElementById('duracion');
    const exact = document.getElementById('durationMinutes');
    if (!estimate || !exact?.dataset.suggestionUrl) return;

    let timer;
    function updateSuggestion() {
        clearTimeout(timer);
        exact.placeholder = '';
        const description = estimate.value.trim();
        if (!description) return;

        timer = setTimeout(async () => {
            const url = new URL(exact.dataset.suggestionUrl, document.baseURI);
            url.searchParams.set('description', description);
            try {
                const response = await fetch(url, {cache: 'no-store'});
                if (response.status !== 200) return;
                const minutes = await response.json();
                if (estimate.value.trim() === description && Number.isInteger(minutes) && minutes > 0) {
                    exact.placeholder = String(minutes);
                }
            } catch {
                // При недоступной подсказке форма остаётся доступна для ручного ввода.
            }
        }, 180);
    }

    estimate.addEventListener('input', updateSuggestion);
    updateSuggestion();
})();
