import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture=JSON.parse(await readFile(process.argv[2],'utf8'));
const origin=new URL(fixture.baseUrl);
assert.equal(origin.hostname,'127.0.0.1');
const output='target/telegram-connections-qa';
await mkdir(output,{recursive:true});
const browser=await chromium.launch();
const checks=[],errors=[];
const decline={ru:'Не пользуюсь Telegram',es:'No uso Telegram',en:'I don’t use Telegram'};
let page;
async function session(scenario,email=scenario.email) {
    const context=await browser.newContext({locale:scenario.language,viewport:{width:scenario.width,height:1000}});
    await context.route('**/*',route=>new URL(route.request().url()).origin===origin.origin?route.continue():route.abort());
    page=await context.newPage();page.setDefaultTimeout(15000);
    page.on('pageerror',error=>errors.push(error.message));
    await page.goto(fixture.baseUrl+'/login?lang='+scenario.language);
    const cookies=page.locator('#cookieConsentBanner');
    if(await cookies.isVisible()) await cookies.locator('.cookie-btn-reject').click();
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url=>url.pathname===(email===fixture.adminEmail?'/admin':'/players/dashboard')),
        page.locator('form.auth-form button[type=submit]').click()]);
    return context;
}
async function state() {
    const response=await page.request.get(fixture.baseUrl+'/api/players/me/telegram');
    assert.equal(response.status(),200);return response.json();
}
async function cabinet() {
    const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/players/me/telegram');
    await page.goto(fixture.baseUrl+'/players/dashboard');await response;
}
async function profile() {
    await page.goto(fixture.baseUrl+'/perfil');
    await page.locator('[data-tg-profile]').waitFor({state:'visible'});
}
async function noOverflow(selector) {
    const overflow=await page.evaluate(selector=>{
        const element=selector?document.querySelector(selector):document.documentElement;
        return selector?element.getBoundingClientRect().right>innerWidth+1 || element.scrollWidth>element.clientWidth+1
            :element.scrollWidth>innerWidth+1;
    },selector);
    assert.equal(overflow,false);
}
try {
    for(const scenario of fixture.scenarios.slice(0,3)) {
        const context=await session(scenario);
        const banner=page.locator('[data-tg-invitation-banner]');
        await banner.waitFor({state:'visible'});
        assert.equal(await banner.locator('[data-tg-dismiss]').innerText(),decline[scenario.language]);
        await noOverflow();
        await page.screenshot({path:output+'/'+scenario.language+'-'+scenario.width+'-banner.png',fullPage:true});
        await banner.locator('a').click();
        await page.locator('[data-telegram-join][data-state="READY"]').waitFor();
        assert.equal(await page.locator('[data-join-submit]').count(),1);
        assert.equal((await state()).connected,false);
        await cabinet();
        await banner.locator('[data-tg-dismiss]').focus();
        await page.keyboard.press('Enter');
        await banner.waitFor({state:'hidden'});
        assert.equal((await state()).invitationDismissed,true);
        assert.equal((await state()).preference,'UNSPECIFIED');
        await cabinet();
        assert.equal(await banner.isVisible(),false);
        await profile();
        const root=page.locator('[data-tg-profile]');
        assert.equal(await root.locator('[data-tg-connect]').isVisible(),true);
        await root.locator('[data-tg-restore-invitation]').click();
        await root.locator('[data-tg-invitation-choice]').waitFor({state:'hidden'});
        assert.equal((await state()).invitationDismissed,false);
        await cabinet();
        await banner.waitFor({state:'visible'});
        await page.route('**/api/players/me/telegram/invitation',route=>route.fulfill({status:503,body:'{}'}));
        await banner.locator('[data-tg-dismiss]').click();
        await banner.locator('[data-tg-status][data-error="true"]').waitFor();
        assert.equal(await banner.isVisible(),true);
        assert.equal((await state()).invitationDismissed,false);
        await page.unroute('**/api/players/me/telegram/invitation');
        await banner.locator('[data-tg-dismiss]').click();
        await banner.waitFor({state:'hidden'});
        checks.push(scenario.language+' '+scenario.width+': optional banner, direct short join, keyboard decline, persistent account choice, profile restore and failed-save recovery');
        await context.close();
    }
    for(const scenario of fixture.scenarios.slice(3)) {
        const context=await session(scenario);
        await cabinet();
        assert.equal(await page.locator('[data-tg-invitation-banner]').isVisible(),false);
        checks.push(scenario.state+': no invitation on entry');
        await context.close();
    }
    const context=await session({language:'ru',width:390},fixture.adminEmail);
    const panel=page.locator('[data-tg-connections]');
    assert.equal(await panel.locator('a').getAttribute('href'), '/admin/telegram/messages#tg-invitation-url');
    for(const [language,width] of [['ru',1440],['es',390],['en',320]]) {
        await page.setViewportSize({width,height:1000});
        await page.goto(fixture.baseUrl+'/admin?lang='+language);
        await panel.locator('[data-tg-connection-values]').waitFor({state:'visible'});
        const response=await page.request.get(fixture.baseUrl+'/api/admin/telegram/connections');
        const data=await response.json();
        assert.equal(response.headers()['cache-control'],'no-store');
        assert.equal(data.connected,fixture.statistics.connected);
        assert.equal(data.registered,fixture.statistics.registered);
        assert.equal(data.declined,fixture.statistics.declined+3);
        for(const key of ['registered','connected','notConnected','whatsapp','declined','guests']) {
            const rendered=await panel.locator('[data-tg-count="'+key+'"]').innerText();
            assert.equal(Number(rendered.replace(/\D/g,'')),data[key]);
        }
        await noOverflow('[data-tg-connections]');
        await page.screenshot({path:output+'/'+language+'-'+width+'-statistics.png',fullPage:true});
        checks.push(language+' '+width+': real aggregate in admin, distinct groups and guests, new statistics block fits the viewport');
    }
    await page.route('**/api/admin/telegram/connections',route=>route.fulfill({status:401,body:'{}'}));
    await panel.locator('[data-tg-connections-refresh]').click();
    await panel.locator('[data-tg-connections-status][data-error="true"]').waitFor();
    assert.equal(await panel.locator('[data-tg-connection-values]').isVisible(),false);
    await page.unroute('**/api/admin/telegram/connections');
    await page.route('**/api/admin/telegram/connections',route=>route.fulfill({contentType:'application/json',body:JSON.stringify({registered:0,connected:0,notConnected:0,whatsapp:0,declined:0,guests:0})}));
    await panel.locator('[data-tg-connections-refresh]').click();
    await panel.locator('[data-tg-connection-values]').waitFor({state:'visible'});
    assert.equal(await panel.locator('[data-tg-count="registered"]').innerText(),'0');
    assert.equal(await panel.locator('[data-tg-connection-progress]').getAttribute('max'),'1');
    await page.unroute('**/api/admin/telegram/connections');
    await panel.locator('[data-tg-connections-refresh]').click();
    await panel.locator('[data-tg-connection-values]').waitFor({state:'visible'});
    await page.goto(fixture.baseUrl+'/admin/telegram/messages');
    await page.locator('[data-tg-connections] [data-tg-connection-values]').waitFor({state:'visible'});
    assert.equal(await page.locator('[data-candidates] input:checked').count(),0);
    assert.equal(await page.locator('[data-message-text]').inputValue(),'');
    checks.push('Admin statistics: session error hides old values, explicit retry, valid zero state, composer remains unselected and empty');
    await context.close();
    assert.deepEqual(errors,[]);
    await writeFile(output+'/report.json',JSON.stringify({passed:true,checks},null,2));
    console.log(JSON.stringify({passed:true,checks}));
} catch(error) {
    if(page) await page.screenshot({path:output+'/failure.png',fullPage:true}).catch(()=>{});
    console.error(error.stack);process.exitCode=1;
} finally {await browser.close();}
