package game.mandate.core.military;

import game.mandate.core.effects.Effect;
import game.mandate.core.military.TheaterOutcome.TaiwanStatus;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The single authority for operational combat and for forces and inventories. A war is fought in operational
 * turns (6-hour turns in the first week, then daily): each turn the US fires what it has, the PLA salvoes what
 * it has, interceptors are spent, leakers sink ships and destroy aircraft, and the invasion advances against
 * Taiwan's defence. Everything spent or lost is subtracted from the persistent inventory.
 *
 * <p>This replaces the one-roll {@code AbstractTheaterResolver}: the odds still depend on deterrence, basing,
 * warning and commitment, but now also on the actual munitions and platforms left, so losses matter later.
 * The detailed First Island Chain engine remains the expansion path behind {@link TheaterResolver}.
 */
public final class MilitaryEngine {
    private final MilitaryParams p;
    private final MilitaryCatalog catalog;

    public MilitaryEngine(MilitaryParams p, MilitaryCatalog catalog) {
        this.p = p;
        this.catalog = catalog;
    }

    public MilitaryCatalog catalog() { return catalog; }

    public MilitaryState newMilitary() {
        MilitaryState s = new MilitaryState();
        for (MilitaryCatalog.ForceType f : catalog.forces()) s.forces.put(f.id(), f.baseline());
        for (MilitaryCatalog.Munition m : catalog.munitions()) s.munitions.put(m.id(), (double) m.baseline());
        return s;
    }

    public boolean apply(MilitaryState s, Effect e) {
        if (e instanceof Effect.Munitions x) {
            s.productionMultiplier = Math.min(p.surgeCap(), s.productionMultiplier * (1 + x.surge()));
            produce(s, 1.0);
            return true;
        }
        return false;
    }

    /** Monthly production restores munitions (never ships or aircraft). */
    public void stepMonth(MilitaryState s) { produce(s, 1.0); }

    private void produce(MilitaryState s, double months) {
        for (MilitaryCatalog.Munition m : catalog.munitions())
            s.munitions.merge(m.id(), m.productionPerMonth() * s.productionMultiplier * months, Double::sum);
    }

    /** Anti-ship stock as a percentage of the 2027 baseline: the munitions dial the theater scenario reports. */
    public double munitionsIndex(MilitaryState s) {
        double stock = catalog.munitions("antiship").stream().mapToDouble(m -> s.munitions(m.id())).sum();
        return 100.0 * stock / catalog.baseline("antiship");
    }

    /** Surviving fraction of a category's baseline platforms. */
    public double platformFraction(MilitaryState s, String category) {
        int now = 0, base = 0;
        for (MilitaryCatalog.ForceType f : catalog.forces()) if (f.category().equals(category)) { now += s.force(f.id()); base += f.baseline(); }
        return base == 0 ? 0 : (double) now / base;
    }

    // ---- operations ----------------------------------------------------------------------------

    public void startOperation(MilitaryState s, TheaterScenario scenario, DeterministicRandomManager rng) {
        if (s.operation != null) throw new IllegalStateException("A war is already being fought");
        OperationState o = new OperationState();
        o.commitment = scenario.commitment();
        o.deterrence = scenario.deterrence();
        o.basing = scenario.japanBasing();
        o.warningMonths = scenario.warningMonths();
        o.defenseCompetence = scenario.defenseCompetence();
        o.redStrength = p.redStart() - p.redDeterrenceReduction() * (scenario.deterrence() - 50);
        o.fog = Math.max(0.3, 1 + p.fogSd() * rng.gaussian("military.fog"));
        s.operation = o;
    }

    /** Length of the next operational turn, in hours. */
    public double nextTurnHours(MilitaryState s) {
        if (s.operation == null) return 0;
        return s.operation.days() < p.earlyDays() ? p.earlyHours() : p.lateHours();
    }

    /** Fights one operational turn. Returns the outcome if the war ended this turn. */
    public Optional<TheaterOutcome> stepOperation(MilitaryState s, DeterministicRandomManager rng) {
        OperationState o = s.operation;
        if (o == null) throw new IllegalStateException("No war in progress");
        double hours = nextTurnHours(s), f = hours / 24.0;
        double noise = Math.max(0.2, 1 + p.turnNoise() * rng.gaussian("military.turn"));
        boolean fighting = o.commitment == TheaterScenario.Commitment.INTERVENE;

        double damage;
        if (fighting) {
            double strikePlatforms = Math.sqrt(Math.max(0.05, platformFraction(s, "aircraft")));
            double effectiveness = o.fog * noise * (o.basing ? p.basingFactor() : p.noBasingFactor())
                    * (p.postureBase() + p.posturePerDeterrence() * o.deterrence) * (1 + p.warningBonus() * Math.min(3, o.warningMonths))
                    * (1 + p.defenseCompetenceWeight() * (o.defenseCompetence - 60));
            double as = fire(s, o, "antiship", p.antishipPerDay() * f * strikePlatforms, false);
            double la = fire(s, o, "landattack", p.landattackPerDay() * f * strikePlatforms, false);
            damage = (as * p.pkAntiship() + la * p.pkLandattack()) * effectiveness;

            // The PLA salvoes at the fleet and the bases; interceptors are spent; leakers do the damage.
            double salvo = p.redSalvoPerDay() * f * o.redStrength / 100.0;
            double shooters = Math.max(0.05, platformFraction(s, "surface"));
            double interceptorsFired = fire(s, o, "airdefense", salvo * p.interceptRatio() * shooters, false);
            double leakers = Math.max(0, salvo - interceptorsFired * p.interceptPk());
            int ships = lose(s, "SURFACE", leakers * p.shipPerLeaker(), rng);
            int carriers = lose(s, "CARRIER", leakers * p.carrierPerLeaker(), rng);
            int air = lose(s, "FIGHTER", leakers * p.aircraftPerLeaker() * 0.5, rng) + lose(s, "CARRIER_AIR", leakers * p.aircraftPerLeaker() * 0.5, rng);
            if (carriers > 0) air += lose(s, "CARRIER_AIR", carriers * p.carrierAirLostWithCarrier(), rng);
            o.ships += ships;
            o.carriers += carriers;
            o.aircraft += air;
            int killed = (int) Math.round(ships * catalog.force("SURFACE").killedIfLost() + carriers * catalog.force("CARRIER").killedIfLost()
                    + air * catalog.force("FIGHTER").killedIfLost() + p.killedPerDayBases() * f);
            o.killed += killed;
            s.killed += killed;
        } else {
            boolean armed = o.commitment == TheaterScenario.Commitment.ARM_AND_SANCTION;
            if (armed) fire(s, o, "transfer", p.armedTransferPerDay() * f, true);
            damage = (armed ? p.attritionArmed() : p.attritionStandAside()) * f * noise;
        }
        o.redStrength = Math.max(0, o.redStrength - damage);

        double defense = p.defenseBase() + p.defensePerWarningMonth() * Math.min(3, o.warningMonths)
                + (o.commitment == TheaterScenario.Commitment.ARM_AND_SANCTION ? p.defenseArmed() : 0);
        o.progress = Math.max(0, o.progress + f * (p.advancePerDay() * o.redStrength / 100.0 - defense * o.fog * noise));
        o.hours += hours;

        TaiwanStatus status = null;
        if (o.redStrength <= p.redBreak()) status = TaiwanStatus.DEFENDED;
        else if (o.progress >= 100) status = TaiwanStatus.FALLEN;
        else if (o.days() >= p.maxDays()) status = TaiwanStatus.STALEMATE;
        if (status == null) return Optional.empty();
        TheaterOutcome out = outcome(o, status);
        s.wars.add(out);
        s.operation = null;
        return Optional.of(out);
    }

    /** Fires up to {@code wanted} munitions of a role, in catalog priority order; spent stock is gone for good. */
    private double fire(MilitaryState s, OperationState o, String role, double wanted, boolean transfer) {
        double fired = 0;
        List<MilitaryCatalog.Munition> pool = transfer ? catalog.munitions().stream().filter(MilitaryCatalog.Munition::transferable).toList() : catalog.munitions(role);
        for (MilitaryCatalog.Munition m : pool) {
            if (fired >= wanted) break;
            double have = s.munitions(m.id()), take = Math.min(have, wanted - fired);
            if (take <= 0) continue;
            s.munitions.put(m.id(), have - take);
            s.expended.merge(m.id(), take, Double::sum);
            fired += take;
        }
        o.munitionsUsed += fired;
        return fired;
    }

    /** Removes platforms with stochastic rounding of the expected loss; records the permanent loss. */
    private int lose(MilitaryState s, String id, double expected, DeterministicRandomManager rng) {
        int have = s.force(id);
        if (have <= 0 || expected <= 0) return 0;
        int n = (int) Math.floor(expected);
        if (rng.uniform("military.loss") < expected - n) n++;
        n = Math.min(n, have);
        s.forces.put(id, have - n);
        s.forcesLost.merge(id, n, Integer::sum);
        return n;
    }

    private TheaterOutcome outcome(OperationState o, TaiwanStatus status) {
        int i = status.ordinal();
        boolean fighting = o.commitment == TheaterScenario.Commitment.INTERVENE;
        String summary = switch (status) {
            case DEFENDED -> "The invasion fails. Taiwan holds, at a terrible price.";
            case STALEMATE -> "The fighting settles into an armed standoff around a blockaded Taiwan.";
            case FALLEN -> "Taiwan's government accepts Beijing's terms. Allies question American guarantees.";
        };
        return new TheaterOutcome(status, (int) Math.ceil(o.days() / 7.0), o.killed, p.growth()[i], p.inflation()[i],
                fighting ? p.approvalFighting()[i] : p.approvalAside()[i], p.deterrenceAfter()[i], summary,
                o.ships, o.carriers, o.aircraft, (int) Math.round(o.munitionsUsed));
    }

    /**
     * A war fought on the Taiwan crisis engine (docs/ISLAND_CHAIN.md) has ended: it goes on the record, the American
     * munitions it spent leave the stockpile (by this catalog's ids), and the ships and aircraft lost leave the force.
     */
    public TheaterOutcome recordTaiwanWar(MilitaryState s, TaiwanStatus status, int weeks, int killed, int ships, int carriers, int aircraft,
                                          java.util.Map<String, Integer> spent, double deterrenceAfter, String summary) {
        int fired = 0;
        for (java.util.Map.Entry<String, Integer> e : spent.entrySet()) {
            if (catalog.munitions().stream().noneMatch(m -> m.id().equals(e.getKey()))) continue;
            double have = s.munitions(e.getKey()), take = Math.min(have, e.getValue());
            s.munitions.put(e.getKey(), have - take);
            s.expended.merge(e.getKey(), take, Double::sum);
            fired += e.getValue();
        }
        loseExactly(s, "CARRIER", carriers);
        loseExactly(s, "SURFACE", Math.max(0, ships - carriers));
        int air = aircraft - loseExactly(s, "FIGHTER", (aircraft + 1) / 2);
        air -= loseExactly(s, "CARRIER_AIR", air);
        loseExactly(s, "FIGHTER", air);
        // as the operational model counted them: surface ships apart from carriers
        TheaterOutcome out = new TheaterOutcome(status, weeks, killed, 0, 0, 0, deterrenceAfter, summary, Math.max(0, ships - carriers), carriers, aircraft, fired);
        s.killed += killed;
        s.wars.add(out);
        return out;
    }

    private int loseExactly(MilitaryState s, String id, int n) {
        int have = s.force(id), k = Math.max(0, Math.min(n, have));
        if (k == 0) return 0;
        s.forces.put(id, have - k);
        s.forcesLost.merge(id, k, Integer::sum);
        return k;
    }

    /** The transitions a finished war sends to the rest of the world. */
    public List<Effect> consequences(TheaterOutcome out) {
        List<Effect> e = new ArrayList<>();
        e.add(new Effect.Approval(out.approvalShift()));
        e.add(new Effect.EconomyShock(out.growthShock(), out.inflationShock(), 12));
        e.add(new Effect.WarEnded(out.deterrenceAfter()));
        return e;
    }
}
