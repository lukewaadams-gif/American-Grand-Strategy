// Settings that last between games, and the sound: an original score generated as you play, plus a few table sounds.
import { el, $, custom } from './kit';

const KEY = 'ags-settings';
function load() { try { return JSON.parse(localStorage.getItem(KEY) || '{}'); } catch { return {}; } }
export const Settings = Object.assign({ sound: false, reduce: false, patterns: false, ranges: true, text: 100, you: '#2f5d8c', them: '#a3342b' }, load());
export function saveSettings() { try { const { you, them, ...rest } = Settings as any; localStorage.setItem(KEY, JSON.stringify(rest)); } catch { /* storage unavailable */ } }
export function applySettings() { document.documentElement.classList.toggle('reduce-motion', !!Settings.reduce); document.documentElement.style.fontSize = Settings.text + '%'; }

let ctx: AudioContext | null = null, master: GainNode | null = null, timer: any = null, step = 0;
const CHORDS = [[130.8, 196, 261.6, 329.6], [110, 164.8, 220, 261.6], [116.5, 174.6, 233.1, 293.7], [98, 146.8, 196, 246.9]];
function ensure() { if (ctx) return true; try { ctx = new AudioContext(); } catch { return false; } master = ctx.createGain(); master.gain.value = .45;
  const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 1700; const d = ctx.createDelay(); d.delayTime.value = .45; const fb = ctx.createGain(); fb.gain.value = .28;
  master.connect(lp); lp.connect(ctx.destination); lp.connect(d); d.connect(fb); fb.connect(d); d.connect(ctx.destination); return true; }
function v(f: number, t: number, dur: number, type: OscillatorType = 'triangle', vol = .04) { if (!ctx || !master) return; const o = ctx.createOscillator(), g = ctx.createGain(); o.type = type; o.frequency.value = f; o.detune.value = (Math.random() - .5) * 7;
  g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(vol, t + Math.min(1.6, dur * .3)); g.gain.linearRampToValueAtTime(0, t + dur); o.connect(g); g.connect(master); o.start(t); o.stop(t + dur + .1); }
function bar() { if (!ctx) return; const t = ctx.currentTime + .05, ch = CHORDS[step++ % 4]; ch.forEach(f => v(f, t, 7)); v(ch[0] / 2, t, 7, 'sine', .055);
  for (let i = 0; i < 4; i++) if (Math.random() < .55) v(ch[1 + Math.floor(Math.random() * 3)] * 2, t + .8 + i * 1.6, 1.4, 'sine', .022); }
export const sound = {
  start() { if (!ensure()) return; ctx!.resume(); if (!timer) { bar(); timer = setInterval(bar, 7000); } },
  stop() { clearInterval(timer); timer = null; ctx?.suspend(); },
  fx(f: (t: number) => void) { if (!Settings.sound || !ctx) return; f(ctx.currentTime); },
  click() { this.fx(t => v(660, t, .08, 'sine', .04)); },
  turn() { this.fx(t => { v(220, t, 1, 'sine', .07); v(330, t + .12, 1.2, 'sine', .05); }); },
  good() { this.fx(t => [392, 494, 587].forEach((f, i) => v(f, t + i * .1, .6, 'sine', .05))); },
  bad() { this.fx(t => { v(277, t, .7, 'sine', .06); v(207, t + .15, 1, 'sine', .06); }); },
  boom() { this.fx(t => { v(55, t, 1.2, 'sine', .22); v(82, t + .02, 1, 'triangle', .08); }); },
  dice() { this.fx(t => { for (let i = 0; i < 6; i++) v(900 + Math.random() * 600, t + i * .06, .05, 'square', .012); }); },
};
export function setSound(on: boolean) { Settings.sound = on; saveSettings(); on ? sound.start() : sound.stop(); }

export function settingsCard(after?: () => void) {
  const node = el(`<div class="ecard card paper" style="width:min(480px,100%)"><div class="card-in"><div class="ebody"><div class="kicker">Settings</div><h2>Settings</h2>
    <label class="check"><input type="checkbox" id="s-sound" ${Settings.sound ? 'checked' : ''}> The score and the table's sounds</label>
    <label class="check"><input type="checkbox" id="s-patterns" ${Settings.patterns ? 'checked' : ''}> Patterns on territory (for colour-blind players)</label>
    <label class="check"><input type="checkbox" id="s-ranges" ${Settings.ranges ? 'checked' : ''}> Show forecast numbers and ranges</label>
    <label class="check"><input type="checkbox" id="s-motion" ${Settings.reduce ? 'checked' : ''}> Reduce motion</label>
    <div class="field"><label for="s-text">Text size</label><select class="input" id="s-text">${[90, 100, 112, 125].map(n => `<option value="${n}" ${Settings.text === n ? 'selected' : ''}>${n}%</option>`).join('')}</select></div>
    <p class="note">The score is generated as you play; nothing is recorded.</p><div class="efoot"><span></span><button class="btn red" type="button">Done</button></div></div></div></div>`);
  const m = custom(node, 'Settings');
  ($('#s-sound', node) as HTMLInputElement).onchange = e => setSound((e.target as HTMLInputElement).checked);
  ($('#s-patterns', node) as HTMLInputElement).onchange = e => { Settings.patterns = (e.target as HTMLInputElement).checked; saveSettings(); };
  ($('#s-ranges', node) as HTMLInputElement).onchange = e => { Settings.ranges = (e.target as HTMLInputElement).checked; saveSettings(); };
  ($('#s-motion', node) as HTMLInputElement).onchange = e => { Settings.reduce = (e.target as HTMLInputElement).checked; saveSettings(); applySettings(); };
  ($('#s-text', node) as HTMLSelectElement).onchange = e => { Settings.text = +(e.target as HTMLSelectElement).value; saveSettings(); applySettings(); };
  $('.btn', node).onclick = () => { m.close(); after?.(); };
}
