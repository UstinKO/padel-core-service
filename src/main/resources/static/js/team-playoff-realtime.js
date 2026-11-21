/**
 * Team Playoff — WebSocket realtime клиент.
 * Подключается к /topic/team-playoff/{tournamentId} (STOMP/SockJS, endpoint /ws)
 * и рассылает полученные обновления как CustomEvent('team-playoff-update').
 * Конкретный рендеринг (сетка, доска кортов, таблица) реализуют потребители — см. T11/T12.
 */
class TeamPlayoffRealtime {
    constructor(tournamentId, onConnectionChange = () => {}) {
        this.tournamentId = tournamentId;
        this.onConnectionChange = onConnectionChange;
        this.stompClient = null;
        this.connected = false;

        if (this.tournamentId) {
            this.connect();
        }
    }

    connect() {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.warn('[team-playoff] SockJS/Stomp не загружены — realtime отключён');
            this.onConnectionChange(false);
            return;
        }

        try {
            const socket = new SockJS('/ws');
            this.stompClient = Stomp.over(socket);
            this.stompClient.debug = null;

            this.stompClient.connect({}, () => {
                this.connected = true;
                console.log(`[team-playoff] WebSocket подключен, подписка на турнир ${this.tournamentId}`);

                this.stompClient.subscribe(`/topic/team-playoff/${this.tournamentId}`, (message) => {
                    const update = JSON.parse(message.body);
                    console.log('[team-playoff] update:', update);
                    window.dispatchEvent(new CustomEvent('team-playoff-update', {detail: update}));
                });
                this.onConnectionChange(true);
            }, (error) => {
                console.error('[team-playoff] WebSocket error:', error);
                this.connected = false;
                this.onConnectionChange(false);
                setTimeout(() => this.connect(), 5000);
            });
        } catch (error) {
            console.error('[team-playoff] WebSocket connection error:', error);
            this.connected = false;
            this.onConnectionChange(false);
        }
    }
}

window.TeamPlayoffRealtime = TeamPlayoffRealtime;

function startTeamPlayoffPublicUpdates(tournamentId) {
    const notice = document.getElementById('liveUpdateNotice');
    let reloadTimer;
    let connected = false;

    function markStale() {
        clearTimeout(reloadTimer);
        notice.hidden = false;
    }

    function scheduleReload() {
        clearTimeout(reloadTimer);
        if (connected && navigator.onLine) {
            reloadTimer = setTimeout(() => window.location.reload(), 1500);
        }
    }

    window.addEventListener('team-playoff-update', scheduleReload);
    window.addEventListener('offline', markStale);
    window.addEventListener('online', scheduleReload);
    if (!navigator.onLine) markStale();

    new TeamPlayoffRealtime(tournamentId, (available) => {
        connected = available;
        if (!available) markStale();
        else if (!notice.hidden) scheduleReload();
    });
}
