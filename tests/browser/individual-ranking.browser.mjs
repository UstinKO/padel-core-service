import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1');
const {chromium} = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({executablePath: process.env.CHROME_EXECUTABLE});
const output = resolve('target/individual-ranking-qa');
await mkdir(output, {recursive:true});
const report = [];
let publicReference;
const labels = {ru:['Итого','За корт','Победы / поражения'],es:['Total','Cancha','Victorias / derrotas'],en:['Total','Court','Wins / losses']};
async function login(account, language) {
    const context = await browser.newContext({viewport:{width:1440,height:1100}});
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page = await context.newPage();
    const errors=[];
    page.on('pageerror', error=>errors.push(error.message));
    await page.goto(new URL('/login?lang='+language,base).href);
    await page.locator('#username').fill(account.email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url=>url.pathname!=='/login'),page.locator('form.auth-form button[type=submit]').click()]);
    return {context,page,errors};
}
function path(events, king) {return king ? `/admin/tournaments/king-of-court/${events.kingId}` : `/tournaments/americano/admin/${events.americanoEvent}`;}
function api(events, king) {return king ? `/api/king-of-court/tournaments/${events.kingId}/state` : `/api/tournaments/americano/${events.americanoEvent}/ranking`;}
async function sync(page, events, king, reference = page.request) {
    const response = await reference.get(new URL(api(events,king),base).href);
    assert.equal(response.status(),200);
    const data = await response.json();
    assert.ok(Array.isArray(data.ranking));
    await page.waitForFunction(({rows,king}) => rows.every(player=>{
        const row=document.querySelector(`[data-ranking-row="${player.playerId}"]`);
        return row && Number(row.querySelector('.ir-total').textContent) === (king?player.totalPoints:player.totalScore);
    }),{rows:data.ranking,king});
    const ids = await page.locator('[data-ranking-row]').evaluateAll(rows=>rows.map(row=>Number(row.dataset.rankingRow)));
    assert.deepEqual([...ids].sort((a,b)=>a-b),data.ranking.map(row=>row.playerId).sort((a,b)=>a-b));
    const totals=await page.locator('[data-ranking-row] .ir-total').allTextContents();
    assert.deepEqual(totals.map(Number),data.ranking.map(player=>king?player.totalPoints:player.totalScore));
    return data;
}
async function refresh(page, king) {
    await page.evaluate(king=>king ? window.kingOfCourt.loadData() : updateRankingTable(),king);
}
async function verify(page,events,king,language,width) {
    await page.setViewportSize({width,height:1100});
    const response=await page.goto(new URL(path(events,king)+'?lang='+language,base).href);
    assert.equal(response.status(),200);
    await page.locator('[data-ranking-row]').first().waitFor();
    assert.equal(await page.locator('[data-ranking-row]').count(),8);
    const table=page.locator('.individual-ranking');
    assert.equal(await table.locator('th').nth(2).innerText(),labels[language][0]);
    const data=await sync(page,events,king);
    const first=data.ranking[0];
    const toggle=page.locator(`[data-ranking-toggle="${first.playerId}"]`);
    await toggle.click();
    const detail=page.locator(`[data-ranking-details="${first.playerId}"]`);
    assert.equal(await detail.isVisible(),true);
    assert.equal(await detail.locator('dl').first().locator('dt').count(),king?6:10);
    if(king) assert.match(await detail.innerText(),new RegExp(language==='ru'?'Боно|Бонус|Бонусные|Бонус за корт':language==='es'?'Bono de cancha':'Court bonus','i'));
    assert.equal(await page.locator('[data-ranking-row] .ir-player').evaluateAll(rows=>rows.some(row=>/Сторона не указана|Side not specified|Lado no indicado/.test(row.textContent))),false);
    const stage=detail.locator('.ir-stages');
    await stage.locator('summary').first().click();
    await refresh(page,king);
    await page.waitForTimeout(150);
    assert.equal(await page.locator(`[data-ranking-toggle="${first.playerId}"]`).getAttribute('aria-expanded'),'true');
    assert.equal(await page.locator(`[data-ranking-details="${first.playerId}"] .ir-stages`).getAttribute('open'),'');
    const geometry=await table.evaluate(el=>({width:el.getBoundingClientRect().width,scroll:el.scrollWidth,client:el.clientWidth,
        essential:[...el.querySelectorAll('tbody [data-ranking-row]')].every(row=>{
            const cells=[row.querySelector('.ir-rank'),row.querySelector('.ir-player'),row.querySelector('.ir-total')];
            return cells.every(cell=>{const r=cell.getBoundingClientRect();return r.left>=0 && r.right<=innerWidth+1;});
        })}));
    assert.equal(geometry.essential,true,JSON.stringify({width,geometry}));
    assert.ok(geometry.scroll<=geometry.client+1,JSON.stringify({width,geometry}));
    assert.equal(await table.locator('th.ir-secondary').first().isVisible(),width>=900);
    if(width<900) assert.ok(await table.locator('th.ir-player').evaluate(el=>el.getBoundingClientRect().width>=el.closest('table').getBoundingClientRect().width*.4),'Player gets usable width: '+JSON.stringify({width,format:king?'king':'americano',geometry}));
    if(width>=900) {
        await table.locator('.ir-help summary').click();
        assert.equal(await table.locator('.ir-help p').innerText(),labels[language][2]);
    }
    if(king && width===1440) {
        const layout=await page.locator('.individual-ranking-layout').evaluate(el=>[...el.children].map(child=>({top:child.getBoundingClientRect().top,left:child.getBoundingClientRect().left})));
        assert.ok(layout[1].top>layout[0].top,'History goes below when room is limited');
    }
    if(language==='ru' && [390,1440].includes(width)) {
        await page.locator('.individual-ranking-card').scrollIntoViewIfNeeded();
        await page.locator('.individual-ranking-card').screenshot({path:resolve(output,`${king?'king':'americano'}-${language}-${width}.png`),style:'body > header { visibility: hidden !important; }'});
    }
    report.push({format:king?'king':'americano',language,width,rows:8,layout:true,expandedPreserved:true});
}
async function scoreCorrection(page, events, king) {
    await page.goto(new URL(path(events,king)+'?lang=ru',base).href);
    await page.locator('[data-ranking-row]').first().waitFor();
    if(king) {
        await page.locator('.edit-result-btn').first().click();
        await page.locator('#scoreWinners').fill('14');
        await page.locator('#scoreLosers').fill('7');
        await page.locator('#save-result-btn').click();
        await page.locator('#resultModal').waitFor({state:'hidden'});
    } else {
        const edit=page.locator('.edit-score-btn:visible').first();
        const matchId=await edit.getAttribute('data-match-id');
        await edit.click();
        const form=page.locator('#match-'+matchId+' .score-input-form');
        await form.locator('input').nth(0).fill('14');
        await form.locator('input').nth(1).fill('7');
        await form.locator('.submit-score-btn').click();
        await form.waitFor({state:'detached'});
    }
    const data=await sync(page,events,king);
    assert.ok(data.ranking.some(player=>player.matchPoints===14));
    const player=data.ranking.find(player=>player.matchPoints===14);
    await page.locator(`[data-ranking-toggle="${player.playerId}"]`).click();
    const stats=await page.locator(`[data-ranking-details="${player.playerId}"] dl`).first().locator('dd').allTextContents();
    assert.equal(Number(stats[0]),14);
    assert.equal(Number(stats[king?3:2]),king?player.gamesPlayed:player.matchesPlayed);
    assert.equal(Number(stats[king?4:3]),king?player.wins:player.matchesWon);
    assert.equal(Number(stats[king?5:4]),king?player.losses:player.matchesLost);
    report.push({format:king?'king':'americano',scoreCorrection:true,statsUpdated:true});
}
async function corrections(page,events,king) {
    await page.goto(new URL(path(events,king)+'?lang=ru',base).href);
    await page.locator('[data-ranking-row]').first().waitFor();
    const first=await page.locator('[data-ranking-row]').first().getAttribute('data-ranking-row');
    await page.locator(`[data-ranking-toggle="${first}"]`).click();
    await page.locator(`[data-ranking-details="${first}"] [data-open-points]`).click();
    const dialog=page.locator('#additional-points-editor');
    const form=dialog.locator('[data-entry-form]');
    await form.waitFor();
    await form.locator('[name=amount]').fill('7');
    await form.locator('[name=note]').fill('QA draft remains');
    await refresh(page,king);
    assert.equal(await dialog.isVisible(),true);
    assert.equal(await form.locator('[name=amount]').inputValue(),'7');
    assert.equal(await form.locator('[name=note]').inputValue(),'QA draft remains');
    await form.locator('button[type=submit]').click();
    await page.waitForFunction(()=>document.querySelector('#additional-points-editor [data-status]').textContent.length>0);
    assert.match(await dialog.locator('[data-status]').innerText(),/сохран/i);
    await sync(page,events,king);
    const entry=dialog.locator('.additional-points-entry').filter({hasText:'QA draft remains'});
    await entry.getByRole('button',{name:'Изменить',exact:true}).click();
    await form.locator('[name=direction]').selectOption('-1');
    await form.locator('[name=amount]').fill('3');
    await form.locator('[name=note]').fill('QA corrected negative');
    await form.locator('button[type=submit]').click();
    await dialog.locator('.additional-points-entry').filter({hasText:'QA corrected negative'}).waitFor();
    await sync(page,events,king);
    assert.ok(await dialog.locator('[data-audit-list] li').count()>0);
    page.once('dialog', dialog => dialog.accept());
    await dialog.locator('.additional-points-entry').filter({hasText:'QA corrected negative'}).getByRole('button',{name:'Отменить запись',exact:true}).click();
    await dialog.locator('.additional-points-entry').filter({hasText:'QA corrected negative'}).filter({hasText:'Отменена'}).waitFor();
    await sync(page,events,king);
    await dialog.locator('form[method=dialog] button').click();
    report.push({format:king?'king':'americano',correction:true,edit:true,draftPreserved:true});
}
function publicPath(events, king) {return king ? `/tournaments/king-of-court/${events.kingId}` : `/tournaments/americano/${events.americanoEvent}`;}
async function publicSession(account) {
    const context=await browser.newContext({viewport:{width:390,height:1000}});
    await context.route('**/*',route=>new URL(route.request().url()).origin===base.origin?route.continue():route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page=await context.newPage();
    const errors=[];page.on('pageerror',error=>errors.push(error.message));
    if(account) {
        await page.goto(new URL('/login?lang=ru',base).href);
        await page.locator('#username').fill(account.playerEmail);
        await page.locator('#password').fill(fixture.password);
        await Promise.all([page.waitForURL(url=>url.pathname!=='/login'),page.locator('form.auth-form button[type=submit]').click()]);
    }
    return {context,page,errors};
}
async function verifyPublic(page,scenario,king,language,width,loggedIn=false) {
    await page.setViewportSize({width,height:1000});
    const response=await page.goto(new URL(publicPath(scenario.events,king)+'?lang='+language,base).href);
    assert.equal(response.status(),200);
    const html=await response.text();
    assert.equal((html.match(/data-ranking-row="/g)||[]).length,scenario.count,'Full roster is already in server-rendered HTML');
    await page.waitForFunction(count=>document.querySelectorAll('.ir-public [data-ranking-row]').length===count,scenario.count);
    const table=page.locator('.individual-ranking.ir-public');
    assert.equal(await table.locator('thead th').count(),3);
    assert.equal(await table.locator('thead th').nth(2).innerText(),labels[language][0]);
    assert.equal(await table.locator('.playing-side-info').count(),0);
    assert.equal(await table.locator('[data-ranking-row]').filter({hasText:'C7'}).count(),0);
    assert.equal(await page.locator('[data-open-points], #additional-points-editor, [data-entry-form]').count(),0);
    assert.equal((await table.textContent()).includes('QA negative'),false);
    assert.equal(html.includes('QA negative'),false);
    if(!king) assert.equal(html.includes('"playerNivel"'),false,'Hidden level is not serialized into public inline data');
    const data=await sync(page,scenario.events,king,publicReference);
    const last=data.ranking.at(-1);
    const toggle=table.locator(`[data-ranking-toggle="${last.playerId}"]`);
    await toggle.click();
    const detail=table.locator(`[data-ranking-details="${last.playerId}"]`);
    assert.equal(await detail.isVisible(),true);
    assert.equal(await detail.locator('dl').first().locator('dt').count(),king?6:10);
    await detail.locator('.ir-stages > summary').click();
    if(king) {
        await page.evaluate(()=>window.kingOfCourtViewer.loadData());
    } else {
        await page.evaluate(({ranking,currentPlayerId})=>IndividualRanking.render(document.getElementById('public-americano-ranking'),ranking,{publicView:true,canEdit:true,currentPlayerId}),{ranking:data.ranking,currentPlayerId:loggedIn?scenario.playerId:null});
    }
    assert.equal(await table.locator(`[data-ranking-toggle="${last.playerId}"]`).getAttribute('aria-expanded'),'true');
    assert.equal(await table.locator(`[data-ranking-details="${last.playerId}"] .ir-stages`).getAttribute('open'),'');
    assert.equal(await page.locator('[data-open-points], #additional-points-editor').count(),0);
    assert.equal(await table.locator('.ir-me').count(),loggedIn?1:0);
    const geometry=await table.evaluate(el=>({scroll:el.scrollWidth,width:el.clientWidth,inside:[...el.querySelectorAll('[data-ranking-row]')].every(row=>{
        const rect=row.getBoundingClientRect();return rect.left>=0 && rect.right<=innerWidth+1;
    }),unlimited:[el,el.parentElement,el.closest('.individual-ranking-card')].filter(Boolean).every(node=>getComputedStyle(node).maxHeight==='none')}));
    assert.equal(geometry.inside,true,JSON.stringify({width,geometry}));
    assert.ok(geometry.scroll<=geometry.width+1,JSON.stringify({width,geometry}));
    assert.equal(geometry.unlimited,true);
    if(scenario.count===24 && language==='ru' && [390,1440].includes(width)) {
        await table.locator(`[data-ranking-toggle="${last.playerId}"]`).click();
        await table.screenshot({path:resolve(output,`public-${king?'king':'americano'}-${width}.png`),style:'body > header { visibility:hidden!important; }'});
    }
    report.push({public:true,format:king?'king':'americano',language,width,count:scenario.count,finished:scenario.finished,loggedIn,fullRoster:true,readOnly:true});
}
async function publicLiveChange(admin, participant, scenario) {
    await participant.goto(new URL(publicPath(scenario.events,true)+'?lang=ru',base).href);
    const before=await sync(participant,scenario.events,true,publicReference);
    const player=before.ranking.at(-1);
    await participant.locator(`[data-ranking-toggle="${player.playerId}"]`).click();
    await participant.locator(`[data-ranking-details="${player.playerId}"] .ir-stages > summary`).click();
    await admin.goto(new URL(path(scenario.events,true)+'?lang=ru',base).href);
    await admin.locator(`[data-ranking-row="${player.playerId}"] .ir-edit`).click();
    const form=admin.locator('#additional-points-editor [data-entry-form]');
    await form.waitFor();
    await form.locator('[name=amount]').fill('7');
    await form.locator('[name=note]').fill('QA public live private');
    await form.locator('button[type=submit]').click();
    await admin.locator('.additional-points-entry').filter({hasText:'QA public live private'}).waitFor();
    await participant.evaluate(()=>window.kingOfCourtViewer.loadData());
    const after=await sync(participant,scenario.events,true,publicReference);
    assert.equal(after.ranking.find(row=>row.playerId===player.playerId).totalPoints,player.totalPoints+7);
    assert.equal(await participant.locator(`[data-ranking-toggle="${player.playerId}"]`).getAttribute('aria-expanded'),'true');
    assert.equal(await participant.locator(`[data-ranking-details="${player.playerId}"] .ir-stages`).getAttribute('open'),'');
    assert.equal((await participant.locator('.ir-public').textContent()).includes('QA public live private'),false);
    assert.equal(await participant.locator('[data-open-points]').count(),0);
    report.push({public:true,liveChange:true,totalUpdated:true,expandedPreserved:true,privateNoteAbsent:true});
}
try {
    for(const language of ['ru','es','en']) {
        const {context,page,errors}=await login(fixture.accounts[0],language);
        for(const width of [320,390,768,1440,1920]) for(const king of [true,false]) await verify(page,fixture.clubEvents,king,language,width);
        if(language==='ru') for(const king of [true,false]) {
            await scoreCorrection(page,fixture.clubEvents,king);
            await corrections(page,fixture.clubEvents,king);
        }
        assert.deepEqual(errors,[]);
        await context.close();
    }
    for(const account of fixture.accounts.slice(1)) {
        const {context,page,errors}=await login(account,'ru');
        const events=account.role==='ORGANIZER'?fixture.organizerEvents:fixture.clubEvents;
        for(const king of [true,false]) {
            await page.goto(new URL(path(events,king),base).href);
            await page.locator('[data-ranking-row]').first().waitFor();
            assert.equal(await page.locator('[data-open-points]').count()>0,true);
            const response=await page.request.get(new URL(`/admin/tournaments/${king?events.kingEvent:events.americanoEvent}/additional-points`,base).href);
            assert.equal(response.status(),200);
        }
        if(['ORGANIZER','CLUB_ADMIN'].includes(account.role)) {
            const foreign=account.role==='ORGANIZER'?fixture.clubEvents:fixture.organizerEvents;
            assert.equal((await page.request.get(new URL(path(foreign,true),base).href)).status(),403);
            assert.equal((await page.request.get(new URL(path(foreign,false),base).href)).status(),403);
            assert.equal((await page.request.get(new URL(`/admin/tournaments/${foreign.americanoEvent}/additional-points`,base).href)).status(),403);
        }
        assert.deepEqual(errors,[]);
        report.push({role:account.role,ownAccess:true,foreignDenied:['ORGANIZER','CLUB_ADMIN'].includes(account.role)});
        await context.close();
    }
    const publicAdmin=await login(fixture.accounts[0],'ru');
    publicReference=publicAdmin.page.request;
    const anonymous=await publicSession();
    for(const language of ['ru','es','en']) {
        for(const width of [320,390,768,1440]) {
            for(const scenario of fixture.publicScenarios) {
                for(const king of [true,false]) await verifyPublic(anonymous.page,scenario,king,language,width);
            }
        }
    }
    assert.deepEqual(anonymous.errors,[]);
    await anonymous.context.close();
    const scenario=fixture.publicScenarios.find(item=>item.count===24);
    const participant=await publicSession(scenario);
    for(const king of [true,false]) await verifyPublic(participant.page,scenario,king,'ru',390,true);
    assert.deepEqual(participant.errors,[]);
    await publicLiveChange(publicAdmin.page,participant.page,scenario);
    await participant.context.close();
    await publicAdmin.context.close();
    const noScript=await browser.newContext({javaScriptEnabled:false,viewport:{width:390,height:1000}});
    await noScript.route('**/*',route=>new URL(route.request().url()).origin===base.origin?route.continue():route.abort());
    const serverPage=await noScript.newPage();
    for(const king of [true,false]) {
        assert.equal((await serverPage.goto(new URL(publicPath(scenario.events,king),base).href)).status(),200);
        assert.equal(await serverPage.locator('[data-ranking-row]').count(),24);
        assert.equal(await serverPage.locator('.ir-public thead th').count(),3);
        assert.equal(await serverPage.locator('[data-open-points], #additional-points-editor').count(),0);
        report.push({public:true,format:king?'king':'americano',javaScript:false,count:24,fullRoster:true});
    }
    await noScript.close();
    await writeFile(resolve(output,'report.json'),JSON.stringify(report,null,2));
    console.log(JSON.stringify({passed:report.length}));
} finally {await browser.close();}
