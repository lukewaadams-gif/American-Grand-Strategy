//! Units: which form a unit line takes in the current era (and a leader's unique unit), movement over the hexes,
//! the actions, and contested hexes, decided by dice with the odds shown first.
use crate::data::*;
use crate::game::*;
use serde_json::{json, Value};

#[derive(Clone, Debug)]
pub struct UDef { pub line: String, pub id: String, pub name: String, pub icon: String, pub moves: f64, pub sight: u32, pub cost: f64, pub upkeep: f64,
    pub power: f64, pub actions: Vec<String>, pub unique: Option<UniqueUnit> }

/// The probability that 2d6 + a beats 2d6 + b (ties go to the defender).
pub fn dice_odds(a: f64, b: f64) -> f64 {
    let mut win = 0.0;
    for a1 in 1..=6 { for a2 in 1..=6 { for b1 in 1..=6 { for b2 in 1..=6 { if (a1 + a2) as f64 + a > (b1 + b2) as f64 + b { win += 1.0; } } } } }
    win / 1296.0
}

impl Game {
    pub fn unit_def_for(&self, side: usize, line: &str) -> UDef {
        let l = self.data.line(line).expect("unit line"); let y = self.year();
        let e = l.eras.iter().filter(|e| e.from <= y).last().unwrap_or(&l.eras[0]);
        let mut d = UDef { line: line.into(), id: e.id.clone(), name: e.name.clone(), icon: e.icon.clone(), moves: e.moves, sight: e.sight, cost: e.cost, upkeep: e.upkeep,
            power: e.power, actions: e.actions.clone(), unique: None };
        let uu = &self.leader_def(side).unit;
        if uu.line == line { d.name = uu.name.clone(); d.moves += uu.moves; d.power += uu.power; d.unique = Some(uu.clone()); }
        d
    }
    pub fn unit_def(&self, u: &Unit) -> UDef { self.unit_def_for(u.side, &u.line) }
    pub fn max_moves(&self, u: &Unit) -> f64 {
        let d = self.unit_def(u); d.moves + self.md(u.side, "unit_moves") + if u.line == "organizer" { self.md(u.side, "organizer_moves") } else { 0.0 }
    }
    pub fn spawn(&mut self, line: &str, hex: usize, side: usize) -> u32 {
        let id = self.next_uid; self.next_uid += 1;
        let mut u = Unit { id, line: line.into(), side, hex, mp: 0.0, acted: false, skip: false, flew: false, rattled: false, name: None, home: None, great: None, standing: None };
        u.mp = self.max_moves(&u); self.units.push(u); id
    }
    pub fn unit(&self, id: u32) -> Option<&Unit> { self.units.iter().find(|u| u.id == id) }
    fn unit_mut(&mut self, id: u32) -> Option<&mut Unit> { self.units.iter_mut().find(|u| u.id == id) }
    pub fn remove_unit(&mut self, id: u32) { self.units.retain(|u| u.id != id); }

    pub fn move_cost(&self, h: usize) -> f64 {
        let hx = &self.map.hexes[h]; if !self.map.land(h) { return f64::INFINITY; }
        let t = self.data.terrain.terrain.get(&hx.t).map(|t| t.r#move).unwrap_or(1.0);
        let f = hx.f.as_ref().and_then(|f| self.data.terrain.features.get(f)).map(|f| f.r#move).unwrap_or(1.0);
        t.max(f).max(1.0)
    }
    /// Every hex the unit can reach this turn, with the moves it would have left and the way there.
    pub fn reach(&self, u: &Unit) -> (Vec<f64>, Vec<usize>) {
        let n = self.map.hexes.len(); let mut best = vec![-1.0; n]; let mut prev = vec![usize::MAX; n];
        best[u.hex] = u.mp; let mut open = vec![u.hex];
        while !open.is_empty() {
            let (k, _) = open.iter().enumerate().max_by(|a, b| best[*a.1].partial_cmp(&best[*b.1]).unwrap()).unwrap(); let i = open.swap_remove(k);
            let m = best[i]; if m <= 0.0 { continue; }
            for &nb in &self.map.nb[i] { let c = self.move_cost(nb); if !c.is_finite() { continue; } let rem = (m - c).max(0.0);
                if best[nb] < rem || (best[nb] < 0.0) { best[nb] = rem; prev[nb] = i; open.push(nb); } }
        }
        (best, prev)
    }
    pub fn do_move(&mut self, uid: u32, to: usize) -> Result<(), String> {
        let u = self.unit(uid).ok_or("no such unit")?.clone();
        if u.side != YOU { return Err("not your unit".into()); }
        let (best, prev) = self.reach(&u);
        if to == u.hex || best[to] < 0.0 { return Err("out of reach".into()); }
        let mut path = vec![to]; while let Some(&p) = path.last() { if p == u.hex { break; } path.push(prev[p]); } path.reverse();
        self.fx.push(json!({ "kind": "move", "unit": uid, "path": path }));
        // moving a unit wakes it from any standing order, as in Civ
        let u = self.unit_mut(uid).unwrap(); u.hex = to; u.mp = best[to]; u.standing = None; Ok(())
    }

    /// Actions a unit may repeat every turn on standing orders.
    pub const REPEATABLE: [&'static str; 5] = ["stump", "rally", "canvass", "poll", "ads"];
    /// Give a unit a standing order (or clear it with `None`). An action order acts at once if the unit still can.
    pub fn set_standing(&mut self, uid: u32, order: Option<&str>) -> Result<(), String> {
        let u = self.unit(uid).ok_or("no such unit")?.clone(); if u.side != YOU { return Err("not your unit".into()); }
        match order {
            None => { self.unit_mut(uid).unwrap().standing = None; }
            Some("sleep") => { let m = self.unit_mut(uid).unwrap(); m.standing = Some("sleep".into()); m.skip = true; }
            Some(a) => {
                if !Self::REPEATABLE.contains(&a) { return Err("That action cannot be repeated every turn.".into()); }
                if !self.unit_def(&u).actions.contains(&a.to_string()) { return Err("This unit cannot do that.".into()); }
                self.unit_mut(uid).unwrap().standing = Some(a.into());
                if !u.acted && self.act_block(&u, a).is_empty() { self.act(uid, a, None)?; }
            }
        }
        Ok(())
    }
    /// At the start of your turn, units on standing orders act on their own. An order that can no longer be carried out
    /// lapses, and the unit asks for orders again.
    pub fn run_standing(&mut self) {
        let ids: Vec<(u32, String)> = self.units.iter().filter(|u| u.side == YOU).filter_map(|u| u.standing.clone().map(|s| (u.id, s))).collect();
        for (id, order) in ids {
            let u = self.unit(id).unwrap().clone();
            if order == "sleep" { self.unit_mut(id).unwrap().skip = true; continue; }
            if u.rattled || u.acted { continue; }
            let block = self.act_block(&u, &order);
            if block.is_empty() { let _ = self.act(id, &order, None); continue; }
            self.unit_mut(id).unwrap().standing = None;
            let name = self.unit_def(&u).name; let at = self.at(u.hex);
            self.note("unit", &format!("{name} needs new orders"), &format!("Its standing order {at} lapsed. {block}"), json!({ "hex": u.hex, "unit": id }), "bad");
        }
    }

    fn surge(&self, side: usize) -> f64 { if self.sides[side].surge_left > 0 { 1.5 } else { 1.0 } }
    pub fn rally_rings(&self, u: &Unit) -> (Vec<f64>, f64) {
        let d = self.unit_def(u); let rings = if u.line == "candidate" { vec![4.0, 2.0, 1.0] } else { vec![2.5, 1.2, 0.5] };
        let mut mul = self.surge(u.side) * d.power * (1.0 + self.md(u.side, "rally_pct"));
        if u.line == "candidate" { mul *= 1.0 + (self.sides[u.side].leader.cha - 55.0) / 100.0; }
        if u.line == "mate" { if let (Some(home), Some(si)) = (&u.home, self.map.state_of[u.hex]) { if &self.map.states[si] == home { mul *= 2.0; } } }
        if u.side == RIVAL { mul *= self.rival_k; }
        (rings, mul)
    }
    pub fn canvass_rings(&self, u: &Unit) -> (Vec<f64>, f64) {
        let d = self.unit_def(u); let mut rings = vec![2.0, 0.8]; if self.md(u.side, "canvass_rings") > 0.0 { rings.push(0.4); }
        let mut mul = self.surge(u.side) * d.power * (1.0 + self.md(u.side, "canvass_pct")); if u.side == RIVAL { mul *= self.rival_k; }
        (rings, mul)
    }
    pub fn city_mul(&self, side: usize) -> f64 { 1.0 + self.md(side, "city_pct") }
    /// "in Dayton" or "near Dayton": a place with its preposition.
    pub fn at(&self, h: usize) -> String { let p = self.place(h); if p.starts_with("near ") || p.starts_with("the ") { p } else { format!("in {p}") } }
    pub fn place(&self, h: usize) -> String {
        let hx = &self.map.hexes[h]; if let Some(c) = hx.c { return self.map.cities[c].name.clone(); }
        let si = match self.map.state_of[h] { Some(s) => s, None => return "the sea".into() };
        let mut best: Option<(i32, &str)> = None;
        for c in &self.map.cities { if c.s != self.map.states[si] { continue; } let d = self.map.dist(c.h, h); if best.map_or(true, |b| d < b.0) { best = Some((d, &c.name)); } }
        match best { Some((d, n)) if d <= 3 => format!("near {n}"), _ => state_name(&self.data, &self.map.states[si]) }
    }
    fn who(&self, side: usize) -> String { if side == YOU { "Your".into() } else { format!("{}'s", self.sides[RIVAL].leader.last) } }

    /// What an action needs before it can be taken (an empty string means it can).
    pub fn act_block(&self, u: &Unit, a: &str) -> String {
        let h = u.hex; let hx = &self.map.hexes[h]; let s = &self.sides[u.side];
        if a == "skip" { return String::new(); }
        if !self.unit_def(u).actions.contains(&a.to_string()) { return "This unit cannot do that.".into(); }
        if u.rattled { return "Rattled: it lost a contest and sits out this turn.".into(); }
        if u.acted && a != "plane" && a != "charter" { return "Already acted this turn.".into(); }
        match a {
            "plane" => if u.flew { "The plane has flown this turn.".into() } else { String::new() },
            "charter" => if u.flew { "Already flown this turn.".into() } else if s.money < 2.0 { "Needs $2.".into() } else if !s.hqs.iter().any(|q| q.hex != h) { "No other HQ to fly to.".into() } else { String::new() },
            "office" => if hx.c.is_some() { "Not on a city: open an HQ there instead.".into() } else if s.offices.contains(&h) { "There is already a field office here.".into() } else { String::new() },
            "hq" => if hx.c.is_none() { "Only on a city.".into() } else if self.sides.iter().any(|sd| sd.hqs.iter().any(|q| q.hex == h)) { "This city already has an HQ.".into() } else { String::new() },
            "ads" => if s.money < 3.0 { "Needs $3.".into() } else { String::new() },
            "attack_ad" => if s.money < 4.0 { "Needs $4.".into() } else if self.targets(u, a).is_empty() { "No rival unit in this state.".into() } else { String::new() },
            "contest" => if self.targets(u, a).is_empty() { "No rival unit on or next to this hex.".into() } else { String::new() },
            _ => String::new(),
        }
    }
    /// Who a contest or an attack ad can target.
    pub fn targets(&self, u: &Unit, a: &str) -> Vec<u32> {
        let si = self.map.state_of[u.hex];
        self.units.iter().filter(|o| o.side != u.side && match a {
            "contest" => self.map.dist(o.hex, u.hex) <= 1,
            "attack_ad" => self.map.state_of[o.hex] == si,
            _ => false }).map(|o| o.id).collect()
    }
    fn contest_mods(&self, u: &Unit, defending: bool) -> f64 {
        let d = self.unit_def(u); let mut m = 2.0 * d.power;
        if u.line == "candidate" { m += (self.sides[u.side].leader.cha - 50.0) / 10.0; }
        if u.line == "lawyer" { m += 2.0; }
        if self.sides[u.side].surge_left > 0 { m += 1.0; }
        if defending { m += 1.0; if self.sides[u.side].hqs.iter().any(|q| q.hex == u.hex) { m += 2.0; } }
        if u.side == RIVAL { m += (self.rival_k - 1.0) * 3.0; }
        m
    }
    pub fn contest_odds(&self, a: &Unit, b: &Unit, ad: bool) -> (f64, f64, f64) {
        let am = if ad { 2.0 * self.unit_def(a).power * (1.0 + self.md(a.side, "ads_pct")) } else { self.contest_mods(a, false) };
        let bm = self.contest_mods(b, true); (dice_odds(am, bm), am, bm)
    }

    /// Take an action with a unit. `target` is a hex (flights) or a unit id (contests and attack ads).
    pub fn act(&mut self, uid: u32, a: &str, target: Option<i64>) -> Result<(), String> {
        let u = self.unit(uid).ok_or("no such unit")?.clone();
        let block = self.act_block(&u, a); if !block.is_empty() { return Err(block); }
        let d = self.unit_def(&u); let side = u.side; let sign = if side == YOU { 1.0 } else { -1.0 };
        let who = self.who(side); let place = self.place(u.hex);
        match a {
            "skip" => { let m = self.unit_mut(uid).unwrap(); m.skip = true; m.mp = 0.0; }
            "stump" | "rally" => {
                let (rings, mul) = self.rally_rings(&u); let heart = d.unique.as_ref().and_then(|x| x.heartland.clone()).and_then(|f| self.data.faction(&f));
                let what = if a == "stump" { "stump speech".to_string() } else { format!("{} rally", d.name) };
                self.push_area(u.hex, &rings, sign, &format!("{who} {what} {}", self.at(u.hex)), mul, self.city_mul(side), heart);
                self.unit_mut(uid).unwrap().acted = true;
                if side == YOU { if let Some(si) = self.map.state_of[u.hex] { let code = self.map.states[si].clone(); self.flags.insert(format!("rallied:{code}")); } self.quest_check(); if a == "stump" { self.sides[YOU].mom_bonus += 0.5; } }
            }
            "canvass" => {
                let (rings, mul) = self.canvass_rings(&u); let mut cm = self.city_mul(side);
                if d.unique.as_ref().map_or(false, |x| x.city_double) && self.map.hexes[u.hex].c.is_some() { cm *= 2.0; }
                self.push_area(u.hex, &rings, sign, &format!("{who} organizers canvassed {place}"), mul, cm, None); self.unit_mut(uid).unwrap().acted = true;
            }
            "office" => {
                self.sides[side].offices.push(u.hex);
                if side == YOU { self.note("office", "Field office opened", &format!("A field office {} will push every turn.", self.at(u.hex)), json!({ "hex": u.hex }), "good"); }
                if d.unique.as_ref().map_or(false, |x| x.keep_on_office) { let m = self.unit_mut(uid).unwrap(); m.acted = true; m.mp = 0.0; } else { self.remove_unit(uid); }
            }
            "hq" => {
                let city = self.map.hexes[u.hex].c.unwrap(); self.sides[side].hqs.push(Hq { hex: u.hex, city, prod: None, buildings: vec![] }); self.remove_unit(uid);
                if side == YOU { let n = self.map.cities[city].name.clone(); self.note("office", &format!("HQ opened in {n}"), "Choose what it should produce.", json!({ "city": u.hex }), "good"); self.achieve("expansion"); }
            }
            "poll" => {
                let si = self.map.state_of[u.hex].unwrap(); self.polled[si] = true;
                if self.md(side, "poll_reach") > 0.0 { let near: Vec<usize> = self.map.within(u.hex, 4).iter().filter_map(|(h, _)| self.map.state_of[*h]).collect(); for s in near { self.polled[s] = true; } }
                let bonus = 1.0 + self.md(side, "poll_ideas") + d.unique.as_ref().map_or(0.0, |x| x.poll_ideas); self.sides[side].idea_bonus += bonus;
                self.unit_mut(uid).unwrap().acted = true;
                self.fx.push(json!({ "kind": "float", "hex": u.hex, "text": format!("Polled · +{bonus} Ideas") }));
            }
            "ads" => {
                let si = self.map.state_of[u.hex].unwrap(); let mut amt = 1.1 * self.surge(side) * d.power * (1.0 + self.md(side, "ads_pct"));
                if side == RIVAL { amt *= self.rival_k; }
                self.sides[side].money -= 3.0; let sn = state_name(&self.data, &self.map.states[si]);
                self.push_state(si, sign * amt, &format!("{who} ads in {sn}"));
                if d.unique.as_ref().map_or(false, |x| x.spill) || self.md(side, "ads_spill") > 0.0 {
                    let mut ns: Vec<usize> = vec![]; for &h in &self.map.state_hexes[si] { for &n in &self.map.nb[h] { if let Some(s2) = self.map.state_of[n] { if s2 != si && !ns.contains(&s2) { ns.push(s2); } } } }
                    for s2 in ns { let n2 = state_name(&self.data, &self.map.states[s2]); self.push_state(s2, sign * amt / 2.0, &format!("{who} ads spill into {n2}")); }
                }
                self.unit_mut(uid).unwrap().acted = true;
            }
            "attack_ad" | "contest" => {
                let tid = target.ok_or("choose a target")? as u32; if !self.targets(&u, a).contains(&tid) { return Err("not a valid target".into()); }
                let t = self.unit(tid).unwrap().clone(); let ad = a == "attack_ad"; if ad { self.sides[side].money -= 4.0; }
                let (p, am, bm) = self.contest_odds(&u, &t, ad);
                let (a1, a2, b1, b2) = (self.rng.d6(), self.rng.d6(), self.rng.d6(), self.rng.d6());
                let win = (a1 + a2) as f64 + am > (b1 + b2) as f64 + bm;
                let tname = self.unit_def(&t).name; let tat = self.at(t.hex);
                self.fx.push(json!({ "kind": "dice", "a": [a1, a2], "b": [b1, b2], "am": am, "bm": bm, "p": p, "win": win, "side": side, "hex": t.hex,
                    "title": if ad { format!("Attack ad on {}'s {}", if side == YOU { self.sides[RIVAL].leader.last.clone() } else { "your".into() }, tname) } else { { let mut c = tat.clone(); c.replace_range(..1, &c[..1].to_uppercase()); format!("Contest {c}") } } }));
                let (winner, loser) = if win { (side, t.id) } else { (t.side, uid) };
                let wsign = if winner == YOU { 1.0 } else { -1.0 };
                if ad { let si = self.map.state_of[t.hex].unwrap(); let sn = state_name(&self.data, &self.map.states[si]);
                    if win { self.push_state(si, sign * 0.6, &format!("{who} attack ad landed in {sn}")); } else { self.push_state(si, -sign * 0.4, &format!("{who} attack ad backfired in {sn}")); } }
                else { self.push_area(t.hex, &[1.5, 0.6], wsign, &format!("{} won a contest {tat}", self.who(winner)), 1.0, 1.0, None); }
                if let Some(l) = self.unit_mut(loser) { l.rattled = true; }
                let ls = if win { t.side } else { side }; self.sides[ls].mom_bonus -= 1.0;
                if let Some(m) = self.unit_mut(uid) { m.acted = true; }
                if side == YOU && win { self.achieve("contest"); }
            }
            "plane" | "charter" => {
                let to = target.ok_or("choose where to fly")? as usize;
                let ok = if a == "plane" { self.map.hexes.get(to).map_or(false, |h| h.c.is_some()) } else { self.sides[side].hqs.iter().any(|q| q.hex == to) };
                if !ok || to == u.hex { return Err("cannot fly there".into()); }
                if a == "charter" { self.sides[side].money -= 2.0; }
                self.fx.push(json!({ "kind": "fly", "unit": uid, "from": u.hex, "to": to }));
                let m = self.unit_mut(uid).unwrap(); m.hex = to; m.mp = 0.0; m.flew = true;
            }
            "great" => {
                if u.great.as_deref() == Some("strategist") { let si = self.map.state_of[u.hex].unwrap(); let sn = state_name(&self.data, &self.map.states[si]); let s = self.surge(side); self.push_state(si, 2.6 * s, &format!("Masterstroke in {sn}")); }
                else { let s = self.surge(side); self.push_area(u.hex, &[3.0, 3.0, 2.0, 1.0], 1.0, &format!("Barnstorm through {place}"), s, 1.0, None); }
                self.remove_unit(uid); self.achieve("legend");
            }
            _ => return Err(format!("unknown action {a}")),
        }
        Ok(())
    }

    /// A short description of an action for the panel, with its numbers.
    pub fn act_hint(&self, u: &Unit, a: &str) -> Value {
        let (text, rings): (String, Vec<f64>) = match a {
            "stump" | "rally" => { let (r, m) = self.rally_rings(u); (format!("+{:.1} here, +{:.1} next ring, +{:.1} the ring after.", r[0] * m, r[1] * m, r[2] * m), r.iter().map(|x| x * m).collect()) }
            "canvass" => { let (r, m) = self.canvass_rings(u); (format!("+{:.1} here, +{:.1} around it{}.", r[0] * m, r[1] * m, if r.len() > 2 { ", and a third ring" } else { "" }), r.iter().map(|x| x * m).collect()) }
            "office" => ("Uses the unit. The office pushes this hex and its neighbours every turn.".into(), vec![0.5, 0.5]),
            "hq" => ("Uses the unit. A new HQ: it produces units and buildings, and pushes three rings around it every turn.".into(), vec![0.4, 0.25, 0.1]),
            "poll" => ("Replaces the public polls' guess with the truth in this state. Feeds the think tank.".into(), vec![]),
            "ads" => ("$3: moves every hex in this state.".into(), vec![]),
            "attack_ad" => ("$4: target a rival unit in this state. Win and it sits out a turn; lose and it backfires.".into(), vec![]),
            "contest" => ("Challenge a rival unit on or next to this hex. Dice decide; the loser sits out a turn.".into(), vec![1.5, 0.6]),
            "plane" => ("Fly to any city. Once a turn; ends movement, but the candidate can still act.".into(), vec![]),
            "charter" => ("$2: fly to one of your HQs; ends movement.".into(), vec![]),
            "great" => (if u.great.as_deref() == Some("strategist") { "Masterstroke: +2.6 on every hex of this state. Uses the Operative." } else { "Barnstorm: +3 for two rings, then +2 and +1. Uses the Operative." }.into(), vec![3.0, 3.0, 2.0, 1.0]),
            _ => ("Do nothing with this unit this turn.".into(), vec![]),
        };
        json!({ "text": text, "rings": rings, "block": self.act_block(u, a) })
    }
}
