// Writes mandate-core's data/state_centroids.csv: the area-weighted centroid (lat, lon) of each state's land, from
// us-atlas states-10m (US Census, public domain). Usage: node tools/state-centroids.cjs > ../mandate-core/src/main/resources/data/state_centroids.csv
const topo = require('us-atlas/states-10m.json');
const FIPS = {'01':'AL','02':'AK','04':'AZ','05':'AR','06':'CA','08':'CO','09':'CT','10':'DE','11':'DC','12':'FL','13':'GA','15':'HI','16':'ID','17':'IL','18':'IN','19':'IA','20':'KS','21':'KY','22':'LA','23':'ME','24':'MD','25':'MA','26':'MI','27':'MN','28':'MS','29':'MO','30':'MT','31':'NE','32':'NV','33':'NH','34':'NJ','35':'NM','36':'NY','37':'NC','38':'ND','39':'OH','40':'OK','41':'OR','42':'PA','44':'RI','45':'SC','46':'SD','47':'TN','48':'TX','49':'UT','50':'VT','51':'VA','53':'WA','54':'WV','55':'WI','56':'WY'};
const [sx, sy] = topo.transform.scale, [tx, ty] = topo.transform.translate;
const arcs = topo.arcs.map(a => { let x = 0, y = 0; return a.map(([dx, dy]) => { x += dx; y += dy; return [x * sx + tx, y * sy + ty]; }); });
const ring = r => { let pts = []; for (const i of r) { const a = i >= 0 ? arcs[i] : arcs[~i].slice().reverse(); pts = pts.concat(pts.length ? a.slice(1) : a); } return pts; };
const out = [];
for (const g of topo.objects.states.geometries) {
  const code = FIPS[g.id]; if (!code) continue;
  const polys = g.type === 'Polygon' ? [g.arcs] : g.arcs;
  let A = 0, X = 0, Y = 0;
  for (const p of polys) { const pts = ring(p[0]); for (let i = 0; i < pts.length - 1; i++) { const [x0, y0] = pts[i], [x1, y1] = pts[i + 1], c = x0 * y1 - x1 * y0; A += c; X += (x0 + x1) * c; Y += (y0 + y1) * c; } }
  out.push([code, (Y / (3 * A)).toFixed(3), (X / (3 * A)).toFixed(3)]);
}
out.sort((a, b) => a[0].localeCompare(b[0]));
console.log("# code,lat,lon"); console.log("# Area-weighted centroid of each state's land (degrees), from us-atlas 3.0.1 states-10m (US Census, public domain), written by"); console.log("# mandate-web/tools/state-centroids.cjs. Air Force One's flight times are measured from DC's."); for (const r of out) console.log(r.join(','));
