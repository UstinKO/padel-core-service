/**
 * King of Court - Padel Core
 * Управление турниром Король Корта с WebSocket
 */

class KingOfCourt {
    constructor() {
        // Защита от undefined
        const data = window.tournamentData || {};

        this.kingId = data.kingId;
        this.tournamentId = data.tournamentId;
        this.currentRound = data.currentRound || 1;
        this.calibrationRounds = data.calibrationRounds || 3;
        this.maxCourts = data.maxCourts || 5;
        this.isAdmin = data.isAdmin || false;
        this.isFinished = data.isFinished || false;
        this.stompClient = null;
        this.connected = false;

        this.editCourt = null;
        this.confirmAction = null;
        this.finalResultsShown = false;
        this.pollingInterval = null;

        console.log('🏆 King of Court initialized:', {
            kingId: this.kingId,
            tournamentId: this.tournamentId,
            currentRound: this.currentRound,
            maxCourts: this.maxCourts,
            isAdmin: this.isAdmin,
            isFinished: this.isFinished
        });

        // Проверяем, что kingId существует
        if (!this.kingId) {
            console.error('❌ kingId is undefined! Check if window.tournamentData is set correctly');
            return;
        }

        this.init();
        this.initBurgerMenu(); // Добавляем инициализацию бургер-меню
        this.loadData();
        window.addEventListener('pageshow', event => { if (event.persisted) this.loadData(); });
        this.connectWebSocket();
        this.startPolling();
    }

    // ===== НОВЫЙ МЕТОД: Инициализация бургер-меню =====
    initBurgerMenu() {
        const navbarToggler = document.getElementById('navbarToggler');
        const navbarNav = document.getElementById('navbarNav');

        if (navbarToggler && navbarNav) {
            console.log('✅ King of Court: найдены элементы меню');

            // Убираем старые обработчики
            const newToggler = navbarToggler.cloneNode(true);
            navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

            // Добавляем обработчик
            newToggler.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();

                navbarNav.classList.toggle('show');

                // Анимация иконки
                newToggler.classList.toggle('active');

                console.log('🍔 Меню King of Court:', navbarNav.classList.contains('show') ? 'открыто' : 'закрыто');
            });

            // Закрываем меню при клике на ссылку
            navbarNav.querySelectorAll('a').forEach(link => {
                link.addEventListener('click', () => {
                    navbarNav.classList.remove('show');
                    newToggler.classList.remove('active');
                });
            });

            // Закрываем меню при клике вне его
            document.addEventListener('click', (e) => {
                if (navbarNav.classList.contains('show') &&
                    !navbarNav.contains(e.target) &&
                    !newToggler.contains(e.target)) {
                    navbarNav.classList.remove('show');
                    newToggler.classList.remove('active');
                }
            });
        } else {
            console.warn('⚠️ King of Court: элементы меню не найдены', {
                toggler: navbarToggler,
                nav: navbarNav
            });
        }
    }

    async checkDebugEndpoint() {
        if (!this.kingId) return;

        try {
            const response = await fetch(`/api/king-of-court/debug/tournaments/${this.kingId}/check-players`);
            if (response.ok) {
                const data = await response.json();
                console.log('🔍 Debug info:', data);
            }
        } catch (error) {
            console.error('Debug endpoint error:', error);
        }
    }

    async loadData() {
        if (!this.kingId) {
            console.error('❌ Cannot load data: kingId is undefined');
            return;
        }

        console.log('📥 Loading tournament data for kingId:', this.kingId);

        try {
            // Добавляем timestamp для предотвращения кэширования
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/state?t=${Date.now()}`);

            if (!response.ok) {
                console.error('API returned error:', response.status, response.statusText);
                if (response.status === 404 && this.isAdmin) {
                    window.location.replace(`/admin/tournaments/${this.tournamentId}`);
                }
                return;
            }

            const data = await response.json();
            console.log('📊 API Response - Round:', data.currentRound, 'Courts:', data.courts?.length);

            if (!data) {
                console.error('API returned empty data');
                return;
            }

            // Обновляем текущий раунд из данных
            if (data.currentRound) {
                this.currentRound = data.currentRound;
            }

            this.roundId = data.currentRoundId;
            this.conductVersion = data.conductVersion;
            const restartVersion = document.querySelector('#restartTournamentForm [name="expectedConductVersion"]');
            if (restartVersion) restartVersion.value = this.conductVersion;
            this.isFinished = data.isFinished;
            this.currentCourts = data.courts || [];
            this.renderCourts(this.currentCourts);
            this.renderLeaderboard(data.ranking || []);
            this.renderMatchHistory(data.history || []);

            this.checkAdminButtons(data.allResultsIn);

            if (data.isFinished && !this.finalResultsShown) {
                this.showFinalResults(data.ranking);
                this.finalResultsShown = true;
            } else if (this.isFinished) {
                this.showFinalResults(data.ranking, false);
            }

            console.log('✅ Data loaded and rendered successfully');
        } catch (error) {
            console.error('❌ Error loading tournament data:', error);
        }
    }

    connectWebSocket() {
        if (typeof SockJS === 'undefined') {
            console.warn('SockJS not loaded, skipping WebSocket connection');
            return;
        }

        if (!this.kingId) {
            console.warn('Cannot connect WebSocket: kingId is undefined');
            return;
        }

        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;

            this.stompClient.connect({}, (frame) => {
                console.log('✅ WebSocket connected successfully');
                this.connected = true;

                this.stompClient.subscribe(`/topic/tournament/${this.kingId}`, (message) => {
                    try {
                        const update = JSON.parse(message.body);
                        console.log('📩 WebSocket message received:', update);
                        this.handleWebSocketUpdate(update);
                    } catch (e) {
                        console.error('Error parsing WebSocket message:', e);
                    }
                });

                console.log(`📡 Subscribed to /topic/tournament/${this.kingId}`);

            }, (error) => {
                console.error('❌ WebSocket connection error:', error);
                this.connected = false;
                setTimeout(() => this.connectWebSocket(), 5000);
            });
        } catch (error) {
            console.error('Error creating WebSocket connection:', error);
        }
    }

    handleWebSocketUpdate(update) {
        console.log('🔄 Processing WebSocket update:', update);

        switch (update.type) {
            case 'RESULT_SAVED':
                console.log('🎯 Result saved for court');
                this.showNotification(t('koc.admin.result_saved'), 'success');
                this.loadData();
                break;
            case 'ROUND_COMPLETED':
                console.log('🏁 Round completed');
                this.showNotification(t('koc.admin.round_complete', update.data), 'success');
                this.loadData(); // Загружаем данные, чтобы обновить состояние кнопок
                break;
            case 'NEXT_ROUND_STARTED':
                console.log('➡️ Next round started');
                this.currentRoundData = null;
                this.loadData();
                this.showNotification(t('koc.admin.round_started', update.data), 'success');
                break;
            case 'STATE_UPDATED':
                console.log('🔄 State updated');
                this.loadData(); // Это обновит все кнопки, включая "Закончить турнир"
                break;
            case 'TOURNAMENT_FINISHED':
                console.log('🏆 Tournament finished');
                if (update.data && update.data.ranking) {
                    this.showFinalResults(update.data.ranking);
                }
                this.loadData();
                this.showNotification(t('koc.admin.finished'), 'success');
                break;
            default:
                console.log('Unknown update type:', update.type);
        }
    }

    init() {
        document.addEventListener('click', (e) => {
            if (e.target.closest('.enter-result-btn')) {
                const btn = e.target.closest('.enter-result-btn');
                this.openResultModal(
                    btn.dataset.courtId,
                    btn.dataset.courtNumber,
                    false
                );
                e.preventDefault();
            }

            if (e.target.closest('.edit-result-btn')) {
                const btn = e.target.closest('.edit-result-btn');
                this.openResultModal(
                    btn.dataset.courtId,
                    btn.dataset.courtNumber,
                    true
                );
                e.preventDefault();
            }

            if (e.target.closest('.view-history-btn')) {
                const btn = e.target.closest('.view-history-btn');
                this.openHistoryModal(btn.dataset.playerId, btn.dataset.playerName);
                e.preventDefault();
            }
        });

        document.getElementById('nextRoundBtn')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.nextRound();
        });

        document.getElementById('endTournamentBtn')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.endTournament();
        });

        const saveYoutubeBtn = document.querySelector('.youtube-link-form .btn-primary');
        if (saveYoutubeBtn) {
            saveYoutubeBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.saveYoutubeLink();
            });
        }

        const saveBtn = document.getElementById('save-result-btn');
        if (saveBtn) {
            saveBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.saveResult();
            });
        }

        const cancelBtn = document.getElementById('cancel-result-btn');
        if (cancelBtn) {
            cancelBtn.addEventListener('click', (e) => {
                e.preventDefault();
                this.closeResultModal();
            });
        }

        const modalCloseButtons = document.querySelectorAll('.modal-close');
        modalCloseButtons.forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                const modal = btn.closest('.modal');
                if (modal) {
                    modal.style.display = 'none';
                }
            });
        });
    }

    renderCourts(courts) {
        const container = document.getElementById('courtsContainer');
        if (!container) {
            console.error('Courts container not found');
            return;
        }

        console.log('Rendering courts for round', this.currentRound, 'count:', courts.length);

        // Очищаем контейнер
        container.innerHTML = '';

        if (courts.length === 0) {
            container.innerHTML = `
                <div class="empty-state" style="text-align: center; padding: 2rem;">
                    <i class="fas fa-crown" style="font-size: 3rem; color: #9ca3af;"></i>
                    <h3 style="margin-top: 1rem;">No hay canchas activas</h3>
                    <p style="color: #6b7280;">Posiblemente el torneo aún no está inicializado</p>
                    ${this.isAdmin ? `
                        <button class="btn btn-primary" style="margin-top: 1rem;" onclick="window.kingOfCourt?.initializeTournament()">
                            <i class="fas fa-play"></i> Inicializar torneo
                        </button>
                    ` : ''}
                </div>
            `;
            return;
        }

        courts.forEach(court => {
            const courtCard = this.createCourtCard(court);
            container.appendChild(courtCard);
        });
    }

    async initializeTournament() {
        if (!confirm(t('koc.admin.init_confirm'))) return;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.tournamentId}/initialize`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    maxCourts: this.maxCourts,
                    calibrationRounds: 3
                })
            });

            if (response.ok) {
                const data = await response.json();
                this.kingId = data.id;
                console.log('Tournament initialized with ID:', this.kingId);
                this.showNotification(t('koc.admin.init_success'), 'success');
                this.loadData();
                this.connectWebSocket();
            } else {
                const error = await response.json();
                console.error('Init error:', error);
                this.showError(error.message || t('koc.admin.error_init'));
            }
        } catch (error) {
            console.error('Error initializing tournament:', error);
            this.showError(t('koc.admin.error_connection'));
        }
    }

    createCourtCard(court) {
        const card = document.createElement('div');
        card.className = `court-card ${court.hasResult ? 'completed' : 'in-progress'}`;

        let content = `
            <div class="court-header">
                <h3>Корт ${court.courtNumber}</h3>
                <span class="court-status ${court.hasResult ? 'completed' : 'in-progress'}">
                    ${court.hasResult ? t('koc.viewer.status_done') : t('koc.viewer.status_playing')}
                </span>
            </div>
        `;

        if (court.hasResult && court.result) {
            content += `
                <div class="court-result">
                    <div class="result-winners">
                        <i class="fas fa-trophy" style="color: #fbbf24;"></i>
                        <strong>${court.result.winnerNames.join(' & ')}</strong>
                    </div>
                    <div class="result-losers">
                        <span style="margin-left: 1.5rem;">${court.result.loserNames.join(' & ')}</span>
                    </div>
                    <div class="result-score">
                        <strong>${court.result.winnersScore} : ${court.result.losersScore}</strong>
                    </div>
                </div>
            `;

            if (this.isAdmin && !this.isFinished) {
                content += `
                    <div style="display: flex; gap: 0.5rem; margin-top: 1rem;">
                        <button class="btn btn-primary btn-sm edit-result-btn" 
                                data-court-id="${court.id}"
                                data-court-number="${court.courtNumber}"
                                style="flex: 1;">
                            <i class="fas fa-edit"></i> Editar
                        </button>
                    </div>
                `;
            }
        } else if (court.teams && court.teams.length > 0) {
            content += '<div class="court-teams">';

            court.teams.forEach(team => {
                const teamPlayers = team.players.map(p => p.name).join(' & ');
                content += `
                    <div class="team">
                        <span class="team-number">${window.t('koc.viewer.team')} ${team.teamNumber}</span>
                        <span class="team-players">${teamPlayers}</span>
                    </div>
                `;
            });

            content += '</div>';

            if (this.isAdmin && !this.isFinished) {
                content += `
                    <button class="btn btn-primary btn-sm enter-result-btn" 
                            data-court-id="${court.id}"
                            data-court-number="${court.courtNumber}"
                            style="margin-top: 1rem; width: 100%;">
                        <i class="fas fa-edit"></i> ${t('koc.admin.btn.enter_result')}
                    </button>
                `;
            }
        } else {
            content += `<p class="text-gray-500">${t('koc.viewer.court_free')}</p>`;
        }

        card.innerHTML = content;
        return card;
    }

    renderLeaderboard(ranking) {
        const tbody = document.getElementById('leaderboard');
        if (!tbody) {
            console.error('Leaderboard tbody not found');
            return;
        }

        console.log('Rendering leaderboard with', ranking.length, 'players');

        tbody.innerHTML = '';

        if (ranking.length === 0) {
            tbody.innerHTML = `<tr><td colspan="8" class="text-center">${t('koc.admin.no_data')}</td></tr>`;
            return;
        }

        ranking.forEach(player => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${player.rank || '-'}</strong></td>
                <td>
                    <a href="#" class="view-history-btn" 
                       data-player-id="${player.playerId || ''}"
                       data-player-name="${tournamentPoints.escape(player.playerName)}">
                        ${tournamentPoints.escape(player.playerName || t('koc.viewer.unknown_player'))}
                    </a>
                    ${this.isAdmin && !this.isFinished ? tournamentPoints.action(player.playerId) : ''}
                </td>
                <td>${player.matchPoints ?? 0}</td>
                <td><span class="bonus-points">+${player.bonusPoints || 0}</span></td>
                ${tournamentPoints.cell(player.additionalPoints)}
                <td><strong>${player.totalPoints || 0}</strong></td>
                <td>${player.gamesPlayed || 0}</td>
                <td>${player.wins || 0}/${player.losses || 0}</td>
            `;
            tbody.appendChild(row);
        });
    }

    renderMatchHistory(history) {
        const list = document.getElementById('matchHistory');
        if (!list) {
            console.error('Match history list not found');
            return;
        }

        console.log('Rendering match history with', history.length, 'matches');

        list.innerHTML = '';

        if (history.length === 0) {
            list.innerHTML = `<li class="text-center text-gray-500">${t('koc.viewer.history_empty')}</li>`;
            return;
        }

        history.slice(0, 10).forEach(match => {
            const li = document.createElement('li');
            li.className = 'history-item';
            li.innerHTML = `
                <div class="history-header">
                    <span class="badge">${t('koc.viewer.round')} ${match.round || '-'}</span>
                    <span class="badge badge-level">${t('koc.viewer.court')} ${match.courtNumber || '-'}</span>
                </div>
                <div class="history-winners">
                    <i class="fas fa-trophy" style="color: #fbbf24;"></i>
                    ${match.winners?.join(' & ') || t('koc.viewer.unknown')}
                </div>
                <div class="history-losers">
                    <span style="margin-left: 1.5rem;">${match.losers?.join(' & ') || t('koc.viewer.unknown')}</span>
                </div>
                <div class="history-score">
                    <strong>${match.winnersScore || 0} : ${match.losersScore || 0}</strong>
                </div>
            `;
            list.appendChild(li);
        });
    }

    checkAdminButtons(allResultsIn) {
        const nextRoundBtn    = document.getElementById('nextRoundBtn');
        const resetBtn        = document.getElementById('resetRoundBtn');
        const endTournamentBtn = document.getElementById('endTournamentBtn');

        console.log('Checking admin buttons:', {
            isAdmin: this.isAdmin,
            isFinished: this.isFinished,
            allResultsIn,
            currentRound: this.currentRound
        });

        // "Следующий раунд" — только когда все результаты введены
        if (nextRoundBtn) {
            nextRoundBtn.style.display =
                (this.isAdmin && !this.isFinished && allResultsIn) ? 'block' : 'none';
        }

        if (resetBtn) {
            const canReset = this.isAdmin && !this.isFinished && this.currentCourts?.some(court => court.hasResult);
            resetBtn.style.display = canReset ? '' : 'none';
        }

        // "Завершить турнир" — только когда все результаты введены
        if (endTournamentBtn) {
            endTournamentBtn.style.display =
                (this.isAdmin && !this.isFinished && allResultsIn) ? 'block' : 'none';
        }
    }

    openResultModal(courtId, courtNumber, isEditMode = false) {
        const court = this.currentCourts?.find(item => item.id === Number(courtId));
        const modal = document.getElementById('resultModal');
        if (!court || !modal) return;

        this.editCourt = court;
        this.editRoundId = this.roundId;
        this.editVersion = this.conductVersion;
        const result = isEditMode ? court.result : null;

        document.getElementById('modalTitle').textContent =
            t(isEditMode ? 'koc.admin.modal.result_edit' : 'koc.admin.modal.result_new', courtNumber);
        document.getElementById('modalPlayersInfo').textContent = court.teams.map(team =>
            `${t('koc.viewer.team')} ${team.teamNumber}: ${team.players.map(player => player.name).join(' & ')}`
        ).join(' vs ');

        document.getElementById('court-additional-points').innerHTML = court.players.map(player =>
            tournamentPoints.action(player.id, this.currentRound, `${tournamentPoints.copy.action} · ${player.name}`)
        ).join('');

        this.setupResultWinners(court, result);
        document.getElementById('scoreWinners').value = result?.winnersScore ?? '';
        document.getElementById('scoreLosers').value = result?.losersScore ?? '';
        document.getElementById('modalError').style.display = 'none';
        modal.style.display = 'flex';
    }

    setupResultWinners(court, result) {
        const first = document.getElementById('winner1');
        const second = document.getElementById('winner2');
        const manual = document.getElementById('manualWinners');
        const playerIds = new Set(court.players.map(player => String(player.id)));
        const partners = new Map();
        for (const team of court.teams) {
            const ids = team.players.map(player => String(player.id));
            if (ids.length === 2 && ids[0] !== ids[1] && ids.every(id => playerIds.has(id))) {
                partners.set(ids[0], ids[1]);
                partners.set(ids[1], ids[0]);
            }
        }
        this.resultPartners = partners;
        const options = [new Option(first.dataset.placeholder, ''),
            ...court.players.map(player => new Option(player.name, player.id))];
        first.replaceChildren(...options);
        second.replaceChildren(...options.map(option => option.cloneNode(true)));
        first.value = result ? String(result.winners[0]) : '';
        second.value = result ? String(result.winners[1]) : '';

        manual.disabled = court.teams.length !== 2 || playerIds.size !== 4 || partners.size !== 4;
        manual.checked = manual.disabled || Boolean(result && partners.get(first.value) !== second.value);
        const sync = () => {
            second.disabled = !manual.checked;
            if (!manual.checked) second.value = partners.get(first.value) ?? '';
        };
        manual.onchange = sync;
        first.onchange = sync;
        sync();
    }

    showNotification(message, type) {
        console.log(`[${type}] ${message}`);

        const notification = document.createElement('div');
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: ${type === 'success' ? '#10b981' : '#3b82f6'};
            color: white;
            padding: 12px 24px;
            border-radius: 8px;
            z-index: 9999;
            animation: slideIn 0.3s ease;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1);
        `;
        document.body.appendChild(notification);

        setTimeout(() => {
            notification.remove();
        }, 3000);
    }

    async saveResult() {
        if (!this.editCourt || this.savingResult) return;
        const winner1 = document.getElementById('winner1')?.value;
        const winner2 = document.getElementById('winner2')?.value;
        const scoreWinners = parseInt(document.getElementById('scoreWinners')?.value);
        const scoreLosers = parseInt(document.getElementById('scoreLosers')?.value);
        const resultId = this.editCourt.result?.id;
        const manualWinners = document.getElementById('manualWinners').checked;

        if (!winner1 || !winner2 || isNaN(scoreWinners) || isNaN(scoreLosers)) {
            this.showError(t('koc.admin.error_fields'));
            return;
        }

        if (winner1 === winner2) {
            this.showError(t('koc.admin.error_same_winners'));
            return;
        }

        if (scoreWinners <= scoreLosers) {
            this.showError(t('koc.admin.error_score'));
            return;
        }

        const allPlayerIds = this.editCourt.players.map(player => player.id);
        const winners = [parseInt(winner1), parseInt(winner2)];
        const losers = allPlayerIds.filter(id => !winners.includes(id));

        if (losers.length !== 2 || winners.some(id => !allPlayerIds.includes(id))) {
            this.showError(t('koc.admin.error_match_players'));
            return;
        }
        if (!manualWinners && this.resultPartners.get(winner1) !== winner2) {
            this.showError(t('koc.admin.error_assigned_pair'));
            return;
        }

        this.savingResult = true;
        const button = document.getElementById('save-result-btn');
        const label = button.textContent;
        button.disabled = true;
        button.textContent = document.getElementById('conduct-save-status').dataset.saving;
        try {
            const requestBody = {
                roundId: this.editRoundId,
                courtNumber: this.editCourt.courtNumber,
                winners: winners,
                losers: losers,
                winnersScore: scoreWinners,
                losersScore: scoreLosers,
                manualWinners
            };

            const base = '/api/king-of-court/matches/result';
            const url = resultId ? `${base}/${resultId}` : base;
            const method = resultId ? 'PUT' : 'POST';

            const response = await conductFetch(url + `?expectedConductVersion=${this.editVersion}`, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestBody)
            });

            if (response.ok || response.headers.has('X-Conduct-Replayed')) {
                this.closeResultModal();
                this.showNotification(resultId ? t('koc.admin.result_updated') : t('koc.admin.result_saved'), 'success');
                // Данные обновятся через WebSocket, но для надежности загружаем сразу
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || t('koc.admin.error_save'));
            }
        } catch (error) {
            console.error('Error saving result:', error);
            this.showError(t('koc.admin.error_connection'));
        } finally {
            this.savingResult = false;
            button.disabled = false;
            button.textContent = label;
        }
    }

    async nextRound() {
        if (!confirm(t('koc.admin.confirm_next_round'))) return;

        try {
            const response = await conductFetch(`/api/king-of-court/tournaments/${this.kingId}/next-round?expectedConductVersion=${this.conductVersion}`, {
                method: 'POST'
            });

            if (response.ok) {
                console.log('Next round started successfully');
                this.showNotification(t('koc.admin.next_round'), 'success');
                // Данные обновятся через WebSocket, но для надежности загружаем сразу
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || t('koc.admin.error_next_round'));
            }
        } catch (error) {
            console.error('Error next round:', error);
            this.showError(t('koc.admin.error_connection'));
        }
    }

    async endTournament() {
        if (!confirm(t('koc.admin.confirm_finish'))) return;

        try {
            const response = await conductFetch(`/api/king-of-court/tournaments/${this.kingId}/finish?expectedConductVersion=${this.conductVersion}`, {
                method: 'POST'
            });

            if (response.ok) {
                console.log('Tournament finished successfully');
                this.showNotification(t('koc.admin.finished'), 'success');
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || t('koc.admin.error_finish'));
            }
        } catch (error) {
            console.error('Error finishing tournament:', error);
            this.showError(t('koc.admin.error_connection'));
        }
    }

    async saveYoutubeLink() {
        const link = document.getElementById('youtubeLink')?.value;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/youtube?youtubeLink=${encodeURIComponent(link || '')}`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showNotification(t('koc.admin.youtube_saved'), 'success');
                setTimeout(() => this.loadData(), 500);
            } else {
                console.error('Error saving YouTube link');
            }
        } catch (error) {
            console.error('Error saving youtube link:', error);
        }
    }

    async openHistoryModal(playerId, playerName) {
        const historyPlayerName = document.getElementById('historyPlayerName');
        if (historyPlayerName) {
            historyPlayerName.textContent = playerName;
        }

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/players/${playerId}/history`);
            if (!response.ok) return;

            const history = await response.json();

            const tbody = document.getElementById('historyTableBody');
            if (!tbody) return;

            tbody.innerHTML = '';

            if (history.length === 0) {
                tbody.innerHTML = '<tr><td colspan="6" class="text-center">No hay historial de partidos</td></tr>';
            } else {
                history.forEach(match => {
                    const row = document.createElement('tr');
                    const isWinner = match.winners && match.winners.includes(playerName);

                    row.innerHTML = `
                        <td>${match.round || ''}</td>
                        <td>${match.courtNumber || ''}</td>
                        <td class="${isWinner ? 'text-success' : 'text-danger'}">
                            <strong>${isWinner ? t('koc.viewer.win') : t('koc.viewer.loss')}</strong>
                        </td>
                        <td>${match.winnersScore || 0}:${match.losersScore || 0}</td>
                        <td>${isWinner && match.winners ?
                        match.winners.find(w => w !== playerName) || '' :
                        match.losers ? match.losers.find(l => l !== playerName) || '' : ''}
                        </td>
                        <td>${isWinner && match.losers ?
                        match.losers.join(' & ') :
                        match.winners ? match.winners.join(' & ') : ''}
                        </td>
                    `;
                    tbody.appendChild(row);
                });
            }

            const historyModal = document.getElementById('historyModal');
            if (historyModal) {
                historyModal.style.display = 'flex';
            }

        } catch (error) {
            console.error('Error loading player history:', error);
        }
    }

    showFinalResults(ranking, open = true) {
        if (!ranking || ranking.length === 0) return;

        const winner = ranking[0];
        const finalWinnerName = document.getElementById('finalWinnerName');
        if (finalWinnerName) {
            finalWinnerName.textContent = winner.playerName || '';
        }

        const container = document.getElementById('finalLeaderboardContainer');
        if (!container) return;

        container.innerHTML = '';

        const table = document.createElement('table');
        table.className = 'admin-table';

        table.innerHTML = `
            <thead>
                <tr>
                    <th>№</th>
                    <th>${t('koc.table.player')}</th>
                    <th>${tournamentPoints.escape(tournamentPoints.copy.match)}</th>
                    <th>${tournamentPoints.escape(tournamentPoints.copy.bonus)}</th>
                    <th>${tournamentPoints.escape(tournamentPoints.copy.title)}</th>
                    <th>${tournamentPoints.escape(tournamentPoints.copy.total)}</th>
                    <th>${t('koc.table.games')}</th>
                </tr>
            </thead>
            <tbody>
                ${ranking.map(p => `
                    <tr>
                        <td><strong>#${p.rank || ''}</strong></td>
                        <td>${tournamentPoints.escape(p.playerName)}</td>
                        <td>${p.matchPoints ?? 0}</td>
                        <td>+${p.bonusPoints || 0}</td>
                        ${tournamentPoints.cell(p.additionalPoints)}
                        <td>${p.totalPoints || 0}</td>
                        <td>${p.gamesPlayed || 0}</td>
                    </tr>
                `).join('')}
            </tbody>
        `;

        container.appendChild(table);

        const finalResultsModal = document.getElementById('finalResultsModal');
        if (open && finalResultsModal) {
            finalResultsModal.style.display = 'flex';
        }
    }

    closeResultModal() {
        const resultModal = document.getElementById('resultModal');
        if (resultModal) {
            resultModal.style.display = 'none';
        }

        this.editCourt = null;
    }

    showError(message) {
        const errorEl = document.getElementById('modalError');
        if (errorEl) {
            errorEl.textContent = message;
            errorEl.style.display = 'block';
        } else {
            console.error('Error:', message);
        }
    }

    startPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }

        this.pollingInterval = setInterval(() => {
            this.loadData();
        }, 10000); // Уменьшил до 10 секунд для надежности
    }

    closeHistoryModal() {
        const historyModal = document.getElementById('historyModal');
        if (historyModal) {
            historyModal.style.display = 'none';
        }
    }

    closeFinalModal() {
        const finalResultsModal = document.getElementById('finalResultsModal');
        if (open && finalResultsModal) {
            finalResultsModal.style.display = 'none';
        }
    }

    closeConfirmModal() {
        const confirmModal = document.getElementById('confirmModal');
        if (confirmModal) {
            confirmModal.style.display = 'none';
        }
    }

    executeConfirmAction() {
        if (this.confirmAction && typeof this.confirmAction === 'function') {
            this.confirmAction();
        }
        this.closeConfirmModal();
    }

    // ═══════════════════════════════════════════════════════════════════
    // ПАТЧ для king-of-court.js
    async resetRound() {
        const button = document.getElementById('resetRoundBtn');
        const scoredCourts = this.currentCourts.filter(court => court.hasResult)
            .map(court => `${court.courtNumber} (${court.result.winnersScore}:${court.result.losersScore})`).join(', ');
        if (!scoredCourts || !confirm(button.dataset.confirm.replace('{0}', this.currentRound).replace('{1}', scoredCourts))) {
            return;
        }
        button.disabled = true;
        try {
            const response = await conductFetch(
                `/api/king-of-court/rounds/${this.roundId}/reset?expectedConductVersion=${this.conductVersion}`,
                { method: 'POST' }
            );
            if (response.ok || response.headers.has('X-Conduct-Stale') || response.headers.has('X-Conduct-Replayed')) {
                await this.loadData();
            }
        } catch (error) {
            console.error('Error resetting round:', error);
        } finally {
            button.disabled = false;
        }
    }

}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    // Проверяем, что window.tournamentData существует
    if (!window.tournamentData) {
        console.error('❌ window.tournamentData is not defined! Check if the template includes the data script.');
        return;
    }

    window.kingOfCourt = new KingOfCourt();
});
document.addEventListener('additional-points-saved', event => {
    const manager = window.kingOfCourt;
    if (!manager) return;
    const {before, after} = event.detail;
    if (after > before && manager.editVersion === before) {
        manager.editVersion = after;
    }
    manager.loadData();
});
