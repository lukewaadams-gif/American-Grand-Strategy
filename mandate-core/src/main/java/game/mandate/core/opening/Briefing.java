package game.mandate.core.opening;

import java.util.List;

/**
 * The weekly "new information" step of the core loop: what changed, why, and how the political world
 * reacted. Every line names a cause, so consequences stay understandable.
 */
public record Briefing(int week, String headline, List<String> consequences, List<String> reactions, List<String> intelligence) {
    public Briefing {
        consequences = List.copyOf(consequences);
        reactions = List.copyOf(reactions);
        intelligence = List.copyOf(intelligence);
    }
}
