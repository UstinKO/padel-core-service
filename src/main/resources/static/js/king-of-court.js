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

        this.currentCourtId = null;
        this.currentCourtNumber = null;
        this.isEditMode = false;
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
        this.loadData();
        this.connectWebSocket();
        this.startPolling();
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

            // Принудительно обновляем отображение
            this.renderCourts(data.courts || []);
            this.renderLeaderboard(data.ranking || []);
            this.renderMatchHistory(data.history || []);

            this.checkAdminButtons(data.allResultsIn);

            if (data.isFinished && !this.finalResultsShown) {
                this.showFinalResults(data.ranking);
                this.finalResultsShown = true;
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
                this.showNotification('Результат сохранен', 'success');
                this.loadData();
                break;
            case 'ROUND_COMPLETED':
                console.log('🏁 Round completed');
                this.showNotification(`Раунд ${update.data} завершен`, 'success');
                this.loadData(); // Загружаем данные, чтобы обновить состояние кнопок
                break;
            case 'NEXT_ROUND_STARTED':
                console.log('➡️ Next round started');
                this.currentRoundData = null;
                this.loadData();
                this.showNotification(`Начался раунд ${update.data}`, 'success');
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
                this.showNotification('Турнир завершен', 'success');
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
                    <h3 style="margin-top: 1rem;">Нет активных кортов</h3>
                    <p style="color: #6b7280;">Возможно, турнир еще не инициализирован</p>
                    ${this.isAdmin ? `
                        <button class="btn btn-primary" style="margin-top: 1rem;" onclick="window.kingOfCourt?.initializeTournament()">
                            <i class="fas fa-play"></i> Инициализировать турнир
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
        if (!confirm('Инициализировать турнир Король Корта?')) return;

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
                this.showNotification('Турнир инициализирован', 'success');
                this.loadData();
                this.connectWebSocket();
            } else {
                const error = await response.json();
                console.error('Init error:', error);
                this.showError(error.message || 'Ошибка инициализации');
            }
        } catch (error) {
            console.error('Error initializing tournament:', error);
            this.showError('Ошибка соединения');
        }
    }

    createCourtCard(court) {
        const card = document.createElement('div');
        card.className = `court-card ${court.hasResult ? 'completed' : 'in-progress'}`;

        let content = `
            <div class="court-header">
                <h3>Корт ${court.courtNumber}</h3>
                <span class="court-status ${court.hasResult ? 'completed' : 'in-progress'}">
                    ${court.hasResult ? 'ГОТОВО' : 'ИДЕТ ИГРА'}
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
                            <i class="fas fa-edit"></i> Редактировать
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
                        <span class="team-number">Команда ${team.teamNumber}</span>
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
                        <i class="fas fa-edit"></i> Ввести результат
                    </button>
                `;
            }
        } else {
            content += '<p class="text-gray-500">Корт свободен</p>';
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
            tbody.innerHTML = '<tr><td colspan="6" class="text-center">Нет данных. Зарегистрируйте игроков и инициализируйте турнир.</td></tr>';
            return;
        }

        ranking.forEach(player => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td><strong>#${player.rank || '-'}</strong></td>
                <td>
                    <a href="#" class="view-history-btn" 
                       data-player-id="${player.playerId || ''}"
                       data-player-name="${player.playerName || ''}">
                        ${player.playerName || 'Неизвестный игрок'}
                    </a>
                </td>
                <td><strong>${player.totalPoints || 0}</strong></td>
                <td><span class="bonus-points">+${player.bonusPoints || 0}</span></td>
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
            list.innerHTML = '<li class="text-center text-gray-500">История пуста</li>';
            return;
        }

        history.slice(0, 10).forEach(match => {
            const li = document.createElement('li');
            li.className = 'history-item';
            li.innerHTML = `
                <div class="history-header">
                    <span class="badge">Раунд ${match.round || '-'}</span>
                    <span class="badge badge-level">Корт ${match.courtNumber || '-'}</span>
                </div>
                <div class="history-winners">
                    <i class="fas fa-trophy" style="color: #fbbf24;"></i>
                    ${match.winners?.join(' & ') || 'Неизвестно'}
                </div>
                <div class="history-losers">
                    <span style="margin-left: 1.5rem;">${match.losers?.join(' & ') || 'Неизвестно'}</span>
                </div>
                <div class="history-score">
                    <strong>${match.winnersScore || 0} : ${match.losersScore || 0}</strong>
                </div>
            `;
            list.appendChild(li);
        });
    }

    checkAdminButtons(allResultsIn) {
        const nextRoundBtn = document.getElementById('nextRoundBtn');
        const endTournamentBtn = document.getElementById('endTournamentBtn');

        console.log('Checking admin buttons:', {
            isAdmin: this.isAdmin,
            isFinished: this.isFinished,
            allResultsIn: allResultsIn,
            currentRound: this.currentRound
        });

        // Кнопка следующего раунда
        if (nextRoundBtn) {
            if (this.isAdmin && !this.isFinished && allResultsIn) {
                nextRoundBtn.style.display = 'block';
                console.log('Next round button SHOWN');
            } else {
                nextRoundBtn.style.display = 'none';
                console.log('Next round button HIDDEN');
            }
        }

        // Кнопка завершения турнира
        if (endTournamentBtn) {
            // Показываем кнопку завершения, если все результаты введены И турнир не завершен
            if (this.isAdmin && !this.isFinished && allResultsIn) {
                endTournamentBtn.style.display = 'block';
                console.log('End tournament button SHOWN');
            } else {
                endTournamentBtn.style.display = 'none';
                console.log('End tournament button HIDDEN');
            }
        }
    }

    openResultModal(courtId, courtNumber, isEditMode = false) {
        this.currentCourtId = courtId;
        this.currentCourtNumber = courtNumber;
        this.isEditMode = isEditMode;

        const resultModal = document.getElementById('resultModal');
        if (!resultModal) {
            console.error('Result modal not found');
            return;
        }

        const modalCourtNumber = document.getElementById('modalCourtNumber');
        if (modalCourtNumber) {
            modalCourtNumber.textContent = courtNumber;
        }

        const modalTitle = document.querySelector('#resultModal .admin-title');
        if (modalTitle) {
            modalTitle.innerHTML = isEditMode ?
                '<i class="fas fa-edit"></i> Редактировать результат для Корта ' + courtNumber :
                '<i class="fas fa-trophy"></i> Результат для Корта ' + courtNumber;
        }

        const oldResultIdField = document.getElementById('resultId');
        if (oldResultIdField) {
            oldResultIdField.remove();
        }

        this.loadCourtData(courtId, isEditMode);

        resultModal.style.display = 'flex';
    }

    async loadCourtData(courtId, isEditMode = false) {
        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/state`);
            if (!response.ok) return;

            const data = await response.json();
            const court = data.courts.find(c => c.id == courtId);
            if (!court) return;

            const modalPlayersInfo = document.getElementById('modalPlayersInfo');
            if (modalPlayersInfo && court.teams) {
                const teamsInfo = court.teams.map(t =>
                    `Команда ${t.teamNumber}: ${t.players.map(p => p.name).join(' & ')}`
                ).join(' vs ');
                modalPlayersInfo.textContent = teamsInfo;
            }

            const winner1 = document.getElementById('winner1');
            const winner2 = document.getElementById('winner2');

            if (winner1 && winner2) {
                winner1.innerHTML = '';
                winner2.innerHTML = '';

                court.players.forEach(player => {
                    const option = document.createElement('option');
                    option.value = player.id;
                    option.textContent = player.name;

                    winner1.appendChild(option.cloneNode(true));
                    winner2.appendChild(option);
                });
            }

            const scoreWinners = document.getElementById('scoreWinners');
            const scoreLosers = document.getElementById('scoreLosers');
            const modalError = document.getElementById('modalError');

            if (isEditMode && court.result) {
                console.log('Found result ID:', court.result.id);

                if (winner1 && winner2 && court.result.winners) {
                    winner1.value = court.result.winners[0];
                    winner2.value = court.result.winners[1];
                }

                if (scoreWinners && scoreLosers) {
                    scoreWinners.value = court.result.winnersScore;
                    scoreLosers.value = court.result.losersScore;
                }

                let resultIdField = document.getElementById('resultId');
                if (!resultIdField) {
                    resultIdField = document.createElement('input');
                    resultIdField.type = 'hidden';
                    resultIdField.id = 'resultId';
                    const formGrid = document.querySelector('#resultModal .form-grid');
                    if (formGrid) {
                        formGrid.appendChild(resultIdField);
                    }
                }
                resultIdField.value = court.result.id;
            } else {
                if (scoreWinners && scoreLosers) {
                    scoreWinners.value = '';
                    scoreLosers.value = '';
                }

                const resultIdField = document.getElementById('resultId');
                if (resultIdField) {
                    resultIdField.remove();
                }
            }

            if (modalError) {
                modalError.style.display = 'none';
            }

        } catch (error) {
            console.error('Error loading court data:', error);
        }
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
        const winner1 = document.getElementById('winner1')?.value;
        const winner2 = document.getElementById('winner2')?.value;
        const scoreWinners = parseInt(document.getElementById('scoreWinners')?.value);
        const scoreLosers = parseInt(document.getElementById('scoreLosers')?.value);
        const resultId = document.getElementById('resultId')?.value;

        console.log('resultId:', resultId);

        if (!winner1 || !winner2 || isNaN(scoreWinners) || isNaN(scoreLosers)) {
            this.showError('Не все поля заполнены');
            return;
        }

        if (winner1 === winner2) {
            this.showError('Выберите двух разных победителей');
            return;
        }

        if (scoreWinners <= scoreLosers) {
            this.showError('Счет победителей должен быть больше счета проигравших');
            return;
        }

        const courtData = await this.getCourtData(this.currentCourtId);
        if (!courtData) {
            this.showError('Не удалось загрузить данные корта');
            return;
        }

        const allPlayerIds = courtData.players.map(p => p.id);
        const winners = [parseInt(winner1), parseInt(winner2)];
        const losers = allPlayerIds.filter(id => !winners.includes(id));

        if (losers.length !== 2) {
            this.showError('Ошибка определения проигравших');
            return;
        }

        try {
            const requestBody = {
                roundId: await this.getCurrentRoundId(),
                courtNumber: parseInt(this.currentCourtNumber),
                winners: winners,
                losers: losers,
                winnersScore: scoreWinners,
                losersScore: scoreLosers
            };

            let url, method;

            if (resultId && resultId !== 'undefined' && resultId !== '') {
                url = `/api/king-of-court/matches/result/${resultId}`;
                method = 'PUT';
                console.log('UPDATING result with ID:', resultId, 'URL:', url);
            } else {
                url = '/api/king-of-court/matches/result';
                method = 'POST';
                console.log('CREATING new result, URL:', url);
            }

            const response = await fetch(url, {
                method: method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(requestBody)
            });

            if (response.ok) {
                console.log('Result saved successfully');
                this.closeResultModal();
                this.showNotification(resultId ? 'Результат обновлен' : 'Результат сохранен', 'success');
                // Данные обновятся через WebSocket, но для надежности загружаем сразу
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || 'Ошибка сохранения');
            }
        } catch (error) {
            console.error('Error saving result:', error);
            this.showError('Ошибка соединения');
        }
    }

    async nextRound() {
        if (!confirm('Перейти к следующему раунду?')) return;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/next-round`, {
                method: 'POST'
            });

            if (response.ok) {
                console.log('Next round started successfully');
                this.showNotification('Переход к следующему раунду', 'success');
                // Данные обновятся через WebSocket, но для надежности загружаем сразу
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || 'Ошибка перехода');
            }
        } catch (error) {
            console.error('Error next round:', error);
            this.showError('Ошибка соединения');
        }
    }

    async endTournament() {
        if (!confirm('Завершить турнир? Дальнейший ввод результатов будет невозможен.')) return;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/finish`, {
                method: 'POST'
            });

            if (response.ok) {
                console.log('Tournament finished successfully');
                this.showNotification('Турнир завершен', 'success');
                setTimeout(() => this.loadData(), 500);
            } else {
                const error = await response.json();
                console.error('Error response:', error);
                this.showError(error.message || 'Ошибка завершения турнира');
            }
        } catch (error) {
            console.error('Error finishing tournament:', error);
            this.showError('Ошибка соединения');
        }
    }

    async saveYoutubeLink() {
        const link = document.getElementById('youtubeLink')?.value;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/youtube?youtubeLink=${encodeURIComponent(link || '')}`, {
                method: 'POST'
            });

            if (response.ok) {
                this.showNotification('YouTube ссылка сохранена', 'success');
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
                tbody.innerHTML = '<tr><td colspan="6" class="text-center">Нет истории матчей</td></tr>';
            } else {
                history.forEach(match => {
                    const row = document.createElement('tr');
                    const isWinner = match.winners && match.winners.includes(playerName);

                    row.innerHTML = `
                        <td>${match.round || ''}</td>
                        <td>${match.courtNumber || ''}</td>
                        <td class="${isWinner ? 'text-success' : 'text-danger'}">
                            <strong>${isWinner ? 'Победа' : 'Поражение'}</strong>
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

    showFinalResults(ranking) {
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
                    <th>Игрок</th>
                    <th>Очки</th>
                    <th>Бонусы</th>
                    <th>Игры</th>
                </tr>
            </thead>
            <tbody>
                ${ranking.map(p => `
                    <tr>
                        <td><strong>#${p.rank || ''}</strong></td>
                        <td>${p.playerName || ''}</td>
                        <td>${p.totalPoints || 0}</td>
                        <td>+${p.bonusPoints || 0}</td>
                        <td>${p.gamesPlayed || 0}</td>
                    </tr>
                `).join('')}
            </tbody>
        `;

        container.appendChild(table);

        const finalResultsModal = document.getElementById('finalResultsModal');
        if (finalResultsModal) {
            finalResultsModal.style.display = 'flex';
        }
    }

    async getCurrentRoundId() {
        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/state`);
            if (!response.ok) return 0;

            const data = await response.json();
            return data.currentRoundId || 0;
        } catch (error) {
            console.error('Error getting current round ID:', error);
            return 0;
        }
    }

    async getCourtData(courtId) {
        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/state`);
            if (!response.ok) return null;

            const data = await response.json();
            return data.courts.find(c => c.id == courtId);
        } catch (error) {
            console.error('Error getting court data:', error);
            return null;
        }
    }

    closeResultModal() {
        const resultModal = document.getElementById('resultModal');
        if (resultModal) {
            resultModal.style.display = 'none';
        }

        const resultIdField = document.getElementById('resultId');
        if (resultIdField) {
            resultIdField.remove();
        }
        this.isEditMode = false;
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
            if (!this.connected) {
                console.log('Polling for updates (WebSocket disconnected)');
                this.loadData();
            }
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
        if (finalResultsModal) {
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