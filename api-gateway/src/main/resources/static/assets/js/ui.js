// Formatting helpers, weather icons (inline SVG) and toasts.
export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

export function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export const fmt = {
  temp: (v) => `${Math.round(v)}°`,
  one: (v) => (Math.round(v * 10) / 10).toFixed(1),
  hour: (iso) => new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
  hourShort: (iso) => new Date(iso).toLocaleTimeString([], { hour: 'numeric' }),
  day: (iso) => new Date(`${iso}T12:00:00`).toLocaleDateString([], { weekday: 'short' }),
  date: (iso) => new Date(`${iso}T12:00:00`).toLocaleDateString([], { day: 'numeric', month: 'short' }),
  dateTime: (iso) => new Date(iso).toLocaleString([], { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }),
  isoDate: (d) => {
    const z = new Date(d.getTime() - d.getTimezoneOffset() * 60000);
    return z.toISOString().slice(0, 10);
  },
};

/** WMO weather code -> icon group. */
export function kind(code) {
  if (code === 0 || code === 1) return 'clear';
  if (code === 2) return 'partly';
  if (code === 3) return 'cloud';
  if (code === 45 || code === 48) return 'fog';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow';
  if (code >= 95) return 'storm';
  if (code >= 51) return 'rain';
  return 'cloud';
}

const SUN = '<circle cx="12" cy="12" r="4.2" fill="#fbbf24" stroke="#f59e0b"/><g stroke="#f59e0b"><path d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/></g>';
const MOON = '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z" fill="#c7d2fe" stroke="#a5b4fc"/>';
const CLOUD = (y = 0) => `<path transform="translate(0 ${y})" d="M7 18h10a4 4 0 0 0 .4-8 5.5 5.5 0 0 0-10.6 1.2A3.4 3.4 0 0 0 7 18z" fill="#e2e8f0" stroke="#94a3b8"/>`;

export function icon(code, day = true) {
  const k = kind(code);
  let body;
  switch (k) {
    case 'clear': body = day ? SUN : MOON; break;
    case 'partly': body = `<g transform="translate(-3 -3) scale(.8)">${day ? SUN : MOON}</g>${CLOUD(1)}`; break;
    case 'fog': body = `${CLOUD(-2)}<g stroke="#94a3b8"><path d="M5 20h14M7 22.5h10"/></g>`; break;
    case 'rain': body = `${CLOUD(-2)}<g stroke="#0ea5e9"><path d="M8 19l-1 3M12 19l-1 3M16 19l-1 3"/></g>`; break;
    case 'snow': body = `${CLOUD(-2)}<g stroke="#7dd3fc"><path d="M8 20.5h.01M12 21.5h.01M16 20.5h.01"/></g>`; break;
    case 'storm': body = `${CLOUD(-2)}<path d="M12.5 16.5 10 20.5h3l-1.5 3" stroke="#facc15" stroke-width="2"/>`; break;
    default: body = CLOUD(0);
  }
  return `<svg viewBox="0 0 24 24" role="img" aria-label="${k}">${body}</svg>`;
}

/** Temperature -> colour (used by map markers and the legend). */
export function tempColor(t) {
  const stops = [[-10, '#6366f1'], [0, '#38bdf8'], [10, '#34d399'], [20, '#facc15'], [30, '#f97316'], [40, '#ef4444']];
  for (let i = stops.length - 1; i >= 0; i--) if (t >= stops[i][0]) return stops[i][1];
  return stops[0][1];
}

export function toast(title, message = '', type = 'info', ms = 5000) {
  const box = $('#toasts');
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.setAttribute('role', type === 'error' ? 'alert' : 'status');
  el.innerHTML = `<b>${esc(title)}</b>${message ? `<span>${esc(message)}</span>` : ''}`;
  box.prepend(el);
  setTimeout(() => el.remove(), ms);
}
