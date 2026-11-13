package game.mandate.web.kit;

import game.mandate.core.data.Fmt;

import java.util.List;

/** Small data displays. Each states its value in text as well as shape, and names itself to assistive technology. */
public final class Widgets {
    private Widgets() {}

    /** A headline number: an uppercase label, the value, and a coloured line under it. */
    public static final class Kpi extends El {
        public Kpi(String label, String value, String sub, Color subColor, String explain) {
            super("div");
            cls("kpi");
            add(El.div("kpi-label").text(label), El.div("kpi-value").text(value));
            if (sub != null) add(El.div("kpi-sub").text(sub).css("color", (subColor == null ? Theme.get().faint : subColor).css()));
            if (explain != null) tip(explain);
            role("group");
            label(label + ": " + value + (sub == null ? "" : ", " + sub));
        }
    }

    /** A horizontal bar for a 0..max reading, with the label left, the value right, and an optional threshold tick. */
    public static final class Meter extends El {
        final double max;
        final El track;

        public Meter(String label, double value, double max, String valueText, Color color, String explain) {
            super("div");
            this.max = max;
            cls("meter");
            add(Layouts.side(0, null, El.span("meter-label").text(label), El.span("meter-value").text(valueText)));
            track = El.div("meter-track");
            track.add(El.div("meter-fill").css("width", pct(value / max)).css("background", color.css()));
            add(track);
            role("meter");
            attr("aria-valuemin", "0").attr("aria-valuemax", num(max)).attr("aria-valuenow", num(value)).label(label + ": " + valueText);
            if (explain != null) tip(explain);
        }

        /** A tick at a threshold (a revolt line, the votes needed). */
        public Meter marker(double v) { track.add(El.div("meter-mark").css("left", pct(v / max))); return this; }
    }

    /** Attention points: filled for each one left this turn. */
    public static final class Pips extends El {
        public Pips(int filled, int total, String explain) {
            super("div");
            cls("pips");
            for (int i = 0; i < total; i++) add(El.span(i < filled ? "pip on" : "pip"));
            role("img").label(filled + " of " + total + " attention points left");
            if (explain != null) tip(explain);
        }
    }

    /** A status pill (CONFIRMED, CRISIS, D+2.1). */
    public static final class Tag extends El {
        public Tag(String text, Color color) {
            super("span");
            cls("tag");
            text(text);
            css("--tone", color.css()).css("--pill", Theme.get().pillFill(color).css());
            css("align-self", "flex-start");
        }
    }

    /** A -100..100 reading from a centre line (relations). */
    public static final class Diverging extends El {
        public Diverging(String label, double value, String word, String explain) {
            super("div");
            cls("diverging");
            Theme t = Theme.get();
            double len = Math.min(1, Math.abs(value) / 100.0) * 50;
            El bar = El.div("div-track").add(El.div("div-mid"),
                    El.div("div-fill").css("background", (value >= 0 ? t.info : t.bad).css())
                            .css(value >= 0 ? "left" : "right", "50%").css("width", Math.max(0.6, len) + "%"));
            add(El.span("div-label").text(label), bar, El.span("div-word").text(word).css("color", (value >= 0 ? t.muted : t.bad).css()));
            role("img").label(label + ": " + word + Fmt.format(" (%+.0f)", value));
            if (explain != null) tip(explain);
        }
    }

    /** A path of named stages with the current one marked: a bill through Congress, a crisis up its ladder. */
    public static final class Stages extends El {
        public Stages(List<String> stages, int current, boolean failed, Color doneColor) {
            super("ol");
            cls("stages");
            css("--done", doneColor.css());
            for (int i = 0; i < stages.size(); i++) {
                El li = new El("li").cls(i < current ? "done" : i == current ? (failed ? "here failed" : "here") : "todo");
                li.add(El.span("stage-dot").attr("aria-hidden", "true"), El.span("stage-name").text(stages.get(i)));
                if (i == current) li.attr("aria-current", "step");
                add(li);
            }
            label("Stage " + (current + 1) + " of " + stages.size() + ": " + (current >= 0 && current < stages.size() ? stages.get(current) : "not started"));
            tip(String.join(" → ", stages));
        }
    }

    /** Battleground 2028's bar: one side's electoral votes, the toss-ups, the other side's, and the 270 line. */
    public static final class RaceBar extends El {
        public RaceBar(int dem, int toss, int rep, String demLabel, String repLabel) {
            super("div");
            cls("racebar");
            Theme t = Theme.get();
            El bar = El.div("race-track");
            bar.add(El.div("race-d").css("width", pct(dem / 538.0)).css("color", t.textOn(t.dem).css()).text(dem >= 40 ? String.valueOf(dem) : ""),
                    El.div("race-t").css("width", pct(toss / 538.0)),
                    El.div("race-r").css("flex", "1 1 auto").css("color", t.textOn(t.rep).css()).text(rep >= 40 ? String.valueOf(rep) : ""),
                    El.div("race-270").css("left", pct(270 / 538.0)));
            El labels = El.div("race-labels");
            labels.add(El.span("").text(demLabel), El.span("race-mid").text("270 to win"), El.span("").text(repLabel));
            add(bar, labels);
            role("img").label(demLabel + " " + dem + ", toss-up " + toss + ", " + repLabel + " " + rep + "; 270 to win");
            tip("Electoral votes by state poll: solid or leaning (more than 2 points), and toss-ups within 2 points. 270 wins.");
        }
    }

    static String pct(double f) { return (Math.round(Math.max(0, Math.min(1, f)) * 10000) / 100.0) + "%"; }

    static String num(double v) { return String.valueOf(Math.round(v * 100) / 100.0); }
}
