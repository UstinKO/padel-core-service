import assert from 'node:assert/strict';
import {readFile, writeFile} from 'node:fs/promises';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl).origin;
assert.equal(new URL(origin).hostname, '127.0.0.1');
const browser = await chromium.launch();
const output = 'target/telegram-message-preview-qa';
const checks = [];
let page;
const endpoint = '/api/admin/telegram/messages/campaigns';
const translate = '/api/admin/telegram/messages/translations';
const ru = 'Играем в 18:30 — https://1-padel.com';
const es = 'Jugamos a las 18:30 — https://1-padel.com';
const en = 'We play at 18:30 — https://1-padel.com';
async function preview() {
    const response = page.waitForResponse(r => new URL(r.url()).pathname === endpoint && r.request().method() === 'POST');
    await page.locator('[data-preview]').click();
    const result = await response; assert.equal(result.status(), 200);
    await page.locator('[data-preview-result]').waitFor({state:'visible'});
    return result.json();
}
try {
    for (const [language,width,source] of [['ru',1440,ru],['es',390,es],['en',320,ru]]) {
        const context = await browser.newContext({locale:language,viewport:{width,height:950}});
        await context.route('**/*', route => new URL(route.request().url()).origin === origin ? route.continue() : route.abort());
        page = await context.newPage(); page.setDefaultTimeout(15000);
        const errors = []; page.on('pageerror', error => errors.push(error.message));
        let translations = 0; page.on('request', request => {if(new URL(request.url()).pathname === translate) translations++;});
        await page.goto(origin + '/login?lang=' + language);
        const cookie = page.locator('#cookieConsentBanner');
        if(await cookie.isVisible()) await cookie.locator('.cookie-btn-reject').click();
        await page.locator('#username').fill(fixture.ownerEmail); await page.locator('#password').fill(fixture.password);
        await Promise.all([page.waitForURL(url => url.pathname === '/admin'),page.locator('form.auth-form button[type=submit]').click()]);
        await page.goto(origin + '/admin/tournaments/' + fixture.eventId + '/telegram/messages');
        await page.locator('[data-candidates] input').first().waitFor();
        await page.locator('[data-groups] input[value=ALL]').check();
        await page.locator('[data-message-text]').fill(source);
        const draft = await preview();
        assert.equal(translations,1); assert.equal(draft.recipients.length,3);
        assert.equal(draft.sendingAvailable,true);
        assert.equal(await page.locator('[data-send]').isEnabled(),true);
        assert.deepEqual(new Set(draft.recipients.map(p=>p.language)),new Set(['es','ru','en']));
        assert.equal(await page.locator('[data-translation=ru]').inputValue(),ru);
        assert.equal(await page.locator('[data-translation=es]').inputValue(),es);
        assert.equal(await page.locator('[data-translation=en]').inputValue(),en);
        for(const lang of ['es','ru','en']) assert.equal(await page.locator(`[data-preview-versions] [data-message-language=${lang}]`).count(),1);
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth > window.innerWidth + 1),false);
        await page.screenshot({path:`${output}/languages-${language}-${width}.png`,fullPage:true});
        if(language==='ru') {
            await page.locator('[data-translations] summary').click();
            await page.locator('[data-translation=es]').fill('Nos vemos a las 18:30 — https://1-padel.com');
            assert.equal(await page.locator('[data-preview-result]').isVisible(),false);
            const changed = await preview(); assert.notEqual(changed.campaignId,draft.campaignId); assert.equal(translations,1);
            await page.locator('[data-send]').click();
            await page.locator('[data-send-dialog]').waitFor({state:'visible'});
            assert.match(await page.locator('[data-confirm-versions]').innerText(),/Nos vemos/);
            assert.equal(await page.locator('[data-confirm-people] li').count(),3);
            await Promise.all([page.waitForResponse(r=>r.url().endsWith('/confirm')&&r.request().method()==='POST'),page.locator('[data-confirm-submit]').click()]);
            await page.waitForFunction(()=>[...document.querySelectorAll('[data-campaign-state]')].some(n=>n.dataset.campaignState==='FINISHED'));
            await page.locator(`[data-campaign-id="${changed.campaignId}"] button`).click();
            assert.match(await page.locator('[data-history-versions]').innerText(),/Nos vemos/);
            assert.match(await page.locator('[data-history-versions]').innerText(),/We play/);
            await page.locator('[data-history-close]').click();
            checks.push('Mixed ES/RU/EN profiles: edited translation is frozen, confirmed, sent once each and shown in history');
        }
        if(language==='es') {
            await page.locator('[data-message-text]').fill('Texto nuevo');
            assert.equal(await page.locator('[data-translations]').isVisible(),false);
            await page.route('**/api/admin/telegram/messages/translations',route=>route.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({code:'TELEGRAM_TRANSLATION_UNAVAILABLE'})}));
            let campaigns = 0; const count = request => {if(new URL(request.url()).pathname===endpoint&&request.method()==='POST')campaigns++;};
            page.on('request',count); await page.locator('[data-preview]').click();
            await page.waitForFunction(()=>document.querySelector('[data-compose-status]').dataset.error==='true');
            assert.match(await page.locator('[data-compose-status]').innerText(),/traductor/);
            assert.equal(await page.locator('[data-send]').isEnabled(),false); assert.equal(campaigns,0);
            page.off('request',count); await page.unroute('**/api/admin/telegram/messages/translations');
            checks.push('Spanish source translates for other profile languages; unavailable provider does not prepare or send a campaign');
        }
        if(language==='en') {
            await page.locator('[data-message-text]').fill('Играем завтра');
            let release; const gate = new Promise(resolve=>{release=resolve;}); let started;
            const began = new Promise(resolve=>{started=resolve;});
            await page.route('**/api/admin/telegram/messages/translations',async route=>{const response=await route.fetch();started();await gate;await route.fulfill({response});});
            await page.locator('[data-preview]').click(); await began;
            await page.locator('[data-message-text]').fill('Другой исходник'); release();
            await page.waitForFunction(()=>!document.querySelector('[data-preview]').disabled);
            assert.equal(await page.locator('[data-translations]').isVisible(),false);
            assert.equal(await page.locator('[data-preview-result]').isVisible(),false);
            assert.equal(await page.locator('[data-message-text]').inputValue(),'Другой исходник');
            checks.push('Delayed translation cannot overwrite a changed source or re-enable sending');
        }
        assert.deepEqual(errors,[]); checks.push(`${language} ${width}: source, translations, recipient languages and responsive layout`);
        await context.close();
    }
    await writeFile(`${output}/languages-report.json`,JSON.stringify({passed:true,checks},null,2));
    console.log(JSON.stringify({passed:true,checks}));
} catch(error) {if(page) await page.screenshot({path:`${output}/languages-failure.png`,fullPage:true}).catch(()=>{});console.error(error.stack);process.exitCode=1;}
finally {await browser.close();}
