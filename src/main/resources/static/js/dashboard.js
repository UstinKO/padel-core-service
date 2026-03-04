/**
 * Padel Core - Dashboard JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // Маппинги для отображения значений
    const displayMaps = {
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
        estado: {
            'REGISTRO_ABIERTO': 'Inscripción abierta',
            'PUBLICADO': 'Próximamente',
            'BORRADOR': 'Borrador',
            'CERRADO': 'Cerrado',
            'FINALIZADO': 'Finalizado',
            'CANCELADO': 'Cancelado'
        },
        genero: {
            'MASCULINO': 'Masculino',
            'FEMENINO': 'Femenino',
            'MIXTO': 'Mixto'
        }
    };

    // Элементы фильтров
    const toggleFilters = document.getElementById('toggleFilters');
    const filtersPanel = document.getElementById('filtersPanel');
    const applyFilters = document.getElementById('applyDashboardFilters');
    const clearFilters = document.getElementById('clearDashboardFilters');
    const generoSelect = document.getElementById('dashboardGenero');
    const nivelSelect = document.getElementById('dashboardNivel');
    const tipoSelect = document.getElementById('dashboardTipo');
    const myTournamentsCheckbox = document.getElementById('myTournamentsOnly');
    const myTournamentsCount = document.getElementById('myTournamentsCount');
    const torneosGrid = document.getElementById('dashboardTorneosGrid');
    const visibleCount = document.getElementById('visibleCount');
    const totalCount = document.getElementById('totalCount');
    const noTournamentsMessage = document.getElementById('noTournamentsMessage');
    const resultsCounter = document.getElementById('resultsCounter');

    // Данные турниров
    const tournaments = window.tournamentData || [];
    const myTournamentIds = new Set(window.myTournamentIds || []);

    console.log('🏆 Турниров загружено:', tournaments.length);
    console.log('📋 Мои турниры:', myTournamentIds.size, 'ID:', [...myTournamentIds]);

    // Логируем уникальные значения для отладки
    const uniqueNiveles = [...new Set(tournaments.map(t => t.categoriaNivel))];
    console.log('📊 Уникальные значения уровней:', uniqueNiveles);

    // Обновляем счетчик моих турниров
    if (myTournamentsCount) {
        myTournamentsCount.textContent = `(${myTournamentIds.size})`;
    }

    // Текущие отфильтрованные турниры
    let filteredTournaments = [...tournaments];

    // Показать/скрыть панель фильтров
    if (toggleFilters) {
        toggleFilters.addEventListener('click', () => {
            const isHidden = filtersPanel.style.display === 'none' || filtersPanel.style.display === '';
            filtersPanel.style.display = isHidden ? 'block' : 'none';
            toggleFilters.innerHTML = isHidden ?
                '<i class="fas fa-times"></i> Cerrar filtros' :
                '<i class="fas fa-sliders-h"></i> Filtrar';
        });
    }

    // Применить фильтры
    if (applyFilters) {
        applyFilters.addEventListener('click', applyFiltersFunction);
    }

    // Очистить фильтры
    if (clearFilters) {
        clearFilters.addEventListener('click', () => {
            if (generoSelect) generoSelect.value = 'todos';
            if (nivelSelect) nivelSelect.value = 'todos';
            if (tipoSelect) tipoSelect.value = 'todos';
            if (myTournamentsCheckbox) myTournamentsCheckbox.checked = false;
            applyFiltersFunction();
        });
    }

    // Функция применения фильтров
    function applyFiltersFunction() {
        const filters = {
            genero: generoSelect ? generoSelect.value : 'todos',
            nivel: nivelSelect ? nivelSelect.value : 'todos',
            tipo: tipoSelect ? tipoSelect.value : 'todos',
            myTournamentsOnly: myTournamentsCheckbox ? myTournamentsCheckbox.checked : false
        };

        console.log('🔍 Применяем фильтры:', filters);

        filteredTournaments = tournaments.filter(tournament => {
            // Фильтр по полу - сравниваем напрямую с enum значениями
            if (filters.genero !== 'todos') {
                if (tournament.generoFormato !== filters.genero) {
                    return false;
                }
            }

            // Фильтр по уровню - сравниваем напрямую с enum значениями (C9, C8, C7, C6, C5)
            if (filters.nivel !== 'todos') {
                if (tournament.categoriaNivel !== filters.nivel) {
                    return false;
                }
            }

            // Фильтр по типу
            if (filters.tipo !== 'todos') {
                if (tournament.tipo !== filters.tipo) {
                    return false;
                }
            }

            // Фильтр "Мои турниры"
            if (filters.myTournamentsOnly && !myTournamentIds.has(tournament.id)) {
                return false;
            }

            return true;
        });

        console.log('✅ Найдено турниров:', filteredTournaments.length);

        // Логируем распределение по уровням после фильтрации
        const nivelCounts = {};
        filteredTournaments.forEach(t => {
            const nivel = t.categoriaNivel || 'undefined';
            nivelCounts[nivel] = (nivelCounts[nivel] || 0) + 1;
        });
        console.log('📊 Распределение по уровням после фильтрации:', nivelCounts);

        renderTournaments();
    }

    // Отрисовка турниров
    function renderTournaments() {
        if (!torneosGrid) return;

        // Обновляем счетчики
        if (visibleCount) visibleCount.textContent = filteredTournaments.length;
        if (totalCount) totalCount.textContent = tournaments.length;

        if (filteredTournaments.length === 0) {
            torneosGrid.innerHTML = '';
            if (noTournamentsMessage) noTournamentsMessage.style.display = 'block';
            if (resultsCounter) resultsCounter.style.display = 'none';
            return;
        }

        if (noTournamentsMessage) noTournamentsMessage.style.display = 'none';
        if (resultsCounter) resultsCounter.style.display = 'block';

        torneosGrid.innerHTML = filteredTournaments.map(tournament => {
            const isMyTournament = myTournamentIds.has(tournament.id);
            const myTournamentBadge = isMyTournament ?
                '<span class="my-tournament-badge"><i class="fas fa-check-circle"></i> Inscrito</span>' : '';

            // Форматируем дату
            const fechaArray = tournament.fechaInicio;
            const fechaStr = Array.isArray(fechaArray) ?
                `${fechaArray[2]}/${fechaArray[1]}/${fechaArray[0]}` :
                tournament.fechaInicio || '';

            // Форматируем время
            const horaArray = tournament.horaInicio;
            const horaStr = Array.isArray(horaArray) ?
                `${horaArray[0]}:${horaArray[1].toString().padStart(2, '0')}` :
                tournament.horaInicio || '';

            // Определяем статус регистрации
            const registrationStatus = tournament.inscritosActuales >= tournament.cupoMax ? 'full' : 'available';
            const registrationText = tournament.inscritosActuales >= tournament.cupoMax ?
                'Lista de espera' : 'Registrarse';

            // Получаем отображаемые тексты
            const generoDisplay = displayMaps.genero[tournament.generoFormato] || tournament.generoFormato || 'N/A';
            const nivelDisplay = displayMaps.nivel[tournament.categoriaNivel] || tournament.categoriaNivel || 'N/A';
            const tipoDisplay = displayMaps.tipo[tournament.tipo] || tournament.tipo || 'N/A';

            return `
            <div class="torneo-card ${isMyTournament ? 'my-tournament' : ''}" data-tournament-id="${tournament.id}">
                <div class="torneo-card-header">
                    <span class="torneo-badge">${generoDisplay}</span>
                    <span class="torneo-badge torneo-badge-level">${nivelDisplay}</span>
                </div>
                <div class="torneo-card-body">
                    <h3 class="torneo-title">${escapeHtml(tournament.nombre || '')}</h3>
                    ${myTournamentBadge}
                    <div class="torneo-info">
                        <div class="torneo-info-item">
                            <i class="fas fa-calendar-alt"></i>
                            <span>${fechaStr} ${horaStr}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-map-marker-alt"></i>
                            <span>${escapeHtml(tournament.clubNombre || '')}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-trophy"></i>
                            <span>${tipoDisplay}</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-users"></i>
                            <span>${tournament.inscritosActuales || 0}/${tournament.cupoMax || 0} inscritos</span>
                        </div>
                        <div class="torneo-info-item">
                            <i class="fas fa-tag"></i>
                            <span>${tournament.precio || 0} ${tournament.moneda || ''}</span>
                        </div>
                    </div>
                    <div class="torneo-footer">
    ${!isMyTournament ? `
        <button class="btn btn-primary btn-small btn-register" 
                data-tournament-id="${tournament.id}"
                data-tournament-name="${escapeHtml(tournament.nombre)}">
            <i class="fas fa-plus-circle"></i> 
            <span>${registrationText}</span>
        </button>
    ` : `
        <button class="btn btn-outline btn-small btn-cancel" 
                data-tournament-id="${tournament.id}"
                data-tournament-name="${escapeHtml(tournament.nombre)}">
            <i class="fas fa-times-circle"></i> Cancelar
        </button>
    `}
    <a href="/torneo/${tournament.id}" class="btn btn-outline btn-small">
        <i class="fas fa-info-circle"></i> Detalles
    </a>
                    </div>
                </div>
            </div>
        `}).join('');

        // Добавляем обработчики для кнопок регистрации
        attachRegistrationHandlers();
    }

    // Прикрепляем обработчики к кнопкам регистрации
    function attachRegistrationHandlers() {
        document.querySelectorAll('.btn-register').forEach(button => {
            button.addEventListener('click', handleRegistration);
        });

        document.querySelectorAll('.btn-cancel').forEach(button => {
            button.addEventListener('click', handleCancellation);
        });
    }

    // Обработчик регистрации
    async function handleRegistration(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        // Показываем подтверждение
        if (!confirm(`¿Deseas inscribirte en el torneo "${tournamentName}"?`)) {
            return;
        }

        // Блокируем кнопку
        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const response = await fetch(`/players/tournaments/${tournamentId}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                // Показываем сообщение об успехе
                alert(data.message);

                // Добавляем турнир в список моих
                myTournamentIds.add(parseInt(tournamentId));
                if (myTournamentsCount) {
                    myTournamentsCount.textContent = `(${myTournamentIds.size})`;
                }

                // Перерисовываем турниры
                applyFiltersFunction();
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-plus-circle"></i> Registrarse';
            }
        } catch (error) {
            console.error('Error registering:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-plus-circle"></i> Registrarse';
        }
    }

    // Обработчик отмены регистрации
    async function handleCancellation(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        const reason = prompt('¿Por qué cancelas tu inscripción? (opcional)');

        if (!confirm(`¿Estás seguro de cancelar tu inscripción en "${tournamentName}"?`)) {
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const url = `/players/tournaments/${tournamentId}/cancel` +
                (reason ? `?reason=${encodeURIComponent(reason)}` : '');

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                alert(data.message);

                // Удаляем турнир из списка моих
                myTournamentIds.delete(parseInt(tournamentId));
                if (myTournamentsCount) {
                    myTournamentsCount.textContent = `(${myTournamentIds.size})`;
                }

                // Перерисовываем турниры
                applyFiltersFunction();
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
            }
        } catch (error) {
            console.error('Error cancelling:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
        }
    }

    // Функция для безопасного экранирования HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Инициализация - показываем все турниры
    renderTournaments();

    // Автоматически обновляем список каждые 30 секунд (опционально)
    setInterval(() => {
        // Можно добавить логику для обновления данных с сервера
        console.log('Auto-refresh not implemented');
    }, 30000);
});