(() => {
    'use strict';

    const form = document.getElementById('registroForm');
    const submit = document.getElementById('submitBtn');
    const captcha = form.querySelector('.g-recaptcha');
    const password = document.getElementById('password');
    const confirmation = document.getElementById('confirmPassword');
    const originalLabel = submit.textContent.trim();

    if (captcha && captcha.parentElement.clientWidth < 304) {
        captcha.dataset.size = 'compact';
    }

    function fieldError(input, message) {
        document.getElementById(input.id + 'Error').textContent = message;
        input.setAttribute('aria-invalid', String(Boolean(message)));
    }

    form.addEventListener('input', ({target}) => {
        if (target.id && document.getElementById(target.id + 'Error')) {
            fieldError(target, '');
        }
    });

    form.addEventListener('submit', event => {
        if (submit.disabled) {
            event.preventDefault();
            return;
        }

        const passwordValid = /^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$/.test(password.value);
        if (!passwordValid || password.value !== confirmation.value) {
            event.preventDefault();
            const input = passwordValid ? confirmation : password;
            fieldError(input, passwordValid ? form.dataset.passwordMismatch : form.dataset.passwordRules);
            input.focus();
            return;
        }

        if (captcha && !form.querySelector('[name="g-recaptcha-response"]')?.value) {
            event.preventDefault();
            document.getElementById('captchaError').textContent = form.dataset.captchaRequired;
            captcha.querySelector('iframe')?.focus();
            return;
        }

        submit.disabled = true;
        submit.setAttribute('aria-busy', 'true');
        submit.textContent = form.dataset.submitting;
    });

    window.addEventListener('pageshow', () => {
        if (submit.getAttribute('aria-busy') === 'true') {
            submit.disabled = false;
            submit.removeAttribute('aria-busy');
            submit.textContent = originalLabel;
        }
    });
})();
