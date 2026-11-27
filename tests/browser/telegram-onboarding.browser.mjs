import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2],'utf8'));
const phase = process.argv[3];
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname,'127.0.0.1');
assert.equal(origin.protocol,'http:');
const output = 'target/telegram-onboarding-qa';
await mkdir(output,{recursive:true});
const checks = [], errors = [];
const browser = await chromium.launch();
const endpoint = '/api/players/me/telegram/onboarding';
const title = {ru:'Подключение Telegram',es:'Conectar Telegram',en:'Connect Telegram'};
let page;
async function context(scenario) {
    const context = await browser.newContext({locale:scenario.language,viewport:{width:scenario.width,height:950}});
    await context.route('**/*',route => new URL(route.request().url()).origin === origin.origin ? route.continue() : route.abort());
    page = await context.newPage(); page.setDefaultTimeout(15000);
    page.on('pageerror',error => errors.push(error.message));
    return context;
}
async function login(email,destination='/telegram/connect') {
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    const banner=page.locator('#cookieConsentBanner');
    if (await banner.isVisible()) await banner.locator('.cookie-btn-reject').click();
    await Promise.all([page.waitForURL(url => url.pathname === destination),page.locator('form.auth-form button[type=submit]').click()]);
}
async function enter(scenario) {
    await page.goto(scenario.url);
    await page.waitForURL(url => url.pathname === '/login');
    const destination = new URL(page.url()).searchParams.get('destination');
    assert.match(destination,/^\/telegram\/connect\?request=[a-f0-9-]{36}$/);
    assert.equal(new URL(page.url()).hash,'');
    const google = await page.locator('a[href*="/oauth2/authorization/google"]').getAttribute('href');
    assert.equal(new URL(google,origin).searchParams.get('destination'),destination);
    await login(scenario.email);
    await page.locator('[data-telegram-connect]').waitFor();
    assert.equal(await page.locator('h1').innerText(),title[scenario.language]);
    assert.deepEqual([...new URL(page.url()).searchParams.keys()],['request']);
}
try {
    for (const scenario of fixture.scenarios) {
        const ctx = await context(scenario);
        let mutations=0;
        const requests=[];
        page.on('request',request => {
            requests.push(request.url());
            if (new URL(request.url()).pathname === endpoint && request.method()==='POST') mutations++;
        });
        await enter(scenario);
        const root=page.locator('[data-telegram-connect]');
        assert.equal(await root.getAttribute('data-state'),phase==='pending'?'READY':'CONNECTED');
        assert.equal(mutations,0);
        const requestId = new URL(page.url()).searchParams.get('request');
        for (const language of [scenario.language === 'ru' ? 'en' : 'ru',scenario.language]) {
            await Promise.all([
                page.waitForURL(url => url.searchParams.get('lang') === language && url.searchParams.get('request') === requestId),
                page.locator('.tg-connect-languages').getByRole('link',{name:language.toUpperCase(),exact:true}).click()
            ]);
            assert.equal(await page.locator('h1').innerText(),title[language]);
            assert.equal(await root.getAttribute('data-state'),phase==='pending'?'READY':'CONNECTED');
            assert.equal(mutations,0);
        }
        assert.equal(await page.locator('[data-player-name]').innerText().then(name=>name.startsWith('QA Telegram')),true);
        if (phase==='pending') {
            await page.screenshot({path:output+'/'+scenario.language+'-'+scenario.width+'-ready.png',fullPage:true});
            await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname===endpoint && r.request().method()==='POST'),page.locator('[data-confirm]').dblclick()]);
            await page.waitForFunction(()=>document.querySelector('[data-telegram-connect]').dataset.state==='PENDING');
            assert.equal(mutations,1);
            assert.equal(await page.locator('[data-open-bot]').getAttribute('href'),'https://t.me/OnePadelQaBot');
            await page.reload();
            assert.equal(await root.getAttribute('data-state'),'PENDING');
            assert.equal(mutations,1);
            assert.equal((await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1)),false);
        } else {
            assert.equal(await page.locator('[data-confirm]').isVisible(),false);
        }
        const secret=new URL(scenario.url).hash.slice(7);
        assert.equal(requests.some(url=>url.includes(secret)),false);
        assert.equal((await page.request.get(fixture.baseUrl+'/telegram/connect')).headers()['referrer-policy'],'no-referrer');
        await page.screenshot({path:output+'/'+scenario.language+'-'+scenario.width+'-'+phase+'.png',fullPage:true});
        checks.push(scenario.language+' '+scenario.width+': '+phase+', locale switch preserves request, clean login destination, one explicit confirmation');
        await ctx.close();
    }
    if (phase==='pending') {
        const scenario=fixture.failure;
        const ctx=await context(scenario);
        await enter(scenario);
        let count=0;
        await page.route('**/api/players/me/telegram/onboarding**',async route=>{
            if (route.request().method()!=='POST') return route.continue();
            count++;
            await route.fetch();
            return route.abort('failed');
        });
        await page.locator('[data-confirm]').click();
        await page.waitForFunction(()=>document.querySelector('[data-status]').dataset.error==='true');
        assert.equal(await page.locator('[data-status]').innerText(), 'No response received. Check the message in Telegram, then refresh the status. We did not repeat the send.');
        assert.equal(await page.locator('[data-confirm]').isVisible(),false);
        await page.locator('[data-refresh]').click();
        await page.waitForFunction(()=>document.querySelector('[data-status]').textContent==='');
        assert.equal(await page.locator('[data-telegram-connect]').getAttribute('data-state'),'PENDING');
        assert.equal(count,1);
        await page.unroute('**/api/players/me/telegram/onboarding**');
        await page.route('**/api/players/me/telegram/onboarding**',route=>route.fulfill({status:401,contentType:'application/problem+json',body:'{}'}));
        await page.locator('[data-refresh]').click();
        await page.locator('[data-login]').waitFor({state:'visible'});
        assert.equal(await page.locator('[data-confirm]').isVisible(),false);
        assert.equal(count,1);
        await page.goto(page.url().split('#')[0]+'#token=invalid');
        await page.waitForFunction(()=>document.querySelector('[data-telegram-connect]').dataset.state==='INVALID');
        assert.equal(new URL(page.url()).hash,'');
        assert.equal(await page.locator('[data-state-message]').innerText(),'This link is invalid or unavailable. Return to the bot and start again.');
        await ctx.close();
        checks.push('Lost response after real POST: durable PENDING, manual refresh, no repeat; session loss and malformed link');

        const admin=await context({language:'ru',width:390});
        await page.goto(fixture.baseUrl+'/login?lang=ru');
        await login(fixture.adminEmail,'/admin');
        await page.goto(fixture.baseUrl+'/admin/telegram/messages?lang=ru');
        const invitation=page.locator('[data-telegram-invitation]');
        await invitation.waitFor({state:'visible'});
        assert.equal(await invitation.locator('input').inputValue(),'https://1-padel.com/telegram/join');
        await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{value:{writeText:async()=>{throw new Error('denied');}},configurable:true}));
        await invitation.locator('button').click();
        assert.equal(await invitation.locator('[role=status]').innerText(),'Выделили ссылку. Скопируй её вручную.');
        assert.equal(await page.locator('[data-candidates] input:checked').count(),0);
        assert.equal(await page.locator('[data-message-text]').inputValue(),'');
        assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1),false);
        await page.screenshot({path:output+'/admin-invitation-390.png',fullPage:true});
        await admin.close();
        checks.push('Admin shared invitation: no personal token, clipboard denial fallback, campaign remains empty');
    }
    assert.deepEqual(errors,[]);
    await writeFile(output+'/'+phase+'-report.json',JSON.stringify({passed:true,checks},null,2));
    console.log(JSON.stringify({passed:true,checks}));
} catch(error) {
    if(page) await page.screenshot({path:output+'/'+phase+'-failure.png',fullPage:true}).catch(()=>{});
    console.error(error.stack); process.exitCode=1;
} finally {await browser.close();}
