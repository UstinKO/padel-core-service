const ContactCheck = (() => {
    'use strict';
    let pending = null;
    let busy = false;
    let previousFocus = null;
    const el = id => document.getElementById(id);
    const errorKey = code => 'contact.validation.' + code;

    function ensureModal() {
        if (el('contactCheckModal')) return;
        document.body.insertAdjacentHTML('beforeend', `
            <div id="contactCheckModal" hidden>
              <section class="cc-dialog" role="dialog" aria-modal="true" aria-labelledby="ccTitle" aria-describedby="ccTournamentName" tabindex="-1">
                <h3 id="ccTitle">${t('contact.modal.title')}</h3>
                <p id="ccTournamentName"></p>
                <form id="ccForm" novalidate>
                  <div class="cc-field">
                    <label for="ccCountry">${t('contact.field.country')}</label>
                    <select id="ccCountry" autocomplete="country"></select>
                  </div>
                  <div class="cc-field">
                    <label for="ccPhone">${t('contact.field.whatsapp')}</label>
                    <input id="ccPhone" type="tel" inputmode="tel" autocomplete="tel" required class="clarity-mask" data-clarity-mask="true" aria-describedby="ccPhoneHelp ccPhoneError">
                    <span id="ccPhoneHelp" class="cc-help"></span>
                    <span id="ccPhoneError" class="cc-error" aria-live="polite" hidden></span>
                  </div>
                  <div class="cc-field cc-telegram-field">
                    <label for="ccTelegram">${t('contact.field.telegram')} <small>${t('contact.telegram.available')}</small></label>
                    <input id="ccTelegram" type="text" inputmode="text" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="@usuario" class="clarity-mask" data-clarity-mask="true" aria-describedby="ccTgHelp ccTgError">
                    <details class="contact-telegram-help">
                      <summary aria-label="${t('contact.telegram.help.title')}"><span aria-hidden="true">ⓘ</span></summary>
                      <span id="ccTgHelp" class="contact-telegram-help-text">${t('contact.telegram.help')}</span>
                    </details>
                    <span id="ccTgError" class="cc-error" aria-live="polite" hidden></span>
                  </div>
                  <div id="ccGlobalError" class="cc-global" role="alert" hidden></div>
                  <div class="cc-actions">
                    <button id="ccCancelBtn" type="button">${t('contact.btn.cancel')}</button>
                    <button id="ccSaveBtn" type="submit">${t('contact.btn.save')}</button>
                  </div>
                </form>
              </section>
            </div>`);
        ContactFields.countries(document.documentElement.lang || 'es')
            .forEach(({code,label}) => el('ccCountry').add(new Option(label, code)));
        el('ccCountry').value = 'AR';
        ContactFields.bindPhone(el('ccPhone'), el('ccCountry'), () => { updateExample(); validatePhone(); });
        el('ccPhone').addEventListener('blur', () => {
            const result = validatePhone();
            if (!result.error) {
                el('ccCountry').value = result.country;
                ContactFields.formatPhoneInput(el('ccPhone'), el('ccCountry').value);
                updateExample();
            }
        });
        el('ccTelegram').addEventListener('input', () => {
            const field = el('ccTelegram');
            const result = ContactFields.telegram(field.value);
            if (field.value && !field.value.startsWith('@')) {
                const cursor = field.selectionStart;
                field.value = result.value;
                field.setSelectionRange(cursor + 1, cursor + 1);
            }
            showFieldError('ccTelegram', 'ccTgError', result.error);
        });
        el('ccForm').addEventListener('submit', save);
        el('ccCancelBtn').addEventListener('click', closeModal);
        el('contactCheckModal').addEventListener('click', event => { if (event.target === event.currentTarget) closeModal(); });
        el('contactCheckModal').addEventListener('keydown', event => {
            if (event.key === 'Escape') { event.preventDefault(); closeModal(); }
            if (event.key !== 'Tab') return;
            const fields = [...el('contactCheckModal').querySelectorAll('input,select,button')].filter(field => !field.disabled);
            const first = fields[0], last = fields[fields.length - 1];
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
        });
    }

    function updateExample() {
        const example = ContactFields.example(el('ccCountry').value);
        el('ccPhone').placeholder = example;
        el('ccPhoneHelp').textContent = t('contact.phone.help', example);
    }

    function showFieldError(field, target, code) {
        el(field).setAttribute('aria-invalid', String(!!code));
        el(target).hidden = !code;
        el(target).textContent = code ? t(errorKey(code)) : '';
    }

    function validatePhone() {
        const result = ContactFields.phone(el('ccPhone').value, el('ccCountry').value);
        showFieldError('ccPhone', 'ccPhoneError', result.error);
        return result;
    }

    function showGlobalError(message) {
        el('ccGlobalError').textContent = message;
        el('ccGlobalError').hidden = false;
    }

    function closeModal() {
        if (busy) return;
        el('contactCheckModal').hidden = true;
        pending = null;
        previousFocus?.focus();
    }

    async function beforeRegister(tournamentId, tournamentName, onReady) {
        if (busy) return;
        let data;
        try {
            const response = await fetch('/api/players/me/contact-status');
            if (!response.ok) throw new Error('Contact status unavailable');
            data = await response.json();
        } catch {
            window.trackClarityRegistrationError?.(tournamentId, 'contact_request_failed');
            window.alert(t('contact.error.connection'));
            return;
        }
        if (data.hasContact) { onReady(); return; }
        ensureModal();
        previousFocus = document.activeElement;
        pending = { tournamentId, onReady };
        el('ccTournamentName').textContent = t('contact.modal.desc', tournamentName);
        el('ccPhone').value = data.telefono || '';
        el('ccTelegram').value = data.telegramUsername ? ContactFields.telegram(data.telegramUsername).value : '';
        const existingPhone = ContactFields.phone(el('ccPhone').value, 'AR');
        el('ccCountry').value = existingPhone.country || 'AR';
        ContactFields.formatPhoneInput(el('ccPhone'), el('ccCountry').value);
        updateExample();
        showFieldError('ccPhone', 'ccPhoneError');
        showFieldError('ccTelegram', 'ccTgError');
        el('ccGlobalError').hidden = true;
        el('contactCheckModal').hidden = false;
        window.trackClarityRegistrationStep?.(tournamentId, 'contact_form_opened');
        el('ccPhone').focus();
    }

    async function save(event) {
        event.preventDefault();
        if (busy || !pending) return;
        const phone = validatePhone();
        const telegram = ContactFields.telegram(el('ccTelegram').value);
        el('ccTelegram').value = telegram.value;
        showFieldError('ccTelegram', 'ccTgError', telegram.error);
        el('ccGlobalError').hidden = true;
        const code = phone.error || telegram.error;
        if (code) {
            window.trackClarityRegistrationError?.(pending.tournamentId, 'contact_' + code.toLowerCase());
            el(phone.error ? 'ccPhone' : 'ccTelegram').focus();
            return;
        }
        busy = true;
        el('ccSaveBtn').disabled = true;
        el('ccCancelBtn').disabled = true;
        el('ccSaveBtn').textContent = t('contact.saving');
        let callback;
        try {
            const response = await fetch('/api/players/me/contact', {
                method:'PATCH', headers:{'Content-Type':'application/json'},
                body:JSON.stringify({telefono:phone.number, phoneCountry:phone.country, telegramUsername:telegram.value || null})
            });
            const data = await response.json();
            if (!response.ok || !data.success) {
                const knownCode = ['PHONE_REQUIRED','PHONE_INVALID','PHONE_COUNTRY_INVALID','PHONE_TOO_SHORT','PHONE_TOO_LONG','TELEGRAM_INVALID','TELEGRAM_IN_USE','PHONE_IN_USE'].includes(data.code);
                if (knownCode && data.field === 'telefono') showFieldError('ccPhone', 'ccPhoneError', data.code);
                else if (knownCode && data.field === 'telegramUsername') showFieldError('ccTelegram', 'ccTgError', data.code);
                else showGlobalError(data.message || t('contact.error.save'));
                window.trackClarityRegistrationError?.(pending.tournamentId, knownCode ? 'contact_' + data.code.toLowerCase() : 'contact_save_rejected');
                el(data.field === 'telegramUsername' ? 'ccTelegram' : 'ccPhone').focus();
                return;
            }
            callback = pending.onReady;
            busy = false;
            closeModal();
        } catch {
            window.trackClarityRegistrationError?.(pending.tournamentId, 'contact_request_failed');
            showGlobalError(t('contact.error.connection'));
        } finally {
            busy = false;
            el('ccSaveBtn').disabled = false;
            el('ccCancelBtn').disabled = false;
            el('ccSaveBtn').textContent = t('contact.btn.save');
        }
        callback?.();
    }
    return { beforeRegister };
})();
