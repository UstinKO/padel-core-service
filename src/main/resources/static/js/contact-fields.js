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
        return libphonenumber.getCountries().map(code => ({code, name:names?.of(code) || code}))
            .sort((a,b) => a.name.localeCompare(b.name, locale))
            .map(({code,name}) => ({code, label:`${name} (+${libphonenumber.getCountryCallingCode(code)})`}));
    }
    return { phone, telegram, example, countries };
})();
