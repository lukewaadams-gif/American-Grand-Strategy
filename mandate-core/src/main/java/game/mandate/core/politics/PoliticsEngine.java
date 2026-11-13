package game.mandate.core.politics;

import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

import java.util.Map;

/**
 * The shared political system: the single authority for national approval, coalition opinion, issue salience,
 * political capital, party wings and the opposition's strategy. National approval is Resolute Desk Version 2's
 * ({@code opinion/Approval.java}): a weekly target built from the era, the economy, the issues, the president's appeal,
 * the honeymoon, the record, scandal, the wear of office and the public mood, squeezed by partisanship. The three
 * coalitions of the prototype and the fifty states are its breakdown, solved so they add up to the national number.
 */
public final class PoliticsEngine {
    private final PoliticsParams p;

    public PoliticsEngine(PoliticsParams p) { this.p = p; }

    public PoliticsParams params() { return p; }

    public PoliticsState newPolitics(CandidateCatalog candidates, double startGoodwill) {
        PoliticsState s = new PoliticsState();
        for (Issue i : Issue.values()) s.salience.put(i, i.baseSalience());
        for (Party party : Party.values()) s.parties.put(party, new PartyState(party, p.moodStart(), null));
        candidates.all().forEach(c -> s.roles.put(c.id(), Role.CANDIDATE));
        s.goodwill = startGoodwill;
        return s;
    }

    // ---- national approval ---------------------------------------------------------------------

    /** National approval: Resolute Desk's number, which the coalitions and the states break down. */
    public double approval(PoliticsState s) {
        if (!s.governing) return Double.NaN;
        return s.approval;
    }

    // ---- transitions ---------------------------------------------------------------------------

    public boolean apply(PoliticsState s, Effect e) {
        switch (e) {
            // an event that moves approval moves where it is heading, for as long as this game's events were tuned to last
            case Effect.Approval x -> s.events += x.points();
            case Effect.CoalitionApproval x -> coalitionEffect(s, x);
            case Effect.IssueWork x -> {
                for (int k = 0; k < x.slots(); k++) {
                    double cmd = s.command(x.issue()), adj = s.standing(x.issue());
                    s.command.put(x.issue(), Math.min(100, cmd + 5 * x.quality() * (1 - cmd / 110)));
                    s.issueAdj.put(x.issue(), Math.min(1.5, adj + 0.02 * x.quality() * (1 - adj / 1.5)));
                }
            }
            case Effect.Remarks x -> {
                bumpSalience(s, x.issue(), 0.16 * x.issue().baseSalience());   // Resolute Desk's +0.08 on a base of 0.5
                double adj = s.standing(x.issue());
                s.issueAdj.put(x.issue(), clamp(adj + x.push() * (1 - Math.abs(adj) / 1.5) + x.luck(), -1.5, 1.5));
            }
            case Effect.IssueCommand x -> s.command.put(x.issue(), Math.min(100, s.command(x.issue()) + x.points()));
            case Effect.Performance x -> s.perf += x.points();
            case Effect.StateStanding x -> s.stateBump.merge(x.state(), x.points(), Double::sum);
            case Effect.Rally x -> s.rally += x.points();
            case Effect.Scandal x -> s.scandal += x.points();
            case Effect.Capital x -> s.capital = Math.max(0, Math.min(p.capitalCap(), s.capital + x.points()));
            case Effect.Salience x -> bumpSalience(s, x.issue(), x.delta());
            case Effect.Goodwill x -> s.goodwill = clamp(s.goodwill + x.delta(), -50, 50);
            case Effect.WingMood x -> {
                if (s.presidentParty != null) {
                    PartyState ps = s.parties.get(s.presidentParty);
                    ps.wingMood[x.wing()] = clamp(ps.wingMood[x.wing()] + x.delta(), 0, 100);
                }
            }
            case Effect.PresidentInaugurated x -> inaugurate(s, x);
            default -> { return false; }
        }
        return true;
    }

    private void inaugurate(PoliticsState s, Effect.PresidentInaugurated x) {
        s.governing = true;
        s.presidentParty = x.party();
        for (int i = 0; i < 3; i++) { s.level[i] = p.start()[i]; s.record[i] = 0; s.groupAdj[i] = 0; }
        s.capital = p.capitalStart() + p.capitalPerElectoralVote() * (x.electoralVotes() - 270);
        s.weeksGoverning = 0;
        s.parties.get(x.party()).leaderId = x.candidateId();
        s.roles.put(x.candidateId(), Role.PRESIDENT);
    }

    /** Called by the engine once Congress is known at inauguration. */
    public void setInitialTerms(PoliticsState s, boolean unified) {
        if (unified) s.capital += p.capitalUnifiedBonus();
        else s.goodwill += p.goodwillDivided();
    }

    public void setRole(PoliticsState s, String id, Role r) { s.roles.put(id, r); }

    public void setLeader(PoliticsState s, Party party, String id) {
        s.parties.get(party).leaderId = id;
        s.roles.put(id, Role.NOMINEE);
    }

    public void bumpSalience(PoliticsState s, Issue i, double delta) {
        s.salience.put(i, clamp(s.salience.get(i) + delta, p.salienceFloor(), p.salienceCeiling()));
    }

    // ---- the step ------------------------------------------------------------------------------

    /**
     * Advance public opinion by {@code weeks}. Salience, the wings, goodwill and capital move as before; national approval
     * moves by Resolute Desk's weekly equations, once a week, and the coalitions and the states follow as its breakdown.
     */
    public void step(PoliticsState s, double weeks, OpinionInputs in, DeterministicRandomManager rng) {
        double months = weeks / 4.345;
        for (Issue i : Issue.values())
            s.salience.merge(i, 0.0, (old, z) -> old + (1 - Math.pow(1 - p.salienceReversionPerMonth(), months)) * (i.baseSalience() - old));
        if (!s.governing) return;
        s.weeksGoverning += weeks;
        for (int k = 0, n = Math.max(1, (int) Math.round(weeks)); k < n; k++) approvalWeek(s, in, rng);

        for (PartyState ps : s.parties.values())
            for (int w = 0; w < 2; w++) ps.wingMood[w] = clamp(ps.wingMood[w] + (1 - Math.pow(1 - p.moodDriftPerWeek(), weeks)) * (p.moodStart() - ps.wingMood[w]), 0, 100);

        s.recentAddresses *= Math.pow(p.addressDecayPerWeek(), weeks);
        s.goodwill = clamp(s.goodwill * Math.pow(p.goodwillDecayPerWeek(), weeks) + p.goodwillNoisePerWeek() * Math.sqrt(weeks) * rng.gaussian("opposition.mood"), -50, 50);
        double nat = approval(s);
        s.capital = Math.max(0, Math.min(p.capitalCap(), s.capital + weeks * (p.capitalPerApprovalWeek() * (nat - p.capitalApprovalPivot()) + p.capitalBaseWeek())));
        updateOpposition(s);
    }

    // ---- Resolute Desk's approval (opinion/Approval.java) -------------------------------------------------------------

    /** How much each kind of event moves the target, and how fast each fades a week (Resolute Desk's constants). */
    public static final double PERF_WEIGHT = 1.6, SCANDAL_WEIGHT = 1.2, RALLY_WEIGHT = 1.6;
    static final double HONEYMOON_FADE = 0.975, RALLY_FADE = 0.965, PERF_FADE = 0.96, SCANDAL_FADE = 0.975, MOOD_FADE = 0.97, MOOD_STEP = 0.45;
    static final double PULL = 0.15, NOISE = 0.55, FLOOR_DEPTH = 16;

    /** What the approval model needs from the inauguration: the president's appeal, the difficulty, the margin, and the states. */
    public record ApprovalStart(double personal, double difficulty, double popularMargin, Map<String, Double> stateLean, Map<String, Double> stateWeight,
                                double commandStart) {}

    /** Starts approval at its target, with the honeymoon the mandate earns (Resolute Desk's {@code Approval.init}). */
    public void beginApproval(PoliticsState s, ApprovalStart a, OpinionInputs in) {
        stateEconomy(s, in);
        s.personal = a.personal();
        s.difficulty = a.difficulty();
        s.mandate = clamp(2 + 0.55 * a.popularMargin(), 0.5, 15);
        s.honeymoon = 4 + 0.6 * s.mandate;
        s.priceLevel = p.rdPriceLevel();
        s.perf = s.scandal = s.rally = s.mood = s.lasting = s.events = 0;
        for (Issue i : Issue.values()) { s.issueAdj.put(i, 0.0); s.command.put(i, a.commandStart()); }
        s.stateLean.clear(); s.stateLean.putAll(a.stateLean());
        s.stateWeight.clear(); s.stateWeight.putAll(a.stateWeight());
        s.stateBump.clear();
        s.approval = target(s, in);
        issues(s, in);
        breakdown(s);
        states(s);
    }

    /** As {@link #beginApproval}, for a game saved before Resolute Desk's approval: it carries on from the coalitions' level. */
    public void upgradeApproval(PoliticsState s, ApprovalStart a, OpinionInputs in) {
        stateEconomy(s, in);
        double was = 0;
        for (int c = 0; c < 3; c++) was += p.weights()[c] * s.level[c];
        double weeks = s.weeksGoverning;
        beginApproval(s, a, in);
        s.honeymoon *= Math.pow(HONEYMOON_FADE, weeks);
        s.approval = was;
        issues(s, in);
        breakdown(s);
        states(s);
    }

    private void approvalWeek(PoliticsState s, OpinionInputs in, DeterministicRandomManager rng) {
        stateEconomy(s, in);
        s.mood = s.mood * MOOD_FADE + rng.gaussian("approval.mood") * MOOD_STEP;
        double t = target(s, in);
        s.approval = clamp(s.approval + (t - s.approval) * PULL + rng.gaussian("approval") * NOISE, 4, 96);
        s.honeymoon *= HONEYMOON_FADE;
        s.rally *= RALLY_FADE;
        s.perf *= PERF_FADE;
        s.scandal *= SCANDAL_FADE;
        s.events *= p.eventFadePerWeek();
        for (Issue i : Issue.values()) s.issueAdj.merge(i, 0.0, (v, z) -> v * 0.97);
        s.stateBump.replaceAll((k, v) -> v * 0.9);
        for (int c = 0; c < 3; c++) s.groupAdj[c] *= 0.99;
        s.priceLevel *= Math.pow(1 + in.inflation() / 100, 1.0 / 52);
        issues(s, in);
        breakdown(s);
        states(s);
    }

    /** How sorted the era's politics are, from 0 to 1. */
    double sorted() { return clamp((p.rdPolarization() - 0.5) / 0.5, 0, 1); }

    /** The partisan floor and ceiling: about 34 and 66 for 2029. */
    public double[] bounds() { double so = sorted(); return new double[]{30 + 6 * so, 72 - 8 * so}; }

    /** Squeezes a raw target into the range partisanship allows; a rally passes through the ceiling. */
    double squash(double raw, double rally) {
        double[] b = bounds();
        double x = raw;
        if (raw < b[0]) x = b[0] - FLOOR_DEPTH * (1 - Math.exp(-0.3 * (b[0] - raw) / FLOOR_DEPTH));
        else if (raw > b[1]) x = b[1] + (raw - b[1]) * 0.35;
        x += rally * (1 - Math.max(0, x - 50) / 100);
        if (x > 86) x = 86 + (x - 86) * 0.45;
        if (x < 14) x = 14 - (14 - x) * 0.45;
        return clamp(x, 4, 96);
    }

    static double piEff(double infl) { return infl >= 0 ? infl : -1.2 * infl; }

    /** Consumer sentiment: the economy's own figure (Resolute Desk's formula, with the stock market), passed in with the rest. */
    public double sentiment(PoliticsState s, OpinionInputs in) { return in.confidence(); }

    /** The economy's part of the target: growth, jobs, prices and gas against what people expect. */
    public double economyTerm(PoliticsState s, OpinionInputs in) {
        return 1.2 * (in.growth() - 2) - 2.4 * (in.unemployment() - p.rdURef()) - 2.0 * (piEff(in.inflation()) - p.rdPiRef())
                - 3.0 * (in.gas() / s.priceLevel - p.rdGasRef()) + 0.08 * (sentiment(s, in) - 80);
    }

    double issueWeighted(PoliticsState s) {
        double v = 0, w = 0;
        for (Issue i : Issue.values()) { double sal = s.salience(i); v += sal * s.standing(i); w += sal; }
        return w > 0 ? v / w : 0;
    }

    /** The wear of office: nothing for six months, then a slow slide of up to about five points in today's sorted politics. */
    public double wear(PoliticsState s) { return Math.min(9 * (1 - 0.45 * sorted()), 0.045 * Math.max(0, s.weeksGoverning - 26)); }

    /**
     * What approval's target is made of, in points, in the order the Country view shows them: the era's baseline, then
     * each part above or below it, then the raw sum, the partisan squeeze and the rally, and the target itself.
     */
    public Map<String, Double> parts(PoliticsState s, OpinionInputs in) {
        Map<String, Double> m = new java.util.LinkedHashMap<>();
        m.put("base", p.rdEraBase());
        m.put("economy", economyTerm(s, in));
        m.put("issues", 1.5 * issueWeighted(s));
        m.put("appeal", s.personal + s.difficulty);
        m.put("honeymoon", s.honeymoon);
        m.put("record", PERF_WEIGHT * s.perf);
        m.put("events", s.events);
        m.put("lasting", s.lasting);
        m.put("scandal", -SCANDAL_WEIGHT * s.scandal);
        m.put("wear", -wear(s));
        m.put("mood", s.mood);
        double raw = 0;
        for (double v : m.values()) raw += v;
        m.put("raw", raw);
        m.put("rally", RALLY_WEIGHT * s.rally);
        m.put("target", target(s, in));
        return m;
    }

    /** Where approval is heading. */
    public double target(PoliticsState s, OpinionInputs in) {
        double fundamentals = p.rdEraBase() + economyTerm(s, in) + 1.5 * issueWeighted(s) + s.personal + s.difficulty;
        double events = s.honeymoon + PERF_WEIGHT * s.perf - SCANDAL_WEIGHT * s.scandal - wear(s) + s.mood + s.lasting + s.events;
        return squash(fundamentals + events, RALLY_WEIGHT * s.rally);
    }

    /** Approval on each issue: the national number, standing and command on it, and for the economy and prices, how they feel. */
    void issues(PoliticsState s, OpinionInputs in) {
        for (Issue i : Issue.values()) {
            double v = s.approval + 4 * s.standing(i) + (s.command(i) - 50) * 0.04;
            if (i == Issue.ECONOMY) v += 1.6 * (in.growth() - 2) - 1.5 * (in.unemployment() - p.rdURef()) + 0.08 * (sentiment(s, in) - 80);
            if (i == Issue.COST_OF_LIVING) v += -2.5 * (piEff(in.inflation()) - p.rdPiRef()) - 5 * (in.gas() / s.priceLevel - p.rdGasRef()) - 4;
            s.issueApproval.put(i, clamp(v, 10, 88));
        }
    }

    /** The three coalitions, shifted together (by bisection on one offset) so their weighted sum is the national number. */
    void breakdown(PoliticsState s) {
        double lo = -8, hi = 8, off = 0;
        for (int it = 0; it < 40; it++) {
            off = (lo + hi) / 2;
            if (coalitionSum(s, off) < s.approval) lo = off; else hi = off;
        }
        s.offset = off;
        for (int c = 0; c < 3; c++) s.level[c] = coalitionLevel(s, c, off);
    }

    private double coalitionLevel(PoliticsState s, int c, double off) {
        double bias = logit(p.start()[c] / 100);
        return clamp(100 * logistic(bias + off + s.groupAdj[c]) + s.record[c], p.floor()[c], p.ceiling()[c]);
    }

    private double coalitionSum(PoliticsState s, double off) {
        double a = 0;
        for (int c = 0; c < 3; c++) a += p.weights()[c] * coalitionLevel(s, c, off);
        return a;
    }

    private static void stateEconomy(PoliticsState s, OpinionInputs in) {
        s.stateEconomy.clear();
        s.stateEconomy.putAll(in.stateEconomy());
    }

    /**
     * Approval in each state: its lean toward the president's party, the president's standing there, its own economy
     * against the nation's, and one national offset (Resolute Desk's {@code cells}, by state).
     */
    void states(PoliticsState s) {
        if (s.stateLean.isEmpty()) return;
        double k = 0.8 + 0.6 * p.rdPolarization(), lo = -6, hi = 6, off = s.stateOffset;
        for (int it = 0; it < 40; it++) {
            double v = stateSum(s, k, off);
            if (Math.abs(v - s.approval / 100) < 0.0005) break;
            if (v < s.approval / 100) lo = off; else hi = off;
            off = (lo + hi) / 2;
        }
        s.stateOffset = off;
        for (String st : s.stateLean.keySet()) s.stateApproval.put(st, 100 * logistic(stateLogit(s, st, k, off)));
    }

    private double stateLogit(PoliticsState s, String st, double k, double off) {
        return k * logit(clamp(0.5 + s.stateLean.get(st) / 200, 0.02, 0.98)) + off + 0.06 * s.stateStanding(st) + s.stateEconomy.getOrDefault(st, 0.0);
    }

    private double stateSum(PoliticsState s, double k, double off) {
        double num = 0, den = 0;
        for (Map.Entry<String, Double> e : s.stateLean.entrySet()) {
            double w = s.stateWeight.getOrDefault(e.getKey(), 1.0);
            num += w * logistic(stateLogit(s, e.getKey(), k, off));
            den += w;
        }
        return den > 0 ? num / den : 0.5;
    }

    /** An event aimed at one coalition: the nation feels its share through the record; the coalition moves against the others. */
    private void coalitionEffect(PoliticsState s, Effect.CoalitionApproval x) {
        int c = x.coalition().ordinal();
        double w = p.weights()[c];
        if (x.lasting()) { s.lasting += w * x.points(); s.record[c] += x.points(); return; }
        s.events += w * x.points();
        double pc = clamp(s.level[c] / 100, 0.05, 0.95);
        s.groupAdj[c] += x.points() / (100 * pc * (1 - pc));
    }

    static double logistic(double x) { return 1 / (1 + Math.exp(-x)); }
    static double logit(double p) { return Math.log(p / (1 - p)); }

    /** RD's opposition strategy rule with hysteresis (from the prototype). */
    void updateOpposition(PoliticsState s) {
        double score = s.goodwill + p.oppSwingWeight() * (s.level[Coalition.SWING.ordinal()] - 50) + p.oppOffset()
                + (s.weeksGoverning <= p.oppHoneymoonWeeks() ? p.oppHoneymoonBonus() : 0);
        double coop = s.opposition == OppositionStrategy.COOPERATE ? p.coopHold() : p.coopLine();
        double obstruct = s.opposition == OppositionStrategy.OBSTRUCT ? p.obstructHold() : p.obstructLine();
        s.opposition = score > coop ? OppositionStrategy.COOPERATE : score < obstruct ? OppositionStrategy.OBSTRUCT : OppositionStrategy.SELECTIVE;
    }

    /**
     * A data release. The prototype moved swing voters by how much the number changed; Resolute Desk's approval reads the
     * economy itself every week, so the release no longer moves it separately (the headline still runs).
     */
    public void onRelease(PoliticsState s, double change) { }

    /**
     * The bully pulpit with diminishing returns: each recent address is worth half the one before.
     * @return the approval gain applied
     */
    public double address(PoliticsState s, double charisma, double issueAdvantage, Issue issue) {
        double gain = Math.max(0.05, (p.addressBase() + p.addressCharisma() * charisma + p.addressAdvantage() * issueAdvantage) * Math.pow(0.5, s.recentAddresses));
        s.recentAddresses++;
        s.events += gain * (p.weights()[Coalition.BASE.ordinal()] + p.weights()[Coalition.SWING.ordinal()]);
        if (issue != null) bumpSalience(s, issue, p.addressSalience());
        return gain;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
