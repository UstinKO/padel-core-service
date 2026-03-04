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

    // Password strength indicator (solo visual, no bloquea)
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
        });
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