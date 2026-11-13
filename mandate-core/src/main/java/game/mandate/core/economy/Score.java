package game.mandate.core.economy;

/**
 * What a policy would do, as the budget office scores it (docs/ECONOMY_MODEL.md, "The ranges, and the draw"): the
 * difference between the economy with it and without it, run forward from today with noise and random shocks off.
 * Growth, unemployment, jobs, inflation and output are at one, three and ten years; prices and sectors at five.
 *
 * @param growth       real GDP growth over the year, points
 * @param unemployment the unemployment rate, points
 * @param jobs         employment, millions
 * @param inflation    consumer prices over the year, points
 * @param output       the level of real GDP, %
 * @param deficit      the deficit, % of GDP, on average over ten years
 * @param direct       the ten-year cost of the policy's own money, $ billions (+ adds to the deficit), as the budget office
 *                     scores it: without interest or the economy's response
 * @param cost         the ten-year change in the deficit, $ billions, with interest on the debt and the economy's response
 * @param debt         debt held by the public after ten years, points of GDP
 * @param tenYear      the 10-year yield after five years, points
 * @param prices       each price part's level after five years, %, in the order of {@link Part}
 * @param sectors      after five years: housing starts (%), manufacturing (index points), AI investment (%), farm income,
 *                     energy production and tourism (index points)
 */
public record Score(double[] growth, double[] unemployment, double[] jobs, double[] inflation, double[] output,
                    double deficit, double direct, double cost, double debt, double tenYear, double[] prices, double[] sectors) {

    public static final int[] YEARS = {1, 3, 10};
    public static final String[] SECTORS = {"housing", "manufacturing", "ai", "farm", "energy", "tourism"};

    /** "will cost $1.2 trillion over ten years", "will save $300 billion over ten years", "costs almost nothing". */
    public static String costPhrase(double direct) {
        if (Math.abs(direct) < 5) return "costs almost nothing";
        return (direct > 0 ? "will cost " : "will save ") + money(Math.abs(direct)) + " over ten years";
    }

    /** $ billions as "$40 billion" or "$1.2 trillion". */
    public static String money(double bn) {
        if (bn >= 995) return "$" + game.mandate.core.data.Fmt.format("%.1f", bn / 1000) + " trillion";
        return "$" + Math.round(bn >= 100 ? Math.round(bn / 10) * 10 : bn) + " billion";
    }

    /**
     * The score in one plain sentence: what it does to output (over ten years, or by year three if it fades), to jobs
     * if that is notable, to the price people notice most, and its cost.
     */
    public static String reading(Range r) {
        Score c = r.central();
        StringBuilder b = new StringBuilder();
        double o3 = c.output[1], o10 = c.output[2];
        boolean fades = Math.abs(o3) > Math.abs(o10) * 1.5;
        double o = fades ? o3 : o10;
        String when = fades ? "by year three" : "after ten years";
        if (Math.abs(o) < 0.05) b.append("Little effect on the size of the economy");
        else b.append(o > 0 ? "Adds about " : "Takes about ").append(pct(Math.abs(o))).append(" ").append(o > 0 ? "to" : "from").append(" output ").append(when);
        double jobs = c.jobs[2];
        if (Math.abs(jobs) >= 0.1) b.append(", with about ").append(game.mandate.core.data.Fmt.format("%.1f", Math.abs(jobs))).append(" million ")
                .append(jobs > 0 ? "more" : "fewer").append(" jobs");
        int top = -1;
        for (int i = 0; i < c.prices.length; i++) if (Math.abs(c.prices[i]) >= 0.3 && (top < 0 || Math.abs(c.prices[i]) > Math.abs(c.prices[top]))) top = i;
        if (top >= 0) b.append("; ").append(c.prices[top] < 0 ? "lowers " : "raises ").append(PRICE_WORDS[top]).append(" by about ")
                .append(pct(Math.abs(c.prices[top]))).append(" over five years");
        b.append("; it ").append(costPhrase(c.direct)).append(".");
        return b.toString();
    }

    private static final String[] PRICE_WORDS = {"rents", "food prices", "energy bills", "medical prices", "the prices of goods", "the prices of services"};

    private static String pct(double v) { return game.mandate.core.data.Fmt.format(v >= 1 ? "%.1f" : "%.2f", v) + "%"; }

    /** The central estimate and the scores at the two ends of the law's range. */
    public record Range(Score central, Score low, Score high) {
        /** The smaller of the two ends for a measure read from a score. */
        public double min(java.util.function.ToDoubleFunction<Score> f) { return Math.min(f.applyAsDouble(low), f.applyAsDouble(high)); }
        public double max(java.util.function.ToDoubleFunction<Score> f) { return Math.max(f.applyAsDouble(low), f.applyAsDouble(high)); }
    }
}
