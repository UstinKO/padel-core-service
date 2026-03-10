/**
 * Padel Core - Home Page JavaScript
 */

class PadelCoreHome {
    constructor() {
        this.tournaments = [];
        this.filteredTournaments = [];

        // Для пагинации (если нужно будет добавить "Показать еще")
        this.itemsPerPage = 9; // Показывать по 9 турниров за раз
        this.currentPage = 1;

        // Маппинги для отображения значений
        this.displayMaps = {
            nivel: {
                'C9': 'C9 (Principiante)',
                'C8': 'C8 (Intermedio)',
                'C7': 'C7 (Avanzado)',
                'C6': 'C6 (Profesional)',
                'C5': 'C5 (Élite)',
                'PRINCIPIANTES': 'Principiante'
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
        this.initEventListeners();
        this.renderTournaments();

        // Инициализируем аккордеон для FAQ
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
        this.navbarToggler = document.getElementById('navbarToggler'); // Должно быть!
        this.navbarNav = document.getElementById('navbarNav'); // Должно быть!
        this.loadMoreBtn = document.getElementById('loadMoreBtn');
        this.tournamentsMore = document.getElementById('tournamentsMore');
    }

    initEventListeners() {
        console.log('initEventListeners started');

        // Обработчики для фильтров
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

        // ПРОСТЕЙШИЙ обработчик для бургер-меню
        const toggler = document.getElementById('navbarToggler');
        const nav = document.getElementById('navbarNav');

        if (toggler && nav) {
            console.log('✅ Нашли бургер и меню');

            // Убираем ВСЕ старые обработчики через замену элемента
            const newToggler = toggler.cloneNode(true);
            toggler.parentNode.replaceChild(newToggler, toggler);

            // Обновляем ссылки
            this.navbarToggler = newToggler;
            this.navbarNav = document.getElementById('navbarNav');

            // Добавляем один простой обработчик
            this.navbarToggler.onclick = (e) => {
                e.preventDefault();
                e.stopPropagation();

                // Переключаем класс
                this.navbarNav.classList.toggle('show');

                // Логируем для отладки
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

        this.filteredTournaments = this.tournaments.filter(t => {
            if (genero !== 'todos' && t.generoFormato !== genero) return false;

            // Фильтрация для уровня
            if (nivel !== 'todos') {
                const nivelMap = {
                    'C9': 'C9',
                    'C8': 'C8',
                    'C7': 'C7',
                    'C6': 'C6',
                    'C5': 'C5',
                    'Principiante': 'PRINCIPIANTES'
                };
                const dbValue = nivelMap[nivel];
                if (t.categoriaNivel !== dbValue) return false;
            }

            if (tipo !== 'todos' && t.tipo !== tipo) return false;
            return true;
        });

        // Сбрасываем пагинацию
        this.currentPage = 1;
        this.renderTournaments();

        // Показываем/скрываем кнопку "Показать больше"
        this.toggleLoadMoreButton();
    }

    clearFiltersFunction() {
        if (this.generoFilter) this.generoFilter.value = 'todos';
        if (this.nivelFilter) this.nivelFilter.value = 'todos';
        if (this.tipoFilter) this.tipoFilter.value = 'todos';

        this.filteredTournaments = [...this.tournaments];

        this.currentPage = 1;
        this.renderTournaments();
        this.toggleLoadMoreButton();
    }

    renderTournaments() {
        if (!this.tournamentsGrid) return;

        // Очищаем сетку
        this.tournamentsGrid.innerHTML = '';

        if (this.filteredTournaments.length === 0) {
            // Показываем сообщение "нет турниров"
            if (this.noTournamentsMessage) {
                this.noTournamentsMessage.style.display = 'block';
            }
            if (this.tournamentsMore) {
                this.tournamentsMore.style.display = 'none';
            }
            return;
        }

        // Скрываем сообщение "нет турниров"
        if (this.noTournamentsMessage) {
            this.noTournamentsMessage.style.display = 'none';
        }

        console.log('Рендерим турниры:', this.filteredTournaments.length);

        // Определяем, сколько турниров показывать
        const start = 0;
        const end = this.filteredTournaments.length; // Показываем все сразу
        // Если хотите пагинацию, раскомментируйте следующую строку и закомментируйте верхнюю
        // const end = Math.min(this.currentPage * this.itemsPerPage, this.filteredTournaments.length);

        const tournamentsToShow = this.filteredTournaments.slice(start, end);

        // Создаем карточки для каждого турнира
        tournamentsToShow.forEach(tournament => {
            const card = this.createTournamentCard(tournament);
            this.tournamentsGrid.appendChild(card);
        });

        // Показываем кнопку "Показать больше" если есть еще турниры
        // this.toggleLoadMoreButton();
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

        // Упрощенное отображение уровня - только C9, C8 и т.д.
        const generoDisplay = this.displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || 'N/A';
        const nivelDisplay = tournament.categoriaNivel || 'N/A'; // Только значение без описания
        const tipoDisplay = this.displayMaps.tipo[tournament.tipo] || tournament.tipo || 'N/A';
        const estadoDisplay = this.displayMaps.estado[tournament.estado] || tournament.estado || '';
        const estadoClass = tournament.estado ? `status-${tournament.estado.toLowerCase()}` : '';
        const isAuthenticated = typeof window.isAuthenticated !== 'undefined' ? window.isAuthenticated : false;

        // Формируем адрес клуба, если он есть в данных
        const clubAddress = tournament.clubDireccion ?
            `<span class="club-address">${this.escapeHtml(tournament.clubDireccion)}</span>` :
            '';

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
                    <span>${tournament.cupoMax || 0} ${tournament.tipo === 'KING_OF_COURT' ? 'jugadores' : 'parejas'}</span>
                </div>
                <div class="tournament-info-item">
                    <i class="fas fa-tag"></i>
                    <span>${tournament.precio || 0} ${tournament.moneda || ''}</span>
                </div>
            </div>
            <div class="tournament-footer">
                ${isAuthenticated ?
            `<a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
                        <i class="fas fa-info-circle"></i> Ver detalles
                    </a>` :
            `<a href="/login" class="btn btn-outline btn-small">
                        <i class="fas fa-sign-in-alt"></i> Inicia sesión
                    </a>`
        }
                <span class="tournament-status ${estadoClass}">${estadoDisplay}</span>
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
                // Закрываем другие открытые вопросы (опционально)
                // Если хотите, чтобы одновременно был открыт только один вопрос
                faqItems.forEach(otherItem => {
                    if (otherItem !== item && otherItem.classList.contains('active')) {
                        otherItem.classList.remove('active');
                    }
                });

                // Открываем/закрываем текущий вопрос
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

// Инициализация
document.addEventListener('DOMContentLoaded', () => {
    window.padelCore = new PadelCoreHome();
});