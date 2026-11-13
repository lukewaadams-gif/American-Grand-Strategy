// The presidential seal, simplified as Resolute Desk draws it (ui/Oval.seal): a blue disc with a gold ring, an arc
// of thirteen stars, an eagle with spread wings and a shield. Used in the top bar, on the desk's kneehole door and,
// laid flat, in the rug.
import { s } from '../core/dom.js';

export interface SealColours { field: string; gold: string; eagle: string; shieldRed: string; shieldBlue: string; }

export const SEAL: SealColours = { field: '#22345e', gold: '#c9a54f', eagle: '#e3d6ad', shieldRed: '#9e2b36', shieldBlue: '#363a64' };

/** The seal as an SVG group centred on (cx, cy) with radius r. */
export function seal(cx: number, cy: number, r: number, c: SealColours = SEAL): SVGGElement {
  const g = s('g');
  g.append(s('circle', { cx, cy, r, fill: c.field }),
    s('circle', { cx, cy, r: r * 0.92, fill: 'none', stroke: c.gold, 'stroke-width': Math.max(1, r * 0.08) }));
  let stars = '';
  for (let i = 0; i < 13; i++) {
    const a = -Math.PI / 2 + (i - 6) * 0.22, sx = cx + Math.cos(a) * r * 0.7, sy = cy + Math.sin(a) * r * 0.7, d = r * 0.05;
    stars += `M${(sx - d).toFixed(2)} ${sy.toFixed(2)}a${d.toFixed(2)} ${d.toFixed(2)} 0 1 0 ${(2 * d).toFixed(2)} 0a${d.toFixed(2)} ${d.toFixed(2)} 0 1 0 ${(-2 * d).toFixed(2)} 0`;
  }
  g.append(s('path', { d: stars, fill: c.gold }));
  const p = (x: number, y: number) => `${(cx + r * x).toFixed(2)} ${(cy + r * y).toFixed(2)}`;
  g.append(s('path', {
    d: `M${p(-0.62, -0.05)}C${p(-0.4, -0.35)} ${p(-0.15, -0.2)} ${p(0, -0.05)}C${p(0.15, -0.2)} ${p(0.4, -0.35)} ${p(0.62, -0.05)}`
      + `C${p(0.35, 0.05)} ${p(0.2, 0.1)} ${p(0, 0.1)}C${p(-0.2, 0.1)} ${p(-0.35, 0.05)} ${p(-0.62, -0.05)}Z`, fill: c.eagle,
  }));
  g.append(s('rect', { x: cx - r * 0.16, y: cy - r * 0.08, width: r * 0.32, height: r * 0.42, rx: r * 0.05, fill: c.shieldRed }),
    s('rect', { x: cx - r * 0.16, y: cy - r * 0.08, width: r * 0.32, height: r * 0.12, fill: c.shieldBlue }));
  return g;
}

/** A standalone seal for the interface (decorative). */
export function sealIcon(size: number): SVGSVGElement {
  const svg = s('svg', { viewBox: '0 0 44 44', width: size, height: size, 'aria-hidden': 'true', focusable: 'false', class: 'seal-icon' });
  svg.append(seal(22, 22, 21));
  return svg;
}
