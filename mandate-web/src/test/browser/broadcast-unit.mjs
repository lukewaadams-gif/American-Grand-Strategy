// The election-night script (presidency/broadcast.ts) against synthetic results: every race is called for its real
// winner and never before its polls close, the projection comes when the winner reaches 270, the final count is the
// result, and the same result always gives the same night. Usage: node broadcast-unit.mjs [path to target/preview]
import { pathToFileURL } from 'node:url';
const root = process.argv[2] || '../../../target/preview';
const { plan, tally, isCalled, shown, counted } = await import(pathToFileURL(root + '/app/presidency/broadcast.js').href);
const codes = 'AL AK AZ AR CA CO CT DE DC FL GA HI ID IL IN IA KS KY LA ME MD MA MI MN MS MO MT NE NV NH NJ NM NY NC ND OH OK OR PA RI SC SD TN TX UT VT VA WA WV WI WY'.split(' ');
const evs = [9,3,11,6,54,10,7,3,3,30,16,4,4,19,11,6,6,8,8,4,10,11,15,10,6,10,4,5,6,4,14,5,28,16,3,17,7,8,19,4,9,3,11,40,6,3,13,12,4,10,3];
let fails = 0, nights = 0;
const fail = (w) => { fails++; console.log('FAIL', w); };
for (let k = 0; k < 400; k++) {
  let x = 12345 + k * 7919; const rnd = () => ((x = (x * 1103515245 + 12345) >>> 0) / 4294967296);
  const swing = (rnd() - 0.5) * 24;
  const states = codes.map((c, i) => ({ code: c, name: c, ev: evs[i], baseline: (rnd() - 0.5) * 50, margin: 0 }));
  states.forEach(s => { s.margin = Math.round((s.baseline * 0.9 + swing + (rnd() - 0.5) * 8) * 100) / 100; if (Math.abs(s.margin) < 0.05) s.margin = 0.05; });
  const dem = states.reduce((a, s) => a + (s.margin >= 0 ? s.ev : 0), 0), rep = 538 - dem;
  if (dem === rep) continue;
  const winner = dem > rep ? 'D' : 'R';
  const v = { year: 2028, pending: true, electionDay: '2028-11-07', result: { dem, rep, popular: swing, winner, tipping: 'PA' }, states,
    player: { name: 'A', id: 'a', party: 'D' }, opponent: { name: 'B', id: 'b', party: 'R' }, won: winner === 'D', next: 'transition', postMortem: [], congress: '' };
  const n = plan(v), n2 = plan(v);
  nights++;
  if (JSON.stringify(n.beats) !== JSON.stringify(n2.beats)) fail(`${k}: the same result gave a different night`);
  for (const l of n.lines) {
    if (l.callAt < l.close) fail(`${k}: ${l.code} called before its polls closed`);
    const s = shown(l, l.callAt);
    if (l.callAt > l.close && s !== null && Math.sign(s) !== Math.sign(l.margin) && counted(l, l.callAt) < 1) fail(`${k}: ${l.code} called while the other side led the count`);
  }
  const end = n.beats[n.beats.length - 1].t, t = tally(n, v, end);
  if (t.dem !== dem || t.rep !== rep) fail(`${k}: the final count ${t.dem}-${t.rep} is not the result ${dem}-${rep}`);
  if (Math.abs(t.national - swing) > 1e-6) fail(`${k}: the final national margin ${t.national} is not the popular vote ${swing}`);
  const proj = n.beats.find(b => b.kind === 'projection');
  if (!proj) fail(`${k}: no projection`);
  else {
    const before = tally(n, v, proj.t);
    if ((winner === 'D' ? before.dem : before.rep) < 270) fail(`${k}: projected before 270`);
    const just = tally(n, v, proj.t - 1);
    if ((winner === 'D' ? just.dem : just.rep) >= 270) fail(`${k}: the projection came late`);
  }
  if (n.beats.some((b, i) => i && b.t < n.beats[i - 1].t)) fail(`${k}: reports out of order`);
}
console.log(`${nights} nights checked`);
console.log(fails ? fails + ' FAILURES' : 'ALL PASS');
process.exit(fails ? 1 : 0);
