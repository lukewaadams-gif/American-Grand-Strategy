//! The turn: what happens when you press Next turn, the rival's moves, third parties, the office and its laws,
//! and election night. Also the player's non-unit commands (production, research, doctrines, pledges, gifts, laws).
use crate::game::*;
use serde_json::json;

impl Game {
    /* ---------------- commands ---------------- */
    pub fn set_production(&mut self, hq_hex: usize, kind: &str, id: &str) -> Result<(), String> {
        let h = self.sides[YOU].hqs.iter().position(|q| q.hex == hq_hex).ok_or("not your HQ")?;
        match kind {
            "unit" => { if self.data.line(id).is_none() || ["candidate", "mate", "operative"].contains(&id) { return Err("cannot build that unit".into()); } }
            "building" => { let b = self.data.building(id).ok_or("unknown building")?; if self.sides[YOU].hqs[h].buildings.contains(&id.to_string()) { return Err("already built".into()); }
                if let Some(r) = &b.requires { if !self.sides[YOU].ideas_done.contains(r) { return Err("needs an idea first".into()); } } }
            _ => return Err("unknown production".into()),
        }
        let got = match &self.sides[YOU].hqs[h].prod { Some(p) if p.id == id => p.got, _ => 0.0 };
        self.sides[YOU].hqs[h].prod = Some(Prod { kind: kind.into(), id: id.into(), got }); Ok(())
    }
    pub fn prod_cost(&self, side: usize, h: usize, p: &Prod) -> f64 {
        if p.kind == "unit" { let mut c = self.unit_def_for(side, &p.id).cost; if p.id == "ads" && self.sides[side].hqs[h].buildings.iter().any(|b| b == "studio") { c /= 2.0; } c }
        else { self.data.building(&p.id).map(|b| b.cost).unwrap_or(99.0) }
    }
    pub fn buy_cost(&self, side: usize, h: usize) -> f64 {
        match &self.sides[side].hqs[h].prod { Some(p) => ((self.prod_cost(side, h, p) - p.got).max(0.0) * 2.2 * (1.0 + self.md(side, "buy_pct"))).ceil(), None => 0.0 }
    }
    pub fn buy(&mut self, hq_hex: usize) -> Result<(), String> {
        let h = self.sides[YOU].hqs.iter().position(|q| q.hex == hq_hex).ok_or("not your HQ")?;
        let c = self.buy_cost(YOU, h); if self.sides[YOU].hqs[h].prod.is_none() { return Err("nothing in production".into()); }
        if self.sides[YOU].money < c { return Err("not enough money".into()); }
        self.sides[YOU].money -= c; self.complete(YOU, h); Ok(())
    }
    fn complete(&mut self, side: usize, h: usize) {
        let p = self.sides[side].hqs[h].prod.take().unwrap(); let hex = self.sides[side].hqs[h].hex;
        if p.kind == "unit" { let id = self.spawn(&p.id, hex, side); if side == YOU { let n = self.unit_def_for(side, &p.id).name; let c = self.map.cities[self.sides[side].hqs[h].city].name.clone();
            self.note("people", &format!("{n} ready"), &format!("{c} trained a new {n}."), json!({ "unit": id }), "good"); } }
        else { self.sides[side].hqs[h].buildings.push(p.id.clone()); if side == YOU { let b = self.data.building(&p.id).unwrap().name.clone(); let c = self.map.cities[self.sides[side].hqs[h].city].name.clone();
            self.note("office", &format!("{b} built"), &format!("{c} finished its {b}."), json!({ "city": hex }), "good"); } }
    }
    pub fn idea_open(&self, side: usize, id: &str) -> bool {
        let s = &self.sides[side]; match self.data.idea(id) { Some(i) => !s.ideas_done.contains(&i.id) && i.requires.iter().all(|r| s.ideas_done.contains(r)) && i.year <= self.year().floor(), None => false }
    }
    pub fn research(&mut self, id: &str) -> Result<(), String> {
        // pick the first missing prerequisite that can be started, as Civ queues a path
        fn first(g: &Game, id: &str, depth: u32) -> Option<String> {
            if depth > 10 { return None; } if g.idea_open(YOU, id) { return Some(id.into()); }
            let i = g.data.idea(id)?; for r in &i.requires { if !g.sides[YOU].ideas_done.contains(r) { if let Some(f) = first(g, r, depth + 1) { return Some(f); } } } None
        }
        let pick = first(self, id, 0).ok_or("not available yet")?; self.sides[YOU].idea_cur = Some(pick); Ok(())
    }
    pub fn policy_open(&self, side: usize, id: &str) -> bool {
        let s = &self.sides[side]; if s.policies.iter().any(|p| p == id) { return false; }
        match self.data.policy(id) { Some((d, k)) => k == 0 || (k < 3 && s.policies.contains(&d.policies[0].id)) || (k == 3 && d.policies[..3].iter().all(|p| s.policies.contains(&p.id))), None => false }
    }
    pub fn adopt(&mut self, id: &str) -> Result<(), String> {
        if !self.policy_open(YOU, id) { return Err("not available".into()); }
        let c = self.doctrine_cost(YOU); if self.sides[YOU].influence < c { return Err("not enough Influence".into()); }
        self.sides[YOU].influence -= c; self.sides[YOU].policies.push(id.into());
        if self.data.policy(id).map_or(false, |(d, k)| d.policies[k].mods.contains_key("free_idea")) {
            if let Some(i) = self.data.ideas.iter().find(|i| self.idea_open(YOU, &i.id)).map(|i| i.id.clone()) { self.sides[YOU].ideas_done.push(i.clone()); if self.sides[YOU].idea_cur.as_deref() == Some(&i) { self.sides[YOU].idea_cur = None; } }
        }
        self.achieve("doctrine"); Ok(())
    }
    pub fn plank_open(&self, id: &str) -> bool { let y = self.year(); self.data.plank(id).map_or(false, |p| p.from <= y && y <= p.until) && !self.sides[YOU].planks.iter().any(|p| p == id) }
    pub fn pledge(&mut self, id: &str, free: bool) -> Result<(), String> {
        if !self.plank_open(id) { return Err("not available".into()); }
        let c = if free { 0.0 } else { self.plank_cost(YOU) }; if self.sides[YOU].influence < c { return Err("not enough Influence".into()); }
        let p = self.data.plank(id).unwrap().clone(); self.sides[YOU].influence -= c; self.sides[YOU].planks.push(id.into());
        let d = p.national[1] + self.rng.gauss() * (p.national[2] - p.national[0]) / 3.3;
        self.push_national(d, &format!("Pledged the {}", p.name));
        for (f, v) in &p.factions { if let Some(k) = self.data.faction(f) { self.gain(k, v * 8.0, &format!("Your pledge: {}.", p.name)); let r = self.facs[k].friend[RIVAL] - v * 3.0; self.set_friend(k, RIVAL, r, ""); } }
        self.sides[YOU].mom_bonus += 1.0; self.quest_check();
        self.fx.push(json!({ "kind": "toast", "icon": "scroll", "title": p.name, "text": format!("Pledged · nationally {:+.2}%", d / 2.0) })); Ok(())
    }
    pub fn gift(&mut self, fid: &str) -> Result<(), String> {
        let k = self.data.faction(fid).ok_or("unknown faction")?; if self.sides[YOU].money < 10.0 { return Err("needs $10".into()); }
        self.sides[YOU].money -= 10.0; self.gain(k, 12.0, "Your gift helped."); Ok(())
    }
    pub fn start_law(&mut self, id: &str) -> Result<(), String> {
        let y = self.year(); let law = self.data.law(id).ok_or("unknown law")?.clone();
        let o = self.office.as_mut().ok_or("you do not hold office")?; if o.holder != YOU { return Err("you do not hold office".into()); }
        if o.passed.contains(&law.id) || !(law.from <= y && y <= law.until) { return Err("not available".into()); }
        let keep = match &o.law { Some((l, p)) if *l == law.id => *p, _ => 0.0 }; o.law = Some((law.id, keep)); Ok(())
    }
    pub fn law_rate(&self) -> f64 { match &self.office { Some(o) => (6.0 + (o.approval - 50.0) / 5.0) * (1.0 + self.md(o.holder, "law_pct")), None => 0.0 }.max(2.0) }

    /* ---------------- the start and end of a turn ---------------- */
    pub fn start_turn(&mut self) {
        let t = self.turn_def().clone();
        if t.kind == "election" { self.run_election(); return; }
        if t.kind == "end" { self.over = true; self.pending = Some(Pending { id: "end".into(), stage: "end".into(), result: None }); self.achieve("slice"); return; }
        for u in self.units.iter_mut().filter(|u| u.side == YOU) { u.acted = u.rattled; u.rattled = false; u.skip = false; u.flew = false; }
        let ids: Vec<u32> = self.units.iter().filter(|u| u.side == YOU).map(|u| u.id).collect();
        for id in ids { let mm = { let u = self.unit(id).unwrap(); self.max_moves(u) }; if let Some(u) = self.units.iter_mut().find(|u| u.id == id) { u.mp = mm; } }
        if t.office && self.office.is_none() { self.enter_office(); }
        self.run_standing();
        self.queue = t.open.clone(); self.next_event();
    }
    /// Next turn. Runs the economy, the rival and the world, then any end-of-turn events.
    pub fn end_turn(&mut self) -> Result<(), String> {
        if self.pending.is_some() { return Err("answer the card first".into()); } if self.over { return Err("the game is over".into()); }
        let before = self.national();
        for side in [YOU, RIVAL] { self.economy(side); }
        // field offices and HQ presence
        for side in [YOU, RIVAL] {
            let sign = if side == YOU { 1.0 } else { -1.0 }; let k = if side == RIVAL { self.rival_k } else { 1.0 };
            let fo = (1.0 + self.md(side, "office_pct")) * k; let offices = self.sides[side].offices.clone(); let who = if side == YOU { "Your".to_string() } else { format!("{}'s", self.sides[RIVAL].leader.last) };
            for h in offices { self.push_area(h, &[0.5, 0.5], sign, &format!("{who} field offices"), fo, 1.0, None); }
            let reach = if self.md(side, "hq_reach") > 0.0 { vec![0.4, 0.25, 0.12, 0.06] } else { vec![0.4, 0.25, 0.1] };
            let hqs: Vec<usize> = self.sides[side].hqs.iter().map(|q| q.hex).collect(); for h in hqs { self.push_area(h, &reach, sign, &format!("{who} HQs"), k, 1.0, None); }
            let nat = self.md(side, "national"); if nat > 0.0 { self.push_national(sign * nat, &format!("{who} narrative (doctrine)")); }
        }
        self.factions_turn();
        self.third_parties();
        self.rival_turn();
        if let Some(o) = self.office.clone() { self.office_turn(o.holder); }
        let d = self.rng.gauss() * 0.35; self.push_national(d, "The news cycle");
        let after = self.national();
        self.fx.push(json!({ "kind": "turn", "before": before, "after": after }));
        self.queue = self.turn_def().close.clone(); self.ctx.insert("closing".into(), "1".into()); self.next_event();
        if self.pending.is_none() { self.finish_turn(); }
        Ok(())
    }
    pub fn finish_turn(&mut self) {
        self.ctx.remove("closing");
        let label = self.turn_def().title.clone(); let nat = self.national(); self.history.push((label, nat));
        self.last_report = std::mem::take(&mut self.report);
        self.note("scroll", "Turn report", "Every cause of last turn's change, largest first.", json!({ "report": true }), "");
        self.turn += 1; self.start_turn();
    }
    fn economy(&mut self, side: usize) {
        let y = self.yields(side);
        let s = &mut self.sides[side];
        s.money += y["net"]; s.influence += y["inf"]; s.gp_pts += y["gp"];
        s.surge_pts += y["mom"].max(0.0); if s.surge_left > 0 { s.surge_left -= 1; }
        let surged = s.surge_pts >= s.surge_need && s.surge_left == 0; if surged { s.surge_left = 2; s.surge_pts = 0.0; s.surge_need += 15.0; }
        s.mom_bonus = if s.mom_bonus > 0.0 { (s.mom_bonus - 1.0).max(0.0) } else { (s.mom_bonus + 1.0).min(0.0) };
        if surged { if side == YOU { self.note("mom", "Surge!", "Your campaign catches fire: every push is 50% stronger for two turns.", json!(null), "good"); self.achieve("surge"); }
            else { let l = self.sides[RIVAL].leader.last.clone(); self.note("mom", &format!("{l} surges"), "Their pushes are 50% stronger for two turns.", json!(null), "bad"); } }
        // research
        let pts = y["ideas"] + self.sides[side].idea_bonus; self.sides[side].idea_bonus = 0.0;
        if side == RIVAL && self.sides[RIVAL].idea_cur.is_none() { if let Some(i) = self.data.ideas.iter().find(|i| self.idea_open(RIVAL, &i.id)) { self.sides[RIVAL].idea_cur = Some(i.id.clone()); } }
        if let Some(id) = self.sides[side].idea_cur.clone() {
            let cost = self.data.idea(&id).map(|i| i.cost).unwrap_or(99.0); let p = self.sides[side].idea_prog.entry(id.clone()).or_insert(0.0); *p += pts;
            if *p >= cost { self.sides[side].ideas_done.push(id.clone()); self.sides[side].idea_cur = None;
                if side == YOU { let i = self.data.idea(&id).unwrap().clone(); self.note(&i.icon, &format!("Idea: {}", i.name), &i.text, json!({ "screen": "ideas" }), "good"); self.quest_check(); } }
        }
        // production
        for h in 0..self.sides[side].hqs.len() {
            if side == RIVAL && self.sides[RIVAL].hqs[h].prod.is_none() { let l = if self.rng.chance(0.5) { "organizer" } else { "surrogate" }; self.sides[RIVAL].hqs[h].prod = Some(Prod { kind: "unit".into(), id: l.into(), got: 0.0 }); }
            let v = self.hq_vol(side, h); let cost = match &self.sides[side].hqs[h].prod { Some(p) => self.prod_cost(side, h, p), None => continue };
            let p = self.sides[side].hqs[h].prod.as_mut().unwrap(); p.got += v; if p.got >= cost { self.complete(side, h); }
        }
        // a Great Operative
        if self.sides[side].gp_pts >= self.sides[side].gp_need { let need = self.sides[side].gp_need; self.sides[side].gp_pts -= need; self.sides[side].gp_need += 14.0;
            if side == YOU { self.queue_gp = true; } else { let hex = self.sides[RIVAL].hqs[0].hex; let id = self.spawn("operative", hex, RIVAL); if let Some(u) = self.units.iter_mut().find(|u| u.id == id) { u.great = Some("orator".into()); } } }
    }
    fn factions_turn(&mut self) {
        let decay = 1.5 * (1.0 + self.md(YOU, "friend_decay_pct"));
        for k in 0..self.facs.len() {
            let [y, t] = self.facs[k].friend; self.set_friend(k, YOU, y - decay, ""); self.set_friend(k, RIVAL, t - 1.5, "");
            let st = self.status(k);
            if st == 2 || st == -2 {
                let side = if st == 2 { YOU } else { RIVAL }; let pull = 0.12 * (1.0 + self.md(side, "ally_pull")) * if side == RIVAL { self.rival_k } else { 1.0 };
                let sign = if side == YOU { 1.0 } else { -1.0 }; let mut ds = std::collections::BTreeMap::new();
                for si in 0..self.map.states.len() { let x = pull * affn(&self.data, k, &self.map.states[si]); if x > 0.05 { for &i in &self.map.state_hexes[si].clone() { self.lean[i] += sign * x; } ds.insert(si, sign * x); } }
                let f = self.data.factions[k].name.clone(); let l = self.sides[RIVAL].leader.last.clone();
                self.record(&if side == YOU { format!("{f}, your allies, turn out") } else { format!("{f} turn out for {l}") }, ds);
            }
        }
        // the rival courts the most promising faction not already its own
        let mut best: Option<(f64, usize)> = None;
        for k in 0..self.facs.len() { let st = self.status(k); if st >= -1 && st < 2 { let v = self.facs[k].friend[RIVAL]; if best.map_or(true, |b| v > b.0) { best = Some((v, k)); } } }
        if let Some((_, k)) = best { let v = self.facs[k].friend[RIVAL] + 7.0 * self.rival_k; self.set_friend(k, RIVAL, v, ""); }
        for k in 0..self.facs.len() { if self.status(k) == -2 { let v = self.facs[k].friend[RIVAL] + 2.0; self.set_friend(k, RIVAL, v, ""); } }
    }
    /// A faction neither party will court splits off: its own party, taking votes in its heartland.
    fn third_parties(&mut self) {
        if self.year() > 2000.84 && self.year() < 2001.0 { return; }
        for k in 0..self.facs.len() {
            let [y, t] = self.facs[k].friend;
            if let Some(tp) = &self.facs[k].third { if y >= 40.0 || t >= 40.0 { let n = tp.name.clone(); self.facs[k].third = None; self.facs[k].low = 0;
                self.note("people", &format!("The {n} folds"), &format!("{} have come home; their party stands down.", self.data.factions[k].name), json!({ "screen": "factions" }), "good"); } continue; }
            if y < 22.0 && t < 22.0 { self.facs[k].low += 1; } else { self.facs[k].low = 0; }
            if self.facs[k].low >= 2 && self.rng.chance(0.5) {
                let f = self.data.factions[k].clone();
                let hurt = if self.setup.factions.contains(&f.id) { 0.75 } else if self.facs[k].friend[RIVAL] > self.facs[k].friend[YOU] { 0.3 } else { 0.5 };
                let title = self.turn_def().title.clone();
                self.facs[k].third = Some(Third { name: f.third_party.name.clone(), short: f.third_party.short.clone(), hurt_you: hurt, cut: 0.0, formed: title });
                self.note("people", &format!("The {} is born", f.third_party.name), &format!("Ignored by both parties, {} have their own candidate now. They take votes in their heartland{}.", f.name,
                    if hurt > 0.5 { ", mostly yours" } else if hurt < 0.5 { ", mostly the rival's" } else { "" }), json!({ "screen": "factions" }), if hurt > 0.5 { "bad" } else { "good" });
            }
        }
    }

    /* ---------------- the rival ---------------- */
    fn rival_target(&self, used: &[usize]) -> Option<usize> {
        let st = self.states(); let mut best: Option<(f64, usize)> = None;
        for si in 0..st.len() { if self.map.states[si] == "DC" || used.contains(&si) || st[si] <= -8.0 || st[si] >= 7.0 { continue; }
            let v = self.ev(si) as f64 / (2.0 + (st[si] - 1.0).abs()); if best.map_or(true, |b| v > b.0) { best = Some((v, si)); } }
        best.map(|b| b.1)
    }
    pub fn rival_turn(&mut self) {
        let mut used = vec![]; let mut acts: Vec<String> = vec![];
        let ids: Vec<u32> = self.units.iter().filter(|u| u.side == RIVAL).map(|u| u.id).collect();
        for id in ids {
            let mm = { let u = self.unit(id).unwrap(); self.max_moves(u) };
            let rattled = { let u = self.units.iter_mut().find(|u| u.id == id).unwrap(); u.mp = mm; u.flew = false; let r = u.rattled; u.acted = r; u.rattled = false; r };
            if rattled { continue; }
            let u = self.unit(id).unwrap().clone();
            match u.line.as_str() {
                "candidate" | "surrogate" | "mate" | "operative" => {
                    let si = match self.rival_target(&used) { Some(s) => s, None => continue }; used.push(si);
                    let to = self.map.biggest_city(si).map(|c| self.map.cities[c].h).unwrap_or(self.map.state_hexes[si][0]);
                    self.fx.push(json!({ "kind": "fly", "unit": id, "from": u.hex, "to": to }));
                    if let Some(m) = self.units.iter_mut().find(|m| m.id == id) { m.hex = to; }
                    // contest a player's unit standing in the way, when the odds are good
                    let me = self.unit(id).unwrap().clone();
                    let target = self.targets(&me, "contest").into_iter().find(|t| { let tu = self.unit(*t).unwrap(); self.contest_odds(&me, tu, false).0 > 0.6 });
                    if u.line != "operative" { if let Some(t) = target { let _ = self.act(id, "contest", Some(t as i64)); acts.push(format!("a contest {}", self.at(to))); continue; } }
                    if u.line == "operative" { let _ = self.act(id, "great", None); acts.push(format!("a barnstorm {}", self.at(to))); continue; }
                    let a = if u.line == "candidate" { "stump" } else { "rally" }; let _ = self.act(id, a, None);
                    acts.push(format!("{} {}", if a == "stump" { "a stump speech" } else { "a rally" }, self.at(to)));
                }
                "organizer" => {
                    let (best, _) = self.reach(&u); let mut pick = u.hex; let mut pv = f64::MIN;
                    for (i, &m) in best.iter().enumerate() { if m < 0.0 { continue; } let h = &self.map.hexes[i]; let mut v = 6.0 - (self.lean[i] + 1.0).abs() + self.map.pop[i] * 0.3;
                        if h.c.is_some() && !self.sides.iter().any(|s| s.hqs.iter().any(|q| q.hex == i)) { v += 4.0; } if v > pv { pv = v; pick = i; } }
                    if let Some(m) = self.units.iter_mut().find(|m| m.id == id) { m.hex = pick; }
                    let me = self.unit(id).unwrap().clone(); let h = &self.map.hexes[pick];
                    if h.c.is_some() && self.act_block(&me, "hq").is_empty() && self.sides[RIVAL].hqs.len() < 2 + self.turn { let _ = self.act(id, "hq", None); acts.push(format!("a new HQ in {}", self.place(pick))); }
                    else if self.rng.chance(0.3) && self.act_block(&me, "office").is_empty() { let _ = self.act(id, "office", None); acts.push(format!("a field office {}", self.at(pick))); }
                    else { let _ = self.act(id, "canvass", None); }
                }
                _ => {}
            }
        }
        // the national air war: both campaigns buy it; difficulty scales only the rival's, so a fair fight nets to zero
        let k = self.rival_k; let l = self.sides[RIVAL].leader.last.clone();
        self.push_national(0.22 * (1.0 + self.md(YOU, "ads_pct") * 0.5), "Your national ads"); self.push_national(-0.22 * k * (1.0 + self.md(RIVAL, "ads_pct") * 0.5), &format!("{l}'s national ads"));
        if !acts.is_empty() { let text = acts.iter().take(5).cloned().collect::<Vec<_>>().join("; "); self.note("star", &format!("{l}'s turn"), &format!("{}{}.", text[..1].to_uppercase(), &text[1..]), json!({ "rival": true }), "bad"); }
        // the rival picks a doctrine when it can afford one
        let c = self.doctrine_cost(RIVAL);
        if self.sides[RIVAL].influence >= c { if let Some(p) = self.data.doctrines.iter().flat_map(|d| d.policies.iter()).find(|p| self.policy_open(RIVAL, &p.id)).map(|p| p.id.clone()) {
            self.sides[RIVAL].influence -= c; self.sides[RIVAL].policies.push(p); } }
    }

    /* ---------------- office ---------------- */
    pub fn enter_office(&mut self) {
        let won = self.election.as_ref().map_or(false, |e| e.won); let holder = if won { YOU } else { RIVAL };
        let nat = self.national(); let base = 52.0 + if won { nat } else { -nat } / 2.0 + 4.0;
        let wh = self.map.cities.iter().find(|c| c.s == "DC").map(|c| c.h).unwrap_or(0);
        self.office = Some(Office { holder, approval: clamp(base, 40.0, 68.0), law: None, passed: vec![], cabinet: vec![], wh_hex: wh });
        if won { self.note("capitol", "The White House is yours", "Choose the first law it will build. The map stays on the table: the 2002 midterms start now.", json!({ "city": wh }), "good"); }
    }
    fn office_turn(&mut self, holder: usize) {
        let nat = self.national(); let target = 50.0 + if holder == YOU { nat } else { -nat } / 2.0;
        if let Some(o) = self.office.as_mut() { o.approval += (target - o.approval) * 0.15; }
        if holder == RIVAL && self.office.as_ref().unwrap().law.is_none() {
            let y = self.year(); let passed = self.office.as_ref().unwrap().passed.clone();
            if let Some(l) = self.data.laws.iter().find(|l| l.from <= y && y <= l.until && !passed.contains(&l.id)) { self.office.as_mut().unwrap().law = Some((l.id.clone(), 0.0)); }
        }
        let rate = self.law_rate();
        let done = match self.office.as_mut().unwrap().law.as_mut() { Some((id, p)) => { *p += rate; let cost = self.data.law(id).map(|l| l.cost).unwrap_or(99.0); if *p >= cost { Some(id.clone()) } else { None } } None => None };
        if let Some(id) = done { self.senate_vote(&id, holder); }
    }
    fn senate_vote(&mut self, id: &str, holder: usize) {
        let law = self.data.law(id).unwrap().clone(); let o = self.office.clone().unwrap();
        let seats = self.election.as_ref().map(|e| if holder == YOU { e.senate } else { 100 - e.senate }).unwrap_or(50) as f64;
        let deal = self.sides[holder].leader.deal;
        let p = clamp(0.5 + (seats - 50.0) * 0.07 + (o.approval - 50.0) * 0.01 + (deal - 50.0) / 200.0, 0.1, 0.95);
        let draw = self.rng.f64(); let pass = draw < p; let yes = if pass { 50 + 1 + self.rng.below(7) as i32 } else { 44 + self.rng.below(6) as i32 };
        let pres = self.sides[holder].leader.last.clone();
        if pass {
            if let Some(o) = self.office.as_mut() { o.passed.push(law.id.clone()); o.law = None; o.approval += 1.0; }
            if holder == YOU { for e in law.effects.clone() { self.apply(&e, &law.name); } self.achieve("first_law"); }
            else { for e in law.effects.clone() { if ["approval", "forecast", "century", "flag"].iter().any(|k| e.get(*k).is_some()) { self.apply(&e, &law.name); } } }
        } else if let Some(o) = self.office.as_mut() { if let Some((_, pr)) = o.law.as_mut() { *pr = law.cost / 2.0; } o.approval -= 1.5; }
        self.fx.push(json!({ "kind": "vote", "law": law.name, "pass": pass, "yes": yes, "p": p, "draw": draw, "holder": holder }));
        self.note("capitol", &format!("{}: {} {}–{}", law.name, if pass { "passes" } else { "fails" }, yes, 100 - yes),
            &if pass { format!("President {pres} signs the {} into law.", law.name) } else { "Back to committee; half the work is lost.".to_string() }, json!(null), if pass == (holder == YOU) { "good" } else { "bad" });
    }

    /* ---------------- election night ---------------- */
    pub fn run_election(&mut self) {
        let n = self.map.states.len(); let nat_noise = self.rng.gauss() * 1.4; let mut m = vec![0.0; n];
        for si in 0..n {
            let mut v = self.margin(si) + nat_noise + self.rng.gauss() * 1.6;
            for side in [YOU, RIVAL] { let sign = if side == YOU { 1.0 } else { -1.0 };
                let eo = self.md(side, "election_office"); if eo > 0.0 { v += sign * eo * (self.sides[side].offices.iter().filter(|&&h| self.map.state_of[h] == Some(si)).count() as f64).min(2.0); }
                let ec = self.md(side, "election_close"); if ec > 0.0 && sign * v > -3.0 && sign * v < 0.0 { v += sign * ec; } }
            m[si] = v;
        }
        // history mode: a close 2000 election comes down to Florida. Only a race that is already close is nudged there,
        // and only states inside the margin of error can flip; a landslide or a collapse stands, so the campaign counts.
        let fl = self.map.state_index("FL"); let mut recount = false;
        if let (Some(fl), true) = (fl, self.year() < 2001.0) {
            let ev = |g: &Game, s: usize| g.ev(s) as i32; let mut ex: i32 = (0..n).filter(|&s| s != fl && m[s] > 0.0).map(|s| ev(self, s)).sum();
            const CLOSE: f64 = 2.5;
            if (230..=290).contains(&ex) {
                for _ in 0..12 { if (245..=268).contains(&ex) { break; }
                    if ex > 268 { if let Some(s) = (0..n).filter(|&s| s != fl && m[s] > 0.0 && m[s] < CLOSE && ev(self, s) <= ex - 245).min_by(|a, b| m[*a].partial_cmp(&m[*b]).unwrap()) { m[s] = -0.4; ex -= ev(self, s); } else { break; } }
                    else if let Some(s) = (0..n).filter(|&s| s != fl && m[s] <= 0.0 && m[s] > -CLOSE && ev(self, s) <= 268 - ex).max_by(|a, b| m[*a].partial_cmp(&m[*b]).unwrap()) { m[s] = 0.4; ex += ev(self, s); } else { break; } }
            }
            if (245..=268).contains(&ex) && m[fl].abs() < 4.0 { m[fl] = if m[fl] >= 0.0 { 0.02 } else { -0.02 }; recount = true; }
        }
        let mut evs = [0u32, 0u32]; for si in 0..n { if recount && Some(si) == fl { continue; } if m[si] > 0.0 { evs[0] += self.ev(si) } else { evs[1] += self.ev(si) } }
        let wn: f64 = (0..n).map(|s| weight(&self.data, s, "2020")).sum(); let nat: f64 = (0..n).map(|s| weight(&self.data, s, "2020") * m[s]).sum::<f64>() / wn;
        let senate = clamp((50.0 + nat * 0.7 + self.rng.gauss() * 1.2).round(), 42.0, 60.0) as i32; let house = clamp((218.0 + nat * 6.0 + self.rng.gauss() * 5.0).round(), 180.0, 262.0) as i32;
        let third_votes = self.facs.iter().filter_map(|f| f.third.as_ref().map(|t| (t.name.clone(), 0.0))).collect();
        self.election = Some(Election { margins: m, ev: evs, won: evs[0] >= 270, senate, house, florida_recount: recount, third_votes });
        if evs[0] >= 350 { self.achieve("landslide"); }
        self.pending = Some(Pending { id: "election".into(), stage: "night".into(), result: None });
    }
    pub fn recount_odds(&self) -> [f64; 3] {
        let lead = self.election.as_ref().map(|e| self.map.state_index("FL").map_or(false, |fl| e.margins[fl] > 0.0)).unwrap_or(false);
        let lawyers = self.units.iter().filter(|u| u.side == YOU && u.line == "lawyer" && self.map.state_of[u.hex] == self.map.state_index("FL")).count() as f64 * 0.06;
        if lead { [0.62 + lawyers, 0.6 + lawyers, 0.85] } else { [0.42 + lawyers, 0.46 + lawyers, 0.12] }
    }
}
