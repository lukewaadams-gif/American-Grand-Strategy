const KEY = 'ags-launch';
/** Opens the game's page to carry out the choice; false if the browser keeps no session storage (or the save is too big). */
export function handOff(l, page) {
    try {
        sessionStorage.setItem(KEY, JSON.stringify(l));
    }
    catch {
        return false;
    }
    location.href = page;
    return true;
}
/** The choice handed over, taken once (a reload does not repeat it); null if there is none. */
export function takeLaunch() {
    try {
        const t = sessionStorage.getItem(KEY);
        sessionStorage.removeItem(KEY);
        return t ? JSON.parse(t) : null;
    }
    catch {
        return null;
    }
}
