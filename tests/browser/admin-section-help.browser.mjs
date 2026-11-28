import assert from 'node:assert/strict';
import {readFile, mkdir, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const base = new URL(fixture.baseUrl);
assert.equal(base.hostname, '127.0.0.1');
const {chromium} = await import(pathToFileURL(process.env.PLAYWRIGHT_MODULE_PATH).href);
const browser = await chromium.launch({executablePath:process.env.CHROME_EXECUTABLE});
const output = resolve('target/admin-section-help-qa');
await mkdir(output, {recursive:true});
const checks = [];

async function session(account) {
    const context = await browser.newContext({viewport:{width:1440,height:1000}, hasTouch:account.role === 'CLUB_ADMIN'});
    await context.route('**/*', route => new URL(route.request().url()).origin === base.origin ? route.continue() : route.abort());
    await context.addCookies([{name:'cookieConsent',value:'rejected',url:base.href}]);
    const page = await context.newPage();
    const writes = [], errors = [], dialogs = [];
    page.on('request', request => {if (!['GET','HEAD'].includes(request.method())) writes.push(request.url());});
    page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', async dialog => {dialogs.push(dialog.message()); await dialog.dismiss();});
    await page.goto(new URL('/login',base).href);
    await page.locator('#username').fill(account.email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([page.waitForURL(url => url.pathname !== '/login'), page.locator('form.auth-form button[type=submit]').click()]);
    return {context,page,writes,errors,dialogs};
}

async function go(page, path, lang) {
    const url = new URL(path, base);
    url.searchParams.set('lang', lang);
    const response = await page.goto(url.href);
    assert.equal(response.status(),200,path);
}

const drafts = page => page.locator('input, select, textarea').evaluateAll(elements => elements.map(el =>
    [el.name,el.id,el.value,el.checked,el.disabled]));

async function help(s, topic, scope=s.page) {
    const {page,writes,dialogs} = s;
    const trigger = scope.locator(`[data-help-topic="${topic}"]:visible`).first();
    await trigger.scrollIntoViewIfNeeded();
    const values = await drafts(page), url = page.url(), count = writes.length, confirmations = dialogs.length;
    const sections = await page.locator('details').evaluateAll(elements => elements.map(el => el.open));
    await trigger.click();
    const panel = page.locator('#action-help-popover');
    await panel.waitFor({state:'visible'});
    assert.equal(await panel.locator('h3').textContent(),await trigger.getAttribute('data-help-title'));
    assert.ok((await panel.locator('p').textContent()).length > 20);
    const box = await panel.boundingBox(), viewport = page.viewportSize();
    assert.ok(box.x >= 0 && box.x + box.width <= viewport.width + 1, topic+' по ширине');
    assert.ok(box.y >= 0 && box.y + box.height <= viewport.height + 1, topic+' по высоте');
    if (viewport.width <= 390) {
        const target = await trigger.boundingBox();
        assert.ok(target.width >= 44 && target.height >= 44, topic+' область нажатия');
    }
    await page.keyboard.press('Escape');
    await panel.waitFor({state:'hidden'});
    assert.equal(await trigger.evaluate(el => document.activeElement === el),true,'Возврат фокуса');
    await trigger.click();
    await panel.locator('button').click();
    await panel.waitFor({state:'hidden'});
    assert.deepEqual(await drafts(page),values,'Сохранён черновик '+topic);
    assert.deepEqual(await page.locator('details').evaluateAll(elements => elements.map(el => el.open)),sections);
    assert.equal(page.url(),url);
    assert.equal(writes.length,count,'Нет записи '+topic);
    assert.equal(dialogs.length,confirmations,'Нет подтверждения '+topic);
}

async function review(page) {
    const manual = page.locator('[data-guest-claim=manual]');
    await manual.locator('summary').first().click();
    for (const [side,id] of [['guest',fixture.guest],['account',fixture.main]]) {
        const picker = manual.locator(`[data-claim-picker=${side}]`);
        await picker.locator('input').fill(String(id));
        await picker.locator('button[type=submit]').click();
        await picker.locator('[data-claim-options] button').filter({hasText:'#'+id}).click();
    }
    await manual.locator('[data-claim-manual-review]').click();
    await manual.locator('[data-claim-merge]').waitFor({state:'visible'});
    const form = manual.locator('[data-claim-merge]');
    await form.locator('[name=contactChoice]').selectOption('KEEP_ACCOUNT');
    await form.locator('[name=sideChoice]').selectOption('ACCOUNT');
    await form.locator('[name=method]').selectOption('OTHER');
    await form.locator('[name=verified]').check();
    assert.equal(await form.locator('button[type=submit]').isEnabled(),true);
    return manual;
}

try {
    for (const account of fixture.accounts) {
        const s = await session(account), {page} = s;
        for (const lang of ['ru','es','en']) {
            await go(page,`/admin/tournaments?view=calendar&month=${fixture.date.slice(0,7)}&day=${fixture.date}&level=C7`,lang);
            await page.locator('#calendarDayEvents .event-calendar__event').first().waitFor({state:'attached'});
            await page.locator('#calendarOrganizationSettings > summary').click();
            await page.locator('#organizationName:enabled').waitFor();
            await page.locator('#organizationName').fill('QA unsaved organization');
            for (const width of [1440,390,320]) {
                await page.setViewportSize({width,height:1000});
                for (const topic of ['ac-view','ac-filters','ac-month','ac-day','ac-organization']) await help(s,topic);
                assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),'Календарь без общей прокрутки');
                if (account.role === 'SUPER_ADMIN' && lang === 'ru' && width !== 390) {
                    await page.locator('[data-help-topic="ac-organization"]').click();
                    await page.screenshot({path:resolve(output,`calendar-${width}.png`)});
                    await page.keyboard.press('Escape');
                }
            }
            checks.push(`${account.role} ${lang}: календарь, 1440/390/320, URL и черновик организации сохранены`);

            await go(page,'/admin/players',lang);
            await page.locator('#playerSearch').fill('QA');
            for (const width of [1440,390,320]) {
                await page.setViewportSize({width,height:1000});
                await help(s,'ap-search');
                if (account.role === 'SUPER_ADMIN') {
                    await help(s,'ap-level-inline');
                    await help(s,'ap-credentials');
                    await help(s,'ap-delete');
                }
            }
            if (account.role === 'SUPER_ADMIN') {
                const guestForm = page.locator('form[action="/admin/players/guests"]');
                await guestForm.locator('xpath=ancestor::details/summary').click();
                await page.locator('#guest-first-name').fill('QA unsaved');
                await page.locator('#guest-last-name').fill('Guest draft');
                for (const width of [1440,390,320]) {
                    await page.setViewportSize({width,height:1000});
                    await help(s,'ap-guest');
                }
            }
            if (['SUPER_ADMIN','ORGANIZER'].includes(account.role)) {
                const manual = await review(page);
                const queue = page.locator('[data-guest-claim=review]');
                await queue.locator('summary').first().click();
                await queue.locator('[name=search]').fill('QA unsaved search');
                for (const width of [1440,390,320]) {
                    await page.setViewportSize({width,height:1000});
                    await help(s,'ap-claim-queue',queue);
                    for (const topic of ['ap-claim-review','ap-claim-contact','ap-claim-merge']) await help(s,topic,manual);
                    assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),'Профили без общей прокрутки');
                }
                if (account.role === 'SUPER_ADMIN' && lang === 'ru') {
                    await page.locator('[data-help-topic="ap-claim-merge"]:visible').first().click();
                    await page.screenshot({path:resolve(output,'merge-phone.png')});
                    await page.keyboard.press('Escape');
                }
            } else assert.equal(await page.locator('[data-help-topic="ap-claim-review"]').count(),0);

            await go(page,`/admin/players/${account.role === 'SUPER_ADMIN' ? fixture.guest : account.player}`,lang);
            if (account.role === 'SUPER_ADMIN') {
                await page.locator('#player-first-name').fill('QA unsaved name');
                await page.locator('#player-last-name').fill('QA unsaved surname');
                await page.locator('[name=nivel]').selectOption('C3');
                await page.locator('[name=playingSide]').selectOption('EITHER');
            }
            for (const width of [1440,390,320]) {
                await page.setViewportSize({width,height:1000});
                await help(s,'ap-history');
                if (account.role === 'SUPER_ADMIN') {
                    for (const topic of ['ap-name','ap-level','ap-side','ap-credentials','ap-delete']) await help(s,topic);
                    const activation = page.locator('[data-guest-claim=activate]');
                    if (!await activation.evaluate(el => el.open)) await activation.locator('summary').click();
                    await activation.locator('[name=whatsappSenderPhone]').fill('+5491155501234');
                    await activation.locator('[name=email]').fill('qa-draft@example.test');
                    await help(s,'ap-activate');
                    if (lang === 'ru') {
                        await page.locator('[data-help-topic="ap-side"]').click();
                        await page.screenshot({path:resolve(output,`profile-${width}.png`)});
                        await page.keyboard.press('Escape');
                    }
                } else assert.equal(await page.locator('[data-help-topic="ap-level"], [data-help-topic="ap-side"]').count(),0);
                assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1),'Карточка без общей прокрутки');
                assert.ok(await page.locator('.player-actions .btn').evaluateAll(buttons => buttons.every(button => {
                    const box = button.getBoundingClientRect();
                    return box.left >= 0 && box.right <= innerWidth + 1 && button.scrollWidth <= button.clientWidth + 1;
                })),'Подписи действий полностью помещаются');
            }
            checks.push(`${account.role} ${lang}: профили, поиск и доступные действия; черновики 1440/390/320; без записи и паролей`);
        }
        assert.deepEqual(s.errors,[],'Нет ошибок JavaScript');
        assert.deepEqual(s.dialogs,[],'Нет подтверждений');
        assert.equal(s.writes.length,1,'Только вход; остальные действия проверки — чтение');
        await s.context.close();
    }
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:true,checks},null,2));
} catch (error) {
    await writeFile(resolve(output,'report.json'),JSON.stringify({ok:false,checks,error:error.stack},null,2));
    throw error;
} finally {await browser.close();}
