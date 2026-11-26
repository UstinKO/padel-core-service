import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
const {chromium}=await import(process.env.PLAYWRIGHT_MODULE_PATH);
const f=JSON.parse(await readFile(process.argv[2],'utf8'));
const origin=new URL(f.baseUrl);assert.equal(origin.hostname,'127.0.0.1');
const browser=await chromium.launch();const checks=[];let page;
async function login(email){
 const context=await browser.newContext({locale:'ru',viewport:{width:1440,height:1000}});
 await context.route('**/*',route=>new URL(route.request().url()).origin===origin.origin?route.continue():route.abort());
 page=await context.newPage();page.setDefaultTimeout(20000);await page.goto(f.baseUrl+'/login?lang=ru');
 const banner=page.locator('#cookieConsentBanner');if(await banner.isVisible())await banner.locator('.cookie-btn-reject').click();
 await page.locator('#username').fill(email);await page.locator('#password').fill(f.password);
 await Promise.all([page.waitForURL(u=>u.pathname===(email===f.playerEmail?'/players/dashboard':'/admin')),page.locator('form.auth-form button[type=submit]').click()]);
 return context;
}
async function prepare(text){
 await page.locator('[data-message-text]').fill(text);
 const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/admin/telegram/messages/campaigns'&&r.request().method()==='POST');
 await page.locator('[data-preview]').click();const r=await response;assert.equal(r.status(),200);const draft=await r.json();
 await page.locator('[data-preview-result]').waitFor({state:'visible'});return draft;
}
async function finished(id){
 for(let n=0;n<60;n++){
  const response=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/admin/telegram/messages/campaigns'&&r.request().method()==='GET');
  await page.locator('[data-history-refresh]').click();const rows=await(await response).json();const campaign=rows.find(c=>c.id===id);
  if(campaign?.state==='FINISHED'){
   await page.locator(`[data-campaign-id="${id}"][data-campaign-state=FINISHED]`).waitFor();return campaign;
  }
  await new Promise(resolve=>setTimeout(resolve,150));
 }
 throw new Error('Campaign did not finish');
}
async function send(draft,loseResponse=false){
 await page.locator('[data-send]').click();await page.locator('[data-send-dialog]').waitFor({state:'visible'});
 assert.equal(await page.locator('[data-confirm-text]').innerText(),draft.text);
 assert.equal(await page.locator('[data-confirm-people] li').count(),1);
 if(loseResponse)await page.route('**/campaigns/*/confirm',async route=>{await route.fetch();await route.abort('failed');},{times:1});
 await page.locator('[data-confirm-submit]').click();await page.locator('[data-send-dialog]').waitFor({state:'hidden'});
 return finished(draft.campaignId);
}
try{
 const admin=await login(f.adminEmail);const adminPage=page;
 await page.goto(f.baseUrl+'/admin/tournaments/'+f.eventId+'/telegram/messages');
 await page.locator('[data-candidates] input').first().waitFor();
 await page.locator(`[data-player-id="${f.alphaId}"]`).check();
 let draft=await prepare('Проба и рассылка');assert.equal(draft.canTest,true);assert.equal(draft.recipients.length,1);
 const expiredId=draft.campaignId;let dispatchPosts=0;
 const watch=r=>{if(r.method()==='POST'&&/\/(confirm|test)$/.test(new URL(r.url()).pathname))dispatchPosts++;};
 page.on('request',watch);
 await page.route('**/campaigns/'+expiredId,async route=>{
  const response=await route.fetch();const view=await response.json();
  await route.fulfill({response,json:{...view,canConfirm:false}});
 },{times:1});
 await page.locator('[data-send]').click();
 await page.locator('[data-compose-status]').filter({hasText:/Этот предпросмотр больше нельзя отправить/}).waitFor();
 assert.equal(await page.locator('[data-send-dialog]').isVisible(),false);
 assert.equal(await page.locator('[data-preview-result]').isVisible(),false);
 assert.equal(await page.locator('[data-message-text]').inputValue(),draft.text);
 assert.equal(await page.locator(`[data-player-id="${f.alphaId}"]`).isChecked(),true);
 assert.equal(dispatchPosts,0);page.off('request',watch);
 draft=await prepare(draft.text);assert.notEqual(draft.campaignId,expiredId);
 checks.push('Недоступный снимок: понятное сообщение, текст и выбор сохранены, новый предпросмотр с новым ID без автоматической отправки');
 const testResponse=page.waitForResponse(r=>new URL(r.url()).pathname.endsWith('/test'));
 await page.locator('[data-test]').click();const test=await(await testResponse).json();assert.equal(test.kind,'TEST');
 assert.equal((await finished(test.id)).recipients[0].state,'SENT');
 assert.equal(await page.locator('[data-test]').isDisabled(),true);checks.push('Проба себе: один адресат, собственный результат в истории');
 const result=await send(draft,true);assert.equal(result.recipients[0].state,'SENT');
 assert.match(await page.locator('[data-compose-status]').innerText(),/Не повторяйте/);
 assert.equal(await page.locator('[data-send]').isDisabled(),true);checks.push('Потерянный ответ подтверждения: история показывает успех, повтор не запускается');
 const retry=await prepare('RETRY');const failed=await send(retry);assert.equal(failed.recipients[0].state,'FAILED');
 await page.locator(`[data-campaign-id="${retry.campaignId}"] button`).click();
 await page.locator('[data-history-retry]').click();await page.locator('[data-send-dialog]').waitFor({state:'visible'});
 await page.locator('[data-confirm-submit]').click();await page.locator('[data-send-dialog]').waitFor({state:'hidden'});
 await page.locator('[data-history-close]').click();
 assert.equal((await finished(retry.campaignId)).recipients[0].state,'SENT');checks.push('Повтор достоверной ошибки: та же кампания и текст, только неуспешный адресат');
 const uncertain=await prepare('UNCERTAIN');const unknown=await send(uncertain);assert.equal(unknown.recipients[0].state,'UNKNOWN');
 await page.locator(`[data-campaign-id="${uncertain.campaignId}"] button`).click();
 assert.equal(await page.locator('[data-history-retry]').isVisible(),false);
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:'target/telegram-message-preview-qa/campaign-history-390.png',fullPage:false});
 await page.locator('[data-history-close]').click();await page.setViewportSize({width:320,height:844});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+1),false);checks.push('Неизвестный результат: виден в истории, повтор недоступен; мобильная ширина 320/390');
 const player=await login(f.playerEmail);await page.goto(f.baseUrl+'/perfil');
 const status=await page.evaluate(async()=>{const token=decodeURIComponent(document.cookie.split('; ').find(c=>c.startsWith('XSRF-TOKEN='))?.split('=')[1]||'');return(await fetch('/api/players/me/telegram',{method:'DELETE',headers:{'X-XSRF-TOKEN':token}})).status;});
 assert.equal(status,204);await player.close();page=adminPage;
 await page.setViewportSize({width:1440,height:1000});const stopped=await prepare('После отключения');assert.equal(stopped.recipients.length,0);assert.equal(stopped.canTest,false);
 assert.equal(await page.locator('[data-send]').isDisabled(),true);assert.equal(await page.locator('[data-test]').isDisabled(),true);
 await page.evaluate(()=>window.scrollTo({top:0,behavior:'instant'}));await page.screenshot({path:'target/telegram-message-preview-qa/campaign-1440.png',fullPage:true});
 checks.push('Отключение игрока: свежий предпросмотр не предлагает ни рассылку, ни пробу');
 await admin.close();await writeFile('target/telegram-message-preview-qa/campaign-report.json',JSON.stringify({checks},null,2));console.log(checks.join('\n'));
}catch(error){if(page&&!page.isClosed())await page.screenshot({path:'target/telegram-message-preview-qa/campaign-failure.png',fullPage:true}).catch(()=>{});throw error;}
finally{await browser.close();}
