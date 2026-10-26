/**
 * Padel Core - Profile Page JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // ===== БУРГЕР-МЕНЮ =====
    const navbarToggler = document.getElementById('navbarToggler');
    const navbarNav = document.getElementById('navbarNav');

    if (navbarToggler && navbarNav) {
        console.log('✅ Profile: найдены элементы меню');

        // Убираем старые обработчики
        const newToggler = navbarToggler.cloneNode(true);
        navbarToggler.parentNode.replaceChild(newToggler, navbarToggler);

        // Добавляем обработчик
        newToggler.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();

            navbarNav.classList.toggle('show');
            console.log('🍔 Меню профиля:', navbarNav.classList.contains('show') ? 'abierto' : 'cerrado');
        });

        // Закрываем меню при клике на ссылку
        navbarNav.querySelectorAll('a').forEach(link => {
            link.addEventListener('click', () => {
                navbarNav.classList.remove('show');
            });
        });

        // Закрываем меню при клике вне его
        document.addEventListener('click', (e) => {
            if (navbarNav.classList.contains('show') &&
                !navbarNav.contains(e.target) &&
                !newToggler.contains(e.target)) {
                navbarNav.classList.remove('show');
            }
        });
    }

    // ===== PASSWORD VISIBILITY TOGGLE =====
    document.querySelectorAll('.toggle-password').forEach(button => {
        button.addEventListener('click', function() {
            const targetId = this.dataset.target;
            const passwordInput = document.getElementById(targetId);

            if (passwordInput) {
                const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
                passwordInput.setAttribute('type', type);

                const icon = this.querySelector('i');
                icon.classList.toggle('fas fa-eye');
                icon.classList.toggle('fas fa-eye-slash');
            }
        });
    });

    // ===== PASSWORD STRENGTH INDICATOR =====
    const newPassword = document.getElementById('newPassword');
    const confirmPassword = document.getElementById('confirmPassword');
    const passwordStrength = document.getElementById('passwordStrength');
    const strengthBar = document.querySelector('.strength-bar');
    const strengthText = document.querySelector('.strength-text');

    if (newPassword) {
        newPassword.addEventListener('input', function() {
            const password = this.value;

            if (password.length > 0) {
                passwordStrength.style.display = 'block';

                let strength = 0;
                let feedback = [];

                if (password.length >= 8) {
                    strength += 25;
                } else {
                    feedback.push(t('profile.pwd.req.min8'));
                }

                if (password.match(/[a-z]+/)) {
                    strength += 25;
                } else {
                    feedback.push(t('profile.pwd.req.lowercase'));
                }

                if (password.match(/[A-Z]+/)) {
                    strength += 25;
                } else {
                    feedback.push(t('profile.pwd.req.uppercase'));
                }

                if (password.match(/[0-9]+/)) {
                    strength += 15;
                } else {
                    feedback.push(t('profile.pwd.req.numbers'));
                }

                if (password.match(/[$@#&!]+/)) {
                    strength += 10;
                } else {
                    feedback.push(t('profile.pwd.req.special'));
                }

                strength = Math.min(strength, 100);
                strengthBar.style.width = strength + '%';

                if (strength < 40) {
                    strengthBar.style.background = 'linear-gradient(90deg, #EF4444, #F59E0B)';
                    strengthText.textContent = t('profile.pwd.weak', feedback.join(', '));
                    strengthText.style.color = '#EF4444';
                } else if (strength < 70) {
                    strengthBar.style.background = 'linear-gradient(90deg, #F59E0B, #10B981)';
                    strengthText.textContent = t('profile.pwd.medium');
                    strengthText.style.color = '#F59E0B';
                } else {
                    strengthBar.style.background = 'linear-gradient(90deg, #10B981, #10B981)';
                    strengthText.textContent = t('profile.pwd.strong');
                    strengthText.style.color = '#10B981';
                }
            } else {
                passwordStrength.style.display = 'none';
            }
        });
    }

    // ===== PASSWORD CONFIRMATION VALIDATION =====
    if (newPassword && confirmPassword) {
        confirmPassword.addEventListener('input', function() {
            if (newPassword.value !== this.value) {
                this.style.borderColor = '#EF4444';
            } else {
                this.style.borderColor = '#10B981';
            }
        });
    }

    // ===== AVATAR EDIT (simulado) =====
    const avatarEdit = document.getElementById('avatarEdit');
    if (avatarEdit) {
        avatarEdit.addEventListener('click', function() {
            alert(t('profile.avatar.soon'));
        });
    }

    // ===== FORM VALIDATION =====
    const profileForm = document.querySelector('.profile-form');
    if (profileForm) {
        profileForm.addEventListener('submit', function(e) {
            const newPass = document.getElementById('newPassword')?.value || '';
            const confirmPass = document.getElementById('confirmPassword')?.value || '';

            if (newPass || confirmPass) {
                // Si se está intentando cambiar la contraseña
                const currentPass = document.getElementById('currentPassword')?.value || '';

                if (!currentPass) {
                    e.preventDefault();
                    alert(t('profile.error.current_pwd'));
                    return false;
                }

                if (newPass !== confirmPass) {
                    e.preventDefault();
                    alert(t('profile.error.pwd_no_match'));
                    return false;
                }

                const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\S+$).{8,}$/;
                if (!passwordRegex.test(newPass)) {
                    e.preventDefault();
                    alert(t('profile.error.pwd_requirements'));
                    return false;
                }
            }
        });
    }

    // ===== Возврат к незавершённой регистрации после указания уровня =====
    // Если игрок пришёл в профиль из попапа "Указать уровень" (LevelCheck сохранил
    // tournamentId в sessionStorage) и профиль только что успешно сохранился — предлагаем
    // вернуться к турниру.
    // Ключ не удаляется, пока нет successAlert: игрок обычно сначала просто открывает
    // /perfil (GET, без алерта), и только при следующей загрузке, после сабмита формы,
    // появляется successMessage; если бы ключ чистился на первой же загрузке, баннер
    // никогда бы не успел показаться.
    try {
        const pendingTournamentId = sessionStorage.getItem('pendingLevelRegistrationTournamentId');
        const successAlert = document.querySelector('.alert-success');
        if (pendingTournamentId && successAlert && typeof t === 'function') {
            sessionStorage.removeItem('pendingLevelRegistrationTournamentId');

            const banner = document.createElement('div');
            banner.className = 'alert alert-success';
            banner.innerHTML = `<i class="fas fa-arrow-circle-left"></i> ${t('level.profile.return_banner')} `
                + `<a href="/torneo/${pendingTournamentId}">${t('level.profile.return_link')}</a>`;
            successAlert.insertAdjacentElement('afterend', banner);
        }
    } catch (e) {
        console.warn('No se pudo verificar la inscripción pendiente:', e);
    }
});