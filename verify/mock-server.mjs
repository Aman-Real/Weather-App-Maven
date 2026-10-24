// Serves the real PWA files plus a mock SkyCast API built from the SAME contract fixtures that
// weather-common's ContractFixturesTest checks against the backend DTOs.
// Usage: node verify/mock-server.mjs [port]
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const STATIC = join(ROOT, 'api-gateway/src/main/resources/static');
const CONTRACTS = join(ROOT, 'weather-common/src/test/resources/contracts');
const PORT = Number(process.argv[2] || process.env.PORT || 8099);
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml' };

const fixture = async (name) => JSON.parse(await readFile(join(CONTRACTS, name), 'utf8'));
const sseClients = new Set();
export const calls = [];

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(body === undefined ? '' : JSON.stringify(body));
}

async function readBody(req) {
  let data = '';
  for await (const chunk of req) data += chunk;
  return data ? JSON.parse(data) : {};
}

async function pushAlert() {
  const alert = await fixture('alert-event.json');
  for (const res of sseClients) res.write(`event:alert\nid:${alert.id}\ndata:${JSON.stringify(alert)}\n\n`);
}

async function api(req, res, url) {
  const p = url.pathname;
  const authed = (req.headers.authorization || '').startsWith('Bearer ') || url.searchParams.has('access_token');
  calls.push(`${req.method} ${p}`);

  if (p === '/api/weather/current') return send(res, 200, { ...(await fixture('current.json')), locationName: url.searchParams.get('name') || 'Dehradun' });
  if (p === '/api/weather/forecast') return send(res, 200, await fixture('forecast.json'));
  if (p === '/api/weather/history') return send(res, 200, await fixture('history.json'));
  if (p === '/api/weather/geocode') return send(res, 200, await fixture('geocode.json'));
  if (p === '/api/weather/map') return send(res, 200, await fixture('map.json'));

  if (p === '/api/auth/login') {
    const body = await readBody(req);
    return body.password === 'demo123' ? send(res, 200, await fixture('auth.json')) : send(res, 401, { status: 401, message: 'Bad credentials' });
  }
  if (p === '/api/auth/register') return send(res, 201, await fixture('auth.json'));
  if (!authed) return send(res, 401, { status: 401, message: 'Unauthorized' });

  if (p === '/api/users/me') return send(res, 200, { id: 2, username: 'aman', email: 'aman@example.com', roles: ['USER'], createdAt: '2026-10-01T09:00:00' });
  if (p === '/api/users/me/favorites') return req.method === 'GET' ? send(res, 200, await fixture('favorites.json')) : send(res, 201, { id: 9, ...(await readBody(req)) });
  if (p.startsWith('/api/users/me/favorites/')) return send(res, 204);
  if (p === '/api/alerts') return send(res, 200, await fixture('alerts.json'));
  if (p === '/api/alerts/rules') return req.method === 'GET' ? send(res, 200, await fixture('alert-rules.json')) : send(res, 201, { id: 10, ...(await readBody(req)) });
  if (p === '/api/alerts/test') { await pushAlert(); return send(res, 201, await fixture('alert-event.json')); }
  if (p === '/api/alerts/stream') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    res.write('event:connected\ndata:{"user":"aman"}\n\n');
    sseClients.add(res);
    req.on('close', () => sseClients.delete(res));
    setTimeout(pushAlert, 1500); // a new alert arrives 1.5 s after connecting
    return undefined;
  }
  if (/^\/api\/alerts\/(\d+\/ack|ack-all)$/.test(p) || p.startsWith('/api/alerts/rules/')) return send(res, 200, {});
  return send(res, 404, { status: 404, message: 'Not found' });
}

export function start(port = PORT) {
  const server = http.createServer(async (req, res) => {
    const url = new URL(req.url, `http://localhost:${port}`);
    try {
      if (url.pathname.startsWith('/api/')) return await api(req, res, url);
      const rel = url.pathname === '/' ? '/index.html' : url.pathname;
      const file = normalize(join(STATIC, rel));
      if (!file.startsWith(STATIC)) return send(res, 403, {});
      const data = await readFile(file);
      res.writeHead(200, { 'Content-Type': TYPES[extname(file)] || 'application/octet-stream' });
      return res.end(data);
    } catch {
      return send(res, 404, { status: 404, message: 'Not found' });
    }
  });
  return new Promise((ok) => server.listen(port, () => ok(server)));
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  start().then(() => console.log(`SkyCast mock API + PWA on http://localhost:${PORT}`));
}
