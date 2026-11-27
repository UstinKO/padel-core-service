import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1');
const {chromium} = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE});
const output = resolve('target/action-help-qa');
await mkdir(output, {recursive:true});
const report = [];
const record = text => {report.push(text); console.log(text);};
const session = async (account, mobile=false) => {
    const context = await browser.newContext({viewport:{width:1440,height:1100},isMobile:mobile,hasTouch:mobile});
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page = await context.newPage();
    const writes = [], dialogs = [], errors = [];
    page.on('request', request => {if (!['GET','HEAD'].includes(request.method())) writes.push(request.url());});
    page.on('dialog', async dialog => {dialogs.push(dialog.message()); await dialog.accept();});
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(new URL('/login',base).href);
    await page.locator('#username').fill(account.email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url=>url.pathname!=='/login'),page.locator('form.auth-form button[type=submit]').click()]);
    return {context,page,writes,dialogs,errors};
};
async function go(page,path,lang='es') {
    const response = await page.goto(new URL(path+'?lang='+lang,base).href);
    assert.equal(response.status(),200,path);
}
const formValues = page => page.locator('form').evaluateAll(forms => forms.map(form => [...new FormData(form)].filter(([,value])=>typeof value==='string')));
async function explanation(s,topic,scope=s.page) {
    const {page,writes,dialogs} = s;
    const trigger = scope.locator(`[data-help-topic="${topic}"]:visible`).first();
    await trigger.scrollIntoViewIfNeeded();
    const values = await formValues(page), count = writes.length, confirmations = dialogs.length;
    await trigger.click();
    const panel = page.locator('#action-help-popover');
    await panel.waitFor({state:'visible'});
    assert.equal(await panel.locator('h3').textContent(),await trigger.getAttribute('data-help-title'));
    const text = await panel.locator('p').textContent();
    assert.ok(text.length>20 && text.length<650 && !text.includes('??'),text);
    const box=await panel.boundingBox(), view=page.viewportSize();
    assert.ok(box.x>=0 && box.x+box.width<=view.width+1,'Подсказка по ширине '+topic);
    assert.ok(box.y>=0 && box.y+box.height<=view.height+1,'Подсказка по высоте '+topic);
    await page.keyboard.press('Escape');
    await panel.waitFor({state:'hidden'});
    assert.equal(await trigger.evaluate(el=>document.activeElement===el),true,'Возврат фокуса');
    await trigger.click();
    await panel.locator('button').click();
    await panel.waitFor({state:'hidden'});
    assert.deepEqual(await formValues(page),values,'Черновик не изменён: '+topic);
    assert.equal(writes.length,count,'Подсказка не отправляет запрос: '+topic+' '+JSON.stringify(writes.slice(count)));
    assert.equal(dialogs.length,confirmations,'Подсказка не открывает подтверждения: '+topic);
}
const navClick = (page,button) => Promise.all([page.waitForNavigation({waitUntil:'domcontentloaded'}),button.click()]);
const payments = event => `/admin/tournaments/${event}/payments`;
const conduct = event => `/tournaments/team-playoff/admin/${event}`;
const row = (page,id) => page.locator('#paymentForm tbody tr').filter({has:page.locator(`input[name$=".registrationId"][value="${id}"]`)});
async function paidPresent(page,id) {
    const target=row(page,id);
    await target.locator('input[name$=".attended"]').check();
    await target.locator('input[name$=".amount"]').fill('1000');
    await target.locator('select[name$=".paymentStatus"]').selectOption('PAID');
    await target.locator('select[name$=".paymentMethod"]').selectOption('CASH');
}
async function include(page,id) {
    await navClick(page,page.locator(`button[name="registrationId"][value="${id}"]`));
}
async function score(page,id,playoff=false) {
    const prefix=playoff?'pg':'qg';
    await page.locator(`#${prefix}1-${id}`).fill('6');
    await page.locator(`#${prefix}2-${id}`).fill('4');
    const response=page.waitForResponse(r=>r.request().method()==='POST'&&r.url().includes(`/api/${playoff?'playoff':'qual'}-matches/${id}/result`));
    const button=page.locator(`[onclick="${playoff?'submitPlayoffScore':'submitQualScore'}(this)"][data-match-id="${id}"]`).first();
    if (playoff) {
        await navClick(page,button);
        assert.equal((await response).status(),200);
    } else {
        await button.click();
        const saved=await (await response).json(); assert.equal(saved.success,true,JSON.stringify(saved));
        await page.reload();
    }
    const displayed=await page.locator(`#${playoff?'pmatch':'qmatch'}-${id} .score-display`).textContent();
    assert.deepEqual(displayed.match(/\d+/g),['6','4'],'Сохранённый счёт есть в карточке после обновления');
}
async function qual(page,a,b,queue=false) {
    if (!await page.locator('#qualificationNewMatch').evaluate(el=>el.open)) await page.locator('#qualificationNewMatch > summary').click();
    await page.locator('#newMatchTeam1').selectOption(String(a));
    await page.locator('#newMatchTeam2').selectOption(String(b));
    if (!queue) await page.locator('#newMatchCourt').selectOption('1');
    await navClick(page,page.locator(`[onclick="${queue?'addToQueue':'createQualMatch'}()"]`));
}

try {
    for (const account of fixture.accounts) {
        const s=await session(account,account.role==='CLUB_ADMIN'), {page}=s;
        for (const lang of ['ru','es','en']) {
            await go(page,payments(account.event),lang);
            const target=row(page,account.pairs[0][0]);
            await target.locator('input[name$=".amount"]').fill('123.45');
            await target.locator('input[name$=".attended"]').check();
            await target.locator('input[name$=".notes"]').fill('QA unsaved');
            for (const width of [1440,390,320]) {
                await page.setViewportSize({width,height:1100});
                for (const topic of ['attendance','payment','save','include','add-pair']) await explanation(s,topic);
                if (account.role==='CLUB_ADMIN') {
                    const button=page.locator('[data-help-topic="include"]:visible').first();
                    await button.click();
                    await page.screenshot({path:resolve(output,`${lang}-${width}.png`)});
                    await page.keyboard.press('Escape');
                }
            }
            if (!await page.locator('#solo-roster').evaluate(el=>el.open)) await page.locator('#solo-roster > summary').click();
            await page.locator('#solo-list [data-help-topic="solo-pair"]').first().waitFor();
            for (const topic of ['solo-add','solo-partner','solo-pair','solo-remove']) await explanation(s,topic);
            if (account.role==='SUPER_ADMIN' && lang==='ru') {
                await page.setViewportSize({width:1440,height:1100});
                const help=page.locator('[data-help-topic="payment"]:visible').first();
                await help.hover();
                await page.locator('#action-help-popover').waitFor({state:'visible'});
                await page.mouse.move(0,0);
                await page.locator('#action-help-popover').waitFor({state:'hidden'});
                await help.focus();
                await page.locator('#action-help-popover').waitFor({state:'visible'});
                await page.keyboard.press('Enter');
                assert.equal(await page.locator('#action-help-popover button').evaluate(el=>el===document.activeElement),true);
                await page.keyboard.press('Escape');
                record('Наведение, уход мыши, фокус клавиатуры, Enter и Escape');
            }
            record(`${account.role} ${lang}: оплаты и одиночки, 1440/390/320, черновик и отсутствие отправки`);
        }
        await page.setViewportSize({width:1440,height:1100});
        await go(page,`/admin/tournaments/${account.event}`);
        if (account.role==='ADMIN') assert.equal(await page.locator('[data-help-topic="edit"]:visible').count(),0);
        else {
            await go(page,`/admin/tournaments/${account.event}/copy`);
            await explanation(s,'copy');
            await page.locator('#tipo').selectOption('AMERICANO_TEAMS');
            assert.equal(await page.locator('#modalidad').inputValue(),'DOBLES');
            for (const topic of ['create','format','schedule','category','capacity','contact','visibility']) await explanation(s,topic);
            await page.locator('#tipo').selectOption('KING_OF_COURT');
            assert.equal(await page.locator('[data-pair-help] [data-action-help]:visible').count(),0);
            await go(page,`/admin/tournaments/${account.event}/edit`);
            await page.locator('#infoDetallada').fill('QA unsaved event description');
            await explanation(s,'edit');

        }
        if (['ORGANIZER','CLUB_ADMIN'].includes(account.role)) {
            const foreign=fixture.accounts.find(a=>a.role==='SUPER_ADMIN').event;
            const response=await page.goto(new URL(payments(foreign),base).href);
            assert.ok(response.status()===403||new URL(page.url()).pathname==='/error','Чужой турнир закрыт');
        }
        await go(page,conduct(account.event));
        await explanation(s,'import'); await explanation(s,'start');
        if (account.role==='ORGANIZER') {
            await navClick(page,page.locator('form[action$="/import-registrations"] button[type="submit"]'));
            assert.equal(await page.locator('#registeredTeams tbody tr').count(),8);
            record('Массовый импорт после открытия помощи перенёс восемь пар');
        }

        assert.deepEqual(s.errors,[],'Ошибки JavaScript');
        await s.context.close();
    }

    const account=fixture.accounts.find(a=>a.role==='CLUB_ADMIN');
    const s=await session(account), {page}=s;
    await go(page,'/admin/tournaments/new');
    await page.locator('#clubId').selectOption(String(fixture.club));
    await page.locator('#fechaInicio').fill(fixture.date);
    await page.locator('#horaInicio').fill('15:00');
    await page.locator('#tipo').selectOption('AMERICANO_TEAMS');
    assert.equal(await page.locator('#modalidad').inputValue(),'DOBLES');
    await page.locator('#generoFormato').selectOption('MASCULINO');
    await page.locator('#categoriaNivel').selectOption('C7');
    await page.locator('#cupoMax').fill('8');
    await page.locator('#precio').fill('1000');
    await page.locator('#organizerCountry').selectOption('AR');
    await page.locator('#organizerPhone').fill('11 2345 6789');
    await page.locator('#organizerName').focus();
    await page.locator('[data-contact-preview]').waitFor({state:'visible'});
    await explanation(s,'contact');
    await navClick(page,page.locator('#tournamentForm button[type=submit]'));
    assert.match(new URL(page.url()).pathname,/^\/admin\/tournaments\/\d+$/);
    record('Клуб создал парное мероприятие через настоящую форму');

    await go(page,payments(account.event));
    for (const id of account.pairs.slice(0,2).flat()) await paidPresent(page,id);
    await include(page,account.pairs[0][0]);
    await include(page,account.pairs[1][0]);
    await go(page,conduct(account.event));
    await page.locator('#qualCourtsInput').fill('2');
    await explanation(s,'start');
    await navClick(page,page.locator('#startQualBtn'));
    await page.waitForFunction(()=>document.querySelector('#newMatchTeam1')?.options.length>=2);
    const first=await page.locator('#newMatchTeam1 option').evaluateAll(options=>options.map(o=>({id:o.value,name:o.textContent})));
    await qual(page,first[0].id,first[1].id);
    const firstMatch=await page.locator('[id^="qg1-"]').first().getAttribute('id');
    const firstMatchId=Number(firstMatch.split('-')[1]);
    await page.locator(`#qg1-${firstMatchId}`).fill('6');
    await page.locator(`#qg2-${firstMatchId}`).fill('3');
    for (const width of [390,320,1440]) {
        await page.setViewportSize({width,height:1100});
        await explanation(s,'score',page.locator(`#qmatch-${firstMatchId}`));
    }
    assert.equal(await page.locator(`#qg2-${firstMatchId}`).inputValue(),'3');
    await page.locator(`[data-court-match="${firstMatchId}"]`).first().click();
    const courtDialog=page.locator('#playoff-courts');
    await courtDialog.locator('input[name="court-assignment"][value="2"]').check();
    await courtDialog.locator('[data-court-consent]').check();
    for (const width of [390,320,1440]) {
        await page.setViewportSize({width,height:1100});
        await explanation(s,'court',courtDialog);
    }
    assert.equal(await courtDialog.evaluate(el=>el.open),true,'Помощь не закрывает исходный диалог');
    assert.equal(await courtDialog.locator('input[name="court-assignment"]:checked').inputValue(),'2');
    assert.equal(await courtDialog.locator('[data-court-consent]').isChecked(),true);
    await courtDialog.locator('[data-court-close]').click();
    record('Помощь внутри диалога сохранила выбранный корт и подтверждение; Escape закрыл только подсказку');

    record('Квалификация началась с двух пар; помощь сохранила несохранённый счёт');

    await go(page,payments(account.event));
    for (const id of account.pairs.slice(2).flat()) await paidPresent(page,id);
    for (const pair of account.pairs.slice(2)) await include(page,pair[0]);
    await go(page,conduct(account.event));
    await page.waitForFunction(()=>document.querySelector('#newMatchTeam1')?.options.length===6);
    const options=await page.locator('#newMatchTeam1 option').evaluateAll(options=>options.map(o=>({id:o.value,name:o.textContent})));
    const ids=Array.from({length:8},(_,i)=>[...first,...options].find(o=>o.name.includes(`Jugador ${i} A`)).id);
    await explanation(s,'ranking');
    if (await page.locator('[data-help-topic="early"]:visible').count()) await explanation(s,'early');
    await qual(page,ids[2],ids[3],true);
    for (const width of [390,320,1440]) {
        await page.setViewportSize({width,height:1100});
        await explanation(s,'court',page.locator('#queueList'));
        await explanation(s,'swap',page.locator('#queueList'));
    }
    const queued=await page.locator('#queueList .match-card').first().getAttribute('id');
    const queuedId=Number(queued.replace('queue-match-',''));
    await page.locator(`#queue-court-${queuedId}`).selectOption('2');
    await navClick(page,page.locator(`[onclick="assignCourtFromQueue(${queuedId})"]`));
    await score(page,firstMatchId); await score(page,queuedId);
    for (const [a,b] of [[4,5],[6,7],[0,2],[1,3],[4,6],[5,7]]) {
        await qual(page,ids[a],ids[b]);
        const input=await page.locator('[id^="qg1-"]').first().getAttribute('id');
        await score(page,Number(input.split('-')[1]));
    }
    record('Все восемь пар приняты постепенно; очередь, назначение корта и два матча квалификации на пару');
    await explanation(s,'playoff');
    await navClick(page,page.locator('button').filter({has:page.locator('span', {hasText:'Iniciar Playoff'})}).first());
    let saved=0;
    for (let step=0;step<20;step++) {
        const inputs=page.locator('#playoffSection input[id^="pg1-"]:visible');
        if (await inputs.count()) {
            const id=Number((await inputs.first().getAttribute('id')).split('-')[1]);
            await page.setViewportSize({width:saved % 2 === 0 ? 390 : 1440,height:1100});
            await explanation(s,'score',page.locator(`#pmatch-${id}`));
            await score(page,id,true); saved++; continue;
        }
        const queuedCards=page.locator('#queueList .match-card');
        if (await queuedCards.count()) {
            const id=Number((await queuedCards.first().getAttribute('id')).replace('queue-match-',''));
            await navClick(page,page.locator(`[onclick="assignCourtFromQueue(${id})"]`)); continue;
        }
        break;
    }
    assert.equal(saved,7,'Четвертьфиналы, полуфиналы и финал');
    await explanation(s,'finish');
    await go(page,`/admin/tournaments/${account.event}`);
    await explanation(s,'resume');
    await navClick(page,page.locator('[data-team-playoff-conduct]'));
    assert.equal(new URL(page.url()).pathname,conduct(account.event));
    record('Плей-офф завершён через интерфейс; возврат в управление после финала');
    await go(page,`/admin/tournaments/${fixture.individual}`);
    assert.equal(await page.locator('[data-action-help]:visible').count(),0,'Нет подсказок Team Playoff в индивидуальном формате');
    assert.deepEqual(s.errors,[]);
    await s.context.close();
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:true,checks:report},null,2));
} catch(error) {
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:false,checks:report,error:error.stack},null,2));
    throw error;
} finally {await browser.close();}
