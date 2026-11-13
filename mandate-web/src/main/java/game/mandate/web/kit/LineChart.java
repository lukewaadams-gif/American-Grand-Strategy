package game.mandate.web.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * A line chart in SVG, drawn at its real width (so labels never shrink below 12 px): series, a padded axis with
 * round ticks, an optional reference line, a legend, end labels, and a hover read-out per point. Its accessible
 * name is a sentence ("Approval from 51% to 48%").
 */
public class LineChart extends El {
    public record Series(String name, Color color, List<Double> values) {}

    final List<String> xLabels;
    final DoubleFunction<String> format;
    final double minSpan;
    final int height;
    final List<Series> series = new ArrayList<>();
    Double reference;
    String referenceLabel;
    String empty = "No data yet.";
    final El svg;
    int width;

    public LineChart(List<String> xLabels, DoubleFunction<String> format, double minSpan, int preferredHeight) {
        super("figure");
        cls("chart");
        this.xLabels = xLabels;
        this.format = format;
        this.minSpan = minSpan;
        this.height = preferredHeight;
        svg = El.svg("svg");
        svg.attr("role", "img").attr("height", String.valueOf(preferredHeight)).attr("width", "100%");
        add(svg);
        Dom.observeWidth(node, w -> { width = (int) w; draw(); });
    }

    public LineChart add(String name, Color color, List<Double> values) { series.add(new Series(name, color, values)); describe(); return this; }
    public LineChart reference(double v, String label) { reference = v; referenceLabel = label; return this; }
    public LineChart emptyText(String s) { empty = s; describe(); return this; }

    void describe() { svg.attr("aria-label", "Chart: " + summary()); }

    int count() { return series.stream().mapToInt(s -> s.values.size()).max().orElse(0); }

    /** {lo, hi, step}: a padded, rounded axis range (the desktop's rule). */
    double[] axis() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Series s : series) for (double v : s.values) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        if (reference != null) { lo = Math.min(lo, reference); hi = Math.max(hi, reference); }
        if (lo > hi) { lo = 0; hi = 1; }
        double span = Math.max(minSpan, hi - lo), mid = (lo + hi) / 2;
        lo = mid - span * 0.6;
        hi = mid + span * 0.6;
        double raw = (hi - lo) / 4, mag = Math.pow(10, Math.floor(Math.log10(raw))), step = mag;
        for (double m : new double[]{1, 2, 2.5, 5, 10}) if (m * mag >= raw) { step = m * mag; break; }
        return new double[]{Math.floor(lo / step) * step, Math.ceil(hi / step) * step, step};
    }

    void draw() {
        Theme t = Theme.get();
        svg.clear();
        int w = Math.max(120, width), h = height, fs = t.px(12);
        svg.attr("viewBox", "0 0 " + w + " " + h).attr("width", String.valueOf(w));
        int n = count();
        // legend
        int lx = 0;
        for (Series s : series) {
            svg.add(rect(lx, t.px(3), t.u(3), t.px(4), s.color.css()));
            svg.add(text(lx + t.u(4), fs, s.name, t.muted, fs, "start", false));
            lx += t.u(4) + s.name.length() * fs * 0.6 + t.u(4);
        }
        if (n < 2) {
            svg.add(text(w / 2.0, h / 2.0, empty, t.faint, t.px(13), "middle", false));
            return;
        }
        double[] ax = axis();
        int x0 = t.u(12), x1 = w - t.u(16), y0 = t.u(7), y1 = h - t.u(6);
        double step = (x1 - x0) / (double) (n - 1);
        java.util.function.DoubleUnaryOperator Y = v -> y1 - (v - ax[0]) / (ax[1] - ax[0]) * (y1 - y0);
        for (double v = ax[0]; v <= ax[1] + 1e-9; v += ax[2]) {
            double y = Y.applyAsDouble(v);
            svg.add(line(x0, y, x1, y, Theme.alpha(t.border, 0.7).css(), 1, null));
            svg.add(text(x0 - t.u(2), y + fs * 0.35, format.apply(v), t.faint, fs, "end", false));
        }
        if (reference != null) {
            double y = Y.applyAsDouble(reference);
            svg.add(line(x0, y, x1, y, t.borderStrong.css(), 1, "4 4"));
            if (referenceLabel != null) svg.add(text(x0 + t.u(1), y - t.px(3), referenceLabel, t.faint, fs, "start", false));
        }
        for (int i : new int[]{0, (n - 1) / 2, n - 1}) {
            if (i >= xLabels.size()) continue;
            double x = x0 + i * step;
            svg.add(text(x, h - t.px(2), xLabels.get(i), t.faint, fs, i == 0 ? "start" : i == n - 1 ? "end" : "middle", false));
        }
        List<double[]> ends = new ArrayList<>();
        for (int k = 0; k < series.size(); k++) {
            Series s = series.get(k);
            if (s.values.isEmpty()) continue;
            StringBuilder d = new StringBuilder();
            for (int i = 0; i < s.values.size(); i++) {
                double x = x0 + i * step, y = Y.applyAsDouble(s.values.get(i));
                d.append(i == 0 ? "M" : "L").append(r1(x)).append(' ').append(r1(y)).append(' ');
            }
            svg.add(El.svg("path").attr("d", d.toString()).attr("fill", "none").attr("stroke", s.color.css())
                    .attr("stroke-width", String.valueOf(2.25 * t.scale)).attr("stroke-linecap", "round").attr("stroke-linejoin", "round"));
            int last = s.values.size() - 1;
            double ex = x0 + last * step, ey = Y.applyAsDouble(s.values.get(last));
            svg.add(El.svg("circle").attr("cx", r1(ex)).attr("cy", r1(ey)).attr("r", String.valueOf(t.px(3))).attr("fill", s.color.css()));
            ends.add(new double[]{ex, ey, k});
        }
        ends.sort((a, b) -> Double.compare(a[1], b[1]));
        double prev = -1e9;
        for (double[] e : ends) {
            Series s = series.get((int) e[2]);
            double y = Math.max(e[1] + fs * 0.35, prev + fs * 1.3);
            svg.add(text(e[0] + t.u(2), y, format.apply(s.values.get(s.values.size() - 1)), s.color, fs, "start", true));
            prev = y;
        }
        // hover read-out: one invisible column per point, with its values as a tooltip
        for (int i = 0; i < n; i++) {
            StringBuilder tip = new StringBuilder(i < xLabels.size() ? xLabels.get(i) : "");
            for (Series s : series) if (i < s.values.size()) tip.append("\n").append(s.name).append(": ").append(format.apply(s.values.get(i)));
            El col = rect(x0 + (i - 0.5) * step, y0, step, y1 - y0, "transparent");
            col.add(El.svg("title").text(tip.toString()));
            svg.add(col.cls("chart-hit"));
        }
    }

    static El rect(double x, double y, double w, double h, String fill) {
        return El.svg("rect").attr("x", r1(x)).attr("y", r1(y)).attr("width", r1(Math.max(0, w))).attr("height", r1(Math.max(0, h))).attr("fill", fill).attr("rx", "1");
    }

    static El line(double x0, double y0, double x1, double y1, String stroke, double width, String dash) {
        El l = El.svg("line").attr("x1", r1(x0)).attr("y1", r1(y0)).attr("x2", r1(x1)).attr("y2", r1(y1)).attr("stroke", stroke).attr("stroke-width", String.valueOf(width));
        if (dash != null) l.attr("stroke-dasharray", dash);
        return l;
    }

    static El text(double x, double y, String s, Color c, int size, String anchor, boolean bold) {
        return El.svg("text").attr("x", r1(x)).attr("y", r1(y)).attr("fill", c.css()).attr("font-size", String.valueOf(size))
                .attr("text-anchor", anchor).attr("font-weight", bold ? "700" : "400").attr("font-family", "Inter, system-ui, sans-serif").text(s);
    }

    static String r1(double v) { return String.valueOf(Math.round(v * 10) / 10.0); }

    /** What the chart shows, in words: each series' first and latest value. */
    public String summary() {
        StringBuilder b = new StringBuilder();
        for (Series s : series) {
            if (s.values.isEmpty()) continue;
            if (b.length() > 0) b.append("; ");
            b.append(s.name).append(" from ").append(format.apply(s.values.get(0))).append(" to ").append(format.apply(s.values.get(s.values.size() - 1)));
        }
        return b.length() == 0 ? empty : b.toString();
    }
}
