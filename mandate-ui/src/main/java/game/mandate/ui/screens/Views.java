package game.mandate.ui.screens;

import game.mandate.core.campaign.PollPoint;
import game.mandate.core.electorate.ElectionResult;
import game.mandate.core.electorate.UsState;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.engine.IndicatorPoint;
import game.mandate.core.opening.HundredDays;
import game.mandate.core.opening.Types.CrisisOutcome;
import game.mandate.core.state.Party;
import game.mandate.core.time.Phase;
import game.mandate.ui.App;
import game.mandate.ui.Notable;
import game.mandate.ui.kit.Btn;
import game.mandate.ui.kit.Card;
import game.mandate.ui.kit.Layouts;
import game.mandate.ui.kit.LineChart;
import game.mandate.ui.kit.Text;
import game.mandate.ui.kit.Theme;
import game.mandate.ui.kit.TileMap;
import game.mandate.ui.kit.Widgets;

import javax.swing.JComponent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/** Views several screens share: the poll map, the race to 270, Congress, trends, the security status line. */
public final class Views {
    private Views() {}

    /** The map's data in this phase: the week's state polls, the 2028 result once it is in, or the 2024 baseline. */
    static Map<String, Double> mapMargins(GameEngine g) {
        Map<String, Double> m = new LinkedHashMap<>();
        if (Parts.campaign(g)) m.putAll(g.statePolls());
        else if (g.result2028().isPresent()) m.putAll(g.result2028().get().stateMargins());
        else for (UsState s : g.states().all()) m.put(s.code(), s.baselineMargin());
        return m;
    }

    static String mapWhat(GameEngine g) {
        return Parts.campaign(g) ? "State polls this week" : g.result2028().isPresent() ? "The 2028 result" : "The 2024 result";
    }

    public static TileMap map(GameEngine g, int height, String selected, Consumer<String> onSelect) {
        Map<String, Double> m = mapMargins(g);
        Function<String, String> tip = code -> {
            UsState s = g.states().get(code);
            String what = Parts.campaign(g) ? "poll " : "result ";
            return s.name() + ": " + what + Parts.margin(m.getOrDefault(code, 0.0)) + ", " + s.electoralVotes() + " electoral votes";
        };
        TileMap map = new TileMap(g.states().all(), m, code -> Parts.margin(m.getOrDefault(code, 0.0)), tip, "Republican", "Democratic", height);
        if (selected != null) map.selected(selected);
        if (onSelect != null) map.onSelect(onSelect);
        return map;
    }

    /** Electoral votes by state poll: {D, toss-up (within 2), R}. */
    static int[] evSplit(GameEngine g, Map<String, Double> m) {
        int d = 0, toss = 0, r = 0;
        for (UsState s : g.states().all()) {
            double v = m.getOrDefault(s.code(), 0.0);
            if (Math.abs(v) <= 2) toss += s.electoralVotes(); else if (v > 0) d += s.electoralVotes(); else r += s.electoralVotes();
        }
        return new int[]{d, toss, r};
    }

    static JComponent raceBar(GameEngine g, Map<String, Double> m) {
        int[] ev = evSplit(g, m);
        String dName = g.side(Party.DEMOCRAT).candidate().name(), rName = g.side(Party.REPUBLICAN).candidate().name();
        return new Widgets.RaceBar(ev[0], ev[1], ev[2], dName + " (D)", rName + " (R)");
    }

    static Card congressCard(GameEngine g) {
        Theme t = Theme.get();
        var c = g.congress();
        Card card = new Card("Congress");
        card.with(new Widgets.Meter("House: " + c.house(Party.DEMOCRAT) + " D, " + c.house(Party.REPUBLICAN) + " R", c.house(Party.DEMOCRAT), 435,
                "218 for a majority", t.dem, "Democratic seats out of 435; the bar's tick is the 218-seat majority").marker(218));
        card.with(new Widgets.Meter("Senate: " + c.senate(Party.DEMOCRAT) + " D, " + c.senate(Party.REPUBLICAN) + " R", c.senate(Party.DEMOCRAT), 100,
                "60 to end a filibuster", t.dem, "Democratic seats out of 100; the tick is the 60 votes that end a filibuster").marker(60));
        Party me = g.player().party();
        card.with(Text.muted(c.unified(me) ? "Your party holds both chambers." : "Divided government: you will need the other party's votes."));
        return card;
    }

    /** The approval trend while governing (one point per turn), or the national poll during a campaign. */
    static LineChart trend(GameEngine g, int height) {
        Theme t = Theme.get();
        if (Parts.campaign(g)) {
            List<PollPoint> ps = g.polls();
            List<String> x = ps.stream().map(p -> Notable.date(p.date()).replace(", 2028", "").replace(", 2032", "")).toList();
            return new LineChart(x, v -> Parts.signed(v, "%.1f"), 4, height).add("National poll (you)", t.accent, ps.stream().map(PollPoint::national).toList())
                    .reference(0, "Tied").emptyText("The first poll arrives after the first week.");
        }
        List<IndicatorPoint> ind = g.indicators();
        List<String> x = ind.stream().map(p -> Notable.date(p.date()).replaceAll(", \\d{4}", "")).toList();
        return new LineChart(x, v -> String.format("%.0f%%", v), 6, height).add("Approval", t.accent, ind.stream().map(IndicatorPoint::approval).toList())
                .reference(50, "50%").emptyText("Approval is charted from Inauguration Day.");
    }

    /** First Island Chain's status line: one word for the state of the world, in its colour. */
    static String[] status(GameEngine g) {
        if (g.phase() == Phase.FIRST_100_DAYS) {
            HundredDays h = g.hundredDays().orElseThrow();
            return switch (h.crisis()) {
                case ACTIVE -> new String[]{h.escalation() >= 2 ? "DANGER" : "CRISIS", "A crisis in the " + h.crisisKind().label + ": " + rung(h)};
                case CLASH -> new String[]{"CLASH", "An armed clash in the " + h.crisisKind().label};
                case NONE -> new String[]{"WATCH", "No crisis yet; something is brewing in the world's flashpoints"};
                case BACKED_DOWN, DEAL -> new String[]{"CALM", "The " + h.crisisKind().label + " crisis is over"};
                case STALEMATE -> new String[]{"WATCH", "A tense stalemate in the " + h.crisisKind().label};
            };
        }
        double taiwan = g.diplomacy().tension(game.mandate.core.diplomacy.HotSpot.TAIWAN_STRAIT);
        if (g.diplomacy().rung() != null && !g.diplomacy().warOver()) return new String[]{"CRISIS", "The Taiwan ladder: " + g.diplomacy().rung().name().toLowerCase()};
        return taiwan > 55 ? new String[]{"WATCH", String.format("Taiwan Strait tension %.0f", taiwan)} : new String[]{"CALM", "No active crisis"};
    }

    static String rung(HundredDays h) {
        String[] r = h.crisisKind().rungs;
        return r[Math.min(h.escalation(), r.length - 1)];
    }

    static Color statusColor(String word) {
        Theme t = Theme.get();
        return switch (word) { case "CALM" -> t.good; case "WATCH" -> t.warn; default -> t.bad; };
    }

    static Card statusCard(App app, GameEngine g) {
        Theme t = Theme.get();
        String[] s = status(g);
        Card c = new Card("National security");
        c.with(new Text(s[0], t.font(Theme.Face.MONO_BOLD, Theme.Size.KPI), statusColor(s[0])), Text.muted(s[1]));
        c.with(new Widgets.Meter("Deterrence", g.diplomacy().deterrence(), 100, String.format("%.0f", g.diplomacy().deterrence()), t.info,
                game.mandate.ui.Glossary.of("Deterrence")));
        c.with(Btn.quiet("Open Security", e -> app.open(App.ScreenId.SECURITY)).tip("Crises, deterrence and relations (Ctrl+6)"));
        return c;
    }

    static List<String> closest(GameEngine g, Map<String, Double> m, int n) {
        List<UsState> ss = new ArrayList<>(g.states().all());
        ss.sort((a, b) -> Double.compare(Math.abs(m.getOrDefault(a.code(), 99.0)), Math.abs(m.getOrDefault(b.code(), 99.0))));
        return ss.subList(0, Math.min(n, ss.size())).stream().map(UsState::code).toList();
    }

    static String resultLine(ElectionResult r) {
        return String.format("Democrats %d, Republicans %d. Popular vote: %s by %.1f. Tipping point: %s.", r.demElectoralVotes(), r.repElectoralVotes(),
                r.popularMargin() >= 0 ? "Democrats" : "Republicans", Math.abs(r.popularMargin()), r.tippingPoint());
    }

    static boolean crisisActive(GameEngine g) {
        return g.phase() == Phase.FIRST_100_DAYS && g.hundredDays().map(h -> h.crisis() == CrisisOutcome.ACTIVE).orElse(false);
    }

    static JComponent spacerText(String s) { return Layouts.stack(0, Text.faint(s)); }
}
