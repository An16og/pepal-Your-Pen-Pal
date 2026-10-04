/**
 * Helper to build YYYY-MM-DD strictly from browser's local date parts.
 * Never call toISOString() to avoid UTC day drift.
 */
export function getTodayLocalIso() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

export function formatLocalDate(dateStr, style = 'full') {
  if (!dateStr) return '';
  const parts = dateStr.split('-').map(Number);
  if (parts.length < 3) return dateStr;
  const [y, m, d] = parts;
  const dateObj = new Date(y, m - 1, d);
  return new Intl.DateTimeFormat(undefined, { dateStyle: style }).format(dateObj);
}

export function formatSavedTime(isoStr) {
  if (!isoStr) return '';
  try {
    return new Intl.DateTimeFormat(undefined, {
      hour: 'numeric',
      minute: '2-digit',
      second: '2-digit'
    }).format(new Date(isoStr));
  } catch (e) {
    return '';
  }
}

export function countWords(text) {
  if (!text) return 0;
  const trimmed = text.trim();
  return trimmed ? trimmed.split(/\s+/).length : 0;
}

/**
 * Safe DOM element creator avoiding innerHTML with dynamic data.
 */
export function el(tag, attrs = {}, children = []) {
  const element = document.createElement(tag);
  for (const [key, val] of Object.entries(attrs)) {
    if (key === 'className') {
      element.className = val;
    } else if (key === 'textContent') {
      element.textContent = val;
    } else if (key === 'innerHTML') {
      element.innerHTML = val;
    } else if (key.startsWith('on') && typeof val === 'function') {
      element.addEventListener(key.slice(2).toLowerCase(), val);
    } else if (val !== null && val !== undefined) {
      element.setAttribute(key, String(val));
    }
  }

  const childList = Array.isArray(children) ? children : [children];
  for (const child of childList) {
    if (child === null || child === undefined) continue;
    if (typeof child === 'string' || typeof child === 'number') {
      element.appendChild(document.createTextNode(String(child)));
    } else if (child instanceof Node) {
      element.appendChild(child);
    }
  }
  return element;
}

/**
 * Toast Notification System
 */
let toastContainer = null;
function getToastContainer() {
  if (!toastContainer) {
    toastContainer = document.getElementById('toast-region');
    if (!toastContainer) {
      toastContainer = el('div', {
        id: 'toast-region',
        className: 'toast-region',
        'aria-live': 'polite',
        'aria-atomic': 'true'
      });
      document.body.appendChild(toastContainer);
    }
  }
  return toastContainer;
}

export function showToast(message, type = 'info', durationMs = 4500, action = null) {
  const container = getToastContainer();
  const toast = el('div', { className: `toast toast-${type}` });
  const msgEl = el('span', { className: 'toast-message', textContent: message });
  toast.appendChild(msgEl);

  let timeoutId = null;
  const dismiss = () => {
    if (timeoutId) clearTimeout(timeoutId);
    toast.classList.add('toast-dismissing');
    toast.addEventListener('transitionend', () => toast.remove(), { once: true });
    setTimeout(() => toast.remove(), 300);
  };

  if (action && typeof action.onClick === 'function') {
    const actionBtn = el('button', {
      type: 'button',
      className: 'toast-action-btn',
      textContent: action.label || 'Action',
      onClick: () => {
        dismiss();
        action.onClick();
      }
    });
    toast.appendChild(actionBtn);
  }

  const closeBtn = el('button', {
    type: 'button',
    className: 'toast-close-btn',
    'aria-label': 'Close notification',
    textContent: '×',
    onClick: dismiss
  });
  toast.appendChild(closeBtn);

  container.appendChild(toast);
  requestAnimationFrame(() => toast.classList.add('toast-visible'));

  if (durationMs > 0) {
    timeoutId = setTimeout(dismiss, durationMs);
  }

  return { dismiss };
}

/**
 * Accessible Modal System with Focus Trap and Escape Listener
 */
let activeModal = null;
export function openModal({ title, contentNode, actions = [], onClose = null }) {
  closeModal();

  const prevActiveElement = document.activeElement;
  const backdrop = el('div', { className: 'modal-backdrop' });
  const dialog = el('div', {
    className: 'modal-dialog',
    role: 'dialog',
    'aria-modal': 'true',
    'aria-labelledby': 'modal-title'
  });

  const header = el('div', { className: 'modal-header' });
  const titleEl = el('h2', { id: 'modal-title', className: 'modal-title', textContent: title });
  const closeBtn = el('button', {
    type: 'button',
    className: 'modal-close-btn',
    'aria-label': 'Close modal',
    textContent: '×',
    onClick: () => closeModal()
  });
  header.append(titleEl, closeBtn);

  const body = el('div', { className: 'modal-body' }, [contentNode]);
  const footer = el('div', { className: 'modal-footer' });

  for (const act of actions) {
    const btn = el('button', {
      type: 'button',
      className: `button ${act.className || 'button-secondary'}`,
      textContent: act.label,
      onClick: async () => {
        if (typeof act.onClick === 'function') {
          const shouldClose = await act.onClick();
          if (shouldClose !== false) closeModal();
        } else {
          closeModal();
        }
      }
    });
    footer.appendChild(btn);
  }

  dialog.append(header, body, footer);
  backdrop.appendChild(dialog);
  document.body.appendChild(backdrop);
  document.body.classList.add('modal-open');

  // Focus trap
  const focusable = dialog.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
  if (focusable.length > 0) {
    focusable[0].focus();
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      closeModal();
    } else if (e.key === 'Tab') {
      const focusableEls = Array.from(dialog.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'))
        .filter(node => !node.disabled);
      if (focusableEls.length === 0) return;
      const first = focusableEls[0];
      const last = focusableEls[focusableEls.length - 1];

      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault();
        first.focus();
      }
    }
  };

  const handleBackdropClick = (e) => {
    if (e.target === backdrop) {
      closeModal();
    }
  };

  document.addEventListener('keydown', handleKeyDown);
  backdrop.addEventListener('click', handleBackdropClick);

  activeModal = {
    backdrop,
    prevActiveElement,
    handleKeyDown,
    onClose
  };

  requestAnimationFrame(() => backdrop.classList.add('modal-visible'));
}

export function closeModal() {
  if (!activeModal) return;
  const { backdrop, prevActiveElement, handleKeyDown, onClose } = activeModal;
  document.removeEventListener('keydown', handleKeyDown);
  document.body.classList.remove('modal-open');
  backdrop.classList.remove('modal-visible');

  setTimeout(() => {
    backdrop.remove();
    if (prevActiveElement && typeof prevActiveElement.focus === 'function') {
      prevActiveElement.focus();
    }
    if (typeof onClose === 'function') {
      onClose();
    }
  }, 200);

  activeModal = null;
}
