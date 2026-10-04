export const THEME_PAPER = 'paper';
export const THEME_NIGHT = 'night';
export const THEME_AMOLED = 'amoled';

const STORAGE_KEY = 'pepal-theme';
const LEGACY_STORAGE_KEY = 'usher-theme';

/**
 * Safely retrieve theme preference from localStorage or prefers-color-scheme.
 */
export function getSavedTheme() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY) || localStorage.getItem(LEGACY_STORAGE_KEY);
    if (saved === 'amoled') return THEME_AMOLED;
    if (saved === 'night' || saved === 'dark') return THEME_NIGHT;
    if (saved === 'paper' || saved === 'light') return THEME_PAPER;
  } catch (e) {
    // localStorage unavailable or restricted
  }
  if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return THEME_NIGHT;
  }
  return THEME_PAPER;
}

/**
 * Apply theme to document element and meta tag.
 */
export function applyTheme(theme) {
  let normalized = THEME_PAPER;
  if (theme === THEME_AMOLED) {
    normalized = THEME_AMOLED;
  } else if (theme === THEME_NIGHT) {
    normalized = THEME_NIGHT;
  }
  
  document.documentElement.dataset.theme = normalized;
  document.body.dataset.theme = normalized;

  try {
    localStorage.setItem(STORAGE_KEY, normalized);
  } catch (e) {
    // localStorage unavailable
  }

  const metaColorScheme = document.querySelector('meta[name="color-scheme"]');
  if (metaColorScheme) {
    metaColorScheme.setAttribute('content', normalized === THEME_PAPER ? 'light' : 'dark');
  }

  window.dispatchEvent(new CustomEvent('pepal:theme-changed', { detail: { theme: normalized } }));
}

export function toggleTheme() {
  const current = document.documentElement.dataset.theme || getSavedTheme();
  let next = THEME_PAPER;
  if (current === THEME_PAPER) {
    next = THEME_NIGHT;
  } else {
    next = THEME_PAPER;
  }
  applyTheme(next);
  return next;
}

export function initTheme() {
  const theme = getSavedTheme();
  applyTheme(theme);
}
