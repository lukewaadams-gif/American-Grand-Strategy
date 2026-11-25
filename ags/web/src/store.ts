// Browser storage that never throws: private windows and blocked storage just remember nothing.
export const store = {
  get(k: string, d: any) { try { const v = localStorage.getItem(k); return v ? JSON.parse(v) : d; } catch { return d; } },
  set(k: string, v: any) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* storage unavailable */ } },
};
