import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const fixture=JSON.parse(await readFile(process.argv[2],'utf8'));
const base=new URL(fixture.baseUrl);
assert.equal(base.hostname,'127.0.0.1');
const {chromium}=await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser=await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE});
const output=resolve('target/individual-action-help-qa');
await mkdir(output,{recursive:true});
const checks=[];
const record=text=>{checks.push(text);console.log(text);};
let current;
const details=id=>`/admin/tournaments/${id}`;
const payments=id=>`${details(id)}/payments`;
const americano=id=>`/tournaments/americano/admin/${id}`;
async function go(page,path,lang='ru') {
    const response=await page.goto(new URL(path+'?lang='+lang,base).href);
    assert.equal(response.status(),200,path);
}
async function session(account,mobile=false) {
    const context=await browser.newContext({viewport:{width:1440,height:1000},isMobile:mobile,hasTouch:mobile});
    await context.route('**/*',route=>new URL(route.request().url()).origin===base.origin?route.continue():route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page=await context.newPage(), writes=[], dialogs=[], errors=[];
    page.on('request',r=>{if(!['GET','HEAD'].includes(r.method())) writes.push(r.url());});
    page.on('dialog',async d=>{dialogs.push(d.message());await d.accept();});
    page.on('pageerror',e=>errors.push(e.message));
    await page.goto(new URL('/login',base).href);
    await page.locator('#username').fill(account.email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(u=>u.pathname!=='/login'),page.locator('form.auth-form button[type=submit]').click()]);
    current={context,page,writes,dialogs,errors};
    return current;
}
const fields=page=>page.locator('input,select,textarea').evaluateAll(es=>es.map(e=>({id:e.id,name:e.name,value:e.value,checked:e.checked})));
async function help(s,topic,scope=s.page) {
    const {page,writes,dialogs}=s;
    const trigger=scope.locator(`[data-help-topic="${topic}"]:visible`).first();
    const before=await fields(page), count=writes.length, confirmations=dialogs.length;
    await trigger.click();
    if(page.viewportSize().width<=640) assert.ok((await trigger.boundingBox()).width>=44,topic+' touch target');
    const panel=page.locator('#action-help-popover');
    await panel.waitFor({state:'visible'});
    assert.equal(await panel.locator('h3').textContent(),await trigger.getAttribute('data-help-title'));
    const text=await panel.locator('p').textContent();
    assert.ok(text.length>20 && text.length<650 && !text.includes('??'),topic+': '+text);
    const box=await panel.boundingBox(),view=page.viewportSize();
    assert.ok(box.x>=0 && box.x+box.width<=view.width+1,topic+' width');
    assert.ok(box.y>=0 && box.y+box.height<=view.height+1,topic+' height');
    await page.keyboard.press('Escape');
    await panel.waitFor({state:'hidden'});
    assert.equal(await trigger.evaluate(e=>document.activeElement===e),true);
    await trigger.click();
    await panel.locator('button').click();
    await panel.waitFor({state:'hidden'});
    assert.deepEqual(await fields(page),before,topic+' preserves draft');
    assert.equal(writes.length,count,topic+' sends no request');
    assert.equal(dialogs.length,confirmations,topic+' sends no confirmation');
}
const nav=(page,button)=>Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded'}),button.click()]);
async function paymentDraft(s,event) {
    const {page}=s;
    await go(page,payments(event.id));
    const row=page.locator('#paymentForm tbody tr').filter({has:page.locator(`input[name$=".registrationId"][value="${event.registrations[0]}"]`)});
    await row.locator('input[name$=".amount"]').fill('123.45');
    await row.locator('select[name$=".paymentStatus"]').selectOption('PAID');
    await row.locator('select[name$=".paymentMethod"]').selectOption('CASH');
    await row.locator('input[name$=".attended"]').check();
    await row.locator('input[name$=".notes"]').fill('QA draft');
    for(const topic of ['individual-attendance','payment','save','telegram']) await help(s,topic);
}
async function kingScore(s,index,edit=false) {
    const {page}=s;
    await page.locator(edit?'.edit-result-btn':'.enter-result-btn').nth(index).click();
    const modal=page.locator('#resultModal');
    await modal.waitFor({state:'visible'});
    if(!edit) {
        const value=await page.locator('#winner1 option').evaluateAll(es=>es.map(e=>e.value).find(v=>v));
        await page.locator('#winner1').selectOption(value);
    }
    await page.locator('#scoreWinners').fill(edit?'22':'21');
    await page.locator('#scoreLosers').fill(edit?'10':'11');
    await help(s,'koc-winners',modal);
    await help(s,'koc-score',modal);
    assert.equal(await modal.isVisible(),true,'Escape did not close score modal');
    const version=await page.evaluate(()=>window.kingOfCourt.conductVersion);
    const saved=page.waitForResponse(r=>r.url().includes('/api/king-of-court/matches')&&['POST','PUT'].includes(r.request().method()));
    await page.locator('#save-result-btn').click();
    assert.equal((await saved).status(),200);
    await modal.waitFor({state:'hidden'});
    await page.waitForFunction(previous=>window.kingOfCourt.conductVersion>previous,version);
    // После сохранения интерфейс также перечитывает состояние с задержкой.
    await page.waitForTimeout(750);
    await page.waitForLoadState('networkidle');
    await page.waitForFunction(()=>document.querySelectorAll('.edit-result-btn').length>0);
}
async function kingCycle(s,event) {
    const {page}=s;
    await go(page,details(event.id));
    await page.locator('#maxCourts').fill('2');
    await page.locator('#calibrationRounds').fill('1');
    for(const topic of ['koc-courts','koc-calibration','koc-start']) await help(s,topic);
    await nav(page,page.locator('form[action="/admin/tournaments/king-of-court/start"] button[type=submit]'));
    await page.locator('.enter-result-btn').first().waitFor();
    assert.equal(await page.locator('#leaderboard [data-ranking-row]').count(),8,'Резерв не включён в KoC');
    const runPath=new URL(page.url()).pathname;
    for(const lang of ['ru','es','en']) {
        await go(page,runPath,lang);
        await page.locator('.enter-result-btn').first().waitFor();
        await page.waitForLoadState('networkidle');
        await help(s,'koc-score');
        await page.locator('.enter-result-btn').first().click();
        await page.locator('#scoreWinners').fill('19');
        await page.locator('#scoreLosers').fill('13');
        await help(s,'koc-score',page.locator('#resultModal'));
        assert.equal(await page.locator('#resultModal').isVisible(),true,'Подсказка сохраняет окно счёта');
        await page.locator('#resultModal .modal-actions .btn-outline').click();
    }
    await go(page,runPath);
    await page.locator('.enter-result-btn').first().waitFor();
    for(const topic of ['koc-ranking','individual-history','koc-youtube','individual-share','koc-restart']) await help(s,topic);
    for(let round=0;round<2;round++) {
        for(let court=0;court<2;court++) {
            await help(s,'koc-score');
            await kingScore(s,0);
            await page.waitForFunction(n=>document.querySelectorAll('.edit-result-btn').length===n,court+1);
        }
        await help(s,'koc-edit');
        await kingScore(s,0,true);
        await help(s,'koc-reset');
        await help(s,'koc-next');
        await help(s,'koc-finish');
        if(round===0) {
            await page.setViewportSize({width:390,height:900});
            const response=page.waitForResponse(r=>r.url().includes('/next-round')&&r.request().method()==='POST');
            await page.locator('#nextRoundBtn').click();
            assert.equal((await response).status(),200);
            await page.waitForFunction(()=>window.kingOfCourt.currentRound===2);
            await page.waitForTimeout(750);
            await page.waitForLoadState('networkidle');
            await page.locator('.enter-result-btn').first().waitFor();
        }
    }
    await page.screenshot({path:resolve(output,'king-phone.png'),fullPage:true});
    const response=page.waitForResponse(r=>r.url().includes('/finish')&&r.request().method()==='POST');
    await page.locator('#endTournamentBtn').click();
    assert.equal((await response).status(),200);
    await page.waitForFunction(()=>window.kingOfCourt.isFinished);
    await go(page,details(event.id));
    await help(s,'individual-resume');
    record('Король корта: запуск, калибровка, бонусный раунд, ввод/исправление, завершение и возврат');
}
async function americanoCycle(s,event,mode) {
    const {page}=s;
    await page.setViewportSize({width:mode==='FULL'?1440:320,height:1000});
    await go(page,details(event.id));
    await page.locator('#generationMode').selectOption(mode==='FULL'?'AUTO':mode);
    await page.locator('#pointsPerMatch').selectOption('32');
    await page.locator('#courts').fill('1');
    await page.locator('#totalRounds').fill('3');
    for(const topic of ['americano-mode','americano-points','americano-setup','americano-ranking','americano-preview','americano-start']) await help(s,topic);
    await nav(page,page.locator('[onclick="previewRounds()"]'));
    await help(s,'americano-preview');
    await page.screenshot({path:resolve(output,`americano-${mode}-preview.png`),fullPage:true});
    await go(page,details(event.id));
    await page.locator('#generationMode').selectOption(mode==='FULL'?'AUTO':mode);
    await page.locator('#pointsPerMatch').selectOption('32');
    await page.locator('#courts').fill('1');
    await page.locator('#totalRounds').fill('3');
    await nav(page,page.locator('#btnIniciarTorneo'));
    assert.equal(new URL(page.url()).pathname,americano(event.id));
    await page.locator(mode==='FULL'?'.format-banner.full':'.format-banner.flex').waitFor();
    assert.equal(await page.locator('#individual-americano-ranking [data-ranking-row]').count(),mode==='FULL'?4:5,'Резерв не включён в Americano');
    for(const lang of ['ru','es','en']) {
        await go(page,americano(event.id),lang);
        await help(s,'americano-score');
        await help(s,'americano-round-complete');
        await help(s,'americano-ranking');
    }
    await go(page,americano(event.id));
    for(let index=0;index<3;index++) {
        if(index===1) await page.setViewportSize({width:390,height:900});
        await page.locator('.round-tab-btn').nth(index).click();
        const round=page.locator('.round-card:visible');
        await help(s,'americano-round-complete');
        await help(s,'americano-points',round);
        if(mode==='FLEX') await help(s,'americano-rest',round);
        const inputs=round.locator('.score-input');
        await inputs.nth(0).fill('20');await inputs.nth(1).fill('12');
        await help(s,'americano-score',round);
        await help(s,'americano-ranking');
        const saved=page.waitForResponse(r=>r.url().includes('/api/tournaments/americano/matches/')&&['POST','PUT'].includes(r.request().method()));
        await round.locator('.submit-score-btn').click();
        assert.equal((await saved).status(),200);
        await round.locator('.edit-score-btn').waitFor();
        await help(s,'americano-edit',round);
        await round.locator('.edit-score-btn').click();
        await inputs.nth(0).fill('21');await inputs.nth(1).fill('11');
        await help(s,'americano-score',round);
        const changed=page.waitForResponse(r=>r.url().includes('/api/tournaments/americano/matches/')&&['POST','PUT'].includes(r.request().method()));
        await round.locator('.submit-score-btn').click();
        assert.equal((await changed).status(),200);
        await round.locator('.edit-score-btn').waitFor();
    }
    await page.reload();
    await help(s,'americano-finish');
    await page.screenshot({path:resolve(output,`americano-${mode}-phone.png`),fullPage:true});
    await nav(page,page.locator('.finish-section button[type=submit]'));
    await go(page,details(event.id));
    await help(s,'individual-resume');
    record(`Americano ${mode}: предварительный просмотр, запуск, три раунда, отдых, ввод/исправление, завершение и возврат`);
}
async function dismissalRegression() {
    const page=await browser.newPage({viewport:{width:500,height:400}});
    const attributes='type="button" data-action-help data-help-title="Help" data-help-text="A short contextual explanation" data-help-close="Close" aria-expanded="false"';
    await page.setContent(`<button id="first" ${attributes} style="position:fixed;left:300px;top:250px">?</button><button id="second" ${attributes} style="position:fixed;left:0;top:0">?</button>`);
    await page.addStyleTag({path:resolve('src/main/resources/static/css/action-help.css')});
    await page.addScriptTag({path:resolve('src/main/resources/static/js/action-help.js')});
    await page.locator('#first').click();
    const panel=page.locator('#action-help-popover');
    const close=await panel.locator('button').boundingBox();
    await page.locator('#second').evaluate((e,box)=>Object.assign(e.style,{left:box.x+'px',top:box.y+'px',width:box.width+'px',height:box.height+'px'}),close);
    await panel.locator('button').click();
    await panel.waitFor({state:'hidden'});
    assert.equal(await page.locator('#second').getAttribute('aria-expanded'),'false','Крестик не открывает вопрос под курсором');
    await page.mouse.move(10,380);
    await page.locator('#second').hover();
    await panel.waitFor({state:'visible'});
    await page.close();
    record('Закрытие крестиком не открывает другую подсказку под неподвижным курсором; новое наведение работает');
}
try {
    await dismissalRegression();
    for(const account of fixture.accounts) {
        const s=await session(account,account.role==='CLUB_ADMIN'),{page}=s;
        for(const lang of ['ru','es','en']) {
            await go(page,details(account.king.id),lang);
            await help(s,'koc-start');
            await help(s,'koc-calibration');
            await go(page,details(account.flex.id),lang);
            await help(s,'americano-start');
            await help(s,'americano-mode');
        }
        if(account.role!=='ADMIN') {
            await go(page,details(account.flex.id)+'/copy');
            await page.locator('#infoDetallada').fill('Draft copy');
            await help(s,'copy');
            await help(s,'individual-capacity');
        }
        for(const width of [1440,390,320]) {
            await page.setViewportSize({width,height:1000});
            for(const event of [account.king,account.flex]) await paymentDraft(s,event);
        }
        if(['CLUB_ADMIN','ORGANIZER'].includes(account.role)) {
            for(const event of [fixture.accounts[0].king,fixture.accounts[0].full]) {
                const response=await page.goto(new URL(payments(event.id),base).href);
                assert.ok(response.status()===403 || new URL(page.url()).pathname==='/error','Чужое мероприятие закрыто');
            }
        }
        assert.deepEqual(s.errors,[]);
        record(`${account.role}: RU/ES/EN, desktop/390/320, черновики оплат/прихода/заметок и границы доступа`);
        await s.context.close();
    }
    const s=await session(fixture.accounts[3],true),{page}=s;
    const account=fixture.accounts[3];
    await page.setViewportSize({width:1440,height:1000});
    await kingCycle(s,account.king);
    await americanoCycle(s,account.full,'FULL');
    await americanoCycle(s,account.flex,'FLEX');
    await go(page,details(account.full.id)+'/edit');
    await page.locator('#infoDetallada').fill('Draft settings');
    for(const topic of ['create','schedule','individual-category','individual-format','individual-capacity','contact','visibility']) await help(s,topic);
    await page.locator('#tipo').selectOption('AMERICANO_TEAMS');
    await help(s,'capacity');
    assert.equal(await page.locator('[data-individual-help]:visible').count(),0);
    record('Создание/редактирование: черновик настроек, индивидуальные/парные подсказки переключаются по формату');
    assert.deepEqual(s.errors,[]);
    await s.context.close();
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:true,checks},null,2));
} catch(error) {
    if(current) await current.page.screenshot({path:resolve(output,'failure.png'),fullPage:true}).catch(()=>{});
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:false,checks,error:error.stack},null,2));
    throw error;
} finally {await browser.close();}
