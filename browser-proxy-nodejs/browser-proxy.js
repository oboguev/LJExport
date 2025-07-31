const express = require('express');
const { chromium } = require('playwright-core');
const path = require('path');
const app = express();
const port = 3000;

// Use system-installed Chrome
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

// Force base64 mode regardless of content type (can be toggled)
const FORCE_BASE64 = true;

// Use a persistent browser profile
const userDataDir = 'C:\\ProxyChromeProfile';

// Accept large request bodies
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

let context = null;

// Launch persistent browser context
(async () => {
    try {
        context = await chromium.launchPersistentContext(userDataDir, {
            headless: false,
            executablePath: CHROME_PATH,
            args: ['--no-sandbox', '--disable-gpu']
        });

        app.listen(port, () => {
            console.log(`Proxy server listening at http://localhost:${port}`);
        });
    } catch (err) {
        console.error('Failed to initialize browser:', err);
        process.exit(1);
    }
})();

// Main request handler
app.post('/fetch', async (req, res) => {
    const { url, method = 'GET', headers = {}, body = null } = req.body;

    if (!url) return res.status(400).json({ error: 'Missing URL' });
    if (!context) return res.status(503).json({ error: 'Browser not ready' });

    const page = await context.newPage();

    // Convert ordered header array to an object with preserved order
    let headerMap = {};
    if (Array.isArray(req.body.headersOrdered)) {
        for (const [name, value] of req.body.headersOrdered) {
            headerMap[name] = value;
        }
    } else {
        headerMap = headers;
    }

    try {
        // NOTE: Modify this to handle POST, custom headers, etc., if needed
        const response = await page.goto(url, {
            waitUntil: 'domcontentloaded',
            timeout: 30000
        });

        const status = response?.status() ?? 0;
        const respHeaders = response?.headers() ?? {};
        const contentType = respHeaders['content-type'] || '';
        const html = await page.content();

        const buffer = Buffer.from(html, 'utf-8');
        const encodedBody = FORCE_BASE64
            ? buffer.toString('base64')
            : html;

        res.json({
            status,
            headers: respHeaders,
            contentType,
            encoding: FORCE_BASE64 ? 'base64' : 'utf-8',
            body: encodedBody
        });
    } catch (err) {
        res.status(500).json({ error: err.toString() });
    } finally {
        await page.close();
    }
});

// Get current cookies
app.get('/cookies', async (req, res) => {
    try {
        const cookies = await context.cookies();
        res.json({ cookies });
    } catch (err) {
        res.status(500).json({ error: err.toString() });
    }
});

// Get user agent string
app.get('/useragent', async (req, res) => {
    try {
        const page = await context.newPage();
        const userAgent = await page.evaluate(() => navigator.userAgent);
        await page.close();
        res.json({ userAgent });
    } catch (err) {
        res.status(500).json({ error: err.toString() });
    }
});

// Get Sec-CH-UA header value (via dummy fetch)
app.get('/sec-ch-ua', async (req, res) => {
    try {
        const page = await context.newPage();
        const [request] = await Promise.all([
            page.waitForRequest(request => request.url().includes('example.com')),
            page.goto('https://example.com', { waitUntil: 'domcontentloaded' })
        ]);
        const secChUa = request.headers()['sec-ch-ua'] || null;
        await page.close();
        res.json({ 'sec-ch-ua': secChUa });
    } catch (err) {
        res.status(500).json({ error: err.toString() });
    }
});

