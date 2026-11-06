package game.mandate.core.politics;

import game.mandate.core.campaign.CandidateCatalog;
import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;

/**
 * The shared political system: the single authority for national approval, coalition opinion, issue salience,
 * political capital, party wings and the opposition's strategy. The coalition model comes from the 12-week
 * prototype (it replaced Resolute Desk's top-down national approval, whose group breakdown was display-only);
 * the polarised band is kept by clamping each coalition.
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

    public double approval(PoliticsState s) {
        if (!s.governing) return Double.NaN;
        double a = 0;
        for (Coalition c : Coalition.values()) a += p.weights()[c.ordinal()] * s.level[c.ordinal()];
        return a;
    }

    /** Economic sentiment: one number for how the economy feels to voters (0 = the reference economy). */
    public double economicSentiment(OpinionInputs in) {
        return -p.econGas() * (in.gas() - in.referenceGas()) - p.econInflation() * (in.inflation() - p.refInflation())
                - p.econUnemployment() * (in.unemployment() - p.refUnemployment()) - p.econMortgage() * (in.mortgage() - p.refMortgage())
                + p.econConfidence() * (in.confidence() - 100) + p.econGrowth() * (in.growth() - p.refGrowth());
    }

    // ---- transitions ---------------------------------------------------------------------------

    public boolean apply(PoliticsState s, Effect e) {
        switch (e) {
            case Effect.Approval x -> { for (int i = 0; i < 3; i++) s.modifier[i] += x.points(); }
            case Effect.CoalitionApproval x -> {
                if (x.lasting()) s.record[x.coalition().ordinal()] += x.points();
                else s.modifier[x.coalition().ordinal()] += x.points();
            }
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
        for (int i = 0; i < 3; i++) { s.level[i] = p.start()[i]; s.modifier[i] = 0; s.record[i] = 0; }
        s.level[Coalition.SWING.ordinal()] += p.mandatePerElectoralVote() * (x.electoralVotes() - 270);
        s.honeymoon = p.honeymoonStart();
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

    /** Advance public opinion by {@code weeks}. The same equations serve weekly and monthly turns. */
    public void step(PoliticsState s, double weeks, OpinionInputs in, DeterministicRandomManager rng) {
        double months = weeks / 4.345;
        for (Issue i : Issue.values())
            s.salience.merge(i, 0.0, (old, z) -> old + (1 - Math.pow(1 - p.salienceReversionPerMonth(), months)) * (i.baseSalience() - old));
        if (!s.governing) return;
        s.weeksGoverning += weeks;
        double decayH = Math.pow(p.honeymoonDecayPerWeek(), weeks), decayM = Math.pow(p.modifierDecayPerWeek(), weeks);
        s.honeymoon *= decayH;
        for (int i = 0; i < 3; i++) s.modifier[i] *= decayM;

        PartyState mine = s.parties.get(s.presidentParty);
        double econ = economicSentiment(in);
        double[] target = {
                p.baseLevel() + (mine.averageMood() - p.moodStart()) * p.baseMood() + p.baseEcon() * econ,
                p.swingLevel() + s.honeymoon + econ + p.swingGoodwill() * s.goodwill,
                p.oppLevel() + p.oppGoodwill() * s.goodwill + p.oppEcon() * econ};
        double adjust = 1 - Math.pow(1 - p.adjustPerWeek(), weeks);
        for (int i = 0; i < 3; i++) {
            double t = clamp(target[i] + s.modifier[i] + s.record[i], p.floor()[i], p.ceiling()[i]);
            s.level[i] += adjust * (t - s.level[i]) + p.noisePerWeek()[i] * Math.sqrt(weeks) * rng.gaussian("opinion");
            s.level[i] = clamp(s.level[i], p.floor()[i], p.ceiling()[i]);
        }

        for (PartyState ps : s.parties.values())
            for (int w = 0; w < 2; w++) ps.wingMood[w] = clamp(ps.wingMood[w] + (1 - Math.pow(1 - p.moodDriftPerWeek(), weeks)) * (p.moodStart() - ps.wingMood[w]), 0, 100);

        s.recentAddresses *= Math.pow(p.addressDecayPerWeek(), weeks);
        s.goodwill = clamp(s.goodwill * Math.pow(p.goodwillDecayPerWeek(), weeks) + p.goodwillNoisePerWeek() * Math.sqrt(weeks) * rng.gaussian("opposition.mood"), -50, 50);
        double nat = approval(s);
        s.capital = Math.max(0, Math.min(p.capitalCap(), s.capital + weeks * (p.capitalPerApprovalWeek() * (nat - p.capitalApprovalPivot()) + p.capitalBaseWeek())));
        updateOpposition(s);
    }

    /** RD's opposition strategy rule with hysteresis (from the prototype). */
    void updateOpposition(PoliticsState s) {
        double score = s.goodwill + p.oppSwingWeight() * (s.level[Coalition.SWING.ordinal()] - 50) + p.oppOffset()
                + (s.weeksGoverning <= p.oppHoneymoonWeeks() ? p.oppHoneymoonBonus() : 0);
        double coop = s.opposition == OppositionStrategy.COOPERATE ? p.coopHold() : p.coopLine();
        double obstruct = s.opposition == OppositionStrategy.OBSTRUCT ? p.obstructHold() : p.obstructLine();
        s.opposition = score > coop ? OppositionStrategy.COOPERATE : score < obstruct ? OppositionStrategy.OBSTRUCT : OppositionStrategy.SELECTIVE;
    }

    /** A data release moves swing voters by how much the number changed (prototype mechanic). */
    public void onRelease(PoliticsState s, double change) {
        if (s.governing) s.modifier[Coalition.SWING.ordinal()] -= p.releaseReaction() * change;
    }

    /**
     * The bully pulpit with diminishing returns: each recent address is worth half the one before.
     * @return the approval gain applied
     */
    public double address(PoliticsState s, double charisma, double issueAdvantage, Issue issue) {
        double gain = Math.max(0.05, (p.addressBase() + p.addressCharisma() * charisma + p.addressAdvantage() * issueAdvantage) * Math.pow(0.5, s.recentAddresses));
        s.recentAddresses++;
        s.modifier[Coalition.BASE.ordinal()] += gain;
        s.modifier[Coalition.SWING.ordinal()] += gain;
        if (issue != null) bumpSalience(s, issue, p.addressSalience());
        return gain;
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
