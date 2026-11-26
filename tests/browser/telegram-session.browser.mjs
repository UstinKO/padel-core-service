import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE_PATH);
const f=JSON.parse(await readFile(process.argv[2],'utf8'));
const origin=new URL(f.baseUrl);assert.equal(origin.hostname,'127.0.0.1');
const browser=await chromium.launch();
const context=await browser.newContext({locale:'ru',viewport:{width:1440,height:1000}});
await context.route('**/*',r=>new URL(r.request().url()).origin===origin.origin?r.continue():r.abort());
const page=await context.newPage();page.setDefaultTimeout(20000);
const path='/api/admin/telegram/messages/campaigns';let posts=0;
page.on('request',r=>{if(r.method()==='POST'&&new URL(r.url()).pathname.startsWith(path))posts++;});
async function signIn(tab){
 const banner=tab.locator('#cookieConsentBanner');if(await banner.isVisible())await banner.locator('.cookie-btn-reject').click();
 await tab.locator('#username').fill(f.adminEmail);await tab.locator('#password').fill(f.password);
 await Promise.all([tab.waitForURL(u=>u.pathname!=='/login'),tab.locator('form.auth-form button[type=submit]').click()]);
}
async function prepare(){
 const response=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()==='POST');
 await page.locator('[data-preview]').click();const r=await response;assert.equal(r.status(),200);
 await page.locator('[data-preview-result]').waitFor({state:'visible'});return r.json();
}
const text='Привет! Просьба подтвердить участие — текст должен сохраниться после повторного входа.';
try{
 await page.goto(f.baseUrl+'/login?lang=ru');await signIn(page);
 await page.goto(f.baseUrl+'/admin/tournaments/'+f.eventId+'/telegram/messages');
 await page.locator(`[data-player-id="${f.alphaId}"]`).check();
 await page.locator('[data-message-text]').fill(text);const first=await prepare();
 assert.equal(await page.locator('[data-send]').isDisabled(),false);
 const logout=await context.newPage();await logout.goto(f.baseUrl+'/admin');
 await Promise.all([logout.waitForURL(u=>u.pathname==='/login'||u.pathname==='/'),logout.getByRole('button',{name:'Выйти',exact:true}).click()]);await logout.close();
 const denied=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()==='GET');
 await page.locator('[data-history-refresh]').click();assert.equal((await denied).status(),401);
 await page.locator('[data-session-warning]').waitFor({state:'visible'});
 assert.equal(await page.locator('[data-message-text]').inputValue(),text);
 assert.equal(await page.locator(`[data-player-id="${f.alphaId}"]`).isChecked(),true);
 assert.equal(await page.locator('[data-preview]').isDisabled(),true);
 assert.equal(await page.locator('[data-send]').isDisabled(),true);
 assert.equal(await page.locator('[data-test]').isDisabled(),true);
 assert.match(await page.locator('[data-compose-status]').innerText(),/Сеанс входа завершён/);
 assert.equal(await page.locator('[data-session-login]').getAttribute('target'),'_blank');
 await page.setViewportSize({width:390,height:844});await page.locator('[data-session-warning]').scrollIntoViewIfNeeded();
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1),false);
 await page.screenshot({path:'target/telegram-message-preview-qa/session-expired-390.png',fullPage:false});
 await page.setViewportSize({width:1440,height:1000});
 const nextTab=context.waitForEvent('page');await page.locator('[data-session-login]').click();const login=await nextTab;
 await login.waitForURL(u=>u.pathname==='/login');await signIn(login);await login.close();
 const beforeRecovery=posts;await page.locator('[data-session-refresh]').click();
 await page.locator('[data-session-warning]').waitFor({state:'hidden'});
 assert.equal(await page.locator('[data-message-text]').inputValue(),text);
 assert.equal(await page.locator(`[data-player-id="${f.alphaId}"]`).isChecked(),true);
 assert.equal(posts,beforeRecovery);assert.equal(await page.locator('[data-preview-result]').isVisible(),false);
 const second=await prepare();assert.notEqual(second.campaignId,first.campaignId);
 await page.route('**/api/admin/telegram/messages/campaigns',route=>route.request().method()==='POST'
  ?route.fulfill({status:401,contentType:'application/problem+json',body:JSON.stringify({status:401,code:'SESSION_REQUIRED'})}):route.continue(),{times:1});
 const failed=page.waitForResponse(r=>new URL(r.url()).pathname===path&&r.request().method()==='POST');
 await page.locator('[data-preview]').click();assert.equal((await failed).status(),401);
 await page.locator('[data-session-warning]').waitFor({state:'visible'});
 assert.match(await page.locator('[data-compose-status]').innerText(),/Сеанс входа завершён/);
 const beforeSecondRecovery=posts;await page.locator('[data-session-refresh]').click();
 await page.locator('[data-session-warning]').waitFor({state:'hidden'});
 assert.equal(posts,beforeSecondRecovery);assert.equal(await page.locator('[data-message-text]').inputValue(),text);
 const checks=['Реальная потеря сессии: понятный вход в новой вкладке, текст и выбор сохранены',
 'Повторный вход: восстановление только GET, новый предпросмотр вручную, автоматической отправки нет',
 '401 при предпросмотре: предупреждение не заменяется общей ошибкой, восстановление не повторяет POST',
 'Мобильная ширина 390: предупреждение и действия помещаются'];
 await writeFile('target/telegram-message-preview-qa/session-report.json',JSON.stringify({checks},null,2));console.log(checks.join('\n'));
}catch(e){await page.screenshot({path:'target/telegram-message-preview-qa/session-failure.png',fullPage:true}).catch(()=>{});throw e;}
finally{await browser.close();}
