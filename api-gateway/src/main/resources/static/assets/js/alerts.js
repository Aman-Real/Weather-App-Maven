// Real-time alerts: Server-Sent Events from alert-service (through the gateway) + system notifications.
import { api } from './api.js';
import { $, esc, fmt, toast } from './ui.js';

const METRIC_LABEL = {
  TEMPERATURE_ABOVE: (t) => `Temperature above ${t} °C`,
  TEMPERATURE_BELOW: (t) => `Temperature below ${t} °C`,
  WIND_ABOVE: (t) => `Wind above ${t} km/h`,
  PRECIPITATION_ABOVE: (t) => `Rain above ${t} mm`,
};

let source = null;
let alerts = [];
let retry = 0;

export function connectStream() {
  disconnectStream();
  const url = api.streamUrl();
  if (!url || !window.EventSource) return;
  setStatus('connecting', 'Connecting…');
  source = new EventSource(url);
  source.addEventListener('connected', () => { retry = 0; setStatus('on', 'Live'); });
  source.addEventListener('alert', (e) => {
    const alert = JSON.parse(e.data);
    alerts = [alert, ...alerts.filter((a) => a.id !== alert.id)];
    renderAlerts();
    toast(alert.severity === 'SEVERE' ? 'Severe weather alert' : 'Weather alert', alert.message, alert.severity, 8000);
    notify(alert);
  });
  source.onerror = () => {
    setStatus('off', 'Reconnecting…');
    if (source && source.readyState === EventSource.CLOSED) {
      // the browser gave up (e.g. 401): back off and try again with the current token
      disconnectStream();
      if (retry < 5) setTimeout(connectStream, Math.min(30000, 2000 * 2 ** retry++));
    }
  };
}

export function disconnectStream() {
  if (source) source.close();
  source = null;
  setStatus('off', 'Offline');
}

function setStatus(state, text) {
  const el = $('#stream-status');
  if (!el) return;
  el.dataset.state = state;
  el.textContent = text;
}

async function notify(alert) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  const options = { body: alert.message, icon: '/icons/icon-192.png', badge: '/icons/icon-192.png', tag: `alert-${alert.id}` };
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) await reg.showNotification('SkyCast alert', options);
    else new Notification('SkyCast alert', options);
  } catch { /* notifications are best-effort */ }
}

export async function loadAlertsView(currentLocation) {
  $('#rule-place').value = currentLocation ? currentLocation.name : '';
  try {
    const [list, rules] = await Promise.all([api.alerts(), api.rules()]);
    alerts = list;
    renderAlerts();
    renderRules(rules);
  } catch (e) {
    toast('Could not load alerts', e.message, 'error');
  }
}

export function renderAlerts() {
  const unread = alerts.filter((a) => !a.acknowledged).length;
  const badge = $('#alert-badge');
  badge.hidden = unread === 0;
  badge.textContent = String(unread);
  const ul = $('#alert-list');
  if (!ul) return;
  ul.innerHTML = alerts.length
    ? alerts.map((a) => `<li class="alert-item ${esc(a.severity)} ${a.acknowledged ? 'read' : ''}">
        <div><p>${esc(a.message)}</p><small>${esc(a.locationName)} · ${esc(fmt.dateTime(a.createdAt))} · ${esc(a.severity)}</small></div>
        ${a.acknowledged ? '' : `<button class="btn small ghost" data-ack="${a.id}">Mark read</button>`}
      </li>`).join('')
    : '<li class="empty">No alerts yet. Add a rule below — we check every few minutes.</li>';
}

function renderRules(rules) {
  const ul = $('#rule-list');
  ul.innerHTML = rules.length
    ? rules.map((r) => `<li class="rule-item ${r.active ? '' : 'off'}">
        <span><b>${esc(r.locationName)}</b> · ${esc(METRIC_LABEL[r.metric](r.threshold))}</span>
        <span class="row gap">
          <button class="btn small ghost" data-toggle="${r.id}" data-active="${!r.active}">${r.active ? 'Pause' : 'Resume'}</button>
          <button class="btn small ghost" data-delete="${r.id}" aria-label="Delete rule">Delete</button>
        </span></li>`).join('')
    : '<li class="empty">No rules yet.</li>';
}

export function bindAlertsUI(getLocation) {
  $('#alert-list').addEventListener('click', async (e) => {
    const id = e.target.dataset.ack;
    if (!id) return;
    await api.ack(id);
    alerts = alerts.map((a) => (String(a.id) === id ? { ...a, acknowledged: true } : a));
    renderAlerts();
  });
  $('#ack-all-btn').addEventListener('click', async () => {
    await api.ackAll();
    alerts = alerts.map((a) => ({ ...a, acknowledged: true }));
    renderAlerts();
  });
  $('#test-alert-btn').addEventListener('click', async () => {
    try { await api.testAlert(); } catch (e) { toast('Test alert failed', e.message, 'error'); }
  });
  $('#notify-btn').addEventListener('click', async () => {
    if (!('Notification' in window)) { toast('Notifications are not supported on this browser'); return; }
    const result = await Notification.requestPermission();
    toast(result === 'granted' ? 'Notifications enabled' : 'Notifications blocked', result === 'granted' ? 'You will be notified about new alerts.' : 'You can change this in the browser settings.');
  });
  $('#rule-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const loc = getLocation();
    try {
      await api.addRule({
        locationName: loc.name, latitude: loc.latitude, longitude: loc.longitude,
        metric: $('#rule-metric').value, threshold: Number($('#rule-threshold').value), active: true,
      });
      toast('Rule added', `${loc.name}: ${METRIC_LABEL[$('#rule-metric').value]($('#rule-threshold').value)}`);
      renderRules(await api.rules());
    } catch (err) { toast('Could not add rule', err.message, 'error'); }
  });
  $('#rule-list').addEventListener('click', async (e) => {
    const { toggle, delete: del, active } = e.target.dataset;
    try {
      if (toggle) await api.toggleRule(toggle, active);
      if (del) await api.deleteRule(del);
      if (toggle || del) renderRules(await api.rules());
    } catch (err) { toast('Update failed', err.message, 'error'); }
  });
}

/** Loads the user's alerts so the unread badge is right even before the Alerts tab is opened. */
export async function refreshAlerts() {
  try {
    alerts = await api.alerts();
    renderAlerts();
  } catch { /* not signed in or offline */ }
}

export function resetAlerts() {
  alerts = [];
  renderAlerts();
}
