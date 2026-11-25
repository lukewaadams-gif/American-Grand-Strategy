// Printed card art: portraits and event illustrations in an engraved style, sepia ink hatching on cream card stock with
// one spot colour, as a premium board game prints its cards. SVG, procedural, from a seed.
const INK = '#2b2118', CARD = '#efe6d0';
let uid = 0;
function hashStr(s: string) { let x = 2166136261; for (const c of s) { x ^= c.charCodeAt(0); x = Math.imul(x, 16777619); } return x >>> 0; }
function seeded(seed: string) { let a = hashStr(seed); return () => { a = (a + 0x6D2B79F5) | 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; }; }

/** Hatch patterns: h45 (light), h135, x (cross-hatch), dense; ids are unique per drawing. */
function hatches(id: string) {
  const p = (n: string, a: number, gap: number, w: number, cross = false) => `<pattern id="${id}${n}" width="${gap}" height="${gap}" patternUnits="userSpaceOnUse" patternTransform="rotate(${a})"><line x1="0" y1="0" x2="0" y2="${gap}" stroke="${INK}" stroke-width="${w}"/>${cross ? `<line x1="0" y1="${gap / 2}" x2="${gap}" y2="${gap / 2}" stroke="${INK}" stroke-width="${w}"/>` : ''}</pattern>`;
  return `<defs>${p('a', 45, 2.2, .45)}${p('b', -45, 2.2, .45)}${p('x', 45, 2, .45, true)}${p('d', 30, 1.3, .55, true)}${p('f', 60, 3.2, .3)}</defs>`;
}

/** An engraved portrait on an oval vignette. opts: spot (party colour), pres ('m'|'f'), age. */
export function portrait(seed: string, opts: { spot?: string; pres?: string; age?: number; title?: string } = {}): string {
  const r = seeded('engr:' + seed), id = 'pe' + (++uid), spot = opts.spot || '#2f5d8c', pres = opts.pres || (r() < .5 ? 'm' : 'f'), age = opts.age || 45 + Math.floor(r() * 25);
  const w = .92 + r() * .14, jaw = r(), hairK = Math.floor(r() * 4), glasses = r() < .2, grey = age > 58;
  const hairM = ['M34 46 C33 30 40 22 50 22 C60 22 67 30 66 46 C64 37 60 32 50 31 C40 32 36 37 34 46 Z', 'M33 47 C31 28 41 20 52 21 C63 22 69 31 67 47 C66 38 62 33 55 31 C49 34 41 33 36 37 Z',
    'M35 40 C35 27 42 23 50 23 C58 23 65 27 65 40 C62 33 57 30 50 30 C43 30 38 33 35 40 Z', 'M34 45 C33 37 35 31 39 27 C42 31 44 33 46 34 C41 36 37 40 34 45 Z M66 45 C67 37 65 31 61 27 C58 31 56 33 54 34 C59 36 63 40 66 45 Z'];
  const hairF = ['M30 66 C26 34 36 20 50 20 C64 20 74 34 70 66 L65 64 C67 44 62 33 50 31 C38 33 33 44 35 64 Z', 'M31 80 C25 36 35 19 50 19 C65 19 75 36 69 80 L64 78 C67 50 62 33 50 31 C38 33 33 50 36 78 Z',
    'M34 45 C33 29 40 21 50 21 C60 21 67 29 66 45 C64 36 59 31 50 30 C41 31 36 36 34 45 Z M43 17 C45 10 55 10 57 17 C54 20 46 20 43 17 Z', 'M28 58 C22 30 34 16 50 16 C66 16 78 30 72 58 C69 42 63 33 50 32 C37 33 31 42 28 58 Z'];
  const hair = (pres === 'f' ? hairF : hairM)[hairK];
  const face = `M36 46 C36 31 42 25 50 25 C58 25 64 31 64 46 C64 ${58 + jaw * 3} 59 ${66 + jaw * 2} 50 ${70 + jaw * 2} C41 ${66 + jaw * 2} 36 ${58 + jaw * 3} 36 46 Z`;
  return `<svg viewBox="0 0 100 120" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="${opts.title || 'Portrait'}">${hatches(id)}
<clipPath id="${id}o"><ellipse cx="50" cy="58" rx="44" ry="54"/></clipPath><clipPath id="${id}r"><rect x="51" y="0" width="60" height="120"/></clipPath>
<rect width="100" height="120" fill="${CARD}"/><rect width="100" height="120" fill="url(#${id}f)" opacity=".55"/>
<g clip-path="url(#${id}o)"><rect width="100" height="120" fill="${CARD}"/>
<g transform="translate(50 0) scale(${w} 1) translate(-50 0)">
${pres === 'f' && hairK < 2 ? `<path d="${hair}" fill="url(#${id}d)" stroke="${INK}" stroke-width=".6"/>` : ''}
<path d="M8 124 L12 98 Q18 84 38 80 L62 80 Q82 84 88 98 L92 124 Z" fill="url(#${id}x)" stroke="${INK}" stroke-width=".8"/>
<path d="M62 80 Q82 84 88 98 L92 124 L60 124 Z" fill="url(#${id}d)" opacity=".7"/>
<path d="M42 80 L50 96 L58 80 Z" fill="${CARD}" stroke="${INK}" stroke-width=".6"/>
${pres === 'm' ? `<path d="M48.3 82 L51.7 82 L53 95 L50 99 L47 95 Z" fill="${spot}" stroke="${INK}" stroke-width=".5"/>` : `<circle cx="50" cy="86" r="2.4" fill="${spot}" stroke="${INK}" stroke-width=".5"/>`}
<path d="M44 66 L44 81 Q50 85 56 81 L56 66" fill="${CARD}" stroke="${INK}" stroke-width=".6"/><path d="M50 66 L56 66 L56 81 Q53 83 50 83 Z" fill="url(#${id}a)"/>
<ellipse cx="35.2" cy="48" rx="2.8" ry="4.4" fill="${CARD}" stroke="${INK}" stroke-width=".6"/><ellipse cx="64.8" cy="48" rx="2.8" ry="4.4" fill="url(#${id}a)" stroke="${INK}" stroke-width=".6"/>
<path d="${face}" fill="${CARD}" stroke="${INK}" stroke-width=".9"/>
<path d="${face}" fill="url(#${id}a)" clip-path="url(#${id}r)" opacity=".85"/>
<path d="M40 41.5 Q43.5 39.6 47 41" stroke="${INK}" stroke-width="1.3" fill="none" stroke-linecap="round"/><path d="M53 41 Q56.5 39.6 60 41.5" stroke="${INK}" stroke-width="1.3" fill="none" stroke-linecap="round"/>
<path d="M40.5 46 Q43.5 44.2 46.5 46 Q43.5 47.3 40.5 46 Z" fill="${INK}"/><path d="M53.5 46 Q56.5 44.2 59.5 46 Q56.5 47.3 53.5 46 Z" fill="${INK}"/>
<path d="M50.3 47 L48.2 55.5 Q50 56.6 52.2 55.6" stroke="${INK}" stroke-width=".8" fill="none"/>
<path d="M45 61 Q50 ${62.5 + r() * .8} 55 61" stroke="${INK}" stroke-width="1" fill="none" stroke-linecap="round"/>
${age > 55 ? `<path d="M43 35.5 Q50 34 57 35.5 M44.6 57 Q43.4 60 45.2 62.5 M55.4 57 Q56.6 60 54.8 62.5" stroke="${INK}" stroke-width=".45" fill="none"/>` : ''}
${glasses ? `<g fill="none" stroke="${INK}" stroke-width=".9"><rect x="39" y="42.5" width="9" height="6.5" rx="2"/><rect x="52" y="42.5" width="9" height="6.5" rx="2"/><path d="M48 45 Q50 44 52 45"/></g>` : ''}
${pres === 'f' && hairK < 2 ? '' : `<path d="${hair}" fill="${grey ? `url(#${id}f)` : `url(#${id}d)`}" stroke="${INK}" stroke-width=".7"/>`}
</g></g>
<ellipse cx="50" cy="58" rx="44" ry="54" fill="none" stroke="${INK}" stroke-width="1.4"/><ellipse cx="50" cy="58" rx="46.5" ry="56.5" fill="none" stroke="${INK}" stroke-width=".4"/>
</svg>`;
}

/** Event illustrations (viewBox 300 × 130), engraved, with a spot colour. */
export function scene(kind: string, spot = '#2f5d8c', spot2 = '#a3342b'): string {
  const id = 'sc' + (++uid), r = seeded('scene:' + kind);
  const sky = `<rect width="300" height="130" fill="${CARD}"/><rect width="300" height="80" fill="url(#${id}f)" opacity=".7"/>`;
  const ground = (y: number) => `<path d="M0 ${y} H300 V130 H0 Z" fill="url(#${id}a)" opacity=".8"/><line x1="0" y1="${y}" x2="300" y2="${y}" stroke="${INK}" stroke-width=".8"/>`;
  const capitol = (x: number, y: number, s: number) => `<g transform="translate(${x} ${y}) scale(${s})" stroke="${INK}" stroke-width=".8"><rect x="-60" y="0" width="120" height="26" fill="${CARD}"/>${Array.from({ length: 11 }, (_, i) => `<line x1="${-52 + i * 10.4}" y1="3" x2="${-52 + i * 10.4}" y2="24" />`).join('')}<rect x="-40" y="-10" width="80" height="10" fill="url(#${id}a)"/><path d="M-26 -10 Q-26 -42 0 -45 Q26 -42 26 -10 Z" fill="${CARD}"/><path d="M0 -45 Q26 -42 26 -10 L0 -10 Z" fill="url(#${id}x)"/><rect x="-4" y="-58" width="8" height="13" fill="${CARD}"/><path d="M0 -68 L3 -58 H-3 Z" fill="${INK}"/></g>`;
  const crowd = (y0: number, n: number) => Array.from({ length: n }, () => { const x = r() * 300, y = y0 + r() * (130 - y0); return `<circle cx="${x.toFixed(0)}" cy="${y.toFixed(0)}" r="${(2 + r() * 1.5).toFixed(1)}" fill="url(#${id}x)" stroke="${INK}" stroke-width=".3"/>`; }).join('');
  const S: Record<string, () => string> = {
    dawn: () => sky + `<circle cx="150" cy="96" r="42" fill="${spot2}" opacity=".35"/>${Array.from({ length: 13 }, (_, i) => { const a = Math.PI + i * Math.PI / 12; return `<line x1="${150 + Math.cos(a) * 48}" y1="${96 + Math.sin(a) * 48}" x2="${150 + Math.cos(a) * 80}" y2="${96 + Math.sin(a) * 80}" stroke="${INK}" stroke-width=".6"/>`; }).join('')}` + ground(96) + capitol(150, 70, .7),
    capitol: () => sky + ground(104) + capitol(150, 78, .9) + `<rect x="92" y="18" width="2" height="34" fill="${INK}"/><path d="M94 18 H112 V28 H94 Z" fill="${spot}" stroke="${INK}" stroke-width=".5"/>`,
    ledger: () => sky + `<g transform="rotate(-6 150 70)"><rect x="70" y="20" width="160" height="100" fill="${CARD}" stroke="${INK}"/><text x="150" y="38" text-anchor="middle" font-family="UnifrakturMaguntia, 'Old English Text MT', serif" font-size="15" fill="${INK}">The Evening Ledger</text><line x1="78" y1="44" x2="222" y2="44" stroke="${INK}"/><rect x="80" y="50" width="140" height="12" fill="${INK}"/>${Array.from({ length: 6 }, (_, i) => `<line x1="80" y1="${70 + i * 7}" x2="${150 - (i % 2) * 8}" y2="${70 + i * 7}" stroke="${INK}" stroke-width=".7"/><line x1="158" y1="${70 + i * 7}" x2="${220 - (i % 3) * 6}" y2="${70 + i * 7}" stroke="${INK}" stroke-width=".7"/>`).join('')}</g>`,
    ticker: () => sky + `${Array.from({ length: 8 }, (_, i) => `<line x1="20" x2="280" y1="${20 + i * 13}" y2="${20 + i * 13}" stroke="${INK}" stroke-width=".25"/>`).join('')}<polyline points="20,110 60,100 95,86 125,66 150,40 168,26 186,48 205,78 235,96 280,106" fill="none" stroke="${spot2}" stroke-width="3"/><polyline points="20,110 60,100 95,86 125,66 150,40 168,26" fill="none" stroke="${INK}" stroke-width="1.2"/><circle cx="168" cy="26" r="3.5" fill="${spot2}" stroke="${INK}"/>`,
    snow: () => sky + `<path d="M0 98 Q80 86 150 96 Q220 106 300 92 V130 H0 Z" fill="${CARD}" stroke="${INK}"/><g stroke="${INK}" stroke-width=".8" fill="url(#${id}x)"><rect x="120" y="58" width="44" height="38"/><path d="M114 60 L142 38 L170 60 Z"/><rect x="136" y="18" width="12" height="26"/><path d="M142 4 L149 20 H135 Z" fill="${INK}"/><rect x="190" y="72" width="40" height="26"/><path d="M184 74 L210 56 L236 74 Z"/></g><g fill="${spot}" stroke="${INK}" stroke-width=".4"><rect x="128" y="66" width="7" height="9"/><rect x="150" y="66" width="7" height="9"/></g>${Array.from({ length: 40 }, () => `<circle cx="${(r() * 300).toFixed(0)}" cy="${(r() * 90).toFixed(0)}" r=".9" fill="${CARD}" stroke="${INK}" stroke-width=".3"/>`).join('')}`,
    convention: () => sky + `<path d="M0 40 H300" stroke="${INK}"/>${Array.from({ length: 12 }, (_, i) => `<path d="M${i * 26} 0 L${i * 26 + 13} 40 L${i * 26 + 26} 0" fill="${i % 2 ? spot : CARD}" stroke="${INK}" stroke-width=".5"/>`).join('')}<rect x="118" y="62" width="64" height="26" fill="url(#${id}x)" stroke="${INK}"/><rect x="140" y="52" width="20" height="10" fill="${CARD}" stroke="${INK}"/>` + crowd(86, 160),
    debate: () => sky + ground(100) + `<rect x="70" y="58" width="34" height="42" fill="url(#${id}x)" stroke="${INK}"/><rect x="66" y="54" width="42" height="5" fill="${spot}" stroke="${INK}" stroke-width=".5"/><rect x="196" y="58" width="34" height="42" fill="url(#${id}x)" stroke="${INK}"/><rect x="192" y="54" width="42" height="5" fill="${spot2}" stroke="${INK}" stroke-width=".5"/><circle cx="150" cy="44" r="26" fill="none" stroke="${INK}"/><circle cx="150" cy="44" r="21" fill="url(#${id}f)" stroke="${INK}" stroke-width=".5"/><text x="150" y="48" text-anchor="middle" font-family="'IM Fell English SC', Georgia, serif" font-size="11" fill="${INK}">DEBATE</text>`,
    oval: () => sky + `<ellipse cx="150" cy="112" rx="140" ry="22" fill="url(#${id}f)" stroke="${INK}"/><ellipse cx="150" cy="112" rx="38" ry="7" fill="${spot}" opacity=".5" stroke="${INK}" stroke-width=".5"/><rect x="40" y="14" width="34" height="70" fill="url(#${id}a)" stroke="${INK}"/><rect x="226" y="14" width="34" height="70" fill="url(#${id}a)" stroke="${INK}"/><rect x="116" y="70" width="68" height="28" fill="url(#${id}x)" stroke="${INK}"/><rect x="110" y="64" width="80" height="7" fill="${CARD}" stroke="${INK}"/>`,
    blackout: () => `<rect width="300" height="130" fill="url(#${id}d)"/><rect width="300" height="130" fill="${INK}" opacity=".35"/>${Array.from({ length: 14 }, (_, i) => { const h = 30 + r() * 60, x = i * 22; return `<rect x="${x}" y="${130 - h}" width="18" height="${h}" fill="${CARD}" stroke="${INK}"/>${Array.from({ length: Math.floor(h / 12) }, (_, k) => `<rect x="${x + 4}" y="${134 - h + k * 12}" width="4" height="5" fill="${r() < .12 ? '#f2c94c' : INK}"/>`).join('')}`; }).join('')}<path d="M220 10 L208 40 L218 40 L204 70" stroke="#f2c94c" stroke-width="3" fill="none"/>`,
    radar: () => `<rect width="300" height="130" fill="${CARD}"/>${Array.from({ length: 5 }, (_, i) => `<circle cx="150" cy="130" r="${25 + i * 25}" fill="none" stroke="${INK}" stroke-width=".6"/>`).join('')}<path d="M150 130 L250 40 A135 135 0 0 1 285 130 Z" fill="url(#${id}a)"/><line x1="150" y1="130" x2="250" y2="40" stroke="${INK}"/><circle cx="214" cy="78" r="4" fill="${spot2}" stroke="${INK}"/>`,
    globe: () => sky + `<circle cx="150" cy="68" r="52" fill="${CARD}" stroke="${INK}" stroke-width="1.2"/>${Array.from({ length: 5 }, (_, i) => `<ellipse cx="150" cy="68" rx="${52 - i * 11}" ry="52" fill="none" stroke="${INK}" stroke-width=".5"/>`).join('')}<path d="M98 68 H202 M104 42 H196 M104 94 H196" stroke="${INK}" stroke-width=".5"/><path d="M150 16 A52 52 0 0 1 150 120 Z" fill="url(#${id}a)"/><path d="M118 52 L130 46 L140 58 L128 66 Z M160 76 L176 70 L184 84 L170 92 Z" fill="${spot}" stroke="${INK}" stroke-width=".5"/>`,
    court: () => sky + `<g stroke="${INK}" fill="${CARD}"><path d="M150 14 L66 42 H234 Z" fill="url(#${id}a)"/><rect x="72" y="42" width="156" height="7"/>${Array.from({ length: 8 }, (_, i) => `<rect x="${80 + i * 19}" y="52" width="9" height="46" fill="url(#${id}f)"/>`).join('')}<rect x="62" y="98" width="176" height="7"/><rect x="52" y="105" width="196" height="8"/></g>`,
    sorrow: () => `<rect width="300" height="130" fill="url(#${id}d)"/><rect width="300" height="130" fill="${INK}" opacity=".25"/><rect x="120" y="20" width="22" height="110" fill="${CARD}" stroke="${INK}"/><rect x="158" y="20" width="22" height="110" fill="${CARD}" stroke="${INK}"/><path d="M0 120 H300" stroke="${INK}"/>`,
  };
  return `<svg class="scene" viewBox="0 0 300 130" xmlns="http://www.w3.org/2000/svg" preserveAspectRatio="xMidYMid slice" aria-hidden="true">${hatches(id)}${(S[kind] || S.capitol)()}</svg>`;
}
