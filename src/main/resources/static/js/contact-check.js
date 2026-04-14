const ContactCheck = (() => {
    'use strict';

    let pendingCallback = null;
    let pendingTournamentId = null;
    let pendingTournamentName = null;

    function ensureModal() {
        if (document.getElementById('contactCheckModal')) return;

        const html = `
        <div id="contactCheckModal"
             style="display:none; position:fixed; inset:0; z-index:9999;
                    background:rgba(0,0,0,.5); align-items:center; justify-content:center;">
            <div style="background:#fff; border-radius:12px; padding:2rem;
                        max-width:440px; width:90%; box-shadow:0 20px 60px rgba(0,0,0,.25);">

                <div style="display:flex; align-items:center; gap:.75rem; margin-bottom:1rem;">
                    <i class="fas fa-address-book" style="font-size:1.4rem; color:#FF6B35;"></i>
                    <h3 style="margin:0; font-size:1.1rem; color:#1e293b;">
                        Datos de contacto requeridos
                    </h3>
                </div>

                <p style="color:#64748b; font-size:.9rem; margin:0 0 1.25rem;">
                    Para inscribirte en <strong id="ccTournamentName">este torneo</strong>
                    necesitamos un dato de contacto. Completa al menos uno:
                </p>

                <div style="margin-bottom:1rem;">
                    <label style="display:block; font-size:.85rem; font-weight:600;
                                  color:#1e293b; margin-bottom:.35rem;">
                        <i class="fab fa-whatsapp" style="color:#25D366;"></i>
                        WhatsApp (Argentina, +54)
                    </label>
                    <input id="ccPhone" type="tel"
                           placeholder="+54 9 11 1234-5678"
                           style="width:100%; padding:.6rem .75rem; border:1px solid #e2e8f0;
                                  border-radius:8px; font-size:.95rem; box-sizing:border-box;">
                    <span id="ccPhoneError"
                          style="display:none; color:#ef4444; font-size:.78rem; margin-top:.25rem;">
                        El número debe comenzar con +54
                    </span>
                </div>

                <div style="margin-bottom:1.5rem;">
                    <label style="display:block; font-size:.85rem; font-weight:600;
                                  color:#1e293b; margin-bottom:.35rem;">
                        <i class="fab fa-telegram" style="color:#2CA5E0;"></i>
                        Telegram (usuario)
                    </label>
                    <input id="ccTelegram" type="text"
                           placeholder="@tu_usuario"
                           style="width:100%; padding:.6rem .75rem; border:1px solid #e2e8f0;
                                  border-radius:8px; font-size:.95rem; box-sizing:border-box;">
                    <span id="ccTgError"
                          style="display:none; color:#ef4444; font-size:.78rem; margin-top:.25rem;">
                        El usuario de Telegram debe comenzar con @
                    </span>
                </div>

                <div id="ccGlobalError"
                     style="display:none; background:#fee2e2; color:#991b1b;
                            border-radius:8px; padding:.6rem .9rem; font-size:.85rem;
                            margin-bottom:1rem;">
                </div>

                <div style="display:flex; gap:.75rem; justify-content:flex-end;">
                    <button id="ccCancelBtn"
                            style="padding:.55rem 1.1rem; border:1px solid #e2e8f0;
                                   border-radius:8px; background:#fff; cursor:pointer;
                                   font-size:.9rem; color:#64748b;">
                        Cancelar
                    </button>
                    <button id="ccSaveBtn"
                            style="padding:.55rem 1.25rem; border:none; border-radius:8px;
                                   background:#FF6B35; color:#fff; cursor:pointer;
                                   font-size:.9rem; font-weight:600; display:flex;
                                   align-items:center; gap:.5rem;">
                        <i class="fas fa-save"></i> Guardar e inscribirse
                    </button>
                </div>
            </div>
        </div>`;

        document.body.insertAdjacentHTML('beforeend', html);

        document.getElementById('contactCheckModal').addEventListener('click', e => {
            if (e.target === e.currentTarget) closeModal();
        });
        document.getElementById('ccCancelBtn').addEventListener('click', closeModal);
    }

    function closeModal() {
        const m = document.getElementById('contactCheckModal');
        if (m) m.style.display = 'none';
        pendingCallback = null;
        pendingTournamentId = null;
        pendingTournamentName = null;

        ['ccPhone', 'ccTelegram'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.value = '';
        });
        ['ccPhoneError', 'ccTgError', 'ccGlobalError'].forEach(id => {
            const el = document.getElementById(id);
            if (el) el.style.display = 'none';
        });
    }

    function showGlobalError(msg) {
        const el = document.getElementById('ccGlobalError');
        if (el) { el.textContent = msg; el.style.display = 'block'; }
    }

    async function beforeRegister(tournamentId, tournamentName, onReady) {
        try {
            const res = await fetch('/api/players/me/contact-status');
            if (!res.ok) {
                onReady();
                return;
            }
            const data = await res.json();

            // Контакты уже есть — сразу вызываем колбэк
            if (data.hasContact) {
                onReady();
                return;
            }

            // Контактов нет — показываем форму
            ensureModal();

            pendingCallback = onReady;
            pendingTournamentId = tournamentId;
            pendingTournamentName = tournamentName;

            document.getElementById('ccTournamentName').textContent = tournamentName;
            document.getElementById('contactCheckModal').style.display = 'flex';

            if (data.telefono) document.getElementById('ccPhone').value = data.telefono;
            if (data.telegramUsername) document.getElementById('ccTelegram').value = data.telegramUsername;

            const saveBtn = document.getElementById('ccSaveBtn');
            const newSaveBtn = saveBtn.cloneNode(true);
            saveBtn.parentNode.replaceChild(newSaveBtn, saveBtn);

            newSaveBtn.addEventListener('click', async () => {
                if (newSaveBtn.disabled) return;

                const phone = document.getElementById('ccPhone').value.trim();
                const telegram = document.getElementById('ccTelegram').value.trim();

                document.getElementById('ccPhoneError').style.display = 'none';
                document.getElementById('ccTgError').style.display = 'none';
                document.getElementById('ccGlobalError').style.display = 'none';

                if (!phone && !telegram) {
                    showGlobalError('Por favor, ingresa al menos un dato de contacto.');
                    return;
                }

                if (phone && !phone.startsWith('+54')) {
                    document.getElementById('ccPhoneError').style.display = 'block';
                    return;
                }

                if (telegram && !telegram.startsWith('@')) {
                    document.getElementById('ccTgError').style.display = 'block';
                    return;
                }

                newSaveBtn.disabled = true;
                newSaveBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Guardando...';

                try {
                    const patchRes = await fetch('/api/players/me/contact', {
                        method: 'PATCH',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({
                            telefono: phone || null,
                            telegramUsername: telegram || null
                        })
                    });

                    const patchData = await patchRes.json();

                    if (!patchData.success) {
                        showGlobalError(patchData.message || 'Error al guardar');
                        newSaveBtn.disabled = false;
                        newSaveBtn.innerHTML = '<i class="fas fa-save"></i> Guardar e inscribirse';
                        return;
                    }

                    // Сохраняем колбэк до closeModal (который его обнуляет)
                    const cb = pendingCallback;
                    closeModal();

                    if (cb) {
                        cb();
                    }

                } catch (err) {
                    console.error('Error saving contact:', err);
                    showGlobalError('Error de conexión. Inténtalo de nuevo.');
                    newSaveBtn.disabled = false;
                    newSaveBtn.innerHTML = '<i class="fas fa-save"></i> Guardar e inscribirse';
                }
            });

        } catch (err) {
            console.error('ContactCheck error:', err);
            onReady();
        }
    }

    return { beforeRegister };
})();