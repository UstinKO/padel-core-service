import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import test, { before, after } from 'node:test';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright-core');
const base = process.env.CALENDAR_BASE_URL || 'http://127.0.0.1:18080';
let browser;

before(async () => {
    browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/usr/bin/chromium',
        headless: true, args: ['--no-sandbox'] });
});
after(async () => browser?.close());

for (const [lang, label] of [['ru', 'Добавить в календарь'], ['es', 'Agregar al calendario'], ['en', 'Add to calendar']]) {
    for (const mobile of [false, true]) {
        test(`${lang}: ${mobile ? 'mobile' : 'desktop'} menu, download, keyboard and event zone`, async () => {
            const context = await browser.newContext({ viewport: mobile ? {width: 390, height: 844} : {width: 1280, height: 900},
                timezoneId: 'Asia/Tokyo', locale: lang, acceptDownloads: true });
            const page = await context.newPage();
            const writes = [];
            page.on('request', request => {
                if (request.method() !== 'GET' && /registration|payment|waitlist|confirm/.test(request.url())) writes.push(request.url());
            });
            // Внешние шрифты, аналитика и OAuth не требуются этому сценарию.
            await page.route('**/*', route => new URL(route.request().url()).origin === new URL(base).origin
                ? route.continue() : route.abort());
            await page.goto(`${base}/torneo/9000808?lang=${lang}`, {waitUntil:'domcontentloaded'});
            const button = page.locator('#addToCalendarBtn');
            assert.equal(await button.count(), 1);
            assert.ok((await button.textContent()).includes(label));
            assert.ok((await page.locator('.info-value').allTextContents()).some(text => /90/.test(text)));
            await button.click();
            await page.locator('[data-calendar="google"][aria-disabled="false"]').waitFor();
            assert.equal(await button.getAttribute('aria-expanded'), 'true');
            assert.equal(await page.locator('[data-calendar]').count(), 3);
            const bounds = await page.locator('#calendarOptions').boundingBox();
            assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= (mobile ? 390 : 1280));
            for (const link of await page.locator('[data-calendar]').all()) {
                assert.ok((await link.boundingBox()).height >= 44);
                assert.ok((await link.getAttribute('href')).includes(`lang=${lang}`));
            }
            const downloadPromise = page.waitForEvent('download');
            await page.locator('[data-calendar="other"]').click();
            const download = await downloadPromise;
            assert.equal(download.suggestedFilename(), '1-padel-9000808.ics');
            const file = await readFile(await download.path(), 'utf8');
            assert.ok(file.includes('DTSTART:20261215T213000Z\r\n'));
            assert.ok(file.includes('DTEND:20261215T230000Z\r\n'));
            assert.ok(file.replaceAll('\r\n ', '').includes('Club Ñandú\\, Av. Córdoba 123'));
            assert.ok(file.includes('https://1-padel.com/torneo/9000808'));
            await page.keyboard.press('Escape');
            assert.equal(await button.getAttribute('aria-expanded'), 'false');
            assert.ok(await button.evaluate(element => element === document.activeElement));
            await page.keyboard.press('Enter');
            await page.locator('[data-calendar="apple"][aria-disabled="false"]').waitFor();
            await page.keyboard.press('Tab');
            assert.equal(await page.evaluate(() => document.activeElement.dataset.calendar), 'google');
            await page.locator('.calendar-help summary').click();
            await page.locator('#calendarPageLink').click();
            assert.equal(await page.locator('#calendarPageLink').inputValue(), `${base}/torneo/9000808`);
            assert.deepEqual(writes, []);
            await context.close();
        });
    }
}

test('every event format uses its own duration; private links are not listed', async () => {
    const context = await browser.newContext();
    const request = context.request;
    const ends = ['230000','233000','223000','231500','234500'];
    for (let index = 0; index < ends.length; index++) {
        const response = await request.get(`${base}/api/public/tournaments/${9000808+index}/calendar/event.ics?lang=en`);
        assert.equal(response.status(), 200);
        assert.equal(response.headers()['content-type'], 'text/calendar;charset=UTF-8');
        assert.ok((await response.text()).includes(`DTEND:20261215T${ends[index]}Z`));
    }
    const list = await (await request.get(`${base}/api/public/tournaments`)).json();
    assert.ok(!list.some(event => event.id === 9000809));
    const google = await request.get(`${base}/api/public/tournaments/9000809/calendar/google?lang=ru`, { maxRedirects:0 });
    assert.equal(google.status(), 302);
    const url = new URL(google.headers().location);
    assert.equal(url.searchParams.get('action'), 'TEMPLATE');
    assert.equal(url.searchParams.get('text'), 'Американо C8 · Парное участие — 1-Padel');
    await context.close();
});

test('missing duration and transient error show guidance without success or navigation', async () => {
    const context = await browser.newContext();
    const page = await context.newPage();
    await page.route('**/*', route => new URL(route.request().url()).origin === new URL(base).origin
        ? route.continue() : route.abort());
    await page.goto(`${base}/torneo/9000813?lang=en`, {waitUntil:'domcontentloaded'});
    await page.locator('#addToCalendarBtn').click();
    await page.locator('#calendarStatus:not([hidden])').waitFor();
    assert.ok((await page.locator('#calendarStatus').textContent()).includes('exact duration'));
    assert.equal(await page.locator('[data-calendar][aria-disabled="true"]').count(), 3);
    await page.locator('#addToCalendarBtn').click();
    await page.route('**/calendar?*', route => route.fulfill({status:503,body:'{}'}));
    await page.locator('#addToCalendarBtn').click();
    await page.locator('#calendarStatus:not([hidden])').waitFor();
    assert.ok((await page.locator('#calendarStatus').textContent()).includes('try again'));
    await context.close();
});
