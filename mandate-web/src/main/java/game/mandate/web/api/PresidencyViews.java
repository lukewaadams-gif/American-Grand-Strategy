package game.mandate.web.api;

import game.mandate.core.campaign.Issue;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.news.News;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.politics.PoliticsState;
import game.mandate.core.presidency.Activity;
import game.mandate.core.presidency.PresidentEngine;
import game.mandate.core.presidency.PresidentState;
import game.mandate.core.presidency.WatchPlan;
import game.mandate.core.presidency.WeekContext;
import game.mandate.core.time.Phase;
import game.mandate.web.Notable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The presidency's view models beyond the Oval Office's bar and cards: the president's week (the calendar, what each
 * slot costs, the activities and where each may go, the week ahead and Air Force One's itinerary), the issues and the
 * states it reads, the watch plan, and The Capital Ledger's front page. Readers only.
 */
final class PresidencyViews {
    private PresidencyViews() {}

    static double r1(double v) { return Math.round(v * 10) / 10.0; }

    /** The calendar and the president's condition. */
    static Map<String, Object> week(GameEngine g) {
        PresidentState p = g.president().orElseThrow();
        PresidentEngine e = g.presidentEngine();
        Map<String, Object> m = new LinkedHashMap<>();
        LocalDate monday = g.date();
        List<Object> slots = new ArrayList<>(), itinerary = new ArrayList<>();
        int policy = 0, trips = 0, recharge = 0;
        for (int i = 0; i < PresidentEngine.SLOTS; i++) {
            Activity a = p.activity(i);
            String arg = p.argument(i);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("slot", i); s.put("day", PresidentEngine.DAYS.get(i / 3)); s.put("part", PresidentEngine.PARTS.get(i % 3));
            s.put("date", monday.plusDays(i / 3).toString());
            s.put("activity", a.name()); s.put("label", a.label); s.put("arg", arg); s.put("argLabel", argLabel(g, a, arg));
            s.put("cost", r1(e.slotCost(p, i)));
            if (a.isTrip() && arg != null) {
                double h = e.flight(arg);
                s.put("flight", r1(h));
                itinerary.add(Map.of("slot", i, "day", PresidentEngine.DAYS.get(i / 3), "state", arg, "name", g.states().get(arg).name(), "hours", r1(h)));
            }
            slots.add(s);
            if (a == Activity.POLICY || a == Activity.REMARKS) policy++;
            if (a.isTrip()) trips++;
            if (a == Activity.REST || a == Activity.FAMILY || a == Activity.CAMP_DAVID) recharge++;
        }
        m.put("slots", slots);
        m.put("itinerary", itinerary);
        m.put("counts", Map.of("policy", policy, "trips", trips, "recharge", recharge));
        m.put("stamina", r1(p.stamina())); m.put("stress", r1(p.stress())); m.put("sleep", r1(p.sleep()));
        m.put("health", r1(p.health())); m.put("fitness", r1(p.fitness())); m.put("sick", p.sickWeeks());
        m.put("projected", r1(e.projectedStamina(p))); m.put("sleepAhead", r1(e.sleepOf(p)));
        m.put("age", p.age()); m.put("location", p.location()); m.put("locationName", g.states().get(p.location()).name());
        m.put("week", p.week()); m.put("delegated", p.delegated()); m.put("warChest", r1(p.warChest())); m.put("hill", r1(p.hill()));
        m.put("hillHours", r1(p.hillHours())); m.put("lastWeek", p.lastWeek()); m.put("lastStaminaChange", r1(p.lastStaminaChange()));
        m.put("midtermSeason", g.midtermSeason());
        return m;
    }

    static String argLabel(GameEngine g, Activity a, String arg) {
        if (arg == null) return null;
        return switch (a.arg) {
            case ISSUE -> Issue.valueOf(arg).label();
            case STATE -> g.states().get(arg).name();
            default -> arg;
        };
    }

    /** Every activity a player can choose, with why it cannot go in each slot (null where it can). */
    static List<Object> activities(GameEngine g) {
        PresidentEngine e = g.presidentEngine();
        boolean season = g.midtermSeason();
        List<Object> out = new ArrayList<>();
        for (Activity a : Activity.values()) {
            if (a == Activity.TRAVEL) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.name()); m.put("label", a.label); m.put("description", a.description);
            m.put("stamina", a.stamina); m.put("stress", a.stress); m.put("arg", a.arg.name());
            List<Object> no = new ArrayList<>();
            for (int i = 0; i < PresidentEngine.SLOTS; i++) no.add(e.refusal(i, a, season));
            m.put("refusals", no);
            out.add(m);
        }
        return out;
    }

    /** The issues as the week reads them: approval on each, command of it, standing on it and how much it matters. */
    static List<Object> issues(GameEngine g) {
        PoliticsState s = g.state().politics();
        WeekContext c = g.weekContext();
        Issue top = g.presidentEngine().topIssue(c);
        List<Object> out = new ArrayList<>();
        for (Issue i : Issue.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.name()); m.put("label", i.label()); m.put("approval", r1(s.issueApproval(i))); m.put("command", r1(s.command(i)));
            m.put("standing", Math.round(s.standing(i) * 100) / 100.0); m.put("salience", Math.round(s.salience(i) * 1000) / 1000.0); m.put("top", i == top);
            out.add(m);
        }
        return out;
    }

    /** The states for a trip: approval there, the flight, and the president's standing from recent visits. */
    static List<Object> states(GameEngine g) {
        PoliticsState s = g.state().politics();
        PresidentEngine e = g.presidentEngine();
        List<Object> out = new ArrayList<>();
        for (UsState u : g.states().all()) {
            if (u.code().equals("DC")) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", u.code()); m.put("name", u.name()); m.put("ev", u.electoralVotes());
            m.put("approval", r1(s.stateApproval().getOrDefault(u.code(), g.approval()))); m.put("flight", r1(e.flight(u.code())));
            m.put("standing", r1(s.stateStanding(u.code())));
            out.add(m);
        }
        return out;
    }

    /** The watch plan: the ready-made plans, the six priorities and the stops. */
    static Map<String, Object> watch(GameEngine g) {
        WatchPlan p = g.president().orElseThrow().plan();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("preset", p.preset());
        List<Object> presets = new ArrayList<>(), pri = new ArrayList<>(), stops = new ArrayList<>();
        for (int i = 0; i < WatchPlan.PRESET.size(); i++) presets.add(Map.of("label", WatchPlan.PRESET.get(i), "description", WatchPlan.PRESET_DESC.get(i)));
        for (int i = 0; i < WatchPlan.PRIORITIES; i++)
            pri.add(Map.of("label", WatchPlan.PRIORITY.get(i), "low", WatchPlan.PRIORITY_ENDS.get(i).get(0), "high", WatchPlan.PRIORITY_ENDS.get(i).get(1),
                    "description", WatchPlan.PRIORITY_DESC.get(i), "value", p.priority(i)));
        for (int i = 0; i < WatchPlan.STOPS; i++) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("label", WatchPlan.STOP.get(i)); s.put("on", p.stops(i));
            s.put("available", i == WatchPlan.STOP_WORLD || i == WatchPlan.STOP_BUDGET || i == WatchPlan.STOP_SIGN || i == WatchPlan.STOP_SCANDAL);
            stops.add(s);
        }
        m.put("presets", presets); m.put("priorities", pri); m.put("stops", stops);
        m.put("roadWeeks", p.roadWeeks());
        return m;
    }

    /** The hundred days' business the chief of staff can run until Congress and the West Wing open in their rooms. */
    static Map<String, Object> hundredDays(GameEngine g) {
        var h = g.hundredDays().orElseThrow();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("week", h.week()); m.put("weeks", h.weeks()); m.put("stage", h.stage() == null ? null : h.stage().name());
        List<Object> ds = new ArrayList<>();
        String[][] labels = {{"LEGISLATION", "The flagship bill", "Draft it, whip it and call the votes"}, {"CABINET", "The cabinet", "Nominate the secretaries"},
                {"CRISIS", "Crises", "Answer the foreign crisis"}, {"BUDGET", "The budget", "Funding deadlines, shutdowns and the economy"}};
        for (String[] l : labels) ds.add(Map.of("id", l[0], "label", l[1], "detail", l[2], "on", h.delegated().contains(Domain.valueOf(l[0]))));
        m.put("domains", ds);
        return m;
    }

    // ---- The Capital Ledger ----------------------------------------------------------------------------------------

    record Story(String section, String head, String body, double weight, String kind) {}

    /** The week's front page (Resolute Desk's {@code ui/Paper}): the lead, six stories, features, In Brief and the numbers. */
    static Map<String, Object> paper(GameEngine g) {
        Map<String, Object> m = EngineApi.ok();
        LocalDate today = g.date(), from = today.minusDays(7);
        List<Story> st = new ArrayList<>();
        java.util.Set<String> heads = new java.util.HashSet<>();
        for (News n : g.news()) if (!n.date().isBefore(from)) { st.add(new Story(n.section(), n.head(), n.body(), n.weight(), n.kind().name())); heads.add(n.head()); }
        // the week's log lines that are not already stories, weighed as Notable classifies them
        for (String line : g.log()) {
            if (line.length() < 13 || line.substring(0, 10).compareTo(from.toString()) < 0) continue;
            String text = line.substring(12);
            if (heads.contains(text)) continue;
            heads.add(text);
            text = thirdPerson(text, g.player().candidate().name());
            var note = Notable.classify(line);
            boolean event = text.startsWith("EVENT: ");
            String head = event ? text.substring(7) : note.map(game.mandate.web.Session.Notice::title).orElse(text);
            String body = note.isPresent() && !event && !note.get().title().equals(text) ? text : null;
            double w = event ? 1.8 : note.map(n -> n.tone() == game.mandate.web.Session.Tone.BAD || n.tone() == game.mandate.web.Session.Tone.WARN ? 2.2 : 2.0).orElse(0.8);
            // the game's log speaks to the player ("Your flagship..."): such lines go to In Brief, not the front page's headlines
            if (note.isEmpty() && !event && (text.startsWith("You") || text.contains(" you ") || text.contains("Your "))) w = 0.3;
            st.add(new Story(null, head, body, w, note.map(n -> n.tone().name()).orElse("INFO")));
        }
        st.sort(Comparator.comparingDouble((Story s) -> -s.weight()));
        for (int i = 0; i < st.size(); i++) if (st.get(i).section() == null) { if (i > 0) st.add(0, st.remove(i)); break; }
        var pres = g.player().candidate();
        String last = pres.name().substring(pres.name().lastIndexOf(' ') + 1);
        PresidentState p = g.president().orElse(null);
        long week = p == null ? 0 : p.week() + 1;
        m.put("date", today.toString());
        m.put("edition", "Week " + week + " of the " + last + " administration");
        Story lead = st.isEmpty() ? null : st.get(0);
        double approval = g.approval();
        var e = g.economy().orElse(null);
        String topIssue = g.presidentEngine().topIssue(g.weekContext()).label().toLowerCase();
        String leadBody = "President " + last + " begins the week with " + Math.round(approval) + " percent of Americans approving of the job, according to the latest polling average. "
                + (e == null ? "" : "Unemployment stands at " + oneDp(e.unemployment()) + " percent and prices are up " + oneDp(e.inflation()) + " percent from a year ago. ")
                + (p != null && p.stamina() < 35 ? "Aides privately worry about the president's punishing schedule." : "Aides say the president's schedule this week focuses on " + topIssue + ".");
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("head", lead == null ? "A quiet week in Washington" : cap(lead.head()));
        l.put("body", (lead != null && lead.body() != null ? lead.body() + "\n\n" : "") + leadBody);
        l.put("kind", lead == null ? "INFO" : lead.kind());
        m.put("lead", l);
        m.put("photo", "President " + last + (p != null && !p.location().equals("DC") ? " in " + g.states().get(p.location()).name() : " at the White House"));
        List<Object> stories = new ArrayList<>(), brief = new ArrayList<>();
        for (int i = 1; i < Math.min(st.size(), 7); i++) stories.add(storyJson(st.get(i)));
        for (int i = 7; i < Math.min(st.size(), 15); i++) brief.add(storyJson(st.get(i)));
        m.put("stories", stories);
        m.put("brief", brief);
        m.put("features", features(g, last, topIssue));
        List<Object> nums = new ArrayList<>();
        nums.add(Map.of("label", "Approval", "value", Math.round(approval) + "%"));
        if (e != null) {
            nums.add(Map.of("label", "Unemployment", "value", oneDp(e.unemployment()) + "%"));
            nums.add(Map.of("label", "Inflation", "value", oneDp(e.inflation()) + "%"));
            nums.add(Map.of("label", "Gas", "value", "$" + game.mandate.core.data.Fmt.format("%.2f", g.gas())));
            nums.add(Map.of("label", "Fed funds", "value", game.mandate.core.data.Fmt.format("%.2f", e.fedRate()) + "%"));
            nums.add(Map.of("label", "10-year Treasury", "value", game.mandate.core.data.Fmt.format("%.2f", e.tenYear()) + "%"));
        }
        if (p != null) nums.add(Map.of("label", "Your stamina", "value", String.valueOf(Math.round(p.stamina()))));
        m.put("numbers", nums);
        return m;
    }

    static Map<String, Object> storyJson(Story s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("section", s.section()); m.put("head", cap(s.head())); m.put("body", s.body()); m.put("kind", s.kind());
        return m;
    }

    /** Resolute Desk's regular features: the week ahead, poll watch, markets (bonds and oil: this game has no stock market) and an opinion column. */
    static List<Object> features(GameEngine g, String last, String topIssue) {
        List<Object> out = new ArrayList<>();
        PresidentState p = g.president().orElse(null);
        List<String> trips = new ArrayList<>();
        if (p != null) for (int i = 0; i < PresidentEngine.SLOTS; i++) if (p.activity(i).isTrip() && p.argument(i) != null) trips.add(g.states().get(p.argument(i)).name());
        out.add(Map.of("head", "The week ahead", "body", (trips.isEmpty() ? "No travel is planned. " : "The president travels to " + String.join(", ", trips) + ". ")
                + "Policy meetings focus on " + topIssue + "."));
        var ind = g.indicators();
        double now = g.approval(), prev = ind.size() >= 5 ? ind.get(ind.size() - 5).approval() : now;
        String trend = now > prev + 1.5 ? "rising" : now < prev - 1.5 ? "slipping" : "steady";
        PoliticsState s = g.state().politics();
        Issue best = null, worst = null;
        for (Issue i : Issue.values()) {
            if (s.salience(i) < 0.06) continue;
            if (best == null || s.issueApproval(i) > s.issueApproval(best)) best = i;
            if (worst == null || s.issueApproval(i) < s.issueApproval(worst)) worst = i;
        }
        out.add(Map.of("head", "Poll watch", "body", "Approval is " + trend + " at " + Math.round(now) + "%." + (best == null ? ""
                : " Among the issues voters care about most, the president scores best on " + best.label().toLowerCase() + " and worst on " + worst.label().toLowerCase() + ".")));
        g.economy().ifPresent(e -> out.add(Map.of("head", "Markets", "body", "The 10-year Treasury yields " + game.mandate.core.data.Fmt.format("%.2f", e.tenYear())
                + "% and oil trades at $" + Math.round(e.oil()) + " a barrel.")));
        String opTitle = now > prev + 1.5 ? "Opinion: The president finds a groove" : now < prev - 1.5 ? "Opinion: Is the honeymoon over?"
                : g.economy().map(e -> e.inflation() > 4).orElse(false) ? "Opinion: Prices are the whole story" : "Opinion: The long game in Washington";
        String op = now < 42 ? "Allies on the Hill are getting nervous, and it shows." : now > 52 ? "For now the public is giving the White House the benefit of the doubt."
                : "Neither a crisis nor a triumph: the country is still making up its mind.";
        out.add(Map.of("head", opTitle, "body", op));
        return out;
    }

    /**
     * The game's log speaks to the player; the paper speaks of the president. "Week 3: You sign the Build More Homes Act"
     * becomes "Marsh signs the Build More Homes Act", and "your" becomes "the president's".
     */
    static String thirdPerson(String text, String presidentName) {
        String last = presidentName.substring(presidentName.lastIndexOf(' ') + 1);
        String t = text.replaceFirst("^Week \\d+: ", "");
        if (t.startsWith("You ")) {
            int sp = t.indexOf(' ', 4);
            String verb = sp < 0 ? t.substring(4) : t.substring(4, sp), rest = sp < 0 ? "" : t.substring(sp);
            t = last + " " + conjugate(verb) + rest;
        }
        return t.replace(" your ", " the president's ").replace("Your ", "The president's ");
    }

    /** A verb for "you" as it goes with "she" or "he": sign/signs, push/pushes, carry/carries, have/has; past tenses stay. */
    static String conjugate(String v) {
        if (v.isEmpty() || v.endsWith("ed") || v.equals("won") || v.equals("lost") || v.equals("chose") || v.equals("took") || v.equals("made")) return v;
        return switch (v) {
            case "have" -> "has";
            case "are" -> "is";
            case "were" -> "was";
            default -> v.matches(".*(s|sh|ch|x|z|o)") ? v + "es" : v.matches(".*[^aeiou]y") ? v.substring(0, v.length() - 1) + "ies" : v + "s";
        };
    }

    static String oneDp(double v) { return game.mandate.core.data.Fmt.format("%.1f", v); }

    static String cap(String s) { return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    /** Whether the presidency's week is in play (the first hundred days, or governing). */
    static boolean inOffice(GameEngine g) { return g.president().isPresent() && (g.phase() == Phase.FIRST_100_DAYS || g.phase() == Phase.GOVERNING); }
}
