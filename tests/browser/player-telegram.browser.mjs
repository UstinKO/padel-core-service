import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const { chromium, webkit } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname, '127.0.0.1');
assert.equal(origin.protocol, 'http:');
const output = 'target/telegram-browser-qa';
await mkdir(output, { recursive: true });
const checks = [], errors = [], browsers = [];
let activePage;
const labels = {
    es: { connect: 'Conectar Telegram', whatsapp: 'Uso WhatsApp', manual: 'WhatsApp — contactar manualmente' },
    ru: { connect: 'Подключить Telegram', whatsapp: 'Пользуюсь WhatsApp', manual: 'WhatsApp — связаться вручную' },
    en: { connect: 'Connect Telegram', whatsapp: 'I use WhatsApp', manual: 'WhatsApp — contact manually' }
};
const record = name => { checks.push(name); console.log('PASS ' + name); };
async function context(browser, options = {}) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 1100 }, ...options });
    await ctx.route('**/*', route => {
        const url = new URL(route.request().url());
        const cdn = ['cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'fonts.googleapis.com', 'fonts.gstatic.com'];
        const asset = ['stylesheet', 'script', 'font', 'image'].includes(route.request().resourceType());
        return url.origin === origin.origin || url.protocol === 'data:' || asset && cdn.includes(url.hostname)
            ? route.continue() : route.abort();
    });
    return ctx;
}
async function pageFor(ctx) {
    const page = await ctx.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => errors.push(String(error)));
    activePage = page;
    return page;
}
async function login(page, email, destination) {
    await page.goto(fixture.baseUrl + '/login');
    const banner = page.locator('#cookieConsentBanner .cookie-btn-reject');
    await banner.waitFor({state:'visible'});
    await banner.click();
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([
        page.waitForURL(url => url.pathname === destination),
        page.locator('form.auth-form button[type=submit]').click()
    ]);
}
async function register(page, id, expected) {
    await page.goto(fixture.baseUrl + '/torneo/' + id);
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/players/tournaments/' + id + '/register');
    await page.locator('#registerButton').click();
    const result = await response;
    assert.equal(result.status(), 200);
    assert.equal((await result.json()).status, expected);
}
async function profile(page, language, preference) {
    await page.goto(fixture.baseUrl + '/perfil');
    assert.equal(await page.locator('html').getAttribute('lang'), language);
    await page.waitForFunction(value => document.querySelector('[data-tg-preference]')?.value === value, preference);
    await page.waitForFunction(() => document.querySelector('[data-tg-profile]')?.dataset.busy === 'false');
}
try {
    const browser = await chromium.launch(); browsers.push(browser);
    const adminContext = await context(browser, { locale: 'es-AR' });
    const admin = await pageFor(adminContext);
    await login(admin, fixture.adminEmail, '/admin');
    for (const scenario of fixture.scenarios) {
        const { language, email, eventId, reserveId } = scenario;
        const ctx = await context(browser, { locale: {es:'es-AR',ru:'ru-RU',en:'en-US'}[language] });
        const page = await pageFor(ctx);
        await login(page, email, '/players/dashboard');
        await register(page, eventId, 'CONFIRMED');
        const prompt = page.locator('[data-tg-prompt]');
        await prompt.waitFor({ state: 'visible' });
        assert.equal(await page.locator('html').getAttribute('lang'), language);
        assert.equal((await prompt.locator('[data-tg-connect]').textContent()).trim(), labels[language].connect);
        assert.equal((await prompt.locator('[data-tg-whatsapp]').textContent()).trim(), labels[language].whatsapp);
        assert.equal(await prompt.locator('button:visible').count(), 3);
        for (const width of [1440, 390, 320]) {
            await page.setViewportSize({ width, height: 1100 });
            await prompt.scrollIntoViewIfNeeded();
            const bounds = await prompt.boundingBox();
            assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1, 'Offer overflows at ' + width);
            for (const button of await prompt.locator('button:visible').all()) {
                const box = await button.boundingBox();
                assert.ok(box.height >= 44 && box.x >= 0 && box.x + box.width <= width + 1);
            }
            await page.screenshot({ path: `${output}/${language}-${width}.png`, fullPage: true });
            record(`${language}: registration offer fits ${width}px`);
        }
        console.log('WhatsApp control', await prompt.locator('[data-tg-whatsapp]').evaluate(element => {
            const rect = element.getBoundingClientRect();
            const target = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2);
            return {disabled:element.disabled, x:rect.x, y:rect.y, height:rect.height,
                target:target?.outerHTML.slice(0,500), cookieBanner:document.querySelector('#cookieConsentBanner')?.style.display};
        }));
        const [saved] = await Promise.all([
            page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/telegram/preference') && r.request().method() === 'PUT', {timeout:30000}),
            prompt.locator('[data-tg-whatsapp]').click({timeout:10000})
        ]);
        assert.equal(saved.status(), 200);
        await prompt.locator('[data-tg-profile-link]').waitFor({ state: 'visible' });
        assert.equal(await prompt.locator('[data-tg-connect]').isVisible(), false);
        await page.locator('#registrationConfirmModalOkBtn').click();
        await page.waitForLoadState('domcontentloaded');
        await register(page, reserveId, 'WAITLIST');
        await page.locator('#resultModal').waitFor({ state: 'visible' });
        assert.equal(await page.locator('[data-tg-prompt]').count(), 0);
        await profile(page, language, 'WHATSAPP');
        record(`${language}: WhatsApp persists and suppresses the next waitlist offer`);
        const state = await ctx.storageState();
        const otherBrowser = language === 'ru' ? await webkit.launch() : browser;
        if (otherBrowser !== browser) browsers.push(otherBrowser);
        const other = await context(otherBrowser, { locale: language, viewport:{width:390,height:1000}, storageState:{cookies:state.cookies,origins:[]} });
        await profile(await pageFor(other), language, 'WHATSAPP');
        await other.close();
        record(`${language}: a fresh browser context reads the server preference`);
        await admin.goto(fixture.baseUrl + '/admin/tournaments/' + eventId + '/payments?lang=' + language);
        await admin.waitForFunction(() => document.querySelector('[data-tg-admin]')?.dataset.busy === 'false');
        assert.ok((await admin.locator('[data-tg-participants]').textContent()).includes(labels[language].manual));
        record(`${language}: admin displays WhatsApp manual contact`);
        const preference = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/telegram/preference') && r.request().method() === 'PUT');
        await page.locator('[data-tg-preference]').selectOption('TELEGRAM');
        await page.locator('[data-tg-save-preference]').click();
        assert.equal((await preference).status(), 200);
        await page.waitForFunction(() => document.querySelector('[data-tg-profile]')?.dataset.busy === 'false');
        const link = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/telegram/link') && r.request().method() === 'POST');
        await page.locator('[data-tg-connect]').click();
        assert.equal((await link).status(), 200);
        await page.locator('[data-tg-open]').waitFor({ state:'visible' });
        const url = new URL(await page.locator('[data-tg-open]').getAttribute('href'));
        assert.equal(url.origin, 'https://t.me');
        assert.equal(url.pathname, '/OnePadelQaBot');
        record(`${language}: explicit connection issues a bot link without opening Telegram`);
        await ctx.close();
    }
    const connected = await context(browser, { locale:'en-US' });
    const player = await pageFor(connected);
    await login(player, fixture.connectedEmail, '/players/dashboard');
    const connectedStatus = player.waitForResponse(r => new URL(r.url()).pathname === '/api/players/me/telegram');
    await register(player, fixture.connectedEventId, 'CONFIRMED');
    await player.locator('#registrationConfirmModal').waitFor({ state:'visible' });
    await connectedStatus;
    assert.equal(await player.locator('[data-tg-prompt]').count(), 0);
    record('Connected profile receives no repeated offer');
    assert.deepEqual(errors, []);
    await writeFile(output + '/report.json', JSON.stringify({ok:true,checks},null,2));
} catch(error) {
    console.error(error);
    if (activePage && !activePage.isClosed()) await activePage.screenshot({path:output+'/failure.png',fullPage:true}).catch(() => {});
    await writeFile(output + '/report.json', JSON.stringify({ok:false,checks,error:String(error),errors},null,2));
    process.exitCode = 1;
} finally {
    await Promise.all(browsers.map(browser => browser.close()));
}
