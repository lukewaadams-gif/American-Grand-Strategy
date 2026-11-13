package game.mandate.core.engine;

import game.mandate.core.data.Json;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An individual crisis (GameSetup.Mode.CRISIS, docs/MODES.md): which crisis, how it was set up, how the presidency
 * stood the day it began, and once it ends, the after-action report the game ends on.
 *
 * @param kind      "taiwan" or "hormuz"
 * @param preset    0 the base case, 1 optimistic, 2 pessimistic
 * @param approval  the president's approval the day it began
 * @param misery    the misery index the day it began
 * @param munitions the munitions index (0-100) the day it began
 */
public record CrisisRecord(String kind, int preset, LocalDate began, double approval, double misery, double munitions, Report report) {

    public static final List<String> KINDS = List.of("taiwan", "hormuz");
    public static final List<String> PRESETS = List.of("Base case", "Optimistic", "Pessimistic");

    public enum Result {
        WON("Won"), PARTIAL("Ended in part"), LOST("Lost");
        public final String label;
        Result(String label) { this.label = label; }
    }

    /**
     * The after-action report: the result (won, lost or ended in part), what happened and why, the figures, and the
     * crisis's own record of the days.
     */
    public record Report(String crisis, Result result, String outcome, String reason, LocalDate ended, int days, String verdict,
                         List<String> figures, List<String> record) {
        public Map<String, Object> toJson() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("crisis", crisis); m.put("result", result.name()); m.put("outcome", outcome); m.put("reason", reason);
            m.put("ended", ended.toString()); m.put("days", days); m.put("verdict", verdict);
            m.put("figures", new ArrayList<Object>(figures)); m.put("record", new ArrayList<Object>(record));
            return m;
        }

        static Report fromJson(Map<String, Object> m) {
            return new Report((String) m.get("crisis"), Result.valueOf((String) m.get("result")), (String) m.get("outcome"), (String) m.get("reason"),
                    LocalDate.parse((String) m.get("ended")), Json.integer(m.get("days")), (String) m.get("verdict"),
                    Json.array(m.get("figures")).stream().map(o -> (String) o).toList(), Json.array(m.get("record")).stream().map(o -> (String) o).toList());
        }
    }

    public String title() { return kind.equals("taiwan") ? "The Taiwan crisis" : "The Strait of Hormuz"; }

    public CrisisRecord withReport(Report r) { return new CrisisRecord(kind, preset, began, approval, misery, munitions, r); }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind); m.put("preset", preset); m.put("began", began.toString());
        m.put("approval", approval); m.put("misery", misery); m.put("munitions", munitions);
        m.put("report", report == null ? null : report.toJson());
        return m;
    }

    static CrisisRecord fromJson(Map<String, Object> m) {
        return new CrisisRecord((String) m.get("kind"), Json.integer(m.get("preset")), LocalDate.parse((String) m.get("began")), Json.num(m.get("approval")),
                Json.num(m.get("misery")), Json.num(m.get("munitions")), m.get("report") == null ? null : Report.fromJson(Json.object(m.get("report"))));
    }
}
