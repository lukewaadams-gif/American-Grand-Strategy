//! The slice, played end to end through the same JSON requests the browser sends: a simple bot answers every card,
//! keeps research and production going, acts with every unit and ends turns until 2002. Checks that it finishes,
//! that the same seed gives the same game, and that a save replays exactly.
use ags_sim::{handle, Engine};
use serde_json::{json, Value};
use std::fs;
use std::path::PathBuf;

fn root() -> PathBuf { PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..") }

pub fn bundle() -> Value {
    let mut d = serde_json::Map::new();
    for k in ["states", "factions", "planks", "demands", "doctrines", "century", "powers", "names", "difficulty", "units", "leaders", "ideas", "buildings", "laws", "terrain", "calendar", "events"] {
        let s = fs::read_to_string(root().join("data").join(format!("{k}.json"))).unwrap_or_else(|_| panic!("data/{k}.json"));
        d.insert(k.into(), serde_json::from_str(&s).unwrap_or_else(|e| panic!("data/{k}.json: {e}")));
    }
    Value::Object(d)
}
fn map() -> Value { serde_json::from_str(&fs::read_to_string(root().join("web/gen/map.json")).expect("run tools/build-world.mjs first")).unwrap() }

struct T { e: Engine }
impl T {
    fn new() -> T { let mut e = Engine::default(); let r = handle(&mut e, &json!({ "op": "load", "data": bundle(), "map": map() }).to_string()); assert_eq!(r["ok"], true, "{r}"); T { e } }
    fn req(&mut self, v: Value) -> Value { handle(&mut self.e, &v.to_string()) }
    fn ok(&mut self, v: Value) -> Value { let r = self.req(v.clone()); assert_eq!(r["ok"], true, "{v} -> {r}"); r }
    fn view(&mut self) -> Value { self.req(json!({ "op": "view" })) }
    fn cmd(&mut self, c: Value) -> Value { let mut c = c; c["op"] = json!("cmd"); self.req(c) }
}

fn setup(seed: u64, leader: &str) -> Value {
    json!({ "leader": leader, "party": "Union", "factions": ["labor", "suburbs", "justice"], "difficulty": "operative", "seed": seed })
}

/// Plays one turn's worth of decisions. Returns false when the slice is over.
fn play_turn(t: &mut T, log: &mut Vec<String>) -> bool {
    for _ in 0..60 {
        let v = t.view();
        if v["over"] == true { return false; }
        let p = &v["pending"];
        if !p.is_null() {
            let kind = p["kind"].as_str().unwrap_or("");
            let c = match kind {
                "mate" => { t.cmd(json!({ "c": "vet", "n": 0 })); json!({ "c": "choose", "n": 0 }) }
                "cabinet" => json!({ "c": "choose", "picks": [0, 3, 4] }),
                _ => { let n = p["choices"].as_array().and_then(|a| a.first()).and_then(|c| c["n"].as_u64()).unwrap_or(0); json!({ "c": "choose", "n": n }) }
            };
            let r = t.cmd(c.clone()); assert_eq!(r["ok"], true, "{kind} {c} -> {r} (card {})", p["id"]);
            log.push(format!("{} {}", v["title"], p["id"]));
            continue;
        }
        // research, production, a doctrine, a law
        if v["you"]["idea_cur"].is_null() { if let Some(i) = v["ideas"].as_array().unwrap().iter().find(|i| i["state"] == "open") { t.cmd(json!({ "c": "research", "idea": i["id"] })); } }
        for hq in v["you"]["hqs"].as_array().unwrap() { if hq["prod"].is_null() { let r = t.cmd(json!({ "c": "prod", "hq": hq["hex"], "kind": "unit", "id": if v["turn"].as_u64().unwrap() % 2 == 0 { "organizer" } else { "surrogate" } })); assert_eq!(r["ok"], true, "{r}"); } }
        if v["you"]["influence"].as_f64().unwrap() >= v["you"]["doctrine_cost"].as_f64().unwrap() {
            if let Some(p) = v["doctrines"].as_array().unwrap().iter().flat_map(|d| d["policies"].as_array().unwrap().iter()).find(|p| p["state"] == "open") { t.cmd(json!({ "c": "adopt", "policy": p["id"] })); } }
        if let Some(o) = v["office"].as_object() { if o["holder"] == 0 && o["law"].is_null() { if let Some(l) = v["laws"].as_array().unwrap().iter().find(|l| l["passed"] == false) { t.cmd(json!({ "c": "law", "law": l["id"] })); } } }
        // every unit: move toward a nearby hex, then its first action that is open
        for u in v["units"].as_array().unwrap().iter().filter(|u| u["side"] == 0) {
            let id = u["id"].as_u64().unwrap();
            if !u["standing"].is_null() { continue; }
            // organizers go on standing orders to canvass where they stand, as a player tired of clicking would
            if u["line"] == "organizer" && id % 2 == 0 && u["acted"] == false { if t.cmd(json!({ "c": "standing", "unit": id, "order": "canvass" }))["ok"] == true { continue; } }
            if u["mp"].as_f64().unwrap() > 0.0 { let r = t.req(json!({ "op": "reach", "unit": id })); if let Some(h) = r["hexes"].as_array().and_then(|a| a.get(a.len() / 3)) { t.cmd(json!({ "c": "move", "unit": id, "to": h[0] })); } }
            let v2 = t.view(); let u2 = v2["units"].as_array().unwrap().iter().find(|x| x["id"] == id).cloned();
            if let Some(u2) = u2 { for a in u2["actions"].as_array().unwrap() {
                let aid = a["id"].as_str().unwrap(); if !a["block"].as_str().unwrap().is_empty() || ["plane", "charter", "skip"].contains(&aid) { continue; }
                let target = a["targets"].as_array().and_then(|x| x.first()).cloned();
                let r = t.cmd(json!({ "c": "act", "unit": id, "action": aid, "target": target })); if r["ok"] == true { break; } } }
        }
        let r = t.cmd(json!({ "c": "end_turn" })); assert_eq!(r["ok"], true, "end_turn: {r}");
        return true;
    }
    panic!("stuck in turn");
}

fn play(seed: u64, leader: &str) -> (Value, Vec<String>, T) {
    let mut t = T::new(); t.ok(json!({ "op": "new", "setup": setup(seed, leader) }));
    let mut log = vec![]; let mut turns = 0;
    while play_turn(&mut t, &mut log) { turns += 1; assert!(turns < 40, "too many turns"); }
    let v = t.view(); (v, log, t)
}

#[test]
fn the_slice_plays_to_the_end() {
    let (v, log, _) = play(7, "governor");
    assert_eq!(v["over"], true);
    assert!(log.iter().any(|l| l.contains("insurgent")), "the insurgent card: {log:?}");
    assert!(log.iter().any(|l| l.contains("election")), "election night: {log:?}");
    assert!(log.iter().any(|l| l.contains("year_end")), "the last card: {log:?}");
    println!("cards: {}", log.join(" | "));
    println!("result: {} · legacy {}", v["election"], v["pending"]["legacy"]);
}

#[test]
fn every_leader_plays_through() {
    for (i, l) in ["governor", "senator", "general", "founder", "organiser", "mayor"].iter().enumerate() {
        let (v, log, _) = play(100 + i as u64, l); assert_eq!(v["over"], true, "{l}");
        // the new year opens on the right side of the result: an inauguration if you won, the rival's if you lost
        let want = if v["election"]["won"] == true { "inauguration" } else { "opposition" };
        assert!(log.iter().any(|x| x.ends_with(&format!("\"{want}\""))), "{l}: expected the {want} card in {log:?}");
        println!("{l}: won {} ev {}", v["election"]["won"], v["election"]["ev"]);
    }
}

#[test]
fn the_same_seed_gives_the_same_game() {
    let (a, la, _) = play(42, "mayor"); let (b, lb, _) = play(42, "mayor");
    assert_eq!(la, lb); assert_eq!(a, b);
    let (c, _, _) = play(43, "mayor"); assert_ne!(a["lean"], c["lean"]);
}

#[test]
fn a_save_replays_exactly() {
    let mut t = T::new(); t.ok(json!({ "op": "new", "setup": setup(9, "senator") }));
    let mut log = vec![]; for _ in 0..6 { assert!(play_turn(&mut t, &mut log)); }
    let save = t.ok(json!({ "op": "save" }))["save"].clone(); let before = t.view();
    let mut u = T::new(); u.ok(json!({ "op": "restore", "save": save })); let after = u.view();
    assert_eq!(before, after);
}

#[test]
fn errors_come_back_as_errors() {
    let mut t = T::new(); t.ok(json!({ "op": "new", "setup": setup(1, "governor") }));
    let r = t.cmd(json!({ "c": "end_turn" })); assert_eq!(r["ok"], false, "the intro card must be answered first");
    let r = t.cmd(json!({ "c": "fly" })); assert_eq!(r["ok"], false);
    let r = t.req(json!({ "op": "nope" })); assert_eq!(r["ok"], false);
}

#[test]
fn the_data_adds_up() {
    let d = bundle();
    for census in ["1990", "2000", "2010", "2020"] { let s: u64 = d["states"].as_array().unwrap().iter().map(|s| s["ev"][census].as_u64().unwrap()).sum(); assert_eq!(s, 538, "{census}"); }
    let ids: Vec<&str> = d["events"].as_array().unwrap().iter().map(|e| e["id"].as_str().unwrap()).collect();
    for t in d["calendar"]["turns"].as_array().unwrap() { for k in ["open", "close"] { for e in t[k].as_array().map(|a| a.as_slice()).unwrap_or(&[]) { assert!(ids.contains(&e.as_str().unwrap()), "calendar names {e}"); } } }
}

/// Clears every card, then returns the view.
fn settle(t: &mut T) -> Value {
    for _ in 0..20 { let v = t.view(); let p = &v["pending"]; if p.is_null() { return v; }
        let c = match p["kind"].as_str().unwrap_or("") { "mate" => { t.cmd(json!({ "c": "vet", "n": 0 })); json!({ "c": "choose", "n": 0 }) } "cabinet" => json!({ "c": "choose", "picks": [0, 3, 4] }),
            _ => json!({ "c": "choose", "n": p["choices"].as_array().and_then(|a| a.first()).and_then(|c| c["n"].as_u64()).unwrap_or(0) }) };
        t.ok({ let mut c = c; c["op"] = json!("cmd"); c }); }
    panic!("cards never settled")
}
fn unit_of(v: &Value, id: u64) -> Value { v["units"].as_array().unwrap().iter().find(|u| u["id"] == id).cloned().unwrap() }

#[test]
fn standing_orders_act_every_turn_and_lapse() {
    let mut t = T::new(); t.ok(json!({ "op": "new", "setup": setup(5, "governor") }));
    let v = settle(&mut t);
    let mine: Vec<Value> = v["units"].as_array().unwrap().iter().filter(|u| u["side"] == 0).cloned().collect();
    let can = |u: &Value, a: &str| u["actions"].as_array().unwrap().iter().any(|x| x["id"] == a);
    let worker = mine.iter().find(|u| can(u, "canvass") || can(u, "stump")).expect("a unit that can canvass or stump").clone();
    let order = if can(&worker, "canvass") { "canvass" } else { "stump" }; let wid = worker["id"].as_u64().unwrap();
    // targeted and one-shot actions cannot be standing orders
    assert_eq!(t.cmd(json!({ "c": "standing", "unit": wid, "order": "plane" }))["ok"], false);
    assert_eq!(t.cmd(json!({ "c": "standing", "unit": wid, "order": order }))["ok"], true);
    let v = t.view(); let w = unit_of(&v, wid); assert_eq!(w["acted"], true, "an order acts at once"); assert_eq!(w["standing"], order);
    // a second unit sleeps
    let sleeper = mine.iter().find(|u| u["id"] != wid).unwrap()["id"].as_u64().unwrap();
    t.ok(json!({ "op": "cmd", "c": "standing", "unit": sleeper, "order": "sleep" }));
    t.ok(json!({ "op": "cmd", "c": "end_turn" })); let v = settle(&mut t);
    let w = unit_of(&v, wid); assert_eq!(w["acted"], true, "the standing order acted at the start of the turn: {w}"); assert_eq!(w["standing"], order);
    let s = unit_of(&v, sleeper); assert_eq!(s["skip"], true, "the sleeper sits: {s}"); assert_eq!(s["standing"], "sleep");
    // moving wakes a unit
    let r = t.req(json!({ "op": "reach", "unit": sleeper })); let to = r["hexes"].as_array().and_then(|a| a.first()).map(|h| h[0].clone());
    if let Some(to) = to { t.ok(json!({ "op": "cmd", "c": "move", "unit": sleeper, "to": to })); assert!(unit_of(&t.view(), sleeper)["standing"].is_null(), "moving wakes the unit"); }
    // clearing an order
    t.ok(json!({ "op": "cmd", "c": "standing", "unit": wid })); assert!(unit_of(&t.view(), wid)["standing"].is_null());
}

#[test]
fn an_ads_order_lapses_when_the_money_runs_out() {
    let mut t = T::new(); t.ok(json!({ "op": "new", "setup": setup(6, "senator") })); settle(&mut t);
    // an ad team in a state, on standing orders; then the campaign goes broke
    let id = { let g = t.e.game.as_mut().unwrap(); let hex = g.sides[0].hqs[0].hex; g.spawn("ads", hex, 0) } as u64;
    t.ok(json!({ "op": "cmd", "c": "standing", "unit": id, "order": "ads" }));
    let v = t.view(); assert_eq!(unit_of(&v, id)["standing"], "ads"); assert_eq!(unit_of(&v, id)["acted"], true, "the first ad buy runs at once");
    { let g = t.e.game.as_mut().unwrap(); g.sides[0].money = 1.0; for u in g.units.iter_mut() { if u.id == id as u32 { u.acted = false; } } g.run_standing(); }
    let v = t.view(); let u = unit_of(&v, id);
    assert!(u["standing"].is_null(), "the order lapsed: {u}"); assert_eq!(u["acted"], false, "and the unit is free for new orders");
    assert!(v["you"]["money"].as_f64().unwrap() >= 0.0, "no spending into debt");
    assert!(v["notes"][0]["title"].as_str().unwrap().contains("needs new orders"), "a note says so: {}", v["notes"][0]);
    println!("{}: {}", v["notes"][0]["title"], v["notes"][0]["text"]);
}

/// A competent player: units go to the close, big states and act there; production keeps the field growing.
fn smart_turn(t: &mut T, m: &Value) -> bool {
    for _ in 0..60 {
        let v = t.view(); if v["over"] == true { return false; }
        if !v["pending"].is_null() { settle(t); continue; }
        if v["you"]["idea_cur"].is_null() { if let Some(i) = v["ideas"].as_array().unwrap().iter().find(|i| i["state"] == "open") { t.cmd(json!({ "c": "research", "idea": i["id"] })); } }
        for (k, hq) in v["you"]["hqs"].as_array().unwrap().iter().enumerate() { if hq["prod"].is_null() {
            let money = v["you"]["money"].as_f64().unwrap(); let pick = if money > 14.0 && k % 2 == 0 { "ads" } else if (v["turn"].as_u64().unwrap() + k as u64) % 2 == 0 { "organizer" } else { "surrogate" };
            if t.cmd(json!({ "c": "prod", "hq": hq["hex"], "kind": "unit", "id": pick }))["ok"] != true { t.cmd(json!({ "c": "prod", "hq": hq["hex"], "kind": "unit", "id": "organizer" })); } } }
        if v["you"]["influence"].as_f64().unwrap() >= v["you"]["doctrine_cost"].as_f64().unwrap() {
            if let Some(p) = v["doctrines"].as_array().unwrap().iter().flat_map(|d| d["policies"].as_array().unwrap().iter()).find(|p| p["state"] == "open") { t.cmd(json!({ "c": "adopt", "policy": p["id"] })); } }
        if let Some(o) = v["office"].as_object() { if o["holder"] == 0 && o["law"].is_null() { if let Some(l) = v["laws"].as_array().unwrap().iter().find(|l| l["passed"] == false) { t.cmd(json!({ "c": "law", "law": l["id"] })); } } }
        let states = v["states"].as_array().unwrap().clone();
        let worth = |code: &str| -> f64 { let s = states.iter().find(|s| s["code"] == code); s.map_or(0.0, |s| { let mg = s["margin"].as_f64().unwrap(); let x = (mg - 1.0) / 3.0; s["ev"].as_f64().unwrap() / (1.0 + x * x) }) };
        let hexes = m["hexes"].as_array().unwrap();
        let score = |h: usize| -> f64 { let x = &hexes[h]; match x["s"].as_str() { Some(c) => worth(c) * (1.0 + x["p"].as_f64().unwrap_or(1.0) / 8.0), None => -1.0 } };
        for u in v["units"].as_array().unwrap().iter().filter(|u| u["side"] == 0) {
            let id = u["id"].as_u64().unwrap(); if u["acted"] == true { continue; }
            if u["mp"].as_f64().unwrap() > 0.0 { let r = t.req(json!({ "op": "reach", "unit": id }));
                let here = u["hex"].as_u64().unwrap() as usize; let mut best = (score(here), here);
                for h in r["hexes"].as_array().cloned().unwrap_or_default() { let hx = h[0].as_u64().unwrap() as usize; let s = score(hx); if s > best.0 { best = (s, hx); } }
                if best.1 != here { t.cmd(json!({ "c": "move", "unit": id, "to": best.1 })); } }
            let v2 = t.view(); let u2 = v2["units"].as_array().unwrap().iter().find(|x| x["id"] == id).cloned(); let Some(u2) = u2 else { continue };
            for want in ["stump", "rally", "ads", "canvass", "poll", "contest"] {
                if let Some(a) = u2["actions"].as_array().unwrap().iter().find(|a| a["id"] == want && a["block"] == "") {
                    let target = a["targets"].as_array().and_then(|x| x.first()).cloned();
                    if t.cmd(json!({ "c": "act", "unit": id, "action": want, "target": target }))["ok"] == true { break; } } }
        }
        let r = t.cmd(json!({ "c": "end_turn" })); assert_eq!(r["ok"], true, "end_turn: {r}"); return true;
    }
    panic!("stuck")
}

#[test]
fn difficulty_bites_and_a_good_campaign_can_win() {
    // a competent bot over every leader: the forgiving level is usually won, the brutal one rarely, and harder never wins more
    let m = map(); let mut rate = vec![];
    for diff in ["intern", "operative", "kingmaker"] {
        let mut wins = 0; let mut evs = vec![];
        for (i, leader) in ["governor", "senator", "general", "founder", "organiser", "mayor"].iter().enumerate() { for k in 0..2u64 {
            let mut t = T::new(); let mut s = setup(1000 + i as u64 * 10 + k, leader); s["difficulty"] = json!(diff); t.ok(json!({ "op": "new", "setup": s }));
            for _ in 0..40 { let v = t.view(); if !v["election"].is_null() || v["over"] == true { break; } if !smart_turn(&mut t, &m) { break; } }
            let v = t.view(); if v["election"]["won"] == true { wins += 1; } evs.push(v["election"]["ev"][0].as_u64().unwrap_or(0));
        } }
        println!("{diff}: won {wins}/12, EV {evs:?}"); rate.push(wins);
    }
    assert!(rate[0] >= 8, "intern should usually be won: {rate:?}");
    assert!(rate[2] <= 5, "kingmaker should rarely be won: {rate:?}");
    assert!(rate[0] >= rate[1] && rate[1] >= rate[2], "harder levels never win more: {rate:?}");
}
