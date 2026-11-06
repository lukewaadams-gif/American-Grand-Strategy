package game.mandate.core.opening;

/** Test-only window onto hidden state (the adversary's true resolve), never exposed to players. */
public final class TestAccess {
    private TestAccess() {}

    public static double resolve(OpeningTerm t) { return t.hiddenResolve(); }
}
