/** Poll closing times, minutes after 6:00 PM Eastern (where a state closes at two times, the later one). */
const CLOSE = {
    GA: 60, IN: 60, KY: 60, SC: 60, VT: 60, VA: 60,
    NC: 90, OH: 90, WV: 90,
    AL: 120, CT: 120, DE: 120, DC: 120, FL: 120, IL: 120, ME: 120, MD: 120, MA: 120, MS: 120, MO: 120, NH: 120, NJ: 120,
    OK: 120, PA: 120, RI: 120, TN: 120,
    AR: 150,
    AZ: 180, CO: 180, KS: 180, LA: 180, MI: 180, MN: 180, NE: 180, NM: 180, NY: 180, ND: 180, SD: 180, TX: 180, WI: 180, WY: 180,
    IA: 240, MT: 240, NV: 240, UT: 240,
    CA: 300, HI: 300, ID: 300, OR: 300, WA: 300,
    AK: 420,
};
/** How long the count takes, in minutes to nearly all of it: states that count mail ballots slowly take longer. */
const SPAN = {
    FL: 150, NC: 180, GA: 200, OH: 180, IN: 150, KY: 150, SC: 170, TX: 200, VA: 220,
    PA: 420, MI: 330, WI: 320, NY: 330, ME: 360, NH: 260, MN: 280,
    AZ: 660, NV: 640, CA: 760, WA: 640, OR: 620, UT: 520, AK: 640, CO: 360, NM: 300,
};
function hash(s) { let x = 2166136261; for (const c of s) {
    x ^= c.charCodeAt(0);
    x = Math.imul(x, 16777619);
} return x >>> 0; }
const unit = (s) => (hash(s) % 10007) / 10007;
const sign = (m) => (m >= 0 ? 1 : -1);
/** The share of the expected vote counted at a time (minutes after 6 PM Eastern). */
export function counted(l, t) {
    if (t < l.close)
        return 0;
    const x = (t - l.close) / l.span;
    return x >= 1 ? 1 : 1 - Math.pow(1 - x, 2.3);
}
/** The D-minus-R margin in the vote counted so far: early returns lean one way, then settle on the final margin. */
export function shown(l, t) {
    const c = counted(l, t);
    if (c <= 0)
        return null;
    return l.margin + l.bias * Math.pow(1 - c, 1.4);
}
export function isCalled(l, t) { return t >= l.callAt; }
/** When a race is called: at poll closing for a safe state; for a close one, once enough is counted and the leader in the count is the winner. */
function callTime(l) {
    const m = Math.abs(l.margin);
    if (m >= 12)
        return l.close;
    if (m >= 7)
        return l.close + 20 + Math.round(unit(l.code + 'call') * 40);
    const need = m >= 4 ? 0.55 : m >= 2 ? 0.8 : m >= 1 ? 0.92 : m >= 0.4 ? 0.97 : 0.995;
    const line = { ...l, callAt: Infinity };
    for (let t = l.close; t <= l.close + l.span; t += 5) {
        const s = shown(line, t);
        if (counted(line, t) >= need && s !== null && sign(s) === sign(l.margin))
            return t + Math.round(unit(l.code + 'desk') * 10);
    }
    return l.close + l.span + 30;
}
/** The clock on the broadcast. */
export function clock(t) {
    const total = 18 * 60 + Math.round(t), day = Math.floor(total / (24 * 60)), mins = total % (24 * 60);
    const h = Math.floor(mins / 60), m = mins % 60, ap = h >= 12 ? 'PM' : 'AM', h12 = h % 12 === 0 ? 12 : h % 12;
    return `${day > 0 ? 'Wednesday ' : ''}${h12}:${String(m).padStart(2, '0')} ${ap} ET`;
}
const plural = (n, a, b) => (n === 1 ? a : b);
/** The whole night, from the result. */
export function plan(v) {
    const lines = v.states.map(s => {
        const base = {
            ...s, close: CLOSE[s.code] ?? 180, span: SPAN[s.code] ?? 240,
            // early returns: in-person and mail ballots are counted at different times, so the first numbers lean one way
            bias: (unit(s.code + v.year) * 2 - 1) * (Math.abs(s.margin) < 8 ? 7 : 4),
            battleground: Math.abs(s.margin) < 6 || Math.abs(s.baseline) < 5, weight: Math.max(1, s.ev - 2),
        };
        return { ...base, callAt: callTime(base) };
    });
    const name = (p) => (p === v.player.party ? v.player.name : v.opponent.name);
    const party = (m) => (m >= 0 ? 'D' : 'R');
    const label = (p) => `${name(p)} (${p})`;
    const ev = (t, p) => lines.reduce((a, l) => a + (isCalled(l, t) && party(l.margin) === p ? l.ev : 0), 0);
    const beats = [];
    const push = (t, kind, title, text, states = []) => beats.push({ t, kind, title, text, states });
    push(0, 'open', `Election night ${v.year}`, `${label('D')} against ${label('R')}. 270 electoral votes win the presidency. The first polls close at 7:00 Eastern.`);
    const closes = [...new Set(lines.map(l => l.close))].sort((a, b) => a - b);
    const callTimes = [...new Set(lines.map(l => l.callAt))].sort((a, b) => a - b);
    const ticks = [...new Set([...closes, ...callTimes, ...Array.from({ length: 48 }, (_, i) => i * 30)])].filter(t => t > 0).sort((a, b) => a - b);
    const leader = new Map();
    let last = 0, projected = false, projection = Infinity;
    const marks = [100, 200, 250];
    const winner = v.result.winner;
    for (const t of ticks) {
        // polls close, with the races called at once from the exit polls
        const closing = lines.filter(l => l.close === t);
        if (closing.length) {
            const now = closing.filter(l => l.callAt === t), early = closing.filter(l => l.callAt > t);
            const close = early.filter(l => l.battleground).map(l => l.name);
            push(t, 'close', `${clock(t)}: polls close in ${closing.length} ${plural(closing.length, 'state', 'states')}`, (now.length ? 'Projected at the close: ' + now.map(l => `${l.name} for ${name(party(l.margin))} (${l.ev})`).join(', ') + '. ' : '')
                + (close.length ? `Too close to call: ${close.join(', ')}.` : early.length ? 'Too early to call the rest.' : ''), closing.map(l => l.code));
        }
        // calls made after the polls closed
        const calls = lines.filter(l => l.callAt === t && l.close < t);
        const big = calls.filter(l => l.battleground || l.ev >= 10), small = calls.filter(l => !big.includes(l));
        for (const l of big) {
            const s = shown(l, t);
            push(t, 'call', `Projection: ${name(party(l.margin))} wins ${l.name}`, `${l.ev} electoral votes. With ${Math.round(counted(l, t) * 100)}% of the expected vote counted, ${name(party(l.margin))} leads by ${Math.abs(s ?? l.margin).toFixed(1)} points.`, [l.code]);
        }
        if (small.length)
            push(t, 'calls', `${clock(t)}: more projections`, small.map(l => `${l.name} for ${name(party(l.margin))} (${l.ev})`).join(', ') + '.', small.map(l => l.code));
        // leads that change hands in the count, in the battlegrounds still uncalled
        if (t % 30 === 0)
            for (const l of lines) {
                if (!l.battleground || isCalled(l, t))
                    continue;
                const s = shown(l, t);
                if (s === null || counted(l, t) < 0.12)
                    continue;
                const before = leader.get(l.code);
                if (before !== undefined && before !== sign(s))
                    push(t, 'lead', `The lead changes hands in ${l.name}`, `${name(party(s))} moves ahead by ${Math.abs(s).toFixed(1)} points with ${Math.round(counted(l, t) * 100)}% counted.`, [l.code]);
                leader.set(l.code, sign(s));
            }
        // milestones and the projection of the presidency
        for (const p of ['D', 'R']) {
            const a = ev(last, p), b = ev(t, p);
            for (const m of marks)
                if (a < m && b >= m && b < 270)
                    push(t, 'milestone', `${name(p)} passes ${m}`, `${name(p)} has ${b} electoral votes; ${270 - b} more win the presidency.`);
        }
        if (!projected && ev(t, winner) >= 270) {
            projected = true;
            projection = t;
            push(t, 'projection', `${name(winner)} is projected to win the presidency`, `With ${ev(t, winner)} electoral votes, ${label(winner)} ${v.year > 2028 && winner === v.player.party && v.won ? 'wins a second term' : `will be the ${v.year === 2028 ? '48th' : '49th'} president of the United States`}.`);
        }
        last = t;
    }
    const end = Math.max(...lines.map(l => l.callAt)) + 15;
    const pop = v.result.popular;
    push(end, 'final', 'Every state is called', `The final count: ${label('D')} ${v.result.dem}, ${label('R')} ${v.result.rep}. The tipping-point state was ${lines.find(l => l.code === v.result.tipping)?.name ?? v.result.tipping}.`);
    push(end, 'popular', 'The popular vote', `${pop >= 0 ? name('D') : name('R')} wins the popular vote by ${Math.abs(pop).toFixed(1)} points${(pop >= 0) !== (winner === 'D') ? ', and loses the Electoral College' : ''}.`);
    const me = v.player.name, them = v.opponent.name;
    const words = v.won ? (v.year > 2028 ? '"You gave us four more years. We will not waste a day of them."' : '"Tonight the country chose a new direction. Tomorrow we get to work."')
        : (v.year > 2028 ? '"The voters have spoken, and we accept their verdict. It has been the honour of my life to serve."' : '"The voters have spoken, and we accept their verdict. Our cause goes on."');
    push(end, 'speech', v.won ? (v.year > 2028 ? 'Four more years' : 'The victory speech') : 'The concession', v.won ? `${them} calls to concede. ${me} takes the stage before a cheering crowd: ${words}` : `${me} calls ${them} to concede, then speaks to supporters: ${words}`);
    push(end, 'analysis', 'The analysis', 'How the map was won, the closest states, and what changed since 2024.');
    beats.sort((a, b) => a.t - b.t);
    return { lines, beats, projection, winner, end };
}
/** Everything the screen shows at a time: called electoral votes, the count, and the national vote as counted. */
export function tally(n, v, t) {
    let dem = 0, rep = 0, w = 0, wCounted = 0, sumShown = 0, sumFinal = 0;
    const full = t >= n.end; // the final reports show the complete count
    for (const l of n.lines) {
        if (isCalled(l, t)) {
            if (l.margin >= 0)
                dem += l.ev;
            else
                rep += l.ev;
        }
        const c = full ? 1 : counted(l, t), s = full ? l.margin : shown(l, t);
        w += l.weight;
        wCounted += l.weight * c;
        if (s !== null) {
            sumShown += l.weight * c * s;
            sumFinal += l.weight * c * l.margin;
        }
    }
    const frac = w ? wCounted / w : 0;
    // the count's national margin, nudged toward the engine's popular vote as the count completes (it is exactly that at the end)
    const national = wCounted ? sumShown / wCounted + (v.result.popular - sumFinal / wCounted) * frac : 0;
    const share = (m, p) => 49 + (p === 'D' ? m : -m) / 2;
    return { dem, rep, counted: frac, national, votes: 158 * frac, demShare: share(national, 'D'), repShare: share(national, 'R') };
}
