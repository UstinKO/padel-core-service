import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1');
const {chromium} = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({executablePath: process.env.CHROME_EXECUTABLE});
const output = resolve('target/organizer-contact-qa');
await mkdir(output, {recursive:true});
const report = [];
const labels = {ru:['Имя организатора','WhatsApp организатора','Проверить ссылку WhatsApp'],es:['Nombre del organizador','WhatsApp del organizador','Probar enlace de WhatsApp'],en:['Organizer name','Organizer WhatsApp','Test WhatsApp link']};
async function openContext(account, lang='es', width=1440) {
    const context=await browser.newContext({viewport:{width,height:1000}});
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page=await context.newPage();
    const errors=[];page.on('pageerror', e=>errors.push(e.message));
    if(account) {
        await page.goto(new URL('/login?lang='+lang,base).href);
        await page.locator('#username').fill(account.email);
        await page.locator('#password').fill(fixture.password);
        await Promise.all([page.waitForURL(url=>url.pathname!=='/login'), page.locator('form.auth-form button[type=submit]').click()]);
    }
    return {context,page,errors};
}
async function go(page,path,lang='es') {
    const response=await page.goto(new URL(path+'?lang='+lang,base).href);
    assert.equal(response.status(),200,path);
}
async function check(page,input,expected,country) {
    await page.locator('#organizerPhone').fill(input);
    await page.waitForFunction(expected=>document.querySelector('[data-contact-link]')?.getAttribute('href')===expected,expected);
    assert.equal(await page.locator('[data-contact-preview]').isVisible(),true);
    if(country) assert.equal(await page.locator('#organizerCountry').inputValue(),country);
}
async function save(page,id) {
    const invalid = await page.locator('#tournamentForm').evaluate(form=>[...form.elements].filter(el=>el.willValidate && !el.validity.valid).map(el=>({id:el.id,message:el.validationMessage})));
    assert.deepEqual(invalid,[], 'Form can be submitted');
    await Promise.all([page.waitForURL(url=>url.pathname===`/admin/tournaments/${id}`),page.locator('#tournamentForm button[type=submit]').click()]);
}
async function publicLink(id,expected,lang,width=390) {
    const {context,page}=await openContext(null,lang,width);
    await go(page,`/torneo/${id}`,lang);
    const contact=page.locator('.contact-info .whatsapp-contact-link');
    if(expected) assert.equal(await contact.getAttribute('href'),expected);
    else assert.equal(await contact.count(),0);
    if(width===390) await page.locator('.contact-info').screenshot({path:resolve(output,`public-${lang}-${id}.png`)});
    await context.close();
}
try {
    for(const account of fixture.accounts.filter(a=>a.role!=='ADMIN')) {
        const {context,page,errors}=await openContext(account);
        for(const lang of ['ru','es','en']) {
            for(const width of [320,390,1440]) {
                await page.setViewportSize({width,height:1000});
                await go(page,`/admin/tournaments/${account.events[0]}/edit`,lang);
                assert.equal(await page.locator('label[for=organizerName]').innerText(),labels[lang][0]);
                assert.equal(await page.locator('label[for=organizerPhone]').innerText(),labels[lang][1]);
                assert.equal(await page.locator('[data-contact-link]').innerText(),labels[lang][2]);
                assert.equal(await page.locator('#organizerPhone').getAttribute('type'),'tel');
                await check(page,'011 15-2345-6789','https://wa.me/5491123456789','AR');
                await page.locator('#organizerCountry').selectOption('ES');
                await check(page,'612 34 56 78','https://wa.me/34612345678','ES');
                await check(page,'https://api.whatsapp.com/send?phone=5493512345678&text=Hola','https://wa.me/5493512345678','AR');
                await page.locator('#organizerPhone').fill('Wrong name +5491123456789');
                await page.waitForFunction(()=>document.querySelector('#organizerPhone')?.getAttribute('aria-invalid')==='true');
                assert.equal(await page.locator('[data-contact-preview]').isVisible(),false);
                assert.equal(await page.locator('[data-contact-link]').getAttribute('href'),null);
                const geometry=await page.locator('#organizerContactForm').evaluate(root=>[...root.querySelectorAll('input,select')].every(el=>{
                    const r=el.getBoundingClientRect();return r.left>=0 && r.right<=innerWidth+1;
                }));assert.equal(geometry,true,`${account.role}/${lang}/${width}`);
                if(account.role==='SUPER_ADMIN' && lang==='ru') {
                    await check(page,'+54 9 11 2345-6789','https://wa.me/5491123456789','AR');
                    await page.locator('#organizerContactForm').screenshot({path:resolve(output,`form-${width}.png`)});
                }
                report.push({role:account.role,lang,width,checks:'labels, AR national, international link, ES country, invalid, geometry'});
            }
        }
        for(const id of account.events) {
            await go(page,`/admin/tournaments/${id}/edit`,'ru');
            if (id===account.events.at(-1)) assert.equal(await page.locator('#modalidad').inputValue(),'DOBLES');
            await page.locator('#organizerName').fill('QA organizer separate');
            await check(page,'https://wa.me/34612345678?text=Hola','https://wa.me/34612345678','ES');
            await save(page,id);
            assert.equal(await page.locator('.contact-info .whatsapp-contact-link').getAttribute('href'),'https://wa.me/34612345678');
            await go(page,`/admin/tournaments/${id}/edit`,'ru');
            assert.equal(await page.locator('#organizerPhone').inputValue(),'+34612345678');
            assert.equal(await page.locator('#organizerName').inputValue(),'QA organizer separate');
            assert.equal(await page.locator('#organizerCountry').inputValue(),'ES');
            if (id===account.events.at(-1)) assert.equal(await page.locator('#modalidad').inputValue(),'DOBLES');
            await go(page,`/admin/tournaments/${id}/copy`,'ru');
            assert.equal(await page.locator('#organizerPhone').inputValue(),'+34612345678');
            await publicLink(id,'https://wa.me/34612345678','ru');
            report.push({role:account.role,id,checks:'save, reopen, copy, public contact'});
        }
        await go(page,`/admin/tournaments/${account.invalidEvent}/edit`,'en');
        assert.match(await page.locator('.organizer-contact-legacy').innerText(),/QA contact needs correction/);
        assert.equal(await page.locator('#organizerPhone').inputValue(),'');
        await publicLink(account.invalidEvent,null,'en');
        // Direct form submission must also validate when the preview is unavailable.
        await page.route('**/admin/api/organizer-contact/preview',route=>route.abort());
        await page.locator('#infoDetallada').fill('Preserve description through server error');
        await page.locator('#organizerPhone').fill('not a phone');
        await Promise.all([page.waitForNavigation(),page.locator('#tournamentForm button[type=submit]').click()]);
        assert.equal(await page.locator('#infoDetallada').inputValue(),'Preserve description through server error');
        assert.equal(await page.locator('#organizerPhone').inputValue(),'not a phone');
        assert.equal(await page.locator('#organizerPhoneServerError').isVisible(),true);
        assert.equal(await page.locator('#organizerContactForm').getAttribute('data-event-id'),String(account.invalidEvent));
        assert.ok((await page.locator('#tournamentForm').getAttribute('action')).endsWith(`/admin/tournaments/${account.invalidEvent}/edit`));
        if(account.role!=='SUPER_ADMIN') {
            const foreign=fixture.accounts.find(a=>a.role==='SUPER_ADMIN').events[0];
            const response=await page.request.get(new URL(`/admin/tournaments/${foreign}/edit`,base).href,{headers:{Accept:'application/json'}});
            assert.equal(response.status(),403);
        }
        assert.deepEqual(errors,[]);
        await context.close();
    }
    // The same form creates every format; all saves stay in the isolated database.
    const {context,page}=await openContext(fixture.accounts[0],'en');
    for(const type of ['KING_OF_COURT','AMERICANO','AMERICANO_TEAMS','CANCHA_ABIERTA','PADEL_CLINIC']) {
        await go(page,'/admin/tournaments/new','en');
        assert.equal(await page.locator('#organizerCountry').inputValue(),'AR');
        await page.locator('#clubId').selectOption(String(fixture.clubId));
        await page.locator('#fechaInicio').fill('2098-02-01');
        await page.locator('#horaInicio').fill('14:00');
        await page.locator('#generoFormato').selectOption('MASCULINO');
        await page.locator('#tipo').selectOption(type);
        if(await page.locator('#modalidad').isVisible()) await page.locator('#modalidad').selectOption('INDIVIDUAL');
        await page.locator('#categoriaNivel').selectOption('C7');
        await page.locator('#cupoMax').fill('16');
        await page.locator('#precio').fill('100');
        await page.locator('#organizerName').fill('QA new organizer');
        await check(page,'+7 (916) 123-45-67','https://wa.me/79161234567','RU');
        await Promise.all([page.waitForURL(url=>/^\/admin\/tournaments\/\d+$/.test(url.pathname)),page.locator('#tournamentForm button[type=submit]').click()]);
        const id=Number(new URL(page.url()).pathname.split('/').at(-1));
        await go(page,`/admin/tournaments/${id}/edit`,'en');
        assert.equal(await page.locator('#organizerPhone').inputValue(),'+79161234567');
        assert.equal(await page.locator('#organizerCountry').inputValue(),'RU');
        await publicLink(id,'https://wa.me/79161234567','en',1440);
        report.push({type,checks:'create, save, reopen, public number'});
    }
    await context.close();
    for(const lang of ['ru','es','en']) {
        for(const width of [320,390,1440]) await publicLink(fixture.accounts[0].events[0],'https://wa.me/34612345678',lang,width);
    }
    const readonly=await openContext(fixture.accounts.find(a=>a.role==='ADMIN'));
    await go(readonly.page,`/admin/tournaments/${fixture.accounts[0].invalidEvent}`);
    assert.equal(await readonly.page.locator('a[href$="/edit"]').count(),0);
    await readonly.context.close();
    await writeFile(resolve(output,'result.json'),JSON.stringify({scenarios:report.length,report,physicalDevicesTested:false},null,2));
    console.log(JSON.stringify({ok:true,scenarios:report.length}));
} finally {await browser.close();}
