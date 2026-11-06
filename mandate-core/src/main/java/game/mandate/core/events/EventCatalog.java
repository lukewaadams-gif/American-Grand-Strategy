package game.mandate.core.events;

import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.time.Phase;
import game.mandate.core.diplomacy.Escalation;
import game.mandate.core.diplomacy.HotSpot;

import java.util.List;
import java.util.Set;

/**
 * The foundation's starter content: enough events to exercise every effect type and every phase.
 * Each one is written to pose a real trade-off rather than a "right answer". In milestone M2 these
 * move to data files with a validator; the record types stay the same.
 */
public final class EventCatalog {
    private EventCatalog() {}

    private static final Set<Phase> CAMPAIGNS = Set.of(Phase.GENERAL_CAMPAIGN_2028, Phase.REELECTION_CAMPAIGN_2032);
    private static final Set<Phase> GOVERNING = Set.of(Phase.GOVERNING);

    public static List<EventDefinition> standard() {
        return List.of(
                new EventDefinition("gaffe", "A hot-mic moment",
                        "A private remark about voters in a swing state leaks.",
                        CAMPAIGNS, true, g -> 0.06 * (11 - g.playerDiscipline()) / 5.0,
                        List.of(
                                EventOption.of("Apologise at once", new Effect.CampaignSwing(-0.3)),
                                EventOption.gamble("Double down: it's true", 0.4,
                                        List.of(new Effect.CampaignSwing(0.4)), List.of(new Effect.CampaignSwing(-1.4))),
                                EventOption.of("Say nothing and change the subject", new Effect.CampaignSwing(-0.7)))),

                new EventDefinition("october_surprise", "October surprise",
                        "Opposition researchers drop documents about your business dealings.",
                        CAMPAIGNS, false, g -> g.date().getMonthValue() == 10 ? 0.12 : 0,
                        List.of(
                                EventOption.gamble("Release everything", 0.7,
                                        List.of(new Effect.CampaignSwing(-0.3)), List.of(new Effect.CampaignSwing(-1.5))),
                                EventOption.of("Attack the source", new Effect.CampaignSwing(-0.6), new Effect.Cash(8)),
                                EventOption.of("Spend to change the story", new Effect.CampaignSwing(-0.2), new Effect.Cash(-25)))),

                new EventDefinition("jobs_report", "A weak jobs report",
                        "Payrolls fall for the first time in years. The economy dominates coverage.",
                        CAMPAIGNS, false, g -> 0.05,
                        List.of(
                                EventOption.of("Pivot your message to the economy", new Effect.Salience(Issue.ECONOMY, 0.06)),
                                EventOption.of("Stay on your message", new Effect.Salience(Issue.ECONOMY, 0.03)))),

                new EventDefinition("hurricane", "A major hurricane",
                        "A Category 4 storm hits the Gulf Coast.",
                        GOVERNING, true, g -> g.date().getMonthValue() >= 8 && g.date().getMonthValue() <= 10 ? 0.12 : 0,
                        List.of(
                                EventOption.of("Go there yourself and fund the recovery", new Effect.Approval(2), new Effect.Deficit(0.05)),
                                EventOption.gamble("Let FEMA lead", 0.6,
                                        List.of(new Effect.Approval(0.5)), List.of(new Effect.Approval(-3))))),

                new EventDefinition("shutdown", "A shutdown looms",
                        "The opposition Congress refuses to fund the government without cuts.",
                        GOVERNING, true, g -> g.unifiedGovernment() ? 0 : (g.date().getMonthValue() == 9 ? 0.6 : 0),
                        List.of(
                                EventOption.of("Accept a stopgap with cuts", new Effect.Capital(-8), new Effect.Deficit(-0.05)),
                                EventOption.gamble("Refuse: let them own the shutdown", 0.5,
                                        List.of(new Effect.Approval(1.5), new Effect.Capital(6), new Effect.EconomyShock(-0.3, 0, 2)),
                                        List.of(new Effect.Approval(-3), new Effect.EconomyShock(-0.5, 0, 3))))),

                new EventDefinition("scotus", "A Supreme Court vacancy",
                        "A justice announces retirement.",
                        GOVERNING, false, g -> 0.025,
                        List.of(
                                EventOption.gamble("Nominate a committed ally", 0.6,
                                                List.of(new Effect.Capital(6), new Effect.Approval(1), new Effect.Salience(Issue.ABORTION, 0.04)),
                                                List.of(new Effect.Capital(-15), new Effect.Approval(-2))),
                                EventOption.of("Nominate a consensus jurist", new Effect.Capital(-4), new Effect.Approval(0.5)))),

                new EventDefinition("ai_layoffs", "AI layoffs spread",
                        "A wave of white-collar layoffs is blamed on AI agents.",
                        GOVERNING, false, g -> 0.03,
                        List.of(
                                EventOption.of("Propose a retraining guarantee", new Effect.Deficit(0.08), new Effect.Approval(1.5),
                                        new Effect.Salience(Issue.AI_TECH, 0.05)),
                                EventOption.of("Let the market adjust", new Effect.EconomyShock(0.3, -0.1, 6), new Effect.Approval(-1.5)))),

                new EventDefinition("oil_spike", "Oil spikes",
                        "Attacks on shipping in the Gulf send crude above $110.",
                        GOVERNING, true, g -> g.tension(HotSpot.MIDDLE_EAST) > 65 ? 0.15 : 0.01,
                        List.of(
                                EventOption.of("Release the strategic reserve", new Effect.EconomyShock(-0.2, 0.4, 6), new Effect.Approval(0.5)),
                                EventOption.of("Send the Navy to escort tankers", new Effect.EconomyShock(-0.3, 0.6, 6),
                                        new Effect.Tension(HotSpot.MIDDLE_EAST, -12), new Effect.Deficit(0.02)))),

                new EventDefinition("scandal", "An aide's scandal",
                        "A senior aide is accused of steering contracts to a donor.",
                        GOVERNING, true, g -> 0.02,
                        List.of(
                                EventOption.of("Fire the aide today", new Effect.Approval(-0.5), new Effect.Capital(-3)),
                                EventOption.gamble("Stand by them", 0.5,
                                        List.of(new Effect.Capital(2)), List.of(new Effect.Approval(-4), new Effect.Capital(-10)))))
        );
    }

    /** Finds any event by id, including the ladder's (save files store ids, not definitions). */
    public static EventDefinition byId(String id) {
        for (EventDefinition d : standard()) if (d.id().equals(id)) return d;
        for (Escalation e : Escalation.values()) { EventDefinition d = escalation(e); if (d.id().equals(id)) return d; }
        throw new IllegalArgumentException("Unknown event " + id);
    }

    /** Decision events for each rung of the Taiwan ladder. */
    public static EventDefinition escalation(Escalation e) {
        Set<Phase> any = Set.of(Phase.GOVERNING);
        return switch (e) {
            case DRILLS -> new EventDefinition("taiwan_drills", "Live-fire drills around Taiwan",
                    "The PLA encircles Taiwan in its largest exercise yet.", any, false, g -> 0,
                    List.of(
                            EventOption.of("Sail a carrier through the Strait", new Effect.Deterrence(6), new Effect.Tension(HotSpot.TAIWAN_STRAIT, 4)),
                            EventOption.of("Quiet diplomacy", new Effect.Tension(HotSpot.TAIWAN_STRAIT, -6), new Effect.Deterrence(-3)),
                            EventOption.of("Ignore it", new Effect.Deterrence(-1))));
            case QUARANTINE -> new EventDefinition("taiwan_quarantine", "A coast guard 'quarantine'",
                    "China's coast guard begins inspecting ships bound for Taiwan.", any, false, g -> 0,
                    List.of(
                            EventOption.of("Escort shipping with the Navy", new Effect.Deterrence(10), new Effect.Tension(HotSpot.TAIWAN_STRAIT, 5),
                                    new Effect.Deficit(0.05)),
                            EventOption.of("Sanctions with allies", new Effect.Deterrence(4), new Effect.EconomyShock(-0.4, 0.3, 6)),
                            EventOption.of("Protest and negotiate", new Effect.Tension(HotSpot.TAIWAN_STRAIT, -8), new Effect.Deterrence(-6),
                                    new Effect.Approval(-1))));
            case BLOCKADE -> new EventDefinition("taiwan_blockade", "Blockade",
                    "The PLA Navy declares a closure zone. Taiwan's LNG will run out in weeks.", any, false, g -> 0,
                    List.of(
                            EventOption.of("Surge forces and airlift energy", new Effect.Deterrence(12), new Effect.Tension(HotSpot.TAIWAN_STRAIT, 4),
                                    new Effect.EconomyShock(-0.8, 0.8, 6), new Effect.Deficit(0.15)),
                            EventOption.of("Broker a face-saving deal", new Effect.Tension(HotSpot.TAIWAN_STRAIT, -15), new Effect.Deterrence(-8),
                                    new Effect.Approval(-2))));
            case WAR -> new EventDefinition("taiwan_war", "War in the Taiwan Strait",
                    "Missiles strike Taiwan's airbases. Landing ships are at sea.", any, false, g -> 0,
                    List.of(
                            EventOption.of("Defend Taiwan with US forces").withHook("theater:INTERVENE"),
                            EventOption.of("Arm Taiwan and sanction China").withHook("theater:ARM_AND_SANCTION"),
                            EventOption.of("Stay out of the war").withHook("theater:STAND_ASIDE")));
        };
    }
}
