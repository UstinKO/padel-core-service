import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveLocale} from '../src/i18n/store.ts';
import {translate} from '../src/i18n/translate.ts';
import {messages} from '../src/i18n/messages.ts';
test('auto-detect regional browser language, default Spanish, honour saved manual choice',()=>{
 assert.equal(resolveLocale(null,null,'en-US'),'en');
 assert.equal(resolveLocale(null,null,'ru-RU'),'ru');
 assert.equal(resolveLocale(null,null,'es-AR'),'es');
 assert.equal(resolveLocale(null,null,'fr-FR'),'es');
 assert.equal(resolveLocale(null,'ru','es-AR'),'ru');
 assert.equal(resolveLocale('en','ru','es-AR'),'en');
 assert.equal(resolveLocale('invalid','invalid','en-GB'),'en');
});
test('translate UI while preserving original names and surrounding spaces',()=>{
 assert.equal(translate(' Войти ','es'),' Ingresar ');
 assert.equal(translate('Александр Волков','en'),'Александр Волков');
 assert.equal(translate('  Привет  ','ru'),'  Привет  ');
 assert.equal(translate('2 места','es'),'2 lugares');
 assert.equal(translate('Бонусные очки: победа +3 · поражение +1','en'),'Bonus points: win +3 · loss +1');
});
test('every translated UI template keeps its interpolation slots',()=>{
 const slots=s=>(s.match(/\{\d+\}/g)||[]).sort();
 for (const [key,entry] of Object.entries(messages)) for (const locale of ['es','en']) {
  assert.ok(entry[locale].trim(),key);
  assert.deepEqual(slots(entry[locale]),slots(key),key+' / '+locale);
 }
});
