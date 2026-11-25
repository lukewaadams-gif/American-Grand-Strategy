//! What the interface sees: one JSON view of the game, with every number the panels need already worked out.
use crate::game::*;
use serde_json::{json, Value};

fn r2(v: f64) -> f64 { (v * 100.0).round() / 100.0 }

impl Game {
    pub fn view(&self) -> Value {
        let t = self.turn_def(); let you = &self.sides[YOU]; let rv = &self.sides[RIVAL]; let y = self.yields(YOU);
        let election_at = self.data.calendar.turns.iter().position(|x| x.kind == "election");
        let hq = |side: usize| -> Vec<Value> { self.sides[side].hqs.iter().enumerate().map(|(h, q)| {
            let c = &self.map.cities[q.city]; let vol = self.hq_vol(side, h);
            let prod = q.prod.as_ref().map(|p| { let cost = self.prod_cost(side, h, p); let name = if p.kind == "unit" { self.unit_def_for(side, &p.id).name } else { self.data.building(&p.id).map(|b| b.name.clone()).unwrap_or_default() };
                json!({ "kind": p.kind, "id": p.id, "name": name, "got": r2(p.got), "cost": cost, "turns": ((cost - p.got) / vol.max(1.0)).ceil().max(1.0) }) });
            json!({ "hex": q.hex, "city": q.city, "name": c.name, "pop": c.pop, "vol": vol, "prod": prod, "buildings": q.buildings, "buy": self.buy_cost(side, h), "capital": h == 0 })
        }).collect() };
        let units: Vec<Value> = self.units.iter().map(|u| { let d = self.unit_def(u);
            let acts: Vec<Value> = if u.side == YOU { let mut a = d.actions.clone(); a.push("skip".into()); a.iter().map(|x| { let h = self.act_hint(u, x); json!({ "id": x, "block": h["block"], "hint": h["text"], "rings": h["rings"], "targets": self.targets(u, x) }) }).collect() } else { vec![] };
            json!({ "id": u.id, "line": u.line, "side": u.side, "hex": u.hex, "name": u.name, "kind": d.name, "icon": d.icon, "mp": r2(u.mp), "max": self.max_moves(u), "acted": u.acted, "skip": u.skip, "standing": u.standing,
                "rattled": u.rattled, "flew": u.flew, "sight": d.sight, "power": d.power, "great": u.great, "home": u.home, "actions": acts, "upkeep": d.upkeep, "role": self.data.line(&u.line).map(|l| l.role.clone()) })
        }).collect();
        let states: Vec<Value> = (0..self.map.states.len()).map(|si| json!({ "code": self.map.states[si], "name": state_name(&self.data, &self.map.states[si]), "ev": self.ev(si),
            "margin": r2(self.margin(si)), "seen": r2(self.seen_margin(si)), "polled": self.polled[si], "third": r2(self.third_drag(si)) })).collect();
        let factions: Vec<Value> = self.data.factions.iter().enumerate().map(|(k, f)| { let fs = &self.facs[k];
            json!({ "id": f.id, "name": f.name, "who": f.who, "icon": f.icon, "you": r2(fs.friend[0]), "rival": r2(fs.friend[1]), "status": self.status(k), "bonus": f.bonus.text,
                "quest": self.quests[k], "third": fs.third, "third_name": f.third_party.name, "founding": self.setup.factions.contains(&f.id),
                "heart": (0..self.map.states.len()).max_by(|a, b| affn(&self.data, k, &self.map.states[*a]).partial_cmp(&affn(&self.data, k, &self.map.states[*b])).unwrap()).map(|s| self.map.states[s].clone()) })
        }).collect();
        let ideas: Vec<Value> = self.data.ideas.iter().map(|i| { let st = if you.ideas_done.contains(&i.id) { "done" } else if you.idea_cur.as_deref() == Some(&i.id) { "cur" } else if self.idea_open(YOU, &i.id) { "open" } else if i.year > self.year().floor() { "year" } else { "locked" };
            json!({ "id": i.id, "name": i.name, "year": i.year, "cost": i.cost, "requires": i.requires, "icon": i.icon, "text": i.text, "prog": r2(you.idea_prog.get(&i.id).copied().unwrap_or(0.0)), "state": st }) }).collect();
        let doctrines: Vec<Value> = self.data.doctrines.iter().map(|d| json!({ "id": d.id, "name": d.name, "tint": d.tint, "blurb": d.blurb,
            "policies": d.policies.iter().map(|p| json!({ "id": p.id, "name": p.name, "text": p.text, "state": if you.policies.contains(&p.id) { "done" } else if self.policy_open(YOU, &p.id) { "open" } else { "locked" } })).collect::<Vec<_>>() })).collect();
        let planks: Vec<Value> = self.data.planks.iter().filter(|p| p.from <= self.year() && self.year() <= p.until).map(|p| json!({ "id": p.id, "name": p.name, "blurb": p.blurb, "national": p.national, "factions": p.factions,
            "forecast": p.forecast, "pledged": you.planks.contains(&p.id),
            "wanted": self.quests.iter().enumerate().filter(|(_, q)| q.as_ref().map_or(false, |q| q.kind == "plank" && q.target == p.id && !q.done)).map(|(k, _)| self.data.factions[k].name.clone()).collect::<Vec<_>>() })).collect();
        let laws: Vec<Value> = self.data.laws.iter().filter(|l| l.from <= self.year() && self.year() <= l.until).map(|l| { let pl = l.plank.as_ref().and_then(|p| self.data.plank(p));
            json!({ "id": l.id, "name": l.name, "cost": l.cost, "text": l.text, "promised": l.plank.as_ref().map_or(false, |p| you.planks.contains(p)), "forecast": pl.map(|p| json!({ "national": p.national, "factions": p.factions, "lines": p.forecast })),
                "passed": self.office.as_ref().map_or(false, |o| o.passed.contains(&l.id)) }) }).collect();
        let office = self.office.as_ref().map(|o| { let senate = self.election.as_ref().map(|e| e.senate).unwrap_or(50);
            json!({ "holder": o.holder, "approval": r2(o.approval), "law": o.law.as_ref().map(|(id, p)| json!({ "id": id, "name": self.data.law(id).map(|l| l.name.clone()), "progress": r2(*p), "cost": self.data.law(id).map(|l| l.cost) })),
                "rate": r2(self.law_rate()), "passed": o.passed, "cabinet": o.cabinet, "wh": o.wh_hex, "senate": senate }) });
        let century: Vec<Value> = self.data.century.iter().map(|c| json!({ "id": c.id, "name": c.name, "lane": c.lane, "year": c.year + self.century_shift.get(&c.id).copied().unwrap_or(0.0), "spread": c.spread, "kind": c.kind, "note": c.note })).collect();
        let powers: Vec<Value> = self.data.powers.era.iter().map(|p| json!({ "c": p.c, "who": p.who, "att": self.attitude.get(&p.c).copied().unwrap_or(p.att), "agenda": p.agenda, "look": p.look })).collect();
        let report: Vec<&Cause> = { let mut v: Vec<&Cause> = self.last_report.iter().filter(|c| c.nat.abs() > 0.002).collect(); v.sort_by(|a, b| b.nat.abs().partial_cmp(&a.nat.abs()).unwrap()); v.truncate(16); v };
        let n = self.national();
        let lines = ["organizer", "surrogate", "pollster", "ads", "lawyer"];
        let build = json!({
            "units": lines.iter().map(|l| { let d = self.unit_def_for(YOU, l); json!({ "line": l, "name": d.name, "cost": d.cost, "icon": d.icon, "upkeep": d.upkeep, "role": self.data.line(l).map(|x| x.role.clone()) }) }).collect::<Vec<_>>(),
            "buildings": self.data.buildings.iter().map(|b| json!({ "id": b.id, "name": b.name, "cost": b.cost, "icon": b.icon, "text": b.text, "requires": b.requires.as_ref().and_then(|r| self.data.idea(r)).map(|i| i.name.clone()),
                "open": b.requires.as_ref().map_or(true, |r| you.ideas_done.contains(r)) })).collect::<Vec<_>>() });
        json!({ "build": build,
            "turn": self.turn, "title": t.title, "label": t.label, "kind": t.kind, "year": t.year, "office_turn": t.office, "seed": self.seed, "over": self.over,
            "election_in": election_at.map(|e| e as i64 - self.turn as i64), "turns": self.data.calendar.turns.iter().map(|x| json!({ "title": x.title, "kind": x.kind })).collect::<Vec<_>>(),
            "you": { "party": you.party, "leader": you.leader, "money": r2(you.money), "influence": r2(you.influence), "yields": y, "ideas_done": you.ideas_done,
                "idea_cur": you.idea_cur.as_ref().map(|id| { let i = self.data.idea(id).unwrap(); let p = you.idea_prog.get(id).copied().unwrap_or(0.0);
                    json!({ "id": id, "name": i.name, "icon": i.icon, "text": i.text, "prog": r2(p), "cost": i.cost, "turns": ((i.cost - p) / y["ideas"].max(1.0)).ceil().max(1.0) }) }),
                "policies": you.policies, "planks": you.planks, "surge": { "pts": r2(you.surge_pts), "need": you.surge_need, "left": you.surge_left }, "gp": { "pts": r2(you.gp_pts), "need": you.gp_need },
                "doctrine_cost": self.doctrine_cost(YOU), "plank_cost": self.plank_cost(YOU), "offices": you.offices, "hqs": hq(YOU), "unique": self.leader_def(YOU).unit.name, "ability": self.leader_def(YOU).ability.name },
            "rival": { "party": rv.party, "leader": rv.leader, "persona": rv.persona, "hqs": hq(RIVAL), "offices": rv.offices, "policies": rv.policies, "money": rv.money.round(),
                "ability": self.leader_def(RIVAL).ability.name, "ability_text": self.leader_def(RIVAL).ability.text },
            "units": units, "lean": self.lean.iter().map(|v| (v * 10.0).round() / 10.0).collect::<Vec<_>>(), "states": states, "projection": self.projection(), "national": r2(50.0 + n / 2.0),
            "factions": factions, "ideas": ideas, "doctrines": doctrines, "planks": planks, "laws": laws, "office": office, "forecasts": self.forecasts, "century": century, "powers": powers,
            "pending": self.pending_view(), "notes": self.notes, "report": report, "history": self.history, "achievements": self.achievements,
            "election": self.election.as_ref().map(|e| json!({ "ev": e.ev, "won": e.won, "senate": e.senate, "house": e.house })), "flags": self.flags,
        })
    }

    fn forecast_card(&self, f: &Value) -> Value {
        let lines: Vec<Value> = f.get("lines").and_then(|v| v.as_array()).cloned().unwrap_or_default().into_iter().map(|mut l| {
            if let Some(var) = f.get("var").and_then(|v| v.as_str()) { let mid = self.forecasts.get(var).copied().unwrap_or(50.0); let sp = l.get("spread").and_then(|v| v.as_f64()).unwrap_or(10.0);
                l["mid"] = json!(mid); l["lo"] = json!(clamp(mid - sp, 1.0, 99.0)); l["hi"] = json!(clamp(mid + sp, 1.0, 99.0)); }
            l }).collect();
        json!({ "title": f.get("title"), "tag": f.get("tag"), "lines": lines })
    }
    fn pending_view(&self) -> Value {
        let p = match &self.pending { Some(p) => p, None => return Value::Null };
        let base = json!({ "id": p.id, "stage": p.stage, "result": p.result });
        let mut v = match p.id.as_str() {
            "end" => json!({ "kind": "end", "won": self.election.as_ref().map_or(false, |e| e.won), "legacy": self.legacy() }),
            "gp" => json!({ "kind": "gp", "title": "A legend joins the campaign", "art": "convention" }),
            "election" => { let e = self.election.as_ref().unwrap();
                json!({ "kind": "election", "margins": self.map.states.iter().enumerate().map(|(si, c)| json!({ "code": c, "margin": r2(e.margins[si]), "ev": self.ev(si) })).collect::<Vec<_>>(),
                    "ev": e.ev, "won": e.won, "senate": e.senate, "house": e.house, "recount": e.florida_recount }) }
            "recount" => { let o = self.recount_odds(); let e = self.election.as_ref().unwrap(); let fl = self.map.state_index("FL").unwrap(); let lead = e.margins[fl] > 0.0;
                json!({ "kind": "recount", "art": "court", "kicker": "Florida · the morning after", "title": "Too close to call",
                    "body": format!("You and {} are a few hundred votes apart out of six million, and you are {}. Florida decides it: without it you have {}, {} has {}.", self.sides[RIVAL].leader.name, if lead { "ahead" } else { "behind" }, e.ev[0], self.sides[RIVAL].leader.last, e.ev[1]),
                    "advice": if lead { "You are ahead. Every day the count stands still helps you. Lawyers on the ground in Florida add to every option." } else { "You are behind. Only a full count can change that. Lawyers on the ground in Florida add to every option." },
                    "choices": [ { "n": 0, "label": "Statewide hand recount", "text": "Every ballot. Fair, slow; it might not finish.", "odds": o[0] },
                                 { "n": 1, "label": "Recount four friendly counties", "text": "Faster where it helps. It will look like what it is (−0.5 nationally).", "odds": o[1] },
                                 { "n": 2, "label": "Accept the machine recount", "text": "Honourable; final.", "odds": o[2] } ] }) }
            id => { let ev = match self.data.event(id) { Some(e) => e, None => return Value::Null };
                let choices: Vec<Value> = ev.choices.iter().enumerate().filter(|(_, c)| c.get("if").map_or(true, |cd| self.cond_static(cd))).map(|(i, c)| json!({ "n": i,
                    "label": self.fill(c.get("label").and_then(|v| v.as_str()).unwrap_or("")), "text": self.fill(c.get("text").and_then(|v| v.as_str()).unwrap_or("")),
                    "odds": c.get("odds").map(|o| self.odds(o)) })).collect();
                let focus = ev.focus.as_ref().and_then(|s| self.map.state_index(s)).and_then(|si| self.map.biggest_city(si)).map(|c| self.map.cities[c].h);
                let talents: Vec<Value> = self.offer.iter().map(|t| { let mut j = serde_json::to_value(t).unwrap(); if !t.vetted && ev.kind == "mate" { j["integ"] = Value::Null; } j["risk"] = json!(if t.vetted { if t.risk > 0.4 { "high" } else if t.risk > 0.2 { "some" } else { "low" } } else { "unknown" }); j }).collect();
                json!({ "kind": ev.kind, "art": ev.art, "kicker": self.fill(&ev.kicker), "title": self.fill(&ev.title), "body": self.fill(&ev.body), "sub": self.fill(&ev.sub),
                    "advice": ev.advice.as_ref().map(|a| self.fill(a)), "focus": focus, "forecast": ev.forecast.as_ref().filter(|f| f.is_object()).map(|f| self.forecast_card(f)), "choices": choices,
                    "talents": talents, "challenger": if ev.context.contains(&"challenger".to_string()) { serde_json::to_value(&self.challenger).unwrap() } else { Value::Null } }) }
        };
        if let (Some(o), Some(b)) = (v.as_object_mut(), base.as_object()) { for (k, x) in b { o.insert(k.clone(), x.clone()); } }
        v
    }
    pub fn legacy(&self) -> i64 {
        let p = self.projection(); let won = self.election.as_ref().map_or(false, |e| e.won);
        (p[0] as f64 * 0.8 + self.sides[YOU].ideas_done.len() as f64 * 20.0 + self.sides[YOU].policies.len() as f64 * 15.0 + self.sides[YOU].planks.len() as f64 * 10.0
            + (0..self.facs.len()).filter(|&k| self.status(k) == 2).count() as f64 * 25.0 + self.sides[YOU].hqs.len() as f64 * 10.0 + if won { 150.0 } else { 0.0 }
            + self.office.as_ref().map_or(0.0, |o| if o.holder == YOU { o.passed.len() as f64 * 40.0 + (o.approval - 50.0) * 2.0 } else { 0.0 })
            + if self.flags.contains("foiled") { 120.0 } else { 0.0 }).round() as i64
    }
}
