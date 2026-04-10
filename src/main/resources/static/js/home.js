/**
 * Padel Core - Home Page JavaScript
 */

// Проверяет, начался ли уже турнир (текущее время > дата+время старта)
function isTournamentStarted(tournament) {
    // Проверка по статусу
    const closedStatuses = ['FINALIZADO', 'CANCELADO', 'CERRADO'];
    if (closedStatuses.includes(tournament.estado)) {
        return true;
    }

    let start = null;

    const fechaInicio = tournament.fechaInicio;
    const horaInicio  = tournament.horaInicio;

    if (Array.isArray(fechaInicio) && Array.isArray(horaInicio)) {
        // Формат массива: [2026, 4, 5] + [18, 0]
        start = new Date(fechaInicio[0], fechaInicio[1] - 1, fechaInicio[2],
            horaInicio[0], horaInicio[1], 0);
    } else if (typeof fechaInicio === 'string') {
        // Формат строки: "2026-04-04 18:00:00" или "2026-04-04T18:00:00"
        // Если hora отдельно — добавляем
        if (typeof horaInicio === 'string') {
            start = new Date((fechaInicio + ' ' + horaInicio).replace(' ', 'T'));
        } else {
            start = new Date(fechaInicio.replace(' ', 'T'));
        }
    }

    if (!start || isNaN(start.getTime())) return false;

    const now = new Date();
    console.log(`[isTournamentStarted] ${tournament.nombre}: start=${start}, now=${now}, started=${now > start}`);
    return now > start;
}

class PadelCoreHome {
    constructor() {
        this.tournaments = [];
        this.filteredTournaments = [];
        this.itemsPerPage = 9;
        this.currentPage = 1;

        this.displayMaps = {
            nivel: {
                'PRINCIPIANTES': 'Principiante',
                'C9': 'C9 (Principiante)',
                'C8': 'C8 (Intermedio)',
                'C7': 'C7 (Avanzado)',
                'C6': 'C6 (Profesional)',
                'C5': 'C5 (Élite)',
                'C4': 'C4',
                'D8': 'D8',
                'D7': 'D7',
                'D6': 'D6',
                'SUMA_15': 'Suma 15+',
                'SUMA_14': 'Suma 14+',
                'SUMA_13': 'Suma 13+'
            },
            tipo: {
                'KING_OF_COURT': 'King of Court',
                'AMERICANA': 'Americana'
            },
            genero: {
                'MASCULINO': 'Masculino',
                'FEMENINO': 'Femenino',
                'MIXTO': 'Mixto'
            },
            estado: {
                'REGISTRO_ABIERTO': 'Inscripción abierta',
                'PUBLICADO': 'Próximamente',
                'BORRADOR': 'Borrador',
                'CERRADO': 'Cerrado',
                'FINALIZADO': 'Finalizado',
                'CANCELADO': 'Cancelado'
            },
            modalidad: {
                'INDIVIDUAL': 'Individual',
                'DOBLES': 'Dobles'
            }
        };

        this.init();
    }

    init() {
        if (typeof tournamentData !== 'undefined') {
            this.tournaments = tournamentData;
            this.filteredTournaments = [...this.tournaments];
            console.log('🏆 Torneos cargados:', this.tournaments.length);
        }

        this.initElements();
        this.initEventListeners();
        this.renderTournaments();
        this.initFaqAccordion();
    }

    initElements() {
        this.tournamentsGrid = document.getElementById('tournamentsGrid');
        this.filtersToggle = document.getElementById('filtersToggle');
        this.filtersForm = document.getElementById('filterForm');
        this.applyFilters = document.getElementById('applyFilters');
        this.clearFilters = document.getElementById('clearFilters');
        this.clearFiltersEmpty = document.getElementById('clearFiltersEmpty');
        this.generoFilter = document.getElementById('generoFilter');
        this.nivelFilter = document.getElementById('nivelFilter');
        this.tipoFilter = document.getElementById('tipoFilter');
        this.noTournamentsMessage = document.getElementById('noTournamentsMessage');
        this.navbarToggler = document.getElementById('navbarToggler');
        this.navbarNav = document.getElementById('navbarNav');
        this.loadMoreBtn = document.getElementById('loadMoreBtn');
        this.tournamentsMore = document.getElementById('tournamentsMore');
        this.modalidadFilter = document.getElementById('modalidadFilter');
    }

    initEventListeners() {
        console.log('initEventListeners started');

        if (this.filtersToggle) {
            this.filtersToggle.addEventListener('click', () => this.toggleFilters());
        }
        if (this.applyFilters) {
            this.applyFilters.addEventListener('click', () => this.applyFiltersFunction());
        }
        if (this.clearFilters) {
            this.clearFilters.addEventListener('click', () => this.clearFiltersFunction());
        }
        if (this.clearFiltersEmpty) {
            this.clearFiltersEmpty.addEventListener('click', () => this.clearFiltersFunction());
        }
        if (this.loadMoreBtn) {
            this.loadMoreBtn.addEventListener('click', () => this.loadMore());
        }

        const toggler = document.getElementById('navbarToggler');
        const nav = document.getElementById('navbarNav');

        if (toggler && nav) {
            console.log('✅ Нашли бургер и меню');
            const newToggler = toggler.cloneNode(true);
            toggler.parentNode.replaceChild(newToggler, toggler);
            this.navbarToggler = newToggler;
            this.navbarNav = document.getElementById('navbarNav');

            this.navbarToggler.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();
                this.navbarNav.classList.toggle('show');
                console.log('🍔 Меню:', this.navbarNav.classList.contains('show') ? 'открыто' : 'закрыто');
            };
            console.log('✅ Новый обработчик добавлен');
        } else {
            console.log('❌ Не найдены элементы меню');
        }
    }

    toggleFilters() {
        if (this.filtersForm) {
            this.filtersForm.classList.toggle('collapsed');
            const icon = this.filtersToggle.querySelector('i');
            const span = this.filtersToggle.querySelector('span');
            if (this.filtersForm.classList.contains('collapsed')) {
                icon.classList.remove('fa-chevron-up');
                icon.classList.add('fa-chevron-down');
                if (span) span.textContent = 'Mostrar filtros';
            } else {
                icon.classList.remove('fa-chevron-down');
                icon.classList.add('fa-chevron-up');
                if (span) span.textContent = 'Ocultar filtros';
            }
        }
    }

    applyFiltersFunction() {
        const genero = this.generoFilter?.value || 'todos';
        const nivel = this.nivelFilter?.value || 'todos';
        const tipo = this.tipoFilter?.value || 'todos';
        const modalidad = this.modalidadFilter?.value || 'todos';

        this.filteredTournaments = this.tournaments.filter(t => {
            if (genero !== 'todos' && t.generoFormato !== genero) return false;

            if (nivel !== 'todos') {
                const nivelMap = {
                    'SUMA_15': 'SUMA_15', 'SUMA_13': 'SUMA_13',
                    'D7': 'D7', 'D8': 'D8', 'D7_D8': 'D7_D8',
                    'D6': 'D6', 'C7_C6': 'C7_C6',
                    'C9': 'C9', 'C8': 'C8', 'C7': 'C7',
                    'C6': 'C6', 'C5': 'C5', 'C4': 'C4',
                    'Principiante': 'PRINCIPIANTES'
                };
                if (t.categoriaNivel !== nivelMap[nivel]) return false;
            }

            if (tipo !== 'todos' && t.tipo !== tipo) return false;
            if (modalidad !== 'todos' && t.modalidad && t.modalidad !== modalidad) return false;
            return true;
        });

        this.currentPage = 1;
        this.renderTournaments();
        this.toggleLoadMoreButton();
    }

    clearFiltersFunction() {
        if (this.generoFilter) this.generoFilter.value = 'todos';
        if (this.nivelFilter) this.nivelFilter.value = 'todos';
        if (this.tipoFilter) this.tipoFilter.value = 'todos';
        if (this.modalidadFilter) this.modalidadFilter.value = 'todos';
        this.filteredTournaments = [...this.tournaments];
        this.currentPage = 1;
        this.renderTournaments();
        this.toggleLoadMoreButton();
    }

    renderTournaments() {
        if (!this.tournamentsGrid) return;
        this.tournamentsGrid.innerHTML = '';

        if (this.filteredTournaments.length === 0) {
            if (this.noTournamentsMessage) this.noTournamentsMessage.style.display = 'block';
            if (this.tournamentsMore) this.tournamentsMore.style.display = 'none';
            return;
        }

        if (this.noTournamentsMessage) this.noTournamentsMessage.style.display = 'none';
        console.log('Рендерим турниры:', this.filteredTournaments.length);

        const tournamentsToShow = this.filteredTournaments.slice(0, this.filteredTournaments.length);
        tournamentsToShow.forEach(tournament => {
            const card = this.createTournamentCard(tournament);
            this.tournamentsGrid.appendChild(card);
        });
    }

    createTournamentCard(tournament) {
        const card = document.createElement('div');
        card.className = 'tournament-card';
        card.dataset.tournamentId = tournament.id;

        const fecha = Array.isArray(tournament.fechaInicio)
            ? `${tournament.fechaInicio[2]}/${tournament.fechaInicio[1]}/${tournament.fechaInicio[0]}`
            : tournament.fechaInicio || 'Fecha por definir';

        const hora = Array.isArray(tournament.horaInicio)
            ? `${tournament.horaInicio[0]}:${tournament.horaInicio[1].toString().padStart(2, '0')}`
            : tournament.horaInicio || 'Hora por definir';

        const generoDisplay = this.displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || 'N/A';
        const nivelDisplay  = tournament.categoriaNivel || 'N/A';
        const tipoDisplay   = this.displayMaps.tipo[tournament.tipo] || tournament.tipo || 'N/A';
        const estadoDisplay = this.displayMaps.estado[tournament.estado] || tournament.estado || '';
        const estadoClass   = tournament.estado ? `status-${tournament.estado.toLowerCase()}` : '';
        const isAuthenticated = typeof window.isAuthenticated !== 'undefined' ? window.isAuthenticated : false;

        // ── НОВАЯ ПРОВЕРКА: начался ли турнир ──────────────────────
        const started = isTournamentStarted(tournament);

        const clubAddress = tournament.clubDireccion
            ? `<span class="club-address">${this.escapeHtml(tournament.clubDireccion)}</span>`
            : '';

        card.innerHTML = `
        <div class="tournament-card-header">
            <span class="tournament-badge">${generoDisplay}</span>
            <span class="tournament-badge tournament-badge-level">${nivelDisplay}</span>
        </div>
        <div class="tournament-card-body">
            <h3 class="tournament-title">${this.escapeHtml(tournament.nombre || '')}</h3>
            <div class="tournament-info">
                <div class="tournament-info-item">
                    <i class="fas fa-calendar-alt"></i>
                    <span>${fecha} ${hora}</span>
                </div>
                <div class="tournament-info-item">
                    <i class="fas fa-map-marker-alt"></i>
                    <div class="club-info">
                        <span class="club-name">${this.escapeHtml(tournament.clubNombre || 'Club por definir')}</span>
                        ${clubAddress}
                    </div>
                </div>
                <div class="tournament-info-item">
                    <i class="fas fa-trophy"></i>
                    <span>${tipoDisplay}</span>
                </div>
                <div class="tournament-info-item">
                    <i class="fas fa-users"></i>
                    <span>${tournament.cupoMax || 0} ${tournament.tipo === 'KING_OF_COURT' ? 'jugadores' : 'cupos'}</span>
                </div>
                <div class="tournament-info-item">
                    <i class="fas fa-tag"></i>
                    <span>${tournament.precio || 0} ${tournament.moneda || ''}</span>
                </div>
            </div>
            <div class="tournament-footer">
                <a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
                    <i class="fas fa-info-circle"></i> Ver detalles
                </a>
                ${started
            ? `<span class="tournament-status" style="color:#6c757d; font-size:.8rem;">
                           <i class="fas fa-lock"></i> Iniciado
                       </span>`
            : `<span class="tournament-status ${estadoClass}">${estadoDisplay}</span>`
        }
            </div>
        </div>
    `;

        return card;
    }

    toggleLoadMoreButton() {
        if (!this.tournamentsMore) return;
        const hasMore = this.filteredTournaments.length > this.currentPage * this.itemsPerPage;
        this.tournamentsMore.style.display = hasMore ? 'block' : 'none';
    }

    loadMore() {
        this.currentPage++;
        this.renderTournaments();
    }

    initFaqAccordion() {
        const faqItems = document.querySelectorAll('.faq-item');
        faqItems.forEach(item => {
            const question = item.querySelector('.faq-question');
            question.addEventListener('click', () => {
                faqItems.forEach(otherItem => {
                    if (otherItem !== item && otherItem.classList.contains('active')) {
                        otherItem.classList.remove('active');
                    }
                });
                item.classList.toggle('active');
            });
        });
        console.log('✅ FAQ Accordion inicializado');
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    window.padelCore = new PadelCoreHome();
});