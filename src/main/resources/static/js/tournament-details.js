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

        // Проверяем разные варианты написания
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

    // Закрытие по клику вне модального окна
    window.addEventListener('click', (e) => {
        if (e.target === partnerModal) {
            closeModal();
        }
    });

    function closeModal() {
        if (partnerModal) {
            partnerModal.classList.remove('show');
            if (partnerForm) partnerForm.reset();
            hideInfoMessage();

            // Сбрасываем ошибки валидации
            document.querySelectorAll('.form-control').forEach(input => {
                input.classList.remove('is-invalid');
            });
        }
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

            // Меняем цвет иконки в зависимости от типа
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

        // Автоматически закрываем через 3 секунды для успеха
        if (type === 'success') {
            setTimeout(() => {
                resultModal.classList.remove('show');
                window.location.reload();
            }, 3000);
        }
    }

    // ===== Кнопки регистрации =====
    const registerBtn = document.querySelector('.btn-register');
    const cancelBtn = document.querySelector('.btn-cancel');

    if (registerBtn) {
        console.log('✅ Register button found');

        // Сохраняем оригинальный обработчик
        const newRegisterBtn = registerBtn.cloneNode(true);
        registerBtn.parentNode.replaceChild(newRegisterBtn, registerBtn);

        newRegisterBtn.addEventListener('click', function(event) {
            const tournamentId = this.dataset.tournamentId;
            const tournamentName = this.dataset.tournamentName;

            console.log('Register clicked for tournament:', tournamentId, tournamentName);
            console.log('Tournament modalidad:', window.tournament?.modalidad);

            // Проверяем, парный ли турнир
            if (isDoubleTournament()) {
                console.log('🎾 Double tournament - showing modal');
                event.preventDefault();
                event.stopPropagation();

                // Заполняем данные в модальном окне
                const modalTournamentId = document.getElementById('modalTournamentId');
                const modalTournamentName = document.getElementById('modalTournamentName');

                if (modalTournamentId) modalTournamentId.value = tournamentId;
                if (modalTournamentName) modalTournamentName.textContent = tournamentName;

                showModal();
            } else {
                console.log('🎾 Single tournament - direct registration');
                // Для одиночного турнира - обычная регистрация
                handleSingleRegistration(event);
            }
        });
    } else {
        console.log('❌ Register button not found');
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', handleCancellation);
    }

    // Обработка отправки формы парного турнира
    if (submitPartnerBtn) {
        submitPartnerBtn.addEventListener('click', handleDoubleRegistration);
    }

    // Валидация формы в реальном времени
    document.querySelectorAll('#partnerForm .form-control').forEach(input => {
        input.addEventListener('input', function() {
            if (this.classList.contains('is-invalid')) {
                this.classList.remove('is-invalid');
            }
        });
    });

    async function handleDoubleRegistration() {
        const tournamentId = document.getElementById('modalTournamentId').value;
        const firstName = document.getElementById('partnerFirstName').value.trim();
        const lastName = document.getElementById('partnerLastName').value.trim();
        const phone = document.getElementById('partnerPhone').value.trim();
        const email = document.getElementById('partnerEmail').value.trim();

        console.log('Submitting double registration:', { tournamentId, firstName, lastName, phone, email });

        // Валидация
        let isValid = true;

        if (!firstName) {
            document.getElementById('partnerFirstName').classList.add('is-invalid');
            isValid = false;
        }

        if (!lastName) {
            document.getElementById('partnerLastName').classList.add('is-invalid');
            isValid = false;
        }

        const phoneRegex = /^\+?[0-9\s\-\(\)]{8,20}$/;
        if (!phone || !phoneRegex.test(phone)) {
            document.getElementById('partnerPhone').classList.add('is-invalid');
            isValid = false;
        }

        if (email) {
            const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
            if (!emailRegex.test(email)) {
                document.getElementById('partnerEmail').classList.add('is-invalid');
                isValid = false;
            }
        }

        if (!isValid) {
            showInfoMessage('Por favor completa todos los campos requeridos correctamente', 'warning');
            return;
        }

        // Блокируем кнопку
        submitPartnerBtn.disabled = true;
        submitPartnerBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';
        hideInfoMessage();

        try {
            const partnerData = {
                nombre: firstName,
                apellido: lastName,
                telefono: phone,
                email: email || null
            };

            const response = await fetch(`/api/tournaments/double/${tournamentId}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(partnerData)
            });

            const data = await response.json();
            console.log('Registration response:', data);

            if (response.ok) {
                closeModal();

                let message = '';
                if (data.status === 'PARTNER_INVITED') {
                    message = '¡Registro exitoso! Hemos enviado un email a tu compañero para completar su registro.';
                } else if (data.status === 'CONFIRMED') {
                    message = '¡Registro exitoso! Tu compañero ya estaba registrado y ha recibido una notificación.';
                } else if (data.status === 'WAITLIST') {
                    message = 'Has sido añadido a la lista de espera. Te notificaremos si hay algún cambio.';
                } else {
                    message = '¡Registro completado exitosamente!';
                }

                showResultModal('success', '¡Registro exitoso!', message);
            } else {
                const errorData = await response.json();

                // Обрабатываем ошибку "уже зарегистрирован"
                if (errorData.message && (errorData.message.includes('Ya estás registrado') ||
                    errorData.message.includes('Ya tienes una registro'))) {
                    showResultModal('info', 'Ya estás registrado', errorData.message);
                    closeModal();
                } else {
                    showInfoMessage(errorData.message || 'Error al procesar la solicitud', 'error');
                }

                submitPartnerBtn.disabled = false;
                submitPartnerBtn.innerHTML = '<i class="fas fa-check-circle"></i> Registrar pareja';
            }
        } catch (error) {
            console.error('Error registering double tournament:', error);
            showInfoMessage('Error de conexión. Por favor intenta de nuevo.', 'error');
            submitPartnerBtn.disabled = false;
            submitPartnerBtn.innerHTML = '<i class="fas fa-check-circle"></i> Registrar pareja';
        }
    }

    async function handleSingleRegistration(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        if (!confirm(`¿Deseas inscribirte en el torneo "${tournamentName}"?`)) {
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const response = await fetch(`/players/tournaments/${tournamentId}/register`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                showResultModal('success', '¡Registro exitoso!', data.message);
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = window.tournament?.inscritosActuales >= window.tournament?.cupoMax ?
                    '<i class="fas fa-clock"></i> Apuntarme a lista de espera' :
                    '<i class="fas fa-check-circle"></i> Inscribirme';
            }
        } catch (error) {
            console.error('Error registering:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = window.tournament?.inscritosActuales >= window.tournament?.cupoMax ?
                '<i class="fas fa-clock"></i> Apuntarme a lista de espera' :
                '<i class="fas fa-check-circle"></i> Inscribirme';
        }
    }

    async function handleCancellation(event) {
        const button = event.currentTarget;
        const tournamentId = button.dataset.tournamentId;
        const tournamentName = button.dataset.tournamentName;

        const reason = prompt('¿Por qué cancelas tu inscripción? (opcional)');

        if (!confirm(`¿Estás seguro de cancelar tu inscripción en "${tournamentName}"?`)) {
            return;
        }

        button.disabled = true;
        button.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Procesando...';

        try {
            const url = `/players/tournaments/${tournamentId}/cancel` +
                (reason ? `?reason=${encodeURIComponent(reason)}` : '');

            const response = await fetch(url, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                }
            });

            const data = await response.json();

            if (data.success) {
                showResultModal('success', 'Cancelación exitosa', data.message);
            } else {
                alert('Error: ' + data.message);
                button.disabled = false;
                button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
            }
        } catch (error) {
            console.error('Error cancelling:', error);
            alert('Error al procesar la solicitud');
            button.disabled = false;
            button.innerHTML = '<i class="fas fa-times-circle"></i> Cancelar inscripción';
        }
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