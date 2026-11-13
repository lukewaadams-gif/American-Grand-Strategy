package game.mandate.core.economy;

import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * National economic conditions (docs/ECONOMY_MODEL.md). Owned by {@link EconomyEngine}: other systems read these values
 * and may only change them through transitions the engine accepts (setters are package-private on purpose).
 *
 * <p>Levels are kept as logs against the first week (output, potential, capital, productivity, each price part), and the
 * last 53 weeks of output, the gap and each price part are kept in rings, so growth and inflation are measured over the
 * year as the statistical agencies measure them.
 */
public final class EconomyState {
    static final int RING = 53;

    int week;
    // supply
    double tfp, capital, population, participation, nairu, structural, immigration, tfpPolicy, userCost, investBase, real10Neutral;
    double potential, potentialGrowth;
    // demand and output
    double outputGap, growth, realRateLag, fiscal;
    int ring;
    final double[] logY = new double[RING], gaps = new double[RING];
    /** What tariffs have added to consumer prices (log), week by week: the Fed looks through it. */
    final double[] tariffPrices = new double[RING];
    // jobs
    double unemployment, payrolls, uAtLastJobs;
    // prices
    final double[] trend = new double[Part.COUNT], yoy = new double[Part.COUNT], partPolicy = new double[Part.COUNT];
    final double[][] prices = new double[Part.COUNT][RING];
    double inflation, core, expected, cpiLog, priceLevel, utilities, gasLog0, tariffAbsorbed;
    // the Fed and the markets
    double fedRate, credibility, tenYear, tenNews, oil, oilBase, oilDev, gas, stocks, stocks0, dollar, sentiment, sentiment0, tariff;
    double reportedUnemployment, reportedInflation, reportedGrowth;
    double sprRelief;
    boolean fedPressured;
    double startMisery;
    // shocks
    double demand, financial, financialPrev, supply, gapPrev;
    int calm;
    /** The surprise of the last week stepped, for the news that follows it within the same turn; not saved. */
    transient EconomyEngine.Shock lastShock;
    final List<Push> pushes = new ArrayList<>();
    // sectors
    double housing, housingSmooth, housingPolicy, manufacturing, aiInvest, farm, energyIndex, tourism;
    // what laws add: to gas ($), to the sectors' targets, and to the price parts' levels this week (%)
    double gasPolicy, manufacturingPolicy, farmPolicy, energyPolicy, tourismPolicy;
    final double[] partPush = new double[Part.COUNT];
    /** The policy steps in force, in the order they passed. */
    final List<Law> laws = new ArrayList<>();
    /** Read from a save made before the structural model: {@link EconomyEngine#upgrade} rebuilds what it lacks. */
    transient boolean legacy;

    EconomyState() {}

    public int week() { return week; }
    public double outputGap() { return outputGap; }
    /** Real GDP over the past year, %. */
    public double growth() { return growth; }
    /** Potential output's growth, % a year. */
    public double potentialGrowth() { return potentialGrowth; }
    public double unemployment() { return unemployment; }
    public double naturalUnemployment() { return nairu; }
    public double participation() { return participation; }
    /** Net immigration, millions a year. */
    public double immigration() { return immigration; }
    /** The last monthly jobs report: jobs added, thousands. */
    public double payrolls() { return payrolls; }
    /** Consumer prices over the past year, %. */
    public double inflation() { return inflation; }
    /** Prices without food and energy over the past year, %. */
    public double core() { return core; }
    public double part(Part p) { return yoy[p.ordinal()]; }
    public double expectedInflation() { return expected; }
    /** What tariffs added to consumer prices over the past year, points. */
    public double tariffInflation() { return (tariffPrices[ring] - tariffPrices[(ring + 1) % RING]) * 100; }
    /** Consumer prices against 2025 (1 = 2025's). */
    public double priceLevel() { return priceLevel; }
    public double fedRate() { return fedRate; }
    public double credibility() { return credibility; }
    public double oil() { return oil; }
    public double oilBase() { return oilBase; }
    public double gas() { return gas; }
    public double tenYear() { return tenYear; }
    public double mortgageRate(EconomyParams p) { return tenYear + p.mortgageSpread; }
    /** The stock market, an index like the S&amp;P 500. */
    public double stocks() { return stocks; }
    /** The trade-weighted dollar, 100 at the start. */
    public double dollar() { return dollar; }
    /** The average tariff on imports, %. */
    public double tariff() { return tariff; }
    /** Consumer sentiment, Resolute Desk's index: about 80 in ordinary times. */
    public double sentiment() { return sentiment; }
    /** The consumer mood other systems read; the same as {@link #sentiment()}. */
    public double confidence() { return sentiment; }
    public double reportedUnemployment() { return reportedUnemployment; }
    public double reportedInflation() { return reportedInflation; }
    public double reportedGrowth() { return reportedGrowth; }
    public boolean fedPressured() { return fedPressured; }
    public double startMisery() { return startMisery; }
    /** The "misery index" voters feel; used by the epilogue. */
    public double misery() { return unemployment + inflation; }
    public int activeShocks() { return pushes.size() + (Math.abs(demand) > 0.3 ? 1 : 0) + (Math.abs(financial) > 0.3 ? 1 : 0) + (Math.abs(supply) > 0.3 ? 1 : 0); }
    /** Housing starts, millions a year. */
    public double housing() { return housing; }
    /** Manufacturing, an index where 50 is no change. */
    public double manufacturing() { return manufacturing; }
    public double aiInvestment() { return aiInvest; }
    public double farmIncome() { return farm; }
    public double energyProduction() { return energyIndex; }
    public double tourism() { return tourism; }
    /** The policy steps in force, in the order they passed. */
    public List<Law> laws() { return java.util.Collections.unmodifiableList(laws); }
    /** The demand that fiscal policy adds to the gap's target this week, points of GDP. */
    public double fiscalPush() { return fiscal; }

    /** A push to the gap and to prices spread over some weeks: an event's effect on the economy, or a recession's onset. */
    static final class Push {
        final double gapPerWeek, inflation;
        int weeksLeft;

        Push(double gapPerWeek, double inflation, int weeks) { this.gapPerWeek = gapPerWeek; this.inflation = inflation; this.weeksLeft = weeks; }
    }

    // ---- saves ---------------------------------------------------------------------------------

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", 2); m.put("week", week);
        m.put("supply", nums(tfp, capital, population, participation, nairu, structural, immigration, tfpPolicy, userCost, investBase, real10Neutral,
                potential, potentialGrowth));
        m.put("outputGap", outputGap); m.put("growth", growth); m.put("realRateLag", realRateLag); m.put("fiscal", fiscal);
        m.put("ring", ring); m.put("logY", nums(logY)); m.put("gaps", nums(gaps)); m.put("tariffPrices", nums(tariffPrices));
        m.put("unemployment", unemployment); m.put("payrolls", payrolls); m.put("uAtLastJobs", uAtLastJobs);
        m.put("trend", nums(trend)); m.put("yoy", nums(yoy)); m.put("partPolicy", nums(partPolicy));
        List<Object> pr = new ArrayList<>();
        for (double[] p : prices) pr.add(nums(p));
        m.put("parts", pr);
        m.put("prices", nums(inflation, core, expected, cpiLog, priceLevel, utilities, gasLog0, tariffAbsorbed));
        m.put("fedRate", fedRate); m.put("credibility", credibility); m.put("tenYear", tenYear); m.put("tenNews", tenNews);
        m.put("markets", nums(oil, oilBase, oilDev, gas, stocks, stocks0, dollar, sentiment, sentiment0, tariff));
        m.put("reportedUnemployment", reportedUnemployment); m.put("reportedInflation", reportedInflation); m.put("reportedGrowth", reportedGrowth);
        m.put("sprRelief", sprRelief); m.put("fedPressured", fedPressured); m.put("startMisery", startMisery);
        m.put("shocks", nums(demand, financial, financialPrev, supply, gapPrev, calm));
        List<Object> ps = new ArrayList<>();
        for (Push p : pushes) ps.add(nums(p.gapPerWeek, p.inflation, p.weeksLeft));
        m.put("pushes", ps);
        m.put("sectors", nums(housing, housingSmooth, housingPolicy, manufacturing, aiInvest, farm, energyIndex, tourism));
        m.put("policy", nums(gasPolicy, manufacturingPolicy, farmPolicy, energyPolicy, tourismPolicy));
        List<Object> ls = new ArrayList<>();
        for (Law l : laws) ls.add(l.toJson());
        m.put("laws", ls);
        return m;
    }

    public static EconomyState fromJson(Map<String, Object> m) {
        EconomyState s = new EconomyState();
        if (!m.containsKey("version")) return legacy(m);
        s.week = Json.integer(m.get("week"));
        double[] v = read(m.get("supply"));
        s.tfp = v[0]; s.capital = v[1]; s.population = v[2]; s.participation = v[3]; s.nairu = v[4]; s.structural = v[5]; s.immigration = v[6];
        s.tfpPolicy = v[7]; s.userCost = v[8]; s.investBase = v[9]; s.real10Neutral = v[10]; s.potential = v[11]; s.potentialGrowth = v[12];
        s.outputGap = Json.num(m.get("outputGap")); s.growth = Json.num(m.get("growth")); s.realRateLag = Json.num(m.get("realRateLag"));
        s.fiscal = Json.num(m.get("fiscal"));
        s.ring = Json.integer(m.get("ring")); copy(read(m.get("logY")), s.logY); copy(read(m.get("gaps")), s.gaps); copy(read(m.get("tariffPrices")), s.tariffPrices);
        s.unemployment = Json.num(m.get("unemployment")); s.payrolls = Json.num(m.get("payrolls")); s.uAtLastJobs = Json.num(m.get("uAtLastJobs"));
        copy(read(m.get("trend")), s.trend); copy(read(m.get("yoy")), s.yoy); copy(read(m.get("partPolicy")), s.partPolicy);
        List<Object> pr = Json.array(m.get("parts"));
        for (int i = 0; i < Part.COUNT; i++) copy(read(pr.get(i)), s.prices[i]);
        v = read(m.get("prices"));
        s.inflation = v[0]; s.core = v[1]; s.expected = v[2]; s.cpiLog = v[3]; s.priceLevel = v[4]; s.utilities = v[5]; s.gasLog0 = v[6]; s.tariffAbsorbed = v[7];
        s.fedRate = Json.num(m.get("fedRate")); s.credibility = Json.num(m.get("credibility")); s.tenYear = Json.num(m.get("tenYear"));
        s.tenNews = Json.num(m.get("tenNews"));
        v = read(m.get("markets"));
        s.oil = v[0]; s.oilBase = v[1]; s.oilDev = v[2]; s.gas = v[3]; s.stocks = v[4]; s.stocks0 = v[5]; s.dollar = v[6]; s.sentiment = v[7];
        s.sentiment0 = v[8]; s.tariff = v[9];
        s.reportedUnemployment = Json.num(m.get("reportedUnemployment")); s.reportedInflation = Json.num(m.get("reportedInflation"));
        s.reportedGrowth = Json.num(m.get("reportedGrowth"));
        s.sprRelief = Json.num(m.get("sprRelief")); s.fedPressured = (Boolean) m.get("fedPressured"); s.startMisery = Json.num(m.get("startMisery"));
        v = read(m.get("shocks"));
        s.demand = v[0]; s.financial = v[1]; s.financialPrev = v[2]; s.supply = v[3]; s.gapPrev = v[4]; s.calm = (int) v[5];
        for (Object o : Json.array(m.get("pushes"))) {
            double[] p = read(o);
            s.pushes.add(new Push(p[0], p[1], (int) p[2]));
        }
        v = read(m.get("sectors"));
        s.housing = v[0]; s.housingSmooth = v[1]; s.housingPolicy = v[2]; s.manufacturing = v[3]; s.aiInvest = v[4]; s.farm = v[5];
        s.energyIndex = v[6]; s.tourism = v[7];
        if (m.containsKey("policy")) {
            v = read(m.get("policy"));
            s.gasPolicy = v[0]; s.manufacturingPolicy = v[1]; s.farmPolicy = v[2]; s.energyPolicy = v[3]; s.tourismPolicy = v[4];
            for (Object o : Json.array(m.get("laws"))) s.laws.add(Law.fromJson(Json.object(o)));
        }
        return s;
    }

    /** A save from before the structural model: the headline figures, kept for {@link EconomyEngine#upgrade}. */
    private static EconomyState legacy(Map<String, Object> m) {
        EconomyState s = new EconomyState();
        s.legacy = true;
        s.outputGap = Json.num(m.get("outputGap")); s.growth = Json.num(m.get("growth")); s.unemployment = Json.num(m.get("unemployment"));
        s.inflation = Json.num(m.get("inflation")); s.fedRate = Json.num(m.get("fedRate")); s.credibility = Json.num(m.get("credibility"));
        s.oil = Json.num(m.get("oil")); s.tenYear = Json.num(m.get("tenYear"));
        s.reportedUnemployment = Json.num(m.get("reportedUnemployment")); s.reportedInflation = Json.num(m.get("reportedInflation"));
        s.sprRelief = Json.num(m.get("sprRelief")); s.fedPressured = (Boolean) m.get("fedPressured"); s.startMisery = Json.num(m.get("startMisery"));
        return s;
    }

    static List<Object> nums(double... v) {
        List<Object> l = new ArrayList<>(v.length);
        for (double x : v) l.add(x);
        return l;
    }

    static double[] read(Object o) {
        List<Object> l = Json.array(o);
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) v[i] = Json.num(l.get(i));
        return v;
    }

    private static void copy(double[] from, double[] to) { System.arraycopy(from, 0, to, 0, Math.min(from.length, to.length)); }
}
