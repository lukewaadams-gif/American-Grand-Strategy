// How numbers read on screen. A margin is always written with its side ("D+2.7"), never by colour alone.
export function margin(dMinusR, digits = 1) {
    if (Math.abs(dMinusR) < 0.05)
        return 'Even';
    return (dMinusR > 0 ? 'D+' : 'R+') + Math.abs(dMinusR).toFixed(digits);
}
export function signed(v, digits = 1) {
    const s = v.toFixed(digits);
    return v > 0 ? '+' + s : Math.abs(v) < Math.pow(10, -digits) / 2 ? (0).toFixed(digits) : s;
}
/** A probability as a person reads it: never "0%" or "100%". */
export function chance(p) {
    if (p < 0.005)
        return '<1%';
    if (p > 0.995)
        return '>99%';
    return Math.round(p * 100) + '%';
}
export function money(millions) {
    return '$' + Math.round(millions) + 'M';
}
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
/** "2028-09-11" as "Sep 11" (or "Sep 11, 2028"). */
export function day(iso, year = false) {
    const [y, m, d] = iso.split('-').map(Number);
    return MONTHS[(m ?? 1) - 1] + ' ' + d + (year ? ', ' + y : '');
}
