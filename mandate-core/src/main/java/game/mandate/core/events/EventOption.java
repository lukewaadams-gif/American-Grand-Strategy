package game.mandate.core.events;

import game.mandate.core.effects.Effect;

import java.util.List;
import java.util.Optional;

/**
 * One choice in an event. If {@code successChance} is below 1, the outcome is rolled and either
 * {@code effects} or {@code failureEffects} apply; the player sees the odds before choosing.
 *
 * @param hook optional engine hook for consequences that are not plain effects (e.g. starting a war)
 */
public record EventOption(String label, List<Effect> effects, double successChance, List<Effect> failureEffects,
                          Optional<String> hook) {

    public EventOption {
        effects = List.copyOf(effects);
        failureEffects = List.copyOf(failureEffects);
    }

    public static EventOption of(String label, Effect... effects) {
        return new EventOption(label, List.of(effects), 1.0, List.of(), Optional.empty());
    }

    public static EventOption gamble(String label, double chance, List<Effect> success, List<Effect> failure) {
        return new EventOption(label, success, chance, failure, Optional.empty());
    }

    public EventOption withHook(String h) { return new EventOption(label, effects, successChance, failureEffects, Optional.of(h)); }
}
