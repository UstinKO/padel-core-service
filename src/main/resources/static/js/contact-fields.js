const ContactFields = (() => {
    'use strict';
    function phone(value, country) {
        const input = value.trim();
        if (!input) return { error: 'PHONE_REQUIRED' };
        if (input.length > 80 || !/^[+0-9\s().-]+$/.test(input)) return { error: 'PHONE_INVALID' };
        if (!libphonenumber.isSupportedCountry(country)) return { error: 'PHONE_COUNTRY_INVALID' };
        const length = libphonenumber.validatePhoneNumberLength(input, country);
        if (length === 'TOO_SHORT') return { error: 'PHONE_TOO_SHORT' };
        if (length === 'TOO_LONG') return { error: 'PHONE_TOO_LONG' };
        const number = libphonenumber.parsePhoneNumberFromString(input, { defaultCountry: country, extract: false });
        if (!number || !number.isPossible() || number.ext) return { error: 'PHONE_INVALID' };
        return { number: number.number, display: number.formatInternational(), country: number.country || country };
    }
    function telegram(value) {
        const input = value.trim();
        const normalized = input && !input.startsWith('@') ? '@' + input : input;
        return normalized && !/^@[A-Za-z][A-Za-z0-9_]{4,31}$/.test(normalized)
            ? { error: 'TELEGRAM_INVALID', value: normalized } : { value: normalized };
    }
    function example(country) {
        return libphonenumber.getExampleNumber(country, window.ContactPhoneExamples)?.formatNational()
            || '+' + libphonenumber.getCountryCallingCode(country);
    }
    function countries(locale) {
        const names = typeof Intl.DisplayNames === 'function' ? new Intl.DisplayNames([locale], {type:'region'}) : null;
        const priority = { AR: 0, RU: 1 };
        return libphonenumber.getCountries().map(code => ({code, name:names?.of(code) || code}))
            .sort((a,b) => (priority[a.code] ?? 2) - (priority[b.code] ?? 2)
                || a.name.localeCompare(b.name, locale))
            .map(({code,name}) => ({code, name, label:`${name} (+${libphonenumber.getCountryCallingCode(code)})`}));
    }
    function formatPhone(value, country) {
        if (!libphonenumber.isSupportedCountry(country) || !/^\+?[0-9\s().-]*$/.test(value.trim())) return value;
        return new libphonenumber.AsYouType(country).input(value.trim());
    }
    function phoneCursor(value, tokens) {
        if (!tokens) return 0;
        for (let index = 0; index < value.length; index++) {
            if (/[+0-9]/.test(value[index]) && --tokens === 0) return index + 1;
        }
        return value.length;
    }
    function formatPhoneInput(input, country) {
        const previous = input.value;
        const formatted = formatPhone(previous, country);
        if (previous === formatted) return;
        const position = offset => offset === previous.length ? formatted.length
            : phoneCursor(formatted, previous.slice(0, offset).replace(/[^+0-9]/g, '').length);
        const start = position(input.selectionStart ?? previous.length);
        const end = position(input.selectionEnd ?? previous.length);
        const direction = input.selectionDirection || 'none';
        input.value = formatted;
        input.setSelectionRange(start, end, direction);
    }
    function deletePhoneSeparator(event) {
        const input = event.target;
        const position = input.selectionStart;
        if (event.isComposing || position == null || position !== input.selectionEnd) return;
        const backward = event.inputType === 'deleteContentBackward';
        if (!backward && event.inputType !== 'deleteContentForward') return;
        let index = backward ? position - 1 : position;
        const isSeparator = char => /[\s().-]/.test(char || '');
        if (!isSeparator(input.value[index])) return;
        // Удаление разделителя также удаляет соседнюю цифру, иначе форматтер вернёт разделитель на место.
        while (index >= 0 && index < input.value.length && isSeparator(input.value[index])) index += backward ? -1 : 1;
        if (index >= 0 && index < input.value.length && /[+0-9]/.test(input.value[index])) {
            input.setSelectionRange(backward ? index : position, backward ? position : index + 1);
        }
    }
    function bindPhone(input, country, onInput) {
        const update = event => {
            if (event?.isComposing) return;
            formatPhoneInput(input, country.value);
            onInput();
        };
        input.addEventListener('beforeinput', deletePhoneSeparator);
        input.addEventListener('input', update);
        input.addEventListener('compositionend', update);
        country.addEventListener('change', update);
    }
    return { phone, telegram, example, countries, formatPhone, formatPhoneInput, bindPhone };
})();
