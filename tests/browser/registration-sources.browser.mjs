import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
const { chromium, webkit, devices } = await import(process.env.PLAYWRIGHT_MODULE_PATH);
const fixture = JSON.parse(await readFile(process.argv[2], 'utf8'));
const origin = new URL(fixture.baseUrl);
assert.equal(origin.hostname, '127.0.0.1');
assert.equal(origin.protocol, 'http:');
const output = 'target/browser-qa';
await mkdir(output, { recursive: true });
const checks = [];
const errors = [];
const requests = [];
const browsers = [];
const record = name => { checks.push(name); console.log('PASS ' + name); };

async function context(browser, options = {}) {
    const ctx = await browser.newContext({ viewport: { width: 1365, height: 1000 }, ...options });
    await ctx.route('**/*', route => {
        const url = new URL(route.request().url());
        requests.push(url.href);
        const cdn = ['cdnjs.cloudflare.com', 'cdn.jsdelivr.net', 'fonts.googleapis.com', 'fonts.gstatic.com'];
        const staticAsset = ['stylesheet', 'script', 'font', 'image'].includes(route.request().resourceType());
        return url.origin === origin.origin || url.protocol === 'data:' || staticAsset && cdn.includes(url.hostname)
            ? route.continue() : route.abort();
    });
    return ctx;
}
async function pageFor(ctx) {
    const page = await ctx.newPage();
    page.setDefaultTimeout(15000);
    page.on('pageerror', error => errors.push(String(error)));
    return page;
}
async function login(page, email, destination) {
    await page.goto(fixture.baseUrl + '/login');
    const banner = page.locator('#cookieConsentBanner .cookie-btn-accept');
    if (await banner.isVisible()) await banner.click();
    await page.locator('#username').fill(email);
    await page.locator('#password').fill(fixture.password);
    await Promise.all([
        page.waitForURL(url => url.pathname === destination),
        page.locator('form.auth-form button[type=submit]').click()
    ]);
}
async function submit(page) {
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/api/admin/marketing/registration-sources');
    await page.locator('#source-filters button[type=submit]').click();
    await response;
    await page.locator('#source-results').waitFor({ state: 'visible' });
}
async function report(page, language, source) {
    await page.goto(fixture.baseUrl + '/admin/marketing/registration-sources?lang=' + language);
    await page.locator('#source-results').waitFor({ state: 'visible' });
    assert.equal(await page.locator('.source-advanced').getAttribute('open'), null);
    await page.locator('.source-advanced summary').click();
    await page.locator('[name=tournamentId]').fill(String(fixture.tournamentId));
    await submit(page);
    assert.equal((await page.locator('#source-participants').textContent()).trim(), '1');
    assert.equal((await page.locator('#source-firstRegistrations').textContent()).trim(), '1');
    assert.equal((await page.locator('#source-pairs').textContent()).trim(), '0');
    assert.match(await page.locator('#source-rows').textContent(), new RegExp(source));
    const titles = { es: 'Origen de inscripciones', ru: 'Источники записей', en: 'Registration sources' };
    assert.equal((await page.locator('h1').textContent()).trim(), titles[language]);
    if ((await page.viewportSize()).width > 768) {
        const brand = await page.locator('.navbar-brand').boundingBox();
        const navigation = await page.locator('#navbarNav').boundingBox();
        assert.ok(brand.x + brand.width <= navigation.x + 1, 'Header navigation overlaps the brand');
    }
}

try {
    const browser = await chromium.launch(); browsers.push(browser);
    const playerContext = await context(browser);
    const player = await pageFor(playerContext);
    await player.goto(fixture.baseUrl + '/?utm_source=reby&utm_medium=qr&utm_campaign=offline_tournaments');
    await player.evaluate(() => window.metaMarketing.ready());
    assert.ok(!(await playerContext.cookies()).some(cookie => cookie.name === 'padel_attribution'));
    record('no identifier before marketing consent');
    const captured = player.waitForResponse(r => new URL(r.url()).pathname === '/api/marketing/context' && r.request().method() === 'POST');
    await player.locator('#cookieConsentBanner .cookie-btn-accept').click();
    assert.equal((await captured).status(), 200);
    await player.evaluate(() => window.metaMarketing.ready());
    assert.ok((await playerContext.cookies()).some(cookie => cookie.name === 'padel_attribution'));
    record('real consent click captures the leaflet source');
    await login(player, fixture.playerEmail, '/players/dashboard');
    await player.goto(fixture.baseUrl + '/torneo/' + fixture.tournamentId + '?utm_source=instagram&utm_medium=social&utm_campaign=browser_qa');
    await player.evaluate(() => window.metaMarketing.ready());
    const registered = player.waitForResponse(r => new URL(r.url()).pathname === '/players/tournaments/' + fixture.tournamentId + '/register');
    await player.locator('#registerButton').click();
    const registration = await registered;
    assert.equal(registration.status(), 200);
    assert.equal((await registration.json()).success, true);
    record('registration through the real player interface succeeds');
    await player.goto(fixture.baseUrl + '/admin/marketing/registration-sources');
    assert.equal(await player.locator('#registration-sources').count(), 0);
    record('player cannot open the admin report');
    const guestContext = await context(browser);
    const guest = await pageFor(guestContext);
    await guest.goto(fixture.baseUrl + '/admin/marketing/registration-sources');
    assert.equal(new URL(guest.url()).pathname, '/login');
    record('guest is redirected to login');
    await guestContext.close();
    const adminContext = await context(browser);
    const admin = await pageFor(adminContext);
    await login(admin, fixture.adminEmail, '/admin');
    for (const language of ['es', 'ru', 'en']) {
        await report(admin, language, 'instagram');
        record('desktop report renders and filters in ' + language);
    }
    record('desktop navigation does not overlap the brand');
    await admin.evaluate(() => window.scrollTo(0, 0));
    await admin.screenshot({ path: output + '/desktop-en.png', fullPage: true });
    await admin.locator('[name=model]').selectOption('first_known');
    await submit(admin);
    assert.match(await admin.locator('#source-rows').textContent(), /reby/);
    record('first source remains Reby after the Instagram registration');
    await admin.locator('[name=source]').fill('not_existing_browser_source');
    await submit(admin);
    assert.equal((await admin.locator('#source-participants').textContent()).trim(), '0');
    assert.equal(await admin.locator('#source-empty').isVisible(), true);
    record('empty filtered result shows zero');
    await admin.locator('[name=source]').fill('');
    const validFrom = await admin.locator('[name=from]').inputValue();
    await admin.locator('[name=from]').fill('2020-01-01');
    await admin.locator('#source-filters button[type=submit]').click();
    const invalidMessage = await admin.locator('#registration-sources').getAttribute('data-invalid');
    await admin.waitForFunction(text => document.getElementById('source-status').textContent === text, invalidMessage);
    record('invalid period is explained');
    await admin.locator('[name=from]').fill(validFrom);
    await submit(admin);
    const api = '**/api/admin/marketing/registration-sources?*';
    await admin.route(api, route => route.fulfill({ status: 500, contentType: 'application/json', body: '{}' }));
    await admin.locator('#source-filters button[type=submit]').click();
    const errorMessage = await admin.locator('#registration-sources').getAttribute('data-error');
    await admin.waitForFunction(text => document.getElementById('source-status').textContent === text, errorMessage);
    assert.equal(await admin.locator('#source-results').isVisible(), false);
    record('server error does not masquerade as zero');
    await admin.unroute(api);
    assert.ok(!requests.some(url => url.includes('connect.facebook.net')));
    record('internal attribution works with Meta disabled');
    const safari = await webkit.launch(); browsers.push(safari);
    const mobileContext = await context(safari, { ...devices['iPhone 13 Pro'] });
    const mobile = await pageFor(mobileContext);
    await login(mobile, fixture.adminEmail, '/admin');
    await report(mobile, 'ru', 'instagram');
    const fits = await mobile.locator('#source-filters').evaluate(element => element.getBoundingClientRect().right <= innerWidth + 1);
    assert.equal(fits, true);
    await mobile.evaluate(() => window.scrollTo(0, 0));
    await mobile.screenshot({ path: output + '/mobile-webkit-ru.png', fullPage: true });
    record('WebKit with iPhone 13 Pro viewport renders the Russian report');
    assert.deepEqual(errors, []);
    record('no uncaught browser JavaScript errors');
    await writeFile(output + '/report.json', JSON.stringify({ passed: true, browsers: ['Chromium', 'WebKit (iPhone 13 Pro emulation)'], checks }, null, 2));
    console.log(JSON.stringify({ passed: true, checks: checks.length }));
} catch (error) {
    await writeFile(output + '/report.json', JSON.stringify({ passed: false, checks, error: String(error), errors }, null, 2));
    console.error(error);
    process.exitCode = 1;
} finally {
    for (const browser of browsers) await browser.close();
}
