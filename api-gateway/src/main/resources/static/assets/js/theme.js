// Light / dark theme: follows the system until the user picks one with the toggle (saved per browser).
const KEY = 'skycast.theme';
const root = document.documentElement;
const systemDark = window.matchMedia('(prefers-color-scheme: dark)');

export function isDark() {
  return root.dataset.theme ? root.dataset.theme === 'dark' : systemDark.matches;
}

function render(button) {
  const dark = isDark();
  const label = dark ? 'Switch to light theme' : 'Switch to dark theme';
  button.dataset.theme = dark ? 'dark' : 'light';
  button.setAttribute('aria-label', label);
  button.title = label;
  button.setAttribute('aria-pressed', String(dark));
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#0b1220' : '#0e285c');
}

function announce() {
  window.dispatchEvent(new CustomEvent('skycast:theme', { detail: isDark() ? 'dark' : 'light' }));
}

export function bindThemeToggle(button) {
  render(button);
  button.addEventListener('click', () => {
    const next = isDark() ? 'light' : 'dark';
    root.classList.add('theme-anim');
    root.dataset.theme = next;
    try { localStorage.setItem(KEY, next); } catch { /* storage unavailable */ }
    render(button);
    announce();
  });
  // While the user has not chosen, keep following the operating-system setting.
  systemDark.addEventListener('change', () => {
    if (root.dataset.theme) return;
    render(button);
    announce();
  });
}
