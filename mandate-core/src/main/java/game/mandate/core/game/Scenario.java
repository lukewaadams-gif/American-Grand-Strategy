package game.mandate.core.game;

/**
 * Starting conditions. {@code environment} is the expected national margin (D minus R) before
 * candidates and campaigns. TOSS_UP is the default because a commercial game must be winnable from
 * either side: the Battleground 2028 baseline, carried forward from the 2026 midterms, made the
 * Democrat win 40 of 40 headless general elections.
 */
public enum Scenario {
    TOSS_UP("Toss-up", "An evenly divided country.", 0.3, 230, 50),
    BLUE_ENVIRONMENT("2026 wave carries on", "The Democratic midterm wave of 2026 still shapes the map.", 3.5, 255, 54),
    RED_ENVIRONMENT("The wave recedes", "Voters sour on the new Democratic Congress.", -2.0, 255, 54);

    private final String label;
    private final String description;
    private final double environment;
    private final int houseDemBefore;
    private final int senateDemBefore;

    Scenario(String label, String description, double environment, int houseDemBefore, int senateDemBefore) {
        this.label = label;
        this.description = description;
        this.environment = environment;
        this.houseDemBefore = houseDemBefore;
        this.senateDemBefore = senateDemBefore;
    }

    public String label() { return label; }
    public String description() { return description; }
    public double environment() { return environment; }
    public int houseDemBefore() { return houseDemBefore; }
    public int senateDemBefore() { return senateDemBefore; }
}
