// Interactive weather map: OpenFreeMap vector base (OSM raster fallback), RainViewer radar animation, SkyCast temperature/wind grid.
import { api } from './api.js';
import { $, esc, fmt, tempColor } from './ui.js';

const RAINVIEWER = 'https://api.rainviewer.com/public/weather-maps.json';
let map, radarLayer, tempLayer, windLayer, frames = [], host = '', frameIndex = 0, timer = null, pending = null, centerMarker;

export function initMap(location) {
  if (map) { focus(location); return; }
  if (!window.L) { $('#map').innerHTML = '<p class="empty">Map library failed to load.</p>'; return; }
  map = L.map('map', { zoomControl: true, worldCopyJump: true }).setView([location.latitude, location.longitude], 7);
  addBaseMap();
  // Radar sits in its own pane above the base map (vector or raster) and below the markers.
  map.createPane('radar');
  map.getPane('radar').style.zIndex = '350';
  map.getPane('radar').style.pointerEvents = 'none';
  // Re-measure whenever the container changes size (it is created while the tab is being shown).
  if (window.ResizeObserver) new ResizeObserver(() => map.invalidateSize()).observe($('#map'));
  tempLayer = L.layerGroup().addTo(map);
  windLayer = L.layerGroup();
  centerMarker = L.circleMarker([location.latitude, location.longitude], { radius: 7, color: '#fff', weight: 2, fillColor: '#2563eb', fillOpacity: 1 }).addTo(map);

  map.on('moveend', () => { clearTimeout(pending); pending = setTimeout(loadGrid, 350); });
  $('#layer-radar').addEventListener('change', (e) => toggle(radarLayer, e.target.checked));
  $('#layer-temp').addEventListener('change', (e) => toggle(tempLayer, e.target.checked));
  $('#layer-wind').addEventListener('change', (e) => toggle(windLayer, e.target.checked));
  $('#radar-play').addEventListener('click', togglePlay);
  $('#radar-frame').addEventListener('input', (e) => showFrame(Number(e.target.value)));

  loadRadar();
  map.whenReady(() => setTimeout(() => { map.invalidateSize(); loadGrid(); }, 100));
}

export function focus(location) {
  if (!map) return;
  map.setView([location.latitude, location.longitude], Math.max(map.getZoom(), 6));
  centerMarker.setLatLng([location.latitude, location.longitude]);
}

export function invalidate() {
  if (map) setTimeout(() => map.invalidateSize(), 50);
}

/**
 * Base map: OpenFreeMap vector tiles (free, no API key, no registration) rendered by MapLibre GL
 * inside Leaflet. Falls back to OpenStreetMap raster tiles when WebGL2 is not available.
 */
async function addBaseMap() {
  const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const attribution = '<a href="https://openfreemap.org" target="_blank" rel="noopener">OpenFreeMap</a> '
    + '&copy; <a href="https://www.openmaptiles.org/" target="_blank" rel="noopener">OpenMapTiles</a> '
    + 'Data &copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a> contributors';
  try {
    if (!document.createElement('canvas').getContext('webgl2')) throw new Error('WebGL2 not available');
    const maplibregl = await import('/vendor/maplibre/maplibre-gl.js');
    maplibregl.setWorkerUrl(new URL('/vendor/maplibre/maplibre-gl-worker.js', location.href).href);
    window.maplibregl = maplibregl;
    await loadScript('/vendor/maplibre/leaflet-maplibre-gl.js');
    loadCss('/vendor/maplibre/maplibre-gl.css');
    const layer = window.MaplibreGLLeaflet.maplibreGL({
      style: `https://tiles.openfreemap.org/styles/${dark ? 'dark' : 'positron'}`,
      attribution,
    }).addTo(map);
    layer.getMaplibreMap().on('error', (e) => console.warn('Base map:', e.error?.message || e));
    map.attributionControl.addAttribution(attribution);
  } catch (e) {
    console.warn('Vector base map unavailable, using raster tiles:', e.message);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19, referrerPolicy: 'strict-origin-when-cross-origin',
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);
  }
}

function loadScript(src) {
  return new Promise((resolve, reject) => {
    const el = document.createElement('script');
    el.src = src;
    el.onload = resolve;
    el.onerror = () => reject(new Error(`failed to load ${src}`));
    document.head.appendChild(el);
  });
}

function loadCss(href) {
  if (document.querySelector(`link[href="${href}"]`)) return;
  const el = document.createElement('link');
  el.rel = 'stylesheet';
  el.href = href;
  document.head.appendChild(el);
}

function toggle(layer, on) {
  if (!layer) return;
  if (on) layer.addTo(map); else map.removeLayer(layer);
}

async function loadRadar() {
  try {
    const res = await fetch(RAINVIEWER);
    const data = await res.json();
    host = data.host;
    frames = [...(data.radar?.past || []), ...(data.radar?.nowcast || [])];
    if (!frames.length) throw new Error('no frames');
    const slider = $('#radar-frame');
    slider.max = String(frames.length - 1);
    showFrame(frames.length - 1);
  } catch {
    $('#radar-time').textContent = 'Radar unavailable';
    $('#radar-play').disabled = true;
  }
}

function radarUrl(frame) {
  return `${host}${frame.path}/256/{z}/{x}/{y}/2/1_1.png`;
}

function showFrame(i) {
  if (!frames.length) return;
  frameIndex = i;
  const next = L.tileLayer(radarUrl(frames[i]), { pane: 'radar', opacity: 0.7, maxNativeZoom: 7, maxZoom: 19 });
  const wasOn = $('#layer-radar').checked;
  if (radarLayer) map.removeLayer(radarLayer);
  radarLayer = next;
  if (wasOn) radarLayer.addTo(map);
  $('#radar-frame').value = String(i);
  $('#radar-time').textContent = new Date(frames[i].time * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function togglePlay() {
  const btn = $('#radar-play');
  if (timer) {
    clearInterval(timer); timer = null; btn.textContent = '▶ Play';
    return;
  }
  btn.textContent = '❚❚ Pause';
  timer = setInterval(() => showFrame((frameIndex + 1) % frames.length), 700);
}

async function loadGrid() {
  const b = map.getBounds();
  try {
    const points = await api.grid({
      north: b.getNorth().toFixed(2), south: b.getSouth().toFixed(2),
      east: b.getEast().toFixed(2), west: b.getWest().toFixed(2),
    });
    tempLayer.clearLayers();
    windLayer.clearLayers();
    for (const p of points) {
      const t = L.divIcon({
        className: '', iconSize: [44, 26],
        html: `<div class="temp-marker" style="background:${tempColor(p.temperature)}">${Math.round(p.temperature)}°</div>`,
      });
      L.marker([p.latitude, p.longitude], { icon: t, keyboard: false })
        .bindPopup(`<b>${fmt.one(p.temperature)} °C</b><br>Wind ${fmt.one(p.windSpeed)} km/h`)
        .addTo(tempLayer);
      const w = L.divIcon({
        className: '', iconSize: [20, 20],
        // wind_direction is where the wind comes FROM; the arrow points where it blows TO
        html: `<div class="wind-marker" style="transform:rotate(${(p.windDirection + 180) % 360}deg)" title="${esc(fmt.one(p.windSpeed))} km/h">↑</div>`,
      });
      L.marker([p.latitude - 0.18, p.longitude], { icon: w, keyboard: false }).addTo(windLayer);
    }
  } catch { /* map grid is best-effort */ }
}
