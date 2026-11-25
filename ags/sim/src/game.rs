//! The game state and its core rules: setup, the electorate on the hexes, factions, modifiers and yields.
use crate::data::*;
use crate::rng::Rng;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::rc::Rc;

pub const YOU: usize = 0;
pub const RIVAL: usize = 1;

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct Setup {
    pub leader: String, #[serde(default)] pub first: Option<String>, #[serde(default)] pub last: Option<String>, #[serde(default)] pub state: Option<String>,
    pub party: String, pub factions: Vec<String>, pub difficulty: String, #[serde(default)] pub seed: Option<u64>, #[serde(default)] pub ironman: bool,
    #[serde(default)] pub assists: Option<Value>,
}

#[derive(Serialize, Clone, Debug)]
pub struct Talent { pub id: String, pub first: String, pub last: String, pub name: String, pub pres: String, pub age: u32, pub state: String, pub faction: String,
    pub office: String, pub cha: f64, pub pol: f64, pub deal: f64, pub integ: f64, #[serde(skip)] pub risk: f64, pub vetted: bool, pub stars: u32 }

#[derive(Serialize, Clone, Debug)]
pub struct Leader { pub id: String, pub name: String, pub first: String, pub last: String, pub state: String, pub pres: String, pub age: u32,
    pub cha: f64, pub pol: f64, pub deal: f64, pub integ: f64, pub title: String, pub background: String }

#[derive(Serialize, Clone, Debug)]
pub struct Prod { pub kind: String, pub id: String, pub got: f64 }
#[derive(Serialize, Clone, Debug)]
pub struct Hq { pub hex: usize, pub city: usize, pub prod: Option<Prod>, pub buildings: Vec<String> }

#[derive(Serialize, Clone, Debug)]
pub struct Side {
    pub leader: Leader, pub party: String, pub money: f64, pub influence: f64, pub ideas_done: Vec<String>, pub idea_cur: Option<String>,
    pub idea_prog: BTreeMap<String, f64>, pub idea_bonus: f64, pub policies: Vec<String>, pub planks: Vec<String>, pub mom_bonus: f64,
    pub surge_pts: f64, pub surge_need: f64, pub surge_left: u32, pub gp_pts: f64, pub gp_need: f64, pub hqs: Vec<Hq>, pub offices: Vec<usize>, pub persona: String,
}

#[derive(Serialize, Clone, Debug)]
pub struct Unit { pub id: u32, pub line: String, pub side: usize, pub hex: usize, pub mp: f64, pub acted: bool, pub skip: bool, pub flew: bool, pub rattled: bool,
    pub name: Option<String>, pub home: Option<String>, pub great: Option<String> }

#[derive(Serialize, Clone, Debug)]
pub struct Third { pub name: String, pub short: String, pub hurt_you: f64, pub cut: f64, pub formed: String }
#[derive(Serialize, Clone, Debug)]
pub struct Fac { pub friend: [f64; 2], pub third: Option<Third>, pub low: u32 }
#[derive(Serialize, Clone, Debug)]
pub struct Quest { pub kind: String, pub target: String, pub text: String, pub done: bool }
#[derive(Serialize, Clone, Debug)]
pub struct Cause { pub why: String, pub nat: f64, pub top: Vec<(String, f64)> }
#[derive(Serialize, Clone, Debug)]
pub struct Note { pub icon: String, pub title: String, pub text: String, pub go: Value, pub tone: String }
#[derive(Serialize, Clone, Debug)]
pub struct Office { pub holder: usize, pub approval: f64, pub law: Option<(String, f64)>, pub passed: Vec<String>, pub cabinet: Vec<(String, Talent)>, pub wh_hex: usize }
#[derive(Serialize, Clone, Debug)]
pub struct Election { pub margins: Vec<f64>, pub ev: [u32; 2], pub won: bool, pub senate: i32, pub house: i32, pub florida_recount: bool, pub third_votes: Vec<(String, f64)> }

/// The card the player must answer before play continues.
#[derive(Clone, Debug)]
pub struct Pending { pub id: String, pub stage: String, pub result: Option<Value> }

pub struct Game {
    pub data: Rc<Data>, pub map: Rc<Map>, pub setup: Setup, pub rng: Rng, pub seed: u64,
    pub turn: usize, pub lean: Vec<f64>, pub sides: [Side; 2], pub units: Vec<Unit>, pub next_uid: u32,
    pub facs: Vec<Fac>, pub quests: Vec<Option<Quest>>, pub poll_noise: Vec<f64>, pub polled: Vec<bool>,
    pub flags: BTreeSet<String>, pub forecasts: BTreeMap<String, f64>, pub century_shift: BTreeMap<String, f64>, pub attitude: BTreeMap<String, i32>,
    pub queue: Vec<String>, pub pending: Option<Pending>, pub ctx: BTreeMap<String, String>, pub offer: Vec<Talent>, pub challenger: Option<Talent>, pub mate: Option<Talent>,
    pub report: Vec<Cause>, pub last_report: Vec<Cause>, pub notes: Vec<Note>, pub fx: Vec<Value>, pub history: Vec<(String, f64)>,
    pub office: Option<Office>, pub election: Option<Election>, pub achievements: Vec<String>, pub over: bool,
    pub rival_k: f64, pub log: Vec<Value>, pub stake: f64, pub queue_gp: bool,
}

pub fn clamp(v: f64, a: f64, b: f64) -> f64 { if v < a { a } else if v > b { b } else { v } }
const AFF_SCALE: f64 = 11.0;

impl Game {
    /* ---------------- setup ---------------- */
    pub fn new(setup: Setup, data: Rc<Data>, map: Rc<Map>) -> Result<Game, String> {
        let seed = setup.seed.unwrap_or(0x00C0_FFEE);
        let mut rng = Rng::new(seed);
        let ldef = data.leader(&setup.leader).ok_or("unknown leader")?.clone();
        if setup.factions.len() != 3 { return Err("choose three founding factions".into()); }
        for f in &setup.factions { if data.faction(f).is_none() { return Err(format!("unknown faction {f}")); } }
        let home = setup.state.clone().unwrap_or(ldef.state.clone());
        if map.state_index(&home).is_none() { return Err("unknown home state".into()); }
        let leader = Leader { id: ldef.id.clone(), first: setup.first.clone().unwrap_or(ldef.first.clone()), last: setup.last.clone().unwrap_or(ldef.last.clone()), name: String::new(),
            state: home.clone(), pres: ldef.pres.clone(), age: ldef.age, cha: ldef.ratings.cha, pol: ldef.ratings.pol, deal: ldef.ratings.deal, integ: ldef.ratings.integ,
            title: ldef.title.clone(), background: ldef.background.clone() };
        let leader = Leader { name: format!("{} {}", leader.first, leader.last), ..leader };
        // the rival: another background, fictional, from a big state
        let others: Vec<&LeaderDef> = data.leaders.iter().filter(|l| l.id != ldef.id).collect();
        let rdef = (*rng.pick(&others)).clone();
        let (rf, rl) = (rng.pick(&data.names.first).clone(), rng.pick(&data.names.last).clone());
        let big = ["TX", "FL", "OH", "GA", "NC", "AZ", "MI", "NY", "CA", "IL"]; let cand: Vec<&str> = big.iter().copied().filter(|c| *c != home).collect();
        let rs = rng.pick(&cand).to_string();
        let parties: Vec<String> = data.names.party_names.iter().filter(|p| **p != setup.party).cloned().collect();
        let rival_party = rng.pick(&parties).clone();
        let persona = rng.pick(&["The Closer", "The Technocrat", "The Populist"]).to_string();
        let rleader = Leader { id: rdef.id.clone(), name: format!("{rf} {rl}"), first: rf.clone(), last: rl, state: rs.clone(),
            pres: if data.names.feminine.contains(&rf) { "f".into() } else { "m".into() }, age: 50 + rng.below(18) as u32,
            cha: rdef.ratings.cha, pol: rdef.ratings.pol, deal: rdef.ratings.deal, integ: rdef.ratings.integ, title: format!("Governor of {}", state_name(&data, &rs)), background: rdef.background.clone() };
        let side = |leader: Leader, party: String, money: f64, persona: String| Side { leader, party, money, influence: 8.0, ideas_done: vec![], idea_cur: None, idea_prog: BTreeMap::new(),
            idea_bonus: 0.0, policies: vec![], planks: vec![], mom_bonus: 0.0, surge_pts: 0.0, surge_need: 22.0, surge_left: 0, gp_pts: 0.0, gp_need: 16.0, hqs: vec![], offices: vec![], persona };
        let shift = data.difficulty.iter().find(|d| d.id == setup.difficulty).map(|d| d.shift).unwrap_or(0.0);
        let start_money = 25.0 + ldef.ability.mods.get("start_money").copied().unwrap_or(0.0);
        let rmoney = 25.0 + rdef.ability.mods.get("start_money").copied().unwrap_or(0.0);
        let n = map.hexes.len();
        let mut g = Game { data: data.clone(), map: map.clone(), setup: setup.clone(), rng, seed, turn: 0, lean: vec![0.0; n],
            sides: [side(leader, setup.party.clone(), start_money, String::new()), side(rleader, rival_party, rmoney, persona)], units: vec![], next_uid: 1,
            facs: vec![], quests: vec![], poll_noise: vec![], polled: vec![false; map.states.len()], flags: BTreeSet::new(), forecasts: BTreeMap::new(),
            century_shift: BTreeMap::new(), attitude: BTreeMap::new(), queue: vec![], pending: None, ctx: BTreeMap::new(), offer: vec![], challenger: None, mate: None,
            report: vec![], last_report: vec![], notes: vec![], fx: vec![], history: vec![], office: None, election: None, achievements: vec![], over: false,
            rival_k: clamp(1.0 - shift * 0.09, 0.65, 1.5), log: vec![], stake: shift, queue_gp: false };
        g.forecasts.insert("threat".into(), 70.0);
        for p in &data.powers.era { g.attitude.insert(p.c.clone(), p.att); }
        // factions: your three, the rival's three (those least like yours), three undecided
        let mine = setup.factions.clone();
        let base = coalition_lean(&data, &map, &mine);
        let rest: Vec<String> = data.factions.iter().map(|f| f.id.clone()).filter(|f| !mine.contains(f)).collect();
        let mut scored: Vec<(f64, String)> = rest.iter().map(|f| { let k = data.faction(f).unwrap(); let mut s = 0.0;
            for (si, code) in map.states.iter().enumerate() { s += weight(&data, si, "2020") * base[si] * (affn(&data, k, code) - 0.5); } (s, f.clone()) }).collect();
        scored.sort_by(|a, b| a.0.partial_cmp(&b.0).unwrap());
        let theirs: Vec<String> = scored.iter().take(3).map(|x| x.1.clone()).collect();
        for f in &data.factions {
            let fr = if mine.contains(&f.id) { [70.0, 10.0] } else if theirs.contains(&f.id) { [10.0, 70.0] } else { [25.0, 25.0] };
            g.facs.push(Fac { friend: fr, third: None, low: 0 });
        }
        // the map: each state's lean, then each hex around it (cities lean to the urban factions)
        let up = mine.iter().map(|f| data.factions[data.faction(f).unwrap()].urbanism).sum::<f64>() / 3.0;
        let ur = theirs.iter().map(|f| data.factions[data.faction(f).unwrap()].urbanism).sum::<f64>() / 3.0;
        let m0 = -0.5 + shift;
        for (si, code) in map.states.iter().enumerate() {
            let target = base[si] + m0 + if *code == home { 4.0 } else { 0.0 } + if *code == rs { -3.0 } else { 0.0 };
            let ids = map.state_hexes[si].clone();
            let cn = |i: usize| cityness(map.pop[i]);
            let avg: f64 = ids.iter().map(|&i| cn(i) * map.pop[i]).sum::<f64>() / map.state_pop[si];
            for &i in &ids { g.lean[i] = 7.0 * (up - ur) * (cn(i) - avg) + g.rng.gauss() * 1.1; }
            let cur: f64 = ids.iter().map(|&i| g.lean[i] * map.pop[i]).sum::<f64>() / map.state_pop[si];
            for &i in &ids { g.lean[i] += target - cur; }
            let pn = g.rng.gauss() * 3.0; g.poll_noise.push(pn);
        }
        for side in 0..2 { let sa = g.leader_def(side).ability.start_ally.clone(); if let Some(f) = sa { let k = data.faction(&f).unwrap(); if g.status(k) != if side == YOU { 2 } else { -2 } {
            let before = g.status(k); g.facs[k].friend = if side == YOU { [65.0, 10.0] } else { [10.0, 65.0] }; let after = g.status(k); g.realign(k, before, after, ""); } } }
        for k in 0..data.factions.len() { let q = if g.status(k) == -2 { None } else { Some(g.quest_for(k)) }; g.quests.push(q); }
        // HQs and units
        let cap = map.biggest_city(map.state_index(&home).unwrap()).ok_or("no city in the home state")?;
        let rcap = map.biggest_city(map.state_index(&rs).unwrap()).ok_or("no city in the rival's state")?;
        g.sides[YOU].hqs.push(Hq { hex: map.cities[cap].h, city: cap, prod: None, buildings: vec![] });
        g.sides[RIVAL].hqs.push(Hq { hex: map.cities[rcap].h, city: rcap, prod: Some(Prod { kind: "unit".into(), id: "organizer".into(), got: 0.0 }), buildings: vec![] });
        let (ch, rh) = (map.cities[cap].h, map.cities[rcap].h);
        for l in ["candidate", "organizer", "organizer", "surrogate", "pollster"] { g.spawn(l, ch, YOU); }
        for l in ["candidate", "organizer", "organizer", "surrogate", "surrogate"] { g.spawn(l, rh, RIVAL); }
        let label = g.turn_def().title.clone(); let nat = g.national(); g.history.push((label, nat));
        g.start_turn();
        Ok(g)
    }

    /* ---------------- calendar ---------------- */
    pub fn turn_def(&self) -> &TurnDef { let t = &self.data.calendar.turns; &t[self.turn.min(t.len() - 1)] }
    pub fn year(&self) -> f64 { self.turn_def().year }
    pub fn census(&self) -> String { let y = self.year(); (if y < 2002.5 { "1990" } else if y < 2012.5 { "2000" } else if y < 2022.5 { "2010" } else { "2020" }).into() }
    pub fn ev(&self, si: usize) -> u32 { *self.data.states[si].ev.get(&self.census()).unwrap_or(&3) }
    pub fn in_office_turn(&self) -> bool { self.turn_def().office }

    /* ---------------- the electorate ---------------- */
    pub fn state_lean(&self, si: usize) -> f64 { let m = &self.map; m.state_hexes[si].iter().map(|&i| m.pop[i] * self.lean[i]).sum::<f64>() / m.state_pop[si] }
    pub fn states(&self) -> Vec<f64> { (0..self.map.states.len()).map(|s| self.state_lean(s)).collect() }
    /// Third parties take votes in their heartlands; this is their drag on your margin in a state.
    pub fn third_drag(&self, si: usize) -> f64 {
        let mut d = 0.0;
        for (k, f) in self.facs.iter().enumerate() { if let Some(t) = &f.third { let share = third_share(&self.data, &self.map, k, si) * (1.0 - t.cut); d -= share * (2.0 * t.hurt_you - 1.0); } }
        d
    }
    pub fn margin(&self, si: usize) -> f64 { self.state_lean(si) + self.third_drag(si) }
    pub fn national(&self) -> f64 { let c = "2020"; let mut s = 0.0; let mut w = 0.0; for si in 0..self.map.states.len() { let k = weight(&self.data, si, c); s += k * self.margin(si); w += k; } s / w }
    pub fn seen_margin(&self, si: usize) -> f64 { if self.polled[si] { self.margin(si) } else { self.margin(si) + self.poll_noise[si] } }
    pub fn projection(&self) -> [u32; 2] { let mut e = [0, 0]; for si in 0..self.map.states.len() { if self.seen_margin(si) > 0.0 { e[0] += self.ev(si) } else { e[1] += self.ev(si) } } e }

    /// Push the lean of the hexes around `at`: rings[d] points at distance d. Records the cause and returns the national change.
    pub fn push_area(&mut self, at: usize, rings: &[f64], sign: f64, why: &str, mul: f64, city_mul: f64, heartland: Option<usize>) -> f64 {
        let mut ds: BTreeMap<usize, f64> = BTreeMap::new();
        for (h, d) in self.map.within(at, rings.len() as i32 - 1) {
            let mut a = rings[d as usize] * mul; if self.map.hexes[h].c.is_some() { a *= city_mul; }
            if let Some(k) = heartland { a *= 1.0 + affn(&self.data, k, &self.map.states[self.map.state_of[h].unwrap()]); }
            self.lean[h] += sign * a; let si = self.map.state_of[h].unwrap(); *ds.entry(si).or_insert(0.0) += sign * a * self.map.pop[h] / self.map.state_pop[si];
        }
        let shown = sign * rings[0] * mul * if self.map.hexes[at].c.is_some() { city_mul } else { 1.0 };
        self.fx.push(serde_json::json!({ "kind": "float", "hex": at, "value": shown }));
        self.record(why, ds)
    }
    pub fn push_state(&mut self, si: usize, amt: f64, why: &str) -> f64 {
        for &i in &self.map.state_hexes[si].clone() { self.lean[i] += amt; }
        if let Some(c) = self.map.biggest_city(si) { self.fx.push(serde_json::json!({ "kind": "float", "hex": self.map.cities[c].h, "value": amt })); }
        let mut ds = BTreeMap::new(); ds.insert(si, amt); self.record(why, ds)
    }
    pub fn push_national(&mut self, d: f64, why: &str) -> f64 { for i in 0..self.lean.len() { if self.map.land(i) { self.lean[i] += d; } } self.report.push(Cause { why: why.into(), nat: d, top: vec![] }); d }
    pub fn record(&mut self, why: &str, ds: BTreeMap<usize, f64>) -> f64 {
        let mut nat = 0.0; let mut w = 0.0; for si in 0..self.map.states.len() { w += weight(&self.data, si, "2020"); }
        for (&si, &d) in &ds { nat += weight(&self.data, si, "2020") * d / w; }
        let mut top: Vec<(String, f64)> = ds.iter().map(|(&si, &d)| (self.map.states[si].clone(), d)).collect();
        top.sort_by(|a, b| b.1.abs().partial_cmp(&a.1.abs()).unwrap()); top.truncate(3);
        self.report.push(Cause { why: why.into(), nat, top }); nat
    }

    /* ---------------- factions ---------------- */
    /// From your side: 2 your ally, 1 friendly, 0 undecided, −1 leans rival, −2 the rival's ally.
    pub fn status(&self, k: usize) -> i32 { let [y, t] = self.facs[k].friend; if y >= 60.0 && y > t { 2 } else if t >= 60.0 && t > y { -2 } else if y >= 30.0 { 1 } else if t >= 30.0 { -1 } else { 0 } }
    pub fn set_friend(&mut self, k: usize, side: usize, v: f64, why: &str) {
        let before = self.status(k); self.facs[k].friend[side] = clamp(v, 0.0, 100.0); let after = self.status(k);
        if before != after { self.realign(k, before, after, why); }
    }
    pub fn gain(&mut self, k: usize, n: f64, why: &str) { let m = 1.0 + if n > 0.0 { self.mods(YOU).get("friend_gain_pct").copied().unwrap_or(0.0) } else { 0.0 }; let v = self.facs[k].friend[YOU] + n * m; self.set_friend(k, YOU, v, why); }
    pub fn realign(&mut self, k: usize, before: i32, after: i32, why: &str) {
        let a = |s: i32| if s == 2 { 1.0 } else if s == -2 { -1.0 } else { 0.0 };
        let d = a(after) - a(before);
        if d != 0.0 {
            let mut ds = BTreeMap::new();
            for i in 0..self.lean.len() { if let Some(si) = self.map.state_of[i] { let x = d * 2.0 * affn(&self.data, k, &self.map.states[si]); self.lean[i] += x; *ds.entry(si).or_insert(0.0) += x * self.map.pop[i] / self.map.state_pop[si]; } }
            let f = &self.data.factions[k]; let rl = self.sides[RIVAL].leader.last.clone();
            self.record(&format!("{} side with {}", f.name, if d > 0.0 { "you".to_string() } else { rl }), ds);
        }
        let f = self.data.factions[k].clone(); let rl = &self.sides[RIVAL].leader.last;
        let label = match after { 2 => "your allies".to_string(), 1 => "friendly to you".to_string(), 0 => "undecided".to_string(), -1 => format!("leaning to {rl}"), _ => format!("allied with {rl}") };
        if !self.history.is_empty() {
            self.note(&f.icon, &format!("{}: {}", f.name, if after == 2 { "Allies!".to_string() } else if after == -2 { "lost to the rival".to_string() } else { label.clone() }),
                &format!("{} are now {}.{}", f.name, label, if why.is_empty() { String::new() } else { format!(" {why}") }), serde_json::json!({ "screen": "factions" }), if after > before { "good" } else { "bad" });
        }
        if after == 2 && !self.achievements.contains(&"ally".to_string()) && !self.history.is_empty() { self.achieve("ally"); }
    }
    pub fn quest_for(&mut self, k: usize) -> Quest {
        let f = self.data.factions[k].clone();
        if f.quest == "plank" {
            if let Some(p) = self.data.demands.get("era").and_then(|d| d.get(&f.id)).cloned() { if let Some(pd) = self.data.plank(&p) { return Quest { kind: "plank".into(), target: p.clone(), text: format!("Pledge the {}", pd.name), done: false }; } }
        }
        if f.quest == "idea" { return Quest { kind: "idea".into(), target: "pollsci".into(), text: "Research Tracking Polls".into(), done: false }; }
        let st = self.states();
        let mut best: Option<(f64, usize)> = None;
        for (si, code) in self.map.states.iter().enumerate() { if code == "DC" || affn(&self.data, k, code) <= 0.6 { continue; } let v = st[si].abs() - self.ev(si) as f64 * 0.05; if best.map_or(true, |b| v < b.0) { best = Some((v, si)); } }
        let si = best.map(|b| b.1).unwrap_or(0);
        Quest { kind: "rally".into(), target: self.map.states[si].clone(), text: format!("Hold a rally or stump speech in {}", state_name(&self.data, &self.map.states[si])), done: false }
    }
    pub fn quest_check(&mut self) {
        for k in 0..self.quests.len() {
            let done = match &self.quests[k] { Some(q) if !q.done => match q.kind.as_str() {
                "plank" => self.sides[YOU].planks.contains(&q.target), "idea" => self.sides[YOU].ideas_done.contains(&q.target),
                _ => self.flags.contains(&format!("rallied:{}", q.target)) }, _ => false };
            if done { let text = self.quests[k].as_ref().unwrap().text.clone(); self.quests[k].as_mut().unwrap().done = true; self.gain(k, 25.0, "You did what they asked.");
                let f = self.data.factions[k].clone(); self.note(&f.icon, &format!("{}: request met", f.name), &format!("{text}. Friendship +25."), serde_json::json!({ "screen": "factions" }), "good"); }
        }
    }

    /* ---------------- modifiers and yields ---------------- */
    pub fn leader_def(&self, side: usize) -> &LeaderDef { self.data.leader(&self.sides[side].leader.id).unwrap() }
    /// Every modifier in force for a side: its leader's ability, its doctrines and its ideas.
    pub fn mods(&self, side: usize) -> HashMap<String, f64> {
        let mut m: HashMap<String, f64> = HashMap::new();
        let mut add = |src: &HashMap<String, f64>| for (k, v) in src { *m.entry(k.clone()).or_insert(0.0) += v; };
        add(&self.leader_def(side).ability.mods);
        for p in &self.sides[side].policies { if let Some((d, k)) = self.data.policy(p) { add(&d.policies[k].mods); } }
        for i in &self.sides[side].ideas_done { if let Some(d) = self.data.idea(i) { add(&d.mods); } }
        m
    }
    pub fn md(&self, side: usize, k: &str) -> f64 { self.mods(side).get(k).copied().unwrap_or(0.0) }
    pub fn fac_bonus(&self, side: usize, kind: &str) -> f64 {
        let mut s = 0.0;
        for (k, f) in self.data.factions.iter().enumerate() { let st = self.status(k) * if side == YOU { 1 } else { -1 }; if st > 0 && f.bonus.kind == kind { s += f.bonus.amount * st as f64; } }
        s
    }
    pub fn hq_vol(&self, side: usize, h: usize) -> f64 {
        let hq = &self.sides[side].hqs[h]; let c = &self.map.cities[hq.city];
        let mut v = 3.0 + (2.0 * c.pop.sqrt()).round() + self.md(side, "hq_vol");
        for b in &hq.buildings { if let Some(d) = self.data.building(b) { v += d.yields.get("vol").copied().unwrap_or(0.0); if b == "phonebank" { v += self.md(side, "phonebank_vol"); } } }
        if h == 0 { v += self.fac_bonus(side, "vol"); }
        v
    }
    pub fn yields(&self, side: usize) -> BTreeMap<String, f64> {
        let s = &self.sides[side]; let n = s.hqs.len() as f64; let m = self.mods(side); let g = |k: &str| m.get(k).copied().unwrap_or(0.0);
        let (mut money, mut ideas, mut inf, mut gp, mut mom, mut vol, mut upkeep) = (4.0, 4.0, 3.0, 3.0, 3.0, 0.0, 0.0);
        for (h, hq) in s.hqs.iter().enumerate() {
            money += self.map.cities[hq.city].pop.sqrt().round(); vol += self.hq_vol(side, h);
            for b in &hq.buildings { if let Some(d) = self.data.building(b) { money += d.yields.get("money").copied().unwrap_or(0.0) + if b == "finance" { g("finance_money") } else { 0.0 };
                ideas += d.yields.get("ideas").copied().unwrap_or(0.0); inf += d.yields.get("inf").copied().unwrap_or(0.0); gp += d.yields.get("gp").copied().unwrap_or(0.0); } }
        }
        ideas += n + g("hq_ideas") * n; inf += (n / 2.0).ceil() + g("hq_inf") * n; money += g("hq_money") * n + g("office_money") * s.offices.len() as f64;
        inf += g("ally_inf") * (0..self.facs.len()).filter(|&k| self.status(k) * if side == YOU { 1 } else { -1 } == 2).count() as f64;
        money += self.fac_bonus(side, "money"); ideas += self.fac_bonus(side, "ideas"); inf += self.fac_bonus(side, "inf"); mom += self.fac_bonus(side, "mom");
        mom += g("momentum") + s.mom_bonus;
        for u in &self.units { if u.side == side { upkeep += self.unit_def(u).upkeep; } }
        money *= 1.0 + g("money_pct");
        let mut y = BTreeMap::new();
        for (k, v) in [("money", money), ("ideas", ideas), ("inf", inf), ("gp", gp), ("mom", mom), ("vol", vol), ("upkeep", upkeep), ("net", money - upkeep)] { y.insert(k.to_string(), v); }
        y
    }
    pub fn doctrine_cost(&self, side: usize) -> f64 { ((12.0 + 8.0 * self.sides[side].policies.len() as f64) * (1.0 + self.md(side, "doctrine_cost_pct"))).round() }
    pub fn plank_cost(&self, side: usize) -> f64 { (10.0 * (1.0 + self.md(side, "plank_cost_pct"))).round() }

    /* ---------------- people ---------------- */
    pub fn talent(&mut self, faction: Option<usize>, boost: Option<&str>, state: Option<&str>, office: Option<String>, age: Option<u32>) -> Talent {
        let data = self.data.clone();
        let first = self.rng.pick(&data.names.first).clone(); let last = self.rng.pick(&data.names.last).clone();
        let pres = if data.names.feminine.contains(&first) { "f" } else { "m" }.to_string();
        let st = state.map(|s| s.to_string()).unwrap_or_else(|| { let c: Vec<&String> = self.map.states.iter().filter(|c| *c != "DC").collect(); self.rng.pick(&c).to_string() });
        let fk = faction.unwrap_or_else(|| self.rng.below(data.factions.len()));
        let mut r = || clamp((48.0 + self.rng.gauss() * 11.0).round(), 20.0, 80.0);
        let (mut cha, mut pol, mut deal, integ) = (r(), r(), r(), r());
        match boost { Some("cha") => cha = (cha + 14.0).min(80.0), Some("pol") => pol = (pol + 14.0).min(80.0), Some("deal") => deal = (deal + 14.0).min(80.0), _ => {} }
        let age = age.unwrap_or(38 + self.rng.below(30) as u32);
        let sn = state_name(&data, &st);
        let city = self.map.cities.iter().find(|c| c.s == st).map(|c| c.name.clone()).unwrap_or("the capital".into());
        let offices = [format!("Governor of {sn}"), format!("Senator from {sn}"), format!("Mayor of {city}"), format!("Representative from {sn}"), format!("Attorney General of {sn}")];
        let office = office.unwrap_or_else(|| self.rng.pick(&offices).clone());
        let risk = clamp((70.0 - integ) / 60.0 + self.rng.gauss() * 0.12, 0.03, 0.9);
        let stars = clamp(((cha + pol + deal) / 36.0 - 1.0 + if age < 50 { 1.0 } else { 0.0 }).round(), 1.0, 5.0) as u32;
        let id = format!("t{}", self.rng.next_u64() % 1_000_000_000);
        Talent { id, name: format!("{first} {last}"), first, last, pres, age, state: st, faction: data.factions[fk].id.clone(), office, cha, pol, deal, integ, risk, vetted: false, stars }
    }

    /* ---------------- notes and achievements ---------------- */
    pub fn note(&mut self, icon: &str, title: &str, text: &str, go: Value, tone: &str) {
        self.notes.insert(0, Note { icon: icon.into(), title: title.into(), text: text.into(), go, tone: tone.into() }); self.notes.truncate(14);
    }
    pub fn achieve(&mut self, id: &str) {
        if self.achievements.iter().any(|a| a == id) { return; }
        self.achievements.push(id.into()); self.fx.push(serde_json::json!({ "kind": "achievement", "id": id }));
    }
}

/* ---------------- free functions over the data ---------------- */
pub fn state_name(data: &Data, code: &str) -> String { data.states.iter().find(|s| s.code == code).map(|s| s.name.clone()).unwrap_or(code.into()) }
pub fn weight(data: &Data, si: usize, census: &str) -> f64 { (*data.states[si].ev.get(census).unwrap_or(&3) as f64 - 2.0).max(1.0) }
pub fn cityness(pop: f64) -> f64 { // a smooth 0..1 scale of how urban a hex is, without logarithms
    let x = (pop - 1.0).max(0.0); x / (x + 6.0)
}
pub fn aff(data: &Data, k: usize, code: &str) -> f64 {
    let f = &data.factions[k].affinity; let st = data.states.iter().find(|s| s.code == code).unwrap();
    let u = st.urban;
    let mut w = f.base + f.region.get(&st.region).copied().unwrap_or(0.0) + f.states.get(code).copied().unwrap_or(0.0);
    w += f.urban * u; w += f.suburban * (1.0 - ((u - 0.68).abs() * 2.5).min(1.0)); w += f.rural * (1.0 - u);
    w.max(0.05)
}
pub fn affn(data: &Data, k: usize, code: &str) -> f64 {
    let mx = data.states.iter().map(|s| aff(data, k, &s.code)).fold(0.0, f64::max); aff(data, k, code) / mx
}
/// How much of a state's vote a faction's third party takes, in points of margin.
pub fn third_share(data: &Data, map: &Map, k: usize, si: usize) -> f64 { 1.5 + 7.0 * affn(data, k, &map.states[si]) }
/// Each state's lean for a party built from these factions, centred on zero nationally.
pub fn coalition_lean(data: &Data, map: &Map, mine: &[String]) -> Vec<f64> {
    let raw: Vec<f64> = map.states.iter().map(|code| {
        let (mut m, mut o) = (0.0, 0.0);
        for (k, f) in data.factions.iter().enumerate() { if mine.contains(&f.id) { m += aff(data, k, code) } else { o += aff(data, k, code) } }
        m / mine.len().max(1) as f64 - o / (data.factions.len() - mine.len()).max(1) as f64
    }).collect();
    let w: Vec<f64> = (0..map.states.len()).map(|s| weight(data, s, "2020")).collect(); let wt: f64 = w.iter().sum();
    let mu: f64 = raw.iter().zip(&w).map(|(r, w)| r * w).sum::<f64>() / wt;
    let var: f64 = raw.iter().zip(&w).map(|(r, w)| w * (r - mu) * (r - mu)).sum::<f64>() / wt; let sd = var.sqrt().max(1e-9);
    raw.iter().map(|r| (r - mu) / sd * AFF_SCALE).collect()
}
