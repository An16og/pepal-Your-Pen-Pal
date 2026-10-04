import { initTheme, toggleTheme, getSavedTheme, THEME_PAPER, THEME_NIGHT, THEME_AMOLED } from './theme.js?v=2.4';
import { store } from './state.js?v=2.4';
import { initWriteView, hasUnsavedDraft, updateLandingGreeting } from './views/write.js?v=2.4';
import { initHistoryView, refreshHistory } from './views/history.js?v=2.4';
import { initTrashView, refreshTrash } from './views/trash.js?v=2.4';
import { initSummariesView, refreshSummaries } from './views/summaries.js?v=2.4';
import { initChatView } from './views/chat.js?v=2.4';
import { initSettingsView } from './views/settings.js?v=2.4';

// Single source of truth for App Name and Title
export const APP_NAME = 'Pepal';
export const APP_SUBHEADING = 'Your Pen-pal';
export const APP_TITLE = 'Pepal — Your Pen-pal';

document.title = APP_TITLE;

export function getUserName() {
  try {
    return localStorage.getItem('pepal-user-name') || store.get().settings?.userName || '';
  } catch (e) {
    return '';
  }
}

// Live Clocks (Masthead and Writing Sheet Corner)
function updateClocks() {
  const now = new Date();
  const mastheadFormat = new Intl.DateTimeFormat(undefined, {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit'
  }).format(now);

  const cornerFormat = new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit'
  }).format(now);

  const mainClock = document.getElementById('current-time');
  const sheetClock = document.getElementById('sheet-corner-time');
  if (mainClock) mainClock.textContent = mastheadFormat;
  if (sheetClock) sheetClock.textContent = cornerFormat;
}

// Left Side Menu Drawer
function initLeftMenu() {
  const menu = document.getElementById('left-side-menu');
  const backdrop = document.getElementById('left-menu-backdrop');
  const openBtn = document.getElementById('left-menu-btn');
  const closeBtn = document.getElementById('left-menu-close');

  const toggle = (open) => {
    const isOpen = open !== undefined ? open : !menu.classList.contains('is-open');
    menu.classList.toggle('is-open', isOpen);
    backdrop.classList.toggle('is-active', isOpen);
    menu.setAttribute('aria-hidden', String(!isOpen));
  };

  if (openBtn) openBtn.addEventListener('click', () => toggle(true));
  if (closeBtn) closeBtn.addEventListener('click', () => toggle(false));
  if (backdrop) backdrop.addEventListener('click', () => toggle(false));

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && menu && menu.classList.contains('is-open')) {
      toggle(false);
    }
  });

  // Clicking any menu item closes the drawer
  document.querySelectorAll('.left-menu-item').forEach((item) => {
    item.addEventListener('click', () => {
      toggle(false);
    });
  });

  syncMenuUserPill();
}

function syncMenuUserPill() {
  const pill = document.getElementById('menu-user-pill');
  if (!pill) return;
  const name = getUserName();
  pill.innerHTML = `<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg> <span>${name || 'Friend'}</span>`;
}

// Hash Router (#/write, #/history, #/summaries, #/trash, #/settings, #/chat)
const VALID_ROUTES = ['write', 'history', 'summaries', 'trash', 'settings', 'chat'];

function switchRoute(routeName) {
  const target = VALID_ROUTES.includes(routeName) ? routeName : 'write';
  store.set({ activeTab: target });

  // Update nav tabs
  document.querySelectorAll('.tab').forEach((tab) => {
    const active = tab.dataset.panel === target;
    tab.classList.toggle('is-active', active);
    tab.setAttribute('aria-selected', String(active));
  });

  // Update left menu active state
  document.querySelectorAll('.left-menu-item').forEach((item) => {
    const href = item.getAttribute('href');
    const isCurrent = href === `#/${target}`;
    item.classList.toggle('is-active', isCurrent);
  });

  // Update panels
  document.querySelectorAll('.panel').forEach((panel) => {
    const active = panel.id === `panel-${target}`;
    panel.hidden = !active;
    panel.classList.toggle('is-active', active);
  });

  // View-specific refreshes
  if (target === 'write') {
    updateLandingGreeting();
  } else if (target === 'history') {
    refreshHistory();
  } else if (target === 'trash') {
    refreshTrash();
  } else if (target === 'summaries') {
    refreshSummaries();
  }
}

function handleHashChange() {
  const hash = window.location.hash.replace(/^#\/?/, '').trim();
  switchRoute(hash || 'write');
}

// Sync Theme Button Label
function syncThemeButton() {
  const btn = document.getElementById('theme-toggle');
  if (!btn) return;
  const currentTheme = document.documentElement.dataset.theme;
  if (currentTheme === THEME_NIGHT || currentTheme === THEME_AMOLED) {
    btn.innerHTML = `<svg class="ui-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> <span>Paper mode</span>`;
    btn.setAttribute('aria-label', 'Switch to paper theme');
  } else {
    btn.innerHTML = `<svg class="ui-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> <span>Night mode</span>`;
    btn.setAttribute('aria-label', 'Switch to night theme');
  }
}

// Bootstrap Application
function initApp() {
  initTheme();
  syncThemeButton();

  const themeToggleBtn = document.getElementById('theme-toggle');
  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      toggleTheme();
      syncThemeButton();
    });
  }

  window.addEventListener('pepal:theme-changed', syncThemeButton);
  window.addEventListener('pepal:user-updated', () => {
    syncMenuUserPill();
    updateLandingGreeting();
  });

  updateClocks();
  setInterval(updateClocks, 1000);

  initLeftMenu();

  // Initialize Views
  initWriteView();
  initHistoryView();
  initTrashView();
  initSummariesView();
  initChatView();
  initSettingsView();

  // Tab click bindings
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      const panel = tab.dataset.panel;
      window.location.hash = `#/${panel}`;
    });
  });

  window.addEventListener('hashchange', handleHashChange);
  handleHashChange();

  // Warn on tab close if there is an unsaved draft
  window.addEventListener('beforeunload', (e) => {
    if (hasUnsavedDraft()) {
      e.preventDefault();
      e.returnValue = '';
    }
  });
}

// Start app when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}
