// The hand-off from the site's front page to the game's page: what the player chose there (a new game, a moment, a saved
// file, Island Chain), kept in the tab's session storage for the one page load that carries it out.
import type { NewGame } from '../start.js';

export type Launch =
  | { kind: 'new'; game: NewGame }
  | { kind: 'quick'; save: string; then?: object }
  | { kind: 'load'; text: string }
  | { kind: 'islandChain'; options: { year: number; preset: number; side: 'US' | 'PRC' } }
  | { kind: 'tutorial' };

const KEY = 'ags-launch';

/** Opens the game's page to carry out the choice; false if the browser keeps no session storage (or the save is too big). */
export function handOff(l: Launch, page: string): boolean {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(l));
  } catch {
    return false;
  }
  location.href = page;
  return true;
}

/** The choice handed over, taken once (a reload does not repeat it); null if there is none. */
export function takeLaunch(): Launch | null {
  try {
    const t = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return t ? (JSON.parse(t) as Launch) : null;
  } catch {
    return null;
  }
}
