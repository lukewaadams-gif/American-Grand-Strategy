package game.mandate.core.opening;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * The 12-week governing prototype: the first hundred days ({@link HundredDays}) in a self-contained world, used by
 * the design probes ({@link Strategy}, {@code mandate compare}) and the {@code weeks} mode. The full game runs the
 * same {@link HundredDays} over its own {@code WorldState} as the {@code FIRST_100_DAYS} phase.
 */
public final class OpeningTerm extends HundredDays {
    public static final int WEEKS = 12;
    public static final int ATTENTION_PER_WEEK = 3;
    /** Week 1 starts the Monday after the inauguration; Fed meetings and data releases follow the calendar. */
    public static final LocalDate START = LocalDate.of(2029, 1, 22);

    private final OpeningSetup setup;
    private final List<OpeningAction> history = new ArrayList<>();

    public OpeningTerm(OpeningSetup setup) { this(setup, new StandaloneHost(setup, WEEKS, START)); }

    private OpeningTerm(OpeningSetup setup, StandaloneHost host) {
        super(host, HundredDays.begin(host));
        this.setup = setup;
    }

    public static OpeningTerm replay(OpeningSetup setup, List<OpeningAction> actions) {
        OpeningTerm t = new OpeningTerm(setup);
        for (OpeningAction a : actions) t.apply(a);
        return t;
    }

    /** Applies an action and records it, so the session can be replayed exactly. */
    @Override
    public void apply(OpeningAction a) {
        super.apply(a);
        history.add(a);
    }

    public OpeningSetup setup() { return setup; }
    public List<OpeningAction> history() { return List.copyOf(history); }
}
