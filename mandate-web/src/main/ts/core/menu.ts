// A small accessible pop-up menu: a list of choices anchored to the control that opened it. Arrow keys move,
// Enter picks, Escape closes and returns focus. One menu at a time.
import { h } from './dom.js';

export interface MenuItem {
  label: string;
  /** Secondary text under the label. */ detail?: string;
  /** A figure at the right (a cost, a count). */ right?: string;
  /** Why it cannot be chosen, if it cannot. */ disabled?: string | null;
  /** A colour swatch at the left (the activity's family). */ swatch?: string;
  pick(): void;
}

let open: { el: HTMLElement; back: HTMLElement | null; off: () => void } | null = null;

export function closeMenu(focusBack = true): void {
  if (!open) return;
  const o = open;
  open = null;
  o.off();
  o.el.remove();
  if (focusBack) o.back?.focus();
}

export function menuOpen(): boolean { return open !== null; }

/** Opens a menu at an element (below it) or at a point (a right-click). */
export function openMenu(at: HTMLElement | { x: number; y: number }, title: string, items: MenuItem[], note?: string): void {
  closeMenu(false);
  const back = at instanceof HTMLElement ? at : (document.activeElement as HTMLElement | null);
  const buttons: HTMLButtonElement[] = [];
  const list = h('div', { class: 'menu-list', role: 'menu', 'aria-label': title });
  for (const it of items) {
    const b = h('button', { type: 'button', class: 'menu-item', role: 'menuitem', tabindex: -1, 'aria-disabled': it.disabled ? 'true' : null,
      title: it.disabled ?? it.detail ?? null },
      it.swatch ? h('span', { class: 'menu-swatch', style: `background:${it.swatch}`, 'aria-hidden': 'true' }) : null,
      h('span', { class: 'menu-text' }, h('span', { class: 'menu-label' }, it.label),
        it.disabled ? h('span', { class: 'menu-detail' }, it.disabled) : it.detail ? h('span', { class: 'menu-detail' }, it.detail) : null),
      it.right ? h('span', { class: 'menu-right' }, it.right) : null);
    b.addEventListener('click', () => { if (it.disabled) return; closeMenu(true); it.pick(); });
    buttons.push(b);
    list.append(b);
  }
  const el = h('div', { class: 'menu' }, h('div', { class: 'menu-title' }, title), note ? h('p', { class: 'menu-note' }, note) : null, list);
  document.body.append(el);
  // position: under the anchor (or at the point), kept inside the window
  const r = at instanceof HTMLElement ? at.getBoundingClientRect() : { left: at.x, bottom: at.y, top: at.y };
  const w = el.offsetWidth, ht = el.offsetHeight;
  let x = r.left, y = r.bottom + 4;
  if (x + w > window.innerWidth - 8) x = window.innerWidth - w - 8;
  if (y + ht > window.innerHeight - 8) y = Math.max(8, r.top - ht - 4);
  el.style.left = Math.max(8, x) + 'px';
  el.style.top = Math.max(8, y) + 'px';
  const enabled = () => buttons.filter(b => b.getAttribute('aria-disabled') !== 'true');
  // roving focus: the current item is the menu's one Tab stop
  const focusAt = (i: number) => {
    const e = enabled();
    if (!e.length) return;
    const next = e[(i + e.length) % e.length]!;
    for (const b of buttons) b.tabIndex = b === next ? 0 : -1;
    next.focus();
  };
  focusAt(0);
  const onKey = (e: KeyboardEvent) => {
    const e2 = enabled(), i = e2.indexOf(document.activeElement as HTMLButtonElement);
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); closeMenu(true); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); focusAt(i + 1); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); focusAt(i - 1); }
    else if (e.key === 'Home') { e.preventDefault(); focusAt(0); }
    else if (e.key === 'End') { e.preventDefault(); focusAt(e2.length - 1); }
    else if (e.key === 'Tab') { e.preventDefault(); closeMenu(true); }
  };
  const onDown = (e: MouseEvent) => { if (!el.contains(e.target as Node)) closeMenu(false); };
  document.addEventListener('keydown', onKey, true);
  setTimeout(() => document.addEventListener('mousedown', onDown, true));
  open = { el, back, off: () => { document.removeEventListener('keydown', onKey, true); document.removeEventListener('mousedown', onDown, true); } };
}
