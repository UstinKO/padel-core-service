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
    return now > start;
}

class PadelCoreHome {
    constructor() {
        this.tournaments = [];
        this.filteredTournaments = [];
        this.eventGroup = 'all';
        this.dateFormat = new Intl.DateTimeFormat(document.documentElement.lang, {
            weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC'
        });

        this.displayMaps = {
            nivel: {
                'PRINCIPIANTES': t('enum.nivel.principiantes'),
                'C9': t('enum.nivel.c9'),
                'C8': t('enum.nivel.c8'),
                'C7': t('enum.nivel.c7'),
                'C6': t('enum.nivel.c6'),
                'C5': t('enum.nivel.c5'),
                'C4': t('enum.nivel.c4'),
                'D9': t('enum.nivel.d9'),
                'D8': t('enum.nivel.d8'),
                'D7': t('enum.nivel.d7'),
                'D6': t('enum.nivel.d6'),
                'SUMA_15': t('enum.nivel.suma15'),
                'SUMA_14': t('enum.nivel.suma14'),
                'SUMA_13': t('enum.nivel.suma13')
            },
            tipo: {
                'KING_OF_COURT':    t('enum.tipo.koc'),
                'AMERICANO':        t('enum.tipo.americana'),
                'AMERICANO_TEAMS':  t('enum.tipo.americano_teams'),
                'CANCHA_ABIERTA':   t('enum.tipo.cancha_abierta'),
                'PADEL_CLINIC':     t('enum.tipo.padel_clinic')
            },
            genero: {
                'MASCULINO': t('enum.genero.m'),
                'FEMENINO': t('enum.genero.f'),
                'MIXTO': t('enum.genero.mix')
            },
            estado: {
                'REGISTRO_ABIERTO': t('enum.estado.open'),
                'PUBLICADO': t('enum.estado.published'),
                'BORRADOR': t('enum.estado.draft'),
                'CERRADO': t('enum.estado.closed'),
                'FINALIZADO': t('enum.estado.finished'),
                'CANCELADO': t('enum.estado.cancelled')
            },
            modalidad: {
                'INDIVIDUAL': t('enum.modalidad.individual'),
                'DOBLES': t('enum.modalidad.doubles')
            }
        };

        this.init();
    }

    init() {
        if (typeof tournamentData !== 'undefined') {
            this.tournaments = tournamentData;
            this.filteredTournaments = [...this.tournaments];
        }

        this.initElements();
        this.populateClubs();
        this.initEventListeners();
        this.applyFiltersFunction();
    }

    initElements() {
        this.tournamentsGrid = document.getElementById('tournamentsGrid');
        this.noTournamentsMessage = document.getElementById('noTournamentsMessage');
        this.clearFilters = document.getElementById('clearFilters');
        this.eventCount = document.getElementById('eventCount');
        this.generoFilter = document.getElementById('generoFilter');
        this.nivelFilter = document.getElementById('nivelFilter');
        this.clubFilter = document.getElementById('clubFilter');
        this.tipoFilter = document.getElementById('tipoFilter');
        this.modalidadFilter = document.getElementById('modalidadFilter');
        this.filters = [this.generoFilter, this.nivelFilter, this.clubFilter, this.tipoFilter, this.modalidadFilter];
        this.groupButtons = document.querySelectorAll('[data-event-group]');
    }

    populateClubs() {
        const clubs = new Map();
        this.tournaments.forEach(({ clubId, clubNombre }) => {
            if (clubId != null && clubNombre) clubs.set(String(clubId), clubNombre);
        });
        [...clubs].sort(([, first], [, second]) =>
            first.localeCompare(second, document.documentElement.lang)
        ).forEach(([id, name]) => this.clubFilter.add(new Option(name, id)));
    }

    initEventListeners() {
        this.filters.forEach(filter => filter.addEventListener('change', () => {
            if (filter === this.tipoFilter) this.selectGroup('all');
            this.applyFiltersFunction();
        }));
        this.groupButtons.forEach(button => button.addEventListener('click', () => {
            this.selectGroup(button.dataset.eventGroup);
            this.tipoFilter.value = 'todos';
            this.applyFiltersFunction();
        }));
        [this.clearFilters, document.getElementById('clearFiltersEmpty')].forEach(button =>
            button.addEventListener('click', () => this.clearFiltersFunction())
        );
        document.getElementById('homeLevelHelp').addEventListener('click', () => {
            document.getElementById('home-level-question').open = true;
        });
        const toggle = document.getElementById('navbarToggler');
        const menu = document.getElementById('navbarNav');
        const mobileMenu = document.getElementById('homeMobileMenu');
        mobileMenu.addEventListener('click', () => {
            toggle.click();
            if (menu.classList.contains('show')) menu.querySelector('a').focus();
        });
        const syncMenu = () => {
            const expanded = String(menu.classList.contains('show'));
            toggle.setAttribute('aria-expanded', expanded);
            mobileMenu.setAttribute('aria-expanded', expanded);
        };
        new MutationObserver(syncMenu).observe(menu, { attributes: true, attributeFilter: ['class'] });
    }

    selectGroup(group) {
        this.eventGroup = group;
        this.groupButtons.forEach(button =>
            button.setAttribute('aria-pressed', String(button.dataset.eventGroup === group))
        );
    }

    applyFiltersFunction() {
        const [gender, level, club, type, modality] = this.filters.map(filter => filter.value);
        const eventGroups = {
            tournaments: ['KING_OF_COURT', 'AMERICANO', 'AMERICANO_TEAMS'],
            training: ['PADEL_CLINIC'],
            open: ['CANCHA_ABIERTA']
        };
        const groupTypes = eventGroups[this.eventGroup];

        this.filteredTournaments = this.tournaments.filter(event =>
            (!groupTypes || groupTypes.includes(event.tipo))
            && (gender === 'todos' || event.generoFormato === gender)
            && (level === 'todos' || event.categoriaNivel === level)
            && (club === 'todos' || String(event.clubId) === club)
            && (type === 'todos' || event.tipo === type)
            && (modality === 'todos' || event.modalidad === modality)
        );
        this.eventCount.textContent = this.filteredTournaments.length;
        this.clearFilters.hidden = this.eventGroup === 'all' && this.filters.every(filter => filter.value === 'todos');
        this.renderTournaments();
    }

    clearFiltersFunction() {
        this.filters.forEach(filter => { filter.value = 'todos'; });
        this.selectGroup('all');
        this.applyFiltersFunction();
        this.groupButtons[0].focus({ preventScroll: true });
    }

    renderTournaments() {
        if (!this.tournamentsGrid) return;
        this.tournamentsGrid.innerHTML = '';

        if (this.filteredTournaments.length === 0) {
            if (this.noTournamentsMessage) this.noTournamentsMessage.style.display = 'block';
            return;
        }

        if (this.noTournamentsMessage) this.noTournamentsMessage.style.display = 'none';
        this.filteredTournaments.forEach(tournament => {
            const card = this.createTournamentCard(tournament);
            this.tournamentsGrid.appendChild(card);
        });
    }

    // Статус заполненности турнира по порогам 70%/100% — не путать с абсолютными
    // порогами getSpotsStatus() в torneos.js (10 пар/15 игроков): разные страницы,
    // разная, осознанно выбранная формула.
    getSpotsStatus(tournament) {
        const cupoMax = tournament.cupoMax || 0;
        const inscritos = tournament.inscritosActuales || 0;
        if (cupoMax <= 0) return 'available';

        const fillRatio = inscritos / cupoMax;
        if (fillRatio >= 1) return 'full';
        if (fillRatio >= 0.7) return 'limited';
        return 'available';
    }

    createTournamentCard(tournament) {
        const card = document.createElement('div');
        card.className = 'tournament-card';
        card.dataset.tournamentId = tournament.id;

        const { fechaInicio, horaInicio } = tournament;
        const dateParts = Array.isArray(fechaInicio) ? fechaInicio : String(fechaInicio).split('-').map(Number);
        const [year, month, day] = dateParts;
        const date = new Date(Date.UTC(year, month - 1, day));
        const fecha = Number.isNaN(date.getTime()) ? t('card.date.unknown') : this.dateFormat.format(date);
        const hora = Array.isArray(horaInicio)
            ? horaInicio.slice(0, 2).map(part => String(part).padStart(2, '0')).join(':')
            : horaInicio?.slice(0, 5) || t('card.time.unknown');

        const generoDisplay = this.escapeHtml(this.displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || '—');
        const nivelDisplay = this.escapeHtml(tournament.categoriaNivel || '—');
        const tipoDisplay = this.escapeHtml(this.displayMaps.tipo[tournament.tipo] || tournament.tipo || '—');
        const estadoDisplay = this.displayMaps.estado[tournament.estado] || tournament.estado || '';
        const estadoClass   = tournament.estado ? `status-${tournament.estado.toLowerCase()}` : '';
        const modalidadDisplay = tournament.modalidad === 'DOBLES'
            ? t('card.modalidad.doubles')
            : t('card.modalidad.individual');

        // ── НОВАЯ ПРОВЕРКА: начался ли турнир ──────────────────────
        const started = isTournamentStarted(tournament);

        const spotsStatus = this.getSpotsStatus(tournament);
        const spotsStatusText = {
            available: t('card.spots.available'),
            limited: t('card.spots.limited'),
            full: t('card.spots.completos')
        }[spotsStatus];

        const typeIcons = {
            KING_OF_COURT: 'fa-crown',
            AMERICANO: 'fa-trophy',
            AMERICANO_TEAMS: 'fa-trophy',
            PADEL_CLINIC: 'fa-bolt',
            CANCHA_ABIERTA: 'fa-table-tennis-paddle-ball'
        };
        const clubAddress = tournament.clubDireccion
            ? `<span class="club-address">${this.escapeHtml(tournament.clubDireccion)}</span>`
            : '';

        card.innerHTML = `
        <div class="tournament-card-body">
            <div class="home-event-top">
                <span class="home-event-type">
                    <i class="fas ${typeIcons[tournament.tipo] || 'fa-calendar'}" aria-hidden="true"></i>
                    ${tipoDisplay}
                </span>
                <span class="tournament-spots tournament-spots--${spotsStatus}">
                    <span class="tournament-spots-dot"></span>${spotsStatusText}
                </span>
            </div>
            <p class="home-event-date">${fecha} · ${hora}</p>
            <h3 class="tournament-title">
                <a href="/torneo/${tournament.id}">${this.escapeHtml(tournament.nombre) || tipoDisplay}</a>
            </h3>
            <div class="home-event-tags">
                <span>${nivelDisplay}</span><span>${generoDisplay}</span>
            </div>
            <p class="tournament-modalidad">${modalidadDisplay}</p>
            <div class="tournament-info">
                <div class="tournament-info-item">
                    <i class="fas fa-map-marker-alt"></i>
                    <div class="club-info">
                        <span class="club-name">${this.escapeHtml(tournament.clubNombre || t('card.club.unknown'))}</span>
                        ${clubAddress}
                    </div>
                </div>
            </div>
            <div class="tournament-footer">
                <a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
                    <i class="fas fa-info-circle"></i> ${t('card.btn.more_info')}
                </a>
                ${started
            ? `<span class="tournament-status" style="color:#6c757d; font-size:.8rem;">
                           <i class="fas fa-lock"></i> ${t('card.status.started')}
                       </span>`
            : tournament.estado === 'REGISTRO_ABIERTO'
                ? spotsStatus === 'full'
                    ? `<a href="/torneo/${tournament.id}" class="btn btn-waitlist btn-small" data-registration-intent="${tournament.id}">
                               <i class="fas fa-clock"></i> ${t('card.btn.register_waitlist')}
                           </a>`
                    : `<a href="/torneo/${tournament.id}" class="btn btn-primary btn-small" data-registration-intent="${tournament.id}">
                               <i class="fas fa-plus-circle"></i> ${t('card.btn.register_now')}
                           </a>`
                : `<span class="tournament-status ${estadoClass}">${estadoDisplay}</span>`
        }
            </div>
        </div>
    `;

        return card;
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