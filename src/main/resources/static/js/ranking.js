/**
 * Padel Core - Ranking Page JavaScript
 */

class RankingPage {
    constructor() {
        this.rankingData = [];
        this.filteredData = [];
        this.currentFilter = 'all';

        this.init();
    }

    init() {
        if (typeof rankingData !== 'undefined') {
            this.rankingData = rankingData;
            this.filteredData = [...this.rankingData];
        }

        this.initElements();
        this.initEventListeners();
        this.renderRanking();
        this.hideLoading();
    }

    initElements() {
        this.filterTabs = document.querySelectorAll('.filter-tab');
        this.rankingBody = document.getElementById('rankingBody');
        this.rankingLoading = document.getElementById('rankingLoading');
        this.rankingEmpty = document.getElementById('rankingEmpty');
    }

    initEventListeners() {
        this.filterTabs.forEach(tab => {
            tab.addEventListener('click', () => {
                const filter = tab.dataset.filter;
                this.applyFilter(filter);

                // Update active tab
                this.filterTabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
            });
        });
    }

    applyFilter(filter) {
        this.currentFilter = filter;

        if (filter === 'all') {
            this.filteredData = [...this.rankingData];
        } else {
            this.filteredData = this.rankingData.filter(player =>
                player.nivelActual === filter
            );
        }

        this.renderRanking();
    }

    renderRanking() {
        if (!this.rankingBody) return;

        if (this.filteredData.length === 0) {
            this.rankingBody.innerHTML = '';
            this.rankingEmpty.style.display = 'block';
            return;
        }

        this.rankingEmpty.style.display = 'none';

        this.rankingBody.innerHTML = this.filteredData.map((player, index) => {
            const posicion = index + 1;
            const posicionClass = this.getPositionClass(posicion);
            const tendenciaClass = `tendencia ${player.tendencia || 'stable'}`;
            const tendenciaIcon = this.getTendenciaIcon(player.tendencia);

            return `
                <tr>
                    <td>
                        <span class="position-badge ${posicionClass}">${posicion}</span>
                    </td>
                    <td>
                        <strong>${this.escapeHtml(player.playerNombreCompleto || 'Jugador')}</strong>
                    </td>
                    <td>
                        <span class="nivel-badge nivel-${player.nivelActual || 'C9'}">
                            ${player.nivelActual || 'C9'}
                        </span>
                    </td>
                    <td>${player.torneosJugados || 0}</td>
                    <td>${player.torneosGanados || 0}</td>
                    <td>${player.winRateFormateado || '0%'}</td>
                    <td><strong>${player.puntos || 0}</strong></td>
                    <td>
                        <span class="${tendenciaClass}">
                            ${tendenciaIcon}
                        </span>
                    </td>
                </tr>
            `;
        }).join('');
    }

    getPositionClass(position) {
        if (position === 1) return 'top-1';
        if (position === 2) return 'top-2';
        if (position === 3) return 'top-3';
        return '';
    }

    getTendenciaIcon(tendencia) {
        if (tendencia === 'up') return '<i class="fas fa-arrow-up"></i> Subiendo';
        if (tendencia === 'down') return '<i class="fas fa-arrow-down"></i> Bajando';
        return '<i class="fas fa-minus"></i> Estable';
    }

    hideLoading() {
        if (this.rankingLoading) {
            this.rankingLoading.style.display = 'none';
        }
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    new RankingPage();
});