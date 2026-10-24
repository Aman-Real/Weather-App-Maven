// SkyCast PWA entry point: tabs, search, today / history / account views.
import { api, getAuth, setAuth } from './api.js';
import { $, $$, esc, fmt, icon, kind, toast } from './ui.js';
import { historyChart } from './chart.js';
import { initMap, focus as focusMap, invalidate as invalidateMap } from './map.js';
import { bindThemeToggle } from './theme.js';
import { bindAlertsUI, connectStream, disconnectStream, loadAlertsView, refreshAlerts, resetAlerts } from './alerts.js';

const DEFAULT_LOCATION = { name: 'Dehradun', country: 'India', latitude: 30.32, longitude: 78.03 };
const LOCATION_KEY = 'skycast.location';

const state = {
  location: load(LOCATION_KEY) || DEFAULT_LOCATION,
  favorites: [],
  historyDays: 30,
  tab: 'today',
};

function load(key) {
  try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch { return null; }
}
function save(key, value) {
  try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* ignore */ }
}

/* ---------------- tabs ---------------- */
function showTab(tab) {
  state.tab = tab;
  $$('.tab').forEach((b) => (b.dataset.tab === tab ? b.setAttribute('aria-current', 'page') : b.removeAttribute('aria-current')));
  $$('.view').forEach((v) => { v.hidden = v.dataset.view !== tab; });
  if (tab === 'map') { initMap(state.location); invalidateMap(); }
  if (tab === 'history') loadHistory();
  if (tab === 'alerts') renderAlertsView();
  if (tab === 'account') renderAccount();
  const url = new URL(location.href);
  url.searchParams.set('tab', tab);
  history.replaceState(null, '', url);
}

/* ---------------- location + search ---------------- */
function setLocation(loc) {
  state.location = { name: loc.name, country: loc.country, latitude: Number(loc.latitude), longitude: Number(loc.longitude) };
  save(LOCATION_KEY, state.location);
  loadToday();
  focusMap(state.location);
  if (state.tab === 'history') loadHistory();
  if (state.tab === 'alerts') $('#rule-place').value = state.location.name;
}

function bindSearch() {
  const input = $('#search-input');
  const list = $('#search-results');
  let timer;
  let results = [];
  let active = -1;

  const close = () => { list.hidden = true; active = -1; };
  const pick = (i) => {
    const r = results[i];
    if (!r) return;
    input.value = '';
    close();
    setLocation(r);
  };
  const render = () => {
    list.innerHTML = results.length
      ? results.map((r, i) => `<li role="option" data-i="${i}" aria-selected="${i === active}">${esc(r.name)}<span class="muted small"> · ${esc([r.admin1, r.country].filter(Boolean).join(', '))}</span></li>`).join('')
      : '<li class="muted">No places found</li>';
    list.hidden = false;
  };

  input.addEventListener('input', () => {
    clearTimeout(timer);
    const text = input.value.trim();
    if (text.length < 2) { close(); return; }
    timer = setTimeout(async () => {
      try { results = await api.geocode(text); render(); } catch (e) { toast('Search failed', e.message, 'error'); }
    }, 300);
  });
  input.addEventListener('keydown', (e) => {
    if (list.hidden) return;
    if (e.key === 'ArrowDown') { active = Math.min(results.length - 1, active + 1); render(); e.preventDefault(); }
    if (e.key === 'ArrowUp') { active = Math.max(0, active - 1); render(); e.preventDefault(); }
    if (e.key === 'Escape') close();
  });
  $('#search-form').addEventListener('submit', (e) => { e.preventDefault(); pick(active >= 0 ? active : 0); });
  list.addEventListener('click', (e) => { const li = e.target.closest('li[data-i]'); if (li) pick(Number(li.dataset.i)); });
  document.addEventListener('click', (e) => { if (!e.target.closest('.search')) close(); });

  $('#locate-btn').addEventListener('click', () => {
    if (!navigator.geolocation) { toast('Location is not available on this device'); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => setLocation({ name: 'My location', country: '', latitude: p.coords.latitude.toFixed(2), longitude: p.coords.longitude.toFixed(2) }),
      () => toast('Could not get your location', 'Allow location access or search for a city.', 'error'),
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 600000 });
  });
}

/* ---------------- today ---------------- */
async function loadToday() {
  const { latitude, longitude, name } = state.location;
  $('#current-card').innerHTML = '<div class="skeleton">Loading current weather…</div>';
  const [current, forecast] = await Promise.allSettled([api.current(latitude, longitude, name), api.forecast(latitude, longitude)]);
  if (current.status === 'fulfilled') renderCurrent(current.value);
  else $('#current-card').innerHTML = `<div class="skeleton">${esc(current.reason.message)}</div>`;
  if (forecast.status === 'fulfilled') renderForecast(forecast.value);
  else {
    $('#hourly').innerHTML = `<p class="empty">${esc(forecast.reason.message)}</p>`;
    $('#daily').innerHTML = '';
    $('#highlights').innerHTML = '';
  }
}

function isFavorite() {
  const { latitude, longitude } = state.location;
  return state.favorites.find((f) => Math.abs(f.latitude - latitude) < 0.006 && Math.abs(f.longitude - longitude) < 0.006);
}

function renderCurrent(c) {
  const card = $('#current-card');
  const k = kind(c.weatherCode);
  card.className = `card hero ${!c.day ? 'night' : ''} ${k === 'rain' || k === 'snow' ? 'rain' : ''} ${k === 'storm' ? 'storm' : ''}`;
  const fav = isFavorite();
  card.innerHTML = `
    <div class="hero-top">
      <div>
        <h1>${esc(state.location.name || c.locationName)}</h1>
        <div class="place-sub muted">${esc(state.location.country || '')} · updated ${esc(fmt.hour(c.time))}</div>
      </div>
      <div class="row gap">
        <span class="pill ${c.source === 'CACHED' ? 'cached' : ''}" title="${c.source === 'CACHED' ? 'Provider unreachable - showing the last stored reading' : 'Fresh from Open-Meteo'}">${c.source === 'CACHED' ? 'Saved data' : 'Live'}</span>
        ${getAuth() ? `<button class="star" id="fav-btn" aria-pressed="${Boolean(fav)}">${fav ? '★ Saved' : '☆ Save'}</button>` : ''}
      </div>
    </div>
    <div class="hero-main">
      <div>
        <div class="temp-big">${fmt.temp(c.temperature)}</div>
        <div class="condition">${esc(c.condition)}</div>
        <div class="muted">Feels like ${fmt.temp(c.apparentTemperature)}</div>
      </div>
      <div class="hero-icon">${icon(c.weatherCode, c.day)}</div>
    </div>
    <div class="metrics">
      <div class="metric"><span>Humidity</span><b>${c.humidity}%</b></div>
      <div class="metric"><span>Wind</span><b>${fmt.one(c.windSpeed)} km/h</b></div>
      <div class="metric"><span>Precipitation</span><b>${fmt.one(c.precipitation)} mm</b></div>
      <div class="metric"><span>Coordinates</span><b>${c.latitude.toFixed(2)}, ${c.longitude.toFixed(2)}</b></div>
    </div>`;
  $('#fav-btn')?.addEventListener('click', toggleFavorite);
}

function renderForecast(f) {
  $('#hourly').innerHTML = f.hourly.slice(0, 24).map((h, i) => `
    <div class="hour">
      <span>${i === 0 ? 'Now' : esc(fmt.hourShort(h.time))}</span>
      ${icon(h.weatherCode, new Date(h.time).getHours() >= 6 && new Date(h.time).getHours() < 19)}
      <b>${fmt.temp(h.temperature)}</b>
      <span class="pop">${h.precipitationProbability >= 20 ? `${h.precipitationProbability}%` : ''}</span>
    </div>`).join('');

  const lo = Math.min(...f.daily.map((d) => d.tempMin));
  const hi = Math.max(...f.daily.map((d) => d.tempMax));
  const span = hi - lo || 1;
  $('#daily').innerHTML = f.daily.map((d, i) => `
    <li class="day">
      <span class="name">${i === 0 ? 'Today' : esc(fmt.day(d.date))}</span>
      ${icon(d.weatherCode)}
      <span><span class="cond">${esc(d.condition)}</span>${d.precipitationSum >= 0.5 ? `<br><span class="rainline">${fmt.one(d.precipitationSum)} mm</span>` : ''}</span>
      <span class="range"><span class="muted">${fmt.temp(d.tempMin)}</span>
        <span class="bar"><i style="left:${((d.tempMin - lo) / span) * 100}%;right:${100 - ((d.tempMax - lo) / span) * 100}%"></i></span>
        <b>${fmt.temp(d.tempMax)}</b></span>
    </li>`).join('');

  $('#highlights').innerHTML = highlights(f).map((h) => `<li class="${h.level}">${icon(h.code)}<span>${esc(h.text)}</span></li>`).join('');
}

/** Client-side "heads-up" from the forecast: sudden changes the user should plan around. */
function highlights(f) {
  const out = [];
  const storm = f.daily.find((d) => d.weatherCode >= 95);
  if (storm) out.push({ level: 'danger', code: 95, text: `Thunderstorm expected ${fmt.day(storm.date)} — plan indoor activities.` });
  const rainHour = f.hourly.slice(0, 24).find((h) => h.precipitationProbability >= 60);
  if (rainHour) out.push({ level: 'warn', code: 61, text: `Rain likely around ${fmt.hour(rainHour.time)} (${rainHour.precipitationProbability}%) — carry an umbrella.` });
  const windy = f.daily.find((d) => d.windSpeedMax >= 40);
  if (windy) out.push({ level: 'warn', code: 3, text: `Strong wind up to ${Math.round(windy.windSpeedMax)} km/h on ${fmt.day(windy.date)}.` });
  if (f.daily.length > 1) {
    const change = f.daily[1].tempMax - f.daily[0].tempMax;
    if (Math.abs(change) >= 4) out.push({ level: 'warn', code: change > 0 ? 0 : 3, text: `Tomorrow will be ${Math.abs(Math.round(change))}° ${change > 0 ? 'warmer' : 'cooler'} than today.` });
  }
  const hot = f.daily.find((d) => d.tempMax >= 38);
  if (hot) out.push({ level: 'danger', code: 0, text: `Heat: ${Math.round(hot.tempMax)}° on ${fmt.day(hot.date)} — stay hydrated.` });
  if (!out.length) out.push({ level: '', code: 1, text: 'No significant weather changes expected in the next days.' });
  return out;
}

/* ---------------- history ---------------- */
function setHistoryRange(days) {
  state.historyDays = days;
  const end = new Date(); end.setDate(end.getDate() - 1);
  const start = new Date(end); start.setDate(start.getDate() - (days - 1));
  $('#history-start').value = fmt.isoDate(start);
  $('#history-end').value = fmt.isoDate(end);
  $$('#history-form .chip').forEach((c) => c.classList.toggle('active', Number(c.dataset.days) === days));
}

async function loadHistory() {
  const { latitude, longitude, name } = state.location;
  $('#history-place').textContent = `· ${name}`;
  $('#history-chart').innerHTML = '<p class="empty">Loading…</p>';
  try {
    const h = await api.history(latitude, longitude, $('#history-start').value, $('#history-end').value, name);
    $('#history-source').textContent = h.source === 'DATABASE' ? 'From SkyCast database' : h.source === 'ARCHIVE' ? 'From Open-Meteo archive' : 'Partial data';
    const s = h.stats;
    $('#history-stats').innerHTML = [
      ['Avg high', `${fmt.one(s.averageMax)}°`], ['Avg low', `${fmt.one(s.averageMin)}°`],
      ['Total rain', `${fmt.one(s.totalPrecipitation)} mm`],
      ['Hottest day', s.hottestDay ? fmt.date(s.hottestDay) : '—'], ['Wettest day', s.wettestDay ? fmt.date(s.wettestDay) : '—'],
    ].map(([k, v]) => `<div class="stat"><span>${k}</span><b>${esc(v)}</b></div>`).join('');
    $('#history-chart').innerHTML = historyChart(h.days);
    $('#history-table tbody').innerHTML = h.days.map((d) => `<tr><td>${esc(fmt.date(d.date))}</td><td>${fmt.one(d.tempMax)}°</td><td>${fmt.one(d.tempMin)}°</td><td>${fmt.one(d.precipitationSum)} mm</td><td>${fmt.one(d.windSpeedMax)} km/h</td><td>${esc(d.condition)}</td></tr>`).join('');
  } catch (e) {
    $('#history-chart').innerHTML = `<p class="empty">${esc(e.message)}</p>`;
    $('#history-stats').innerHTML = '';
  }
}

function bindHistory() {
  $$('#history-form .chip').forEach((c) => c.addEventListener('click', () => { setHistoryRange(Number(c.dataset.days)); loadHistory(); }));
  $('#history-form').addEventListener('submit', (e) => {
    e.preventDefault();
    $$('#history-form .chip').forEach((c) => c.classList.remove('active'));
    loadHistory();
  });
}

/* ---------------- account + favourites ---------------- */
async function refreshFavorites() {
  if (!getAuth()) { state.favorites = []; renderFavRow(); return; }
  try { state.favorites = await api.favorites(); } catch { state.favorites = []; }
  renderFavRow();
}

function renderFavRow() {
  const row = $('#fav-row');
  row.hidden = state.favorites.length === 0;
  row.innerHTML = state.favorites.map((f) => `<button class="chip" data-fav="${f.id}">★ ${esc(f.name)}</button>`).join('');
  const list = $('#fav-list');
  list.innerHTML = state.favorites.length
    ? state.favorites.map((f) => `<li class="fav-item"><span>${esc(f.name)} <span class="muted small">${esc(f.country || '')}</span></span><button class="btn small ghost" data-remove-fav="${f.id}">Remove</button></li>`).join('')
    : '<li class="empty">Save places with ☆ on the Today screen.</li>';
}

async function toggleFavorite() {
  const fav = isFavorite();
  try {
    if (fav) await api.removeFavorite(fav.id);
    else await api.addFavorite({ name: state.location.name, country: state.location.country, latitude: state.location.latitude, longitude: state.location.longitude });
    await refreshFavorites();
    const btn = $('#fav-btn');
    const now = isFavorite();
    btn.setAttribute('aria-pressed', String(Boolean(now)));
    btn.textContent = now ? '★ Saved' : '☆ Save';
  } catch (e) { toast('Could not update favourites', e.message, 'error'); }
}

async function renderAccount() {
  const auth = getAuth();
  $('#auth-forms').hidden = Boolean(auth);
  $('#profile').hidden = !auth;
  if (!auth) return;
  $('#profile-name').textContent = `Hi, ${auth.username}`;
  try {
    const me = await api.me();
    $('#profile-meta').textContent = `${me.email} · roles: ${me.roles.join(', ')} · member since ${fmt.dateTime(me.createdAt)}`;
  } catch { /* token may have expired */ }
  renderFavRow();
}

function renderAlertsView() {
  const loggedIn = Boolean(getAuth());
  $('#alerts-login').hidden = loggedIn;
  $('#alerts-app').hidden = !loggedIn;
  if (loggedIn) loadAlertsView(state.location);
}

function onSignedIn(auth) {
  setAuth(auth);
  toast(`Welcome, ${auth.username}`, 'Live alerts are now connected.');
  refreshFavorites();
  connectStream();
  refreshAlerts();
  loadToday();
  showTab('today');
}

function bindAccount() {
  $('#login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try { onSignedIn(await api.login(f.get('username'), f.get('password'))); e.target.reset(); } catch (err) { toast('Sign-in failed', err.message, 'error'); }
  });
  $('#register-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const f = new FormData(e.target);
    try { onSignedIn(await api.register(f.get('username'), f.get('email'), f.get('password'))); e.target.reset(); } catch (err) { toast('Registration failed', err.message, 'error'); }
  });
  $('#logout-btn').addEventListener('click', () => {
    setAuth(null);
    signedOut();
    toast('Signed out');
  });
  $('#fav-row').addEventListener('click', (e) => {
    const id = e.target.dataset.fav;
    const f = state.favorites.find((x) => String(x.id) === id);
    if (f) setLocation(f);
  });
  $('#fav-list').addEventListener('click', async (e) => {
    const id = e.target.dataset.removeFav;
    if (!id) return;
    await api.removeFavorite(id);
    refreshFavorites();
  });
  window.addEventListener('skycast:logout', signedOut);
}

function signedOut() {
  disconnectStream();
  resetAlerts();
  state.favorites = [];
  renderFavRow();
  if (state.tab === 'account') renderAccount();
  if (state.tab === 'alerts') renderAlertsView();
  loadToday();
}

/* ---------------- PWA ---------------- */
function bindPwa() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('/sw.js').catch(() => {});
  }
  let deferred;
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferred = e;
    $('#install-btn').hidden = false;
  });
  $('#install-btn').addEventListener('click', async () => {
    if (!deferred) return;
    deferred.prompt();
    await deferred.userChoice;
    deferred = null;
    $('#install-btn').hidden = true;
  });
  window.addEventListener('online', () => { toast('Back online'); loadToday(); });
  window.addEventListener('offline', () => toast('You are offline', 'Showing saved data where available.', 'WARNING'));
}

/* ---------------- start ---------------- */
function start() {
  $$('.tab').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.tab)));
  $$('[data-goto]').forEach((b) => b.addEventListener('click', () => showTab(b.dataset.goto)));
  bindThemeToggle($('#theme-btn'));
  bindSearch();
  bindHistory();
  bindAccount();
  bindAlertsUI(() => state.location);
  bindPwa();
  setHistoryRange(state.historyDays);

  loadToday();
  if (getAuth()) {
    refreshFavorites();
    connectStream();
    refreshAlerts();
  }
  const tab = new URLSearchParams(location.search).get('tab');
  showTab(['today', 'map', 'history', 'alerts', 'account'].includes(tab) ? tab : 'today');
}

start();
