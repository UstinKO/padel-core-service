import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import test, { before, after } from 'node:test';

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE_PATH || 'playwright-core');
let browser;
let server;
let base;
const channels = [
    ['telegram', 'telegram', 'social', 'telegram_posts'],
    ['stories', 'instagram', 'social', 'instagram_stories'],
    ['flobi', 'flobi', 'referral', 'flobi_tournaments']
];
const titles = { ru: 'Ссылка для публикации', es: 'Enlace para publicar', en: 'Publication link' };

before(async () => {
    server = createServer(async (req, res) => {
        const path = new URL(req.url, 'http://localhost').pathname;
        const asset = ['/js/admin-publication-link.js', '/js/analytics-traffic.js',
            '/css/admin-publication-link.css', '/css/admin.css', '/css/style.css'].includes(path);
        const landing = /^\/padel\/torneo\/(185|196)$/.test(path);
        const language = /^\/(ru|es|en)$/.exec(path)?.[1];
        if (!asset && !landing && !language) return res.writeHead(404).end();
        const file = asset ? '../src/main/resources/static' + path
            : landing ? '../target/analytics-traffic/visitor.html' : `../target/publication-link/${language}.html`;
        let body = await readFile(new URL(file, import.meta.url), 'utf8');
        if (language) {
            body = body.replaceAll('https://configured.example/padel',
                base + '/padel?utm_source=old&amp;utm_source=duplicate&amp;utm_medium=old&amp;utm_campaign=old&amp;lang=ru#old');
        }
        const type = path.endsWith('.js') ? 'text/javascript' : path.endsWith('.css') ? 'text/css' : 'text/html';
        res.setHeader('Content-Type', type + '; charset=utf-8');
        res.end(body);
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

async function open(language, width) {
    const context = await browser.newContext({ viewport: { width, height: 844 } });
    await context.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: base });
    // Внешние SDK не загружаются, проверочные посещения не поступают в production.
    await context.route('**/*', route => new URL(route.request().url()).origin === base
        ? route.continue() : route.fulfill({ contentType: 'text/javascript', body: '' }));
    const page = await context.newPage();
    await page.goto(base + '/' + language + '?utm_source=admin&utm_campaign=private');
    await page.locator('[data-publication-id="185"]').click();
    return { context, page, dialog: page.locator('#tournamentPublicationDialog') };
}

function checkLink(value, id, channel) {
    const [, source, medium, campaign] = channel;
    const url = new URL(value);
    assert.equal(url.origin, base);
    assert.equal(url.pathname, '/padel/torneo/' + id);
    assert.equal(url.hash, '');
    assert.deepEqual([...url.searchParams.entries()], [
        ['utm_source', source], ['utm_medium', medium],
        ['utm_campaign', campaign], ['utm_content', 'torneo_' + id]
    ]);
}

for (const language of ['ru', 'es', 'en']) {
    for (const width of [1440, 390, 320]) {
        test(`${language}/${width}: выбор канала, буфер обмена, повторные UTM и ID события`, async () => {
            const { context, page, dialog } = await open(language, width);
            try {
                assert.equal(await dialog.locator('h2').textContent(), titles[language]);
                assert.equal(await dialog.locator('[data-publication-channel]').count(), 3);
                for (const channel of channels) {
                    await dialog.locator(`[data-publication-channel="${channel[0]}"]`).click();
                    const value = await dialog.locator('#publicationLinkUrl').inputValue();
                    checkLink(value, 185, channel);
                    assert.equal(await dialog.locator('[aria-pressed="true"]').count(), 1);
                    await dialog.locator('[data-publication-copy]').click();
                    await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.length > 0);
                    assert.equal(await page.evaluate(() => navigator.clipboard.readText()), value);
                }
                assert.ok(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth));
                const box = await dialog.boundingBox();
                assert.ok(box.x >= 0 && box.x + box.width <= width);
                await page.keyboard.press('Escape');
                assert.equal(await page.locator('[data-publication-id="185"]').evaluate(el => el === document.activeElement), true);
                await page.locator('[data-publication-id="196"]').click();
                checkLink(await dialog.locator('#publicationLinkUrl').inputValue(), 196, channels[0]);
                await dialog.locator('[data-publication-channel="flobi"]').click();
                checkLink(await dialog.locator('#publicationLinkUrl').inputValue(), 196, channels[2]);
                assert.equal(await dialog.locator('[role="status"]').textContent(), '');
            } finally { await context.close(); }
        });
    }
}

test('При отказе Clipboard API ссылка Flobi выделяется для ручного копирования', async () => {
    const { context, page, dialog } = await open('ru', 390);
    try {
        await page.evaluate(() => Object.defineProperty(Object.getPrototypeOf(navigator.clipboard), 'writeText', {
            value: () => Promise.reject(new DOMException('Denied', 'NotAllowedError'))
        }));
        await dialog.locator('[data-publication-channel="flobi"]').click();
        await dialog.locator('[data-publication-copy]').click();
        await page.waitForFunction(() => document.querySelector('[role="status"]').textContent.length > 0);
        assert.equal(await dialog.locator('[role="status"]').textContent(), await dialog.getAttribute('data-manual'));
        assert.equal(await dialog.locator('#publicationLinkUrl').evaluate(el => el.selectionEnd - el.selectionStart),
            (await dialog.locator('#publicationLinkUrl').inputValue()).length);
    } finally { await context.close(); }
});

test('Скопированная ссылка передаёт Flobi UTM в настоящий общий тег GA4', async () => {
    const { context, page, dialog } = await open('ru', 390);
    try {
        await dialog.locator('[data-publication-channel="flobi"]').click();
        const link = await dialog.locator('#publicationLinkUrl').inputValue();
        await page.goto(link);
        const configs = await page.evaluate(() => window.dataLayer.map(row => Array.from(row)).filter(row => row[0] === 'config'));
        assert.equal(configs.length, 1);
        assert.equal(configs[0][2].page_location, link);
        checkLink(configs[0][2].page_location, 185, channels[2]);
        assert.equal(configs[0][2].page_path, new URL(link).pathname + new URL(link).search);
    } finally { await context.close(); }
});
