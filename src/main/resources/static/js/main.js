/**
 * Padel Core - Main JavaScript
 * Handles UI interactions, form validation and AJAX requests
 */

document.addEventListener('DOMContentLoaded', function() {
    'use strict';

    // ===== MOBILE MENU =====
    const navbarToggler = document.getElementById('navbarToggler');
    const navbarCollapse = document.getElementById('navbarNav');

    if (navbarToggler && navbarCollapse) {
        navbarToggler.addEventListener('click', function() {
            navbarCollapse.classList.toggle('show');
            this.classList.toggle('active');
        });

        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                navbarCollapse.classList.remove('show');
            });
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
                icon.classList.toggle('fa-eye');
                icon.classList.toggle('fa-eye-slash');
            }
        });
    });

    // ===== PASSWORD STRENGTH INDICATOR =====
    const passwordInput = document.getElementById('password');
    const strengthBar = document.querySelector('.strength-bar');

    if (passwordInput && strengthBar) {
        passwordInput.addEventListener('input', function() {
            const password = this.value;
            let strength = 0;

            if (password.length >= 8) strength += 25;
            if (password.match(/[a-z]+/)) strength += 25;
            if (password.match(/[A-Z]+/)) strength += 25;
            if (password.match(/[0-9]+/)) strength += 15;
            if (password.match(/[$@#&!]+/)) strength += 10;

            strength = Math.min(strength, 100);
            strengthBar.style.width = strength + '%';

            if (strength < 40) {
                strengthBar.style.background = 'linear-gradient(90deg, #EF4444, #F59E0B)';
            } else if (strength < 70) {
                strengthBar.style.background = 'linear-gradient(90deg, #F59E0B, #10B981)';
            } else {
                strengthBar.style.background = 'linear-gradient(90deg, #10B981, #10B981)';
            }
        });
    }

    // ===== FORM VALIDATION =====
    const registerForm = document.getElementById('registerForm');

    if (registerForm) {
        registerForm.addEventListener('submit', function(e) {
            const password = document.getElementById('password').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            if (password !== confirmPassword) {
                e.preventDefault();
                showError('Las contraseñas no coinciden');
                return false;
            }

            const passwordRegex = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\S+$).{8,}$/;
            if (!passwordRegex.test(password)) {
                e.preventDefault();
                showError('La contraseña debe contener al menos: 1 mayúscula, 1 minúscula, 1 número y 1 carácter especial');
                return false;
            }
        });
    }

    // ===== ERROR MESSAGE HANDLER =====
    function showError(message) {
        let errorAlert = document.querySelector('.alert-error');

        if (!errorAlert) {
            errorAlert = document.createElement('div');
            errorAlert.className = 'alert alert-error';
            errorAlert.innerHTML = `<i class="fas fa-exclamation-circle"></i> ${message}`;

            const form = document.querySelector('.register-form');
            if (form) {
                form.insertBefore(errorAlert, form.firstChild);
            }
        } else {
            errorAlert.innerHTML = `<i class="fas fa-exclamation-circle"></i> ${message}`;
        }

        setTimeout(() => {
            if (errorAlert) {
                errorAlert.remove();
            }
        }, 5000);
    }

    // ===== ANIMATION ON SCROLL =====
    const animateOnScroll = function() {
        const elements = document.querySelectorAll('.feature-card, .carousel-card, .form-card');

        elements.forEach(element => {
            const elementTop = element.getBoundingClientRect().top;
            const elementVisible = 150;

            if (elementTop < window.innerHeight - elementVisible) {
                element.style.opacity = '1';
                element.style.transform = 'translateY(0)';
            }
        });
    };

    document.querySelectorAll('.feature-card, .carousel-card, .form-card').forEach(el => {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        el.style.transition = 'opacity 0.5s ease, transform 0.5s ease';
    });

    animateOnScroll();
    window.addEventListener('scroll', animateOnScroll);
});