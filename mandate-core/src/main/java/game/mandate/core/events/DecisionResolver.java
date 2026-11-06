package game.mandate.core.events;

import game.mandate.core.command.IllegalCommandException;
import game.mandate.core.effects.Effect;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.List;
import java.util.Optional;

/**
 * Turns a player's answer to a pending decision into consequences: validates the choice, rolls the odds the
 * player was shown, and returns the transitions (and any engine hook) for the game engine to dispatch.
 */
public final class DecisionResolver {
    public record Resolution(PendingEvent event, EventOption option, boolean success, List<Effect> effects, Optional<String> hook, String text) {}

    public Resolution resolve(EventScheduler scheduler, int instanceId, int optionIndex, DeterministicRandomManager rng) {
        PendingEvent p = scheduler.find(instanceId);
        if (p == null) throw new IllegalCommandException("No pending event " + instanceId);
        List<EventOption> options = p.definition().options();
        if (optionIndex < 0 || optionIndex >= options.size()) throw new IllegalCommandException("No option " + optionIndex);
        EventOption o = options.get(optionIndex);
        scheduler.remove(p);
        boolean success = o.successChance() >= 1.0 || rng.chance("events.outcome", o.successChance());
        String text = p.definition().title() + ": " + o.label() + (o.successChance() < 1 ? (success ? " (it works)" : " (it fails)") : "");
        return new Resolution(p, o, success, success ? o.effects() : o.failureEffects(), o.hook(), text);
    }
}
