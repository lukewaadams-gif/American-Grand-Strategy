// The campaign's colours: a broadcast decision desk, not a game. Muted party colours on a near-black desk, a
// neutral grey for toss-ups (not purple), and labels whose colour is chosen per fill so they read at 4.5:1 or
// better. Three palettes, as everywhere in the game: standard, high contrast, colour-blind safe (blue/orange).

export type PaletteName = 'standard' | 'high-contrast' | 'colour-blind';

interface Ramp { light: string; mid: string; solid: string; }
export interface CampaignPalette { d: Ramp; r: Ramp; toss: string; ink: string; paper: string; }

const PALETTES: Record<PaletteName, CampaignPalette> = {
  'standard': {
    d: { light: '#a3b8d6', mid: '#6385b8', solid: '#34588c' },
    r: { light: '#d9a8a1', mid: '#bb6c63', solid: '#8f3c34' },
    toss: '#8d8a92', ink: '#0b1018', paper: '#f3f5f9',
  },
  'high-contrast': {
    d: { light: '#86aef5', mid: '#3f7ae0', solid: '#1a4fb8' },
    r: { light: '#f4a39b', mid: '#e0544a', solid: '#b31e14' },
    toss: '#b0b0b0', ink: '#000000', paper: '#ffffff',
  },
  'colour-blind': {
    d: { light: '#a3b8d6', mid: '#6385b8', solid: '#34588c' },
    r: { light: '#f1c18f', mid: '#dd8a3d', solid: '#a85a16' },
    toss: '#8f8b95', ink: '#0b1018', paper: '#f3f5f9',
  },
};

let current: CampaignPalette = PALETTES['standard'];
export function usePalette(name: PaletteName): void { current = PALETTES[name] ?? PALETTES['standard']; }
export function palette(): CampaignPalette { return current; }

// ---- colour arithmetic ----------------------------------------------------------------------------------------

type RGB = [number, number, number];
function rgb(hex: string): RGB { const n = parseInt(hex.slice(1), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
function hex([r, g, b]: RGB): string { return '#' + [r, g, b].map(v => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, '0')).join(''); }
export function mix(a: string, b: string, t: number): string {
  const x = rgb(a), y = rgb(b);
  return hex([x[0] + (y[0] - x[0]) * t, x[1] + (y[1] - x[1]) * t, x[2] + (y[2] - x[2]) * t]);
}
function lum(c: string): number {
  return rgb(c).map(v => { const s = v / 255; return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4); })
    .reduce((acc, v, i) => acc + v * [0.2126, 0.7152, 0.0722][i]!, 0);
}
export function contrast(a: string, b: string): number {
  const la = lum(a), lb = lum(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}
/** Dark or light label text for a fill, whichever reads better. */
export function inkOn(fill: string): string {
  const p = current;
  return contrast(fill, p.ink) >= contrast(fill, p.paper) ? p.ink : p.paper;
}

// ---- scales ---------------------------------------------------------------------------------------------------

/** A D-minus-R margin as a colour: toss-up grey within a point, then light, mid and solid as the lead grows. */
export function marginColour(dMinusR: number): string {
  const p = current, m = Math.abs(dMinusR), ramp = dMinusR >= 0 ? p.d : p.r;
  if (m < 1) return legible(mix(p.toss, ramp.light, m));
  if (m < 6) return legible(mix(ramp.light, ramp.mid, (m - 1) / 5));
  return legible(mix(ramp.mid, ramp.solid, Math.min(1, (m - 6) / 8)));
}

/**
 * A fill whose label reads at 4.5:1: mid-tones, where neither dark nor light text quite gets there, are deepened a
 * step at a time toward the desk until light text does. (The axe audit found Florida's and New Jersey's labels at 4.2-4.4.)
 */
export function legible(fill: string): string {
  let c = fill;
  for (let i = 0; i < 24 && Math.max(contrast(c, current.ink), contrast(c, current.paper)) < 4.6; i++) c = mix(c, '#090d13', 0.05);
  return c;
}

/** A share leaning one way (-1 all Republican .. +1 all Democratic) on the same ramp. */
export function leanColour(dShare: number): string { return marginColour(dShare * 14); }

/** The ramp for a legend: colours from R+span to D+span. */
export function legendStops(span = 14, n = 15): string[] {
  const out: string[] = [];
  for (let i = 0; i < n; i++) out.push(marginColour(-span + (2 * span * i) / (n - 1)));
  return out;
}
