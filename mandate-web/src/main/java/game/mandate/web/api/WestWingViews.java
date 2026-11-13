package game.mandate.web.api;

import game.mandate.core.command.Command;
import game.mandate.core.data.Fmt;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.hill.HillState;
import game.mandate.core.news.RdFx;
import game.mandate.core.presidency.WatchPlan;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.core.westwing.WestWingData;
import game.mandate.core.westwing.WestWingEngine;
import game.mandate.core.westwing.WestWingOrder;
import game.mandate.core.westwing.WestWingState;
import game.mandate.core.westwing.WestWingState.Person;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The West Wing (Resolute Desk's {@code WestWing}: staff, cabinet, vice president, orders and rules, the courts,
 * oversight) and the Press Briefing Room (the press, speeches, posts, the party), and the questions they put on the desk.
 */
final class WestWingViews {
    private WestWingViews() {}

    static boolean open(GameEngine g) { return g.westWing().isPresent() && g.phase() == Phase.GOVERNING; }

    static Map<String, Object> person(WestWingState s, Person p, int week) {
        if (p == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id()); m.put("name", p.name()); m.put("background", p.background()); m.put("party", p.party().code());
        m.put("trait", WestWingData.TRAIT[p.trait()]); m.put("traitDesc", WestWingData.TRAIT_DESC[p.trait()]); m.put("traitRisky", p.trait() >= WestWingData.T_LEAKER);
        m.put("skill", Math.round(p.skill())); m.put("loyalty", Math.round(p.loyalty())); m.put("morale", Math.round(p.morale()));
        m.put("weeks", Math.max(0, week - p.since())); m.put("quality", r2(p.quality()));
        if (p.confirmed()) m.put("vote", p.voteYes() + "-" + p.voteNo());
        return m;
    }

    static Map<String, Object> room(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("date", g.date().toString());
        v.put("open", open(g));
        if (g.westWing().isEmpty()) {
            v.put("reason", "The West Wing is staffed on day 100; until then the hundred days run the transition team's nominations.");
            return v;
        }
        WestWingState s = g.westWing().get();
        WestWingEngine e = g.westWingEngine();
        WestWingEngine.Context c = g.westWingContext();
        int week = s.week();
        v.put("president", g.player().party().code());
        // the staff
        List<Object> staff = new ArrayList<>();
        double moraleSum = 0;
        int people = 0;
        for (int r = 0; r < WestWingState.NSTAFF; r++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", r); m.put("title", WestWingData.STAFF_TITLE[r]); m.put("does", WestWingData.STAFF_DOES[r]);
            m.put("person", person(s, s.staff(r), week));
            staff.add(m);
        }
        for (Person p : s.everyone()) { moraleSum += p.morale(); people++; }
        v.put("staff", staff);
        v.put("mood", Math.round(people == 0 ? 0 : moraleSum / people));
        v.put("delegatedPeople", s.delegatedPeople()); v.put("delegatedPardons", s.delegatedPardons());
        v.put("leaks", s.leaks()); v.put("departures", s.departures()); v.put("failures", s.failures());
        // the cabinet
        List<Object> posts = new ArrayList<>();
        double wf = 0;
        for (int k = 0; k < WestWingState.NPOST; k++) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("post", k); m.put("name", WestWingData.POST_NAME[k]); m.put("short", WestWingData.POST_SHORT[k]); m.put("title", WestWingData.POST_TITLE[k]);
            m.put("person", person(s, s.post(k), week));
            m.put("readiness", Math.round(s.readiness(k)));
            m.put("workforce", r1(s.workforce(k)));
            wf += s.workforce(k);
            Person n = s.nominee(k);
            if (n != null) {
                Map<String, Object> x = person(s, n, week);
                double[] cnt = e.count(c, n);
                x.put("voteIn", Math.max(0, s.voteAt(k) - week)); x.put("expected", Math.round(cnt[0])); x.put("chance", r2(e.chance(c, n)));
                m.put("nominee", x);
            }
            posts.add(m);
        }
        v.put("posts", posts);
        v.put("workforce", Math.round(wf));
        // the vice president
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("name", s.vicePresident());
        vp.put("skills", List.of(Math.round(s.vpSkill(0)), Math.round(s.vpSkill(1)), Math.round(s.vpSkill(2)), Math.round(s.vpSkill(3))));
        vp.put("mission", s.vpMission()); vp.put("missionName", WestWingData.VM_NAME[s.vpMission()]);
        vp.put("issue", s.vpArgument() == null ? null : game.mandate.core.campaign.Issue.valueOf(s.vpArgument()).label());
        vp.put("weeksLeft", s.vpUntil() < 0 ? null : Math.max(0, s.vpUntil() - week));
        vp.put("loyalty", Math.round(s.vpLoyalty()));
        List<Object> missions = new ArrayList<>();
        for (int i = 0; i < WestWingData.VM_NAME.length; i++)
            missions.add(Map.of("id", i, "name", WestWingData.VM_NAME[i], "desc", WestWingData.VM_DESC[i], "weeks", WestWingData.VM_WEEKS[i]));
        vp.put("missions", missions);
        v.put("vp", vp);
        // orders, rules and emergencies
        List<Object> library = new ArrayList<>();
        for (WestWingData.ActionDef d : e.data().library()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id()); m.put("kind", d.kind()); m.put("name", d.name()); m.put("desc", d.desc());
            m.put("issue", RdFx.issue(d.area()).label()); m.put("lean", d.lean() == null ? null : d.lean().code());
            m.put("support", Math.round(d.support()));
            m.put("legal", r2(Math.max(0, Math.min(0.98, d.legal() * (1.15 - 0.3 * WestWingEngine.sq(s, WestWingData.S_COUNSEL))))));
            int post = WestWingData.AREA_POST[d.area()];
            m.put("department", WestWingData.POST_NAME[post]); m.put("readiness", Math.round(s.readiness(post)));
            m.put("needs", d.needs() < 0 ? null : "a national emergency over " + WestWingData.EMERG_NAME[d.needs()]);
            m.put("refusal", refusal(g, s, c, d));
            library.add(m);
        }
        v.put("library", library);
        v.put("commentWeeks", Math.round(14 - 6 * WestWingEngine.pq(s, WestWingData.P_OMB)));
        boolean anyEmergency = false;
        for (int i = 0; i < 5; i++) anyEmergency |= s.emergency(i);
        v.put("emergencyDeclared", anyEmergency);
        List<Object> done = new ArrayList<>();
        for (int i = s.actions().size() - 1; i >= 0; i--) {
            WestWingState.Action a = s.actions().get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id()); m.put("name", a.name()); m.put("kind", WestWingData.ACTION_KIND[a.kind()]); m.put("status", WestWingData.ACTION_STATUS[a.status()]);
            m.put("statusKey", a.status()); m.put("standing", a.standing()); m.put("inCourt", inCourt(s, a.id())); m.put("failed", a.failed());
            m.put("issued", g.date().minusWeeks(week - a.issued()).toString());
            if (a.status() == WestWingState.Action.COMMENT) m.put("finalIn", Math.max(0, a.finalWeek() - week));
            done.add(m);
        }
        v.put("actions", done);
        // the courts
        List<Object> justices = new ArrayList<>();
        int year = g.date().getYear();
        for (WestWingState.Justice j : s.justices())
            justices.add(Map.of("name", j.name(), "age", year - j.born(), "party", j.party().code(), "lean", j.ideology() > 0.2 ? "conservative" : j.ideology() < -0.2 ? "liberal" : "center"));
        Map<String, Object> courts = new LinkedHashMap<>();
        courts.put("justices", justices);
        courts.put("vacancies", 9 - s.justices().size());
        if (s.justiceNominee() != null) {
            double[] k = scotusCount(g, s, c);
            courts.put("nominee", Map.of("name", s.justiceNominee().name(), "voteIn", Math.max(0, s.scotusVoteAt() - week), "expected", Math.round(k[0]), "chance", r2(k[2])));
        } else if (s.justices().size() < 9) {
            List<Object> sl = new ArrayList<>();
            List<WestWingState.Justice> list = shortlist(g, s, c);
            for (int i = 0; i < list.size(); i++) {
                WestWingState.Justice j = list.get(i);
                sl.add(Map.of("name", j.name(), "age", year - j.born(), "what", WestWingData.SHORTLIST_WHAT[i]));
            }
            courts.put("shortlist", sl);
        }
        boolean dem = g.player().party() == Party.DEMOCRAT;
        courts.put("district", r2(dem ? s.districtDemShare() : 1 - s.districtDemShare()));
        courts.put("circuit", r2(dem ? s.circuitDemShare() : 1 - s.circuitDemShare()));
        courts.put("districtVacancies", Math.round(s.districtVacancies())); courts.put("circuitVacancies", Math.round(s.circuitVacancies()));
        courts.put("judges", s.judgesAppointed()); courts.put("strategy", s.judgeStrategy());
        courts.put("strategies", List.of(WestWingData.JUDGE_STRATEGY));
        courts.put("strategyDesc", List.of(WestWingData.JUDGE_STRATEGY_DESC));
        List<Object> cases = new ArrayList<>();
        for (int i = s.cases().size() - 1; i >= 0; i--) {
            WestWingState.Case k = s.cases().get(i);
            WestWingState.Action a = s.action(k.action());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", k.plaintiff() + " v. United States"); m.put("action", a == null ? "" : a.name());
            m.put("stage", WestWingData.CASE_STAGE[k.stage()]); m.put("blocked", k.blocked()); m.put("done", k.done()); m.put("won", k.won()); m.put("note", k.note());
            if (!k.done()) m.put("rulingIn", Math.max(0, k.decideWeek() - week));
            if (k.votesFor() + k.votesAgainst() > 0) m.put("votes", k.votesFor() + "-" + k.votesAgainst());
            cases.add(m);
        }
        courts.put("cases", cases);
        v.put("courts", courts);
        // oversight: scandals, and Congress's investigations
        List<Object> scandals = new ArrayList<>();
        for (int i = s.scandals().size() - 1; i >= 0; i--) {
            WestWingState.Scandal x = s.scandals().get(i);
            if (x.over() && week - x.started() > 52) continue;
            scandals.add(Map.of("title", x.title(), "severity", r1(x.severity()), "stage", x.stage() == 2 ? "A special counsel" : x.stage() == 1 ? "Under investigation" : "A story",
                    "impeachable", x.impeachable(), "over", x.over()));
        }
        v.put("scandals", scandals);
        v.put("pressure", r1(WestWingEngine.impeachmentPressure(s)));
        v.put("pardons", s.pardons()); v.put("defiance", s.defiance());
        g.hill().ifPresent(h -> {
            List<Object> probes = new ArrayList<>();
            for (HillState.Probe p : h.probes()) probes.add(Map.of("subject", p.subject(), "status", p.status(), "chamber", HillState.CHAMBER[p.chamber()]));
            v.put("probes", probes);
            v.put("impeachment", h.impeachment());
        });
        List<Object> record = new ArrayList<>();
        for (int i = s.record().size() - 1; i >= 0 && record.size() < 30; i--) {
            String r = s.record().get(i);
            int colon = r.indexOf(": ");
            record.add(Map.of("date", colon > 0 ? r.substring(0, colon) : "", "what", colon > 0 ? r.substring(colon + 2) : r));
        }
        v.put("record", record);
        return v;
    }

    /** Three people for a job: a staff role, or a cabinet post (with each one's chance in the Senate). */
    static Map<String, Object> options(GameEngine g, String kind, int index) {
        Map<String, Object> v = EngineApi.ok();
        WestWingState s = g.westWing().orElseThrow(() -> new IllegalStateException("The West Wing is not staffed yet"));
        WestWingEngine e = g.westWingEngine();
        WestWingEngine.Context c = g.westWingContext();
        List<Person> ps = kind.equals("staff") ? e.staffOptions(s, c, index, s.departures()) : e.options(s, c, index, s.departures());
        List<Object> out = new ArrayList<>();
        for (Person p : ps) {
            Map<String, Object> m = person(s, p, s.week());
            if (!kind.equals("staff")) m.put("chance", r2(e.chance(c, p)));
            out.add(m);
        }
        v.put("options", out);
        return v;
    }

    private static double[] scotusCount(GameEngine g, WestWingState s, WestWingEngine.Context c) {
        return g.westWingEngine().scotusCount(s, c);
    }

    private static List<WestWingState.Justice> shortlist(GameEngine g, WestWingState s, WestWingEngine.Context c) {
        return g.westWingEngine().shortlist(s, c);
    }

    private static String refusal(GameEngine g, WestWingState s, WestWingEngine.Context c, WestWingData.ActionDef d) {
        return g.westWingEngine().refusal(s, c, d);
    }

    private static boolean inCourt(WestWingState s, String action) {
        for (WestWingState.Case k : s.cases()) if (k.action().equals(action) && !k.done()) return true;
        return false;
    }

    // ---- the Press Briefing Room ---------------------------------------------------------------------------------

    static Map<String, Object> press(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("date", g.date().toString());
        v.put("open", open(g));
        if (g.westWing().isEmpty()) {
            v.put("reason", "The press office is yours on day 100.");
            return v;
        }
        WestWingState s = g.westWing().get();
        WestWingEngine e = g.westWingEngine();
        int week = s.week();
        List<Object> outlets = new ArrayList<>();
        for (int o = 0; o < WestWingState.NOUTLET; o++) {
            WestWingData.Outlet x = e.data().outlets().get(o);
            double tone = s.tone(o);
            outlets.add(Map.of("index", o, "name", x.name(), "audience", x.audience(), "interviews", x.interviews(), "tone", Math.round(tone),
                    "label", tone >= 60 ? "friendly" : tone >= 40 ? "mixed" : "hostile"));
        }
        v.put("outlets", outlets);
        v.put("relations", Math.round(s.pressRelations()));
        v.put("presserIn", Math.max(0, 2 - (week - s.lastPresser())));
        v.put("interviewIn", Math.max(0, 2 - (week - s.lastInterview())));
        v.put("addressIn", Math.max(0, 8 - (week - s.lastAddress())));
        v.put("busy", s.inQuestions() || s.inSpeech());
        v.put("postsLeft", 5 - s.postsThisWeek());
        v.put("followers", r1(s.followers()));
        v.put("tones", List.of(WestWingData.POST_TONE));
        v.put("lastPoll", s.lastPoll());
        v.put("pressers", s.pressConferences()); v.put("interviews", s.interviews()); v.put("speeches", s.speeches());
        LocalDate d = g.date();
        LocalDate sotu = LocalDate.of(d.getMonthValue() >= 2 && d.getDayOfMonth() > 4 || d.getMonthValue() > 2 ? d.getYear() + 1 : d.getYear(), 2, 4);
        v.put("sotu", sotu.toString());
        // the party and the opposition
        Party me = g.player().party();
        var party = g.state().politics().party(me);
        List<Object> wings = new ArrayList<>();
        for (int w = 0; w < 2; w++) wings.add(Map.of("name", party.wingName(w), "mood", Math.round(party.wingMood(w))));
        v.put("wings", wings);
        double re = g.state().politics().recordEcon(), rc = g.state().politics().recordCulture(), mid = (re + rc) / 2;
        v.put("record", g.state().politics().recordWeight() < 0.5 ? "Your record is too short to place yet" :
                "Your record so far sits " + (mid < -0.15 ? "to the left" : mid > 0.15 ? "to the right" : "in the center"));
        int opp = switch (g.state().politics().opposition()) { case COOPERATE -> 0; case SELECTIVE -> 1; case OBSTRUCT -> 2; };
        v.put("opposition", game.mandate.core.politics.PoliticsEngine.OPP_STRAT[opp]);
        List<Object> leaders = new ArrayList<>();
        g.hill().ifPresent(h -> { for (HillState.Member m : h.members()) if (m.leader() && m.party() != me) leaders.add(m.role() + " " + m.name()); });
        v.put("oppLeaders", leaders);
        v.put("issues", issues());
        return v;
    }

    static List<Object> issues() {
        List<Object> l = new ArrayList<>();
        for (game.mandate.core.campaign.Issue i : game.mandate.core.campaign.Issue.values()) l.add(Map.of("id", i.name(), "label", i.label()));
        return l;
    }

    // ---- the questions on the desk ----------------------------------------------------------------------------------

    static List<Object> decisions(GameEngine g) {
        List<Object> out = new ArrayList<>();
        if (!open(g)) return out;
        WestWingState s = g.westWing().get();
        for (WestWingState.Decision d : s.decisions()) {
            List<Object> options = new ArrayList<>();
            for (int i = 0; i < d.options().size(); i++) {
                String adv = d.advice().get(i);
                options.add(EngineApi.option(d.options().get(i), adv == null ? "" : named(g, s, adv), null, null, new Command.WestWing(new WestWingOrder.Answer(d.id(), i))));
            }
            String eyebrow = switch (d.kind()) {
                case "qa" -> "The press";
                case "speech" -> "Speechwriting";
                case "story" -> "A developing story";
                case "court" -> "The courts";
                case "cra", "emergency" -> "Congress";
                case "pardon" -> "The pardon attorney";
                case "scandal", "leak" -> "Damage control";
                default -> "The West Wing";
            };
            Map<String, Object> m = EngineApi.decision("westwing", eyebrow, d.title(), d.body(), options);
            m.put("recommended", d.recommended());
            m.put("stop", switch (d.kind()) {
                case "scandal", "leak", "pardon" -> WatchPlan.STOP_SCANDAL;
                case "court" -> WatchPlan.STOP_COURT;
                case "cra", "emergency" -> WatchPlan.STOP_SIGN;
                case "story" -> WatchPlan.STOP_STORY;
                default -> -1;
            });
            out.add(m);
        }
        return out;
    }

    /** "Counsel: ..." with the person's name, as Resolute Desk shows advice. */
    private static String named(GameEngine g, WestWingState s, String advice) {
        int colon = advice.indexOf(':');
        if (colon <= 0) return advice;
        return g.westWingEngine().adviserName(s, advice.substring(0, colon)) + advice.substring(colon);
    }

    static double r1(double x) { return Math.round(x * 10) / 10.0; }
    static double r2(double x) { return Math.round(x * 100) / 100.0; }

    @SuppressWarnings("unused")
    private static String f(double v) { return Fmt.format("%.1f", v); }
}
