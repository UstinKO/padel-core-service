/**
 * LFPT-0573: вынесено из tournament-playoff.html (LFPT-0562) — форма поиска-или-создания
 * двух игроков команды Team Playoff теперь встраивается и на странице «Оплаты и посещаемость»,
 * поэтому логика живёт в общем файле, параметризованном URL поиска и способом показать
 * пользователю сообщение (на странице Team Playoff уже есть свой toast, на странице
 * оплат — нет, поэтому notify передаётся вызывающей страницей).
 *
 * @param {string} searchUrl базовый URL поиска игроков (без query), например
 *        "/tournaments/team-playoff/admin/42/players/search"
 * @param {{formEl: HTMLFormElement, noResults: string, searchError: string, playerRequired: string}} opts
 * @param {function(string): void} notify показывает пользователю короткое сообщение
 */
function initTeamPlayoffAddForm(searchUrl, opts, notify) {
    const formEl = opts.formEl;
    if (!formEl) return;

    formEl.querySelectorAll('.player-slot').forEach(function (slot) {
        const searchWrap = slot.querySelector('.player-search-wrap');
        const searchInput = slot.querySelector('.player-search-input');
        const resultsBox = slot.querySelector('.player-search-results');
        const selectedCard = slot.querySelector('.player-selected-card');
        const selectedName = slot.querySelector('.player-selected-name');
        const clearBtn = slot.querySelector('.player-clear-btn');
        const newFields = slot.querySelector('.player-new-fields');
        const toggleBtn = slot.querySelector('.player-toggle-mode-btn');
        const idField = slot.querySelector('.player-id-field');
        const newInputs = Array.from(newFields.querySelectorAll('input'));
        let debounceTimer = null;

        function playerLabel(p) {
            return (p.nombreCompleto || ((p.nombre || '') + ' ' + (p.apellido || ''))).trim();
        }

        function closeResults() {
            resultsBox.classList.remove('open');
            resultsBox.innerHTML = '';
        }

        function selectPlayer(player) {
            idField.value = player.id;
            selectedName.textContent = playerLabel(player);
            selectedCard.style.display = 'flex';
            searchWrap.style.display = 'none';
            closeResults();
        }

        function clearSelection() {
            idField.value = '';
            selectedCard.style.display = 'none';
            searchInput.value = '';
        }

        function renderMessage(cssClass, text) {
            resultsBox.innerHTML = '';
            const row = document.createElement('div');
            row.className = cssClass;
            row.textContent = text;
            resultsBox.appendChild(row);
            resultsBox.classList.add('open');
        }

        function renderResults(players) {
            if (!players.length) {
                renderMessage('player-result-empty', opts.noResults);
                return;
            }
            resultsBox.innerHTML = '';
            players.forEach(function (p) {
                const item = document.createElement('div');
                item.className = 'player-result-item';
                const name = document.createElement('div');
                name.className = 'pr-name';
                name.textContent = playerLabel(p);
                const contact = document.createElement('div');
                contact.className = 'pr-contact';
                contact.textContent = p.telefono || p.email || '';
                item.appendChild(name);
                item.appendChild(contact);
                item.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    selectPlayer(p);
                });
                resultsBox.appendChild(item);
            });
            resultsBox.classList.add('open');
        }

        function doSearch(query) {
            fetch(searchUrl + '?query=' + encodeURIComponent(query), {headers: {'Accept': 'application/json'}})
                .then(function (r) {
                    if (!r.ok) throw new Error('search failed');
                    return r.json();
                })
                .then(renderResults)
                .catch(function () {
                    renderMessage('player-result-error', opts.searchError);
                });
        }

        searchInput.addEventListener('input', function () {
            const query = searchInput.value.trim();
            clearTimeout(debounceTimer);
            if (query.length < 2) {
                closeResults();
                return;
            }
            debounceTimer = setTimeout(function () { doSearch(query); }, 300);
        });
        searchInput.addEventListener('blur', function () {
            setTimeout(closeResults, 150);
        });

        clearBtn.addEventListener('click', clearSelection);

        toggleBtn.addEventListener('click', function () {
            const showingNewFields = newFields.style.display !== 'none';
            if (showingNewFields) {
                newFields.style.display = 'none';
                newInputs.forEach(function (i) { i.value = ''; });
                searchWrap.style.display = 'block';
                toggleBtn.textContent = toggleBtn.dataset.labelNew;
            } else {
                newFields.style.display = 'flex';
                clearSelection();
                searchWrap.style.display = 'none';
                toggleBtn.textContent = toggleBtn.dataset.labelSearch;
            }
        });
    });

    formEl.addEventListener('submit', function (e) {
        const incomplete = Array.from(formEl.querySelectorAll('.player-slot')).some(function (slot) {
            const idField = slot.querySelector('.player-id-field');
            if (idField.value) return false;
            return Array.from(slot.querySelectorAll('.player-new-fields input'))
                .some(function (i) { return !i.value.trim(); });
        });
        if (incomplete) {
            e.preventDefault();
            notify(opts.playerRequired);
        }
    });
}
