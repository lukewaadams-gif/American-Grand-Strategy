let mod = null;
/** Loads the engine module once. */
export async function start(url = new URL('../../engine/engine.js', import.meta.url).href) {
    if (!mod)
        mod = (await import(url));
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
    /** The budget office's score of moving a policy some steps from where it stands. */
    score(policy, steps) { return request({ op: 'view', name: 'score', policy, steps }); },
    /** The budget office's score of one of the game's bills. */
    billScore(bill) { return request({ op: 'view', name: 'billScore', bill }); },
    decisions() { return request({ op: 'view', name: 'decisions' }).decisions; },
    /** Applies a command (in the save file's command format); returns the log lines it produced, newest first. */
    command(command) { return request({ op: 'command', command }).news; },
};
