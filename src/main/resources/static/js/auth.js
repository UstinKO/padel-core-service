document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // Password visibility toggle
    document.querySelectorAll('.toggle-password').forEach(button => {
        button.addEventListener('click', function() {
            const targetId = this.dataset.target;
            const passwordInput = document.getElementById(targetId);

            if (passwordInput) {
                const type = passwordInput.getAttribute('type') === 'password' ? 'text' : 'password';
                passwordInput.setAttribute('type', type);

                const icon = this.querySelector('i');
                icon.classList.toggle('fa-eye');
                icon.classList.toggle('fa-eye-slash');
            }
        });
    });

    // Разрешённые символы: a-z, A-Z, 0-9, @#$%^&+=!
    const FORBIDDEN_CHARS = /[^a-zA-Z0-9@#$%^&+=!]/g;

    const passwordInput   = document.getElementById('password');
    const confirmInput    = document.getElementById('confirmPassword');
    const strengthBar     = document.querySelector('.strength-bar');

    // ── Hint под полем password ──────────────────────────────
    let passwordHint = document.getElementById('passwordHint');
    if (!passwordHint && passwordInput) {
        passwordHint = document.createElement('div');
        passwordHint.id = 'passwordHint';
        passwordHint.style.cssText = 'font-size:.82rem; margin-top:.4rem; line-height:1.4;';
        const strengthBlock = passwordInput.closest('.form-group')?.querySelector('.password-strength');
        if (strengthBlock) {
            strengthBlock.insertAdjacentElement('afterend', passwordHint);
        } else {
            passwordInput.closest('.form-group')?.appendChild(passwordHint);
        }
    }

    // ── Hint под полем confirmPassword ───────────────────────
    let confirmHint = document.getElementById('confirmHint');
    if (!confirmHint && confirmInput) {
        confirmHint = document.createElement('div');
        confirmHint.id = 'confirmHint';
        confirmHint.style.cssText = 'font-size:.82rem; margin-top:.4rem; line-height:1.4;';
        confirmInput.closest('.form-group')?.appendChild(confirmHint);
    }

    // ── Вспомогательные функции ──────────────────────────────
    function getPasswordErrors(password) {
        const errors = [];
        if (password.length < 8)           errors.push(t('auth.pwd.req.min8'));
        if (!password.match(/[a-z]/))       errors.push(t('auth.pwd.req.lowercase'));
        if (!password.match(/[A-Z]/))       errors.push(t('auth.pwd.req.uppercase'));
        if (!password.match(/[0-9]/))       errors.push(t('auth.pwd.req.number'));
        if (!password.match(/[@#$%^&+=!]/)) errors.push(t('auth.pwd.req.special'));
        return errors;
    }

    function getForbiddenChars(password) {
        const found = password.match(FORBIDDEN_CHARS);
        if (!found) return [];
        return [...new Set(found)];
    }

    function updateConfirmHint() {
        if (!confirmHint || !confirmInput) return;
        const confirm = confirmInput.value;
        const password = passwordInput ? passwordInput.value : '';

        if (confirm.length === 0) {
            confirmHint.textContent = '';
            confirmInput.classList.remove('is-invalid', 'is-valid');
            return;
        }

        if (confirm === password) {
            confirmHint.innerHTML = `<span style="color:#10B981;"><i class="fas fa-check-circle"></i> ${t('auth.pwd.match')}</span>`;
            confirmInput.classList.remove('is-invalid');
            confirmInput.classList.add('is-valid');
        } else {
            confirmHint.innerHTML = `<span style="color:#EF4444;"><i class="fas fa-times-circle"></i> ${t('auth.pwd.no_match')}</span>`;
            confirmInput.classList.remove('is-valid');
            confirmInput.classList.add('is-invalid');
        }
    }

    // ── Обработчик поля password ─────────────────────────────
    if (passwordInput && strengthBar) {
        passwordInput.addEventListener('input', function() {
            const password = this.value;

            // Индикатор силы
            let strength = 0;
            if (password.length >= 8)          strength += 25;
            if (password.match(/[a-z]+/))       strength += 25;
            if (password.match(/[A-Z]+/))       strength += 25;
            if (password.match(/[0-9]+/))       strength += 15;
            if (password.match(/[@#$%^&+=!]+/)) strength += 10;
            strength = Math.min(strength, 100);

            strengthBar.style.width = strength + '%';
            if (strength < 40) {
                strengthBar.style.background = 'linear-gradient(90deg, #EF4444, #F59E0B)';
            } else if (strength < 70) {
                strengthBar.style.background = 'linear-gradient(90deg, #F59E0B, #10B981)';
            } else {
                strengthBar.style.background = 'linear-gradient(90deg, #10B981, #10B981)';
            }

            // Сообщение об ошибках
            if (passwordHint) {
                if (password.length === 0) {
                    passwordHint.textContent = '';
                    passwordInput.classList.remove('is-invalid', 'is-valid');
                } else {
                    const forbidden = getForbiddenChars(password);
                    if (forbidden.length > 0) {
                        const chars = forbidden
                            .map(c => `<code style="background:#fee2e2;padding:1px 4px;border-radius:3px;font-size:.85em;">${c === ' ' ? t('auth.pwd.space_label') : c}</code>`)
                            .join(' ');
                        passwordHint.innerHTML = `<span style="color:#EF4444;"><i class="fas fa-ban"></i> ${t('auth.pwd.invalid_chars', chars)}</span>`;
                        passwordInput.classList.remove('is-valid');
                        passwordInput.classList.add('is-invalid');
                    } else {
                        const errors = getPasswordErrors(password);
                        if (errors.length === 0) {
                            passwordHint.innerHTML = `<span style="color:#10B981;"><i class="fas fa-check-circle"></i> ${t('auth.pwd.valid')}</span>`;
                            passwordInput.classList.remove('is-invalid');
                            passwordInput.classList.add('is-valid');
                        } else {
                            passwordHint.innerHTML = `<span style="color:#EF4444;"><i class="fas fa-exclamation-circle"></i> ${t('auth.pwd.missing', errors.join(', '))}</span>`;
                            passwordInput.classList.remove('is-valid');
                            passwordInput.classList.add('is-invalid');
                        }
                    }
                }
            }

            // Обновляем hint confirmPassword при изменении основного пароля
            updateConfirmHint();
        });
    }

    // ── Обработчик поля confirmPassword ─────────────────────
    if (confirmInput) {
        confirmInput.addEventListener('input', updateConfirmHint);
    }

    // Smooth fade-in animation
    const authCard = document.querySelector('.auth-card');
    if (authCard) {
        authCard.style.opacity = '0';
        authCard.style.transform = 'translateY(20px)';
        setTimeout(() => {
            authCard.style.transition = 'all 0.6s ease';
            authCard.style.opacity = '1';
            authCard.style.transform = 'translateY(0)';
        }, 100);
    }
});