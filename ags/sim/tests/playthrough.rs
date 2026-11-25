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
        let (v, _, _) = play(100 + i as u64, l); assert_eq!(v["over"], true, "{l}");
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
