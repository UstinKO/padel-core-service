import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1');
const {chromium} = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE});
const output = resolve('target/all-levels-qa');
await mkdir(output,{recursive:true});
const report=[];
const labels={ru:'Все уровни',es:'Todos los niveles',en:'All levels'};
const types=['KING_OF_COURT','AMERICANO','AMERICANO_TEAMS','CANCHA_ABIERTA','PADEL_CLINIC'];
const genders=['MASCULINO','FEMENINO','MIXTO'];
async function go(page,path,lang='es') {
    const response=await page.goto(new URL(path+'?lang='+lang,base).href);
    assert.equal(response.status(),200,path);
}
async function checkOption(page,lang) {
    const options=page.locator('#categoriaNivel option[value="TODOS"]');
    assert.equal(await options.count(),1);
    assert.equal(await options.innerText(),labels[lang]);
    assert.equal(await options.evaluate(option=>!option.disabled && !option.hidden),true);
}
async function save(page) {
    const invalid=await page.locator('#tournamentForm').evaluate(form=>[...form.elements]
        .filter(el=>el.willValidate && !el.validity.valid).map(el=>({id:el.id,message:el.validationMessage})));
    assert.deepEqual(invalid,[]);
    await Promise.all([page.waitForURL(url=>/^\/admin\/tournaments\/\d+$/.test(url.pathname)),
        page.locator('#tournamentForm button[type=submit]').click()]);
    return Number(new URL(page.url()).pathname.split('/').at(-1));
}
try {
    for(const [index,account] of fixture.accounts.entries()) {
        const context=await browser.newContext({viewport:{width:[1440,390,320][index],height:1000}});
        await context.route('**/*',route=>new URL(route.request().url()).origin===base.origin?route.continue():route.abort());
        await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
        const page=await context.newPage();const errors=[];
        page.on('pageerror',error=>errors.push(error.message));
        await go(page,'/login');
        await page.locator('#username').fill(account.email);await page.locator('#password').fill(fixture.password);
        await Promise.all([page.waitForURL(url=>url.pathname!=='/login'),page.locator('form.auth-form button[type=submit]').click()]);
        for(const [eventIndex,id] of account.events.entries()) {
            const lang=['ru','es','en'][eventIndex%3];
            await go(page,`/admin/tournaments/${id}/edit`,lang);
            const originalType=await page.locator('#tipo').inputValue();
            const originalModality=await page.locator('#modalidad').inputValue();
            for(const gender of genders) {
                await page.locator('#generoFormato').selectOption(gender);
                await checkOption(page,lang);
                await page.locator('#categoriaNivel').selectOption('TODOS');
                const specific={MASCULINO:'C7',FEMENINO:'D7',MIXTO:'SUMA_13'}[gender];
                await page.locator('#categoriaNivel').selectOption(specific);
                assert.equal(await page.locator('#categoriaNivel').inputValue(),specific);
                await page.locator('#categoriaNivel').selectOption('TODOS');
            }
            if(eventIndex===0) {
                for(const type of types) {
                    await page.locator('#tipo').selectOption(type);
                    assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
                    await checkOption(page,lang);
                }
                await page.locator('#tipo').selectOption(originalType);
                for(const next of ['en','es','ru']) {
                    if(!await page.locator(`.lang-switcher-option[data-lang="${next}"]`).isVisible()) {
                        await page.locator('#navbarToggler').click();
                    }
                    await Promise.all([page.waitForURL(url=>url.searchParams.get('lang')===next),
                        page.locator(`.lang-switcher-option[data-lang="${next}"]`).click()]);
                    assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
                    assert.equal(await page.locator('#generoFormato').inputValue(),'MIXTO');
                    await checkOption(page,next);
                }
            }
            if(eventIndex===account.events.length-1) {
                assert.equal(originalModality,'DOBLES');
                assert.equal(await page.locator('#modalidad').inputValue(),'DOBLES');
            }
            await save(page);
            await go(page,`/admin/tournaments/${id}/edit`,lang);
            assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
            await checkOption(page,lang);
            await go(page,`/admin/tournaments/${id}/copy`,lang);
            assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
            if(eventIndex===0) {
                const copied=await save(page);
                assert.notEqual(copied,id);
                await go(page,`/admin/tournaments/${copied}/edit`,lang);
                assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
            }
            const geometry=await page.locator('#categoriaNivel').evaluate(el=>{
                const r=el.getBoundingClientRect();return r.left>=0 && r.right<=innerWidth+1;
            });assert.equal(geometry,true);
            await go(page,`/admin/tournaments/${id}`,lang);
            assert.equal(await page.locator('.badge-level').innerText(),labels[lang]);
            report.push({role:account.role,id,originalType,originalModality,checks:'matrix, save, reopen, copy, display'});
        }
        if(index===0) {
            for(const type of types) for(const gender of genders) {
                await go(page,'/admin/tournaments/new','en');
                await page.locator('#clubId').selectOption(String(fixture.clubId));
                await page.locator('#fechaInicio').fill('2098-02-01');await page.locator('#horaInicio').fill('14:00');
                await page.locator('#generoFormato').selectOption(gender);
                await page.locator('#tipo').selectOption(type);
                if(await page.locator('#modalidad').isVisible()) await page.locator('#modalidad').selectOption('INDIVIDUAL');
                await checkOption(page,'en');await page.locator('#categoriaNivel').selectOption('TODOS');
                await page.locator('#cupoMax').fill('16');await page.locator('#precio').fill('100');
                await page.locator('#organizerName').fill('All levels QA');await page.locator('#organizerPhone').fill('+5491123456789');
                await page.waitForFunction(()=>document.querySelector('[data-contact-link]')?.getAttribute('href')==='https://wa.me/5491123456789');
                const id=await save(page);
                await go(page,`/admin/tournaments/${id}/edit`,'en');
                assert.equal(await page.locator('#categoriaNivel').inputValue(),'TODOS');
                for(const lang of ['ru','es','en']) {
                    await go(page,`/torneo/${id}`,lang);
                    assert.equal(await page.locator('.badge-level').innerText(),labels[lang]);
                }
                report.push({type,gender,id,checks:'create, persist, public RU ES EN'});
            }
        }
        for(const lang of ['ru','es','en']) for(const width of [320,390,1440]) {
            await page.setViewportSize({width,height:1000});
            await go(page,`/admin/tournaments/${account.events[0]}/edit`,lang);
            await checkOption(page,lang);
            await page.locator('#categoriaNivel').screenshot({path:resolve(output,`level-${account.role}-${lang}-${width}.png`)});
        }
        if(account.role!=='SUPER_ADMIN') {
            const response=await page.request.get(new URL(`/admin/tournaments/${fixture.accounts[0].events[0]}/edit`,base).href,
                {headers:{Accept:'application/json'}});
            assert.equal(response.status(),403);
        }
        assert.deepEqual(errors,[]);await context.close();
    }
    await writeFile(resolve(output,'result.json'),JSON.stringify({ok:true,scenarios:report.length,report},null,2));
    console.log(JSON.stringify({ok:true,scenarios:report.length}));
} finally {await browser.close();}
