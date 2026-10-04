import { request } from '../api.js?v=2.4';
import { store } from '../state.js?v=2.4';
import { formatLocalDate, formatSavedTime, showToast, el } from '../ui.js?v=2.4';

let allEntries = [];
let filteredEntries = [];
let displayLimit = 50;
const PAGE_SIZE = 50;

// Filter state
let searchQuery = '';
let filterMood = 'ALL';
let filterEnergy = 'ALL';
let filterType = 'ALL';
let filterDateFrom = '';
let filterDateTo = '';

export async function initHistoryView() {
  bindFilterEvents();
  await loadEntries();
}

export async function refreshHistory() {
  await loadEntries();
}

async function loadEntries() {
  const container = document.getElementById('history-list');
  if (!container) return;

  container.replaceChildren();
  const loading = el('p', { className: 'empty-state', textContent: 'Loading your journal archive…' });
  container.appendChild(loading);

  try {
    const data = await request('/api/entries');
    allEntries = Array.isArray(data) ? data : [];
    store.set({ entries: allEntries });
    applyFilters();
  } catch (err) {
    container.replaceChildren();
    container.appendChild(el('p', {
      className: 'empty-state empty-error',
      textContent: `Could not load entries: ${err.detail || err.message}`
    }));
  }
}

function bindFilterEvents() {
  const searchInput = document.getElementById('history-search');
  const moodSelect = document.getElementById('history-filter-mood');
  const energySelect = document.getElementById('history-filter-energy');
  const typeSelect = document.getElementById('history-filter-type');
  const dateFrom = document.getElementById('history-date-from');
  const dateTo = document.getElementById('history-date-to');
  const clearBtn = document.getElementById('history-filter-clear');

  if (searchInput) {
    searchInput.addEventListener('input', () => {
      searchQuery = searchInput.value.trim().toLowerCase();
      applyFilters();
    });
  }

  if (moodSelect) {
    moodSelect.addEventListener('change', () => {
      filterMood = moodSelect.value;
      applyFilters();
    });
  }

  if (energySelect) {
    energySelect.addEventListener('change', () => {
      filterEnergy = energySelect.value;
      applyFilters();
    });
  }

  if (typeSelect) {
    typeSelect.addEventListener('change', () => {
      filterType = typeSelect.value;
      applyFilters();
    });
  }

  if (dateFrom) {
    dateFrom.addEventListener('change', () => {
      filterDateFrom = dateFrom.value;
      applyFilters();
    });
  }

  if (dateTo) {
    dateTo.addEventListener('change', () => {
      filterDateTo = dateTo.value;
      applyFilters();
    });
  }

  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      if (searchInput) searchInput.value = '';
      if (moodSelect) moodSelect.value = 'ALL';
      if (energySelect) energySelect.value = 'ALL';
      if (typeSelect) typeSelect.value = 'ALL';
      if (dateFrom) dateFrom.value = '';
      if (dateTo) dateTo.value = '';

      searchQuery = '';
      filterMood = 'ALL';
      filterEnergy = 'ALL';
      filterType = 'ALL';
      filterDateFrom = '';
      filterDateTo = '';
      applyFilters();
    });
  }
}

function applyFilters() {
  filteredEntries = allEntries.filter((entry) => {
    // Text search
    if (searchQuery && !entry.body.toLowerCase().includes(searchQuery)) {
      return false;
    }
    // Mood
    if (filterMood !== 'ALL' && (entry.mood || 'GOOD').toUpperCase() !== filterMood) {
      return false;
    }
    // Energy
    if (filterEnergy !== 'ALL' && (entry.energy || 'HIGH').toUpperCase() !== filterEnergy) {
      return false;
    }
    // Type
    if (filterType !== 'ALL' && entry.entryType !== filterType) {
      return false;
    }
    // Date range
    if (filterDateFrom && entry.entryDate < filterDateFrom) {
      return false;
    }
    if (filterDateTo && entry.entryDate > filterDateTo) {
      return false;
    }
    return true;
  });

  displayLimit = PAGE_SIZE;
  renderHistoryList();
}

function renderHistoryList() {
  const container = document.getElementById('history-list');
  if (!container) return;

  container.replaceChildren();

  if (filteredEntries.length === 0) {
    const emptyMsg = allEntries.length === 0
      ? 'Your journal is empty. Write your first reflection page today!'
      : 'No entries match your search filters.';
    container.appendChild(el('p', { className: 'empty-state', textContent: emptyMsg }));
    return;
  }

  // Slice for pagination
  const visibleEntries = filteredEntries.slice(0, displayLimit);

  // Group by entryDate
  const byDate = new Map();
  for (const entry of visibleEntries) {
    const d = entry.entryDate;
    if (!byDate.has(d)) byDate.set(d, []);
    byDate.get(d).push(entry);
  }

  for (const [dateStr, entries] of byDate.entries()) {
    const group = el('div', { className: 'history-date-group' });
    const dateHeader = el('h3', {
      className: 'history-date-heading',
      textContent: formatLocalDate(dateStr, 'full')
    });
    group.appendChild(dateHeader);

    for (const entry of entries) {
      group.appendChild(renderEntryCard(entry));
    }
    container.appendChild(group);
  }

  // Load More button if more available
  if (displayLimit < filteredEntries.length) {
    const moreBtn = el('button', {
      type: 'button',
      className: 'button button-ghost history-load-more',
      textContent: `Load more (${filteredEntries.length - displayLimit} remaining)`,
      onClick: () => {
        displayLimit += PAGE_SIZE;
        renderHistoryList();
      }
    });
    container.appendChild(moreBtn);
  }
}

function renderEntryCard(entry) {
  const card = el('article', { className: 'history-entry', id: `entry-card-${entry.id}` });

  // Header
  const header = el('div', { className: 'entry-card-header' });
  const timeInfo = el('span', {
    className: 'entry-corner-time',
    textContent: `Saved: ${formatSavedTime(entry.createdAt)}`
  });

  // Check if edited
  const isEdited = entry.updatedAt && entry.createdAt &&
    new Date(entry.updatedAt).getTime() - new Date(entry.createdAt).getTime() > 1000;
  if (isEdited) {
    timeInfo.textContent += ` (edited ${formatSavedTime(entry.updatedAt)})`;
  }

  header.appendChild(timeInfo);

  // Badges & Action Buttons
  const bar = el('div', { className: 'entry-card-bar' });
  const badges = el('div', { className: 'entry-badges' });

  const typeBadge = el('span', {
    className: 'badge badge-type',
    textContent: entry.entryType === 'DAILY_PROMPT' ? 'Daily Reflection' : 'Freeform'
  });
  const energyBadge = el('span', {
    className: `badge badge-energy-${(entry.energy || 'HIGH').toLowerCase()}`,
    textContent: entry.energy === 'HIGH' ? 'High Energy' : 'Low Energy'
  });
  const moodBadge = el('span', {
    className: `badge badge-mood-${(entry.mood || 'GOOD').toLowerCase()}`,
    textContent: entry.mood === 'GOOD' ? 'Good Mood' : 'Difficult Mood'
  });

  badges.append(typeBadge, energyBadge, moodBadge);

  const actions = el('div', { className: 'entry-actions' });
  const editBtn = el('button', {
    type: 'button',
    className: 'button-icon',
    title: 'Edit entry',
    'aria-label': 'Edit entry',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg> <span>Edit</span>',
    onClick: () => startInlineEdit(card, entry)
  });
  const deleteBtn = el('button', {
    type: 'button',
    className: 'button-icon button-icon-danger',
    title: 'Move to trash',
    'aria-label': 'Move to trash',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg> <span>Delete</span>',
    onClick: () => deleteEntryWithUndo(entry)
  });

  actions.append(editBtn, deleteBtn);
  bar.append(badges, actions);

  // Body with Collapsible text (~6 lines)
  const bodyWrap = el('div', { className: 'entry-body-wrap' });
  const bodyText = el('p', { className: 'entry-text', textContent: entry.body });
  bodyWrap.appendChild(bodyText);

  // If long text (> 350 chars or multi-line), add show more toggle
  if (entry.body.length > 350 || entry.body.split('\n').length > 5) {
    bodyText.classList.add('is-collapsed');
    const toggleBtn = el('button', {
      type: 'button',
      className: 'entry-expand-toggle',
      textContent: 'Show more ▼',
      onClick: () => {
        const isCollapsed = bodyText.classList.toggle('is-collapsed');
        toggleBtn.textContent = isCollapsed ? 'Show more ▼' : 'Show less ▲';
      }
    });
    bodyWrap.appendChild(toggleBtn);
  }

  card.append(header, bar, bodyWrap);
  return card;
}

function startInlineEdit(card, entry) {
  card.classList.add('is-editing');
  card.replaceChildren();

  const editHeader = el('div', { className: 'edit-header' });
  const dateInfo = el('span', {
    className: 'edit-readonly-info',
    textContent: `Date: ${entry.entryDate} (${entry.entryType === 'DAILY_PROMPT' ? 'Daily Reflection' : 'Freeform'} - read-only)`
  });
  editHeader.appendChild(dateInfo);

  // Energy & Mood editors
  let editEnergy = entry.energy || 'HIGH';
  let editMood = entry.mood || 'GOOD';

  const metaRow = el('div', { className: 'edit-meta-row' });
  const energyLabel = el('label', { className: 'eyebrow', textContent: 'Energy' });
  const energyToggle = el('div', { className: 'toggle-group' }, [
    el('button', {
      type: 'button',
      className: `pill ${editEnergy === 'HIGH' ? 'is-selected' : ''}`,
      textContent: 'High',
      onClick: (e) => {
        energyToggle.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        editEnergy = 'HIGH';
      }
    }),
    el('button', {
      type: 'button',
      className: `pill ${editEnergy === 'LOW' ? 'is-selected' : ''}`,
      textContent: 'Low',
      onClick: (e) => {
        energyToggle.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        editEnergy = 'LOW';
      }
    })
  ]);

  const moodLabel = el('label', { className: 'eyebrow', textContent: 'Mood' });
  const moodToggle = el('div', { className: 'toggle-group' }, [
    el('button', {
      type: 'button',
      className: `pill ${editMood === 'GOOD' ? 'is-selected' : ''}`,
      textContent: 'Good',
      onClick: (e) => {
        moodToggle.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        editMood = 'GOOD';
      }
    }),
    el('button', {
      type: 'button',
      className: `pill ${editMood === 'BAD' ? 'is-selected' : ''}`,
      textContent: 'Difficult',
      onClick: (e) => {
        moodToggle.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        editMood = 'BAD';
      }
    })
  ]);

  metaRow.append(energyLabel, energyToggle, moodLabel, moodToggle);

  // Textarea
  const textarea = el('textarea', {
    className: 'edit-textarea',
    rows: '8',
    textContent: entry.body
  });

  const errorEl = el('div', { className: 'edit-error', textContent: '' });

  // Buttons
  const actionsRow = el('div', { className: 'edit-actions-row' });
  const cancelBtn = el('button', {
    type: 'button',
    className: 'button button-ghost',
    textContent: 'Cancel',
    onClick: () => {
      card.replaceWith(renderEntryCard(entry));
    }
  });

  const saveBtn = el('button', {
    type: 'button',
    className: 'button button-primary',
    textContent: 'Save changes',
    onClick: async () => {
      const newBody = textarea.value.trim();
      if (!newBody) {
        errorEl.textContent = 'Journal body cannot be empty.';
        return;
      }

      saveBtn.disabled = true;
      saveBtn.textContent = 'Re-embedding…';
      errorEl.textContent = '';

      try {
        const updated = await request(`/api/entries/${entry.id}`, {
          method: 'PUT',
          body: JSON.stringify({
            body: newBody,
            energy: editEnergy,
            mood: editMood
          })
        });

        // Update in allEntries
        const idx = allEntries.findIndex(e => e.id === entry.id);
        if (idx !== -1) allEntries[idx] = updated;

        showToast('Entry updated successfully!', 'success');
        card.replaceWith(renderEntryCard(updated));
      } catch (err) {
        // Keep editor open with user's edits preserved
        errorEl.textContent = `Update failed: ${err.detail || err.message}`;
        saveBtn.disabled = false;
        saveBtn.textContent = 'Save changes';
      }
    }
  });

  actionsRow.append(cancelBtn, saveBtn);
  card.append(editHeader, metaRow, textarea, errorEl, actionsRow);
  textarea.focus();
}

async function deleteEntryWithUndo(entry) {
  // Optimistically remove from list
  const prevEntries = [...allEntries];
  allEntries = allEntries.filter(e => e.id !== entry.id);
  applyFilters();

  try {
    await request(`/api/entries/${entry.id}`, { method: 'DELETE' });

    // Show 8-second undo toast
    showToast('Entry moved to trash.', 'info', 8000, {
      label: 'Undo',
      onClick: async () => {
        try {
          const restored = await request(`/api/entries/${entry.id}/restore`, { method: 'POST' });
          allEntries.unshift(restored);
          allEntries.sort((a, b) => (b.entryDate > a.entryDate ? 1 : b.entryDate < a.entryDate ? -1 : b.id - a.id));
          applyFilters();
          showToast('Entry restored to your journal!', 'success');
        } catch (restoreErr) {
          showToast(`Could not restore: ${restoreErr.detail || restoreErr.message}`, 'error');
        }
      }
    });
  } catch (err) {
    // Revert optimistic removal
    allEntries = prevEntries;
    applyFilters();
    showToast(`Failed to delete entry: ${err.detail || err.message}`, 'error');
  }
}
