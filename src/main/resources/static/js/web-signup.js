(() => {
    'use strict';

    const form = document.getElementById('registroForm');
    const submit = document.getElementById('submitBtn');
    const captcha = form.querySelector('.g-recaptcha');
    const password = document.getElementById('password');
    const confirmation = document.getElementById('confirmPassword');
    const originalLabel = submit.textContent.trim();
    const phone = document.getElementById('telefono');
    const country = document.getElementById('phoneCountry');
    const telegram = document.getElementById('telegramUsername');

    ContactFields.countries(document.documentElement.lang || 'es')
        .forEach(({code,label}) => country.add(new Option(label, code)));
    country.value = libphonenumber.isSupportedCountry(country.dataset.selected) ? country.dataset.selected : 'AR';

    function updateExample() {
        document.getElementById('telefonoHint').textContent = t('contact.phone.help', ContactFields.example(country.value));
    }

    function validatePhone() {
        const result = ContactFields.phone(phone.value, country.value);
        fieldError(phone, result.error ? t('contact.validation.' + result.error) : '');
        return result;
    }

    function validateTelegram() {
        const result = ContactFields.telegram(telegram.value);
        if (telegram.value && !telegram.value.startsWith('@')) {
            const cursor = telegram.selectionStart;
            telegram.value = result.value;
            telegram.setSelectionRange(cursor + 1, cursor + 1);
        }
        fieldError(telegram, result.error ? t('contact.validation.' + result.error) : '');
        return result;
    }

    phone.addEventListener('input', validatePhone);
    phone.addEventListener('blur', () => {
        const result = validatePhone();
        if (result.error) return;
        phone.value = result.display;
        country.value = result.country;
        updateExample();
    });
    country.addEventListener('change', () => { updateExample(); validatePhone(); });
    telegram.addEventListener('input', validateTelegram);
    updateExample();

    if (captcha && captcha.parentElement.clientWidth < 304) {
        captcha.dataset.size = 'compact';
    }

    function fieldError(input, message) {
        document.getElementById(input.id + 'Error').textContent = message;
        input.setAttribute('aria-invalid', String(Boolean(message)));
    }

    form.addEventListener('input', ({target}) => {
        if (target !== phone && target !== telegram && target.id && document.getElementById(target.id + 'Error')) {
            fieldError(target, '');
        }
    });

    form.addEventListener('submit', event => {
        if (submit.disabled) {
            event.preventDefault();
            return;
        }

        const normalizedPhone = validatePhone();
        const normalizedTelegram = validateTelegram();
        if (normalizedPhone.error || normalizedTelegram.error) {
            event.preventDefault();
            (normalizedPhone.error ? phone : telegram).focus();
            return;
        }
        phone.value = normalizedPhone.number;
        country.value = normalizedPhone.country;
        telegram.value = normalizedTelegram.value;

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
