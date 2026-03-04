/**
 * Padel Core - Home Page JavaScript
 */

class PadelCoreHome {
    constructor() {
        this.tournaments = [];
        this.filteredTournaments = [];
        this.currentSlide = 0;
        this.slidesPerView = this.getSlidesPerView();
        this.maxSlideIndex = 0;

        // Маппинги для отображения значений
        this.displayMaps = {
            nivel: {
                'C9': 'C9 (Principiante)',
                'C8': 'C8 (Intermedio)',
                'C7': 'C7 (Avanzado)',
                'C6': 'C6 (Profesional)',
                'C5': 'C5 (Élite)'
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
        this.updateContainerVisibility();

        if (this.filtersForm) {
            this.filtersForm.classList.remove('collapsed');
        }
        if (this.filtersToggle) {
            const icon = this.filtersToggle.querySelector('i');
            const span = this.filtersToggle.querySelector('span');
            if (icon) {
                icon.classList.remove('fa-chevron-down');
                icon.classList.add('fa-chevron-up');
            }
            if (span) {
                span.textContent = 'Ocultar filtros';
            }
        }

        this.initEventListeners();
        this.renderTournaments();
        this.createIndicators();
    }

    initElements() {
        this.carouselTrack = document.getElementById('carouselTrack');
        this.carouselPrev = document.getElementById('carouselPrev');
        this.carouselNext = document.getElementById('carouselNext');
        this.filtersToggle = document.getElementById('filtersToggle');
        this.filtersForm = document.getElementById('filterForm');
        this.applyFilters = document.getElementById('applyFilters');
        this.clearFilters = document.getElementById('clearFilters');
        this.clearFiltersEmpty = document.getElementById('clearFiltersEmpty');
        this.generoFilter = document.getElementById('generoFilter');
        this.nivelFilter = document.getElementById('nivelFilter');
        this.tipoFilter = document.getElementById('tipoFilter');
        this.noTournamentsMessage = document.getElementById('noTournamentsMessage');
        this.carouselIndicators = document.getElementById('carouselIndicators');
        this.navbarToggler = document.getElementById('navbarToggler');
        this.navbarNav = document.getElementById('navbarNav');
        this.carouselContainer = document.getElementById('carouselContainer');
    }

    initEventListeners() {
        if (this.carouselPrev) {
            this.carouselPrev.addEventListener('click', () => this.prevSlide());
        }
        if (this.carouselNext) {
            this.carouselNext.addEventListener('click', () => this.nextSlide());
        }

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

        window.addEventListener('resize', () => {
            const oldSlidesPerView = this.slidesPerView;
            this.slidesPerView = this.getSlidesPerView();
            if (oldSlidesPerView !== this.slidesPerView) {
                this.renderTournaments();
            }
        });

        if (this.navbarToggler) {
            this.navbarToggler.addEventListener('click', () => {
                this.navbarNav.classList.toggle('show');
            });
        }
    }

    updateContainerVisibility() {
        if (this.carouselContainer) {
            this.carouselContainer.style.display = this.filteredTournaments.length > 0 ? 'block' : 'none';
        }
    }

    getSlidesPerView() {
        if (window.innerWidth <= 768) return 1;
        if (window.innerWidth <= 1024) return 2;
        return 3;
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

        this.filteredTournaments = this.tournaments.filter(t => {
            if (genero !== 'todos' && t.generoFormato !== genero) return false;
            if (nivel !== 'todos' && t.categoriaNivel !== nivel) return false;
            if (tipo !== 'todos' && t.tipo !== tipo) return false;
            return true;
        });

        this.currentSlide = 0;
        this.renderTournaments();
        this.updateContainerVisibility();

        if (this.noTournamentsMessage) {
            this.noTournamentsMessage.style.display = this.filteredTournaments.length === 0 ? 'block' : 'none';
        }
    }

    clearFiltersFunction() {
        if (this.generoFilter) this.generoFilter.value = 'todos';
        if (this.nivelFilter) this.nivelFilter.value = 'todos';
        if (this.tipoFilter) this.tipoFilter.value = 'todos';

        this.filteredTournaments = [...this.tournaments];

        this.currentSlide = 0;
        this.renderTournaments();
        this.updateContainerVisibility();

        if (this.noTournamentsMessage) {
            this.noTournamentsMessage.style.display = 'none';
        }
    }

    renderTournaments() {
        if (!this.carouselTrack) return;
        this.carouselTrack.innerHTML = '';
        if (this.filteredTournaments.length === 0) return;

        console.log('Рендерим турниры:', this.filteredTournaments.length);
        console.log('Карточек на слайд:', this.slidesPerView);

        // Группируем карточки по слайдам
        for (let i = 0; i < this.filteredTournaments.length; i += this.slidesPerView) {
            const slide = document.createElement('li');
            slide.className = 'carousel-slide';
            const group = this.filteredTournaments.slice(i, i + this.slidesPerView);
            console.log(`Слайд ${i/this.slidesPerView + 1}: ${group.length} карточек`);
            group.forEach(tournament => {
                const card = this.createTournamentCard(tournament);
                slide.appendChild(card);
            });
            this.carouselTrack.appendChild(slide);
        }

        this.calculateMaxSlide();
        this.updateCarousel();
        this.createIndicators();
    }

    createTournamentCard(tournament) {
        const card = document.createElement('div');
        card.className = 'carousel-card';
        card.dataset.tournamentId = tournament.id;

        const fecha = Array.isArray(tournament.fechaInicio)
            ? `${tournament.fechaInicio[2]}/${tournament.fechaInicio[1]}/${tournament.fechaInicio[0]}`
            : tournament.fechaInicio || 'Fecha por definir';

        const hora = Array.isArray(tournament.horaInicio)
            ? `${tournament.horaInicio[0]}:${tournament.horaInicio[1].toString().padStart(2, '0')}`
            : tournament.horaInicio || 'Hora por definir';

        const generoDisplay = this.displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || 'N/A';
        const nivelDisplay = this.displayMaps.nivel[tournament.categoriaNivel] || tournament.categoriaNivel || 'N/A';
        const tipoDisplay = this.displayMaps.tipo[tournament.tipo] || tournament.tipo || 'N/A';
        const estadoDisplay = this.displayMaps.estado[tournament.estado] || tournament.estado || '';
        const estadoClass = tournament.estado ? `status-${tournament.estado.toLowerCase()}` : '';
        const isAuthenticated = typeof window.isAuthenticated !== 'undefined' ? window.isAuthenticated : false;

        card.innerHTML = `
            <div class="carousel-card-header">
                <span class="carousel-badge">${generoDisplay}</span>
                <span class="carousel-badge carousel-badge-level">${nivelDisplay}</span>
            </div>
            <div class="carousel-card-body">
                <h3 class="carousel-title">${this.escapeHtml(tournament.nombre || '')}</h3>
                <div class="carousel-info">
                    <div class="carousel-info-item">
                        <i class="fas fa-calendar-alt"></i>
                        <span>${fecha} ${hora}</span>
                    </div>
                    <div class="carousel-info-item">
                        <i class="fas fa-map-marker-alt"></i>
                        <span>${this.escapeHtml(tournament.clubNombre || 'Club por definir')}</span>
                    </div>
                    <div class="carousel-info-item">
                        <i class="fas fa-trophy"></i>
                        <span>${tipoDisplay}</span>
                    </div>
                    <div class="carousel-info-item">
                        <i class="fas fa-users"></i>
                        <span>${tournament.cupoMax || 0} ${tournament.tipo === 'KING_OF_COURT' ? 'jugadores' : 'parejas'}</span>
                    </div>
                    <div class="carousel-info-item">
                        <i class="fas fa-tag"></i>
                        <span>${tournament.precio || 0} ${tournament.moneda || ''}</span>
                    </div>
                </div>
                <div class="carousel-footer">
                    ${isAuthenticated ?
            `<a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
                            <i class="fas fa-info-circle"></i> Ver detalles
                        </a>` :
            `<a href="/login" class="btn btn-outline btn-small">
                            <i class="fas fa-sign-in-alt"></i> Inicia sesión
                        </a>`
        }
                    <span class="carousel-status ${estadoClass}">${estadoDisplay}</span>
                </div>
            </div>
        `;

        return card;
    }

    createIndicators() {
        if (!this.carouselIndicators) return;
        const totalSlides = this.maxSlideIndex + 1;
        let indicators = '';
        for (let i = 0; i < totalSlides; i++) {
            indicators += `<button class="carousel-dot ${i === this.currentSlide ? 'active' : ''}" data-slide="${i}"></button>`;
        }
        this.carouselIndicators.innerHTML = indicators;

        this.carouselIndicators.querySelectorAll('.carousel-dot').forEach(dot => {
            dot.addEventListener('click', (e) => {
                this.currentSlide = parseInt(e.target.dataset.slide);
                this.updateCarousel();
                this.createIndicators();
            });
        });
    }

    prevSlide() {
        if (this.currentSlide > 0) {
            this.currentSlide--;
            this.updateCarousel();
            this.createIndicators();
        }
    }

    nextSlide() {
        if (this.currentSlide < this.maxSlideIndex) {
            this.currentSlide++;
            this.updateCarousel();
            this.createIndicators();
        }
    }

    updateCarousel() {
        if (!this.carouselTrack) return;

        // Смещаем трек на 100% за каждый слайд
        this.carouselTrack.style.transform = `translateX(-${this.currentSlide * 100}%)`;

        if (this.carouselPrev) {
            this.carouselPrev.disabled = this.currentSlide === 0;
        }
        if (this.carouselNext) {
            this.carouselNext.disabled = this.currentSlide >= this.maxSlideIndex;
        }

        console.log(`Слайд ${this.currentSlide + 1} из ${this.maxSlideIndex + 1}`);
    }

    calculateMaxSlide() {
        this.maxSlideIndex = Math.max(0, Math.ceil(this.filteredTournaments.length / this.slidesPerView) - 1);
        if (this.currentSlide > this.maxSlideIndex) {
            this.currentSlide = this.maxSlideIndex;
        }
        console.log('Всего турниров:', this.filteredTournaments.length);
        console.log('Карточек на слайд:', this.slidesPerView);
        console.log('Индекс последнего слайда:', this.maxSlideIndex);
        console.log('Всего слайдов:', this.maxSlideIndex + 1);
    }

    escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
}

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    window.padelCore = new PadelCoreHome();
});