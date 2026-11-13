// Quiet room sound (docs/PRESIDENCY_PORT.md: "quiet room sound, off by default, with a toggle in the menu"): made in the
// browser with Web Audio, no files. A room tone (softly filtered noise, the hum of a quiet building), a clock that
// ticks once every two seconds, and a soft chime when a week ends. Off until the player turns it on; the choice is
// remembered on this device. Nothing plays until a click turns it on, as browsers require.
type Ctx = AudioContext;

let ctx: Ctx | null = null;
let master: GainNode | null = null;
let tone: AudioBufferSourceNode | null = null;
let tick: number | null = null;
let on = false;

function stored(): boolean {
  try { return localStorage.getItem('ags-sound') === 'on'; } catch { return false; }
}
function remember(v: boolean): void {
  try { localStorage.setItem('ags-sound', v ? 'on' : 'off'); } catch { /* private window: the choice lasts this visit */ }
}

/** Whether the room's sound is on. */
export function soundOn(): boolean { return on; }

function context(): Ctx | null {
  if (ctx) return ctx;
  const A = (window as unknown as { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }).AudioContext
    ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!A) return null;
  ctx = new A();
  master = ctx.createGain();
  master.gain.value = 0;
  master.connect(ctx.destination);
  return ctx;
}

function startTone(c: Ctx): void {
  // two seconds of brown noise, looped, low-passed: a large quiet room
  const len = c.sampleRate * 2, buf = c.createBuffer(1, len, c.sampleRate), d = buf.getChannelData(0);
  let last = 0;
  for (let i = 0; i < len; i++) { const w = Math.random() * 2 - 1; last = (last + 0.02 * w) / 1.02; d[i] = last * 3.5; }
  tone = c.createBufferSource();
  tone.buffer = buf; tone.loop = true;
  const lp = c.createBiquadFilter();
  lp.type = 'lowpass'; lp.frequency.value = 320;
  const g = c.createGain();
  g.gain.value = 0.05;
  tone.connect(lp).connect(g).connect(master!);
  tone.start();
}

function click(freq: number, gain: number, dur: number): void {
  if (!ctx || !master || !on) return;
  const o = ctx.createOscillator(), g = ctx.createGain(), t = ctx.currentTime;
  o.type = 'sine'; o.frequency.value = freq;
  g.gain.setValueAtTime(gain, t);
  g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g).connect(master);
  o.start(t); o.stop(t + dur + 0.02);
}

/** Turns the room's sound on or off (from a click, so the browser lets it play). */
export function setSound(v: boolean): void {
  on = v;
  remember(v);
  const c = context();
  if (!c || !master) return;
  if (v) {
    void c.resume();
    if (!tone) startTone(c);
    if (tick === null) tick = window.setInterval(() => click(1400, 0.012, 0.03), 2000);
    master.gain.setTargetAtTime(0.6, c.currentTime, 0.4);
  } else {
    if (tick !== null) { window.clearInterval(tick); tick = null; }
    master.gain.setTargetAtTime(0, c.currentTime, 0.2);
  }
}

/** A soft two-note chime: a week ends, a vote is counted. */
export function chime(): void {
  if (!on) return;
  click(660, 0.05, 0.6);
  window.setTimeout(() => click(880, 0.04, 0.8), 140);
}

/** Whether the player turned sound on before, on this device (it still waits for a click to start). */
export function soundWanted(): boolean { return stored(); }

/** The menu entry: "Sound: off" / "Sound: on". */
export function soundMenuItem(after?: () => void): { label: string; detail: string; pick: () => void } {
  return {
    label: on ? 'Sound: on' : 'Sound: off',
    detail: on ? 'The room’s quiet sound. Choose to turn it off.' : 'A quiet room tone and a clock. Choose to turn it on.',
    pick: () => { setSound(!on); after?.(); },
  };
}
