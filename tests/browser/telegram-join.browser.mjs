import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {dirname,join} from 'node:path';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture=JSON.parse(await readFile(process.argv[2],'utf8'));
const phase=process.argv[3];
const origin=new URL(fixture.baseUrl);
assert.equal(origin.hostname,'127.0.0.1');
const output='target/telegram-join-qa';
await mkdir(output,{recursive:true});
const browser=await chromium.launch();
const checks=[],errors=[],handoffs=[];
const titles={ru:'Подключение Telegram',es:'Conectar Telegram',en:'Connect Telegram'};
let page;
async function context(scenario) {
    const ctx=await browser.newContext({locale:scenario.language,viewport:{width:scenario.width,height:950}});
    await ctx.route('**/*',async route=>{
        const request=route.request();
        const url=new URL(request.url());
        if(url.origin!==origin.origin) return route.abort();
        if(url.pathname==='/telegram/join' && request.method()==='POST') {
            // Останавливаемся на настоящем 303 сервера, не передавая тестовый токен внешнему Telegram.
            const response=await route.fetch({maxRedirects:0});
            const location=response.headers().location;
            if(response.status()===303 && location?.startsWith('https://t.me/')) {
                ctx.qaHandoff=location;
                return route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><title>QA bot handoff</title><p data-qa-bot>Start is simulated by the isolated Java fixture.</p>'});
            }
            return route.fulfill({response});
        }
        return route.continue();
    });
    page=await ctx.newPage(); page.setDefaultTimeout(15000);
    page.on('pageerror',error=>errors.push(error.message));
    return ctx;
}
async function login(email,destination='/telegram/join') {
    const banner=page.locator('#cookieConsentBanner');
    if(await banner.isVisible()) await banner.locator('.cookie-btn-reject').click();
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url=>url.pathname===destination),page.locator('form.auth-form button[type=submit]').click()]);
}
async function enter(scenario) {
    await page.goto(fixture.baseUrl+'/telegram/join?lang='+scenario.language);
    await page.waitForURL(url=>url.pathname==='/login');
    assert.equal(new URL(page.url()).searchParams.get('destination'),'/telegram/join');
    const google=await page.locator('a[href*="/oauth2/authorization/google"]').getAttribute('href');
    assert.equal(new URL(google,origin).searchParams.get('destination'),'/telegram/join');
    await login(scenario.email);
    await page.locator('[data-telegram-join]').waitFor();
    assert.equal(await page.locator('h1').innerText(),titles[scenario.language]);
    assert.equal(await page.locator('[data-player-name]').innerText().then(value=>value.startsWith('QA Telegram')),true);
}
try {
    if(phase==='disabled') {
        const ctx=await context(fixture.scenarios[0]);
        await page.goto(fixture.baseUrl+'/telegram/join?lang=ru');
        assert.equal(await page.locator('[data-telegram-join]').getAttribute('data-state'),'UNAVAILABLE');
        assert.equal(await page.locator('form').count(),0);
        checks.push('Disabled feature shows unavailable without login loop or form');
        await ctx.close();
    } else {
        for(const scenario of fixture.scenarios.slice(0,3)) {
            const ctx=await context(scenario);
            let mutations=0;
            page.on('request',request=>{
                if(new URL(request.url()).pathname==='/telegram/join' && request.method()==='POST') mutations++;
            });
            await enter(scenario);
            const root=page.locator('[data-telegram-join]');
            assert.equal(await root.getAttribute('data-state'),phase==='ready'?'READY':'CONNECTED');
            assert.equal(mutations,0);
            assert.equal(await page.locator('a[href*="t.me"]').count(),0);
            assert.equal((await page.content()).includes('?start='),false);
            for(const language of [scenario.language==='ru'?'en':'ru',scenario.language]) {
                await page.locator('.tg-connect-languages').getByRole('link',{name:language.toUpperCase(),exact:true}).click();
                await page.waitForURL(url=>url.searchParams.get('lang')===language);
                assert.equal(await page.locator('h1').innerText(),titles[language]);
                assert.equal(mutations,0);
            }
            assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+1),false);
            const response=await page.request.get(fixture.baseUrl+'/telegram/join');
            assert.equal(response.headers()['cache-control'],'no-store');
            assert.equal(response.headers()['referrer-policy'],'same-origin');
            await page.screenshot({path:output+'/'+scenario.language+'-'+scenario.width+'-'+phase+'.png',fullPage:true});
            if(phase==='ready') {
                const button=page.locator('[data-join-submit]');
                await button.focus();
                assert.equal(await button.evaluate(element=>document.activeElement===element),true);
                // Две активации подряд должны отправить только одну нативную форму.
                await button.evaluate(element=>{element.click();element.click();});
                await page.locator('[data-qa-bot]').waitFor();
                assert.equal(mutations,1);
                const target=new URL(ctx.qaHandoff);
                assert.equal(target.origin,'https://t.me');
                assert.equal(target.pathname,'/OnePadelQaBot');
                assert.match(target.searchParams.get('start'),/^[A-Za-z0-9_-]{32}$/);
                handoffs.push({email:scenario.email,url:target.href});
                checks.push(scenario.language+' '+scenario.width+': clean normal/Google login destination, locale switch, explicit form, one POST, direct bot handoff');
            } else {
                assert.equal(await page.locator('[data-join-form]').count(),0);
                assert.equal(mutations,0);
                checks.push(scenario.language+' '+scenario.width+': CONNECTED after personal Start, no second confirmation or token issuance');
            }
            await ctx.close();
        }
        if(phase==='ready') {
            const scenario=fixture.scenarios[3];
            const ctx=await context(scenario);
            await enter(scenario);
            await page.locator('input[name="_csrf"]').evaluate(element=>{element.value='invalid-qa-csrf';});
            await Promise.all([page.waitForURL(url=>url.pathname==='/error/403'),page.locator('[data-join-submit]').click()]);
            checks.push('Invalid CSRF is rejected by real server and does not connect the player');
            await ctx.close();
            await writeFile(join(dirname(process.argv[2]),'handoffs.json'),JSON.stringify(handoffs),{mode:0o600});

            const admin=await context({language:'ru',width:390});
            await page.goto(fixture.baseUrl+'/login?lang=ru');
            await login(fixture.adminEmail,'/admin');
            await page.goto(fixture.baseUrl+'/telegram/join');
            assert.equal(await page.locator('[data-telegram-join]').getAttribute('data-state'),'ACCOUNT_REQUIRED');
            assert.equal(await page.locator('form').count(),0);
            await page.goto(fixture.baseUrl+'/admin/telegram/messages?lang=ru');
            const invitation=page.locator('[data-telegram-invitation]');
            await invitation.waitFor({state:'visible'});
            assert.equal(await invitation.locator('input').inputValue(),'https://1-padel.com/telegram/join');
            await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{value:{writeText:async text=>{window.qaCopied=text;}},configurable:true}));
            await invitation.locator('button').click();
            assert.equal(await page.evaluate(()=>window.qaCopied),'https://1-padel.com/telegram/join');
            await page.evaluate(()=>Object.defineProperty(navigator,'clipboard',{value:{writeText:async()=>{throw Error('denied');}},configurable:true}));
            await invitation.locator('button').click();
            assert.equal(await invitation.locator('[role=status]').innerText(),'Выделили ссылку. Скопируй её вручную.');
            assert.equal(await page.locator('[data-candidates] input:checked').count(),0);
            assert.equal(await page.locator('[data-message-text]').inputValue(),'');
            await page.screenshot({path:output+'/admin-site-invitation-390.png',fullPage:true});
            checks.push('Administrator sees account-required state; shared URL copies with fallback, campaign audience/message unchanged');
            await admin.close();
        }
    }
    assert.deepEqual(errors,[]);
    await writeFile(output+'/'+phase+'-report.json',JSON.stringify({passed:true,checks},null,2));
    console.log(JSON.stringify({passed:true,checks}));
} catch(error) {
    if(page) await page.screenshot({path:output+'/'+phase+'-failure.png',fullPage:true}).catch(()=>{});
    console.error(error.stack);process.exitCode=1;
} finally {await browser.close();}
