(() => {
    const narrow = window.matchMedia('(max-width: 899px)');
    const copy = document.querySelector('[data-individual-ranking-copy]')?.dataset ?? {};
    const escape = value => tournamentPoints.escape(value);
    const sign = value => Number(value) > 0 ? `+${value}` : String(value ?? 0);
    const value = (label, points) => `<dt>${escape(label)}</dt><dd>${escape(points ?? 0)}</dd>`;
    const level = player => player.playerNivel && player.playerNivel !== 'SIN_ESPECIFICAR'
        ? String(player.playerNivel).replaceAll('_', '/') : '';

    window.IndividualRanking = {
        render(tbody, ranking, {king = false, canEdit = false, publicView = false, currentPlayerId} = {}) {
            if (!tbody) return;
            canEdit = canEdit && !publicView;
            tbody.closest('table').classList.toggle('ir-public', publicView);
            const expanded = new Set([...tbody.querySelectorAll('[data-ranking-toggle][aria-expanded="true"]')]
                .map(button => button.dataset.rankingToggle));
            const focused = tbody.contains(document.activeElement) ? document.activeElement : null;
            const focusKey = focused?.dataset.rankingToggle;
            const openStages = new Set([...tbody.querySelectorAll('[data-ranking-details]')]
                .filter(row => row.querySelector('.ir-stages[open]'))
                .map(row => row.dataset.rankingDetails));
            const openBreakdown = new Set([...tbody.querySelectorAll('[data-ranking-row], [data-ranking-details]')]
                .filter(row => row.querySelector('.additional-points-breakdown[open]'))
                .map(row => (row.hasAttribute('data-ranking-details') ? 'detail-' : 'row-') + (row.dataset.rankingRow || row.dataset.rankingDetails)));
            const columns = publicView ? 3 : king ? 8 : 7;
            tbody.dataset.rankingColumns = String(columns);
            const visibleColumns = narrow.matches ? 3 : columns;
            tbody.innerHTML = ranking.map(player => {
                const id = String(player.playerId);
                const name = [player.playerName, king ? '' : player.playerLastName].filter(Boolean).join(' ');
                const rank = king ? player.rank : player.position;
                const total = king ? player.totalPoints : player.totalScore;
                const games = king ? player.gamesPlayed : player.matchesPlayed;
                const wins = king ? player.wins : player.matchesWon;
                const losses = king ? player.losses : player.matchesLost;
                const detailsId = `${tbody.id}-details-${id}`;
                const metadata = publicView ? '' : `<small class="playing-side-info" data-playing-side-player="${escape(id)}" data-playing-side-compact>${escape(level(player))}</small>`;
                const action = canEdit ? `<button type="button" class="ir-edit" data-open-points data-player-id="${escape(id)}"
                    aria-label="${escape(tournamentPoints.copy.action)}: ${escape(name)}">±<span class="ir-sr-only"> ${escape(tournamentPoints.copy.action)}</span></button>` : '';
                const breakdown = tournamentPoints.cell(player.additionalPoints);
                const extras = king ? '' : value(copy.draws, player.matchesDrawn) + value(copy.difference, sign(player.pointDifference))
                    + value(copy.byes, player.byeCount) + value(copy.partners, player.uniquePartners) + value(copy.opponents, player.uniqueOpponents);
                return `<tr data-ranking-row="${escape(id)}">
                    <td class="ir-rank">${escape(rank ?? '—')}</td>
                    <td class="ir-player ${canEdit ? 'ir-has-action' : ''}">
                        <button type="button" class="ir-toggle" data-ranking-toggle="${escape(id)}" aria-controls="${detailsId}" aria-expanded="${expanded.has(id)}">
                            <span>${escape(name)}${Number(currentPlayerId) === Number(id) ? `<span class="ir-me" aria-label="${escape(copy.me)}"> ✓</span>` : ''}</span><span aria-hidden="true" class="ir-chevron">⌄</span><span class="ir-sr-only"> — ${escape(copy.details)}</span>
                        </button>${metadata}${action}
                    </td>
                    <td class="ir-total">${escape(total ?? 0)}</td>
                    ${publicView ? '' : `
                    <td class="ir-secondary">${escape(player.matchPoints ?? 0)}</td>
                    ${king ? `<td class="ir-secondary">${escape(sign(player.bonusPoints))}</td>` : ''}
                    ${breakdown.replace('class="additional-points-cell"', 'class="additional-points-cell ir-secondary"')}
                    <td class="ir-secondary">${escape(games ?? 0)}</td>
                    <td class="ir-secondary">${escape(wins ?? 0)}/${escape(losses ?? 0)}</td>`}
                </tr>
                <tr class="ir-detail-row" data-ranking-details="${escape(id)}" ${expanded.has(id) ? '' : 'hidden'}>
                    <td colspan="${visibleColumns}" id="${detailsId}">
                        <div class="ir-detail-content">
                            ${publicView ? '' : `<small class="playing-side-info" data-playing-side-player="${escape(id)}">${escape(level(player))}</small>`}
                            <dl>${value(copy.match, player.matchPoints)}${king ? value(copy.bonus, sign(player.bonusPoints)) : ''}
                                ${value(copy.additional, sign(player.additionalPoints?.total))}${value(copy.games, games)}
                                ${value(copy.wins, wins)}${value(copy.losses, losses)}${extras}</dl>
                            <details class="ir-stages" ${openStages.has(id) ? 'open' : ''}><summary>${escape(copy.stages)}</summary>
                                <dl>${(player.additionalPoints?.stages ?? []).map(item => value(item.roundNumber === 0 ? tournamentPoints.copy.before : `${tournamentPoints.copy.round} ${item.roundNumber}`, sign(item.points))).join('')}${value(copy.additional, sign(player.additionalPoints?.total))}</dl>
                            </details>
                            <div class="ir-detail-actions">${canEdit ? tournamentPoints.action(id) : ''}
                                ${king ? `<button type="button" class="btn btn-outline btn-sm view-history-btn" data-player-id="${escape(id)}" data-player-name="${escape(name)}">${escape(copy.history)}</button>` : ''}
                            </div>
                        </div>
                    </td>
                </tr>`;
            }).join('') || `<tr><td colspan="${visibleColumns}">${escape(copy.empty)}</td></tr>`;
            for (const row of tbody.querySelectorAll('[data-ranking-row], [data-ranking-details]')) {
                const key = (row.hasAttribute('data-ranking-details') ? 'detail-' : 'row-') + (row.dataset.rankingRow || row.dataset.rankingDetails);
                if (openBreakdown.has(key)) row.querySelector('.additional-points-breakdown')?.setAttribute('open', '');
            }
            if (focusKey) [...tbody.querySelectorAll('[data-ranking-toggle]')]
                .find(button => button.dataset.rankingToggle === focusKey)?.focus({preventScroll: true});
        }
    };
    narrow.addEventListener('change', () => {
        for (const tbody of document.querySelectorAll('tbody[data-ranking-columns]')) {
            for (const cell of tbody.querySelectorAll('[colspan]')) {
                cell.colSpan = narrow.matches ? 3 : Number(tbody.dataset.rankingColumns);
            }
        }
    });
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-ranking-toggle]');
        if (!button) return;
        const row = document.getElementById(button.getAttribute('aria-controls'))?.parentElement;
        if (!row) return;
        row.hidden = !row.hidden;
        button.setAttribute('aria-expanded', String(!row.hidden));
    });
})();
