/**
 * Team Playoff — WebSocket realtime клиент.
 * Подключается к /topic/team-playoff/{tournamentId} (STOMP/SockJS, endpoint /ws)
 * и рассылает полученные обновления как CustomEvent('team-playoff-update').
 * Конкретный рендеринг (сетка, доска кортов, таблица) реализуют потребители — см. T11/T12.
 */
class TeamPlayoffRealtime {
    constructor(tournamentId) {
        this.tournamentId = tournamentId;
        this.stompClient = null;
        this.connected = false;

        if (this.tournamentId) {
            this.connect();
        }
    }

    connect() {
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.warn('[team-playoff] SockJS/Stomp не загружены — realtime отключён');
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
            }, (error) => {
                console.error('[team-playoff] WebSocket error:', error);
                this.connected = false;
                setTimeout(() => this.connect(), 5000);
            });
        } catch (error) {
            console.error('[team-playoff] WebSocket connection error:', error);
        }
    }
}

window.TeamPlayoffRealtime = TeamPlayoffRealtime;
