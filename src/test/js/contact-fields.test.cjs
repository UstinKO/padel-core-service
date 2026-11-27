const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const context = {window:{}};
vm.createContext(context);
const base = path.resolve(__dirname, '../../main/resources/static/js');
for (const file of ['vendor/libphonenumber/libphonenumber-max.js','vendor/libphonenumber/examples.mobile.js','contact-fields.js']) {
    vm.runInContext(fs.readFileSync(path.join(base, file),'utf8'), context);
}
const fields = vm.runInContext('ContactFields', context);
test('local and international phones normalize consistently with the server', () => {
    for (const [input,country,expected] of [
        ['011 15-2345-6789','AR','+5491123456789'],
        ['8 (912) 345-67-89','RU','+79123456789'],
        ['+7 (912) 345-67-89','AR','+79123456789'],
        ['+44 20 7946 0018','AR','+442079460018']
    ]) assert.equal(fields.phone(input,country).number,expected);
});
test('phone required, concrete errors, no accidental parsing from text', () => {
    for (const [input,country,error] of [
        ['','AR','PHONE_REQUIRED'],['123','AR','PHONE_TOO_SHORT'],
        ['+549112345678901234567890','AR','PHONE_TOO_LONG'],
        ['call +79123456789','AR','PHONE_INVALID'],['+79123456789','ZZ','PHONE_COUNTRY_INVALID']
    ]) assert.equal(fields.phone(input,country).error,error);
});
test('Telegram optional, auto-prefix, validates username rather than just @', () => {
    assert.equal(fields.telegram('').value,'');
    assert.equal(fields.telegram('valid_user').value,'@valid_user');
    assert.equal(fields.telegram('valid_user').error,undefined);
    for (const input of ['@','bad!','https://t.me/valid_user','@@valid_user','12345']) {
        assert.equal(fields.telegram(input).error,'TELEGRAM_INVALID');
    }
});
test('country examples follow selection', () => {
    assert.equal(fields.example('AR'),'011 15-2345-6789');
    assert.equal(fields.example('RU'),'8 (912) 345-67-89');
});

test('country list has translated calling codes in all supported languages', () => {
    for (const [locale,name] of [['ru','Аргентина'],['es','Argentina'],['en','Argentina']]) {
        const ar = fields.countries(locale).find(country => country.code === 'AR');
        assert.equal(ar.label, name + ' (+54)');
    }
});

test('Argentina and Russia are first, other countries stay alphabetical without duplicates', () => {
    for (const locale of ['es', 'ru', 'en']) {
        const countries = fields.countries(locale);
        assert.equal(countries[0].code, 'AR');
        assert.equal(countries[1].code, 'RU');
        assert.equal(new Set(countries.map(country => country.code)).size, countries.length);
        for (let index = 3; index < countries.length; index++) {
            assert.ok(countries[index - 1].name.localeCompare(countries[index].name, locale) <= 0);
        }
    }
});

test('phone digits acquire country-specific separators without changing the saved number', () => {
    for (const [digits,country,display,number] of [
        ['0111523456789','AR','011 15-2345-6789','+5491123456789'],
        ['89123456789','RU','8 (912) 345-67-89','+79123456789'],
        ['2025550123','US','(202) 555-0123','+12025550123'],
        ['+79123456789','AR','+7 912 345 67 89','+79123456789']
    ]) {
        assert.equal(fields.formatPhone(digits,country),display);
        assert.equal(fields.phone(display,country).number,number);
    }
    assert.equal(fields.formatPhone('  +7 (912) 345-67-89  ','AR'),'+7 912 345 67 89');
});

test('formatting never turns text or an extension into another plausible phone', () => {
    for (const value of ['call +79123456789','+79123456789 ext 123','++79123456789','+7 912 345 67 89#123']) {
        assert.equal(fields.formatPhone(value,'RU'),value);
        assert.equal(fields.phone(value,'RU').error,'PHONE_INVALID');
    }
});

function phoneField(value = '') {
    return {
        value, selectionStart:value.length, selectionEnd:value.length, selectionDirection:'none',
        listeners:{},
        addEventListener(type, listener) { this.listeners[type] = listener; },
        setSelectionRange(start,end,direction = 'none') {
            this.selectionStart = start; this.selectionEnd = end; this.selectionDirection = direction;
        }
    };
}
function editPhone(field, inputType, text = '') {
    field.listeners.beforeinput({target:field, inputType});
    let start = field.selectionStart, end = field.selectionEnd;
    if (start === end && inputType === 'deleteContentBackward') start = Math.max(0,start - 1);
    if (start === end && inputType === 'deleteContentForward') end = Math.min(field.value.length,end + 1);
    field.value = field.value.slice(0,start) + text + field.value.slice(end);
    field.setSelectionRange(start + text.length,start + text.length);
    field.listeners.input({target:field,inputType});
}

test('typing, deleting separators, replacing a selection and clearing work in the same field', () => {
    const input = phoneField(), country = phoneField(); country.value = 'RU';
    fields.bindPhone(input,country,() => {});
    for (const digit of '9123456789') editPhone(input,'insertText',digit);
    assert.equal(input.value,'912 345-67-89');
    assert.equal(input.selectionStart,input.value.length);
    input.setSelectionRange(8,8);
    editPhone(input,'deleteContentBackward');
    assert.equal(input.value.replace(/\D/g,''),'912346789');
    assert.equal(input.selectionStart,6);
    input.value = '912 345-67-89'; input.setSelectionRange(3,3);
    editPhone(input,'deleteContentForward');
    assert.equal(input.value.replace(/\D/g,''),'912456789');
    input.setSelectionRange(0,input.value.length);
    editPhone(input,'insertFromPaste','+54 (9) 11-2345-6789');
    assert.equal(input.value,'+54 9 11 2345 6789');
    input.setSelectionRange(0,input.value.length);
    editPhone(input,'deleteContentBackward');
    assert.equal(input.value,'');
    assert.equal(input.selectionStart,0);
});

test('country changes reformat digits and composition is not interrupted', () => {
    const input = phoneField('2025550123'), country = phoneField(); country.value = 'US';
    fields.bindPhone(input,country,() => {});
    country.listeners.change();
    assert.equal(input.value,'(202) 555-0123');
    country.value = 'AR'; country.listeners.change();
    assert.equal(input.value,'202 555-0123');
    input.value = '0111523456789'; input.setSelectionRange(13,13);
    input.listeners.input({isComposing:true});
    assert.equal(input.value,'0111523456789');
    input.listeners.compositionend({isComposing:false});
    assert.equal(input.value,'011 15-2345-6789');
});
