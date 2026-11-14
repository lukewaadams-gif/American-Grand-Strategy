package game.mandate.web.api;

import game.mandate.core.command.Command;
import game.mandate.core.data.Fmt;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.game.Legacy;
import game.mandate.core.presidency.WatchPlan;
import game.mandate.core.terms.TermsEngine;
import game.mandate.core.terms.TermsOrder;
import game.mandate.core.terms.TermsState;
import game.mandate.core.time.Phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Terms of office (milestone 9): the term and its lame-duck decline, whether the president runs again, a primary
 * challenge, the race to succeed the president, the second term's legacy goals, and the verdict when it is all over.
 */
final class TermsViews {
    private TermsViews() {}

    static boolean open(GameEngine g) { return g.terms().isPresent() && g.inOffice(); }

    /** The questions about the term on the desk. */
    static List<Object> decisions(GameEngine g) {
        List<Object> out = new ArrayList<>();
        if (!open(g)) return out;
        for (TermsState.Question q : g.terms().get().questions()) {
            List<Object> options = new ArrayList<>();
            for (int i = 0; i < q.options().size(); i++) {
                String adv = i < q.advice().size() ? q.advice().get(i) : "";
                options.add(EngineApi.option(q.options().get(i), adv, i == q.recommended() ? "Advised" : null, null, new Command.Terms(new TermsOrder.Answer(q.id(), i))));
            }
            Map<String, Object> m = EngineApi.decision("terms", q.kind().equals("info") ? "Your term" : "A decision about your term", q.title(), q.body(), options);
            m.put("recommended", q.recommended());
            m.put("stop", q.kind().equals("info") ? -1 : WatchPlan.STOP_CAMPAIGN);
            out.add(m);
        }
        return out;
    }

    static int count(GameEngine g) { return decisions(g).size(); }

    static Map<String, Object> contender(TermsState.Contender c) {
        if (c == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", c.name()); m.put("party", c.party().code()); m.put("home", c.home()); m.put("role", c.vicePresident() ? "Vice President" : c.role() == null || c.role().isEmpty() ? "From " + c.home() : c.role());
        m.put("share", Math.round(c.share())); m.put("delegates", c.delegates());
        m.put("charisma", Math.round(c.charisma())); m.put("debate", Math.round(c.debate()));
        return m;
    }

    /** The Press Briefing Room's term panel: the term, the run question's answer, the race, the legacy goals. */
    static Map<String, Object> view(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        if (g.terms().isEmpty()) { v.put("open", false); return v; }
        TermsState s = g.terms().get();
        v.put("open", open(g));
        v.put("term", g.term());
        v.put("termWeek", g.termWeek() + 1);
        v.put("lameDuck", Math.round(g.lameDuck() * 100));
        v.put("running", switch (s.runStage()) {
            case TermsState.RUN_RUNNING -> "Running for a second term";
            case TermsState.RUN_STEPPED_ASIDE -> "Not seeking a second term";
            case TermsState.RUN_DENIED -> "Denied renomination";
            case TermsState.RUN_ASKED -> "Deciding whether to run";
            default -> g.term() >= 2 ? "Serving a second and final term" : "Not yet declared";
        });
        if (s.challenger() != null) { v.put("challenger", s.challenger()); v.put("challengerShare", Math.round(s.challengerShare())); }
        if (s.leaving()) {
            v.put("leaving", true);
            v.put("successor", s.successor());
            v.put("handoff", s.handoff().toString());
        }
        List<Object> goals = new ArrayList<>();
        if (s.theme() >= 0) v.put("theme", TermsEngine.THEMES.get(s.theme()).name());
        for (Map.Entry<String, Boolean> e : g.legacyGoals()) goals.add(Map.of("label", e.getKey(), "met", e.getValue()));
        v.put("goals", goals);
        TermsState.Race r = s.race();
        if (r != null) {
            Map<String, Object> race = new LinkedHashMap<>();
            race.put("year", r.year());
            race.put("electionDay", r.electionDay().toString());
            List<Object> rivals = new ArrayList<>();
            for (int i = 0; i < r.rivals().size(); i++) {
                Map<String, Object> c = contender(r.rivals().get(i));
                c.put("index", i);
                c.put("endorsed", r.endorsed() == i);
                c.put("nominee", r.nominee() == i);
                rivals.add(c);
            }
            race.put("rivals", rivals);
            race.put("canEndorse", open(g) && g.termsEngine().endorseRefusal(s, 0) == null);
            race.put("nominee", contender(r.nomineeContender()));
            race.put("opponent", contender(r.opponent()));
            race.put("split", Math.round(r.split()));
            race.put("contests", r.contests());
            if (r.nomineeContender() != null) {
                race.put("national", EngineApi.n2(r.national()));
                race.put("forecast", r.done() ? r.evMe() : g.termsEngine().forecast(g.termsContext(), r));
            }
            race.put("done", r.done());
            if (r.done()) {
                race.put("won", r.won());
                race.put("ev", List.of(r.evMe(), r.evOpp()));
                race.put("popular", List.of(EngineApi.n2(r.popMe()), EngineApi.n2(r.popOpp())));
            }
            List<String> log = r.primaryLog();
            race.put("primaries", log.subList(Math.max(0, log.size() - 6), log.size()));
            race.put("season", g.midtermSeason());
            v.put("race", race);
        }
        v.put("record", s.record());
        return v;
    }

    /** The verdict on the presidency, once it is over. */
    static Map<String, Object> verdict(GameEngine g) {
        Map<String, Object> v = EngineApi.ok();
        v.put("over", g.over());
        g.legacy().ifPresent(l -> {
            v.put("score", l.score());
            v.put("grade", l.grade());
            v.put("rank", l.rank());
            List<Object> ms = new ArrayList<>();
            for (int i = 0; i < l.measures().size(); i++) ms.add(Map.of("name", Legacy.MEASURES.get(i), "score", l.measures().get(i)));
            v.put("measures", ms);
            v.put("lines", l.lines().stream().filter(x -> Legacy.MEASURES.stream().noneMatch(x::startsWith) && !x.equals(l.rank())).toList());
        });
        v.put("president", g.player().candidate().name());
        v.put("date", g.date().toString());
        v.put("terms", g.term());
        g.terms().ifPresent(t -> v.put("record", t.record()));
        v.put("summary", Fmt.format("%s, %d %s.", g.player().candidate().name(), g.term(), g.term() == 1 ? "term" : "terms"));
        v.put("mode", g.mode().name());
        v.put("served", g.inaugurated());
        // the campaign's end (Campaign Only, or a 2028 defeat): the assessment, the popular vote and the states
        if (!g.inaugurated()) g.campaignAssessment().ifPresent(a -> {
            Map<String, Object> c = new LinkedHashMap<>();
            var r = g.result2028().orElseThrow();
            game.mandate.core.state.Party me = g.player().party();
            c.put("won", a.won()); c.put("grade", a.grade()); c.put("headline", a.headline()); c.put("lines", a.lines());
            c.put("ev", r.electoralVotes(me)); c.put("evOpp", r.electoralVotes(me.opponent())); c.put("popular", EngineApi.n2(r.marginFor(me)));
            c.put("player", g.player().candidate().name()); c.put("opponent", g.opponent().candidate().name()); c.put("party", me.code());
            List<Object> states = new ArrayList<>();
            for (var s : g.states().all()) {
                Map<String, Object> x = new LinkedHashMap<>();
                double m = r.stateMargins().get(s.code()) * me.sign();
                x.put("code", s.code()); x.put("name", s.name()); x.put("ev", s.electoralVotes()); x.put("margin", EngineApi.n2(m));
                states.add(x);
            }
            c.put("states", states);
            v.put("campaign", c);
        });
        // Individual Crisis: the after-action report
        g.individualCrisis().ifPresent(cr -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("title", cr.title()); c.put("preset", game.mandate.core.engine.CrisisRecord.PRESETS.get(cr.preset())); c.put("began", cr.began().toString());
            var r = cr.report();
            if (r != null) {
                c.put("result", r.result().name()); c.put("resultLabel", r.result().label); c.put("outcome", r.outcome()); c.put("reason", r.reason());
                c.put("ended", r.ended().toString()); c.put("days", r.days()); c.put("verdict", r.verdict()); c.put("figures", r.figures()); c.put("record", r.record());
            }
            v.put("crisis", c);
        });
        return v;
    }
}
