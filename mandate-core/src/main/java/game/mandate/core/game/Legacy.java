package game.mandate.core.game;

import game.mandate.core.data.Fmt;
import game.mandate.core.data.Json;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.military.TheaterOutcome;
import game.mandate.core.campaign.Issue;
import game.mandate.core.engine.IndicatorPoint;
import game.mandate.core.hill.HillState;
import game.mandate.core.terms.TermsState;
import game.mandate.core.westwing.WestWingData;
import game.mandate.core.westwing.WestWingEngine;
import game.mandate.core.westwing.WestWingState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The end-of-game verdict. Components are listed separately so the player can see exactly what their
 * score is made of; a single opaque number is the most common complaint about strategy-game endings.
 */
public record Legacy(int score, String grade, List<String> lines, List<Integer> measures, String rank) {

    public Legacy(int score, String grade, List<String> lines) { this(score, grade, lines, List.of(), null); }

    /** The historians' ten measures (Resolute Desk's {@code LegacyData.MEASURE}, after the C-SPAN survey's categories). */
    public static final List<String> MEASURES = List.of("Public persuasion", "Crisis leadership", "Economic management", "Moral authority",
            "International relations", "Administrative skills", "Relations with Congress", "Vision and agenda", "Pursued equal justice for all",
            "Performance within the times");

    /** The C-SPAN 2021 Presidential Historians Survey, in rank order; the scores are Resolute Desk's internal scale, used only to place a president. */
    static final String[] RANKED = {"Abraham Lincoln", "George Washington", "Franklin D. Roosevelt", "Theodore Roosevelt", "Dwight D. Eisenhower", "Harry S. Truman",
            "Thomas Jefferson", "John F. Kennedy", "Ronald Reagan", "Barack Obama", "Lyndon B. Johnson", "James Monroe", "Woodrow Wilson", "William McKinley", "John Adams",
            "James Madison", "John Quincy Adams", "James K. Polk", "Bill Clinton", "Ulysses S. Grant", "George H. W. Bush", "Andrew Jackson", "William Howard Taft",
            "Calvin Coolidge", "Grover Cleveland", "Jimmy Carter", "James A. Garfield", "Gerald Ford", "George W. Bush", "Chester A. Arthur", "Richard Nixon",
            "Benjamin Harrison", "Rutherford B. Hayes", "Martin Van Buren", "Zachary Taylor", "Herbert Hoover", "Warren G. Harding", "Millard Fillmore", "John Tyler",
            "William Henry Harrison", "Donald Trump", "Franklin Pierce", "Andrew Johnson", "James Buchanan"};
    static final int[] RANK_SCORE = {897, 851, 841, 785, 734, 713, 704, 699, 681, 664, 654, 643, 617, 612, 609, 604, 603, 599, 594, 590, 585, 568, 543, 535, 523, 507,
            506, 498, 495, 472, 463, 448, 443, 435, 430, 396, 380, 370, 360, 350, 312, 305, 265, 227};

    public String headline() {
        return measures.isEmpty() ? "Legacy: " + grade + " (" + score + ")" : "Legacy: " + grade + ", " + score + " of 1000. " + rank;
    }

    public static Legacy score(GameEngine g) {
        if (g.mode() == GameSetup.Mode.CAMPAIGN_ONLY) return campaign(g);
        if (g.president().isPresent() && g.hill().isPresent()) return verdict(g);
        var t = g.tuning();
        List<String> lines = new ArrayList<>();
        int score = 0;
        ElectionResult r28 = g.result2028().orElseThrow();
        boolean won28 = r28.winner() == g.player().party();
        // a campaign that lost is judged as a campaign, never as a presidency it did not have (B-07)
        if (!won28) return campaign(g);
        int win = t.i("legacy.win");
        score += win;
        lines.add("Won the presidency with " + r28.electoralVotes(g.player().party()) + " electoral votes (+" + win + ").");

        int laws = g.laws().size(), perLaw = t.i("legacy.perLaw"), perAgenda = t.i("legacy.perAgenda");
        long agendaDone = g.agenda().stream().filter(g.laws()::contains).count();
        score += laws * perLaw + (int) agendaDone * perAgenda;
        lines.add(laws + " laws signed (+" + laws * perLaw + "); " + agendaDone + " of " + g.agenda().size() + " agenda priorities (+" + agendaDone * perAgenda + ").");

        EconomyState e = g.economy().orElseThrow();
        int econ = (int) Math.round((g.startMisery() - e.misery()) * t.d("legacy.perMiseryPoint"));
        score += econ;
        lines.add(Fmt.format("Misery index %.1f -> %.1f (%+d).", g.startMisery(), e.misery(), econ));

        int app = (int) Math.round((g.approval() - t.d("legacy.approvalPivot")) * t.d("legacy.perApprovalPoint"));
        score += app;
        lines.add(Fmt.format("Final approval %.0f%% (%+d).", g.approval(), app));

        if (g.war().isPresent()) {
            TheaterOutcome w = g.war().get();
            int pts = switch (w.status()) { case DEFENDED -> t.i("legacy.war.defended"); case STALEMATE -> t.i("legacy.war.stalemate"); case FALLEN -> t.i("legacy.war.fallen"); };
            score += pts;
            lines.add("Taiwan: " + w.status().name().toLowerCase() + " (" + (pts >= 0 ? "+" : "") + pts + "); " + w.usKilled() + " Americans killed.");
        } else if (g.diplomacy().rung() != null) {
            score += t.i("legacy.peaceThroughCrisis");
            lines.add("Kept the peace through a Taiwan crisis (+" + t.i("legacy.peaceThroughCrisis") + ").");
        }

        boolean re = g.result2032().map(r -> r.winner() == g.player().party()).orElse(false);
        g.result2032().ifPresent(r -> lines.add((re ? "Re-elected" : "Defeated") + " in 2032, " + r.electoralVotes(g.player().party()) + " electoral votes"
                + (re ? " (+" + t.i("legacy.reelected") + ")." : ".")));
        if (re) score += t.i("legacy.reelected");
        return new Legacy(score, grade(g, score), lines);
    }

    /** A campaign's verdict (Campaign Only, or a 2028 defeat): the campaign's grade, score (its electoral votes) and assessment. */
    static Legacy campaign(GameEngine g) {
        var a = g.campaignAssessment().orElseThrow();
        List<String> lines = new ArrayList<>();
        lines.add(a.headline());
        lines.addAll(a.lines());
        return new Legacy(a.electoralVotes(), a.grade(), lines);
    }

    /** Reads every legacy constant so a missing one fails at start-up, not at the end of a four-year game. */
    public static void validate(game.mandate.core.data.Tuning t) {
        for (String k : java.util.List.of("lossFloorEv", "win", "perLaw", "perAgenda", "perMiseryPoint", "approvalPivot", "perApprovalPoint",
                "war.defended", "war.stalemate", "war.fallen", "peaceThroughCrisis", "reelected", "grade.historic", "grade.consequential",
                "grade.solid", "grade.troubled", "grade.failed")) t.d("legacy." + k);
    }

    private static String grade(GameEngine g, int s) {
        var t = g.tuning();
        if (s >= t.i("legacy.grade.historic")) return "Historic presidency";
        if (s >= t.i("legacy.grade.consequential")) return "Consequential presidency";
        if (s >= t.i("legacy.grade.solid")) return "Solid presidency";
        if (s >= t.i("legacy.grade.troubled")) return "Troubled presidency";
        if (s >= t.i("legacy.grade.failed")) return "Failed presidency";
        return "A campaign remembered by few";
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", score); m.put("grade", grade); m.put("lines", lines);
        if (!measures.isEmpty()) { m.put("measures", new ArrayList<Object>(measures)); m.put("rank", rank); }
        return m;
    }

    public static Legacy fromJson(Map<String, Object> m) {
        List<Integer> ms = m.get("measures") == null ? List.of() : Json.array(m.get("measures")).stream().map(Json::integer).toList();
        return new Legacy(Json.integer(m.get("score")), (String) m.get("grade"), Json.array(m.get("lines")).stream().map(o -> (String) o).toList(), ms, (String) m.get("rank"));
    }

    // =========================================================================================
    //  The verdict on a presidency (Resolute Desk's legacy/Legacy: the historians' scores and ranking, the record)
    // =========================================================================================

    static double clamp100(double v) { return Math.max(0, Math.min(100, v)); }

    /** One verdict across every term served: ten measures of 0-100, their total, and a place among the presidents. */
    static Legacy verdict(GameEngine g) {
        List<IndicatorPoint> h = g.indicators();
        IndicatorPoint first = h.isEmpty() ? null : h.get(0), last = h.isEmpty() ? null : h.get(h.size() - 1);
        double avg = 0, gsum = 0;
        for (IndicatorPoint p : h) { avg += p.approval(); gsum += p.growth(); }
        avg = h.isEmpty() ? g.approval() : avg / h.size();
        double gavg = h.isEmpty() ? 2 : gsum / h.size();
        double years = h.size() < 2 ? 1 : Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(first.date(), last.date()) / 365.25);
        HillState hill = g.hill().orElseThrow();
        WestWingState ww = g.westWing().orElse(null);
        TermsState terms = g.terms().orElse(null);
        String ending = terms == null || terms.ending() == null ? "served" : terms.ending();
        int term = g.term();
        boolean removed = ending.equals("removed"), lost = ending.equals("lost");
        double[] s = new double[10];
        // public persuasion: approval across the presidency, and the big speeches
        s[0] = clamp100(46 + (avg - 50) * 1.8 + Math.min(6, (ww == null ? 0 : ww.speeches()) * 0.5));
        // crisis leadership: the crises and wars, and how they ended
        int[] crises = g.crisisRecord();
        int nCr = crises[0] + crises[1];
        double warsWon = 0, warsLost = 0, warsDrawn = 0;
        for (TheaterOutcome x : g.wars()) switch (x.status()) { case DEFENDED -> warsWon++; case FALLEN -> warsLost++; case STALEMATE -> warsDrawn++; }
        s[1] = clamp100(28 + (nCr > 0 ? 45.0 * crises[0] / nCr : 22) + Math.min(8, nCr * 0.3) + 12 * warsWon + 4 * warsDrawn - 18 * warsLost);
        // economic management: jobs, growth against potential, prices, markets and the debt
        double u0 = first == null ? 4.4 : first.unemployment(), pi0 = first == null ? 2.5 : first.inflation();
        double u = last == null ? u0 : last.unemployment(), pi = last == null ? pi0 : last.inflation();
        double sp0 = detail(first, "stocks", 100), sp = detail(last, "stocks", sp0), d0 = detail(first, "debt", 100), d = detail(last, "debt", d0);
        double potential = detail(last, "potential", 2);
        s[2] = clamp100(52 + 3.2 * (u0 - u) + 4 * (gavg - potential) - 2.5 * Math.max(0, pi - Math.max(3, pi0)) + 2.5 * Math.max(0, pi0 - Math.max(3, pi))
                + 6 * Math.log(Math.max(0.2, sp / Math.max(0.01, sp0))) - 0.15 * Math.max(0, d - d0));
        // moral authority: scandals, impeachment, pardons, the Court
        double sev = 0;
        if (ww != null) for (WestWingState.Scandal k : ww.scandals()) if (k.kind() != WestWingState.Scandal.LEAK) sev += k.severity();
        String imp = hill.impeachment();
        int impeached = imp.equals("trial") || imp.equals("acquitted") || imp.equals("removed") ? 1 : 0;
        int justices = ww == null ? 0 : ww.justicesAppointed();
        s[3] = clamp100(72 - sev * 1.5 - impeached * 15 - (removed ? 45 : 0) - (ww == null ? 0 : ww.pardons()) * 0.5 + justices * 4 + (ending.equals("retired") && term == 1 ? 4 : 0));
        // international relations: the world as the president leaves it
        double[] world = g.worldRecord();
        s[4] = clamp100(36 + (world[0] - 50) * 0.5 + Math.min(18, world[1] * 2.5) + 8 * warsWon + 5 * warsDrawn - 14 * warsLost + Math.min(6, world[2] * 0.5));
        // administrative skills: the staff's morale, departures, failures and the chief of staff
        double mor = 0;
        int people = 0;
        if (ww != null) for (WestWingState.Person p : ww.everyone()) { mor += p.morale(); people++; }
        mor = people == 0 ? 55 : mor / people;
        s[5] = clamp100(52 + (mor - 55) * 0.6 - (ww == null ? 0 : ww.departures()) / years * 3.5 - (ww == null ? 0 : ww.failures()) * 3
                + (ww == null ? 0 : WestWingEngine.sq(ww, WestWingData.S_COS) - 0.6) * 40);
        // relations with Congress: laws, overrides, shutdowns, standing on the Hill
        int laws = hill.enacted().size();
        double standing = g.president().map(p -> p.hill()).orElse(50.0);
        s[6] = clamp100(34 + Math.min(34, laws / years * 7) - hill.overrides() * 5 - hill.shutdowns() * 6 + (standing - 50) * 0.4);
        // vision and agenda: the laws that mattered, and the second term's goals
        double big = 0;
        for (String id : hill.enacted()) big += g.lawWeight(id);
        List<Map.Entry<String, Boolean>> goals = g.legacyGoals();
        double goalPoints = 0;
        for (Map.Entry<String, Boolean> e : goals) goalPoints += e.getValue() ? 6 : -3;
        s[7] = clamp100(24 + Math.min(62, big * 1.5) + goalPoints);
        // equal justice: the Court and standing on democracy and rights
        double rights = g.issueApproval().getOrDefault(Issue.DEMOCRACY, 50.0);
        s[8] = clamp100(38 + justices * 8 + (rights - 50) * 0.25);
        // performance within the times: what the president inherited, and how it ended
        double hard = Math.max(0, u0 - 6) * 1.5 + Math.max(0, pi0 - 5) * 2 + g.wars().size() * 6;
        s[9] = clamp100(45 + hard + (avg - 50) * 0.8 + (term >= 2 ? 6 : 0) - (lost ? 8 : 0) - (removed ? 25 : 0));
        double adj = switch (g.setup().difficulty()) { case HARD -> 3; case STORY -> -3; default -> 0; };
        List<Integer> ms = new ArrayList<>();
        int total = 0;
        for (double v : s) { int x = (int) Math.round(clamp100(v + adj)); ms.add(x); total += x; }

        // where the president lands among the presidents
        String me = g.player().candidate().name();
        List<String> names = new ArrayList<>();
        List<Integer> sc = new ArrayList<>();
        for (int i = 0; i < RANKED.length; i++) if (!RANKED[i].equals(me)) { names.add(RANKED[i]); sc.add(RANK_SCORE[i]); }
        int pos = 0;
        while (pos < sc.size() && sc.get(pos) > total) pos++;
        int n = names.size() + 1;
        String above = pos > 0 ? names.get(pos - 1) : null, below = pos < names.size() ? names.get(pos) : null;
        String rank = "Historians rank you " + ordinal(pos + 1) + " of " + n + " presidents"
                + (above != null && below != null ? ", between " + above + " and " + below : above == null ? ", ahead of " + below : ", behind " + above) + ".";
        String grade = total >= 760 ? "A great presidency" : total >= 660 ? "A near-great presidency" : total >= 570 ? "An above-average presidency"
                : total >= 480 ? "An average presidency" : total >= 380 ? "A below-average presidency" : "A failed presidency";

        List<String> lines = new ArrayList<>();
        int endYear = g.date().getYear();
        lines.add(switch (ending) {
            case "removed" -> "The Senate removed you from office on " + longDate(g.date()) + ".";
            case "lost" -> "You lost your bid for a second term" + (terms != null && terms.successor() != null ? " to " + terms.successor() : "") + ", and handed over the office on January 20, " + endYear + ".";
            case "denied" -> "Your own party turned you out in the primaries. You left office on January 20, " + endYear + ".";
            case "retired" -> "You chose not to run again, and left office on January 20, " + endYear + ".";
            default -> "You served " + (term >= 2 ? "two terms" : "a full term") + " and left office on January 20, " + endYear + ".";
        });
        if (terms != null && terms.race() != null && terms.race().done()) {
            TermsState.Race r = terms.race();
            TermsState.Contender winner = r.won() ? r.nomineeContender() : r.opponent();
            lines.add("Your successor: " + winner.name() + " won the " + r.year() + " election, " + Math.max(r.evMe(), r.evOpp()) + " electoral votes to "
                    + Math.min(r.evMe(), r.evOpp()) + (r.won() ? ", keeping the White House in your party's hands." : ", and the White House changed parties."));
        }
        lines.add(Fmt.format("The economy: unemployment went from %.1f%% to %.1f%%, inflation from %.1f%% to %.1f%%, the public debt from %.0f%% to %.0f%% of GDP; stocks %s %.0f%%.",
                u0, u, pi0, pi, d0, d, sp >= sp0 ? "rose" : "fell", Math.abs(sp / Math.max(0.01, sp0) - 1) * 100));
        lines.add(laws == 0 ? "Your laws: no major legislation became law." : "Your laws: " + laws + (laws == 1 ? " law" : " laws") + " signed, " + hill.vetoes() + " vetoes, "
                + hill.overrides() + " overridden.");
        if (!g.wars().isEmpty()) for (TheaterOutcome x : g.wars())
            lines.add("War: Taiwan was " + x.status().name().toLowerCase() + ", at a cost of " + x.usKilled() + " American lives.");
        for (Map.Entry<String, Boolean> e : goals) lines.add("Legacy goal: " + e.getKey() + (e.getValue() ? " (met)." : " (missed)."));
        for (int i = 0; i < 10; i++) lines.add(MEASURES.get(i) + ": " + ms.get(i) + " of 100.");
        lines.add(rank);
        return new Legacy(total, grade, lines, ms, rank);
    }

    static String longDate(java.time.LocalDate d) {
        String m = d.getMonth().name();
        return m.charAt(0) + m.substring(1).toLowerCase() + " " + d.getDayOfMonth() + ", " + d.getYear();
    }

    static double detail(IndicatorPoint p, String key, double fallback) {
        if (p == null) return fallback;
        int i = IndicatorPoint.DETAIL.indexOf(key);
        return i >= 0 && i < p.detail().length ? p.detail()[i] : fallback;
    }

    static String ordinal(int n) {
        int m = n % 100;
        String suf = m >= 11 && m <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + suf;
    }
}
