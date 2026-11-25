(() => {
    const leaderboard = document.querySelector('#elo-leaderboard');
    const form = leaderboard.querySelector('.elo-preview-filters');
    const { category, status, player } = form.elements;
    const rows = [...leaderboard.querySelectorAll('tbody tr')];
    const count = leaderboard.querySelector('output');
    const requestedCategory = new URLSearchParams(window.location.search).get('category');
    if ([...category.options].some(option => option.value === requestedCategory)) {
        category.value = requestedCategory;
    }

    function filter() {
        const query = player.value.trim().toLocaleLowerCase();
        let visible = 0;
        for (const row of rows) {
            row.hidden = Boolean(
                (category.value && row.dataset.category !== category.value) ||
                (status.value && row.dataset.status !== status.value) ||
                !row.querySelector('th').textContent.toLocaleLowerCase().includes(query)
            );
            if (!row.hidden) visible++;
        }
        count.value = visible;
    }

    form.addEventListener('input', filter);
    form.addEventListener('submit', event => event.preventDefault());
    form.hidden = false;
    filter();
})();
