import { request } from '../api.js?v=2.4';
import { el, showToast, getTodayLocalIso } from '../ui.js?v=2.4';

let conversationHistory = [];

export function initChatView() {
  bindChatForm('chat-form', 'chat-input', 'chat-messages');
  bindChatForm('drawer-chat-form', 'drawer-chat-input', 'drawer-chat-messages');
  bindSuggestionChips();
  bindSaveChatModal();
}

function bindChatForm(formId, inputId, containerId) {
  const form = document.getElementById(formId);
  const input = document.getElementById(inputId);
  if (!form || !input) return;

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const query = input.value.trim();
    if (!query) return;

    const btn = form.querySelector('button[type="submit"]');
    await handleChatTurn(query, containerId, input, btn);
  });
}

function bindSuggestionChips() {
  document.addEventListener('click', (e) => {
    if (e.target.classList.contains('chip-suggestion')) {
      const text = e.target.textContent.trim();
      const mainInput = document.getElementById('chat-input');
      const mainBtn = document.querySelector('#chat-form button[type="submit"]');
      if (mainInput) {
        handleChatTurn(text, 'chat-messages', mainInput, mainBtn);
      }
    }
  });
}

async function handleChatTurn(question, containerId, inputEl, buttonEl) {
  const container = document.getElementById(containerId);
  if (!container) return;

  // Clear starter note if present
  const starter = container.querySelector('.chat-starter');
  if (starter) starter.remove();

  // 1. User message (safe textContent)
  const userMsg = el('div', { className: 'message message-user' });
  userMsg.appendChild(el('p', { className: 'message-text', textContent: question }));
  container.appendChild(userMsg);

  inputEl.value = '';
  inputEl.disabled = true;
  if (buttonEl) buttonEl.disabled = true;

  // 2. Pending indicator
  const pendingMsg = el('div', { className: 'message message-assistant message-pending' });
  const pendingText = el('p', { className: 'message-text', textContent: 'Consulting local Ollama & pgvector…' });
  pendingMsg.appendChild(pendingText);
  container.appendChild(pendingMsg);
  pendingMsg.scrollIntoView({ behavior: 'smooth', block: 'end' });

  try {
    const data = await request('/api/chat', {
      method: 'POST',
      body: JSON.stringify({
        message: question,
        history: conversationHistory.slice(-4)
      })
    });

    // Replace pending text with AI answer (strictly textContent, styled with pre-wrap)
    pendingMsg.classList.remove('message-pending');
    pendingText.textContent = data.answer || 'No response returned from model.';
    conversationHistory.push({ role: 'user', content: question }, { role: 'assistant', content: data.answer });
  } catch (err) {
    pendingMsg.classList.remove('message-pending');
    pendingMsg.classList.add('message-error');
    pendingText.textContent = `Error: ${err.detail || err.message}`;
  } finally {
    inputEl.disabled = false;
    if (buttonEl) buttonEl.disabled = false;
    inputEl.focus();
    pendingMsg.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }
}

function bindSaveChatModal() {
  const topSaveBtn = document.getElementById('rag-save-entry-btn');
  const modal = document.getElementById('save-chat-modal');
  const closeBtn = document.getElementById('save-chat-modal-close');
  const cancelBtn = document.getElementById('modal-cancel-btn');
  const confirmBtn = document.getElementById('modal-confirm-save-btn');
  const textarea = document.getElementById('modal-chat-body');
  const dateInput = document.getElementById('modal-entry-date');

  let selectedEnergy = 'HIGH';
  let selectedMood = 'GOOD';

  // Toggle pills inside modal
  const energyBtns = document.querySelectorAll('#modal-energy-toggle .pill');
  energyBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      energyBtns.forEach(b => b.classList.remove('is-selected'));
      btn.classList.add('is-selected');
      selectedEnergy = btn.dataset.value;
    });
  });

  const moodBtns = document.querySelectorAll('#modal-mood-toggle .pill');
  moodBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      moodBtns.forEach(b => b.classList.remove('is-selected'));
      btn.classList.add('is-selected');
      selectedMood = btn.dataset.value;
    });
  });

  const closeModal = () => {
    if (modal && typeof modal.close === 'function') {
      modal.close();
    } else if (modal) {
      modal.removeAttribute('open');
    }
  };

  if (closeBtn) closeBtn.addEventListener('click', closeModal);
  if (cancelBtn) cancelBtn.addEventListener('click', closeModal);

  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) closeModal();
    });
  }

  if (topSaveBtn) {
    topSaveBtn.addEventListener('click', () => {
      const today = getTodayLocalIso();
      if (dateInput) dateInput.value = today;

      // Format complete conversation text into editable reflection
      let formattedText = `Reflection with RAG Bot (${today})\n\n`;
      if (conversationHistory && conversationHistory.length > 0) {
        for (const turn of conversationHistory) {
          if (turn.role === 'user') {
            formattedText += `Q: ${turn.content}\n\n`;
          } else {
            formattedText += `A: ${turn.content}\n\n--------------------\n\n`;
          }
        }
      } else {
        const chatInput = document.getElementById('chat-input');
        if (chatInput && chatInput.value.trim()) {
          formattedText += `Q: ${chatInput.value.trim()}\n\n`;
        }
        formattedText += `[Add your reflections or notes here…]\n`;
      }

      if (textarea) {
        textarea.value = formattedText.trim();
      }

      if (modal && typeof modal.showModal === 'function') {
        modal.showModal();
      } else if (modal) {
        modal.setAttribute('open', '');
      }

      if (textarea) {
        textarea.focus();
        textarea.selectionStart = 0;
        textarea.selectionEnd = 0;
      }
    });
  }

  if (confirmBtn) {
    confirmBtn.addEventListener('click', async () => {
      const content = textarea ? textarea.value.trim() : '';
      if (!content) {
        showToast('Entry text cannot be blank.', 'error');
        if (textarea) textarea.focus();
        return;
      }

      const entryDate = (dateInput && dateInput.value) ? dateInput.value : getTodayLocalIso();

      confirmBtn.disabled = true;
      confirmBtn.textContent = 'Saving…';

      try {
        await request('/api/entries', {
          method: 'POST',
          body: JSON.stringify({
            body: content,
            entryType: 'FREEFORM',
            entryDate: entryDate,
            energy: selectedEnergy,
            mood: selectedMood
          })
        });

        closeModal();
        showToast('Chat conversation saved as journal entry!', 'success');
      } catch (err) {
        showToast(`Could not save entry: ${err.detail || err.message}`, 'error');
      } finally {
        confirmBtn.disabled = false;
        confirmBtn.textContent = 'Save to Journal Archive';
      }
    });
  }
}
