//! The event engine. Events are data (`data/events.json`): a condition, context providers, text with {placeholders},
//! and choices whose effects are a small language interpreted here. See `docs/MODDING.md`.
use crate::game::*;
use serde_json::{json, Value};

impl Game {
    /* ---------------- the queue ---------------- */
    pub fn next_event(&mut self) {
        if self.queue_gp { self.queue_gp = false; self.pending = Some(Pending { id: "gp".into(), stage: "card".into(), result: None }); return; }
        while !self.queue.is_empty() {
            let id = self.queue.remove(0);
            let ev = match self.data.event(&id) { Some(e) => e.clone(), None => continue };
            if let Some(c) = &ev.cond { if !self.cond(c) { continue; } }
            for p in &ev.context { self.provide(p); }
            if ev.kind == "roll" {
                let var = ev.forecast.as_ref().and_then(|v| v.as_str()).unwrap_or("threat").to_string();
                let p = self.forecasts.get(&var).copied().unwrap_or(50.0) / 100.0; let draw = self.rng.f64();
                self.fx.push(json!({ "kind": "roll", "var": var, "p": p, "draw": draw }));
                let nx = if draw < p { ev.hit.clone() } else { ev.miss.clone() }; if let Some(n) = nx { self.queue.insert(0, n); } continue;
            }
            if ev.kind == "mate" { self.offer = (0..3).map(|i| { let f = self.setup.factions[i].clone(); let k = self.data.faction(&f); let boost = ["cha", "pol", "deal"][i];
                let st = if i == 2 { Some(*self.rng.pick(&["OH", "PA", "MI", "WI", "FL", "GA", "AZ", "NC"])) } else { None }; self.talent(k, Some(boost), st, None, None) }).collect(); }
            if ev.kind == "cabinet" { self.offer = vec![];
                for (i, b) in ["deal", "cha", "pol"].iter().enumerate() {
                    if i == 0 && self.flags.contains("promise") { if let Some(c) = self.challenger.clone() { self.offer.push(c); } else { let k = self.data.faction(&self.setup.factions[0]); let t = self.talent(k, Some(b), None, None, None); self.offer.push(t); } }
                    else { let k = self.data.faction(&self.setup.factions[i % 3]); let t = self.talent(k, Some(b), None, None, None); self.offer.push(t); }
                    let outside: Vec<usize> = (0..self.data.factions.len()).filter(|&k| self.status(k) <= 0).collect(); let k = if outside.is_empty() { None } else { Some(*self.rng.pick(&outside)) };
                    let t = self.talent(k, Some(b), None, None, None); self.offer.push(t);
                } }
            self.pending = Some(Pending { id, stage: "card".into(), result: None });
            return;
        }
        if self.ctx.contains_key("closing") && self.pending.is_none() { /* the caller finishes the turn */ }
    }
    /// After a card is answered: the next card, or the end of the turn if we were closing it.
    fn after_card(&mut self) {
        self.pending = None; self.next_event();
        if self.pending.is_none() && self.ctx.contains_key("closing") { self.finish_turn(); }
    }

    /* ---------------- conditions and context ---------------- */
    /// A condition that needs no dice (used for choices, and by the view).
    pub fn cond_static(&self, c: &Value) -> bool {
        if let Some(f) = c.get("flag").and_then(|v| v.as_str()) { if !self.flags.contains(f) { return false; } }
        if let Some(f) = c.get("not_flag").and_then(|v| v.as_str()) { if self.flags.contains(f) { return false; } }
        if let Some(w) = c.get("won").and_then(|v| v.as_bool()) { if self.election.as_ref().map_or(false, |e| e.won) != w { return false; } }
        if let Some(w) = c.get("third_party").and_then(|v| v.as_bool()) { if self.facs.iter().any(|f| f.third.is_some()) != w { return false; } }
        if let Some(m) = c.get("mod").and_then(|v| v.as_str()) { if self.md(YOU, m) <= 0.0 { return false; } }
        if let Some(all) = c.get("all").and_then(|v| v.as_array()) { for x in all { if !self.cond_static(x) { return false; } } }
        true
    }
    pub fn cond(&mut self, c: &Value) -> bool {
        if let Some(f) = c.get("flag").and_then(|v| v.as_str()) { if !self.flags.contains(f) { return false; } }
        if let Some(f) = c.get("not_flag").and_then(|v| v.as_str()) { if self.flags.contains(f) { return false; } }
        if let Some(w) = c.get("won").and_then(|v| v.as_bool()) { if self.election.as_ref().map_or(false, |e| e.won) != w { return false; } }
        if let Some(w) = c.get("third_party").and_then(|v| v.as_bool()) { if self.facs.iter().any(|f| f.third.is_some()) != w { return false; } }
        if let Some(m) = c.get("mod").and_then(|v| v.as_str()) { if self.md(YOU, m) <= 0.0 { return false; } }
        if c.get("mate_risky").and_then(|v| v.as_bool()) == Some(true) {
            let r = match &self.mate { Some(m) => m.risk + if m.vetted { -0.2 } else { 0.1 }, None => return false }; if !self.rng.chance(r) { return false; }
        }
        if let Some(all) = c.get("all").and_then(|v| v.as_array()) { for x in all.clone() { if !self.cond(&x) { return false; } } }
        true
    }
    fn provide(&mut self, p: &str) {
        match p {
            "weakest" => {
                let mine: Vec<usize> = self.setup.factions.iter().filter_map(|f| self.data.faction(f)).collect();
                let k = *mine.iter().min_by(|a, b| self.facs[**a].friend[YOU].partial_cmp(&self.facs[**b].friend[YOU]).unwrap()).unwrap();
                let f = self.data.factions[k].clone(); let d = self.data.demands.get("era").and_then(|d| d.get(&f.id)).cloned().unwrap_or_default();
                let dn = self.data.plank(&d).map(|p| p.name.clone()).unwrap_or_default();
                self.ctx.insert("weakest".into(), f.id); self.ctx.insert("weakest_name".into(), f.name); self.ctx.insert("weakest_demand".into(), d); self.ctx.insert("weakest_demand_name".into(), dn);
            }
            "challenger" => {
                let k = self.ctx.get("weakest").and_then(|f| self.data.faction(f)); let t = self.talent(k, Some("cha"), Some("NH"), Some("Governor of New Hampshire".into()), Some(44));
                self.ctx.insert("challenger".into(), t.name.clone()); self.ctx.insert("challenger_last".into(), t.last.clone()); self.ctx.insert("challenger_office".into(), t.office.clone());
                self.challenger = Some(t);
            }
            "mate" => { if let Some(m) = self.mate.clone() { self.ctx.insert("mate".into(), m.name); self.ctx.insert("mate_first".into(), m.first); self.ctx.insert("mate_last".into(), m.last); } }
            "third" => {
                let best = self.facs.iter().enumerate().filter(|(_, f)| f.third.is_some()).map(|(k, _)| k).next();
                if let Some(k) = best { let t = self.facs[k].third.clone().unwrap(); let f = self.data.factions[k].clone();
                    let top = (0..self.map.states.len()).map(|s| third_share(&self.data, &self.map, k, s)).fold(0.0, f64::max) * (1.0 - t.cut);
                    self.ctx.insert("third_name".into(), t.name); self.ctx.insert("third_faction".into(), f.name); self.ctx.insert("third_faction_id".into(), f.id); self.ctx.insert("third_share".into(), format!("{:.0}", top)); }
            }
            _ => {}
        }
    }
    pub fn fill(&self, text: &str) -> String {
        let mut out = String::with_capacity(text.len()); let mut rest = text;
        while let Some(i) = rest.find('{') { out.push_str(&rest[..i]); let after = &rest[i + 1..];
            if let Some(j) = after.find('}') { let k = &after[..j]; out.push_str(&self.var(k)); rest = &after[j + 1..]; } else { out.push_str(&rest[i..]); rest = ""; } }
        out.push_str(rest); out
    }
    fn var(&self, k: &str) -> String {
        let (y, r) = (&self.sides[YOU], &self.sides[RIVAL]);
        match k {
            "leader" => y.leader.name.clone(), "leader_last" => y.leader.last.clone(), "leader_state" => state_name(&self.data, &y.leader.state), "party" => y.party.clone(),
            "rival" => r.leader.name.clone(), "rival_last" => r.leader.last.clone(), "rival_party" => r.party.clone(),
            "ev_you" => self.election.as_ref().map(|e| e.ev[0].to_string()).unwrap_or_default(),
            "senate" => self.election.as_ref().map(|e| e.senate.to_string()).unwrap_or_default(), "house" => self.election.as_ref().map(|e| e.house.to_string()).unwrap_or_default(),
            "threat" => format!("{:.0}", self.forecasts.get("threat").copied().unwrap_or(0.0)),
            "year_end_line" => {
                let won = self.election.as_ref().map_or(false, |e| e.won); let laws = self.office.as_ref().map(|o| o.passed.len()).unwrap_or(0);
                let sept = if self.flags.contains("attacked") { "The country is at war in Afghanistan." } else if self.flags.contains("foiled") { "The plot that was foiled is still mostly a secret." } else { "" };
                if won { format!("President {} has signed {} law{}. {}", y.leader.last, laws, if laws == 1 { "" } else { "s" }, sept) } else { format!("The {} Party leads the opposition. {}", y.party, sept) }
            }
            _ => self.ctx.get(k).cloned().unwrap_or_else(|| format!("{{{k}}}")),
        }
    }

    /* ---------------- odds and choices ---------------- */
    pub fn odds(&self, o: &Value) -> f64 {
        if let Some(f) = o.get("fixed").and_then(|v| v.as_f64()) { return f; }
        let base = o.get("base").and_then(|v| v.as_f64()).unwrap_or(0.5); let per = o.get("per").and_then(|v| v.as_f64()).unwrap_or(0.01);
        let stat = o.get("stat").and_then(|v| v.as_str()).unwrap_or("cha"); let l = &self.sides[YOU].leader;
        let mine = match stat { "pol" => l.pol, "deal" => l.deal, "integ" => l.integ, _ => l.cha };
        let theirs = if o.get("vs").and_then(|v| v.as_str()) == Some("challenger") { self.challenger.as_ref().map(|t| match stat { "pol" => t.pol, "deal" => t.deal, "integ" => t.integ, _ => t.cha }).unwrap_or(50.0) } else { 50.0 };
        clamp(base + (mine - theirs) * per, 0.05, 0.95)
    }
    /// The choices of the pending card that are open to you, with their indexes in the data.
    pub fn open_choices(&mut self) -> Vec<(usize, Value)> {
        let id = match &self.pending { Some(p) => p.id.clone(), None => return vec![] };
        let ev = match self.data.event(&id) { Some(e) => e.clone(), None => return vec![] };
        let mut out = vec![]; for (i, c) in ev.choices.iter().enumerate() { if let Some(cd) = c.get("if") { if !self.cond_static(cd) { continue; } } out.push((i, c.clone())); } out
    }
    pub fn choose(&mut self, n: usize, picks: &[usize]) -> Result<(), String> {
        let p = self.pending.clone().ok_or("no card to answer")?;
        match p.id.as_str() {
            "end" => return Err("the slice is over".into()),
            "gp" => return self.choose_gp(n),
            "election" => return self.after_election(n),
            "recount" => return self.choose_recount(n),
            _ => {}
        }
        if p.stage == "result" { self.after_card(); return Ok(()); }
        let ev = self.data.event(&p.id).ok_or("unknown event")?.clone();
        match ev.kind.as_str() {
            "intro" | "headline" => { for e in &ev.effects { self.apply(e, &ev.title); } self.after_card(); Ok(()) }
            "mate" => self.choose_mate(n),
            "cabinet" => self.choose_cabinet(picks),
            _ => {
                let choices = self.open_choices(); let (_, c) = choices.iter().find(|(i, _)| *i == n).cloned().ok_or("no such choice")?;
                let title = self.fill(&ev.title);
                let label = self.fill(c.get("label").and_then(|v| v.as_str()).unwrap_or(""));
                let mut result: Option<Value> = None;
                if let Some(o) = c.get("odds") {
                    let pr = self.odds(o); let draw = self.rng.f64(); let win = draw < pr;
                    let key = if win { "win" } else { "lose" }; for e in c.get(key).and_then(|v| v.as_array()).cloned().unwrap_or_default() { self.apply(&e, &title); }
                    let r = c.get("result").cloned().unwrap_or(json!({}));
                    let t = r.get(key).and_then(|v| v.as_str()).map(|s| self.fill(s)).unwrap_or_else(|| if win { "It worked".into() } else { "It did not work".into() });
                    let b = r.get(if win { "body_win" } else { "body_lose" }).and_then(|v| v.as_str()).map(|s| self.fill(s)).unwrap_or_default();
                    result = Some(json!({ "title": t, "body": b, "roll": { "p": pr, "draw": draw, "win": win }, "choice": label }));
                } else {
                    for e in c.get("effects").and_then(|v| v.as_array()).cloned().unwrap_or_default() { self.apply(&e, &title); }
                    if let Some(r) = c.get("result") { result = Some(json!({ "title": self.fill(r.get("title").and_then(|v| v.as_str()).unwrap_or("")), "body": self.fill(r.get("body").and_then(|v| v.as_str()).unwrap_or("")), "choice": label })); }
                }
                if let Some(r) = result { self.pending = Some(Pending { id: p.id.clone(), stage: "result".into(), result: Some(r) }); } else { self.after_card(); }
                Ok(())
            }
        }
    }
    pub fn vet(&mut self, n: usize) -> Result<(), String> {
        if self.sides[YOU].money < 5.0 { return Err("needs $5".into()); }
        let t = self.offer.get_mut(n).ok_or("no such candidate")?; if t.vetted { return Ok(()); } t.vetted = true; self.sides[YOU].money -= 5.0; Ok(())
    }
    fn choose_mate(&mut self, n: usize) -> Result<(), String> {
        let t = self.offer.get(n).cloned().ok_or("choose one of the three")?;
        let si = self.map.state_index(&t.state).unwrap(); let hex = self.map.biggest_city(si).map(|c| self.map.cities[c].h).unwrap_or(self.map.state_hexes[si][0]);
        let id = self.spawn("mate", hex, YOU); if let Some(u) = self.units.iter_mut().find(|u| u.id == id) { u.name = Some(t.name.clone()); u.home = Some(t.state.clone()); }
        self.push_state(si, 2.5, &format!("{} on the ticket", t.name));
        if let Some(k) = self.data.faction(&t.faction) { self.gain(k, 10.0, &format!("{} is on the ticket.", t.name)); }
        self.note("medal", &format!("{} joins the ticket", t.last), &format!("{} is on the board in {}.", t.name, self.place(hex)), json!({ "unit": id }), "good");
        self.mate = Some(t); self.offer.clear(); self.after_card(); Ok(())
    }
    fn choose_cabinet(&mut self, picks: &[usize]) -> Result<(), String> {
        if picks.len() != 3 { return Err("nominate one person for each post".into()); }
        let posts = ["Treasury", "State", "Defense"]; let mut cab = vec![];
        for (i, &k) in picks.iter().enumerate() { if k / 2 != i { return Err("each nominee must come from their post's shortlist".into()); } cab.push((posts[i].to_string(), self.offer[k].clone())); }
        let mut unity = 0.0;
        for (_, t) in &cab { let k = self.data.faction(&t.faction); if let Some(k) = k { if self.status(k) == 2 { self.gain(k, 5.0, ""); } else { unity += 1.5; } } }
        if let Some(o) = self.office.as_mut() { o.cabinet = cab; o.approval += unity; }
        if self.flags.contains("promise") { let ch = self.challenger.as_ref().map(|c| c.id.clone()); let kept = self.office.as_ref().unwrap().cabinet.iter().any(|(_, t)| Some(&t.id) == ch.as_ref());
            if !kept { if let Some(k) = self.ctx.get("weakest").and_then(|f| self.data.faction(f)) { self.gain(k, -15.0, "You broke the promise."); } } }
        self.offer.clear(); self.after_card(); Ok(())
    }
    fn choose_gp(&mut self, n: usize) -> Result<(), String> {
        let hex = self.sides[YOU].hqs[0].hex;
        match n { 0 | 1 => { let id = self.spawn("operative", hex, YOU); let kind = if n == 0 { "strategist" } else { "orator" };
                let name = format!("{} {}", self.rng.pick(&self.data.names.first.clone()), self.rng.pick(&self.data.names.last.clone()));
                if let Some(u) = self.units.iter_mut().find(|u| u.id == id) { u.great = Some(kind.into()); u.name = Some(name.clone()); }
                self.note("laurel", &format!("Great {} ready", if n == 0 { "Strategist" } else { "Orator" }), &format!("{name} waits in your capital. Fly them where it counts."), json!({ "unit": id }), "good"); }
            _ => { self.sides[YOU].money += 40.0; } }
        self.achieve("great"); self.after_card(); Ok(())
    }
    fn after_election(&mut self, _n: usize) -> Result<(), String> {
        if self.election.as_ref().map_or(false, |e| e.florida_recount) && !self.flags.contains("recounted") { self.pending = Some(Pending { id: "recount".into(), stage: "card".into(), result: None }); self.achieve("chads"); return Ok(()); }
        self.pending = None; self.turn += 1; self.start_turn(); Ok(())
    }
    fn choose_recount(&mut self, n: usize) -> Result<(), String> {
        let p = self.pending.clone().unwrap(); if p.stage == "result" { self.flags.insert("recounted".into()); return self.after_election(0); }
        let odds = self.recount_odds(); let pr = *odds.get(n).ok_or("no such choice")?; let draw = self.rng.f64(); let win = draw < pr;
        let fl = self.map.state_index("FL").unwrap(); let ev = self.ev(fl);
        if n == 1 { self.push_national(-0.5, "The four-county recount looked partisan"); }
        if let Some(e) = self.election.as_mut() { e.margins[fl] = if win { 0.01 } else { -0.01 }; if win { e.ev[0] += ev } else { e.ev[1] += ev } e.won = e.ev[0] >= 270; }
        let rl = self.sides[RIVAL].leader.last.clone();
        self.pending = Some(Pending { id: "recount".into(), stage: "result".into(), result: Some(json!({ "title": if win { "Florida is yours, and the presidency".to_string() } else { format!("Florida goes to {rl}") },
            "body": if win { "By a few hundred votes. Half the country thinks it was stolen; the other half is relieved." } else { "You concede in a speech people will quote for years. Your party goes into opposition." },
            "roll": { "p": pr, "draw": draw, "win": win } })) });
        Ok(())
    }

    /* ---------------- the effects language ---------------- */
    fn faction_ids(&self, v: &str) -> Vec<usize> {
        if v == "$allies" { return (0..self.facs.len()).filter(|&k| self.status(k) == 2).collect(); }
        let id = if let Some(var) = v.strip_prefix('$') { self.ctx.get(var).cloned().unwrap_or_default() } else { v.to_string() };
        self.data.faction(&id).into_iter().collect()
    }
    pub fn apply(&mut self, e: &Value, why: &str) {
        let num = |k: &str| e.get(k).and_then(|v| v.as_f64());
        let add = num("add").unwrap_or(0.0);
        if let Some(x) = num("nat") { self.push_national(x, why); }
        if let Some(x) = num("scandal") { let m = 1.0 + self.md(YOU, "scandal_pct"); self.push_national(x * m, why); }
        if let Some(s) = e.get("state").and_then(|v| v.as_str()) { if let Some(si) = self.map.state_index(s) { self.push_state(si, add, why); } }
        if let Some(f) = e.get("friend").and_then(|v| v.as_str()) { for k in self.faction_ids(f) { self.gain(k, add, ""); } }
        if let Some(x) = num("momentum") { self.sides[YOU].mom_bonus += x; }
        if let Some(x) = num("rival_momentum") { self.sides[RIVAL].mom_bonus += x; }
        if let Some(x) = num("money") { self.sides[YOU].money += x; }
        if let Some(x) = num("influence") { self.sides[YOU].influence += x; }
        if let Some(x) = num("ideas") { self.sides[YOU].idea_bonus += x; }
        if let Some(x) = num("approval") { if let Some(o) = self.office.as_mut() { o.approval = clamp(o.approval + x, 10.0, 95.0); } }
        if let Some(f) = e.get("flag").and_then(|v| v.as_str()) { self.flags.insert(f.into()); }
        if let Some(f) = e.get("forecast").and_then(|v| v.as_str()) { let v = self.forecasts.entry(f.into()).or_insert(50.0); *v = clamp(*v + add, 2.0, 98.0); }
        if let Some(c) = e.get("century").and_then(|v| v.as_str()) { *self.century_shift.entry(c.into()).or_insert(0.0) += add; }
        if let Some(c) = e.get("attitude").and_then(|v| v.as_str()) { let a = self.attitude.entry(c.into()).or_insert(0); *a = (*a + add as i32).clamp(-2, 2); }
        if let Some(p) = e.get("pledge").and_then(|v| v.as_str()) { let id = if let Some(var) = p.strip_prefix('$') { self.ctx.get(var).cloned().unwrap_or_default() } else { p.into() };
            let free = e.get("free").and_then(|v| v.as_bool()).unwrap_or(false); let _ = self.pledge(&id, free); }
        if let Some(l) = e.get("law_start").and_then(|v| v.as_str()) { if self.start_law(l).is_ok() { if let Some((_, p)) = self.office.as_mut().and_then(|o| o.law.as_mut()) { *p += num("progress").unwrap_or(0.0); } } }
        if let Some(x) = num("third_cut") { if let Some(k) = self.ctx.get("third_faction_id").and_then(|f| self.data.faction(f)) { if let Some(t) = self.facs[k].third.as_mut() { t.cut = clamp(t.cut + x, 0.0, 0.9); } } }
    }
}
