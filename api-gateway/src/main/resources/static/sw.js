// SkyCast service worker: offline app shell + last-known weather.
const VERSION = 'skycast-v3';
const SHELL = [
  '/', '/index.html', '/manifest.json',
  '/assets/css/app.css',
  '/assets/js/app.js', '/assets/js/api.js', '/assets/js/theme.js', '/assets/js/ui.js', '/assets/js/chart.js', '/assets/js/map.js', '/assets/js/alerts.js',
  '/vendor/leaflet/leaflet.js', '/vendor/leaflet/leaflet.css',
  '/icons/icon.svg', '/icons/icon-192.png', '/icons/icon-512.png', '/icons/favicon-32.png',
];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(VERSION).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;

  // Public weather data: network first, fall back to the last cached answer when offline.
  if (url.pathname.startsWith('/api/weather/')) {
    event.respondWith(
      fetch(req)
        .then((res) => {
          if (res.ok) {
            const copy = res.clone();
            caches.open(VERSION).then((cache) => cache.put(req, copy));
          }
          return res;
        })
        .catch(() => caches.match(req).then((hit) => hit || Response.json(
          { status: 503, message: 'Offline and no saved data for this place yet.' }, { status: 503 }))));
    return;
  }
  // Personal API calls and the SSE stream are never cached.
  if (url.pathname.startsWith('/api/') || url.pathname.startsWith('/actuator/')) return;

  // App shell: network first so updates show up at once; cached copy when offline.
  event.respondWith(
    fetch(req)
      .then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(VERSION).then((cache) => cache.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req, { ignoreSearch: req.mode === 'navigate' }).then((hit) => hit
        || (req.mode === 'navigate' ? caches.match('/index.html') : Response.error()))));
});

// Clicking a system notification focuses (or opens) the app on the Alerts tab.
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      const open = windows.find((w) => new URL(w.url).origin === self.location.origin);
      if (open) { open.focus(); return open.navigate('/?tab=alerts'); }
      return self.clients.openWindow('/?tab=alerts');
    }));
});
