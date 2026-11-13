package game.mandate.web.api;

import game.mandate.core.campaign.CampaignSide;
import game.mandate.core.campaign.PollPoint;
import game.mandate.core.command.Command;
import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.data.Json;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.CommandCodec;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.SaveManager;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.web.Notable;
import org.teavm.jso.JSExport;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The engine, for the TypeScript interface: one exported function, {@link #call}, that takes a JSON request and
 * returns a JSON response. The interface never touches the simulation's objects; it sends commands (in the save
 * file's command format) and reads view models.
 *
 * <p>A view model holds only what the player's screens may show: this week's noisy polls, never the true margins;
 * the published error model, never the hidden polling miss. The same rule as the adviser's (UI_DESIGN.md §9).
 */
public final class EngineApi {
    private EngineApi() {}

    static GameEngine g;

    public static void main(String[] args) { }

    @JSExport
    public static String call(String request) {
        Map<String, Object> out;
        try {
            out = dispatch(Json.object(Json.parse(request)));
        } catch (IllegalCommandException | IllegalStateException | IllegalArgumentException e) {
            out = refusal(e.getMessage());
        } catch (RuntimeException e) {
            out = refusal("The game could not do that: " + e);
        }
        return Json.write(out);
    }

    static Map<String, Object> dispatch(Map<String, Object> req) {
        String op = (String) req.get("op");
        if (op == null) throw new IllegalArgumentException("A request needs an op");
        return switch (op) {
            case "load" -> { g = SaveManager.load((String) req.get("text")); yield ok(); }
            case "save" -> ok("text", SaveManager.save(need(), req.get("ui") == null ? null : Json.object(req.get("ui"))));
            case "command" -> {
                GameEngine game = need();
                int before = game.log().size();
                game.apply(CommandCodec.fromJson(Json.object(req.get("command"))));
                yield ok("news", news(game, before));
            }
            case "view" -> switch ((String) req.get("name")) {
                case "campaign" -> campaign(need());
                case "decisions" -> ok("decisions", decisions(need()));
                default -> throw new IllegalArgumentException("No view called " + req.get("name"));
            };
            default -> throw new IllegalArgumentException("No op called " + op);
        };
    }

    static GameEngine need() {
        if (g == null) throw new IllegalStateException("No game is loaded");
        return g;
    }

    // ---- the campaign ---------------------------------------------------------------------------------------------

    static Map<String, Object> campaign(GameEngine g) {
        Map<String, Object> v = ok();
        int sign = g.player().party().sign();
        LocalDate day = g.phase() == Phase.REELECTION_CAMPAIGN_2032 ? g.calendar().election2032() : g.calendar().election2028();
        LocalDate start = g.phase() == Phase.REELECTION_CAMPAIGN_2032 ? g.calendar().reelectionStart() : g.calendar().campaignStart();
        v.put("phase", g.phase().name());
        v.put("date", g.date().toString());
        v.put("dateLabel", Notable.date(g.date()));
        v.put("electionDay", day.toString());
        v.put("weeksLeft", Math.max(0, ChronoUnit.WEEKS.between(g.date(), day)));
        v.put("week", ChronoUnit.WEEKS.between(start, g.date()) + 1);
        v.put("weeks", ChronoUnit.WEEKS.between(start, day));
        v.put("debate", g.debateThisWeek().map(LocalDate::toString).orElse(null));
        v.put("player", side(g, g.player(), true));
        v.put("opponent", side(g, g.opponent(), false));
        v.put("opponentPlan", g.election().opponentOrders().describe());
        v.put("opponentReasoning", g.election().opponentReasoning());
        v.put("pledges", g.election().pledges().stream().map(id -> g.bill(id).name()).toList());
        v.put("opponentPledges", g.election().opponentPledges().stream().map(id -> g.bill(id).name()).toList());
        v.put("pendingDecisions", g.pendingEvents().size() + (g.debateThisWeek().isPresent() && g.election().pendingPosture() == null ? 1 : 0));

        List<PollPoint> polls = g.polls();
        List<Object> history = new ArrayList<>();
        for (PollPoint p : polls) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", p.date().toString()); m.put("national", r2(p.national())); m.put("ev", p.electoralVotes());
            m.put("win", r3(p.winChance())); m.put("tipping", p.tippingPoint()); m.put("movers", p.movers());
            history.add(m);
        }
        v.put("polls", history);
        v.put("errorSd", r2(g.pollErrorSd()));

        Map<String, Double> now = g.statePolls();
        Map<String, Double> before = polls.size() >= 2 ? polls.get(polls.size() - 2).states() : Map.of();
        Map<String, Double> win = g.stateWinChances();
        List<Object> states = new ArrayList<>();
        for (UsState s : g.states().all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            double poll = now.get(s.code());
            m.put("code", s.code()); m.put("name", s.name()); m.put("ev", s.electoralVotes());
            m.put("margin", r2(poll));                               // D minus R, as the polls read
            m.put("forPlayer", r2(poll * sign));                     // the player's lead
            m.put("change", before.containsKey(s.code()) ? r2((poll - before.get(s.code())) * sign) : null);
            m.put("win", r3(win.get(s.code())));
            m.put("baseline", r2(s.baselineMargin()));
            m.put("adsMe", r2(g.player().ads(s.code()))); m.put("adsThem", r2(g.opponent().ads(s.code())));
            m.put("fieldMe", r2(g.player().field(s.code()))); m.put("fieldThem", r2(g.opponent().field(s.code())));
            m.put("visitsMe", r2(g.player().visits(s.code()))); m.put("visitsThem", r2(g.opponent().visits(s.code())));
            states.add(m);
        }
        v.put("states", states);
        PollPoint last = polls.isEmpty() ? null : polls.get(polls.size() - 1);
        v.put("tipping", last == null ? null : last.tippingPoint());
        v.put("news", news(g, 0));
        var result = g.phase() == Phase.REELECTION_CAMPAIGN_2032 ? g.result2032() : g.result2028();
        result.ifPresent(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dem", r.demElectoralVotes()); m.put("rep", r.repElectoralVotes()); m.put("popular", r2(r.popularMargin()));
            m.put("winner", r.winner().code()); m.put("tipping", r.tippingPoint());
            Map<String, Object> margins = new LinkedHashMap<>();
            r.stateMargins().forEach((k, x) -> margins.put(k, r2(x)));
            m.put("states", margins);
            v.put("result", m);
        });
        return v;
    }

    /** What waits for an answer before the turn can end: events and the week's debate, each option with its command. */
    static List<Object> decisions(GameEngine g) {
        List<Object> out = new ArrayList<>();
        for (var p : g.pendingEvents()) {
            List<Object> options = new ArrayList<>();
            var eo = p.definition().options();
            for (int i = 0; i < eo.size(); i++) {
                var o = eo.get(i);
                String detail = effects(o.effects()) + (o.successChance() < 1 ? "  If it fails: " + effects(o.failureEffects()) : "");
                options.add(option(o.label(), detail, o.successChance() < 1 ? game.mandate.core.data.Fmt.format("Gamble: %.0f%%", 100 * o.successChance()) : null,
                        o.successChance() < 1 ? "WARN" : null, new Command.ResolveEvent(p.instanceId(), i)));
            }
            out.add(decision("event", "Event", p.definition().title(), p.definition().body(), options));
        }
        boolean campaign = g.phase() == Phase.GENERAL_CAMPAIGN_2028 || g.phase() == Phase.REELECTION_CAMPAIGN_2032;
        if (campaign && g.debateThisWeek().isPresent() && g.election().pendingPosture() == null) {
            var theirs = game.mandate.core.campaign.DebatePosture.tendencyOf(g.opponent().candidate());
            List<Object> options = new ArrayList<>();
            for (var d : game.mandate.core.campaign.DebatePosture.values()) {
                boolean counters = d.counters(theirs);
                options.add(option(d.label(), "Your rating with this approach: " + d.rating(g.player().candidate()) + " of 10."
                        + (counters ? " It counters your opponent's usual style." : ""), counters ? "Counters them" : null, counters ? "GOOD" : null,
                        new Command.ChooseDebatePosture(d)));
            }
            out.add(decision("debate", "Debate this week", "How will you debate?",
                    "Prep notes: your opponent tends to \"" + theirs.label().toLowerCase() + "\". A posture that counters theirs scores extra; a steady one limits the risk of a bad night.",
                    options));
        }
        return out;
    }

    static Map<String, Object> decision(String kind, String eyebrow, String title, String body, List<Object> options) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind); m.put("eyebrow", eyebrow); m.put("title", title); m.put("body", body); m.put("options", options);
        return m;
    }

    static Map<String, Object> option(String label, String detail, String tag, String tone, Command command) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label); m.put("detail", detail); m.put("tag", tag); m.put("tone", tone); m.put("command", CommandCodec.toJson(command));
        return m;
    }

    static String effects(List<game.mandate.core.effects.Effect> es) {
        if (es.isEmpty()) return "No direct effect.";
        return String.join("; ", es.stream().map(game.mandate.core.effects.Effect::describe).toList()) + ".";
    }

    static Map<String, Object> side(GameEngine g, CampaignSide s, boolean mine) {
        Map<String, Object> m = new LinkedHashMap<>();
        var c = s.candidate();
        m.put("name", c.name()); m.put("id", c.id()); m.put("party", s.party().code());
        m.put("partyName", s.party() == Party.DEMOCRAT ? "Democrat" : "Republican");
        m.put("home", c.homeState());
        m.put("ratings", Map.of("charisma", c.charisma(), "debate", c.debate(), "fundraising", c.fundraising(), "policy", c.policy(), "discipline", c.discipline()));
        m.put("ideology", r2(c.ideology()));
        // The player knows their own money exactly and the opponent's from filings, to the nearest million.
        m.put("cash", mine ? r2(s.cash()) : (double) Math.round(s.cash()));
        m.put("momentum", r2(s.momentum()));
        if (mine) {
            var o = g.election().playerOrders();
            m.put("orders", Map.of("strategy", o.strategy().name(), "strategyLabel", o.strategy().label, "rate", o.rate().name(), "rateLabel", o.rate().label,
                    "message", o.message() == null ? "" : o.message().name()));
        }
        return m;
    }

    /** Log lines from {@code from} on, newest first, each with its date and, if notable, a headline and tone. */
    static List<Object> news(GameEngine g, int from) {
        List<String> log = g.log();
        List<Object> out = new ArrayList<>();
        for (int i = log.size() - 1; i >= from && out.size() < 60; i--) {
            String line = log.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            boolean dated = line.length() > 12 && Character.isDigit(line.charAt(0));
            m.put("date", dated ? line.substring(0, 10) : null);
            m.put("text", dated ? line.substring(12) : line);
            Notable.classify(line).ifPresent(n -> { m.put("headline", n.title()); m.put("tone", n.tone().name()); });
            out.add(m);
        }
        return out;
    }

    static Map<String, Object> ok() { Map<String, Object> m = new LinkedHashMap<>(); m.put("ok", true); return m; }
    static Map<String, Object> ok(String k, Object v) { Map<String, Object> m = ok(); m.put(k, v); return m; }

    static Map<String, Object> refusal(String why) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", why == null ? "Not possible" : why);
        return m;
    }

    static double r2(double v) { return Math.round(v * 100) / 100.0; }
    static double r3(double v) { return Math.round(v * 1000) / 1000.0; }

    /** For tests on the JVM: the same call, with a command object rather than JSON. */
    static Map<String, Object> apply(Command c) {
        return dispatch(Map.of("op", "command", "command", CommandCodec.toJson(c)));
    }
}
