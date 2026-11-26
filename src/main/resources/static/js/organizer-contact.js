(() => {
    'use strict';
    const root = document.getElementById('organizerContactForm');
    if (!root) return;
    const phone = root.querySelector('#organizerPhone');
    const country = root.querySelector('#organizerCountry');
    const name = root.querySelector('#organizerName');
    const error = root.querySelector('#organizerPhoneError');
    const preview = root.querySelector('[data-contact-preview]');
    const link = root.querySelector('[data-contact-link]');
    const number = root.querySelector('[data-contact-number]');
    const sample = root.querySelector('[data-phone-example]');
    let timer, sequence = 0, pending;
    function example() {
        const value = country.selectedOptions[0]?.dataset.example || '';
        sample.textContent = value;
        phone.placeholder = value;
    }
    function clear() {
        preview.hidden = true;
        link.removeAttribute('href');
        error.hidden = true;
        phone.removeAttribute('aria-invalid');
    }
    async function check() {
        const current = ++sequence;
        pending?.abort();
        pending = new AbortController();
        clear();
        if (!phone.value.trim()) return;
        error.textContent = root.dataset.pending;
        error.hidden = false;
        try {
            const response = await fetch(root.dataset.previewUrl, {
                method: 'POST', credentials: 'same-origin', signal: pending.signal,
                headers: {'Content-Type': 'application/json', 'Accept': 'application/json',
                    'X-CSRF-TOKEN': document.querySelector('input[name="_csrf"]')?.value || ''},
                body: JSON.stringify({eventId: root.dataset.eventId ? Number(root.dataset.eventId) : null,
                    name: name.value, phone: phone.value, country: country.value})
            });
            if (!response.ok) throw new Error('preview unavailable');
            const result = await response.json();
            if (current !== sequence) return;
            error.hidden = result.valid;
            if (!result.valid) {
                error.textContent = result.message;
                phone.setAttribute('aria-invalid', 'true');
                return;
            }
            country.value = result.contact.country;
            example();
            number.textContent = result.contact.displayPhone;
            link.href = result.contact.whatsappUrl;
            preview.hidden = false;
        } catch (failure) {
            if (current !== sequence || failure.name === 'AbortError') return;
            error.textContent = root.dataset.unavailable;
            error.hidden = false;
        }
    }
    function schedule() {
        ++sequence;
        pending?.abort();
        clearTimeout(timer);
        clear();
        timer = setTimeout(check, 350);
    }
    phone.addEventListener('input', schedule);
    name.addEventListener('input', schedule);
    country.addEventListener('change', () => { example(); schedule(); });
    phone.addEventListener('blur', () => { clearTimeout(timer); check(); });
    example();
    if (phone.value.trim()) check();
})();
