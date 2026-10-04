import { request } from '../api.js?v=2.4';
import { store } from '../state.js?v=2.4';
import { showToast, el } from '../ui.js?v=2.4';
import { applyTheme, getSavedTheme, THEME_PAPER, THEME_NIGHT, THEME_AMOLED } from '../theme.js?v=2.4';

let currentSettings = null;
let selectedPreset = 'GENTLE';
let selectedLanguage = 'EN';
let isSaving = false;

export async function initSettingsView() {
  await loadSettings();
  bindSettingsForm();
}

async function loadSettings() {
  const container = document.getElementById('settings-content');
  if (!container) return;

  container.replaceChildren();
  const loading = el('p', { className: 'empty-state', textContent: 'Loading persona settings…' });
  container.appendChild(loading);

  try {
    const data = await request('/api/settings');
    currentSettings = data;
    selectedPreset = data.personaPreset || 'GENTLE';
    selectedLanguage = data.language || 'EN';
    if (data.userName) {
      try {
        localStorage.setItem('pepal-user-name', data.userName);
      } catch (e) {}
    }
    store.set({ settings: data });
    renderSettingsView(data);
  } catch (err) {
    container.replaceChildren();
    container.appendChild(el('p', {
      className: 'empty-state empty-error',
      textContent: `Could not load settings: ${err.detail || err.message}`
    }));
  }
}

function renderSettingsView(settings) {
  const container = document.getElementById('settings-content');
  if (!container) return;

  container.replaceChildren();

  // Section 0: User Name (Personalized Greeting)
  const nameSection = el('section', { className: 'settings-section' });
  nameSection.appendChild(el('h3', { className: 'settings-section-title', textContent: 'Your Name' }));
  nameSection.appendChild(el('p', {
    className: 'settings-section-desc',
    textContent: 'Set your name so pepal can greet you personally on the landing page.'
  }));

  const savedName = localStorage.getItem('pepal-user-name') || settings.userName || '';
  let customTextarea = null;

  const nameInput = el('input', {
    type: 'text',
    id: 'settings-user-name',
    className: 'settings-input',
    placeholder: 'Enter your preferred name (e.g. Anujj)',
    maxLength: '60',
    value: savedName
  });

  const saveNameBtn = el('button', {
    type: 'button',
    id: 'save-name-btn',
    className: 'button button-primary',
    textContent: 'Save Name',
    onClick: async () => {
      const name = nameInput.value.trim();
      try {
        if (name) {
          localStorage.setItem('pepal-user-name', name);
        } else {
          localStorage.removeItem('pepal-user-name');
        }
      } catch (e) {}
      window.dispatchEvent(new CustomEvent('pepal:user-updated', { detail: { userName: name } }));

      try {
        saveNameBtn.disabled = true;
        saveNameBtn.textContent = 'Saving…';
        await request('/api/settings', {
          method: 'PUT',
          body: JSON.stringify({
            personaPreset: selectedPreset,
            customPersona: selectedPreset === 'CUSTOM' ? (customTextarea ? customTextarea.value.trim() : null) : null,
            language: selectedLanguage,
            userName: name || null
          })
        });
        showToast('Your name has been saved!', 'success');
      } catch (err) {
        showToast(`Could not save name: ${err.detail || err.message}`, 'error');
      } finally {
        saveNameBtn.disabled = false;
        saveNameBtn.textContent = 'Save Name';
      }
    }
  });

  nameInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      saveNameBtn.click();
    }
  });

  const nameGroup = el('div', { className: 'name-input-group' }, [nameInput, saveNameBtn]);
  nameSection.appendChild(nameGroup);

  // Section 1: Persona Preset Cards
  const presetSection = el('section', { className: 'settings-section' });
  presetSection.appendChild(el('h3', { className: 'settings-section-title', textContent: 'Companion Persona' }));
  presetSection.appendChild(el('p', {
    className: 'settings-section-desc',
    textContent: 'Choose how your AI journal companion reflects and responds. Presets guide prompt tone and reflection style.'
  }));

  const presetGrid = el('div', { className: 'preset-grid', role: 'radiogroup', 'aria-label': 'Persona presets' });
  const presets = settings.availablePresets || {
    GENTLE: 'Warm, soft, validating, and unhurried reflective presence.',
    PLAYFUL: 'Lighthearted humor and gentle curiosity, never mocking pain.',
    BLUNT: 'Direct and honest reflection with no fluff, always respectful.',
    COACH: 'Forward-looking and action-oriented, prompting one useful question.',
    CUSTOM: 'User-defined custom persona instructions (up to 500 characters).'
  };

  const customTextWrap = el('div', {
    id: 'custom-persona-wrap',
    className: `custom-persona-wrap ${selectedPreset === 'CUSTOM' ? '' : 'is-hidden'}`
  });

  customTextarea = el('textarea', {
    id: 'custom-persona-text',
    className: 'custom-persona-textarea',
    maxlength: '500',
    rows: '4',
    placeholder: 'Describe your desired companion tone (e.g., "Speak softly like an old friend by a fireplace…")'
  });
  if (settings.customPersona) {
    customTextarea.value = settings.customPersona;
  }

  const customCounter = el('div', {
    id: 'custom-persona-counter',
    className: 'character-counter',
    textContent: `${customTextarea.value.length} / 500 characters`
  });

  customTextarea.addEventListener('input', () => {
    customCounter.textContent = `${customTextarea.value.length} / 500 characters`;
  });

  customTextWrap.append(
    el('label', { className: 'eyebrow', for: 'custom-persona-text', textContent: 'Custom Persona Instructions' }),
    customTextarea,
    customCounter
  );

  for (const [key, desc] of Object.entries(presets)) {
    const isSelected = selectedPreset === key;
    const card = el('div', {
      className: `preset-card ${isSelected ? 'is-selected' : ''}`,
      role: 'radio',
      'aria-checked': String(isSelected),
      tabIndex: 0,
      onClick: () => {
        selectedPreset = key;
        presetGrid.querySelectorAll('.preset-card').forEach(c => {
          c.classList.remove('is-selected');
          c.setAttribute('aria-checked', 'false');
        });
        card.classList.add('is-selected');
        card.setAttribute('aria-checked', 'true');

        if (key === 'CUSTOM') {
          customTextWrap.classList.remove('is-hidden');
          customTextarea.focus();
        } else {
          customTextWrap.classList.add('is-hidden');
        }
      }
    }, [
      el('div', { className: 'preset-card-header' }, [
        el('span', { className: 'preset-name', textContent: key }),
        el('span', { className: 'preset-radio-indicator' })
      ]),
      el('p', { className: 'preset-desc', textContent: desc })
    ]);

    presetGrid.appendChild(card);
  }

  presetSection.append(presetGrid, customTextWrap);

  // Section 2: Language Selector
  const langSection = el('section', { className: 'settings-section' });
  langSection.appendChild(el('h3', { className: 'settings-section-title', textContent: 'Language Style' }));
  langSection.appendChild(el('p', {
    className: 'settings-section-desc',
    textContent: 'Language for AI reflections and prompt generation.'
  }));

  const langGroup = el('div', { className: 'toggle-group lang-toggle-group', role: 'radiogroup', 'aria-label': 'Language' }, [
    el('button', {
      type: 'button',
      className: `pill ${selectedLanguage === 'EN' ? 'is-selected' : ''}`,
      textContent: 'English (EN)',
      onClick: (e) => {
        langGroup.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        selectedLanguage = 'EN';
      }
    }),
    el('button', {
      type: 'button',
      className: `pill ${selectedLanguage === 'HINGLISH' ? 'is-selected' : ''}`,
      textContent: 'Hinglish',
      onClick: (e) => {
        langGroup.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        selectedLanguage = 'HINGLISH';
      }
    })
  ]);
  langSection.appendChild(langGroup);

  // Section 3: Visual Theme Selector (Paper, Night, AMOLED)
  const themeSection = el('section', { className: 'settings-section' });
  themeSection.appendChild(el('h3', { className: 'settings-section-title', textContent: 'Appearance Theme' }));

  const currentTheme = getSavedTheme();
  const themeGroup = el('div', { className: 'toggle-group theme-toggle-group', role: 'radiogroup', 'aria-label': 'Visual Theme' }, [
    el('button', {
      type: 'button',
      className: `pill ${currentTheme === THEME_PAPER ? 'is-selected' : ''}`,
      innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg> <span>Paper (Light)</span>',
      onClick: (e) => {
        themeGroup.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        applyTheme(THEME_PAPER);
      }
    }),
    el('button', {
      type: 'button',
      className: `pill ${currentTheme === THEME_NIGHT ? 'is-selected' : ''}`,
      innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg> <span>Night (Slate)</span>',
      onClick: (e) => {
        themeGroup.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        applyTheme(THEME_NIGHT);
      }
    }),
    el('button', {
      type: 'button',
      className: `pill ${currentTheme === THEME_AMOLED ? 'is-selected' : ''}`,
      innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 0 0 20z"/></svg> <span>AMOLED Black</span>',
      onClick: (e) => {
        themeGroup.querySelectorAll('.pill').forEach(b => b.classList.remove('is-selected'));
        e.currentTarget.classList.add('is-selected');
        applyTheme(THEME_AMOLED);
      }
    })
  ]);

  window.addEventListener('pepal:theme-changed', (e) => {
    const t = e.detail?.theme || getSavedTheme();
    themeGroup.querySelectorAll('.pill').forEach((b, idx) => {
      const isTarget = (idx === 0 && t === THEME_PAPER) ||
                       (idx === 1 && t === THEME_NIGHT) ||
                       (idx === 2 && t === THEME_AMOLED);
      b.classList.toggle('is-selected', isTarget);
    });
  });

  themeSection.appendChild(themeGroup);

  // Section 4: Prompt Transparency ("What the AI is told")
  const previewSection = el('section', { className: 'settings-section' });
  const previewToggleBtn = el('button', {
    type: 'button',
    className: 'button button-ghost-sm',
    innerHTML: '<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> <span>Preview System Prompt (What the AI is told)</span>',
    onClick: async () => {
      await togglePromptPreview(previewSection, previewToggleBtn);
    }
  });

  const previewBox = el('div', {
    id: 'prompt-preview-box',
    className: 'prompt-preview-box',
    hidden: 'true'
  });

  previewSection.append(previewToggleBtn, previewBox);

  // Section 5: Save Actions & Notes
  const actionsSection = el('section', { className: 'settings-actions-section' });
  const saveBtn = el('button', {
    type: 'button',
    id: 'settings-save-btn',
    className: 'button button-primary',
    textContent: 'Save Settings',
    onClick: () => handleSaveSettings(customTextarea, nameInput)
  });

  const note = el('p', {
    className: 'settings-save-note',
    textContent: 'Note: Preferences are saved locally and synced with your companion persona.'
  });

  actionsSection.append(saveBtn, note);

  container.append(nameSection, presetSection, langSection, themeSection, previewSection, actionsSection);
}

async function togglePromptPreview(section, buttonEl) {
  const box = section.querySelector('#prompt-preview-box');
  if (!box) return;

  const isHidden = box.hidden;
  if (!isHidden) {
    box.hidden = true;
    buttonEl.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> <span>Preview System Prompt (What the AI is told)</span>';
    return;
  }

  box.hidden = false;
  buttonEl.innerHTML = '<svg class="ui-icon" viewBox="0 0 24 24" width="13" height="13" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> <span>Hide System Prompt</span>';
  box.replaceChildren();
  box.appendChild(el('p', { className: 'empty-sub', textContent: 'Fetching composed prompt preview…' }));

  const customText = document.getElementById('custom-persona-text')?.value || '';
  const query = new URLSearchParams({
    preset: selectedPreset,
    language: selectedLanguage,
    customPersona: customText
  });

  try {
    const data = await request(`/api/settings/preview?${query.toString()}`);
    box.replaceChildren();

    const pre = el('pre', { className: 'prompt-preview-text', textContent: data.systemPrompt });
    const note = el('span', {
      className: 'preview-meta',
      textContent: 'Notice: This transparency preview displays the composed system instructions and safety boundaries. It contains no journal data.'
    });
    box.append(note, pre);
  } catch (err) {
    box.replaceChildren();
    box.appendChild(el('p', {
      className: 'empty-sub empty-error',
      textContent: `Could not load prompt preview: ${err.detail || err.message}`
    }));
  }
}

async function handleSaveSettings(customTextarea, nameInput) {
  if (isSaving) return;
  const saveBtn = document.getElementById('settings-save-btn');
  const customText = customTextarea ? customTextarea.value.trim() : '';
  const userName = nameInput ? nameInput.value.trim() : '';

  if (selectedPreset === 'CUSTOM' && !customText) {
    showToast('Custom persona text is required when CUSTOM preset is selected.', 'error');
    if (customTextarea) customTextarea.focus();
    return;
  }

  isSaving = true;
  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving…';
  }

  // Persist name immediately in localStorage
  try {
    if (userName) {
      localStorage.setItem('pepal-user-name', userName);
    } else {
      localStorage.removeItem('pepal-user-name');
    }
  } catch (e) {}

  window.dispatchEvent(new CustomEvent('pepal:user-updated', { detail: { userName } }));

  try {
    const updated = await request('/api/settings', {
      method: 'PUT',
      body: JSON.stringify({
        personaPreset: selectedPreset,
        customPersona: selectedPreset === 'CUSTOM' ? customText : null,
        language: selectedLanguage,
        userName: userName || null
      })
    });

    currentSettings = updated;
    store.set({ settings: updated });
    showToast('Settings saved successfully!', 'success');
  } catch (err) {
    showToast(`Could not save settings: ${err.detail || err.message}`, 'error');
  } finally {
    isSaving = false;
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save Settings';
    }
  }
}

function bindSettingsForm() {
  // Event listeners are bound directly in render
}
