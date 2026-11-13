// The light through the Oval Office's south windows (Q30, new: Resolute Desk keeps one bright afternoon). The hour
// comes from the player's clock (or ?hour= for a fixed look); the season comes from the game's date, so a January in
// the game shows snow on the South Lawn whatever the month outside. Washington's sunrise and sunset by month set when
// the sun is up; the windows face south, so the morning sun comes in from the left (east) and the evening sun from the
// right, and the low winter sun reaches further into the room than the high summer sun.
import { mixHex } from './paint.js';
// Washington, D.C.: approximate sunrise and sunset by month (local clock time, daylight saving included), in hours.
const RISE = [7.4, 7.0, 7.2, 6.7, 6.0, 5.75, 5.9, 6.4, 6.9, 7.4, 6.8, 7.2];
const SET = [17.1, 17.7, 19.1, 19.7, 20.2, 20.6, 20.55, 20.05, 19.3, 18.6, 16.95, 16.85];
// how high the noon sun stands, relative to midsummer
const NOON = [0.42, 0.52, 0.66, 0.8, 0.92, 1.0, 0.97, 0.88, 0.74, 0.6, 0.48, 0.4];
const smooth = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
export function seasonOf(month) {
    return month === 12 || month <= 2 ? 'winter' : month <= 5 ? 'spring' : month <= 8 ? 'summer' : 'autumn';
}
/** The light for an hour of the day (0 to 24) in a month of the year (1 to 12). */
export function lightFor(hour, month) {
    const m = Math.max(1, Math.min(12, Math.round(month))) - 1;
    const h = ((hour % 24) + 24) % 24;
    const rise = RISE[m], set = SET[m], noon = (rise + set) / 2, half = (set - rise) / 2;
    // daylight rises over the hour around sunrise and falls over the hour around sunset
    const day = Math.min(smooth(rise - 0.5, rise + 0.7, h), 1 - smooth(set - 0.7, set + 0.5, h));
    const elev = Math.max(0, Math.cos(((h - noon) / half) * (Math.PI / 2))) * NOON[m];
    const twilight = Math.max(1 - Math.min(Math.abs(h - rise), Math.abs(h - set)) / 1.1, 0);
    const phase = day < 0.12 ? 'night' : twilight > 0.25 && day < 0.9 ? (h < noon ? 'dawn' : 'dusk') : 'day';
    const season = seasonOf(m + 1);
    const overcast = m === 0 || m === 1 || m === 11 ? 0.45 : m === 2 || m === 10 ? 0.2 : 0;
    // the sky: night blue, a warm horizon at the ends of the day, clear or overcast blue by day
    const daySky = overcast > 0.3 ? ['#93a4b5', '#bcc8d2', '#dfe5ea'] : ['#7aa6d1', '#b3cfe8', '#e2edf5'];
    const warmSky = h < noon ? ['#6f86b0', '#d9a7a0', '#f2c99a'] : ['#5f6f9e', '#d58f78', '#f0b779'];
    const nightSky = ['#0b1426', '#17243d', '#2a3550'];
    const warm = phase === 'dawn' || phase === 'dusk' ? Math.min(1, twilight * 1.4) : 0;
    const sky = [0, 1, 2].map(i => mixHex(mixHex(nightSky[i], daySky[i], day), warmSky[i], warm * 0.85));
    // the South Lawn's trees through the year
    const CANOPY = [
        ['#7d7468'], ['#7d7468'], ['#8f9a6c', '#7f8a62'], ['#93ae6c', '#e7c3cc', '#87a462'], ['#6f9a52', '#7ea65d'],
        ['#557f45', '#4c7640'], ['#527c43', '#4a733e'], ['#56793f', '#62803f'], ['#61803f', '#8a8a3f', '#5b7a3c'],
        ['#b8742f', '#c99a3a', '#9a4d2a', '#7f7a3a'], ['#8a6a44', '#7a5a3a', '#6f6a55'], ['#7d7468'],
    ];
    const bare = m === 0 || m === 1 || m === 11;
    const snow = m === 0 || m === 1;
    const lawnDay = snow ? ['#e6ebef', '#c9d2d9'] : m === 11 || m === 2 ? ['#9aa47a', '#7d8a5c'] : m === 10 ? ['#8f9a5e', '#6f7d48'] : ['#74a157', '#52823b'];
    const lawnNight = snow ? ['#4a5468', '#323b4d'] : ['#1f2e24', '#16221a'];
    const lawn = [mixHex(lawnNight[0], lawnDay[0], day), mixHex(lawnNight[1], lawnDay[1], day)];
    const canopy = CANOPY[m].map(c => mixHex('#141c18', c, 0.25 + 0.75 * day));
    // sunbeams: lean with the sun's bearing, reach further when the sun is low
    const bearing = Math.max(-1, Math.min(1, (h - noon) / half));
    const sun = day * Math.min(1, 0.35 + elev) * (1 - overcast * 0.75) * smooth(0.02, 0.18, elev);
    const beamColour = mixHex('#fff3d6', '#ffc98a', warm);
    const reach = 700 + (1 - Math.min(1, elev)) * 150;
    // the room darkens (a multiply: cool at night, warm at the ends of the day) as the daylight goes
    let tint = mixHex('#ffffff', '#434d68', Math.pow(1 - day, 1.3));
    if (warm > 0)
        tint = mixHex(tint, h < noon ? '#f2cdb8' : '#f0bf92', warm * 0.45);
    const lamps = Math.max(0.25, 1 - day * 0.8);
    const label = `${phase === 'day' ? (h < 12 ? 'Morning' : h < 17 ? 'Afternoon' : 'Evening') : phase === 'night' ? 'Night' : phase === 'dawn' ? 'Dawn' : 'Dusk'}, ${['winter', 'winter', 'early spring', 'spring', 'late spring', 'summer', 'summer', 'late summer', 'early autumn', 'autumn', 'late autumn', 'winter'][m]}`;
    // the outdoors keeps its own light: its colours are divided by the room's tint, which is multiplied over everything
    const out = (c) => unTint(c, tint);
    const clouds = warm > 0.2 ? mixHex('#ffffff', '#ffd2b0', warm) : phase === 'night' ? '#4a5670' : '#ffffff';
    return {
        hour: h, month: m + 1, phase, season, day, sun, sky: sky.map(out),
        clouds: { colour: out(clouds), alpha: phase === 'night' ? 0.3 : 0.55, count: overcast > 0.3 ? 7 : overcast > 0 ? 5 : 4 },
        canopy: canopy.map(out), bare, blossom: m === 3, lawn: lawn.map(out), snow,
        beam: { colour: beamColour, lean: -bearing * 130, reach },
        tint, lamps, monumentLit: day < 0.6, stars: day < 0.08, label,
    };
}
/** The colour that, multiplied by the tint, gives {@code c} (as near as eight bits allow). */
function unTint(c, tint) {
    const n = (x) => { const v = parseInt(x.slice(1), 16); return [(v >> 16) & 255, (v >> 8) & 255, v & 255]; };
    const a = n(c), b = n(tint);
    return '#' + a.map((v, i) => Math.round(Math.min(255, (v * 255) / Math.max(1, b[i]))).toString(16).padStart(2, '0')).join('');
}
/** The hour to light the room for: ?hour= if given (0 to 24), otherwise the player's clock. */
export function currentHour(params = new URLSearchParams(location.search)) {
    const q = params.get('hour');
    if (q !== null && q.trim() !== '' && !Number.isNaN(Number(q)))
        return Number(q);
    const d = new Date();
    return d.getHours() + d.getMinutes() / 60;
}
