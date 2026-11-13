package game.mandate.core.ai;

import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.command.Command;
import game.mandate.core.congress.Bill;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.congress.WhipCount;
import game.mandate.core.effects.Effect;
import game.mandate.core.events.EventOption;
import game.mandate.core.events.PendingEvent;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.opening.Strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Plays the player's side with simple heuristics. Used by balance batches, soak tests and the
 * "let the chief of staff handle it" delegation feature. It must only use public game APIs and
 * commands, so anything it can do, a player can do.
 */
public final class AutoPilot {
    private AutoPilot() {}

    /** Issue every command for the current turn, ending with EndTurn. */
    public static void playTurn(GameEngine g) {
        for (PendingEvent p : g.pendingEvents()) g.apply(new Command.ResolveEvent(p.instanceId(), chooseOption(p)));
        if (g.operationsActive()) {         // operational turns: the war is fought turn by turn
            g.apply(new Command.EndTurn());
            return;
        }
        int night = g.electionNightPending();
        if (night > 0) { g.apply(new Command.ElectionNightSeen(night)); return; }   // the night is watched before anything else
        if (g.taiwanActive()) {             // the Taiwan crisis, turn by turn: the staff's playbook
            g.apply(new Command.Taiwan(new game.mandate.core.taiwan.TaiwanOrder.Playbook()));
            g.apply(new Command.EndTurn());
            return;
        }
        if (g.hormuzActive()) {             // the strait, day by day: the National Security Council's playbook
            g.apply(new Command.Hormuz(new game.mandate.core.hormuz.HormuzOrder.Playbook()));
            g.apply(new Command.EndTurn());
            return;
        }
        // in office, the chief of staff writes the president's week (Resolute Desk's delegated calendar)
        if ((g.phase() == game.mandate.core.time.Phase.FIRST_100_DAYS || g.phase() == game.mandate.core.time.Phase.GOVERNING)
                && g.president().map(p -> !p.delegated()).orElse(false))
            g.apply(new Command.Presidency(new game.mandate.core.presidency.PresidencyOrder.Delegate(true)));
        // and the staff run the Hill: the whip, the desk, the budget and the deadlines
        if (g.phase() == game.mandate.core.time.Phase.GOVERNING && g.hill().map(h -> !h.delegated()).orElse(false))
            g.apply(new Command.Hill(new game.mandate.core.hill.HillOrder.Delegate(true)));
        // and the chief of staff fills jobs; the counsel answers pardon requests
        if (g.phase() == game.mandate.core.time.Phase.GOVERNING && g.westWing().map(x -> !x.delegatedPeople()).orElse(false)) {
            g.apply(new Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("people", true)));
            g.apply(new Command.WestWing(new game.mandate.core.westwing.WestWingOrder.Delegate("pardons", true)));
        }
        switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028, REELECTION_CAMPAIGN_2032 -> {
                if (g.phase() == game.mandate.core.time.Phase.GENERAL_CAMPAIGN_2028 && g.election().pledges().isEmpty())
                    for (String id : chooseAgenda(g).subList(0, 2)) g.apply(new Command.Pledge(id));
                if (g.debateThisWeek().isPresent())
                    g.apply(new Command.ChooseDebatePosture(DebatePosture.tendencyOf(g.player().candidate())));
            }
            case TRANSITION -> g.apply(new Command.SetAgenda(chooseAgenda(g)));
            case FIRST_100_DAYS -> {
                // The hundred days are played by the attentive-expert probe; its EndWeek ends the turn.
                Strategy.STRATEGIST.playWeek(g.hundredDays().orElseThrow());
                return;
            }
            case GOVERNING -> governMonth(g);
            default -> { }
        }
        g.apply(new Command.EndTurn());
        // Events raised during resolution are answered at the start of the next call.
    }

    /** The option the autopilot (and watch mode) takes: the best expected value. */
    public static int chooseOption(PendingEvent p) {
        List<EventOption> os = p.definition().options();
        int best = 0;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < os.size(); i++) {
            EventOption o = os.get(i);
            double v = o.successChance() * value(o.effects()) + (1 - o.successChance()) * value(o.failureEffects());
            if (o.hook().isPresent()) v = o.hook().get().endsWith("INTERVENE") ? 1 : 0;
            if (v > bestValue) { bestValue = v; best = i; }
        }
        return best;
    }

    static double value(List<Effect> effects) {
        double v = 0;
        for (Effect e : effects) {
            v += switch (e) {
                case Effect.Approval a -> a.points();
                case Effect.CampaignSwing s -> s.points() * 2;
                case Effect.Capital c -> c.points() / 5;
                case Effect.Cash c -> c.millions() / 20;
                case Effect.Deterrence d -> d.delta() / 6;
                case Effect.Tension t -> t.hotSpot() == HotSpot.TAIWAN_STRAIT ? -t.delta() / 8 : -t.delta() / 15;
                case Effect.EconomyShock s -> s.growthPoints() - s.inflationPoints();
                case Effect.Deficit d -> -d.pctGdp() * 3;
                case Effect.PolicyStep p -> -p.pctGdp() * 3;
                case Effect.LawPassed l -> 1;
                case Effect.Salience s -> 0;
                case Effect.CoalitionApproval c -> c.points() * 0.34;
                case Effect.Goodwill gw -> gw.delta() / 10;
                case Effect.WingMood m -> m.delta() / 10;
                case Effect.Performance p -> p.points() * 1.6;
                case Effect.Rally r -> r.points() * 1.6;
                case Effect.Scandal s -> -s.points() * 1.2;
                default -> 0;
            };
        }
        return v;
    }

    /** Promises first (keeping them is cheaper than breaking them), then the best-odds bills. */
    static List<String> chooseAgenda(GameEngine g) {
        List<Bill> mine = new ArrayList<>(g.bills().availableTo(g.player().party()));
        mine.sort(Comparator.comparingDouble((Bill b) -> g.whip(b.id(), LegislativeStrategy.PARTY_LINE, 0).passChance() * (2 + b.publicAppeal())).reversed());
        List<String> agenda = new ArrayList<>(g.election().pledges());
        for (Bill b : mine) if (agenda.size() < 3 && !agenda.contains(b.id())) agenda.add(b.id());
        return agenda;
    }

    static void governMonth(GameEngine g) {
        if (g.diplomacy().tension(HotSpot.TAIWAN_STRAIT) > 55 && g.attention() >= 1) g.apply(new Command.BolsterDeterrence());
        // Try the best-odds bill this month if it is a reasonable bet.
        Bill best = null;
        LegislativeStrategy bestStrategy = null;
        double bestScore = 0.5;
        double capital = Math.min(g.capital(), 25);
        for (Bill b : g.bills().all()) {
            if (g.laws().contains(b.id())) continue;
            for (LegislativeStrategy s : List.of(LegislativeStrategy.PARTY_LINE, LegislativeStrategy.BIPARTISAN_DEAL, LegislativeStrategy.RECONCILIATION)) {
                if (!b.availableTo(g.player().party()) && s != LegislativeStrategy.BIPARTISAN_DEAL) continue;
                if (s == LegislativeStrategy.RECONCILIATION && (b.requires60() || !g.unifiedGovernment())) continue;
                WhipCount w = g.whip(b.id(), s, capital);
                // The other party's agenda angers the base wing, so it is worth less to a sensible president.
                double score = w.passChance() * s.payoff() * (g.agenda().contains(b.id()) ? 1.5 : 1.0) * (b.availableTo(g.player().party()) ? 1.0 : 0.5);
                if (score > bestScore) { bestScore = score; best = b; bestStrategy = s; }
            }
        }
        if (best != null && g.attention() >= 2) {
            try {
                g.apply(new Command.PushBill(best.id(), bestStrategy, capital));
            } catch (RuntimeException ignored) {
                // e.g. reconciliation already used this year: fall through to other actions
            }
        }
        while (g.attention() >= 1) g.apply(new Command.PublicAddress(game.mandate.core.ai.CampaignAi.bestMessage(g, g.player())));
    }
}
