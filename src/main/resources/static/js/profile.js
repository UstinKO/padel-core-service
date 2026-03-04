/**
 * Padel Core - Profile Page JavaScript
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // ===== PASSWORD VISIBILITY TOGGLE =====
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
                    feedback.push('mínimo 8 caracteres');
                }

                if (password.match(/[a-z]+/)) {
                    strength += 25;
                } else {
                    feedback.push('minúsculas');
                }

                if (password.match(/[A-Z]+/)) {
                    strength += 25;
                } else {
                    feedback.push('mayúsculas');
                }

                if (password.match(/[0-9]+/)) {
                    strength += 15;
                } else {
                    feedback.push('números');
                }

                if (password.match(/[$@#&!]+/)) {
                    strength += 10;
                } else {
                    feedback.push('caracteres especiales');
                }

                strength = Math.min(strength, 100);
                strengthBar.style.width = strength + '%';

                if (strength < 40) {
                    strengthBar.style.background = 'linear-gradient(90deg, #EF4444, #F59E0B)';
                    strengthText.textContent = 'Contraseña débil: falta ' + feedback.join(', ');
                    strengthText.style.color = '#EF4444';
                } else if (strength < 70) {
                    strengthBar.style.background = 'linear-gradient(90deg, #F59E0B, #10B981)';
                    strengthText.textContent = 'Contraseña media';
                    strengthText.style.color = '#F59E0B';
                } else {
                    strengthBar.style.background = 'linear-gradient(90deg, #10B981, #10B981)';
                    strengthText.textContent = 'Contraseña fuerte';
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
            alert('Función de cambio de foto próximamente disponible');
        });
    }

    // ===== FORM VALIDATION =====
    const profileForm = document.querySelector('.profile-form');
    if (profileForm) {
        profileForm.addEventListener('submit', function(e) {
            const newPass = document.getElementById('newPassword').value;
            const confirmPass = document.getElementById('confirmPassword').value;

            if (newPass || confirmPass) {
                // Si se está intentando cambiar la contraseña
                const currentPass = document.getElementById('currentPassword').value;

                if (!currentPass) {
                    e.preventDefault();
                    alert('Debes ingresar tu contraseña actual para cambiarla');
                    return false;
                }

                if (newPass !== confirmPass) {
                    e.preventDefault();
                    alert('Las contraseñas nuevas no coinciden');
                    return false;
                }

                const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\S+$).{8,}$/;
                if (!passwordRegex.test(newPass)) {
                    e.preventDefault();
                    alert('La nueva contraseña debe contener al menos: 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial');
                    return false;
                }
            }
        });
    }
});