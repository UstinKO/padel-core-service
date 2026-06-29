/**
 * King of Court - Просмотр для игроков и зрителей
 */

class KingOfCourtViewer {
    constructor() {
        const data = window.tournamentData || {};

        this.kingId = data.kingId;
        this.tournamentId = data.tournamentId;
        this.currentRound = data.currentRound || 1;
        this.calibrationRounds = data.calibrationRounds || 3;
        this.maxCourts = data.maxCourts || 5;
        this.isFinished = data.isFinished || false;
        this.stompClient = null;
        this.connected = false;

        console.log('KingOfCourtViewer initialized:', {
            kingId: this.kingId,
            tournamentId: this.tournamentId
        });

        // Инициализация прямо в конструкторе
        this.initElements();
        this.loadData();
        this.connectWebSocket();
    }

    initElements() {
        // Сохраняем ссылки на элементы DOM
        this.courtsContainer = document.getElementById('courtsContainer');
        this.leaderboard = document.getElementById('leaderboard');
        this.matchHistory = document.getElementById('matchHistory');
        this.historyModal = document.getElementById('historyModal');
        this.historyPlayerName = document.getElementById('historyPlayerName');
        this.historyTableBody = document.getElementById('historyTableBody');

        // Добавляем обработчик для закрытия модального окна
        document.querySelectorAll('.modal-close').forEach(btn => {
            btn.addEventListener('click', () => this.closeHistoryModal());
        });
    }

    connectWebSocket() {
        if (typeof SockJS === 'undefined') {
            console.warn('SockJS not loaded');
            return;
        }

        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;

            this.stompClient.connect({}, () => {
                console.log('WebSocket connected');
                this.connected = true;

                this.stompClient.subscribe(`/topic/tournament/${this.kingId}`, (message) => {
                    const update = JSON.parse(message.body);
                    this.handleWebSocketUpdate(update);
                });
            }, (error) => {
                console.error('WebSocket error:', error);
                this.connected = false;
                setTimeout(() => this.connectWebSocket(), 5000);
            });
        } catch (error) {
            console.error('WebSocket connection error:', error);
        }
    }

    handleWebSocketUpdate(update) {
        console.log('WebSocket update:', update);
        // При любом обновлении просто перезагружаем данные
        this.loadData();
    }

    async loadData() {
        if (!this.kingId) {
            console.error('No kingId');
            return;
        }

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/state?t=${Date.now()}`);
            const data = await response.json();

            this.renderCourts(data.courts || []);
            this.renderLeaderboard(data.ranking || []);
            this.renderMatchHistory(data.history || []);
        } catch (error) {
            console.error('Error loading data:', error);
        }
    }

    renderCourts(courts) {
        if (!this.courtsContainer) return;

        this.courtsContainer.innerHTML = '';

        if (courts.length === 0) {
            this.courtsContainer.innerHTML = `<p class="text-center">${t('koc.viewer.no_courts')}</p>`;
            return;
        }

        courts.forEach(court => {
            const courtCard = this.createCourtCard(court);
            this.courtsContainer.appendChild(courtCard);
        });
    }

    createCourtCard(court) {
        const card = document.createElement('div');
        card.className = `court-card ${court.hasResult ? 'completed' : 'in-progress'}`;

        let content = `
            <div class="court-header">
                <h3>${t('koc.viewer.court')} ${court.courtNumber}</h3>
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
        } else if (court.teams && court.teams.length > 0) {
            const meId = window.tournamentData?.currentPlayerId;
            content += '<div class="court-teams">';
            court.teams.forEach(team => {
                const teamPlayers = team.players.map(p => {
                    const isMe = meId && p.id === meId;
                    return isMe
                        ? `${p.name}<i class="fas fa-circle-check" style="color:#1d9bf0;font-size:.85em;margin-left:.3rem;vertical-align:middle"></i>`
                        : p.name;
                }).join(' & ');
                content += `
                    <div class="team">
                        <span class="team-number">${t('koc.viewer.team')} ${team.teamNumber}</span>
                        <span class="team-players">${teamPlayers}</span>
                    </div>
                `;
            });
            content += '</div>';
        } else {
            content += `<p class="text-gray-500">${t('koc.viewer.court_free')}</p>`;
        }

        card.innerHTML = content;
        return card;
    }

    renderLeaderboard(ranking) {
        if (!this.leaderboard) return;

        this.leaderboard.innerHTML = '';

        if (ranking.length === 0) {
            this.leaderboard.innerHTML = `<tr><td colspan="6" class="text-center">${t('koc.viewer.no_data')}</td></tr>`;
            return;
        }

        const meId = window.tournamentData?.currentPlayerId;
        ranking.forEach(player => {
            const isMe = meId && player.playerId === meId;
            const row = document.createElement('tr');
            const badgeHtml = isMe ? '<i class="fas fa-circle-check" style="color:#1d9bf0;font-size:.85em;margin-left:.3rem;vertical-align:middle"></i>' : '';
            row.innerHTML = `
                <td><strong>#${player.rank || '-'}</strong></td>
                <td>
                    <a href="#" class="view-history-btn"
                       data-player-id="${player.playerId || ''}"
                       data-player-name="${player.playerName || ''}">
                        ${player.playerName || t('koc.viewer.unknown_player')}${badgeHtml}
                    </a>
                </td>
                <td><strong>${player.totalPoints || 0}</strong></td>
                <td><span class="bonus-points">+${player.bonusPoints || 0}</span></td>
                <td>${player.gamesPlayed || 0}</td>
                <td>${player.wins || 0}/${player.losses || 0}</td>
            `;
            this.leaderboard.appendChild(row);
        });

        // Добавляем обработчики для истории
        this.leaderboard.querySelectorAll('.view-history-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                this.openHistoryModal(btn.dataset.playerId, btn.dataset.playerName);
            });
        });
    }

    renderMatchHistory(history) {
        if (!this.matchHistory) return;

        this.matchHistory.innerHTML = '';

        if (history.length === 0) {
            this.matchHistory.innerHTML = `<li class="text-center">${t('koc.viewer.history_empty')}</li>`;
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
            this.matchHistory.appendChild(li);
        });
    }

    async openHistoryModal(playerId, playerName) {
        if (!this.historyPlayerName || !this.historyModal) return;

        this.historyPlayerName.textContent = playerName;

        try {
            const response = await fetch(`/api/king-of-court/tournaments/${this.kingId}/players/${playerId}/history`);
            const history = await response.json();

            if (this.historyTableBody) {
                this.historyTableBody.innerHTML = '';

                if (history.length === 0) {
                    this.historyTableBody.innerHTML = `<tr><td colspan="6" class="text-center">${t('koc.viewer.no_history')}</td></tr>`;
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
                            <td>${isWinner ?
                            (match.winners?.find(w => w !== playerName) || '') :
                            (match.losers?.find(l => l !== playerName) || '')}
                            </td>
                            <td>${isWinner ?
                            (match.losers?.join(' & ') || '') :
                            (match.winners?.join(' & ') || '')}
                            </td>
                        `;
                        this.historyTableBody.appendChild(row);
                    });
                }
            }

            this.historyModal.style.display = 'flex';
        } catch (error) {
            console.error('Error loading history:', error);
        }
    }

    closeHistoryModal() {
        if (this.historyModal) {
            this.historyModal.style.display = 'none';
        }
    }
}

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    window.kingOfCourtViewer = new KingOfCourtViewer();
});