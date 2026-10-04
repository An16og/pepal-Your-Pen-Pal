import { request } from '../api.js?v=2.4';
import { store } from '../state.js?v=2.4';
import { getTodayLocalIso, countWords, showToast, el } from '../ui.js?v=2.4';

let activeQuestions = [];
let currentPromptsData = null;
let currentMode = 'DAILY_PROMPT'; // 'DAILY_PROMPT' or 'FREEFORM'
let currentEnergy = 'HIGH';
let currentMood = 'GOOD';
let isSaving = false;
let isShuffling = false;

function getDraftKey(date, type) {
  return `pepal-draft-${date}-${type}`;
}

function saveDraft(date, type, text) {
  try {
    const key = getDraftKey(date, type);
    if (text && text.trim()) {
      localStorage.setItem(key, text);
    } else {
      localStorage.removeItem(key);
    }
  } catch (e) {
    // Ignore storage quota errors
  }
}

function loadDraft(date, type) {
  try {
    return localStorage.getItem(getDraftKey(date, type)) || '';
  } catch (e) {
    return '';
  }
}

function clearDraft(date, type) {
  try {
    localStorage.removeItem(getDraftKey(date, type));
  } catch (e) {
    // Ignore
  }
}

export function hasUnsavedDraft() {
  const textarea = document.getElementById('entry-body');
  return textarea && textarea.value.trim().length > 0;
}

export function initWriteView() {
  const dateInput = document.getElementById('entry-date');
  const textarea = document.getElementById('entry-body');
  const wordCountEl = document.getElementById('word-count');
  const shuffleBtn = document.getElementById('shuffle-prompts');
  const insertTemplateBtn = document.getElementById('insert-prompts-template');
  const entryForm = document.getElementById('entry-form');
  const clearBtn = document.getElementById('entry-clear');
  const statusEl = document.getElementById('entry-status');

  if (!dateInput || !textarea) return;

  // Set default date
  if (!dateInput.value) {
    dateInput.value = getTodayLocalIso();
  }

  // Load draft for initial date & mode
  const initialDraft = loadDraft(dateInput.value, currentMode);
  if (initialDraft && !textarea.value) {
    textarea.value = initialDraft;
  }
  updateWordCount();

  // Load prompts for current date
  loadPromptsForDate(dateInput.value);

  // Date change handler
  dateInput.addEventListener('change', () => {
    const newDate = dateInput.value;
    loadPromptsForDate(newDate);
    // Switch draft
    const draft = loadDraft(newDate, currentMode);
    textarea.value = draft;
    updateWordCount();
  });

  // Autosave draft on input
  textarea.addEventListener('input', () => {
    updateWordCount();
    saveDraft(dateInput.value, currentMode, textarea.value);
  });

  // Keyboard shortcut Ctrl+Enter / Cmd+Enter
  textarea.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      if (!isSaving && textarea.value.trim()) {
        entryForm.dispatchEvent(new Event('submit', { cancelable: true }));
      }
    }
  });

  // Landing page greeting
  updateLandingGreeting();
  window.addEventListener('pepal:user-updated', updateLandingGreeting);

  // Mode Selection (Daily Reflection vs Freeform)
  document.querySelectorAll('.mode-pill').forEach((pill) => {
    pill.addEventListener('click', () => {
      document.querySelectorAll('.mode-pill').forEach((p) => {
        p.classList.remove('is-active');
        p.setAttribute('aria-checked', 'false');
      });
      pill.classList.add('is-active');
      pill.setAttribute('aria-checked', 'true');
      currentMode = pill.dataset.mode;

      const isDaily = currentMode === 'DAILY_PROMPT';
      const promptContainer = document.getElementById('prompt-container');
      const eyebrow = document.getElementById('sheet-mode-eyebrow');
      const intro = document.getElementById('sheet-intro');

      if (promptContainer) promptContainer.hidden = !isDaily;
      if (eyebrow) eyebrow.textContent = isDaily ? 'DAILY REFLECTION' : 'FREEFORM JOURNAL';
      updateLandingGreeting();
      if (intro) {
        intro.textContent = isDaily
          ? 'Answer 3 thoughtful prompts (max 1 daily entry per day).'
          : 'No questions, no limits. Express whatever is on your mind.';
      }

      // Swap draft
      const draft = loadDraft(dateInput.value, currentMode);
      textarea.value = draft;
      updateWordCount();
    });
  });

  // Energy toggles (supports both .state-btn and .pill)
  document.querySelectorAll('#energy-toggle button').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#energy-toggle button').forEach((b) => {
        b.classList.remove('is-selected');
        b.setAttribute('aria-checked', 'false');
      });
      btn.classList.add('is-selected');
      btn.setAttribute('aria-checked', 'true');
      currentEnergy = btn.dataset.value;
    });
  });

  // Mood toggles (supports both .state-btn and .pill)
  document.querySelectorAll('#mood-toggle button').forEach((btn) => {
    btn.addEventListener('click', () => {
      document.querySelectorAll('#mood-toggle button').forEach((b) => {
        b.classList.remove('is-selected');
        b.setAttribute('aria-checked', 'false');
      });
      btn.classList.add('is-selected');
      btn.setAttribute('aria-checked', 'true');
      currentMood = btn.dataset.value;
    });
  });

  // Reshuffle Prompts
  if (shuffleBtn) {
    shuffleBtn.addEventListener('click', async () => {
      if (isShuffling) return;
      await reshufflePrompts(dateInput.value);
    });
  }

  // Insert Prompts as Template
  if (insertTemplateBtn) {
    insertTemplateBtn.addEventListener('click', () => {
      if (activeQuestions.length === 0) return;
      const template = activeQuestions.map((q, idx) => `${idx + 1}. ${q.text}\n[Your reflection here]\n`).join('\n');
      insertPromptText(template + '\n');
    });
  }

  // Clear Page
  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      if (!textarea.value.trim() || confirm('Clear this journal page?')) {
        textarea.value = '';
        updateWordCount();
        clearDraft(dateInput.value, currentMode);
        if (statusEl) statusEl.textContent = '';
        textarea.focus();
      }
    });
  }

  // Submit Entry
  if (entryForm) {
    entryForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      const bodyText = textarea.value.trim();
      const dateVal = dateInput.value;
      const submitBtn = document.getElementById('entry-submit');

      if (!bodyText || isSaving) return;

      isSaving = true;
      if (submitBtn) submitBtn.disabled = true;
      if (statusEl) {
        statusEl.className = 'status status-saving';
        statusEl.textContent = 'Saving & embedding entry…';
      }

      try {
        await request('/api/entries', {
          method: 'POST',
          body: JSON.stringify({
            body: bodyText,
            entryType: currentMode,
            entryDate: dateVal,
            energy: currentEnergy,
            mood: currentMood
          })
        });

        // Clear textarea & draft
        textarea.value = '';
        updateWordCount();
        clearDraft(dateVal, currentMode);

        if (statusEl) {
          statusEl.className = 'status status-success';
          statusEl.textContent = 'Page saved in journal.';
          setTimeout(() => {
            if (statusEl.textContent === 'Page saved in journal.') statusEl.textContent = '';
          }, 4000);
        }

        showToast('Entry saved to your journal!', 'success');
      } catch (err) {
        if (statusEl) {
          statusEl.className = 'status status-error';
          statusEl.textContent = err.detail || err.message;
        }

        if (err.status === 409) {
          showToast(err.detail || 'A daily reflection already exists for this date.', 'warning', 7000, {
            label: 'View in History',
            onClick: () => {
              window.location.hash = '#/history';
            }
          });
        } else {
          showToast(err.detail || err.message, 'error');
        }
      } finally {
        isSaving = false;
        if (submitBtn) submitBtn.disabled = false;
      }
    });
  }

  function updateWordCount() {
    if (!wordCountEl) return;
    const count = countWords(textarea.value);
    wordCountEl.textContent = `${count} word${count === 1 ? '' : 's'}`;
  }

  function insertPromptText(text) {
    if (!textarea.value.trim()) {
      textarea.value = text;
    } else {
      textarea.value = textarea.value.trimEnd() + '\n\n' + text;
    }
    updateWordCount();
    saveDraft(dateInput.value, currentMode, textarea.value);
    textarea.focus();
  }
}

async function loadPromptsForDate(date) {
  const promptList = document.getElementById('prompt-list');
  const sourceBadge = document.getElementById('prompt-source-badge');
  const shuffleBtn = document.getElementById('shuffle-prompts');
  if (!promptList) return;

  promptList.replaceChildren();
  const loading = el('li', { className: 'prompt-skeleton', textContent: 'Fetching reflective questions…' });
  promptList.appendChild(loading);

  try {
    const data = await request(`/api/prompts/today?date=${date}`);
    currentPromptsData = data;
    activeQuestions = data.questions || [];
    renderQuestions(activeQuestions, data);
  } catch (err) {
    promptList.replaceChildren();
    promptList.appendChild(el('li', {
      className: 'prompt-error',
      textContent: `Could not load daily questions: ${err.detail || err.message}`
    }));
  }
}

async function reshufflePrompts(date) {
  const promptList = document.getElementById('prompt-list');
  const shuffleBtn = document.getElementById('shuffle-prompts');
  if (!promptList || isShuffling) return;

  isShuffling = true;
  if (shuffleBtn) {
    shuffleBtn.disabled = true;
    shuffleBtn.textContent = 'Thinking…';
  }

  promptList.replaceChildren();
  const skeleton = el('li', { className: 'prompt-skeleton', textContent: 'Thinking of fresh questions… (may take up to 20s)' });
  promptList.appendChild(skeleton);

  try {
    const data = await request(`/api/prompts/reshuffle?date=${date}`, { method: 'POST' });
    currentPromptsData = data;
    activeQuestions = data.questions || [];
    renderQuestions(activeQuestions, data);
    showToast('Prompts reshuffled!', 'success');
  } catch (err) {
    if (currentPromptsData) {
      renderQuestions(activeQuestions, currentPromptsData);
    }
    showToast(err.detail || 'Could not reshuffle prompts.', err.status === 409 ? 'warning' : 'error');
  } finally {
    isShuffling = false;
    if (shuffleBtn) {
      shuffleBtn.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="12" height="12" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg> <span>Shuffle</span>';
      updateShuffleButtonState(currentPromptsData);
    }
  }
}

function renderQuestions(questions, data) {
  const promptList = document.getElementById('prompt-list');
  const sourceBadge = document.getElementById('prompt-source-badge');
  if (!promptList) return;

  promptList.replaceChildren();

  // Update source badge
  if (sourceBadge) {
    sourceBadge.replaceChildren();
    if (data.source === 'FALLBACK') {
      const badge = el('span', {
        className: 'badge badge-starter-questions',
        title: "Couldn't reach the local AI, so these are from the built-in set.",
        textContent: 'Starter questions'
      });
      sourceBadge.appendChild(badge);
    } else {
      const badge = el('span', {
        className: 'badge badge-fresh-questions',
        title: 'Custom questions tailored by local AI based on your recent entries and mood.',
        textContent: 'Fresh questions'
      });
      sourceBadge.appendChild(badge);
    }
  }

  // Render questions
  questions.forEach((q, idx) => {
    const li = el('li', {
      className: 'prompt-item',
      title: 'Click to insert into reflection',
      onClick: () => {
        const textarea = document.getElementById('entry-body');
        if (textarea) {
          const insertText = `${idx + 1}. ${q.text}\n\n`;
          if (!textarea.value.trim()) {
            textarea.value = insertText;
          } else {
            textarea.value = textarea.value.trimEnd() + '\n\n' + insertText;
          }
          textarea.dispatchEvent(new Event('input'));
          textarea.focus();
        }
      }
    }, [
      el('span', { className: 'prompt-kind-tag', textContent: q.kind }),
      el('span', { className: 'prompt-text', textContent: q.text })
    ]);
    promptList.appendChild(li);
  });

  updateShuffleButtonState(data);
}

function updateShuffleButtonState(data) {
  const shuffleBtn = document.getElementById('shuffle-prompts');
  if (!shuffleBtn || !data) return;

  const canReshuffle = data.reshufflesRemaining > 0 || data.source === 'FALLBACK';
  shuffleBtn.disabled = !canReshuffle || isShuffling;
  if (!canReshuffle) {
    shuffleBtn.title = 'Reshuffle limit reached for today (max 1 reshuffle per day)';
  } else {
    shuffleBtn.title = 'Get fresh questions for today';
  }
}

export function updateLandingGreeting() {
  const greetingEl = document.getElementById('landing-greeting');
  const badgeEl = document.getElementById('greeting-badge');
  const subEl = document.getElementById('landing-greeting-sub');
  if (!greetingEl) return;

  const name = localStorage.getItem('pepal-user-name') || store.get().settings?.userName || '';
  const hour = new Date().getHours();
  let timeGreeting = 'Good morning';
  let badgeText = 'MORNING REFLECTION';
  if (hour >= 12 && hour < 17) {
    timeGreeting = 'Good afternoon';
    badgeText = 'AFTERNOON REFLECTION';
  } else if (hour >= 17 && hour < 22) {
    timeGreeting = 'Good evening';
    badgeText = 'EVENING REFLECTION';
  } else if (hour >= 22 || hour < 5) {
    timeGreeting = 'Quiet night';
    badgeText = 'NIGHT REFLECTION';
  }

  if (badgeEl) badgeEl.textContent = badgeText;

  const displayName = name ? name : 'Friend';
  if (currentMode === 'DAILY_PROMPT') {
    greetingEl.textContent = `${timeGreeting}, ${displayName}.`;
    if (subEl) subEl.textContent = 'Your Penpal is here. Make a little room for your thoughts today.';
  } else {
    greetingEl.textContent = `Freeform reflection, ${displayName}.`;
    if (subEl) subEl.textContent = 'Write without limits — your penpal preserves whatever is on your mind.';
  }
}

