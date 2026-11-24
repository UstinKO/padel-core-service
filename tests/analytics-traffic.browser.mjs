import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import test, { before, after } from 'node:test';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright-core');
let browser;
let server;
let base;

before(async () => {
    server = createServer(async (req, res) => {
        const url = new URL(req.url, 'http://localhost');
        res.setHeader('Cache-Control', 'no-store');
        if (url.pathname === '/js/analytics-traffic.js') {
            res.setHeader('Content-Type', 'text/javascript');
            res.end(await readFile(new URL('../src/main/resources/static/js/analytics-traffic.js', import.meta.url)));
        } else {
            const name = url.pathname.slice(1) || 'visitor';
            if (!['visitor', 'internal', 'test', 'missing', 'disabled'].includes(name)) {
                res.writeHead(404).end();
                return;
            }
            res.setHeader('Content-Type', 'text/html');
            res.end(await readFile(new URL('../target/analytics-traffic/' + name + '.html', import.meta.url)));
        }
    });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    base = 'http://127.0.0.1:' + server.address().port;
    browser = await chromium.launch({
        ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
        headless: true, args: ['--no-sandbox']
    });
});

after(async () => {
    await browser?.close();
    await new Promise(resolve => server.close(resolve));
});

async function open(width, name = 'visitor', query = '', consent = 'accepted', contextOptions = {}) {
    const context = await browser.newContext({ viewport: { width, height: 844 }, ...contextOptions });
    // Ни Google, ни production-сайт не получают событий автоматической проверки.
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        if (url.origin === base) return route.continue();
        if (url.hostname === 'www.googletagmanager.com') {
            return route.fulfill({ contentType: 'text/javascript', body: '' });
        }
        return route.abort();
    });
    if (consent) await context.addCookies([{ name: 'cookieConsent', value: consent, url: base }]);
    const page = await context.newPage();
    await page.goto(base + '/' + name + query);
    return { context, page };
}

async function commands(page) {
    return page.evaluate(() => (window.dataLayer || []).map(value => Array.from(value)));
}

async function traffic(page) {
    return (await commands(page)).find(row => row[0] === 'set')?.[1];
}

for (const width of [1440, 390]) {
    for (const [name, group, reason] of [
        ['visitor', 'visitor', 'public'], ['internal', 'internal', 'staff_account'],
        ['test', 'test', 'test_event'], ['missing', 'unclassified', 'missing_context']
    ]) {
        test(`${width}: ${name}, первый просмотр и последующие события имеют общий контекст`, async () => {
            const { context, page } = await open(width, name);
            try {
                const rows = await commands(page);
                const defaults = rows.findIndex(row => row[0] === 'set');
                const configs = rows.filter(row => row[0] === 'config');
                assert.equal(configs.length, 1);
                assert.ok(defaults < rows.findIndex(row => row[0] === 'config'));
                assert.equal(rows[defaults][1].traffic_group, group);
                assert.equal(rows[defaults][1].traffic_reason, reason);
                assert.equal(configs[0][2].traffic_group, group);
                assert.equal(configs[0][2].traffic_reason, reason);
                await page.evaluate(() => window.gtag('event', 'tournament_registration_complete', { tournament_id: 999 }));
                assert.equal((await commands(page)).filter(row => row[1] === 'tournament_registration_complete').length, 1);
                assert.equal((await commands(page)).filter(row => row[0] === 'config').length, 1);
            } finally { await context.close(); }
        });
    }

    test(`${width}: ручной тест сохраняется при смене страницы и снимается явно`, async () => {
        const { context, page } = await open(width, 'visitor', '?analytics_test=on&token=secret&codigo=secret&utm_source=qa');
        try {
            assert.equal((await traffic(page)).traffic_reason, 'manual_test');
            const config = (await commands(page)).find(row => row[0] === 'config')[2];
            assert.ok(!config.page_location.includes('secret'));
            assert.ok(!config.page_path.includes('analytics_test'));
            assert.ok(config.page_path.includes('utm_source=qa'));
            await page.goto(base + '/visitor');
            assert.equal((await traffic(page)).traffic_group, 'test');
            await page.reload();
            assert.equal((await traffic(page)).traffic_group, 'test');
            await page.goto(base + '/visitor?analytics_test=off');
            assert.equal((await traffic(page)).traffic_group, 'visitor');
            await page.goto(base + '/visitor');
            assert.equal((await traffic(page)).traffic_group, 'visitor');
            await page.goto(base + '/internal?analytics_test=off');
            assert.equal((await traffic(page)).traffic_group, 'internal');
        } finally { await context.close(); }
    });

    test(`${width}: истечение и неизвестное значение не скрывают обычного посетителя`, async () => {
        const { context, page } = await open(width);
        try {
            await page.evaluate(() => localStorage.setItem('padel.analytics.testUntil', String(Date.now() - 1)));
            await page.reload();
            assert.equal((await traffic(page)).traffic_group, 'visitor');
            await page.goto(base + '/visitor?analytics_test=unknown');
            assert.equal((await traffic(page)).traffic_group, 'visitor');
        } finally { await context.close(); }
    });

    test(`${width}: закрытое хранилище не ломает страницу и явный режим`, async () => {
        const { context, page } = await open(width);
        try {
            await context.addInitScript(() => Object.defineProperty(window, 'localStorage', {
                get() { throw new Error('storage disabled'); }
            }));
            await page.goto(base + '/visitor?analytics_test=on');
            assert.equal((await traffic(page)).traffic_group, 'test');
            await page.goto(base + '/visitor?analytics_test=off');
            assert.equal((await traffic(page)).traffic_group, 'visitor');
        } finally { await context.close(); }
    });

    test(`${width}: ошибка загрузки помощника сохраняет аналитику с неопределённым типом`, async () => {
        const { context, page } = await open(width);
        try {
            await context.route('**/js/analytics-traffic.js', route => route.abort());
            await page.goto(base + '/visitor?analytics_test=on&token=secret');
            assert.equal((await traffic(page)).traffic_group, 'unclassified');
            const config = (await commands(page)).find(row => row[0] === 'config')[2];
            assert.ok(!config.page_location.includes('secret'));
            assert.ok(!config.page_path.includes('analytics_test'));
        } finally { await context.close(); }
    });

    for (const consent of ['', 'rejected']) {
        test(`${width}: режим теста не меняет отсутствие согласия ${consent}`, async () => {
            const { context, page } = await open(width, 'visitor', '?analytics_test=on', consent);
            try {
                const row = (await commands(page)).find(value => value[0] === 'consent');
                assert.equal(row[2].analytics_storage, 'denied');
                assert.equal((await traffic(page)).traffic_group, 'test');
            } finally { await context.close(); }
        });
    }

    test(`${width}: без Measurement ID тег вообще не подключается`, async () => {
        const { context, page } = await open(width, 'disabled');
        try { assert.equal((await commands(page)).length, 0); }
        finally { await context.close(); }
    });
}
