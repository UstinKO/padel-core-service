/**
 * Padel Core - Torneos Page JavaScript
 */

class TorneosPage {
    constructor() {
        this.tournaments = [];
        this.filteredTournaments = [];
        this.currentPage = 1;
        this.itemsPerPage = 9;
        this.totalPages = 1;

        // Маппинг для отображения значений enum в читаемые тексты
        this.nivelDisplayMap = {
            'C9': t('enum.nivel.c9'),
            'C8': t('enum.nivel.c8'),
            'C7': t('enum.nivel.c7'),
            'C6': t('enum.nivel.c6'),
            'C5': t('enum.nivel.c5')
        };

        this.tipoDisplayMap = {
            'KING_OF_COURT':    t('enum.tipo.koc'),
            'AMERICANO':        t('enum.tipo.americana'),
            'AMERICANO_TEAMS':  t('enum.tipo.americano_teams'),
            'CANCHA_ABIERTA':   t('enum.tipo.cancha_abierta')
        };

        this.estadoDisplayMap = {
            'REGISTRO_ABIERTO': t('enum.estado.open'),
            'PUBLICADO': t('enum.estado.published'),
            'BORRADOR': t('enum.estado.draft'),
            'CERRADO': t('enum.estado.closed'),
            'FINALIZADO': t('enum.estado.finished'),
            'CANCELADO': t('enum.estado.cancelled')
        };

        this.generoDisplayMap = {
            'MASCULINO': t('enum.genero.m'),
            'FEMENINO': t('enum.genero.f'),
            'MIXTO': t('enum.genero.mix')
        };

        this.init();
    }

    init() {
        // Получаем данные турниров из модели
        if (typeof tournamentData !== 'undefined') {
            this.tournaments = tournamentData;
            this.filteredTournaments = [...this.tournaments];
            this.totalPages = Math.ceil(this.filteredTournaments.length / this.itemsPerPage);

            console.log('🏆 Torneos cargados:', this.tournaments.length);
            console.log('📊 Valores únicos de nivel:', [...new Set(this.tournaments.map(t => t.categoriaNivel))]);
        }

        this.initElements();
        this.initEventListeners();
        this.updateCounters();
        this.renderTournaments();
        this.renderPagination();
    }

    initElements() {
        this.filtersToggle = document.getElementById('filtersToggle');
        this.filtersForm = document.getElementById('filterForm');
        this.applyFilters = document.getElementById('applyFilters');
        this.clearFilters = document.getElementById('clearFilters');
        this.clearFiltersEmpty = document.getElementById('clearFiltersEmpty');
        this.generoFilter = document.getElementById('generoFilter');
        this.nivelFilter = document.getElementById('nivelFilter');
        this.tipoFilter = document.getElementById('tipoFilter');
        this.estadoFilter = document.getElementById('estadoFilter');
        this.visibleCount = document.getElementById('visibleCount');
        this.totalCount = document.getElementById('totalCount');
        this.torneosGrid = document.getElementById('torneosGrid');
        this.noTournamentsMessage = document.getElementById('noTournamentsMessage');
        this.pagination = document.getElementById('pagination');
        this.paginationPrev = document.getElementById('paginationPrev');
        this.paginationNext = document.getElementById('paginationNext');
        this.paginationPages = document.getElementById('paginationPages');
        this.navbarToggler = document.getElementById('navbarToggler');
        this.navbarNav = document.getElementById('navbarNav');
    }

    initEventListeners() {
        // Фильтры
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

        // Пагинация
        if (this.paginationPrev) {
            this.paginationPrev.addEventListener('click', () => this.prevPage());
        }
        if (this.paginationNext) {
            this.paginationNext.addEventListener('click', () => this.nextPage());
        }

        // Навбар для мобильных
        if (this.navbarToggler && this.navbarNav) {
            this.navbarToggler.addEventListener('click', () => {
                this.navbarNav.classList.toggle('show');
            });
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
                if (span) span.textContent = t('filter.toggle.show');
            } else {
                icon.classList.remove('fa-chevron-down');
                icon.classList.add('fa-chevron-up');
                if (span) span.textContent = t('filter.toggle.hide');
            }
        }
    }

    applyFiltersFunction() {
        const genero = this.generoFilter?.value || 'todos';
        const nivel = this.nivelFilter?.value || 'todos';
        const tipo = this.tipoFilter?.value || 'todos';
        const estado = this.estadoFilter?.value || 'todos';

        console.log('🔍 Aplicando filtros:', { genero, nivel, tipo, estado });

        this.filteredTournaments = this.tournaments.filter(t => {
            // Получаем значения из турнира
            const tGenero = t.generoFormato || '';
            const tNivel = t.categoriaNivel || '';
            const tTipo = t.tipo || '';
            const tEstado = t.estado || '';

            let matchesGenero = true;
            let matchesNivel = true;
            let matchesTipo = true;
            let matchesEstado = true;

            // Фильтр по полу - сравниваем напрямую с enum значениями
            if (genero !== 'todos') {
                matchesGenero = tGenero === genero;
            }

            // Фильтр по уровню - сравниваем напрямую с enum значениями (C9, C8, C7, C6, C5)
            if (nivel !== 'todos') {
                matchesNivel = tNivel === nivel;
            }

            // Фильтр по типу
            if (tipo !== 'todos') {
                matchesTipo = tTipo === tipo;
            }

            // Фильтр по статусу
            if (estado !== 'todos') {
                matchesEstado = tEstado === estado;
            }

            return matchesGenero && matchesNivel && matchesTipo && matchesEstado;
        });

        console.log('✅ Найдено турниров:', this.filteredTournaments.length);

        // Логируем распределение по уровням для отладки
        const nivelCounts = {};
        this.filteredTournaments.forEach(t => {
            const nivel = t.categoriaNivel || 'undefined';
            nivelCounts[nivel] = (nivelCounts[nivel] || 0) + 1;
        });
        console.log('📊 Распределение по уровням:', nivelCounts);

        this.totalPages = Math.ceil(this.filteredTournaments.length / this.itemsPerPage);
        this.currentPage = 1;
        this.updateCounters();
        this.renderTournaments();
        this.renderPagination();

        if (this.noTournamentsMessage) {
            this.noTournamentsMessage.style.display = this.filteredTournaments.length === 0 ? 'block' : 'none';
        }
    }

    clearFiltersFunction() {
        if (this.generoFilter) this.generoFilter.value = 'todos';
        if (this.nivelFilter) this.nivelFilter.value = 'todos';
        if (this.tipoFilter) this.tipoFilter.value = 'todos';
        if (this.estadoFilter) this.estadoFilter.value = 'todos';

        this.filteredTournaments = [...this.tournaments];
        this.totalPages = Math.ceil(this.filteredTournaments.length / this.itemsPerPage);
        this.currentPage = 1;
        this.updateCounters();
        this.renderTournaments();
        this.renderPagination();

        if (this.noTournamentsMessage) {
            this.noTournamentsMessage.style.display = 'none';
        }
    }

    updateCounters() {
        if (this.visibleCount && this.totalCount) {
            const start = (this.currentPage - 1) * this.itemsPerPage + 1;
            const end = Math.min(this.currentPage * this.itemsPerPage, this.filteredTournaments.length);

            if (this.filteredTournaments.length > 0) {
                this.visibleCount.textContent = `${start}-${end}`;
            } else {
                this.visibleCount.textContent = '0';
            }
            this.totalCount.textContent = this.filteredTournaments.length;
        }
    }

    renderTournaments() {
        if (!this.torneosGrid) return;

        const start = (this.currentPage - 1) * this.itemsPerPage;
        const end = start + this.itemsPerPage;
        const pageTournaments = this.filteredTournaments.slice(start, end);

        if (pageTournaments.length === 0) {
            this.torneosGrid.innerHTML = '';
            return;
        }

        this.torneosGrid.innerHTML = pageTournaments.map(tournament => this.renderTournamentCard(tournament)).join('');
    }

    renderTournamentCard(tournament) {
        const fecha = Array.isArray(tournament.fechaInicio)
            ? `${tournament.fechaInicio[2]}/${tournament.fechaInicio[1]}/${tournament.fechaInicio[0]}`
            : tournament.fechaInicio || t('card.date.unknown');

        const hora = Array.isArray(tournament.horaInicio)
            ? `${tournament.horaInicio[0]}:${tournament.horaInicio[1].toString().padStart(2, '0')}`
            : tournament.horaInicio || t('card.time.unknown');

        const estadoTexto = this.getEstadoTexto(tournament.estado);
        const estadoClass = this.getEstadoClass(tournament.estado);

        // Получаем отображаемые тексты
        const nivelDisplay = this.nivelDisplayMap[tournament.categoriaNivel] || tournament.categoriaNivel || 'N/A';
        const tipoDisplay = this.getTipoTexto(tournament.tipo);
        const generoDisplay = this.generoDisplayMap[tournament.generoFormato] || tournament.generoFormato || 'N/A';

        const isAuthenticated = typeof window.isAuthenticated !== 'undefined' ? window.isAuthenticated : false;

        return `
            <div class="torneo-card">
                <div class="torneo-card-header">
                    <span class="torneo-badge">${generoDisplay}</span>
                    <span class="torneo-badge torneo-badge-level">${nivelDisplay}</span>
                </div>
                <div class="torneo-card-body">
                    <h3 class="torneo-title">${this.escapeHtml(tournament.nombre || '')}</h3>
                    <div class="torneo-info">
                        <div class="torneo-info-item">
                            <i class="fas fa-calendar-alt"></i>
                            <span>${fecha} ${hora}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-map-marker-alt"></i>
                            <span>${this.escapeHtml(tournament.clubNombre || t('card.club.unknown'))}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-trophy"></i>
                            <span>${tipoDisplay}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-tag"></i>
                            <span>${tournament.precio || '0'} ${tournament.moneda || ''}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-user-check"></i>
                            <span>Inscritos: ${tournament.inscritosActuales || 0}/${tournament.cupoMax || 0}</span>
                        </div>
                    </div>
                    <div class="torneo-footer">
                        <a href="/torneo/${tournament.id}" class="btn btn-primary btn-small">
                            <i class="fas fa-info-circle"></i> ${t('card.btn.details')}
                        </a>
                        <span class="torneo-status ${estadoClass}">${estadoTexto}</span>
                    </div>
                </div>
            </div>
        `;
    }

    getTipoTexto(tipo) {
        return this.tipoDisplayMap[tipo] || tipo;
    }

    getEstadoTexto(estado) {
        return this.estadoDisplayMap[estado] || estado;
    }

    getEstadoClass(estado) {
        const classes = {
            'REGISTRO_ABIERTO': 'status-registro_abierto',
            'PUBLICADO': 'status-publicado',
            'BORRADOR': 'status-borrador',
            'CERRADO': 'status-cerrado',
            'FINALIZADO': 'status-finalizado',
            'CANCELADO': 'status-cancelado'
        };
        return classes[estado] || '';
    }

    renderPagination() {
        if (!this.pagination || !this.paginationPages) return;

        if (this.totalPages <= 1) {
            this.pagination.style.display = 'none';
            return;
        }

        this.pagination.style.display = 'flex';

        // Actualizar botones prev/next
        if (this.paginationPrev) {
            this.paginationPrev.disabled = this.currentPage === 1;
        }
        if (this.paginationNext) {
            this.paginationNext.disabled = this.currentPage === this.totalPages;
        }

        // Renderizar números de página
        let pagesHtml = '';
        const maxVisiblePages = 5;
        let startPage = Math.max(1, this.currentPage - Math.floor(maxVisiblePages / 2));
        let endPage = Math.min(this.totalPages, startPage + maxVisiblePages - 1);

        if (endPage - startPage + 1 < maxVisiblePages) {
            startPage = Math.max(1, endPage - maxVisiblePages + 1);
        }

        if (startPage > 1) {
            pagesHtml += `<button class="pagination-page" data-page="1">1</button>`;
            if (startPage > 2) {
                pagesHtml += `<span class="pagination-ellipsis">...</span>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            pagesHtml += `<button class="pagination-page ${i === this.currentPage ? 'active' : ''}" data-page="${i}">${i}</button>`;
        }

        if (endPage < this.totalPages) {
            if (endPage < this.totalPages - 1) {
                pagesHtml += `<span class="pagination-ellipsis">...</span>`;
            }
            pagesHtml += `<button class="pagination-page" data-page="${this.totalPages}">${this.totalPages}</button>`;
        }

        this.paginationPages.innerHTML = pagesHtml;

        // Добавить обработчики для кнопок страниц
        this.paginationPages.querySelectorAll('.pagination-page').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const page = parseInt(e.target.dataset.page);
                this.goToPage(page);
            });
        });
    }

    goToPage(page) {
        if (page < 1 || page > this.totalPages || page === this.currentPage) return;

        this.currentPage = page;
        this.updateCounters();
        this.renderTournaments();
        this.renderPagination();

        // Прокрутить к началу сетки
        this.torneosGrid.scrollIntoView({ behavior: 'smooth' });
    }

    prevPage() {
        this.goToPage(this.currentPage - 1);
    }

    nextPage() {
        this.goToPage(this.currentPage + 1);
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    window.torneosPage = new TorneosPage();
});