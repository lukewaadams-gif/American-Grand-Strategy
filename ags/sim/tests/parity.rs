//! Native and WebAssembly builds must give bit-identical games. `tools/parity.mjs` plays the same commands through the
//! WebAssembly build and writes its views' fingerprints to web/gen/parity-wasm.json; this test compares them with native.
use ags_sim::{handle, Engine};
use serde_json::{json, Value};
use std::fs;
use std::path::PathBuf;

fn root() -> PathBuf { PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..") }
pub fn script() -> Vec<Value> {
    let mut s = vec![json!({ "op": "new", "setup": { "leader": "mayor", "party": "Union", "factions": ["labor", "suburbs", "justice"], "difficulty": "operative", "seed": 42 } })];
    for _ in 0..4 { s.push(json!({ "op": "cmd", "c": "choose", "n": 0 })); }
    s.push(json!({ "op": "cmd", "c": "end_turn" }));
    s
}
fn fingerprint(v: &Value) -> String { let s = v.to_string(); let mut h: u64 = 1469598103934665603; for b in s.bytes() { h ^= b as u64; h = h.wrapping_mul(1099511628211); } format!("{h:016x}") }

#[test]
fn native_matches_webassembly() {
    let p = root().join("web/gen/parity-wasm.json");
    let Ok(text) = fs::read_to_string(&p) else { eprintln!("skipped: run node tools/parity.mjs first"); return; };
    let wasm: Vec<String> = serde_json::from_str(&text).unwrap();
    let mut e = Engine::default();
    let data: Value = serde_json::from_str(&fs::read_to_string(root().join("web/gen/data.json")).unwrap()).unwrap();
    let map: Value = serde_json::from_str(&fs::read_to_string(root().join("web/gen/map.json")).unwrap()).unwrap();
    handle(&mut e, &json!({ "op": "load", "data": data, "map": map }).to_string());
    let mut native = vec![];
    for r in script() { handle(&mut e, &r.to_string()); native.push(fingerprint(&handle(&mut e, &json!({ "op": "view" }).to_string()))); }
    assert_eq!(native, wasm, "native and WebAssembly games differ");
}
