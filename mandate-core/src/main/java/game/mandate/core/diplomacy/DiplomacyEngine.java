package game.mandate.core.diplomacy;

import game.mandate.core.data.Tuning;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The single authority for international relationships: flashpoint tensions, the Taiwan escalation ladder,
 * Pacific deterrence and country relations. Crossing a rung returns an escalation the game turns into a
 * decision, so the player always responds before the next rung.
 */
public final class DiplomacyEngine {
    private final Map<HotSpot, Double> startTension = new EnumMap<>(HotSpot.class);
    private final Map<Escalation, Double> threshold = new EnumMap<>(Escalation.class);
    private final double startDeterrence, taiwanBase, deterrenceWeight, reversion, reversionPivot, rungMomentum, postWarDrift,
            otherReversion, otherPivot, noise, postWarTension, relationAdjust, diplomacySuccessBase, diplomacyPolicy, diplomacyState,
            diplomacySuccessTension, diplomacyFailTension, diplomacyRelation;
    private final List<Country> countries;

    public DiplomacyEngine(Tuning t, List<Country> countries) {
        for (HotSpot h : HotSpot.values()) startTension.put(h, t.d("diplomacy.start." + h.name()));
        for (Escalation e : Escalation.values()) threshold.put(e, t.d("diplomacy.rung." + e.name()));
        startDeterrence = t.d("diplomacy.start.deterrence");
        taiwanBase = t.d("diplomacy.taiwan.base");
        deterrenceWeight = t.d("diplomacy.taiwan.deterrenceWeight");
        reversion = t.d("diplomacy.taiwan.reversion");
        reversionPivot = t.d("diplomacy.taiwan.reversionPivot");
        rungMomentum = t.d("diplomacy.taiwan.rungMomentum");
        postWarDrift = t.d("diplomacy.taiwan.postWarDrift");
        otherReversion = t.d("diplomacy.other.reversion");
        otherPivot = t.d("diplomacy.other.pivot");
        noise = t.d("diplomacy.noise");
        postWarTension = t.d("diplomacy.postWarTension");
        relationAdjust = t.d("diplomacy.relationAdjust");
        diplomacySuccessBase = t.d("diplomacy.talks.base");
        diplomacyPolicy = t.d("diplomacy.talks.policy");
        diplomacyState = t.d("diplomacy.talks.stateCompetence");
        diplomacySuccessTension = t.d("diplomacy.talks.successTension");
        diplomacyFailTension = t.d("diplomacy.talks.failTension");
        diplomacyRelation = t.d("diplomacy.talks.relation");
        this.countries = countries;
    }

    public DiplomacyState newDiplomacy() {
        DiplomacyState s = new DiplomacyState();
        s.tension.putAll(startTension);
        s.deterrence = startDeterrence;
        s.japanBasing = true;
        for (Country c : countries) s.relations.put(c.id(), c.startRelation());
        return s;
    }

    public boolean apply(DiplomacyState s, Effect e) {
        switch (e) {
            case Effect.Tension x -> s.tension.put(x.hotSpot(), clamp(s.tension.get(x.hotSpot()) + x.delta()));
            case Effect.Deterrence x -> s.deterrence = clamp(s.deterrence + x.delta());
            case Effect.WarEnded x -> {
                s.warOver = true;
                s.deterrence = clamp(x.deterrenceAfter());
                s.tension.put(HotSpot.TAIWAN_STRAIT, postWarTension);
            }
            default -> { return false; }
        }
        return true;
    }

    /** Advance one month; returns newly crossed Taiwan rungs (at most one a month). */
    public List<Escalation> stepMonth(DiplomacyState s, DeterministicRandomManager rng) {
        for (HotSpot h : HotSpot.values()) {
            double t = s.tension.get(h);
            double drift = h == HotSpot.TAIWAN_STRAIT
                    ? (s.warOver ? postWarDrift : taiwanBase - (s.deterrence - 50) * deterrenceWeight - reversion * (t - reversionPivot)
                    + (s.rung == null ? 0 : rungMomentum * (s.rung.ordinal() + 1)))
                    : otherReversion * (otherPivot - t);
            s.tension.put(h, clamp(t + drift + noise * rng.gaussian("world." + h.name())));
        }
        for (Country c : countries) {
            double target = c.startRelation() - c.hotSpot().map(h -> c.tensionLink() * (s.tension.get(h) - startTension.get(h))).orElse(0.0);
            s.relations.merge(c.id(), 0.0, (old, z) -> Math.max(-100, Math.min(100, old + relationAdjust * (target - old))));
        }
        if (s.monthsSinceBlockade >= 0) s.monthsSinceBlockade++;
        List<Escalation> crossed = new ArrayList<>();
        if (s.warOver) return crossed;
        for (Escalation e : Escalation.values()) {
            if (s.rung != null && e.ordinal() <= s.rung.ordinal()) continue;
            if (s.tension.get(HotSpot.TAIWAN_STRAIT) >= threshold.get(e)) {
                // War also needs Beijing to judge the odds acceptable: deterrence can stop it at the brink.
                if (e == Escalation.WAR && rng.uniform("world.war") < s.deterrence / 100.0) break;
                s.rung = e;
                crossed.add(e);
                if (e == Escalation.BLOCKADE) s.monthsSinceBlockade = 0;
                break;
            }
        }
        return crossed;
    }

    /** Personal diplomacy on a flashpoint. Returns whether talks succeeded; tensions and relations change here. */
    public boolean talks(DiplomacyState s, HotSpot h, double presidentPolicySkill, double stateCompetence, DeterministicRandomManager rng) {
        boolean ok = rng.chance("diplomacy", diplomacySuccessBase + diplomacyPolicy * (presidentPolicySkill - 5) + diplomacyState * (stateCompetence - 60));
        s.tension.put(h, clamp(s.tension.get(h) + (ok ? diplomacySuccessTension : diplomacyFailTension)));
        if (ok) for (Country c : countries) if (c.hotSpot().orElse(null) == h && c.startRelation() < 0)
            s.relations.merge(c.id(), diplomacyRelation, (a, b) -> Math.min(100, a + b));
        return ok;
    }

    public double threshold(Escalation e) { return threshold.get(e); }

    public List<Country> countries() { return List.copyOf(countries); }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }
}
