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

    /** The central estimate and the scores at the two ends of the law's range. */
    public record Range(Score central, Score low, Score high) {
        /** The smaller of the two ends for a measure read from a score. */
        public double min(java.util.function.ToDoubleFunction<Score> f) { return Math.min(f.applyAsDouble(low), f.applyAsDouble(high)); }
        public double max(java.util.function.ToDoubleFunction<Score> f) { return Math.max(f.applyAsDouble(low), f.applyAsDouble(high)); }
    }
}
