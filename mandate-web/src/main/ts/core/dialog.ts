// The one dialog layer and the status toast, shared by every world's screens. A dialog is modal: it takes focus,
// and closing it returns focus to whatever had it before.
import { h } from './dom.js';

export interface DialogHost { open(content: HTMLElement, label: string, onClose?: () => void): void; close(): void; }

export const host: DialogHost = (() => {
  let scrim: HTMLElement | null = null, onClose: (() => void) | undefined, returnTo: Element | null = null;
  return {
    open(content, label, close) {
      if (scrim) scrim.remove(); else returnTo = document.activeElement;
      onClose = close;
      scrim = h('div', { class: 'scrim' }, h('div', { class: 'dialog', role: 'dialog', 'aria-modal': 'true', 'aria-label': label }, content));
      document.body.append(scrim);
      (scrim.querySelector('button.choice, button.primary, button') as HTMLElement | null)?.focus();
    },
    close() {
      if (!scrim) return;
      scrim.remove(); scrim = null;
      const cb = onClose; onClose = undefined;
      if (returnTo instanceof HTMLElement) returnTo.focus();
      cb?.();
    },
  };
})();

let toastTimer = 0;

/** A short message at the corner of the screen, read out by screen readers. */
export function toast(text: string): void {
  let t = document.getElementById('toast');
  if (!t) { t = h('div', { id: 'toast', class: 'toast', role: 'status', 'aria-live': 'polite' }); document.body.append(t); }
  t.textContent = text;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => t!.classList.remove('show'), 6000);
}
