import { request } from '../api.js?v=2.4';
import { store } from '../state.js?v=2.4';
import { formatLocalDate, formatSavedTime, showToast, openModal, closeModal, el } from '../ui.js?v=2.4';

let trashEntries = [];

export async function initTrashView() {
  await loadTrash();

  const emptyTrashBtn = document.getElementById('empty-trash-btn');
  if (emptyTrashBtn) {
    emptyTrashBtn.addEventListener('click', () => {
      if (trashEntries.length === 0) return;
      confirmEmptyTrash();
    });
  }
}

export async function refreshTrash() {
  await loadTrash();
}

async function loadTrash() {
  const container = document.getElementById('trash-list');
  const emptyTrashBtn = document.getElementById('empty-trash-btn');
  if (!container) return;

  container.replaceChildren();
  const loading = el('p', { className: 'empty-state', textContent: 'Loading trash bin…' });
  container.appendChild(loading);

  try {
    const data = await request('/api/trash');
    trashEntries = Array.isArray(data) ? data : [];
    store.set({ trash: trashEntries });
    renderTrashList();
    if (emptyTrashBtn) {
      emptyTrashBtn.disabled = trashEntries.length === 0;
    }
  } catch (err) {
    container.replaceChildren();
    container.appendChild(el('p', {
      className: 'empty-state empty-error',
      textContent: `Could not load trash: ${err.detail || err.message}`
    }));
  }
}

function renderTrashList() {
  const container = document.getElementById('trash-list');
  const emptyTrashBtn = document.getElementById('empty-trash-btn');
  if (!container) return;

  container.replaceChildren();

  if (trashEntries.length === 0) {
    container.appendChild(el('div', { className: 'empty-state' }, [
      el('p', { textContent: 'Your trash is completely empty.' }),
      el('span', { className: 'empty-sub', textContent: 'Items moved to trash stay here for 30 days before being automatically purged.' })
    ]));
    if (emptyTrashBtn) emptyTrashBtn.disabled = true;
    return;
  }

  if (emptyTrashBtn) emptyTrashBtn.disabled = false;

  for (const entry of trashEntries) {
    container.appendChild(renderTrashCard(entry));
  }
}

function renderTrashCard(entry) {
  const card = el('article', { className: 'trash-entry', id: `trash-entry-${entry.id}` });

  const header = el('div', { className: 'entry-card-header' });
  const dateSpan = el('span', {
    className: 'entry-date-badge',
    textContent: formatLocalDate(entry.entryDate, 'medium')
  });

  const purgeCountdown = el('span', {
    className: 'badge badge-countdown',
    textContent: `${entry.daysRemaining ?? 30} days left before permanent deletion`
  });

  header.append(dateSpan, purgeCountdown);

  const metaRow = el('div', { className: 'entry-card-bar' });
  const badges = el('div', { className: 'entry-badges' });

  const typeBadge = el('span', {
    className: 'badge badge-type',
    textContent: entry.entryType === 'DAILY_PROMPT' ? 'Daily Reflection' : 'Freeform'
  });
  const deletedInfo = el('span', {
    className: 'entry-corner-time',
    textContent: `Deleted: ${formatSavedTime(entry.deletedAt)}`
  });
  badges.append(typeBadge, deletedInfo);

  const actions = el('div', { className: 'entry-actions' });
  const restoreBtn = el('button', {
    type: 'button',
    className: 'button button-ghost-sm',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg> <span>Restore</span>',
    onClick: () => handleRestore(entry)
  });
  const permanentBtn = el('button', {
    type: 'button',
    className: 'button button-ghost-sm button-danger',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg> <span>Delete forever</span>',
    onClick: () => confirmPermanentDelete(entry)
  });

  actions.append(restoreBtn, permanentBtn);
  metaRow.append(badges, actions);

  const bodyText = el('p', { className: 'entry-text trash-text', textContent: entry.body });

  card.append(header, metaRow, bodyText);
  return card;
}

async function handleRestore(entry) {
  try {
    await request(`/api/entries/${entry.id}/restore`, { method: 'POST' });
    trashEntries = trashEntries.filter(e => e.id !== entry.id);
    renderTrashList();
    showToast('Entry restored successfully!', 'success');
  } catch (err) {
    if (err.status === 409 && err.conflictingEntryId) {
      await showRestoreConflictModal(entry, err.conflictingEntryId);
    } else {
      showToast(err.detail || err.message, 'error');
    }
  }
}

async function showRestoreConflictModal(trashedEntry, conflictingId) {
  let conflictingEntry = null;
  try {
    conflictingEntry = await request(`/api/entries/${conflictingId}`);
  } catch (fetchErr) {
    // If we cannot fetch the conflicting entry, show basic conflict dialog
  }

  const content = el('div', { className: 'conflict-modal-content' });
  content.appendChild(el('p', {
    className: 'conflict-intro',
    textContent: `A Daily Reflection already exists for ${trashedEntry.entryDate}. Choose which entry you want to keep as active:`
  }));

  const sideBySide = el('div', { className: 'conflict-comparison' });

  // Column 1: Current Active Entry
  const currentCol = el('div', { className: 'conflict-column' });
  currentCol.appendChild(el('h4', { className: 'conflict-col-title', textContent: 'Current Active Entry' }));
  if (conflictingEntry) {
    currentCol.appendChild(el('div', { className: 'entry-badges' }, [
      el('span', { className: 'badge', textContent: `Mood: ${conflictingEntry.mood || 'GOOD'}` }),
      el('span', { className: 'badge', textContent: `Energy: ${conflictingEntry.energy || 'HIGH'}` })
    ]));
    currentCol.appendChild(el('p', { className: 'conflict-body', textContent: conflictingEntry.body }));
  } else {
    currentCol.appendChild(el('p', { className: 'empty-sub', textContent: `Entry ID: ${conflictingId}` }));
  }

  // Column 2: Trashed Entry to Restore
  const trashedCol = el('div', { className: 'conflict-column' });
  trashedCol.appendChild(el('h4', { className: 'conflict-col-title', textContent: 'Entry to Restore (from Trash)' }));
  trashedCol.appendChild(el('div', { className: 'entry-badges' }, [
    el('span', { className: 'badge', textContent: `Mood: ${trashedEntry.mood || 'GOOD'}` }),
    el('span', { className: 'badge', textContent: `Energy: ${trashedEntry.energy || 'HIGH'}` })
  ]));
  trashedCol.appendChild(el('p', { className: 'conflict-body', textContent: trashedEntry.body }));

  sideBySide.append(currentCol, trashedCol);
  content.appendChild(sideBySide);

  openModal({
    title: 'Daily Reflection Date Conflict',
    contentNode: content,
    actions: [
      {
        label: 'Keep current (Cancel restore)',
        className: 'button-ghost',
        onClick: () => {
          showToast('Restore cancelled. Current entry retained.', 'info');
          return true;
        }
      },
      {
        label: 'Keep restored (Move current to trash)',
        className: 'button-primary',
        onClick: async () => {
          try {
            // Step 1: soft-delete the current conflicting entry
            await request(`/api/entries/${conflictingId}`, { method: 'DELETE' });
            // Step 2: restore the trashed entry
            await request(`/api/entries/${trashedEntry.id}/restore`, { method: 'POST' });

            trashEntries = trashEntries.filter(e => e.id !== trashedEntry.id);
            await loadTrash();
            showToast('Replaced active entry with restored entry.', 'success');
            return true;
          } catch (replaceErr) {
            showToast(`Operation failed: ${replaceErr.detail || replaceErr.message}`, 'error');
            await loadTrash();
            return false;
          }
        }
      }
    ]
  });
}

function confirmPermanentDelete(entry) {
  const content = el('div', {}, [
    el('p', { textContent: `Are you sure you want to permanently delete this entry from ${entry.entryDate}?` }),
    el('p', { className: 'modal-warning', textContent: 'This action cannot be undone. The entry and its embeddings will be permanently deleted from the local database.' })
  ]);

  openModal({
    title: 'Permanently Delete Entry?',
    contentNode: content,
    actions: [
      { label: 'Cancel', className: 'button-ghost', onClick: () => true },
      {
        label: 'Delete Forever',
        className: 'button-danger',
        onClick: async () => {
          try {
            await request(`/api/entries/${entry.id}/permanent`, { method: 'DELETE' });
            trashEntries = trashEntries.filter(e => e.id !== entry.id);
            renderTrashList();
            showToast('Entry permanently deleted.', 'info');
            return true;
          } catch (err) {
            showToast(err.detail || err.message, 'error');
            return false;
          }
        }
      }
    ]
  });
}

function confirmEmptyTrash() {
  const content = el('div', {}, [
    el('p', { textContent: `Are you sure you want to empty the trash bin (${trashEntries.length} entries)?` }),
    el('p', { className: 'modal-warning', textContent: 'All trashed entries will be permanently purged immediately. This cannot be undone.' })
  ]);

  openModal({
    title: 'Empty Trash Bin?',
    contentNode: content,
    actions: [
      { label: 'Cancel', className: 'button-ghost', onClick: () => true },
      {
        label: 'Empty Trash',
        className: 'button-danger',
        onClick: async () => {
          try {
            await request('/api/trash', { method: 'DELETE' });
            trashEntries = [];
            renderTrashList();
            showToast('Trash bin emptied.', 'info');
            return true;
          } catch (err) {
            showToast(err.detail || err.message, 'error');
            return false;
          }
        }
      }
    ]
  });
}
