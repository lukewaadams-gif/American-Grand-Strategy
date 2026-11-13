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
        boolean campaigning = g.phase() == Phase.GENERAL_CAMPAIGN_2028 || g.phase() == Phase.REELECTION_CAMPAIGN_2032;
        if (campaigning) {
            v.put("turn", turn(g));
            v.put("staff", staff(g));
            v.put("trail", trail(g));
            v.put("schedule", schedule(g));
            v.put("activities", activities(g));
            v.put("field", field(g));
        }
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

    // ---- the candidate's schedule (Battleground 2028's) -------------------------------------------------------------

    /** The turn: a week, or a day of the final stretch. */
    static Map<String, Object> turn(GameEngine g) {
        Map<String, Object> m = new LinkedHashMap<>();
        int day = g.campaignDay();
        m.put("daily", day >= 0);
        m.put("day", day);
        m.put("dailyFrom", game.mandate.core.campaign.CampaignEngine.Turn.monday(g.electionDay()).minusWeeks(2).toString());
        m.put("label", day >= 0 ? game.mandate.core.campaign.CampaignOrders.DAY[day] + " " + Notable.date(g.date()) : "The week of " + Notable.date(g.date()));
        m.put("weeksToVote", g.campaignTurn().weeksToVote());
        return m;
    }

    static Map<String, Object> staff(GameEngine g) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (var a : game.mandate.core.campaign.ElectionState.StaffArea.values()) m.put(a.name().toLowerCase(), g.election().staffRuns(a));
        return m;
    }

    /** The candidate on the trail: stamina now and after the turn's events, their cost, debate preparation, where they are. */
    static Map<String, Object> trail(GameEngine g) {
        CampaignSide me = g.player();
        var o = me.orders();
        int day = g.campaignDay();
        int s0 = day >= 0 ? day * 3 : 0, s1 = day >= 0 ? s0 + 3 : game.mandate.core.campaign.CampaignOrders.SLOTS;
        double fm = 1.3 - me.ratings().sta() / 150.0, fat = 0, cost = 0;
        for (int i = s0; i < s1; i++) { fat += o.activity(i).fatigue() * fm * 0.55; cost += o.activity(i).cost(); }
        double after = Math.max(5, Math.min(100, me.stamina() - fat + (38 + 0.12 * me.ratings().sta()) * (day >= 0 ? 1.0 / 7 : 1)));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("stamina", r2(me.stamina())); m.put("staminaAfter", r2(after)); m.put("eventCosts", r2(cost));
        m.put("prep", r2(me.prep()));
        m.put("location", me.location()); m.put("locationName", g.states().get(me.location()).name());
        m.put("enthusiasm", r3(me.enthusiasm())); m.put("nameRecognition", r3(me.nameRecognition()));
        m.put("rallies", me.rallies()); m.put("bestRally", me.bestRally());
        LocalDate next = null;
        for (LocalDate d : g.campaignDebates()) if (!d.isBefore(g.date()) && (next == null || d.isBefore(next))) next = d;
        m.put("nextDebate", next == null ? null : next.toString());
        return m;
    }

    /** The week's 21 slots as the schedule shows them: what, where, the choice, whether it is past, and long same-day flights. */
    static List<Object> schedule(GameEngine g) {
        CampaignSide me = g.player();
        var geo = g.campaignEngine().geography();
        var o = me.orders();
        int day = g.campaignDay(), from = day >= 0 ? day * 3 : 0;
        LocalDate monday = game.mandate.core.campaign.CampaignEngine.Turn.monday(g.date());
        int prev = geo.index(me.location());
        List<Object> out = new ArrayList<>();
        for (int i = 0; i < game.mandate.core.campaign.CampaignOrders.SLOTS; i++) {
            var a = o.activity(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slot", i); m.put("day", i / 3); m.put("part", i % 3); m.put("date", monday.plusDays(i / 3).toString());
            m.put("activity", a.name()); m.put("past", i < from);
            int loc = a.local() ? (o.location(i) >= 0 ? o.location(i) : prev) : -1;
            boolean travel = false;
            if (i >= from && loc >= 0) {
                travel = loc != prev && i % 3 != 0 && geo.distance(loc, prev) > 220;
                prev = loc;
            }
            m.put("state", loc >= 0 ? geo.code(loc) : null);
            m.put("stateName", loc >= 0 ? g.states().get(geo.code(loc)).name() : null);
            m.put("param", o.parameter(i));
            m.put("paramName", a.parameterName(o.parameter(i)));
            m.put("travel", travel);
            out.add(m);
        }
        return out;
    }

    /** The activity menu, in Battleground 2028's order, with costs and the stamina each costs this candidate. */
    static List<Object> activities(GameEngine g) {
        CampaignSide me = g.player();
        double fm = 1.3 - me.ratings().sta() / 150.0;
        var order = List.of(game.mandate.core.campaign.Activity.RALLY, game.mandate.core.campaign.Activity.TOWN_HALL, game.mandate.core.campaign.Activity.RETAIL,
                game.mandate.core.campaign.Activity.DINNER, game.mandate.core.campaign.Activity.SPEECH, game.mandate.core.campaign.Activity.UNION,
                game.mandate.core.campaign.Activity.CHURCH, game.mandate.core.campaign.Activity.CAMPUS, game.mandate.core.campaign.Activity.COMMUNITY,
                game.mandate.core.campaign.Activity.FUNDRAISER, game.mandate.core.campaign.Activity.CALL_TIME, game.mandate.core.campaign.Activity.PODCAST,
                game.mandate.core.campaign.Activity.TV, game.mandate.core.campaign.Activity.CROSSOVER, game.mandate.core.campaign.Activity.PREP,
                game.mandate.core.campaign.Activity.REST);
        List<Object> out = new ArrayList<>();
        for (var a : order) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.name()); m.put("label", a.label()); m.put("short", a.shortLabel()); m.put("kind", a.kind().name().toLowerCase());
            m.put("cost", a.cost()); m.put("stamina", r2(a.fatigue() * fm * 0.55)); m.put("local", a.local()); m.put("description", a.description());
            List<Object> choices = new ArrayList<>();
            for (int p = 0; p < a.parameterChoices(); p++) {
                Map<String, Object> c = new LinkedHashMap<>();
                c.put("name", a.parameterName(p));
                if (a == game.mandate.core.campaign.Activity.PODCAST) {
                    var show = game.mandate.core.campaign.Show.ALL.get(p);
                    c.put("audience", show.audience()); c.put("tier", game.mandate.core.campaign.Show.TIER_NAME[show.tier()]);
                    c.put("views", show.views()); c.put("lean", show.lean());
                    c.put("refusal", g.campaignEngine().showRefusal(me, p, g.date()));
                }
                choices.add(c);
            }
            m.put("choices", choices);
            out.add(m);
        }
        return out;
    }

    // ---- the ground game (Battleground 2028's) -------------------------------------------------------------------

    /** The player's field programme: totals, doors week by week, and every state's offices, organisers, volunteers and contacts. */
    static Map<String, Object> field(GameEngine g) {
        CampaignSide me = g.player();
        var ce = g.campaignEngine();
        var geo = ce.geography();
        var o = me.orders();
        var t = g.campaignTurn();
        double[] value = ce.stateValue(t.polls(), t.nationalPoll(), me.party().sign(), 0);
        double offices = 0, org = 0, party = 0, vol = 0, shifts = 0, doors = 0, calls = 0, texts = 0, rel = 0, doorsTotal = 0;
        List<Object> states = new ArrayList<>();
        for (int i = 0; i < geo.size(); i++) {
            String c = geo.code(i);
            offices += me.offices(c); org += me.organizers(c); party += me.partyOrganizers(c); vol += me.volunteers(c); shifts += me.shifts(c);
            doors += me.doors(c); calls += me.calls(c); texts += me.texts(c); rel += me.relationalContacts(c); doorsTotal += me.doorsToDate(c);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", c);
            m.put("offices", me.offices(c)); m.put("officeOrder", o.officeOrder(i));
            m.put("organizers", me.organizers(c)); m.put("organizerOrder", o.organizerOrder(i));
            m.put("party", r2(me.partyOrganizers(c))); m.put("transfer", r3(o.transfer(i)));
            m.put("volunteers", Math.round(me.volunteers(c))); m.put("shifts", Math.round(me.shifts(c)));
            m.put("doors", Math.round(me.doors(c))); m.put("calls", Math.round(me.calls(c))); m.put("texts", Math.round(me.texts(c)));
            m.put("relational", Math.round(me.relationalContacts(c))); m.put("doorsToDate", Math.round(me.doorsToDate(c)));
            m.put("persuasion", r3(game.mandate.core.campaign.CampaignEngine.fieldSat(me.persuasion(c)) / 0.1));
            m.put("gotv", r3(game.mandate.core.campaign.CampaignEngine.gotvSat(me.getOutTheVote(c)) / 0.12));
            m.put("openCost", r3(ce.officeOpenCost(i))); m.put("officeWeek", r3(ce.officeWeek(i)));
            m.put("organizerWeek", r3(ce.organizerWeek(i)));
            m.put("value", r2(value[i]));
            states.add(m);
        }
        Map<String, Object> f = new LinkedHashMap<>();
        Map<String, Object> tot = new LinkedHashMap<>();
        tot.put("offices", offices); tot.put("organizers", org); tot.put("party", r2(party)); tot.put("volunteers", Math.round(vol));
        tot.put("shifts", Math.round(shifts)); tot.put("doors", Math.round(doors)); tot.put("calls", Math.round(calls)); tot.put("texts", Math.round(texts));
        tot.put("relational", Math.round(rel)); tot.put("doorsToDate", Math.round(doorsTotal));
        f.put("totals", tot);
        f.put("hireCost", game.mandate.core.campaign.CampaignEngine.organizerHireCost());
        List<Object> hist = new ArrayList<>();
        for (double[] row : me.history()) hist.add(Math.round(row[3]));
        f.put("doorsByTurn", hist);
        f.put("states", states);
        return f;
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
