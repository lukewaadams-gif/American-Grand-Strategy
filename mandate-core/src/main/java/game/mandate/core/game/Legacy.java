package game.mandate.core.game;

import game.mandate.core.data.Json;
import game.mandate.core.economy.EconomyState;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.military.TheaterOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The end-of-game verdict. Components are listed separately so the player can see exactly what their
 * score is made of; a single opaque number is the most common complaint about strategy-game endings.
 */
public record Legacy(int score, String grade, List<String> lines) {

    public String headline() { return "Legacy: " + grade + " (" + score + ")"; }

    public static Legacy score(GameEngine g) {
        var t = g.tuning();
        List<String> lines = new ArrayList<>();
        int score = 0;
        ElectionResult r28 = g.result2028().orElseThrow();
        boolean won28 = r28.winner() == g.player().party();
        if (!won28) {
            lines.add("Lost the 2028 election, " + r28.electoralVotes(g.player().party()) + " electoral votes.");
            score += Math.max(0, r28.electoralVotes(g.player().party()) - t.i("legacy.lossFloorEv"));
            return new Legacy(score, grade(g, score), lines);
        }
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
        lines.add(String.format("Misery index %.1f -> %.1f (%+d).", g.startMisery(), e.misery(), econ));

        int app = (int) Math.round((g.approval() - t.d("legacy.approvalPivot")) * t.d("legacy.perApprovalPoint"));
        score += app;
        lines.add(String.format("Final approval %.0f%% (%+d).", g.approval(), app));

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
        return m;
    }

    public static Legacy fromJson(Map<String, Object> m) {
        return new Legacy(Json.integer(m.get("score")), (String) m.get("grade"), Json.array(m.get("lines")).stream().map(o -> (String) o).toList());
    }
}
