// The few DOM helpers the interface uses: build elements and SVG with attributes and children. No framework.
function apply(el, attrs) {
    if (!attrs)
        return;
    for (const [k, v] of Object.entries(attrs)) {
        if (v === null || v === undefined || v === false)
            continue;
        if (typeof v === 'function')
            el.addEventListener(k.replace(/^on/, '').toLowerCase(), v);
        else if (k === 'class')
            el.setAttribute('class', String(v));
        else if (k === 'style')
            el.setAttribute('style', String(v));
        else
            el.setAttribute(k, v === true ? '' : String(v));
    }
}
function append(el, children) {
    for (const c of children) {
        if (c === null || c === undefined || c === false)
            continue;
        el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
}
/** An HTML element: h('div', {class: 'card'}, 'text', child). Attributes named on* become listeners. */
export function h(tag, attrs = null, ...children) {
    const el = document.createElement(tag);
    apply(el, attrs);
    append(el, children);
    return el;
}
const SVG_NS = 'http://www.w3.org/2000/svg';
/** An SVG element. */
export function s(tag, attrs = null, ...children) {
    const el = document.createElementNS(SVG_NS, tag);
    apply(el, attrs);
    append(el, children);
    return el;
}
/** Replaces an element's children. */
export function fill(el, ...children) {
    el.replaceChildren();
    append(el, children);
}
/** True when motion should be kept to a minimum: the player's setting or the system's. */
export function reducedMotion() {
    return document.documentElement.dataset['motion'] === 'reduce' || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}
/** Counts a number up or down to its new value (immediately under reduced motion). */
export function countTo(el, from, to, format, ms = 700) {
    if (reducedMotion() || from === to) {
        el.textContent = format(to);
        return;
    }
    const start = performance.now();
    const step = (now) => {
        const t = Math.min(1, (now - start) / ms), e = 1 - Math.pow(1 - t, 3);
        el.textContent = format(from + (to - from) * e);
        if (t < 1)
            requestAnimationFrame(step);
    };
    requestAnimationFrame(step);
}
