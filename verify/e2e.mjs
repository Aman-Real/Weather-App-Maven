// End-to-end check of the SkyCast PWA in Chromium (Playwright) against verify/mock-server.mjs.
// Usage: node verify/e2e.mjs      (screenshots go to verify/out/)
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { start } from './mock-server.mjs';

// require() also finds a globally installed Playwright (NODE_PATH); ESM import would not.
const { chromium } = createRequire(import.meta.url)('playwright');

const OUT = join(dirname(fileURLToPath(import.meta.url)), 'out');
const PORT = 8099;
const BASE = `http://localhost:${PORT}`;
const PNG_1PX = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok: Boolean(ok), detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  (${detail})` : ''}`);
}

async function stubThirdParty(context) {
  // Map tiles and RainViewer are blocked in CI sandboxes; serve stand-ins so the map renders deterministically.
  await context.route('https://tile.openstreetmap.org/**', (r) => r.fulfill({ body: PNG_1PX, contentType: 'image/png' }));
  await context.route('https://tiles.openfreemap.org/styles/**', (r) => r.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ version: 8, sources: {}, layers: [{ id: 'bg', type: 'background', paint: { 'background-color': '#dfe7f2' } }] }),
  }));
  await context.route('https://tilecache.rainviewer.com/**', (r) => r.fulfill({ body: PNG_1PX, contentType: 'image/png' }));
  await context.route('https://api.rainviewer.com/**', (r) => r.fulfill({
    contentType: 'application/json',
    body: JSON.stringify({ version: '2.0', generated: 1761300000, host: 'https://tilecache.rainviewer.com',
      radar: { past: [1, 2, 3].map((i) => ({ time: 1761300000 + i * 600, path: `/v2/radar/${1761300000 + i * 600}` })), nowcast: [] } }),
  }));
}

async function run(viewport, label, mobile, colorScheme) {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport, isMobile: mobile, hasTouch: mobile, deviceScaleFactor: mobile ? 2 : 1, colorScheme });
  await context.grantPermissions(['notifications'], { origin: BASE });
  await stubThirdParty(context);
  const page = await context.newPage();
  const errors = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push(e.message));
  // Headless Chromium always reports Notification.permission = 'denied', so simulate the user's
  // "Allow" and record what the app asks the service worker to show.
  await page.addInitScript(() => {
    window.__notifications = [];
    Object.defineProperty(Notification, 'permission', { get: () => 'granted' });
    ServiceWorkerRegistration.prototype.showNotification = function (title, opts) {
      window.__notifications.push({ title, body: opts && opts.body });
      return Promise.resolve();
    };
  });

  const onlyView = async (name) => page.evaluate((v) => [...document.querySelectorAll('.view')]
    .every((el) => (getComputedStyle(el).display !== 'none') === (el.dataset.view === v)), name);
  const noHScroll = async () => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);

  // TODAY
  await page.goto(BASE + '/');
  await page.waitForSelector('#current-card .temp-big');
  check(`[${label}] current conditions render`, (await page.textContent('#current-card .temp-big')).includes('25°'));
  check(`[${label}] 24 hourly items`, (await page.locator('#hourly .hour').count()) === 24);
  check(`[${label}] 7-day forecast`, (await page.locator('#daily .day').count()) === 7);
  check(`[${label}] heads-up mentions thunderstorm`, (await page.textContent('#highlights')).includes('Thunderstorm'));
  check(`[${label}] only the Today view is visible`, await onlyView('today'));
  check(`[${label}] no horizontal scroll (today)`, await noHScroll());
  await page.screenshot({ path: join(OUT, `${label}-today.png`), fullPage: true });

  // THEME TOGGLE: starts from the system setting, flips, survives a reload
  const bodyBg = () => page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  const startTheme = await page.getAttribute('#theme-btn', 'data-theme');
  check(`[${label}] theme follows system (${colorScheme})`, startTheme === colorScheme, startTheme);
  const bgBefore = await bodyBg();
  await page.click('#theme-btn');
  const flipped = await page.evaluate(() => document.documentElement.dataset.theme);
  check(`[${label}] toggle switches theme`, flipped === (colorScheme === 'dark' ? 'light' : 'dark'), flipped);
  await page.waitForTimeout(400); // let the 0.25 s colour transition finish
  check(`[${label}] colours change with theme`, (await bodyBg()) !== bgBefore);
  check(`[${label}] toggle label updates`, (await page.getAttribute('#theme-btn', 'aria-label')).includes(flipped === 'dark' ? 'light' : 'dark'));
  await page.reload();
  await page.waitForSelector('#current-card .temp-big');
  check(`[${label}] chosen theme persists after reload`, (await page.evaluate(() => document.documentElement.dataset.theme)) === flipped);
  await page.screenshot({ path: join(OUT, `${label}-today-${flipped}.png`) });

  // SEARCH
  await page.fill('#search-input', 'Deh');
  await page.waitForSelector('#search-results li[data-i]');
  await page.click('#search-results li[data-i="2"]'); // Dubai
  await page.waitForFunction(() => document.querySelector('#current-card h1')?.textContent.includes('Dubai'));
  check(`[${label}] search switches location`, true);

  // HISTORY
  await page.click('.tab[data-tab="history"]');
  await page.waitForSelector('#history-chart svg');
  check(`[${label}] history chart`, (await page.locator('#history-chart circle').count()) === 60);
  check(`[${label}] history stats`, (await page.locator('#history-stats .stat').count()) === 5);
  check(`[${label}] history table rows`, (await page.locator('#history-table tbody tr').count()) === 30);
  check(`[${label}] only the History view is visible`, await onlyView('history'));
  check(`[${label}] no horizontal scroll (history)`, await noHScroll());
  await page.screenshot({ path: join(OUT, `${label}-history.png`), fullPage: true });

  // MAP
  await page.click('.tab[data-tab="map"]');
  await page.waitForSelector('#map .temp-marker');
  check(`[${label}] map temperature layer`, (await page.locator('#map .temp-marker').count()) === 16);
  const spread = await page.evaluate(() => new Set([...document.querySelectorAll('#map .temp-marker')]
    .map((m) => { const r = m.getBoundingClientRect(); return `${Math.round(r.left / 20)}:${Math.round(r.top / 20)}`; })).size);
  check(`[${label}] map markers are spread out`, spread >= 12, `${spread} distinct positions`);
  await page.waitForSelector('#map canvas.maplibregl-canvas, #map .leaflet-tile-pane img', { timeout: 8000 });
  const base = await page.evaluate(() => ({
    vector: !!document.querySelector('#map canvas.maplibregl-canvas'),
    tiles: document.querySelectorAll('#map .leaflet-tile-pane img').length,
  }));
  check(`[${label}] base map renders`, base.vector || base.tiles >= 6, JSON.stringify(base));
  check(`[${label}] radar frames loaded`, (await page.getAttribute('#radar-frame', 'max')) === '2');
  check(`[${label}] radar tiles requested`, (await page.locator('#map img[src*="tilecache.rainviewer.com"]').count()) > 0);
  await page.check('#layer-wind');
  check(`[${label}] wind layer toggles`, (await page.locator('#map .wind-marker').count()) === 16);
  check(`[${label}] only the Map view is visible`, await onlyView('map'));
  check(`[${label}] no horizontal scroll (map)`, await noHScroll());
  await page.screenshot({ path: join(OUT, `${label}-map.png`) });

  // ALERTS (logged out -> prompt)
  await page.click('.tab[data-tab="alerts"]');
  check(`[${label}] alerts ask to sign in`, await page.isVisible('#alerts-login'));

  // ACCOUNT -> sign in
  await page.click('.tab[data-tab="account"]');
  await page.fill('#login-form input[name="username"]', 'aman');
  await page.fill('#login-form input[name="password"]', 'demo123');
  await page.click('#login-form button[type="submit"]');
  await page.waitForSelector('#fav-row .chip');
  check(`[${label}] sign-in loads favourites`, (await page.locator('#fav-row .chip').count()) === 2);

  // ALERTS: live SSE push
  await page.click('.tab[data-tab="alerts"]');
  await page.waitForSelector('#stream-status[data-state="on"]', { timeout: 5000 });
  check(`[${label}] SSE stream connected`, true);
  const t0 = Date.now();
  await page.waitForSelector('.toast >> text=33.4', { timeout: 3000 });
  check(`[${label}] live alert toast within 3 s`, Date.now() - t0 <= 3000, `${Date.now() - t0} ms`);
  await page.waitForFunction(() => document.querySelector('#alert-list').textContent.includes('33.4'));
  check(`[${label}] live alert added to list`, true);
  check(`[${label}] unread badge`, Number(await page.textContent('#alert-badge')) >= 1);
  const notes = await page.evaluate(() => window.__notifications);
  check(`[${label}] system notification shown`, notes.some((n) => (n.body || '').includes('33.4')), JSON.stringify(notes));
  check(`[${label}] only the Alerts view is visible`, await onlyView('alerts'));
  check(`[${label}] no horizontal scroll (alerts)`, await noHScroll());
  await page.screenshot({ path: join(OUT, `${label}-alerts.png`), fullPage: true });

  // PWA installability basics
  const manifest = await page.evaluate(async () => (await fetch('/manifest.json')).json());
  const sizes = manifest.icons.map((i) => i.sizes);
  check(`[${label}] manifest valid`, manifest.name && manifest.start_url && manifest.display === 'standalone'
    && sizes.includes('192x192') && sizes.includes('512x512'));
  const iconsOk = await page.evaluate(async (list) => (await Promise.all(list.map((i) => fetch(i.src)))).every((r) => r.ok
    && r.headers.get('content-type').includes('image/png')), manifest.icons);
  check(`[${label}] manifest icons load`, iconsOk);
  const sw = await page.evaluate(async () => (await navigator.serviceWorker.ready).active?.state);
  check(`[${label}] service worker active`, sw === 'activated' || sw === 'activating', sw);

  check(`[${label}] no console errors`, errors.length === 0, errors.join(' | '));
  await browser.close();
}

await mkdir(OUT, { recursive: true });
const server = await start(PORT);
try {
  await run({ width: 390, height: 844 }, 'mobile-390', true, 'light');
  await run({ width: 1280, height: 800 }, 'desktop-1280', false, 'dark');
} finally {
  server.close();
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
process.exit(failed.length ? 1 : 0);
