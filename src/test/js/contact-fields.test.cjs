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
