import { request } from '../api.js?v=2.4';
import { formatLocalDate, showToast, el, getTodayLocalIso } from '../ui.js?v=2.4';

let currentPeriodType = 'WEEK'; // 'WEEK' or 'MONTH'
let summariesList = [];
let offset = 0;
const LIMIT = 10;
let hasMore = false;
let isGenerating = false;

export async function initSummariesView() {
  bindControls();
  await loadSummaries(true);
}

export async function refreshSummaries() {
  await loadSummaries(true);
}

function bindControls() {
  const weekToggle = document.getElementById('summary-toggle-week');
  const monthToggle = document.getElementById('summary-toggle-month');
  const yearToggle = document.getElementById('summary-toggle-year');
  const generateForm = document.getElementById('summary-generate-form');
  const dateInput = document.getElementById('summary-target-date');
  const submitBtn = document.getElementById('summary-generate-submit');
  const dateLabel = document.getElementById('summary-date-label');

  if (dateInput && !dateInput.value) {
    dateInput.value = getTodayLocalIso();
  }

  const updateLabels = (type) => {
    if (submitBtn) {
      const typeLabel = type === 'WEEK' ? 'Weekly' : (type === 'MONTH' ? 'Monthly' : 'Yearly');
      submitBtn.textContent = `Generate ${typeLabel} Summary`;
    }
    if (dateLabel) {
      dateLabel.textContent = `DATE IN TARGET ${type === 'WEEK' ? 'WEEK' : (type === 'MONTH' ? 'MONTH' : 'YEAR')}`;
    }
  };

  const setActiveToggle = (type) => {
    currentPeriodType = type;
    const toggles = [
      { el: weekToggle, type: 'WEEK' },
      { el: monthToggle, type: 'MONTH' },
      { el: yearToggle, type: 'YEAR' }
    ];
    for (const t of toggles) {
      if (t.el) {
        const isActive = t.type === type;
        t.el.classList.toggle('is-active', isActive);
        t.el.classList.toggle('is-selected', isActive);
        t.el.setAttribute('aria-selected', isActive ? 'true' : 'false');
      }
    }
    updateLabels(type);
    loadSummaries(true);
  };

  if (weekToggle) weekToggle.addEventListener('click', () => { if (currentPeriodType !== 'WEEK') setActiveToggle('WEEK'); });
  if (monthToggle) monthToggle.addEventListener('click', () => { if (currentPeriodType !== 'MONTH') setActiveToggle('MONTH'); });
  if (yearToggle) yearToggle.addEventListener('click', () => { if (currentPeriodType !== 'YEAR') setActiveToggle('YEAR'); });

  updateLabels(currentPeriodType);

  if (generateForm) {
    generateForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const targetDate = dateInput.value;
      await generateNewSummary(currentPeriodType, targetDate);
    });
  }
}

async function loadSummaries(reset = false) {
  const container = document.getElementById('summaries-list');
  if (!container) return;

  if (reset) {
    offset = 0;
    container.replaceChildren();
    container.appendChild(el('p', { className: 'empty-state', textContent: 'Loading summaries…' }));
  }

  try {
    const data = await request(`/api/summaries?type=${currentPeriodType}&limit=${LIMIT}&offset=${offset}`);
    const items = Array.isArray(data) ? data : (data.items || []);

    if (reset) {
      summariesList = items;
    } else {
      summariesList.push(...items);
    }

    hasMore = items.length >= LIMIT;
    renderSummariesList();
  } catch (err) {
    container.replaceChildren();
    if (err.status === 404) {
      container.appendChild(el('div', { className: 'empty-state' }, [
        el('h3', { textContent: 'Summaries coming soon' }),
        el('p', { className: 'empty-sub', textContent: 'Weekly and monthly reflective summaries will appear here once entries accumulate.' })
      ]));
    } else {
      container.appendChild(el('p', {
        className: 'empty-state empty-error',
        textContent: `Could not load summaries: ${err.detail || err.message}`
      }));
    }
  }
}

function renderSummariesList() {
  const container = document.getElementById('summaries-list');
  if (!container) return;

  container.replaceChildren();

  if (summariesList.length === 0) {
    const periodLabel = currentPeriodType === 'WEEK' ? 'weekly' : (currentPeriodType === 'MONTH' ? 'monthly' : 'yearly');
    container.appendChild(el('div', { className: 'empty-state' }, [
      el('p', { textContent: `No ${periodLabel} summaries generated yet.` }),
      el('span', { className: 'empty-sub', textContent: 'Use the generator above to summarize a past week, month, or year of journaling.' })
    ]));
    return;
  }

  for (const summary of summariesList) {
    container.appendChild(renderSummaryCard(summary));
  }

  if (hasMore) {
    const loadMoreBtn = el('button', {
      type: 'button',
      className: 'button button-ghost summary-load-more',
      textContent: 'Load older summaries',
      onClick: async () => {
        offset += LIMIT;
        await loadSummaries(false);
      }
    });
    container.appendChild(loadMoreBtn);
  }
}

function renderSummaryCard(summary) {
  const card = el('article', { className: 'summary-card', id: `summary-card-${summary.id}` });

  // Header: Headline & Period
  const header = el('div', { className: 'summary-card-header' });
  const titleGroup = el('div', {}, [
    el('h3', { className: 'summary-headline', textContent: summary.headline || `${summary.periodType} Reflection` }),
    el('span', {
      className: 'summary-period-span',
      textContent: formatPeriod(summary.periodStart, summary.periodEnd)
    })
  ]);

  // Status Badges
  const badges = el('div', { className: 'summary-badges' });

  if (!summary.isFinal) {
    badges.appendChild(el('span', {
      className: 'badge badge-in-progress',
      title: 'This period is still ongoing.',
      textContent: 'In progress'
    }));
  }

  if (summary.source === 'STATS_ONLY') {
    badges.appendChild(el('span', {
      className: 'badge badge-stats-only',
      title: 'Generated without AI. Regenerate when the local model is running.',
      textContent: 'Stats only'
    }));
  }

  if (summary.stale) {
    badges.appendChild(el('span', {
      className: 'badge badge-stale',
      title: 'Entries changed since this was written.',
      textContent: 'Out of date'
    }));
  }

  header.append(titleGroup, badges);

  // Theme Chips
  let themeChips = null;
  if (Array.isArray(summary.themes) && summary.themes.length > 0) {
    themeChips = el('div', { className: 'summary-themes' });
    for (const theme of summary.themes) {
      themeChips.appendChild(el('span', { className: 'chip chip-theme', textContent: `#${theme}` }));
    }
  }

  // Stats Strip (the numbers come from here, never the narrative)
  let statsStrip = null;
  if (summary.stats && typeof summary.stats === 'object') {
    statsStrip = renderStatsStrip(summary.stats);
  }

  // Narrative Body (white-space: pre-wrap)
  const bodyText = el('div', { className: 'summary-body' });
  bodyText.appendChild(el('p', { className: 'summary-narrative', textContent: summary.body || '' }));

  // Action Buttons: Regenerate & View Entries
  const actions = el('div', { className: 'summary-card-actions' });

  const shouldShowRegen = summary.stale || summary.source === 'STATS_ONLY' || !summary.isFinal;
  if (shouldShowRegen) {
    const regenBtn = el('button', {
      type: 'button',
      className: 'button button-ghost-sm',
      innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg> <span>Regenerate</span>',
      onClick: async () => {
        await regenerateSummary(summary, regenBtn);
      }
    });
    actions.appendChild(regenBtn);
  }

  const entriesToggleBtn = el('button', {
    type: 'button',
    className: 'button button-ghost-sm',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg> <span>View entries</span>',
    onClick: async () => {
      await toggleSummaryEntries(card, summary.id, entriesToggleBtn);
    }
  });
  actions.appendChild(entriesToggleBtn);

  // Entries Container (accordion)
  const entriesAccordion = el('div', {
    className: 'summary-entries-accordion',
    id: `summary-entries-${summary.id}`,
    hidden: 'true'
  });

  card.append(header);
  if (themeChips) card.append(themeChips);
  if (statsStrip) card.append(statsStrip);
  card.append(bodyText, actions, entriesAccordion);

  return card;
}

function renderStatsStrip(stats) {
  const strip = el('div', { className: 'summary-stats-strip' });

  const addStat = (label, value) => {
    if (value === undefined || value === null) return;
    const item = el('div', { className: 'stat-item' }, [
      el('span', { className: 'stat-value', textContent: String(value) }),
      el('span', { className: 'stat-label', textContent: label })
    ]);
    strip.appendChild(item);
  };

  const daysLabel = stats.daysInPeriod ? `${stats.daysJournaled || 0}/${stats.daysInPeriod} days` : `${stats.daysJournaled || 0} days`;
  addStat('Journaled', daysLabel);
  addStat('Total Entries', stats.totalEntries ?? stats.entryCount);
  if (stats.longestStreak !== undefined) addStat('Best Streak', `${stats.longestStreak}d`);
  if (stats.goodMoodDays !== undefined || stats.goodMoodCount !== undefined) {
    addStat('Good Mood', stats.goodMoodDays ?? stats.goodMoodCount);
  }
  if (stats.highEnergyDays !== undefined || stats.highEnergyCount !== undefined) {
    addStat('High Energy', stats.highEnergyDays ?? stats.highEnergyCount);
  }
  if (stats.totalWords !== undefined || stats.wordCount !== undefined) {
    addStat('Words', stats.totalWords ?? stats.wordCount);
  }

  return strip;
}

async function regenerateSummary(summary, buttonEl) {
  buttonEl.disabled = true;
  buttonEl.textContent = 'Generating… (up to 60s)';

  try {
    const updated = await request(`/api/summaries/generate?type=${summary.periodType}&date=${summary.periodStart}&force=true`, {
      method: 'POST'
    });
    const idx = summariesList.findIndex(s => s.id === summary.id);
    if (idx !== -1) summariesList[idx] = updated;
    renderSummariesList();
    showToast('Summary regenerated successfully!', 'success');
  } catch (err) {
    showToast(`Could not regenerate: ${err.detail || err.message}`, 'error');
  } finally {
    buttonEl.disabled = false;
    buttonEl.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg> <span>Regenerate</span>';
  }
}

async function toggleSummaryEntries(card, summaryId, toggleBtn) {
  const accordion = card.querySelector(`#summary-entries-${summaryId}`);
  if (!accordion) return;

  const isExpanded = !accordion.hidden;
  if (isExpanded) {
    accordion.hidden = true;
    toggleBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg> <span>View entries</span>';
    return;
  }

  accordion.hidden = false;
  toggleBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="18 15 12 9 6 15"/></svg> <span>Hide entries</span>';

  // If already loaded, return
  if (accordion.children.length > 0) return;

  accordion.appendChild(el('p', { className: 'empty-sub', textContent: 'Loading entries for this period…' }));

  try {
    const entries = await request(`/api/summaries/${summaryId}/entries`);
    accordion.replaceChildren();

    if (!Array.isArray(entries) || entries.length === 0) {
      accordion.appendChild(el('p', { className: 'empty-sub', textContent: 'No individual entries found for this period.' }));
      return;
    }

    const list = el('div', { className: 'summary-entries-list' });
    for (const e of entries) {
      const item = el('div', { className: 'summary-entry-item' }, [
        el('div', { className: 'summary-entry-meta' }, [
          el('span', { className: 'entry-date-badge', textContent: formatLocalDate(e.entryDate, 'medium') }),
          el('span', { className: 'badge', textContent: e.entryType === 'DAILY_PROMPT' ? 'Reflection' : 'Freeform' }),
          el('span', { className: 'badge', textContent: `${e.energy || 'HIGH'} Energy` }),
          el('span', { className: 'badge', textContent: `${e.mood || 'GOOD'} Mood` })
        ]),
        el('p', { className: 'summary-entry-body', textContent: e.body })
      ]);
      list.appendChild(item);
    }
    accordion.appendChild(list);
  } catch (err) {
    accordion.replaceChildren();
    accordion.appendChild(el('p', { className: 'empty-sub', textContent: `Could not load period entries: ${err.detail || err.message}` }));
  }
}

async function generateNewSummary(type, dateStr) {
  if (isGenerating) return;
  const submitBtn = document.getElementById('summary-generate-submit');
  const statusEl = document.getElementById('summary-generate-status');

  isGenerating = true;
  if (submitBtn) {
    submitBtn.disabled = true;
    submitBtn.textContent = 'Generating…';
  }
  if (statusEl) {
    statusEl.className = 'status status-saving';
    statusEl.textContent = 'Analyzing journal entries & generating reflection…';
  }

  try {
    const summary = await request(`/api/summaries/generate?type=${type}&date=${dateStr}`, {
      method: 'POST'
    });
    summariesList.unshift(summary);
    renderSummariesList();
    if (statusEl) {
      statusEl.className = 'status status-success';
      statusEl.textContent = 'Summary generated!';
      setTimeout(() => { if (statusEl.textContent === 'Summary generated!') statusEl.textContent = ''; }, 4000);
    }
    showToast('New summary generated!', 'success');
  } catch (err) {
    if (statusEl) {
      statusEl.className = 'status status-error';
      if (err.status === 404) {
        statusEl.textContent = 'No entries found in that period to summarize.';
      } else if (err.status === 400) {
        statusEl.textContent = 'Cannot generate a summary for a future period.';
      } else {
        statusEl.textContent = err.detail || err.message;
      }
    }
    showToast(err.detail || err.message, err.status === 404 ? 'info' : 'error');
  } finally {
    isGenerating = false;
    if (submitBtn) {
      submitBtn.disabled = false;
      const typeLabel = type === 'WEEK' ? 'Weekly' : (type === 'MONTH' ? 'Monthly' : 'Yearly');
      submitBtn.textContent = `Generate ${typeLabel} Summary`;
    }
  }
}

function formatPeriod(startStr, endStr) {
  if (!startStr) return '';
  if (!endStr || startStr === endStr) return formatLocalDate(startStr, 'medium');

  const startFormatted = formatLocalDate(startStr, 'medium');
  const endFormatted = formatLocalDate(endStr, 'medium');
  return `${startFormatted} – ${endFormatted}`;
}
