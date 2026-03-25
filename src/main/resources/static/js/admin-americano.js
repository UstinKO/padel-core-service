/**
 * admin-americano.js
 * Управление страницей проведения Americano турнира.
 *
 * Зависимости: нет (vanilla JS).
 * Подключается в конце tournament.html (admin/americano/tournament.html).
 */

'use strict';

// ─── Навигация по раундам ────────────────────────────────────────────────────

let currentRoundIndex = 0;
const roundCards = Array.from(document.querySelectorAll('#roundsContainer [data-round-id]'));
const totalRoundsCount = roundCards.length;

const prevBtn  = document.getElementById('prevRound');
const nextBtn  = document.getElementById('nextRound');
const display  = document.getElementById('currentRoundDisplay');
const statusBadge = document.getElementById('currentRoundStatusBadge');

/**
 * Показывает раунд по индексу, обновляет кнопки, значок статуса и активную вкладку.
 */
function showRound(index) {
    roundCards.forEach((card, i) => {
        card.style.display = i === index ? 'block' : 'none';
    });
    currentRoundIndex = index;

    if (display) display.textContent = index + 1;
    if (prevBtn)  prevBtn.disabled  = index === 0;
    if (nextBtn)  nextBtn.disabled  = index === totalRoundsCount - 1;

    // Обновляем значок статуса в навигаторе
    if (statusBadge && roundCards[index]) {
        const status = roundCards[index].dataset.roundStatus || '';
        statusBadge.textContent = formatStatus(status);
        statusBadge.className = 'round-status-badge ' + status.toLowerCase().replace('_', '-');
    }

    // Обновляем активную вкладку
    document.querySelectorAll('.round-tab-btn').forEach((btn, i) => {
        if (i === index) {
            btn.classList.add('active');
        } else {
            btn.classList.remove('active');
        }
    });

    // Управляем видимостью кнопок старт/завершение
    updateRoundButtons(roundCards[index]);
}

function formatStatus(status) {
    const map = { PENDING: 'Pendiente', IN_PROGRESS: 'En curso', COMPLETED: 'Completado', CANCELLED: 'Cancelado' };
    return map[status] || status;
}

if (prevBtn) prevBtn.addEventListener('click', () => {
    if (currentRoundIndex > 0) showRound(currentRoundIndex - 1);
});
if (nextBtn) nextBtn.addEventListener('click', () => {
    if (currentRoundIndex < totalRoundsCount - 1) showRound(currentRoundIndex + 1);
});

// ─── Обновление кнопок управления раундом ───────────────────────────────────

function updateRoundButtons(card) {
    const startBtn    = document.getElementById('startRoundBtn');
    const completeBtn = document.getElementById('completeRoundBtn');
    if (!card) return;

    const status  = card.dataset.roundStatus;
    const roundId = card.dataset.roundId;

    if (startBtn) {
        startBtn.style.display = status === 'PENDING' ? '' : 'none';
        startBtn.dataset.roundId = roundId;
    }
    if (completeBtn) {
        completeBtn.style.display = status === 'IN_PROGRESS' ? '' : 'none';
        completeBtn.dataset.roundId = roundId;
    }
}

// ─── Старт раунда ────────────────────────────────────────────────────────────

async function startRound(button) {
    const roundId = button.dataset.roundId;
    if (!roundId) return;

    button.disabled = true;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Iniciando...';

    try {
        const resp = await fetch(`/api/tournaments/americano/rounds/${roundId}/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (resp.ok) {
            showToast('success', 'Ronda iniciada');
            location.reload();
        } else {
            const data = await resp.json().catch(() => ({}));
            showToast('error', data.message || 'Error al iniciar ronda');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-play"></i> Iniciar Ronda';
        }
    } catch {
        showToast('error', 'Error de conexión');
        button.disabled = false;
        button.innerHTML = '<i class="fas fa-play"></i> Iniciar Ronda';
    }
}

// ─── Завершение раунда ───────────────────────────────────────────────────────

async function completeRound(button) {
    const roundId = button.dataset.roundId;
    if (!roundId) return;

    if (!confirm('¿Finalizar esta ronda? Todos los partidos deben estar guardados.')) return;

    button.disabled = true;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Finalizando...';

    try {
        const resp = await fetch(`/api/tournaments/americano/rounds/${roundId}/complete`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
        });

        if (resp.ok) {
            showToast('success', 'Ronda completada');
            location.reload();
        } else {
            const data = await resp.json().catch(() => ({}));
            showToast('error', data.message || 'Error al completar ronda');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-flag-checkered"></i> Finalizar Ronda';
        }
    } catch {
        showToast('error', 'Error de conexión');
        button.disabled = false;
        button.innerHTML = '<i class="fas fa-flag-checkered"></i> Finalizar Ronda';
    }
}

// ─── Валидация суммы очков (п. 3 ТЗ) ────────────────────────────────────────

/**
 * Вызывается при изменении любого из двух input-ов счёта.
 * Активирует кнопку "Guardar" только если сумма == pointsPerMatch.
 */
function validateScoreSum(input) {
    const matchCard  = input.closest('[data-points-limit]');
    const matchId    = matchCard?.id?.replace('match-', '');
    if (!matchId) return;

    const limit  = parseInt(matchCard.dataset.pointsLimit || '32');
    const score1 = parseInt(document.getElementById(`score1-${matchId}`)?.value) || 0;
    const score2 = parseInt(document.getElementById(`score2-${matchId}`)?.value) || 0;
    const sum    = score1 + score2;
    const isOk   = sum === limit;

    const hintEl  = document.getElementById(`sum-hint-${matchId}`);
    const saveBtn = matchCard.querySelector('.submit-score-btn');

    // Обновляем подсказку
    if (hintEl) {
        hintEl.className = `score-sum-hint ${isOk ? 'ok' : 'bad'}`;
        hintEl.innerHTML = isOk
            ? `<i class="fas fa-check"></i> Correcto (${score1}+${score2}=${sum})`
            : `Suma actual: <strong>${sum}</strong> / necesaria: <strong>${limit}</strong>`;
    }

    // Подсвечиваем поля
    [document.getElementById(`score1-${matchId}`), document.getElementById(`score2-${matchId}`)]
        .forEach(el => el?.classList.toggle('invalid', !isOk));

    // Активируем кнопку
    if (saveBtn) saveBtn.disabled = !isOk;
}

// ─── Отправка результата матча ───────────────────────────────────────────────

async function submitScore(button) {
    const matchId = button.dataset.matchId;
    const limit   = parseInt(button.dataset.points || '32');

    const score1Input = document.getElementById(`score1-${matchId}`);
    const score2Input = document.getElementById(`score2-${matchId}`);
    if (!score1Input || !score2Input) return;

    const team1Score = parseInt(score1Input.value);
    const team2Score = parseInt(score2Input.value);

    // Финальная проверка суммы
    if (team1Score + team2Score !== limit) {
        showToast('error', `La suma de puntos debe ser ${limit}`);
        return;
    }

    button.disabled = true;
    const orig = button.innerHTML;
    button.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';

    try {
        const resp = await fetch(`/api/tournaments/americano/matches/${matchId}/result`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ matchId, team1Score, team2Score })
        });

        if (resp.ok) {
            showToast('success', 'Resultado guardado');
            // Переключаем форму → отображение счёта без перезагрузки
            replaceFormWithDisplay(matchId, team1Score, team2Score);
        } else {
            const data = await resp.json().catch(() => ({}));
            showToast('error', data.message || 'Error al guardar resultado');
            button.disabled = false;
            button.innerHTML = orig;
        }
    } catch {
        showToast('error', 'Error de conexión');
        button.disabled = false;
        button.innerHTML = orig;
    }
}

/**
 * Заменяет форму ввода на отображение счёта после сохранения.
 */
function replaceFormWithDisplay(matchId, team1Score, team2Score) {
    const formEl = document.getElementById(`score-form-${matchId}`);
    if (!formEl) { location.reload(); return; }

    const winner = team1Score > team2Score ? 'team1' : (team2Score > team1Score ? 'team2' : 'draw');
    formEl.outerHTML = `
        <div class="score-display" id="score-display-${matchId}">
            <span class="score-val ${winner === 'team1' ? 'winner' : ''}">${team1Score}</span>
            <span class="vs-badge">:</span>
            <span class="score-val ${winner === 'team2' ? 'winner' : ''}">${team2Score}</span>
            <button class="edit-score-btn"
                    data-match-id="${matchId}"
                    data-team1="${team1Score}"
                    data-team2="${team2Score}"
                    onclick="editScore(this)"
                    title="Editar resultado">
                <i class="fas fa-edit"></i>
            </button>
        </div>`;
}

// ─── Редактирование результата ───────────────────────────────────────────────

function editScore(button) {
    const matchId  = button.dataset.matchId;
    const t1       = button.dataset.team1 || '0';
    const t2       = button.dataset.team2 || '0';

    const matchCard = document.getElementById(`match-${matchId}`);
    const limit     = parseInt(matchCard?.dataset.pointsLimit || '32');
    const displayEl = document.getElementById(`score-display-${matchId}`);
    if (!displayEl) return;

    displayEl.outerHTML = `
        <div class="score-input-form" id="score-form-${matchId}">
            <div class="score-input-wrapper">
                <input type="number" class="score-input" id="score1-${matchId}"
                       value="${t1}" min="0" max="${limit}"
                       oninput="validateScoreSum(this)">
                <span class="vs-badge">VS</span>
                <input type="number" class="score-input" id="score2-${matchId}"
                       value="${t2}" min="0" max="${limit}"
                       oninput="validateScoreSum(this)">
            </div>
            <div class="score-sum-hint ok" id="sum-hint-${matchId}">
                <i class="fas fa-check"></i> Suma: ${parseInt(t1)+parseInt(t2)} / ${limit}
            </div>
            <button class="submit-score-btn"
                    data-match-id="${matchId}"
                    data-points="${limit}"
                    onclick="submitScore(this)">
                <i class="fas fa-check"></i> Guardar
            </button>
        </div>`;

    // Валидируем сразу
    const input = document.getElementById(`score1-${matchId}`);
    if (input) validateScoreSum(input);
}

// ─── Toast-уведомления ───────────────────────────────────────────────────────

function showToast(type, message) {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.style.cssText =
            'position:fixed;top:20px;right:20px;z-index:9999;display:flex;flex-direction:column;gap:8px';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    const bg = type === 'success' ? '#10b981' : type === 'error' ? '#ef4444' : '#3b82f6';
    const icon = type === 'success' ? 'fa-check-circle' : type === 'error' ? 'fa-exclamation-circle' : 'fa-info-circle';
    toast.style.cssText = `background:${bg};color:white;padding:10px 18px;border-radius:8px;
        box-shadow:0 4px 12px rgba(0,0,0,.15);display:flex;align-items:center;gap:8px;
        animation:slideInRight .25s ease;font-size:.9rem;max-width:320px`;
    toast.innerHTML = `<i class="fas ${icon}"></i><span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
        toast.style.animation = 'slideOutRight .25s ease';
        setTimeout(() => toast.remove(), 250);
    }, 3500);
}

// ─── CSS анимации для toast-ов ────────────────────────────────────────────────

const toastStyle = document.createElement('style');
toastStyle.textContent = `
    @keyframes slideInRight  { from { transform:translateX(110%);opacity:0 } to { transform:translateX(0);opacity:1 } }
    @keyframes slideOutRight { from { transform:translateX(0);opacity:1 } to { transform:translateX(110%);opacity:0 } }
`;
document.head.appendChild(toastStyle);

// ─── Инициализация при загрузке ──────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
    if (roundCards.length > 0) {
        // После reload — восстанавливаем раунд из sessionStorage
        const savedKey = 'americano_round_' + (window.location.pathname.match(/\/(\d+)(?:\/|$)/) || [])[1];
        const saved = sessionStorage.getItem(savedKey);
        let startIndex = 0;

        if (saved !== null && parseInt(saved) < roundCards.length) {
            startIndex = parseInt(saved);
        } else {
            // Открываем первый IN_PROGRESS, иначе первый PENDING, иначе последний
            let found = false;
            for (let i = 0; i < roundCards.length; i++) {
                const s = roundCards[i].dataset.roundStatus;
                if (s === 'IN_PROGRESS') { startIndex = i; found = true; break; }
            }
            if (!found) {
                for (let i = 0; i < roundCards.length; i++) {
                    const s = roundCards[i].dataset.roundStatus;
                    if (s === 'PENDING') { startIndex = i; found = true; break; }
                }
            }
            if (!found) startIndex = roundCards.length - 1;
        }
        showRound(startIndex);
    }

    // Назначаем обработчики на кнопки старт/завершение
    const startBtn = document.getElementById('startRoundBtn');
    if (startBtn) startBtn.addEventListener('click', () => startRound(startBtn));

    const completeBtn = document.getElementById('completeRoundBtn');
    if (completeBtn) completeBtn.addEventListener('click', () => completeRound(completeBtn));
});

// ─── Переключение через вкладки ──────────────────────────────────────────────

function switchRound(btn) {
    const idx = parseInt(btn.dataset.idx);
    showRound(idx);
    saveCurrentRound(idx);
}

function saveCurrentRound(idx) {
    const key = 'americano_round_' + (window.location.pathname.match(/\/(\d+)(?:\/|$)/) || [])[1];
    sessionStorage.setItem(key, idx !== undefined ? idx : currentRoundIndex);
}