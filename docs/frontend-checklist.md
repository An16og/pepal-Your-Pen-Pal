# pepal — Frontend Manual Testing Checklist

This checklist guides manual verification of the **pepal** offline AI journal frontend across all views, features, responsive breakpoints, and offline/edge states.

---

## 1. Environment & Setup

- [ ] **Startup:** Backend starts cleanly with `./mvnw spring-boot:run`.
- [ ] **Navigation:** App is accessible at `http://localhost:8080/`.
- [ ] **Offline Readiness:** Open DevTools > Network > Set to "Offline" (or turn off Wi-Fi/LAN). Verify no failed external network requests (no fonts.googleapis.com, cdnjs, external scripts, or external assets).
- [ ] **Console Cleanliness:** Open browser DevTools Console. No unhandled errors or CSP violations on initial load.

---

## 2. Theme & Design (Paper vs Night)

- [ ] **Initial Load (Paper Theme):** Default theme loads in warm paper mode (creamy beige background `#fcfaf7`, dark charcoal typography, warm amber accents).
- [ ] **Theme Toggle:**
  - Click the sun/moon button in the top navigation bar.
  - Theme switches instantly to **Night** mode (deep slate `#16181d`, high-contrast text, indigo/slate accents).
  - `<meta name="color-scheme">` updates to `dark`.
  - Icon in header switches to show the sun icon.
- [ ] **No-Flash Persistence:**
  - Refresh the page while in Night mode.
  - Verify there is **zero white flash** before the page renders (handled by the synchronous inline script in `<head>`).
- [ ] **Settings Theme Radio:**
  - Navigate to Settings (`#/settings`).
  - Toggle theme using the radio buttons under "Appearance".
  - Verify header theme button and entire UI reflect the change synchronously.
- [ ] **Legacy Migration:**
  - If `localStorage` contains `usher-theme`, verify it is read correctly and seamlessly migrated to `pepal-theme`.

---

## 3. Daily Prompts & Write View (`#/write`)

### 3.1 Date & Time
- [ ] **Live Clock:** Digital clock in the sub-header updates every second.
- [ ] **Local Date Picker:** Defaults to the browser's local calendar date. Changing the date updates the prompt context and draft keys without UTC day-shift errors.
- [ ] **Today Button:** Clicking "Today" resets the date picker to the current day.

### 3.2 Dynamic Prompts & Fallback
- [ ] **Ollama Online:** When Ollama is running, dynamic AI-tailored questions load with source badge `AI generated`.
- [ ] **Ollama Offline (Static Fallback):**
  - Stop Ollama or disconnect Ollama service.
  - Refresh prompts or change date.
  - Verify the fallback pool returns 3 deterministic questions with badge `Starter questions`.
  - Hovering/tapping the info icon reveals: *"Generated without AI from the local question pool. Check Ollama if you expected personalized questions."*
- [ ] **Kinds Displayed:** The 3 questions are properly categorized with colored kind chips (`Reflective`, `Playful`, `Forward`).
- [ ] **Reshuffle Button:**
  - Clicking "Reshuffle" requests new prompts via `POST /api/prompts/reshuffle?date=...`.
  - Remaining reshuffle count decrements (e.g. "2 left today").
  - When remaining reshuffles reach 0, button becomes disabled (`0 left today`).
  - If backend returns 409 (reshuffle limit reached), toast warning displays gracefully.

### 3.3 Writing & Reflection Form
- [ ] **Reflection Mode:**
  - "Daily Reflection" tab selected.
  - 3 textareas are rendered, corresponding to the 3 prompt questions.
  - Answering prompts automatically compiles into an answer block when submitted.
- [ ] **Freeform Mode:**
  - Click "Freeform Entry". Single notebook-lined textarea appears.
- [ ] **Mood & Energy Selectors:**
  - Mood options: `GOOD` (😊 Good) and `BAD` (😔 Low).
  - Energy options: `HIGH` (⚡ High) and `LOW` (🪫 Low).
  - Selecting options highlights the corresponding segmented pill button.
- [ ] **Draft Autosave:**
  - Type some text in the entry body.
  - Refresh or navigate to another tab and come back to `#/write`.
  - Text, mood, and energy state are preserved from `localStorage` (`pepal-draft-...`).
  - Toast or draft indicator confirms saved draft.
- [ ] **Word Count:** Live counter updates as you type.
- [ ] **Keyboard Shortcut:** Pressing `Ctrl+Enter` (or `Cmd+Enter` on macOS) submits the entry.
- [ ] **Duplicate Daily Reflection (409):**
  - Submit a daily reflection for today.
  - Attempt to submit a second daily reflection for the same date.
  - Backend returns 409 Conflict.
  - Toast appears: *"A daily reflection already exists for this date."* with an action link *"View in History"*.

---

## 4. History View (`#/history`)

### 4.1 Entry Grouping & Display
- [ ] **Grouped by Date:** Entries are grouped under formatted date headers (e.g., `Today`, `Yesterday`, `October 3, 2026`), sorted newest date first.
- [ ] **Metadata Chips:** Each entry card displays:
  - Time formatted locally.
  - Type chip (`Daily Reflection` or `Freeform`).
  - Mood badge (`GOOD` / `BAD`).
  - Energy badge (`HIGH` / `LOW`).
  - Word count chip.
  - `(edited)` indicator if `updated_at > created_at`.
- [ ] **Collapsible Text:** Long reflections display a preview with a "Read more" toggle that expands/collapses cleanly.
- [ ] **XSS Verification:** Entries containing `<script>`, `<img>`, or raw HTML display as literal characters, not executable code.

### 4.2 Filtering & Search
- [ ] **Search Input:** Typing in the search field filters entries in real time across the entry body text.
- [ ] **Mood Filter:** Filter by `All moods`, `Good`, or `Low`.
- [ ] **Energy Filter:** Filter by `All energy`, `High`, or `Low`.
- [ ] **Type Filter:** Filter by `All types`, `Daily reflections`, or `Freeform`.
- [ ] **Date Range Filter:** Selecting "From" and "To" dates constrains the results accurately.
- [ ] **Clear Filters:** Clicking "Clear" resets all filters and shows all entries.
- [ ] **Empty State:** When no entries match filters, a friendly empty-state card with a "Clear Filters" button appears.

### 4.3 Inline Editing
- [ ] Click "Edit" on an entry card.
- [ ] The card switches to an inline editor preserving current text, mood, and energy.
- [ ] Date and entry type are read-only / immutable.
- [ ] Modify the body, switch mood/energy, and click "Save changes".
- [ ] `PUT /api/entries/{id}` is called; the card returns to view mode with updated content and `(edited)` badge.
- [ ] Clicking "Cancel" discards unsaved edits without modifying the entry.

### 4.4 Soft Delete & Undo
- [ ] Click "Trash" on an entry card.
- [ ] The card is removed immediately from the History view.
- [ ] A toast notification appears at the bottom-right: *"Entry moved to trash"* with an **"Undo"** button and an 8-second countdown bar.
- [ ] Clicking **"Undo"** before 8 seconds calls `POST /api/entries/{id}/restore` and restores the entry immediately back into the list.
- [ ] Letting the 8 seconds expire allows the entry to remain in trash.

---

## 5. Trash View (`#/trash`)

### 5.1 Listing & Days Remaining
- [ ] Navigate to Trash (`#/trash`).
- [ ] Trashed entries list in order of newest-deleted first.
- [ ] Badge displays days remaining until permanent auto-purge (e.g., `29 days remaining` or `Purging soon`).
- [ ] Empty state message displays when trash contains 0 items.

### 5.2 Restore & Conflict Resolution
- [ ] **Standard Restore:**
  - Click "Restore" on an entry where no conflicting date reflection exists.
  - Entry is restored successfully and removed from trash.
- [ ] **409 Conflict Resolution Modal:**
  - Create a daily reflection for Date $D$.
  - Soft-delete that daily reflection.
  - Create a *new* daily reflection for Date $D$.
  - Go to Trash and click "Restore" on the old reflection for Date $D$.
  - Backend responds with 409 Conflict naming the existing entry ID.
  - An accessible modal dialog opens displaying side-by-side comparison:
    - **Current Entry** (existing active entry for that date).
    - **Restored Entry** (the entry being recovered from trash).
  - Modal provides clear options:
    - **"Keep Current"**: Dismisses the modal, keeps the active reflection.
    - **"Replace with Restored"**: Soft-deletes the current conflicting entry and restores the trashed one.
    - **"Cancel"**: Closes modal with no changes made.

### 5.3 Permanent Deletion & Empty Trash
- [ ] **Delete Forever:**
  - Click "Delete forever" on a trashed entry.
  - Confirmation prompt prevents accidental clicks.
  - Upon confirmation, calls `DELETE /api/entries/{id}/permanent`. Entry is permanently removed.
- [ ] **Empty Trash:**
  - Click "Empty Trash" button in the view header.
  - Modal/confirmation asks for confirmation.
  - Upon confirmation, calls `DELETE /api/trash`. All trashed entries are permanently removed.

---

## 6. Summaries View (`#/summaries`)

### 6.1 Period Navigation
- [ ] Navigate to Summaries (`#/summaries`).
- [ ] Sub-tabs toggle between "Weekly Reviews" and "Monthly Reviews".

### 6.2 Summary Cards
- [ ] Each summary card displays:
  - Date range / period heading (e.g. `Week of Sep 22 – Sep 28, 2026`).
  - Status badges: `In progress` (current period), `Final` (past period), `Stats only` (if LLM skipped), `Out of date` (if entries were added after generation).
  - Headline and narrative body rendered safely with `white-space: pre-wrap`.
  - Stats strip: Total entries, Word count, Mood breakdown (Good vs Low), Energy breakdown (High vs Low).
  - Key Themes / Topics chips.
- [ ] **Regenerate Button:**
  - Clicking "Regenerate" sends `POST /api/summaries/generate?type=...&force=true`.
  - Card shows loading spinner while background generation runs.
- [ ] **Accordion Entries Expansion:**
  - Clicking "View entries in this period" expands an accordion list showing all entries included in the summary.
- [ ] **Graceful 404 / Missing Backend Support:**
  - If backend returns 404 (feature endpoint not yet available), a helpful placeholder card renders without crashing or throwing uncaught JS errors.

---

## 7. Companion Chat View (`#/chat` & Side Drawer)

### 7.1 Dedicated Chat Tab (`#/chat`)
- [ ] Navigate to Companion (`#/chat`).
- [ ] Initial state displays warm greeting and suggestion chips (e.g. *"What patterns do you notice in my energy?"*, *"Summarize my last week"*).
- [ ] Clicking a suggestion chip populates the input field.
- [ ] Send a message via Enter key or click "Send".
- [ ] User message appears immediately on the right side.
- [ ] Animated typing indicator (three bouncing dots) displays while waiting for the LLM response.
- [ ] Assistant response appears rendered safely with proper line breaks (`pre-wrap`).
- [ ] Any LLM markdown or quotes with special characters are rendered as text without XSS vulnerabilities.

### 7.2 Slide-Over Chat Drawer
- [ ] From any view (`#/write`, `#/history`, `#/settings`, etc.), click the floating "Chat" button or header icon.
- [ ] Drawer slides out smoothly from the right side of the screen over an overlay backdrop.
- [ ] Chat conversation state is synchronized between the drawer and the main chat view.
- [ ] Pressing `Esc` or clicking the backdrop closes the drawer.
- [ ] Focus returns to the triggering button.

### 7.3 Error Handling
- [ ] Disconnect Ollama and send a chat message.
- [ ] Error message renders inline inside the chat thread with a "Retry" button rather than breaking the application.

---

## 8. Settings View (`#/settings`)

### 8.1 Companion Persona
- [ ] Navigate to Settings (`#/settings`).
- [ ] 5 persona preset cards are displayed:
  - **Gentle** (Warm, empathetic, reflective)
  - **Playful** (Curious, lightly humorous, upbeat)
  - **Blunt** (Direct, concise, no-nonsense)
  - **Coach** (Goal-oriented, forward-looking, action-focused)
  - **Custom** (User-defined persona prompt)
- [ ] Selecting "Custom" reveals a textarea for custom instructions.
- [ ] **Character Counter:** Custom persona textarea displays a live counter enforcing the 500-character maximum.
- [ ] Selecting presets automatically updates the preview.

### 8.2 Language
- [ ] Language toggle options: `English (EN)` and `Hinglish (HINGLISH)`.
- [ ] Changing language updates the companion prompt structure.

### 8.3 System Prompt Transparency Preview
- [ ] Click "Preview System Prompt" or observe the live preview card.
- [ ] Queries `GET /api/settings/preview?preset=...&language=...`.
- [ ] Displays the composed prompt layers (Persona + Language instruction + Safety boundary).
- [ ] Demonstrates transparency into what instructions are given to the local LLM.

### 8.4 Saving Settings
- [ ] Click "Save Settings".
- [ ] Calls `PUT /api/settings`.
- [ ] Success toast confirms: *"Companion settings saved"*.

---

## 9. Accessibility & Responsiveness

### 9.1 Keyboard & Screen Reader Accessibility
- [ ] **Tab Order:** Logical tab order across all interactive elements (navigation, forms, cards, modals).
- [ ] **Focus Styles:** Visible focus ring on all interactive buttons, inputs, links, and cards in both Paper and Night themes.
- [ ] **Modal Trapping:** Opening the 409 conflict modal or side drawer traps focus inside the dialog; `Esc` dismisses it.
- [ ] **ARIA Attributes:** All tabs have `role="tab"`, `aria-selected`, `aria-controls`. Modals have `role="dialog"`, `aria-modal="true"`.

### 9.2 Responsive Layouts
- [ ] **Desktop ($\ge 1024$px):** Full layout with visible side navigation and wide content containers.
- [ ] **Tablet ($768$px - $1023$px):** Flexible grid collapses cleanly; drawer slides out to max 480px.
- [ ] **Mobile ($360$px - $767$px):**
  - Navigation bar remains accessible (scrolling or sticky bottom/top bar).
  - Touch targets are $\ge 40\times 40$ pixels.
  - Side-by-side conflict modal stacks vertically.
  - No horizontal page overflow or clipping.

---

## 10. Summary & Sign-off

| Area | Status | Verified By | Notes |
| :--- | :---: | :--- | :--- |
| Paper / Night Theme | ⬜ Pass / ⬜ Fail | | Zero flash, instant persistence |
| Dynamic Prompts & Fallback | ⬜ Pass / ⬜ Fail | | 3 questions, kind chips, reshuffle limit |
| Write & Reflection | ⬜ Pass / ⬜ Fail | | Autosave, Ctrl+Enter, 409 duplicate |
| History & Filtering | ⬜ Pass / ⬜ Fail | | Date groups, search, inline edit, 8s undo |
| Trash & Conflict Modal | ⬜ Pass / ⬜ Fail | | Days remaining, side-by-side comparison |
| Summaries & Fallback | ⬜ Pass / ⬜ Fail | | Cards, badges, stats strip, 404 handling |
| Chat (Tab & Drawer) | ⬜ Pass / ⬜ Fail | | Typing dots, pre-wrap, drawer sync |
| Settings & Preview | ⬜ Pass / ⬜ Fail | | 500-char counter, transparency preview |
| Offline & XSS Security | ⬜ Pass / ⬜ Fail | | 0 external requests, textContent rendering |
