/**
 * Central client-side state store (settings shared across views).
 */
class Store {
  constructor() {
    this.state = {
      settings: null
    };
  }

  get() {
    return this.state;
  }

  set(partial) {
    this.state = { ...this.state, ...partial };
  }
}

export const store = new Store();
