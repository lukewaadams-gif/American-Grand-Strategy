//! American Grand Strategy: the simulation. One entry point, `call`, takes a JSON request and returns a JSON reply;
//! the browser reaches it through three `extern "C"` functions (see `wasm` below), the tests call it directly.
//!
//! Requests (`op`):
//! - `load` {data, map}: the game's data files and the hex map, once.
//! - `new` {setup}: a new game.
//! - `cmd` {c, ...}: a player command (see `command`). Returns the effects to animate (`fx`).
//! - `view`: everything the interface shows.
//! - `reach` {unit}: where a unit can move this turn. `odds` {unit, target, action}: a contest's odds.
//! - `save`, `restore` {save}: a save is the setup plus the command log, replayed exactly.
pub mod data;
pub mod events;
pub mod game;
pub mod rng;
pub mod turn;
pub mod units;
pub mod view;

use data::{Data, Map, MapFile};
use game::{Game, Setup, YOU};
use serde_json::{json, Value};
use std::cell::RefCell;
use std::rc::Rc;

pub const SAVE_VERSION: u32 = 1;

#[derive(Default)]
pub struct Engine { pub data: Option<Rc<Data>>, pub map: Option<Rc<Map>>, pub game: Option<Game> }

thread_local! { static ENGINE: RefCell<Engine> = RefCell::new(Engine::default()); }

/// One request in, one reply out (both JSON).
pub fn call(req: &str) -> String { ENGINE.with(|e| handle(&mut e.borrow_mut(), req)).to_string() }

pub fn handle(e: &mut Engine, req: &str) -> Value {
    let r: Value = match serde_json::from_str(req) { Ok(v) => v, Err(err) => return json!({ "ok": false, "error": format!("bad request: {err}") }) };
    match dispatch(e, &r) { Ok(v) => v, Err(err) => json!({ "ok": false, "error": err }) }
}

fn dispatch(e: &mut Engine, r: &Value) -> Result<Value, String> {
    let op = r.get("op").and_then(|v| v.as_str()).ok_or("missing op")?;
    match op {
        "load" => {
            let data: Data = serde_json::from_value(r["data"].clone()).map_err(|x| format!("data: {x}"))?;
            let mf: MapFile = serde_json::from_value(r["map"].clone()).map_err(|x| format!("map: {x}"))?;
            let map = Map::new(mf, &data); e.data = Some(Rc::new(data)); e.map = Some(Rc::new(map)); Ok(json!({ "ok": true }))
        }
        "new" => {
            let setup: Setup = serde_json::from_value(r["setup"].clone()).map_err(|x| format!("setup: {x}"))?;
            let g = Game::new(setup, e.data.clone().ok_or("load first")?, e.map.clone().ok_or("load first")?)?; e.game = Some(g); Ok(json!({ "ok": true }))
        }
        "view" => Ok(e.game.as_ref().ok_or("no game")?.view()),
        "cmd" => {
            let g = e.game.as_mut().ok_or("no game")?; g.fx.clear();
            command(g, r)?; g.log.push(r.clone());
            Ok(json!({ "ok": true, "fx": std::mem::take(&mut g.fx) }))
        }
        "reach" => {
            let g = e.game.as_ref().ok_or("no game")?; let id = r["unit"].as_u64().ok_or("unit")? as u32; let u = g.unit(id).ok_or("no such unit")?;
            let (best, prev) = g.reach(u);
            let hexes: Vec<Value> = best.iter().enumerate().filter(|(i, m)| **m >= 0.0 && *i != u.hex).map(|(i, m)| json!([i, m, prev[i]])).collect();
            Ok(json!({ "ok": true, "from": u.hex, "hexes": hexes }))
        }
        "odds" => {
            let g = e.game.as_ref().ok_or("no game")?; let a = g.unit(r["unit"].as_u64().ok_or("unit")? as u32).ok_or("no such unit")?;
            let b = g.unit(r["target"].as_u64().ok_or("target")? as u32).ok_or("no such target")?; let ad = r["action"].as_str() == Some("attack_ad");
            let (p, am, bm) = g.contest_odds(a, b, ad); Ok(json!({ "ok": true, "p": p, "am": am, "bm": bm }))
        }
        "save" => {
            let g = e.game.as_ref().ok_or("no game")?;
            Ok(json!({ "ok": true, "save": { "version": SAVE_VERSION, "setup": g.setup, "log": g.log, "turn": g.turn, "label": format!("{} · {}", g.turn_def().title, g.turn_def().label),
                "leader": g.sides[YOU].leader.name, "party": g.sides[YOU].party, "ironman": g.setup.ironman } }))
        }
        "restore" => {
            let s = &r["save"]; if s["version"].as_u64() != Some(SAVE_VERSION as u64) { return Err("this save is from another version".into()); }
            let setup: Setup = serde_json::from_value(s["setup"].clone()).map_err(|x| format!("setup: {x}"))?;
            let mut g = Game::new(setup, e.data.clone().ok_or("load first")?, e.map.clone().ok_or("load first")?)?;
            for c in s["log"].as_array().cloned().unwrap_or_default() { command(&mut g, &c).map_err(|x| format!("the save does not replay: {x}"))?; g.log.push(c); }
            g.fx.clear(); e.game = Some(g); Ok(json!({ "ok": true }))
        }
        _ => Err(format!("unknown op {op}")),
    }
}

/// A player command. Every command that changes the game goes through here, so saves can replay it.
pub fn command(g: &mut Game, r: &Value) -> Result<(), String> {
    let c = r.get("c").and_then(|v| v.as_str()).ok_or("missing c")?;
    let s = |k: &str| r.get(k).and_then(|v| v.as_str()).map(|x| x.to_string()).ok_or(format!("missing {k}"));
    let n = |k: &str| r.get(k).and_then(|v| v.as_u64()).ok_or(format!("missing {k}"));
    let busy = g.pending.is_some() && !["choose", "vet"].contains(&c);
    if busy { return Err("answer the card first".into()); }
    match c {
        "move" => g.do_move(n("unit")? as u32, n("to")? as usize),
        "act" => { let id = n("unit")? as u32; let u = g.unit(id).ok_or("no such unit")?; if u.side != YOU { return Err("not your unit".into()); }
            let target = r.get("target").and_then(|v| v.as_i64()); g.act(id, &s("action")?, target) }
        "prod" => g.set_production(n("hq")? as usize, &s("kind")?, &s("id")?),
        "buy" => g.buy(n("hq")? as usize),
        "research" => g.research(&s("idea")?),
        "adopt" => g.adopt(&s("policy")?),
        "pledge" => g.pledge(&s("plank")?, false),
        "gift" => g.gift(&s("faction")?),
        "law" => g.start_law(&s("law")?),
        "choose" => { let picks: Vec<usize> = r.get("picks").and_then(|v| v.as_array()).map(|a| a.iter().filter_map(|x| x.as_u64().map(|y| y as usize)).collect()).unwrap_or_default();
            g.choose(r.get("n").and_then(|v| v.as_u64()).unwrap_or(0) as usize, &picks) }
        "vet" => g.vet(n("n")? as usize),
        "end_turn" => g.end_turn(),
        _ => Err(format!("unknown command {c}")),
    }
}

/* ---------------- the WebAssembly bridge ---------------- */
#[cfg(target_arch = "wasm32")]
mod wasm {
    /// Space for a request of `len` bytes; the browser writes the JSON there.
    #[no_mangle]
    pub extern "C" fn ags_alloc(len: usize) -> *mut u8 { let mut v = Vec::<u8>::with_capacity(len); let p = v.as_mut_ptr(); std::mem::forget(v); p }
    #[no_mangle]
    pub extern "C" fn ags_free(ptr: *mut u8, len: usize) { unsafe { drop(Vec::from_raw_parts(ptr, 0, len)); } }
    /// Run a request; returns the reply's address and length packed in one number (address << 32 | length).
    #[no_mangle]
    pub extern "C" fn ags_call(ptr: *const u8, len: usize) -> u64 {
        let req = unsafe { std::str::from_utf8_unchecked(std::slice::from_raw_parts(ptr, len)) };
        let out = super::call(req).into_bytes().into_boxed_slice(); let l = out.len() as u64;
        let p = Box::into_raw(out) as *mut u8 as u64; (p << 32) | l
    }
}
