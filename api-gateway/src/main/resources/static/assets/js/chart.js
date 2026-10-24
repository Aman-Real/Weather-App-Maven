// Dependency-free SVG chart: max/min temperature lines over daily precipitation bars.
import { esc, fmt } from './ui.js';

export function historyChart(days) {
  if (!days.length) return '<p class="empty">No data for this range.</p>';
  const W = 720, H = 280, L = 36, R = 36, T = 34, B = 28;
  const iw = W - L - R, ih = H - T - B;
  const temps = days.flatMap((d) => [d.tempMax, d.tempMin]);
  const tMin = Math.floor(Math.min(...temps) - 1), tMax = Math.ceil(Math.max(...temps) + 1);
  const pMax = Math.max(5, ...days.map((d) => d.precipitationSum));
  const x = (i) => L + (days.length === 1 ? iw / 2 : (i * iw) / (days.length - 1));
  const yT = (t) => T + ih - ((t - tMin) / (tMax - tMin || 1)) * ih;
  const yP = (p) => T + ih - (p / pMax) * ih;
  const line = (key) => days.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${yT(d[key]).toFixed(1)}`).join('');
  const bw = Math.max(2, (iw / days.length) * 0.6);

  const grid = [0, 0.25, 0.5, 0.75, 1].map((f) => {
    const t = tMin + (tMax - tMin) * f, y = yT(t);
    return `<line class="axis" x1="${L}" x2="${W - R}" y1="${y}" y2="${y}"/><text x="${L - 6}" y="${y + 4}" text-anchor="end">${Math.round(t)}°</text>`;
  }).join('');
  const step = Math.ceil(days.length / 7);
  const labels = days.map((d, i) => (i % step === 0 ? `<text x="${x(i)}" y="${H - 8}" text-anchor="middle">${esc(fmt.date(d.date))}</text>` : '')).join('');
  const bars = days.map((d, i) => `<rect x="${(x(i) - bw / 2).toFixed(1)}" y="${yP(d.precipitationSum).toFixed(1)}" width="${bw.toFixed(1)}" height="${(T + ih - yP(d.precipitationSum)).toFixed(1)}" rx="2" fill="#0ea5e9" opacity=".35"><title>${esc(d.date)}: ${d.precipitationSum} mm</title></rect>`).join('');
  const dots = (key, color) => days.map((d, i) => `<circle cx="${x(i).toFixed(1)}" cy="${yT(d[key]).toFixed(1)}" r="2.6" fill="${color}"><title>${esc(d.date)}: ${d[key]}°C</title></circle>`).join('');

  return `<svg viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid meet">
    ${grid}${bars}
    <path d="${line('tempMax')}" fill="none" stroke="#f97316" stroke-width="2.4"/>
    <path d="${line('tempMin')}" fill="none" stroke="#3b82f6" stroke-width="2.4"/>
    ${dots('tempMax', '#f97316')}${dots('tempMin', '#3b82f6')}
    <text x="${W - R + 6}" y="${T + 10}">${Math.round(pMax)}mm</text>
    ${labels}
    <g transform="translate(${L},6)"><rect width="10" height="3" y="4" fill="#f97316"/><text x="14" y="9">Max</text><rect x="50" width="10" height="3" y="4" fill="#3b82f6"/><text x="64" y="9">Min</text><rect x="100" width="10" height="8" y="1" fill="#0ea5e9" opacity=".35"/><text x="114" y="9">Rain</text></g>
  </svg>`;
}
