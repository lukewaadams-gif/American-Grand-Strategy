package game.mandate.web.api;

import game.mandate.core.congress.Bill;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.core.westwing.TransitionOffice;
import game.mandate.core.westwing.TransitionState;
import game.mandate.core.westwing.WestWingData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The transition office (milestone 10): from the day after the election to the oath, the agenda and the White House
 * staff; and the election night that comes before it, and the start of a new game.
 */
final class TransitionViews {
    private TransitionViews() {}

    /** What a new game can be: the candidates of each party (the fictional cast, or with the real roster), the scenarios, the difficulties. */
    static Map<String, Object> options(boolean realRoster) {
        Map<String, Object> v = EngineApi.ok();
        game.mandate.core.campaign.CandidateCatalog cat = game.mandate.core.campaign.CandidateCatalog.load(realRoster);
        for (Party p : Party.values()) {
            List<Object> l = new ArrayList<>();
            for (game.mandate.core.campaign.Candidate c : cat.forParty(p)) {
                if (c.id().equals(game.mandate.core.campaign.Candidate.CUSTOM_ID)) continue;
                if (realRoster && !c.isReal()) continue;   // a real-people world has only real candidates (and your own)
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.id()); m.put("name", c.name()); m.put("look", EngineApi.look(c.id())); m.put("home", c.homeState()); m.put("charisma", c.charisma()); m.put("debate", c.debate());
                m.put("fundraising", c.fundraising()); m.put("policy", c.policy()); m.put("discipline", c.discipline()); m.put("ideology", c.ideology()); m.put("real", c.isReal());
                l.add(m);
            }
            v.put(p == Party.DEMOCRAT ? "democrats" : "republicans", l);
        }
        List<Object> sc = new ArrayList<>();
        for (game.mandate.core.game.Scenario x : game.mandate.core.game.Scenario.values()) sc.add(Map.of("id", x.name(), "label", x.label(), "description", x.description()));
        v.put("scenarios", sc);
        v.put("difficulties", List.of(
                Map.of("id", "STORY", "label", "A gentle race", "description", "Your opponent has less money and runs a fixed plan."),
                Map.of("id", "STANDARD", "label", "A real race", "description", "An opponent with the money to match you, who reads the race and adapts."),
                Map.of("id", "HARD", "label", "An uphill race", "description", "An opponent with more money than you, who reads the race and adapts.")));
        return v;
    }

    /** A new game, full length: the 2028 campaign and on for as long as the president serves. */
    static GameEngine newGame(Map<String, Object> req) {
        Party party = Party.valueOf((String) req.get("party"));
        game.mandate.core.game.GameSetup setup = new game.mandate.core.game.GameSetup(game.mandate.core.data.Json.integer(req.get("seed")), party,
                (String) req.get("player"), (String) req.get("opponent"), game.mandate.core.game.Scenario.valueOf((String) req.get("scenario")),
                game.mandate.core.game.GameSetup.Difficulty.valueOf((String) req.get("difficulty")))
                .withRealRoster(Boolean.TRUE.equals(req.get("realRoster")));
        return new GameEngine(setup);
    }

    static Map<String, Object> view(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("phase", g.phase().name());
        v.put("date", g.date().toString());
        v.put("open", g.phase() == Phase.TRANSITION);
        v.put("inauguration", g.calendar().inauguration().toString());
        v.put("president", g.player().candidate().name());
        v.put("party", g.player().party().code());
        g.result2028().ifPresent(r -> {
            Party me = g.player().party();
            v.put("ev", r.electoralVotes(me));
            v.put("evOpp", r.electoralVotes(me.opponent()));
            v.put("popular", EngineApi.n2(r.marginFor(me)));
        });
        v.put("congress", g.congress().toString());
        // the agenda: the party's bills, the promises first
        List<String> pledges = g.election().pledges();
        List<String> agenda = g.agenda();
        List<Object> bills = new ArrayList<>();
        for (Bill b : g.bills().all()) {
            if (!b.availableTo(g.player().party())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.id()); m.put("name", b.name()); m.put("issue", b.issue().label());
            m.put("cost", Math.round(b.annualCostBn())); m.put("appeal", b.publicAppeal()); m.put("needs60", b.requires60());
            m.put("pledged", pledges.contains(b.id())); m.put("chosen", agenda.indexOf(b.id()));
            bills.add(m);
        }
        bills.sort((a, b) -> Boolean.compare((Boolean) ((Map<?, ?>) b).get("pledged"), (Boolean) ((Map<?, ?>) a).get("pledged")));
        v.put("bills", bills);
        v.put("agenda", agenda);
        v.put("broken", g.brokenPromises().size());
        // the White House staff
        g.transition().ifPresent(t -> {
            TransitionOffice office = g.transitionOffice();
            v.put("hours", t.hoursLeft());
            v.put("hoursMax", TransitionState.HOURS);
            List<Object> jobs = new ArrayList<>();
            for (int role = 0; role < WestWingData.STAFF_TITLE.length; role++) {
                Map<String, Object> j = new LinkedHashMap<>();
                j.put("role", role); j.put("title", WestWingData.STAFF_TITLE[role]); j.put("does", WestWingData.STAFF_DOES[role]);
                j.put("pick", t.pick(role));
                List<Object> ps = new ArrayList<>();
                for (TransitionState.Prospect q : t.prospects(role)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", q.id()); m.put("name", q.name()); m.put("profile", q.profile()); m.put("kind", TransitionOffice.KIND[q.kind()]);
                    m.put("skill", Math.round(q.skillShown())); m.put("loyalty", Math.round(q.loyaltyShown())); m.put("interviewed", q.interviewed());
                    m.put("trait", q.traitShown() < 0 ? null : WestWingData.TRAIT[q.traitShown()]);
                    m.put("traitDesc", q.traitShown() < 0 ? null : WestWingData.TRAIT_DESC[q.traitShown()]);
                    m.put("checked", q.vetted()); m.put("baggage", q.baggageShown());
                    m.put("canInterview", office.cannot(t, q, "interview") == null);
                    m.put("canCheck", office.cannot(t, q, "check") == null);
                    m.put("canAnnounce", office.cannot(t, q, "announce") == null);
                    ps.add(m);
                }
                j.put("prospects", ps);
                jobs.add(j);
            }
            v.put("jobs", jobs);
            double s = office.score(t);
            v.put("score", Math.round(s));
            v.put("grade", TransitionOffice.grade(s));
            v.put("news", t.news());
        });
        return v;
    }
}
