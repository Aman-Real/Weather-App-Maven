// Thin fetch wrapper for the SkyCast gateway (same origin). Stores the JWT in localStorage.
const TOKEN_KEY = 'skycast.auth';

export function getAuth() {
  try {
    const auth = JSON.parse(localStorage.getItem(TOKEN_KEY) || 'null');
    if (auth && new Date(auth.expiresAt) > new Date()) return auth;
  } catch { /* storage unavailable */ }
  return null;
}

export function setAuth(auth) {
  try {
    if (auth) localStorage.setItem(TOKEN_KEY, JSON.stringify(auth));
    else localStorage.removeItem(TOKEN_KEY);
  } catch { /* storage unavailable */ }
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, { body, auth = false } = {}) {
  const headers = { Accept: 'application/json' };
  const session = getAuth();
  if (auth && session) headers.Authorization = `Bearer ${session.token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';

  let res;
  try {
    res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  } catch {
    throw new ApiError(0, 'You appear to be offline.');
  }
  if (res.status === 401 && auth) {
    setAuth(null);
    window.dispatchEvent(new CustomEvent('skycast:logout'));
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try { message = (await res.json()).message || message; } catch { /* not JSON */ }
    throw new ApiError(res.status, message);
  }
  return res.status === 204 ? null : res.json();
}

const q = (params) => new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null)).toString();

export const api = {
  current: (lat, lon, name) => request('GET', `/api/weather/current?${q({ lat, lon, name })}`),
  forecast: (lat, lon) => request('GET', `/api/weather/forecast?${q({ lat, lon })}`),
  history: (lat, lon, start, end, name) => request('GET', `/api/weather/history?${q({ lat, lon, start, end, name })}`),
  geocode: (text) => request('GET', `/api/weather/geocode?${q({ q: text })}`),
  grid: (b) => request('GET', `/api/weather/map?${q(b)}`),

  login: (username, password) => request('POST', '/api/auth/login', { body: { username, password } }),
  register: (username, email, password) => request('POST', '/api/auth/register', { body: { username, email, password } }),
  me: () => request('GET', '/api/users/me', { auth: true }),
  favorites: () => request('GET', '/api/users/me/favorites', { auth: true }),
  addFavorite: (fav) => request('POST', '/api/users/me/favorites', { body: fav, auth: true }),
  removeFavorite: (id) => request('DELETE', `/api/users/me/favorites/${id}`, { auth: true }),

  alerts: () => request('GET', '/api/alerts', { auth: true }),
  rules: () => request('GET', '/api/alerts/rules', { auth: true }),
  addRule: (rule) => request('POST', '/api/alerts/rules', { body: rule, auth: true }),
  toggleRule: (id, active) => request('PATCH', `/api/alerts/rules/${id}?active=${active}`, { auth: true }),
  deleteRule: (id) => request('DELETE', `/api/alerts/rules/${id}`, { auth: true }),
  ack: (id) => request('POST', `/api/alerts/${id}/ack`, { auth: true }),
  ackAll: () => request('POST', '/api/alerts/ack-all', { auth: true }),
  testAlert: () => request('POST', '/api/alerts/test', { auth: true }),
  streamUrl: () => {
    const session = getAuth();
    return session ? `/api/alerts/stream?access_token=${encodeURIComponent(session.token)}` : null;
  },
};
