/**
 * share-panel.js
 * Модальная панель шаринга для админских страниц.
 * Подключить в:
 *   - templates/admin/tournaments/king-of-court.html
 *   - templates/admin/americano/tournament.html
 *
 * <script th:src="@{/js/share-panel.js}"></script>
 */

function showSharePanel(tournamentName, publicUrl) {
    // Удаляем старую панель если есть
    const existing = document.getElementById('sharePanelOverlay');
    if (existing) existing.remove();

    const waText  = encodeURIComponent(`🎾 ¡Te invito a este torneo de pádel!\n*${tournamentName}*\n${publicUrl}`);
    const tgUrl   = encodeURIComponent(publicUrl);
    const tgText  = encodeURIComponent(`🎾 ${tournamentName}`);

    const html = `
    <div id="sharePanelOverlay"
         style="position:fixed; inset:0; z-index:9999; background:rgba(0,0,0,.5);
                display:flex; align-items:center; justify-content:center;"
         onclick="if(event.target===this) closeSharePanel()">
        <div style="background:#fff; border-radius:14px; padding:1.75rem;
                    max-width:420px; width:90%; box-shadow:0 20px 60px rgba(0,0,0,.25);">

            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1.25rem;">
                <h3 style="margin:0; font-size:1.05rem; color:#1e293b;">
                    <i class="fas fa-share-alt" style="color:#FF6B35;"></i>
                    Compartir torneo
                </h3>
                <button onclick="closeSharePanel()"
                        style="background:none; border:none; cursor:pointer; color:#94a3b8; font-size:1.2rem;">
                    <i class="fas fa-times"></i>
                </button>
            </div>

            <!-- Поле с URL -->
            <div style="display:flex; gap:.5rem; margin-bottom:1.25rem;">
                <input id="sharePanelUrl" type="text" readonly value="${publicUrl}"
                       style="flex:1; padding:.55rem .75rem; border:1px solid #e2e8f0;
                              border-radius:8px; font-size:.85rem; color:#475569;
                              background:#f8fafc;">
                <button onclick="copyShareLink()"
                        style="padding:.55rem .9rem; background:#f1f5f9; border:1px solid #e2e8f0;
                               border-radius:8px; cursor:pointer; color:#475569; font-size:.85rem;
                               white-space:nowrap;" id="copyShareBtn">
                    <i class="fas fa-copy"></i> Copiar
                </button>
            </div>

            <!-- Кнопки соцсетей -->
            <div style="display:flex; flex-direction:column; gap:.6rem;">

                <a href="https://wa.me/?text=${waText}"
                   target="_blank" rel="noopener noreferrer"
                   style="display:flex; align-items:center; gap:.65rem; padding:.7rem 1rem;
                          background:#25D366; color:#fff; border-radius:8px;
                          text-decoration:none; font-weight:600; font-size:.9rem;">
                    <i class="fab fa-whatsapp" style="font-size:1.15rem;"></i>
                    Enviar por WhatsApp
                </a>

                <a href="https://t.me/share/url?url=${tgUrl}&text=${tgText}"
                   target="_blank" rel="noopener noreferrer"
                   style="display:flex; align-items:center; gap:.65rem; padding:.7rem 1rem;
                          background:#2CA5E0; color:#fff; border-radius:8px;
                          text-decoration:none; font-weight:600; font-size:.9rem;">
                    <i class="fab fa-telegram" style="font-size:1.15rem;"></i>
                    Enviar por Telegram
                </a>

                <button onclick="copyShareLink()"
                        style="display:flex; align-items:center; gap:.65rem; padding:.7rem 1rem;
                               background:#f1f5f9; color:#1e293b; border:1px solid #e2e8f0;
                               border-radius:8px; font-weight:600; font-size:.9rem;
                               cursor:pointer; width:100%;" id="copyShareBtn2">
                    <i class="fas fa-link" style="font-size:1rem;"></i>
                    Copiar enlace para Instagram / redes
                </button>
            </div>

            <p style="margin:.9rem 0 0; font-size:.78rem; color:#94a3b8; text-align:center;">
                El enlace lleva directamente a la página del torneo para jugadores
            </p>
        </div>
    </div>`;

    document.body.insertAdjacentHTML('beforeend', html);
}

function closeSharePanel() {
    const el = document.getElementById('sharePanelOverlay');
    if (el) el.remove();
}

function copyShareLink() {
    const input = document.getElementById('sharePanelUrl');
    if (!input) return;
    navigator.clipboard.writeText(input.value).then(() => {
        const btns = [
            document.getElementById('copyShareBtn'),
            document.getElementById('copyShareBtn2')
        ];
        btns.forEach(btn => {
            if (!btn) return;
            const orig = btn.innerHTML;
            btn.innerHTML = btn.id === 'copyShareBtn'
                ? '<i class="fas fa-check"></i> ¡Copiado!'
                : '<i class="fas fa-check" style="font-size:1rem;"></i> ¡Enlace copiado!';
            btn.style.background = '#dcfce7';
            btn.style.borderColor = '#86efac';
            setTimeout(() => {
                btn.innerHTML = orig;
                btn.style.background = '';
                btn.style.borderColor = '';
            }, 2500);
        });
    });
}