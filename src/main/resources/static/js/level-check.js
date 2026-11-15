const LevelCheck = (() => {
    'use strict';

    const STORAGE_KEY = 'pendingLevelRegistrationTournamentId';

    function ensureModal() {
        if (document.getElementById('levelCheckModal')) return;

        const html = `
        <div id="levelCheckModal"
             style="display:none; position:fixed; inset:0; z-index:9999;
                    background:rgba(0,0,0,.5); align-items:center; justify-content:center;">
            <div style="background:#fff; border-radius:12px; padding:2rem;
                        max-width:420px; width:90%; box-shadow:0 20px 60px rgba(0,0,0,.25);">

                <div style="display:flex; align-items:center; gap:.75rem; margin-bottom:1rem;">
                    <i class="fas fa-medal" style="font-size:1.4rem; color:#FF6B35;"></i>
                    <h3 style="margin:0; font-size:1.1rem; color:#1e293b;">
                        ${t('level.modal.title')}
                    </h3>
                </div>

                <p style="color:#64748b; font-size:.9rem; margin:0 0 1.5rem;">
                    ${t('level.modal.desc')}
                </p>

                <div style="display:flex; gap:.75rem; justify-content:flex-end;">
                    <button id="lcCancelBtn"
                            style="padding:.55rem 1.1rem; border:1px solid #e2e8f0;
                                   border-radius:8px; background:#fff; cursor:pointer;
                                   font-size:.9rem; color:#64748b;">
                        ${t('level.btn.cancel')}
                    </button>
                    <a id="lcGoToProfileBtn" href="/perfil"
                       style="padding:.55rem 1.25rem; border:none; border-radius:8px;
                              background:#FF6B35; color:#fff; cursor:pointer;
                              font-size:.9rem; font-weight:600; text-decoration:none;
                              display:flex; align-items:center; gap:.5rem;">
                        <i class="fas fa-user-edit"></i> ${t('level.btn.go')}
                    </a>
                </div>
            </div>
        </div>`;

        document.body.insertAdjacentHTML('beforeend', html);

        document.getElementById('levelCheckModal').addEventListener('click', e => {
            if (e.target === e.currentTarget) closeModal();
        });
        document.getElementById('lcCancelBtn').addEventListener('click', closeModal);
    }

    function closeModal() {
        const m = document.getElementById('levelCheckModal');
        if (m) m.style.display = 'none';
    }

    // Сохраняет турнир, на который не удалась регистрация, чтобы /perfil мог предложить
    // вернуться к ней после сохранения уровня. sessionStorage может быть недоступен
    // (приватный режим) — это не должно мешать показать попап.
    function rememberTournament(tournamentId) {
        try {
            sessionStorage.setItem(STORAGE_KEY, tournamentId);
        } catch (e) {
            console.warn('LevelCheck: no se pudo guardar sessionStorage', e);
        }
    }

    function showInline(dialog, onSaved) {
        if (dialog.open) return;

        const form = document.getElementById('registrationLevelForm');
        const select = document.getElementById('registrationLevel');
        const save = document.getElementById('registrationLevelSave');
        const cancel = document.getElementById('registrationLevelCancel');
        const error = document.getElementById('registrationLevelError');

        form.reset();
        error.hidden = true;
        document.getElementById('registrationLevelTitle').textContent = t('level.modal.title');
        document.getElementById('registrationLevelDescription').textContent = t('level.registration.desc');
        select.options[0].textContent = t('level.registration.select');
        save.textContent = t('level.registration.save');
        cancel.textContent = t('level.btn.cancel');
        cancel.onclick = () => dialog.close();
        dialog.oncancel = event => {
            if (save.disabled) event.preventDefault();
        };
        form.onsubmit = async event => {
            event.preventDefault();
            if (save.disabled || !select.value) return;

            const nivelJugador = select.value;
            save.disabled = cancel.disabled = select.disabled = true;
            error.hidden = true;
            try {
                const response = await fetch('/api/players/me/level', {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ nivelJugador })
                });
                if (!response.ok) throw new Error();
            } catch {
                error.textContent = t('level.registration.error');
                error.hidden = false;
                return;
            } finally {
                save.disabled = cancel.disabled = select.disabled = false;
            }
            dialog.close();
            onSaved?.();
        };

        dialog.showModal();
        select.focus();
    }

    function show(tournamentId, onSaved) {
        const inlineDialog = document.getElementById('registrationLevelDialog');
        if (inlineDialog) {
            showInline(inlineDialog, onSaved);
            return;
        }
        ensureModal();
        rememberTournament(tournamentId);
        document.getElementById('levelCheckModal').style.display = 'flex';
    }

    return { show, STORAGE_KEY };
})();
