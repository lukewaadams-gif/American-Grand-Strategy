let mod = null;
/** The site's build (version.json), so the engine and the saves always come from the same one; empty if there is none. */
let build = '';
/** A URL for this build's copy of a file, so a browser's cached copy from an earlier build is never used with this one. */
function versioned(url) { return build ? url + (url.includes('?') ? '&' : '?') + 'v=' + build : url; }
/**
 * Loads the engine module once. It asks the server which build it has (past the browser's cache) and loads that
 * build's engine: an engine kept from an earlier visit cannot read a newer build's saves.
 */
export async function start(url = new URL('../../engine/engine.js', import.meta.url).href) {
    if (mod)
        return;
    try {
        const r = await fetch(new URL('../../version.json', import.meta.url).href, { cache: 'no-cache' });
        if (r.ok)
            build = (await r.json()).build ?? '';
    }
    catch { /* a build without version.json: load as before */ }
    mod = (await import(versioned(url)));
}
/** A file of this build (a sample save), fetched past any stale copy in the browser's cache. */
export async function buildFile(path) {
    const r = await fetch(versioned(path), { cache: 'no-cache' });
    if (!r.ok)
        throw new Error(`Could not load ${path} (${r.status})`);
    return r.text();
}
export class Refused extends Error {
}
function request(req) {
    if (!mod)
        throw new Error('The engine has not started');
    const res = JSON.parse(mod.call(JSON.stringify(req)));
    if (!res.ok)
        throw new Refused(res.error ?? 'Not possible');
    return res;
}
export const engine = {
    load(text) { request({ op: 'load', text }); },
    save(ui = null) { return request({ op: 'save', ui }).text; },
    campaign() { return request({ op: 'view', name: 'campaign' }); },
    presidency() { return request({ op: 'view', name: 'presidency' }); },
    paper() { return request({ op: 'view', name: 'paper' }); },
    country() { return request({ op: 'view', name: 'country' }); },
    economy() { return request({ op: 'view', name: 'economy' }); },
    cabinet() { return request({ op: 'view', name: 'cabinet' }); },
    hill() { return request({ op: 'view', name: 'hill' }); },
    westWing() { return request({ op: 'view', name: 'westwing' }); },
    westWingOptions(kind, index) { return request({ op: 'view', name: 'westwingOptions', kind, index }); },
    press() { return request({ op: 'view', name: 'press' }); },
    terms() { return request({ op: 'view', name: 'terms' }); },
    verdict() { return request({ op: 'view', name: 'verdict' }); },
    residence() { return request({ op: 'view', name: 'residence' }); },
    /** The budget office's score of moving a policy some steps from where it stands. */
    score(policy, steps) { return request({ op: 'view', name: 'score', policy, steps }); },
    /** The budget office's score of one of the game's bills. */
    billScore(bill) { return request({ op: 'view', name: 'billScore', bill }); },
    decisions() { return request({ op: 'view', name: 'decisions' }).decisions; },
    /** Applies a command (in the save file's command format); returns the log lines it produced, newest first. */
    command(command) { return request({ op: 'command', command }).news; },
};
