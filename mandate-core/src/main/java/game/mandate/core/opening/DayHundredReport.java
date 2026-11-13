package game.mandate.core.opening;

import game.mandate.core.data.Fmt;
import game.mandate.core.data.Json;
import game.mandate.core.opening.Types.BillStage;
import game.mandate.core.opening.Types.CrisisOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The 100-day report: five goals, each met, partly met or missed, with the reason in one line, and an outcome.
 * No hidden score: the outcome follows from the goals by a rule the report states.
 *
 * <ul>
 * <li><b>Victory</b>: 9 or more of 10 points (at most one goal short) and the flagship signed.</li>
 * <li><b>Failure</b>: 4 points or fewer.</li>
 * <li><b>Partial success</b>: anything between.</li>
 * </ul>
 * A goal met is 2 points, partly met 1, missed 0.
 */
public record DayHundredReport(Outcome outcome, int score, List<Goal> goals, String headline, List<String> decisions,
                               List<Double> approvalByWeek, double startApproval, double approval) {

    public enum Outcome {
        VICTORY("Victory"), PARTIAL("Partial success"), FAILURE("Failure");

        public final String label;

        Outcome(String label) { this.label = label; }
    }

    public enum Status { MET, PARTLY, MISSED }

    public record Goal(String name, Status status, String why) {
        public int points() { return status == Status.MET ? 2 : status == Status.PARTLY ? 1 : 0; }
    }

    public DayHundredReport {
        goals = List.copyOf(goals);
        decisions = List.copyOf(decisions);
        approvalByWeek = List.copyOf(approvalByWeek);
    }

    public static final String RULE = "A goal met scores 2, partly met 1. Victory: 9 or more with the flagship signed. Failure: 4 or fewer.";

    /**
     * Grades a finished (or running) hundred days.
     * @param promises the campaign promises (bill ids); kept ones are law
     */
    public static DayHundredReport of(HundredDays hd, List<String> promises, List<String> laws) {
        Report r = hd.report();
        HundredDaysState st = hd.state();
        List<Goal> goals = new ArrayList<>();

        // 1. Legislation: the flagship.
        long kept = promises.stream().filter(laws::contains).count();
        String promiseLine = promises.isEmpty() ? "" : Fmt.format(" Campaign promises kept: %d of %d.", kept, promises.size());
        String bill = hd.billName();
        goals.add(switch (r.billStage()) {
            case LAW -> r.policyDelivered() >= 1.7
                    ? new Goal("Legislation", Status.MET, "You signed the " + bill + (hd.reconciliation() ? " through reconciliation" : "")
                    + " in week " + hd.weekSigned() + "." + promiseLine)
                    : new Goal("Legislation", Status.PARTLY, "You signed a scaled-back " + bill + " in week " + hd.weekSigned()
                    + ": law, but a small one." + promiseLine);
            case SENATE_FLOOR -> new Goal("Legislation", Status.PARTLY, "The " + bill + " passed the House but not the Senate." + promiseLine);
            case NOT_DRAFTED -> new Goal("Legislation", Status.MISSED, "You never sent Congress the " + bill + "." + promiseLine);
            case DEAD -> new Goal("Legislation", Status.MISSED, "The " + bill + " died." + promiseLine);
            default -> new Goal("Legislation", Status.MISSED, "The " + bill + " never passed a chamber." + promiseLine);
        });

        // 2. Administration: the cabinet.
        int confirmed = r.confirmed();
        boolean able = r.avgCompetence() >= 70;
        goals.add(new Goal("Administration", confirmed == 3 && able ? Status.MET : confirmed >= 2 ? Status.PARTLY : Status.MISSED,
                Fmt.format("%d of 3 cabinet secretaries confirmed; average competence %.0f%s.", confirmed, r.avgCompetence(),
                        confirmed == 3 && !able ? " (a cabinet of loyalists: confirmed, but not strong)" : "")));

        // 3. Economy: the challenge, judged by what it threatened (prices or jobs) since it struck, and what the answer cost in borrowing.
        double borrowed = r.deficitChange();
        Status econ;
        String econWhy;
        if (st.challenge == null || Double.isNaN(st.challengeStartInflation)) {
            double misery = hd.inflation() + hd.unemployment() - st.startMisery;
            econ = misery <= 0.3 ? Status.MET : misery <= 0.8 ? Status.PARTLY : Status.MISSED;
            econWhy = Fmt.format("The misery index %s %.1f points.", misery >= 0 ? "rose" : "fell", Math.abs(misery));
        } else if (st.challenge == Types.EconomicChallenge.INFLATION_SURGE) {
            double rise = hd.inflation() - st.challengeStartInflation;
            econ = rise <= 0.20 ? Status.MET : rise <= 0.40 ? Status.PARTLY : Status.MISSED;
            econWhy = Fmt.format("The inflation surge: inflation %s %.2f points since it struck, to %.1f%%.", rise >= 0 ? "rose" : "fell", Math.abs(rise), hd.inflation());
        } else {
            double rise = hd.unemployment() - st.challengeStartUnemployment;
            econ = rise <= 0.12 ? Status.MET : rise <= 0.28 ? Status.PARTLY : Status.MISSED;
            econWhy = Fmt.format("The hiring slump: unemployment %s %.2f points since it struck, to %.1f%%.", rise >= 0 ? "rose" : "fell", Math.abs(rise), hd.unemployment());
        }
        if (st.challengeResponse < 0 && st.challenge != null) econWhy += " You never answered it.";
        if (borrowed > 0.6 && econ != Status.MISSED) {
            econ = econ == Status.MET ? Status.PARTLY : Status.MISSED;
            econWhy += Fmt.format(" Bought with borrowing: the deficit is %.2f%% of GDP higher.", borrowed);
        }
        goals.add(new Goal("Economy", econ, econWhy));

        // 4. Security: the crisis.
        String where = st.crisisKind == null ? "the crisis" : "the " + st.crisisKind.label;
        goals.add(switch (r.crisis()) {
            case BACKED_DOWN -> new Goal("Security", Status.MET, "The other side backed down in " + where + Fmt.format("; deterrence %.0f.", r.deterrence()));
            case DEAL -> new Goal("Security", Status.MET, "Diplomacy ended " + where + " with a deal.");
            case STALEMATE -> new Goal("Security", Status.PARTLY, where.substring(0, 1).toUpperCase() + where.substring(1) + " settled into a stalemate.");
            case NONE -> new Goal("Security", Status.PARTLY, "No crisis came.");
            case ACTIVE -> hd.escalation() >= 2 ? new Goal("Security", Status.MISSED, "The crisis in " + where + " is still escalating.")
                    : new Goal("Security", Status.PARTLY, "The crisis in " + where + " is unresolved but contained.");
            case CLASH -> new Goal("Security", Status.MISSED, "An armed clash in " + where + " killed Americans.");
        });

        // 5. Standing: approval and the party behind you.
        double start = st.startApproval, now = r.approval();
        boolean united = r.wingMoodA() >= 35 && r.wingMoodB() >= 35;
        Status standing = now >= start - 2 && united ? Status.MET : now >= start - 6 ? Status.PARTLY : Status.MISSED;
        goals.add(new Goal("Standing", standing, Fmt.format("Approval %.0f%% (from %.0f%%); %s; the opposition is %s. Midterm outlook: %+d House seats.",
                now, start, united ? "your party is behind you" : "a wing of your party is in revolt", r.opposition().name().toLowerCase(), r.midtermSeatOutlook())));

        int score = goals.stream().mapToInt(Goal::points).sum();
        Outcome outcome = score >= 9 && r.billStage() == BillStage.LAW ? Outcome.VICTORY : score <= 4 ? Outcome.FAILURE : Outcome.PARTIAL;
        String headline = headline(outcome, goals, r);
        List<String> decisions = new ArrayList<>();
        for (String line : st.log)
            if (line.contains("Decision: ") || line.contains("You send Congress") || line.contains("You nominate") || line.contains("You sign"))
                decisions.add(line);
        return new DayHundredReport(outcome, score, goals, headline, decisions, st.approvalByWeek, start, now);
    }

    private static String headline(Outcome o, List<Goal> goals, Report r) {
        String best = goals.stream().filter(g -> g.status() == Status.MET).map(g -> g.name().toLowerCase()).reduce((a, b) -> a + ", " + b).orElse("nothing");
        String worst = goals.stream().filter(g -> g.status() == Status.MISSED).map(g -> g.name().toLowerCase()).reduce((a, b) -> a + ", " + b).orElse("nothing");
        return switch (o) {
            case VICTORY -> "A commanding start: " + best + " delivered.";
            case PARTIAL -> "A mixed start: " + best + " delivered; " + worst + " fell short.";
            case FAILURE -> "A troubled start: " + worst + " fell short" + (r.crisis() == CrisisOutcome.CLASH ? ", and Americans died abroad." : ".");
        };
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("outcome", outcome.name()); m.put("score", score); m.put("headline", headline);
        List<Object> gs = new ArrayList<>();
        for (Goal g : goals) gs.add(List.of(g.name(), g.status().name(), g.why()));
        m.put("goals", gs);
        m.put("decisions", new ArrayList<>(decisions)); m.put("approvalByWeek", new ArrayList<>(approvalByWeek));
        m.put("startApproval", startApproval); m.put("approval", approval);
        return m;
    }

    public static DayHundredReport fromJson(Map<String, Object> m) {
        List<Goal> goals = new ArrayList<>();
        for (Object o : Json.array(m.get("goals"))) {
            List<Object> g = Json.array(o);
            goals.add(new Goal((String) g.get(0), Status.valueOf((String) g.get(1)), (String) g.get(2)));
        }
        return new DayHundredReport(Outcome.valueOf((String) m.get("outcome")), Json.integer(m.get("score")), goals, (String) m.get("headline"),
                Json.array(m.get("decisions")).stream().map(o -> (String) o).toList(),
                Json.array(m.get("approvalByWeek")).stream().map(Json::num).toList(), Json.num(m.get("startApproval")), Json.num(m.get("approval")));
    }
}
