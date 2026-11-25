//! Everything the game is made of, read from the data files in `ags/data` (and from mods): states, factions, units,
//! leaders, ideas, doctrines, planks, laws, the calendar and the event chains; plus the hex map built by
//! `tools/build-world.mjs`. Effects and conditions in events stay as JSON and are interpreted by `events.rs`.
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, HashMap};

#[derive(Deserialize, Clone, Debug)]
pub struct StateDef { pub code: String, pub name: String, pub region: String, pub urban: f64, pub ev: BTreeMap<String, u32> }

#[derive(Deserialize, Clone, Debug)]
pub struct Affinity { pub base: f64, #[serde(default)] pub region: HashMap<String, f64>, #[serde(default)] pub urban: f64, #[serde(default)] pub suburban: f64,
    #[serde(default)] pub rural: f64, #[serde(default)] pub states: HashMap<String, f64> }
#[derive(Deserialize, Clone, Debug)]
pub struct Bonus { pub kind: String, pub amount: f64, pub text: String }
#[derive(Deserialize, Clone, Debug)]
pub struct ThirdPartyDef { pub name: String, pub short: String }
#[derive(Deserialize, Clone, Debug)]
pub struct FactionDef { pub id: String, pub name: String, pub who: String, pub icon: String, pub affinity: Affinity, pub urbanism: f64, pub bonus: Bonus, pub quest: String, pub third_party: ThirdPartyDef }

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct ForecastLine { pub label: String, #[serde(default)] pub lo: f64, #[serde(default)] pub mid: f64, #[serde(default)] pub hi: f64, pub unit: String, pub good: String,
    #[serde(default)] pub level: Option<Value>, #[serde(default)] pub spread: Option<f64> }
#[derive(Deserialize, Clone, Debug)]
pub struct PlankDef { pub id: String, pub name: String, pub blurb: String, pub from: f64, pub until: f64, pub national: [f64; 3], pub factions: HashMap<String, f64>, pub forecast: Vec<ForecastLine> }

#[derive(Deserialize, Clone, Debug)]
pub struct PolicyDef { pub id: String, pub name: String, pub text: String, #[serde(default)] pub mods: HashMap<String, f64> }
#[derive(Deserialize, Clone, Debug)]
pub struct DoctrineDef { pub id: String, pub name: String, pub tint: String, pub blurb: String, pub policies: Vec<PolicyDef> }

#[derive(Deserialize, Clone, Debug)]
pub struct CenturyDef { pub id: String, pub name: String, pub lane: String, pub year: f64, pub spread: f64, pub kind: String, pub note: String }
#[derive(Deserialize, Clone, Debug)]
pub struct PowerDef { pub c: String, pub who: String, pub att: i32, pub agenda: String, pub look: String }
#[derive(Deserialize, Clone, Debug)]
pub struct Powers { pub era: Vec<PowerDef>, pub term: Vec<PowerDef> }
#[derive(Deserialize, Clone, Debug)]
pub struct Names { pub first: Vec<String>, pub feminine: Vec<String>, pub last: Vec<String>, pub party_names: Vec<String> }
#[derive(Deserialize, Clone, Debug)]
pub struct DifficultyDef { pub id: String, pub name: String, pub note: String, pub shift: f64 }

#[derive(Deserialize, Clone, Debug)]
pub struct UnitEra { pub from: f64, pub id: String, pub name: String, pub icon: String, pub moves: f64, pub sight: u32, pub cost: f64, pub upkeep: f64, pub power: f64, pub actions: Vec<String> }
#[derive(Deserialize, Clone, Debug)]
pub struct UnitLine { pub line: String, pub role: String, pub eras: Vec<UnitEra> }

#[derive(Deserialize, Clone, Debug)]
pub struct Ratings { pub cha: f64, pub pol: f64, pub deal: f64, pub integ: f64 }
#[derive(Deserialize, Clone, Debug)]
pub struct Ability { pub name: String, pub text: String, #[serde(default)] pub mods: HashMap<String, f64>, #[serde(default)] pub start_ally: Option<String> }
#[derive(Deserialize, Clone, Debug, Default)]
pub struct UniqueUnit { pub line: String, pub name: String, pub text: String, #[serde(default)] pub moves: f64, #[serde(default)] pub power: f64,
    #[serde(default)] pub poll_ideas: f64, #[serde(default)] pub heartland: Option<String>, #[serde(default)] pub spill: bool,
    #[serde(default)] pub keep_on_office: bool, #[serde(default)] pub city_double: bool }
#[derive(Deserialize, Clone, Debug)]
pub struct LeaderDef { pub id: String, pub background: String, pub title: String, pub first: String, pub last: String, pub state: String, pub pres: String,
    pub age: u32, pub ratings: Ratings, pub ability: Ability, pub unit: UniqueUnit }

#[derive(Deserialize, Clone, Debug)]
pub struct IdeaDef { pub id: String, pub name: String, pub year: f64, pub cost: f64, pub requires: Vec<String>, pub icon: String, pub text: String, #[serde(default)] pub mods: HashMap<String, f64> }
#[derive(Deserialize, Clone, Debug)]
pub struct BuildingDef { pub id: String, pub name: String, pub cost: f64, pub icon: String, pub text: String, pub yields: HashMap<String, f64>, #[serde(default)] pub requires: Option<String> }
#[derive(Deserialize, Clone, Debug)]
pub struct LawDef { pub id: String, pub name: String, pub cost: f64, pub from: f64, pub until: f64, pub text: String, #[serde(default)] pub plank: Option<String>, #[serde(default)] pub effects: Vec<Value> }

#[derive(Deserialize, Clone, Debug)]
pub struct TerrainDef { pub name: String, #[serde(default)] pub r#move: f64, #[serde(default)] pub water: bool }
#[derive(Deserialize, Clone, Debug)]
pub struct Terrain { pub terrain: HashMap<String, TerrainDef>, pub features: HashMap<String, TerrainDef> }

#[derive(Deserialize, Clone, Debug)]
pub struct TurnDef { pub title: String, pub label: String, pub kind: String, pub year: f64, #[serde(default)] pub office: bool,
    #[serde(default)] pub open: Vec<String>, #[serde(default)] pub close: Vec<String> }
#[derive(Deserialize, Clone, Debug)]
pub struct Calendar { pub start_year: f64, pub census: String, pub turns: Vec<TurnDef> }

/// An event: a card, a headline, or one of the engine's special scenes (intro, mate, cabinet, roll).
#[derive(Deserialize, Clone, Debug)]
pub struct EventDef { pub id: String, pub kind: String, #[serde(default, rename = "if")] pub cond: Option<Value>, #[serde(default)] pub context: Vec<String>,
    #[serde(default)] pub art: String, #[serde(default)] pub kicker: String, #[serde(default)] pub title: String, #[serde(default)] pub body: String,
    #[serde(default)] pub sub: String, #[serde(default)] pub focus: Option<String>, #[serde(default)] pub advice: Option<String>,
    #[serde(default)] pub forecast: Option<Value>, #[serde(default)] pub choices: Vec<Value>, #[serde(default)] pub effects: Vec<Value>,
    #[serde(default, rename = "forecast_var")] pub roll_var: Option<String>, #[serde(default)] pub hit: Option<String>, #[serde(default)] pub miss: Option<String> }

#[derive(Deserialize, Clone, Debug)]
pub struct Data {
    pub states: Vec<StateDef>, pub factions: Vec<FactionDef>, pub planks: Vec<PlankDef>, pub demands: HashMap<String, HashMap<String, String>>,
    pub doctrines: Vec<DoctrineDef>, pub century: Vec<CenturyDef>, pub powers: Powers, pub names: Names, pub difficulty: Vec<DifficultyDef>,
    pub units: Vec<UnitLine>, pub leaders: Vec<LeaderDef>, pub ideas: Vec<IdeaDef>, pub buildings: Vec<BuildingDef>, pub laws: Vec<LawDef>,
    pub terrain: Terrain, pub calendar: Calendar, pub events: Vec<EventDef>,
}
impl Data {
    pub fn faction(&self, id: &str) -> Option<usize> { self.factions.iter().position(|f| f.id == id) }
    pub fn leader(&self, id: &str) -> Option<&LeaderDef> { self.leaders.iter().find(|l| l.id == id) }
    pub fn idea(&self, id: &str) -> Option<&IdeaDef> { self.ideas.iter().find(|l| l.id == id) }
    pub fn plank(&self, id: &str) -> Option<&PlankDef> { self.planks.iter().find(|l| l.id == id) }
    pub fn law(&self, id: &str) -> Option<&LawDef> { self.laws.iter().find(|l| l.id == id) }
    pub fn building(&self, id: &str) -> Option<&BuildingDef> { self.buildings.iter().find(|l| l.id == id) }
    pub fn event(&self, id: &str) -> Option<&EventDef> { self.events.iter().find(|e| e.id == id) }
    pub fn line(&self, id: &str) -> Option<&UnitLine> { self.units.iter().find(|l| l.line == id) }
    pub fn policy(&self, id: &str) -> Option<(&DoctrineDef, usize)> {
        for d in &self.doctrines { if let Some(k) = d.policies.iter().position(|p| p.id == id) { return Some((d, k)); } } None
    }
}

/* ---------------- the hex map ---------------- */
#[derive(Deserialize, Clone, Debug)]
pub struct HexDef { pub q: i32, pub r: i32, pub x: f64, pub y: f64, pub t: String, #[serde(default)] pub s: Option<String>, #[serde(default)] pub p: Option<f64>,
    #[serde(default)] pub f: Option<String>, #[serde(default)] pub c: Option<usize> }
#[derive(Deserialize, Clone, Debug)]
pub struct CityDef { pub name: String, pub s: String, pub pop: f64, pub h: usize, pub x: f64, pub y: f64 }
#[derive(Deserialize, Clone, Debug)]
pub struct MapFile { #[serde(rename = "R")] pub r: f64, pub hexes: Vec<HexDef>, pub cities: Vec<CityDef> }

/// The map, indexed for the rules: neighbours, the hexes of each state, populations.
pub struct Map {
    pub hexes: Vec<HexDef>, pub cities: Vec<CityDef>, pub nb: Vec<Vec<usize>>,
    pub states: Vec<String>, pub state_of: Vec<Option<usize>>, pub state_hexes: Vec<Vec<usize>>, pub state_pop: Vec<f64>, pub pop: Vec<f64>,
}
impl Map {
    pub fn new(f: MapFile, data: &Data) -> Map {
        let n = f.hexes.len();
        let mut index = HashMap::new();
        for (i, h) in f.hexes.iter().enumerate() { index.insert((h.q, h.r), i); }
        let dirs = [(1, 0), (1, -1), (0, -1), (-1, 0), (-1, 1), (0, 1)];
        let nb = f.hexes.iter().map(|h| dirs.iter().filter_map(|(dq, dr)| index.get(&(h.q + dq, h.r + dr)).copied()).collect()).collect();
        let states: Vec<String> = data.states.iter().map(|s| s.code.clone()).collect();
        let mut state_of = vec![None; n]; let mut state_hexes = vec![Vec::new(); states.len()]; let mut state_pop = vec![0.0; states.len()]; let mut pop = vec![0.0; n];
        for (i, h) in f.hexes.iter().enumerate() {
            if let Some(s) = &h.s { if let Some(k) = states.iter().position(|c| c == s) { state_of[i] = Some(k); state_hexes[k].push(i); pop[i] = h.p.unwrap_or(1.0); state_pop[k] += pop[i]; } }
        }
        Map { hexes: f.hexes, cities: f.cities, nb, states, state_of, state_hexes, state_pop, pop }
    }
    pub fn land(&self, i: usize) -> bool { self.state_of[i].is_some() }
    pub fn dist(&self, a: usize, b: usize) -> i32 { let (p, q) = (&self.hexes[a], &self.hexes[b]); let (dq, dr) = (p.q - q.q, p.r - q.r); (dq.abs() + dr.abs() + (dq + dr).abs()) / 2 }
    /// Land hexes within `r` of `i`, with their distance.
    pub fn within(&self, i: usize, r: i32) -> Vec<(usize, i32)> {
        let mut out = vec![(i, 0)]; let mut seen = vec![false; self.hexes.len()]; seen[i] = true; let mut frontier = vec![i];
        for d in 1..=r { let mut next = Vec::new(); for &h in &frontier { for &n in &self.nb[h] { if !seen[n] { seen[n] = true; next.push(n); if self.land(n) { out.push((n, d)); } } } } frontier = next; }
        out.retain(|&(h, _)| self.land(h)); out
    }
    pub fn state_index(&self, code: &str) -> Option<usize> { self.states.iter().position(|c| c == code) }
    pub fn biggest_city(&self, state: usize) -> Option<usize> {
        let code = &self.states[state];
        self.cities.iter().enumerate().filter(|(_, c)| &c.s == code).max_by(|a, b| a.1.pop.partial_cmp(&b.1.pop).unwrap()).map(|(i, _)| i)
    }
}
