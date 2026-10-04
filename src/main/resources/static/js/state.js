/**
 * Central client-side state store.
 */
class Store {
  constructor() {
    this.state = {
      activeTab: 'write',
      entries: [],
      trash: [],
      settings: null,
      promptsCache: new Map(),
      summaries: [],
      isDrawerOpen: false,
      chatDrawerHistory: []
    };
    this.listeners = new Set();
  }

  get() {
    return this.state;
  }

  set(partial) {
    this.state = { ...this.state, ...partial };
    this.notify();
  }

  subscribe(listener) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  notify() {
    for (const listener of this.listeners) {
      try {
        listener(this.state);
      } catch (err) {
        // Prevent one subscriber failure from breaking state propagation
      }
    }
  }

  cachePrompts(date, promptData) {
    this.state.promptsCache.set(date, promptData);
  }

  getCachedPrompts(date) {
    return this.state.promptsCache.get(date);
  }
}

export const store = new Store();
