// src/main/resources/static/js/tournament-details.js

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    console.log('✅ Tournament details JS loaded');
    console.log('Tournament data:', window.tournament);

    // ===== БУРГЕР-МЕНЮ =====
    const navbarToggler = document.getElementById('navbarToggler');
    const navbarNav = document.getElementById('navbarNav');

    if (navbarToggler && navbarNav) {
        console.log('✅ Tournament details: найдены элементы меню');

        const newToggler = navbarToggler.cloneNode(true);
        navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

        newToggler.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            navbarNav.classList.toggle('show');
        });

        navbarNav.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navbarNav.classList.remove('show');
            });
        });

        document.addEventListener('click', (e) => {
            if (navbarNav.classList.contains('show') &&
                !navbarNav.contains(e.target) &&
                !newToggler.contains(e.target)) {
                navbarNav.classList.remove('show');
            }
        });
    }

    // ===== МОДАЛЬНОЕ ОКНО ДЛЯ ПАРНЫХ ТУРНИРОВ =====
    const partnerModal = document.getElementById('partnerModal');
    const modalClose = document.querySelectorAll('[data-dismiss="modal"]');
    const submitPartnerBtn = document.getElementById('submitPartnerBtn');
    const partnerForm = document.getElementById('partnerForm');
    const partnerInfoMessage = document.getElementById('partnerInfoMessage');
    const infoMessageText = document.getElementById('infoMessageText');

    // Функция для проверки, является ли турнир парным
    function isDoubleTournament() {
        if (!window.tournament) {
            console.error('❌ Tournament data not found!');
            return false;
        }

        const modalidad = window.tournament.modalidad;
        console.log('Tournament modalidad:', modalidad);

        const isDouble = modalidad === 'DOBLES' ||
            modalidad === 'Dobles' ||
            modalidad === 'dobles';

        console.log('Is double tournament?', isDouble);
        return isDouble;
    }

    // Закрытие модального окна
    if (modalClose) {
        modalClose.forEach(btn => {
            btn.addEventListener('click', closeModal);
        });
    }

    window.addEventListener('click', (e) => {
        if (e.target === partnerModal) {
            closeModal();
        }
    });

    function closeModal() {
        if (partnerModal) {
            partnerModal.classList.remove('show');
            if (partnerForm) partnerForm.reset();
            clearSelectedPartner();
            hideInfoMessage();
            document.querySelectorAll('.form-control').forEach(input => {
                input.classList.remove('is-invalid');
            });
            setRegistrationMode('WITH_PARTNER');
        }
    }

    // ===== РЕЖИМЫ РЕГИСТРАЦИИ =====
    window.setRegistrationMode = function(mode) {
        document.getElementById('registrationMode').value = mode;

        const withPartnerSection = document.getElementById('withPartnerSection');
        const searchSection      = document.getElementById('searchSection');
        const addLaterSection    = document.getElementById('addLaterSection');
        const submitBtnText      = document.getElementById('submitPartnerBtnText');
        const modeWithPartner    = document.getElementById('modeWithPartner');
        const modeSearch         = document.getElementById('modeSearch');
        const modeLater          = document.getElementById('modeLater');

        [modeWithPartner, modeSearch, modeLater].forEach(b => b && b.classList.remove('mode-btn--active'));

        if (mode === 'WITH_PARTNER') {
            withPartnerSection && (withPartnerSection.style.display = '');
            searchSection      && (searchSection.style.display      = 'none');
            addLaterSection    && (addLaterSection.style.display     = 'none');
            if (submitBtnText) submitBtnText.textContent = t('details.mode.register_pair');
            modeWithPartner && modeWithPartner.classList.add('mode-btn--active');
        } else if (mode === 'SEARCH') {
            withPartnerSection && (withPartnerSection.style.display = 'none');
            searchSection      && (searchSection.style.display      = '');
            addLaterSection    && (addLaterSection.style.display     = 'none');
            if (submitBtnText) submitBtnText.textContent = t('details.mode.search');
            modeSearch && modeSearch.classList.add('mode-btn--active');
        } else if (mode === 'ADD_LATER') {
            withPartnerSection && (withPartnerSection.style.display = 'none');
            searchSection      && (searchSection.style.display      = 'none');
            addLaterSection    && (addLaterSection.style.display     = '');
            if (submitBtnText) submitBtnText.textContent = t('details.mode.later');
            modeLater && modeLater.classList.add('mode-btn--active');
        }
    };

    // ===== ПОИСК ПАРТНЁРА =====
    const partnerSearch = document.getElementById('partnerSearch');
    const partnerSearchResults = document.getElementById('partnerSearchResults');
    const selectedPartnerCard = document.getElementById('selectedPartnerCard');
    const selectedPartnerIdInput = document.getElementById('selectedPartnerId');
    const clearPartnerBtn = document.getElementById('clearPartnerBtn');

    let searchDebounceTimer = null;

    const manualFields = document.getElementById('manualPartnerFields');

    function clearSelectedPartner() {
        if (selectedPartnerIdInput) selectedPartnerIdInput.value = '';
        if (selectedPartnerCard) selectedPartnerCard.style.display = 'none';
        if (partnerSearch) partnerSearch.value = '';
        if (partnerSearchResults) partnerSearchResults.style.display = 'none';
        if (manualFields) manualFields.style.display = '';
    }

    function selectPartner(player) {
        selectedPartnerIdInput.value = player.id;
        document.getElementById('selectedPartnerName').textContent  = player.nombreCompleto;
        document.getElementById('selectedPartnerEmail').textContent = player.email;
        document.getElementById('partnerFirstName').value = player.nombre  || '';
        document.getElementById('partnerLastName').value  = player.apellido || '';
        document.getElementById('partnerEmail').value     = player.email   || '';
        document.getElementById('partnerPhone').value     = '';
        selectedPartnerCard.style.display  = 'flex';
        partnerSearchResults.style.display = 'none';
        partnerSearch.value = '';
        if (manualFields) manualFields.style.display = 'none';
    }

    if (clearPartnerBtn) {
        clearPartnerBtn.addEventListener('click', () => {
            clearSelectedPartner();
            document.getElementById('partnerFirstName').value = '';
            document.getElementById('partnerLastName').value  = '';
            document.getElementById('partnerEmail').value     = '';
            document.getElementById('partnerPhone').value     = '';
        });
    }

    if (partnerSearch) {
        partnerSearch.addEventListener('input', () => {
            clearTimeout(searchDebounceTimer);
            const query = partnerSearch.value.trim();
            if (query.length < 2) {
                partnerSearchResults.style.display = 'none';
                return;
            }
            searchDebounceTimer = setTimeout(async () => {
                const tournamentId = document.getElementById('modalTournamentId').value;
                try {
                    const res = await fetch(
                        `/api/players/search?query=${encodeURIComponent(query)}&tournamentId=${tournamentId}`
                    );
                    if (!res.ok) return;
                    const players = await res.json();
                    renderSearchResults(players);
                } catch (e) {
                    partnerSearchResults.style.display = 'none';
                }
            }, 300);
        });

        partnerSearch.addEventListener('blur', () => {
            setTimeout(() => { partnerSearchResults.style.display = 'none'; }, 200);
        });

        partnerSearch.addEventListener('focus', () => {
            if (partnerSearchResults.children.length > 0) {
                positionDropdown();
                partnerSearchResults.style.display = 'block';
            }
        });
    }

    // Пересчитываем позицию при скролле модала
    const partnerModalBody = document.querySelector('#partnerModal .modal-body');
    if (partnerModalBody) {
        partnerModalBody.addEventListener('scroll', () => {
            if (partnerSearchResults.style.display !== 'none') {
                positionDropdown();
            }
        });
    }

    function positionDropdown() {
        const rect = partnerSearch.getBoundingClientRect();
        const gap = 4;
        const maxH = 200;
        const spaceBelow = window.innerHeight - rect.bottom - gap;
        const spaceAbove = rect.top - gap;

        partnerSearchResults.style.width = rect.width + 'px';
        partnerSearchResults.style.left  = rect.left + 'px';

        if (spaceBelow >= Math.min(maxH, 80) || spaceBelow >= spaceAbove) {
            partnerSearchResults.style.top    = (rect.bottom + gap) + 'px';
            partnerSearchResults.style.bottom = 'auto';
        } else {
            partnerSearchResults.style.top    = 'auto';
            partnerSearchResults.style.bottom = (window.innerHeight - rect.top + gap) + 'px';
        }
    }

    function renderSearchResults(players) {
        partnerSearchResults.innerHTML = '';
        if (!players.length) {
            partnerSearchResults.innerHTML =
                `<li style="padding:.6rem 1rem; color:#9ca3af; font-size:.9rem;">${t('details.search.no_results')}</li>`;
        } else {
            players.forEach(p => {
                const li = document.createElement('li');
                li.style.cssText = 'padding:.6rem 1rem; cursor:pointer; border-bottom:1px solid #f3f4f6;';
                li.innerHTML = `
                    <div style="font-weight:500;">${p.nombreCompleto}</div>
                    <div style="font-size:.82rem; color:#6b7280;">${p.email}${p.nivelJugador ? ' · ' + p.nivelJugador : ''}</div>
                `;
                li.addEventListener('mousedown', () => selectPartner(p));
                li.addEventListener('mouseover', () => { li.style.background = '#f9fafb'; });
                li.addEventListener('mouseout', () => { li.style.background = ''; });
                partnerSearchResults.appendChild(li);
            });
        }
        positionDropdown();
        partnerSearchResults.style.display = 'block';
    }

    function showModal() {
        if (partnerModal) {
            partnerModal.classList.add('show');
        }
    }

    function showInfoMessage(message, type = 'info') {
        if (partnerInfoMessage && infoMessageText) {
            infoMessageText.textContent = message;
            partnerInfoMessage.style.display = 'flex';
            const icon = partnerInfoMessage.querySelector('i');
            if (icon) {
                icon.style.color = type === 'warning' ? '#ffc107' :
                    type === 'success' ? '#28a745' :
                        '#FF6B35';
            }
        }
    }

    function hideInfoMessage() {
        if (partnerInfoMessage) {
            partnerInfoMessage.style.display = 'none';
        }
    }

    function showResultModal(type, title, message) {
        const resultModal = document.getElementById('resultModal');
        const resultModalBody = document.getElementById('resultModalBody');

        if (!resultModal || !resultModalBody) return;

        let icon = '';
        switch(type) {
            case 'success':
                icon = '<i class="fas fa-check-circle" style="color: #28a745; font-size: 3rem;"></i>';
                break;
            case 'error':
                icon = '<i class="fas fa-times-circle" style="color: #dc3545; font-size: 3rem;"></i>';
                break;
            default:
                icon = '<i class="fas fa-info-circle" style="color: #FF6B35; font-size: 3rem;"></i>';
        }

        resultModalBody.innerHTML = `
            ${icon}
            <h4 style="margin: 16px 0 8px;">${title}</h4>
            <p style="color: #6c757d; margin: 0;">${message}</p>
        `;

        resultModal.classList.add('show');

        if (type === 'success') {
            setTimeout(() => {
                resultModal.classList.remove('show');
                window.location.reload();
            }, 3000);
        }
    }

    // ===== КНОПКИ РЕГИСТРАЦИИ =====
    const registerBtn = document.querySelector('.btn-register');
    const cancelBtn = document.querySelector('.btn-cancel');

    if (registerBtn) {
        console.log('✅ Register button found');

        const newRegisterBtn = registerBtn.cloneNode(true);
        registerBtn.parentNode.replaceChild(newRegisterBtn, registerBtn);

        newRegisterBtn.addEventListener('click', function(event) {
            const tournamentId = this.dataset.tournamentId;
            const tournamentName = this.dataset.tournamentName;

            console.log('Register clicked for tournament:', tournamentId, tournamentName);
            console.log('Tournament modalidad:', window.tournament?.modalidad);

            // Проверяем, парный ли турнир
            if (isDoubleTournament()) {
                console.log('🎾 Double tournament - checking contacts first');
                event.preventDefault();
                event.stopPropagation();

                // Сначала проверяем контактные данные игрока,
                // и только после — открываем форму регистрации пары.
                ContactCheck.beforeRegister(tournamentId, tournamentName, () => {
                    const modalTournamentId = document.getElementById('modalTournamentId');
                    const modalTournamentName = document.getElementById('modalTournamentName');

                    if (modalTournamentId) modalTournamentId.value = tournamentId;
                    if (modalTournamentName) modalTournamentName.textContent = tournamentName;

                    showModal();
                });
            } else {
                console.log('🎾 Single tournament - using ContactCheck');
                event.preventDefault();
                event.stopPropagation();

                // ВСЯ ЛОГИКА РЕГИСТРАЦИИ В ContactCheck
                if (typeof ContactCheck !== 'undefined' && ContactCheck.beforeRegister) {
                    ContactCheck.beforeRegister(tournamentId, tournamentName, async () => {
                        // ContactCheck вызовет этот колбэк когда контакты есть
                        // ИЛИ после сохранения контактов
                        const btn = newRegisterBtn;
                        btn.disabled = true;
                        btn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${t('details.processing')}`;

                        try {
                            const response = await fetch(`/players/tournaments/${tournamentId}/register`, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' }
                            });

                            const data = await response.json();

                            if (data.success) {
                                const msg = data.status === 'CONFIRMED' ? t('details.success.confirmed') : t('details.success.waitlist_added');
                                showResultModal('success', t('details.success.registered'), msg);
                            } else {
                                btn.disabled = false;
                                btn.innerHTML = window.tournament?.inscritosActuales >= window.tournament?.cupoMax ?
                                    `<i class="fas fa-clock"></i> ${t('details.btn.waitlist')}` :
                                    `<i class="fas fa-check-circle"></i> ${t('details.btn.register')}`;

                                const modalBody = document.getElementById('resultModalBody');
                                const resultModal = document.getElementById('resultModal');
                                if (modalBody && resultModal) {
                                    modalBody.innerHTML = `
                                        <i class="fas fa-exclamation-circle"
                                           style="font-size:48px; color:#dc3545; margin-bottom:15px;"></i>
                                        <p style="font-size:16px; margin:0;">${data.message}</p>`;
                                    resultModal.style.display = 'block';
                                    resultModal.classList.add('show');
                                }
                            }
                        } catch (error) {
                            console.error('Error registering:', error);
                            btn.disabled = false;
                            btn.innerHTML = window.tournament?.inscritosActuales >= window.tournament?.cupoMax ?
                                `<i class="fas fa-clock"></i> ${t('details.btn.waitlist')}` :
                                `<i class="fas fa-check-circle"></i> ${t('details.btn.register')}`;
                        }
                    });
                } else {
                    console.error('❌ ContactCheck not available');
                }
            }
        });
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', handleCancellation);
    }

    // Обработка отправки формы парного турнира
    if (submitPartnerBtn) {
        submitPartnerBtn.addEventListener('click', handleDoubleRegistration);
    }

    document.querySelectorAll('#partnerForm .form-control').forEach(input => {
        input.addEventListener('input', function() {
            if (this.classList.contains('is-invalid')) {
                this.classList.remove('is-invalid');
            }
        });
    });

    async function handleDoubleRegistration() {
        const tournamentId = document.getElementById('modalTournamentId').value;
        const mode = document.getElementById('registrationMode').value;

        submitPartnerBtn.disabled = true;
        submitPartnerBtn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${t('details.processing')}`;
        hideInfoMessage();

        try {
            // Соло-регистрация (SEARCH или ADD_LATER)
            if (mode === 'SEARCH' || mode === 'ADD_LATER') {
                const shareContacts = mode === 'SEARCH'
                    && document.getElementById('shareContactsCheckbox')?.checked;

                const response = await fetch(`/api/tournaments/double/${tournamentId}/register-solo`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ soloType: mode, shareContacts: !!shareContacts })
                });
                const data = await response.json();

                if (response.ok) {
                    closeModal();
                    loadLookingForPartner(tournamentId);
                    const soloMsg = mode === 'SEARCH' ? t('details.success.solo_search') : t('details.success.solo_later');
                    showResultModal('success', t('details.success.ready'), soloMsg);
                } else {
                    showInfoMessage(data.message || t('details.error.process'), 'error');
                    submitPartnerBtn.disabled = false;
                    submitPartnerBtn.innerHTML = `<i class="fas fa-check-circle"></i> <span id="submitPartnerBtnText">${t('details.mode.register_pair')}</span>`;
                }
                return;
            }

            // Режим WITH_PARTNER
            const firstName = document.getElementById('partnerFirstName').value.trim();
            const lastName  = document.getElementById('partnerLastName').value.trim();
            const phone     = document.getElementById('partnerPhone').value.trim();
            const email     = document.getElementById('partnerEmail').value.trim();
            const existingUserId = document.getElementById('selectedPartnerId').value;

            let isValid = true;
            if (!existingUserId) {
                if (!firstName) { document.getElementById('partnerFirstName').classList.add('is-invalid'); isValid = false; }
                if (!lastName)  { document.getElementById('partnerLastName').classList.add('is-invalid');  isValid = false; }
                const phoneRegex = /^\+?[0-9\s\-\(\)]{8,20}$/;
                if (!phone || !phoneRegex.test(phone)) { document.getElementById('partnerPhone').classList.add('is-invalid'); isValid = false; }
            }
            if (email) {
                const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
                if (!emailRegex.test(email)) { document.getElementById('partnerEmail').classList.add('is-invalid'); isValid = false; }
            }

            if (!isValid) {
                showInfoMessage(t('details.error.required_fields'), 'warning');
                submitPartnerBtn.disabled = false;
                submitPartnerBtn.innerHTML = `<i class="fas fa-check-circle"></i> <span id="submitPartnerBtnText">${t('details.mode.register_pair')}</span>`;
                return;
            }

            const partnerData = {
                nombre: firstName, apellido: lastName,
                telefono: phone || null, email: email || null,
                isExistingUser: !!existingUserId,
                existingUserId: existingUserId ? parseInt(existingUserId) : null
            };

            const response = await fetch(`/api/tournaments/double/${tournamentId}/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(partnerData)
            });
            const data = await response.json();

            if (response.ok) {
                closeModal();
                let message = '';
                if (data.status === 'PARTNER_INVITED') message = t('details.success.partner_invited');
                else if (data.status === 'CONFIRMED')  message = t('details.success.partner_confirmed');
                else if (data.status === 'WAITLIST')   message = t('details.success.waitlist_pair');
                else message = t('details.success.pair_complete');
                showResultModal('success', t('details.success.registered'), message);
            } else {
                if (data.message && data.message.includes('Ya estás registrado')) {
                    showResultModal('info', t('details.already_registered'), t('details.already_registered'));
                    closeModal();
                } else {
                    showInfoMessage(data.message || t('details.error.process'), 'error');
                }
                submitPartnerBtn.disabled = false;
                submitPartnerBtn.innerHTML = `<i class="fas fa-check-circle"></i> <span id="submitPartnerBtnText">${t('details.mode.register_pair')}</span>`;
            }
        } catch (error) {
            console.error('Error registering double tournament:', error);
            showInfoMessage(t('common.error.connection'), 'error');
            submitPartnerBtn.disabled = false;
            submitPartnerBtn.innerHTML = `<i class="fas fa-check-circle"></i> <span id="submitPartnerBtnText">${t('details.mode.register_pair')}</span>`;
        }
    }

    // ===== БЛОК «ИЩУ ПАРТНЁРА» =====
    function loadLookingForPartner(tournamentId) {
        const block = document.getElementById('lookingForPartnerBlock');
        const list  = document.getElementById('lookingForPartnerList');
        if (!block || !list) return;

        const currentUserIsSolo    = block.dataset.currentUserIsSolo === 'true';
        const currentUserSoloRegId = block.dataset.currentUserSoloRegId;
        const currentUserPlayerId  = block.dataset.currentUserPlayerId;

        fetch(`/api/tournaments/double/${tournamentId}/looking-for-partner`)
            .then(r => r.json())
            .then(players => {
                if (!players.length) {
                    block.style.display = 'none';
                    return;
                }
                block.style.display = '';
                list.innerHTML = players.map(p => {
                    const isOwnCard = String(p.playerId) === String(currentUserPlayerId);
                    const tgUsername = p.telegramUsername ? p.telegramUsername.replace(/^@/, '') : null;
                    const hasTg  = !!tgUsername;
                    const hasWa  = !!p.telefono;
                    let contacts = '';
                    if (p.shareContacts) {
                        if (hasTg || hasWa) {
                            contacts = `<div class="lp-card-contacts">
                                ${hasTg ? `<a class="lp-contact-btn lp-contact-btn--tg" href="https://t.me/${tgUsername}" target="_blank"><i class="fab fa-telegram"></i> Telegram</a>` : ''}
                                ${hasWa ? `<a class="lp-contact-btn lp-contact-btn--wa" href="https://wa.me/${p.telefono.replace(/\D/g,'')}" target="_blank"><i class="fab fa-whatsapp"></i> WhatsApp</a>` : ''}
                            </div>`;
                        } else if (isOwnCard) {
                            contacts = `<div class="lp-card-contacts">
                                <button class="lp-no-contact-hint" tabindex="0"
                                    aria-label="${t('details.no_contact.hint')}">
                                    <i class="fas fa-exclamation-circle"></i>
                                    <span class="lp-no-contact-tooltip">
                                        ${t('details.no_contact.hint')}
                                    </span>
                                </button>
                            </div>`;
                        }
                    }
                    const proposeBtn = (!isOwnCard && currentUserIsSolo && currentUserSoloRegId)
                        ? `<button class="lp-propose-btn"
                                   data-reg-id="${p.registrationId}"
                                   data-player-name="${escapeHtml(p.playerName)}"
                                   onclick="proposePair(${tournamentId}, ${p.registrationId}, '${escapeHtml(p.playerName)}', this)">
                               <i class="fas fa-handshake"></i> ${t('details.proposal.btn')}
                           </button>`
                        : '';
                    const nivel = p.nivel ? `<span class="lp-card-nivel">${p.nivel}</span>` : '';
                    return `<div class="lp-card">
                        <i class="fas fa-user-circle" style="color:#3b82f6; font-size:1.2rem;"></i>
                        <div style="flex:1;">
                            <div class="lp-card-name">${escapeHtml(p.playerName)}</div>
                            ${nivel}
                        </div>
                        ${contacts}
                        ${proposeBtn}
                    </div>`;
                }).join('');
            })
            .catch(() => { block.style.display = 'none'; });
    }

    window.proposePair = async function(tournamentId, targetRegId, playerName, btn) {
        if (!confirm(`¿Querés proponer pareja a ${playerName}? Se le enviará un email para que acepte.`)) return;
        btn.disabled = true;
        btn.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${t('details.sending')}`;
        try {
            const res = await fetch(`/api/tournaments/double/${tournamentId}/propose-pair/${targetRegId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' }
            });
            const data = await res.json();
            if (data.success) {
                btn.innerHTML = `<i class="fas fa-check"></i> ${t('details.proposal.sent')}`;
                btn.style.background = '#22c55e';
            } else {
                btn.disabled = false;
                btn.innerHTML = `<i class="fas fa-handshake"></i> ${t('details.proposal.btn')}`;
                alert(data.message || 'Error al enviar la propuesta');
            }
        } catch (e) {
            btn.disabled = false;
            btn.innerHTML = `<i class="fas fa-handshake"></i> ${t('details.proposal.btn')}`;
            alert(t('common.error.connection'));
        }
    };

    // Загружаем блок при старте если DOBLES турнир
    const lpBlock = document.getElementById('lookingForPartnerBlock');
    if (lpBlock) {
        const tid = window.tournament?.id;
        if (tid) loadLookingForPartner(tid);
    }

    async function handleCancellation(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        // Получаем модальное окно
        const cancelModal = document.getElementById('cancelModal');
        const cancelModalTournamentName = document.getElementById('cancelModalTournamentName');
        const cancelReason = document.getElementById('cancelReason');
        const confirmCancelBtn = document.getElementById('confirmCancelBtn');

        if (!cancelModal) {
            console.error('Cancel modal not found');
            return;
        }

        // Устанавливаем название турнира в модалке
        if (cancelModalTournamentName) {
            cancelModalTournamentName.innerHTML = t('details.cancel.confirm', `<strong>"${escapeHtml(tournamentName)}"</strong>`);
        }

        // Очищаем поле причины
        if (cancelReason) {
            cancelReason.value = '';
        }

        // Показываем модальное окно
        cancelModal.classList.add('show');
        cancelModal.style.display = 'block';

        // Убираем старые обработчики
        const newConfirmBtn = confirmCancelBtn.cloneNode(true);
        confirmCancelBtn.parentNode.replaceChild(newConfirmBtn, confirmCancelBtn);

        // Обработчик подтверждения
        newConfirmBtn.addEventListener('click', async () => {
            const reason = cancelReason ? cancelReason.value.trim() : '';

            // Закрываем модальное окно
            cancelModal.classList.remove('show');
            cancelModal.style.display = 'none';

            // Блокируем кнопку и показываем спиннер
            button.disabled = true;
            button.innerHTML = `<i class="fas fa-spinner fa-spin"></i> ${t('details.processing')}`;

            try {
                const url = `/players/tournaments/${tournamentId}/cancel` +
                    (reason ? `?reason=${encodeURIComponent(reason)}` : '');

                const response = await fetch(url, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' }
                });

                const data = await response.json();

                if (data.success) {
                    showResultModal('success', t('common.cancel.success'), t('details.success.cancelled'));
                } else {
                    button.disabled = false;
                    button.innerHTML = `<i class="fas fa-times-circle"></i> ${t('details.btn.cancel_registration')}`;
                    showResultModal('error', 'Error', data.message);
                }
            } catch (error) {
                console.error('Error cancelling:', error);
                button.disabled = false;
                button.innerHTML = `<i class="fas fa-times-circle"></i> ${t('details.btn.cancel_registration')}`;
                showResultModal('error', 'Error', t('details.error.process'));
            }
        });

        // Обработчик закрытия модалки по клику на фон
        const closeModalHandler = () => {
            cancelModal.classList.remove('show');
            cancelModal.style.display = 'none';
        };

        // Убираем старые обработчики закрытия
        const closeButtons = cancelModal.querySelectorAll('[data-dismiss="modal"]');
        closeButtons.forEach(btn => {
            const newBtn = btn.cloneNode(true);
            btn.parentNode.replaceChild(newBtn, btn);
            newBtn.addEventListener('click', closeModalHandler);
        });

        // Закрытие по клику вне модалки
        cancelModal.addEventListener('click', (e) => {
            if (e.target === cancelModal) {
                closeModalHandler();
            }
        });
    }

// Функция для экранирования HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ===== Функции для шаринга =====
    window.shareOnWhatsApp = function() {
        const url = encodeURIComponent(window.location.href);
        const text = encodeURIComponent(document.querySelector('h1').textContent);
        window.open(`https://wa.me/?text=${text}%20${url}`, '_blank');
    };

    window.shareOnFacebook = function() {
        const url = encodeURIComponent(window.location.href);
        window.open(`https://www.facebook.com/sharer/sharer.php?u=${url}`, '_blank');
    };

    window.shareOnTwitter = function() {
        const url = encodeURIComponent(window.location.href);
        const text = encodeURIComponent(document.querySelector('h1').textContent);
        window.open(`https://twitter.com/intent/tweet?text=${text}&url=${url}`, '_blank');
    };

    window.shareByEmail = function() {
        const subject = encodeURIComponent(document.querySelector('h1').textContent);
        const body = encodeURIComponent(`Mira este torneo de padel: ${window.location.href}`);
        window.location.href = `mailto:?subject=${subject}&body=${body}`;
    };
});

// Парсит fechaInicio+horaInicio турнира в JS Date. El formato depende del origen:
// tournamentJson (Jackson) entrega arrays [2026,4,5]/[18,0], window.tournament
// (Thymeleaf JS-inlining) entrega strings "2026-04-04"/"18:00:00".
function parseTournamentStart(tournament) {
    if (!tournament) return null;

    const fechaInicio = tournament.fechaInicio;
    const horaInicio = tournament.horaInicio;

    if (Array.isArray(fechaInicio) && Array.isArray(horaInicio)) {
        // Формат массива: [2026, 4, 5] + [18, 0]
        return new Date(fechaInicio[0], fechaInicio[1] - 1, fechaInicio[2],
            horaInicio[0], horaInicio[1], 0);
    }
    if (typeof fechaInicio === 'string') {
        // Формат строки: "2026-04-04 18:00:00"
        if (typeof horaInicio === 'string') {
            return new Date((fechaInicio + ' ' + horaInicio).replace(' ', 'T'));
        }
        return new Date(fechaInicio.replace(' ', 'T'));
    }
    return null;
}

// Функция для проверки, начался ли турнир (аналог той, что в карточке)
function isTournamentStarted(tournament) {
    if (!tournament) return false;

    // Проверка по статусу
    const closedStatuses = ['FINALIZADO', 'CANCELADO', 'CERRADO'];
    if (closedStatuses.includes(tournament.estado)) {
        return true;
    }

    const start = parseTournamentStart(tournament);
    if (!start || isNaN(start.getTime())) return false;

    const now = new Date();
    return now > start;
}

// ===== Añadir a Google Calendar =====
// "duracion" es texto libre del organizador (ej. "4 horas", "2 días", vacío) — no se
// puede parsear con certeza. Solo se usa cuando calza con un prefijo numérico + "hora(s)";
// si no, se usa una duración por defecto y el texto real queda en la descripción del evento.
function estimateDurationHours(duracion) {
    const DEFAULT_HOURS = 4;
    if (!duracion) return DEFAULT_HOURS;
    const match = String(duracion).trim().match(/^(\d+(?:[.,]\d+)?)\s*h/i);
    if (!match) return DEFAULT_HOURS;
    const hours = parseFloat(match[1].replace(',', '.'));
    return hours > 0 ? hours : DEFAULT_HOURS;
}

function toGoogleCalendarDateTime(date) {
    const pad = n => String(n).padStart(2, '0');
    return date.getFullYear() + pad(date.getMonth() + 1) + pad(date.getDate()) +
        'T' + pad(date.getHours()) + pad(date.getMinutes()) + '00';
}

function buildGoogleCalendarUrl(tournament, pageUrl) {
    const start = parseTournamentStart(tournament);
    if (!start || isNaN(start.getTime())) return null;

    const end = new Date(start.getTime() + estimateDurationHours(tournament.duracion) * 60 * 60 * 1000);
    const details = (tournament.duracion ? t('tournament.info.duration') + ': ' + tournament.duracion + '\n' : '') + pageUrl;

    const params = new URLSearchParams({
        action: 'TEMPLATE',
        text: tournament.nombre || '',
        dates: toGoogleCalendarDateTime(start) + '/' + toGoogleCalendarDateTime(end),
        details: details,
        location: tournament.clubDireccion || tournament.clubNombre || '',
        ctz: 'America/Argentina/Buenos_Aires'
    });

    return 'https://calendar.google.com/calendar/render?' + params.toString();
}

(function setupAddToCalendarButton() {
    const btn = document.getElementById('addToCalendarBtn');
    if (!btn) return;

    const url = buildGoogleCalendarUrl(window.tournament, window.location.href);
    if (!url) {
        btn.style.display = 'none';
        return;
    }
    btn.href = url;
})();

// Затем, в том месте где ищешь registerBtn, добавь блокировку:
const registerBtn = document.querySelector('.btn-register');

if (registerBtn) {
    // Проверяем, нужно ли заблокировать кнопку
    if (window.tournament && isTournamentStarted(window.tournament)) {
        // Блокируем кнопку
        registerBtn.disabled = true;
        registerBtn.style.opacity = '0.6';
        registerBtn.style.cursor = 'not-allowed';
        registerBtn.innerHTML = `<i class="fas fa-clock"></i> ${t('details.status.closed')}`;

        // Добавляем пояснение рядом
        const parentDiv = registerBtn.parentElement;
        const closedMessage = document.createElement('div');
        closedMessage.className = 'registration-closed';
        closedMessage.style.marginTop = '0.5rem';
        closedMessage.innerHTML = `<i class="fas fa-calendar-times"></i> <span>${t('details.status.deadline')}</span>`;

        // Удаляем старый, если есть, и добавляем новый
        const oldMessage = parentDiv.querySelector('.registration-closed:not(:last-child)');
        if (oldMessage && oldMessage.innerHTML.includes('Fecha de registro')) {
            oldMessage.remove();
        }
        parentDiv.appendChild(closedMessage);

        console.log('🔒 Registration button blocked - tournament already started');
    } else {
        // Твой существующий код для registerBtn (клонирование и addEventListener)
        // ... весь остальной код, который у тебя уже есть ...
    }
}