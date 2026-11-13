package game.mandate.core.news;

import game.mandate.core.campaign.Issue;
import game.mandate.core.data.Csv;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * The era's news beyond the White House (Resolute Desk's {@code stories/Stories.ambient}, its tables exported to
 * {@code data/rd/ambient.tsv}): one or two stories a week (two on a quiet week, often none on a busy one), each run
 * once, those that follow the news of the moment three times as likely as the era's background. A story can make
 * voters care more about an issue, or rally the country.
 */
public final class AmbientNews {
    /** Sections of the paper, by the letter each story is filed under (Resolute Desk's {@code StoryData.SECTION}). */
    static final String SECTION_CODES = "PEWSCXLNBHDT";
    static final List<String> SECTION = List.of("Politics", "The economy", "The world", "Science", "Culture", "Sports", "Crime and the courts",
            "Weather and the land", "Business", "Health", "Daily life", "Technology");

    record Story(int from, int to, String when, int section, String head, String body, String fx) {}

    /** A story that ran: its news item and what it did. */
    public record Run(int index, News news, List<Effect> effects) {}

    private final List<Story> stories = new ArrayList<>();

    public AmbientNews() {
        for (String[] r : Csv.readTsv("/data/rd/ambient.tsv")) {
            String[] t = r[1].split("\\|", -1);
            if (t.length != 7) throw new IllegalStateException("An ambient story needs 7 fields: " + r[1]);
            int section = SECTION_CODES.indexOf(t[3]);
            if (section < 0) throw new IllegalStateException("No section " + t[3]);
            stories.add(new Story(Integer.parseInt(t[0]), Integer.parseInt(t[1]), t[2], section, t[4], t[5], t[6]));
        }
    }

    public int size() { return stories.size(); }

    /**
     * This week's era news. {@code busy} is the weight of the week's Washington news; {@code var} reads the state of the
     * country for a story's conditions (NaN for what this game does not model, which fails the condition); {@code places}
     * are the states a story can happen in.
     */
    public List<Run> week(Set<Integer> used, double busy, LocalDate day, ToDoubleFunction<String> var, List<String> places,
                          DeterministicRandomManager rng) {
        double chance = busy > 12 ? 0.5 : busy > 6 ? 0.75 : 0.95;
        int n = busy < 5 ? 2 : 1;
        List<Run> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            if (!rng.chance("news.ambient", chance)) continue;
            int i = pick(used, day.getYear(), var, rng);
            if (i < 0) continue;
            used.add(i);
            Story s = stories.get(i);
            String place = places.get(rng.nextInt("news.ambient.place", places.size()));
            News.Kind kind = s.section == 1 || s.section == 8 ? News.Kind.ECON : s.section == 2 ? News.Kind.WORLD : News.Kind.INFO;
            News item = new News(day, kind, s.head.replace("{state}", place), s.body.isEmpty() ? null : s.body.replace("{state}", place),
                    0.5 + 0.4 * rng.uniform("news.ambient.weight"), SECTION.get(s.section));
            out.add(new Run(i, item, effects(s.fx)));
        }
        return out;
    }

    private int pick(Set<Integer> used, int year, ToDoubleFunction<String> var, DeterministicRandomManager rng) {
        double total = 0;
        double[] w = new double[stories.size()];
        for (int i = 0; i < w.length; i++) {
            Story x = stories.get(i);
            if (used.contains(i) || year < x.from || year > x.to || !holds(x.when, var, rng)) continue;
            w[i] = x.when.isEmpty() ? 1 : 3;
            total += w[i];
        }
        if (total <= 0) return -1;
        double r = rng.uniform("news.ambient.pick") * total;
        for (int i = 0; i < w.length; i++) { r -= w[i]; if (w[i] > 0 && r <= 0) return i; }
        return -1;
    }

    /** "unemp>7,month<4": every clause must hold; "p=0.02" is a weekly chance. */
    static boolean holds(String c, ToDoubleFunction<String> var, DeterministicRandomManager rng) {
        if (c == null || c.isEmpty()) return true;
        for (String t : c.split(",")) {
            if (t.startsWith("p=")) { if (!rng.chance("news.ambient.p", Double.parseDouble(t.substring(2)))) return false; continue; }
            int i = t.indexOf('>'), j = t.indexOf('<');
            if (i > 0) { if (!(var.applyAsDouble(t.substring(0, i)) > Double.parseDouble(t.substring(i + 1)))) return false; }
            else if (j > 0) { if (!(var.applyAsDouble(t.substring(0, j)) < Double.parseDouble(t.substring(j + 1)))) return false; }
        }
        return true;
    }

    /** "sal:PRICES=0.03" makes an issue matter more (relative to its usual weight); "rally=0.5" rallies the country. */
    static List<Effect> effects(String fx) {
        List<Effect> out = new ArrayList<>();
        if (fx == null || fx.trim().isEmpty()) return out;
        for (String tok : fx.split(" ")) {
            int eq = tok.indexOf('=');
            if (eq < 0) continue;
            double v = Double.parseDouble(tok.substring(eq + 1));
            if (tok.startsWith("sal:")) { Issue i = issue(tok.substring(4, eq)); if (i != null) out.add(new Effect.Salience(i, v * i.baseSalience())); }
            else if (tok.startsWith("rally=")) out.add(new Effect.Rally(v));
        }
        return out;
    }

    /** Resolute Desk's issue codes, as this game's issues. */
    public static Issue issue(String code) {
        return switch (code) {
            case "ECON", "JOBS" -> Issue.ECONOMY;
            case "PRICES" -> Issue.COST_OF_LIVING;
            case "HEALTH" -> Issue.HEALTHCARE;
            case "IMMIG" -> Issue.IMMIGRATION;
            case "CRIME" -> Issue.CRIME;
            case "ENERGY" -> Issue.CLIMATE_ENERGY;
            case "TECH" -> Issue.AI_TECH;
            case "EDU" -> Issue.EDUCATION;
            case "ETHICS" -> Issue.DEMOCRACY;
            case "RIGHTS" -> Issue.ABORTION;
            case "FOREIGN", "SECURITY", "TRADE" -> Issue.CHINA;
            default -> null;
        };
    }
}
