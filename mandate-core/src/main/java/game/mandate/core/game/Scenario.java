package game.mandate.core.game;

/**
 * Starting conditions. The numbers (expected national margin before candidates and campaigns, and the seats
 * each party holds going into 2028) live in {@code tuning.properties} under {@code scenario.<NAME>.*}.
 * TOSS_UP is the default because a commercial game must be winnable from either side: the Battleground 2028
 * baseline, carried forward from the 2026 midterms, made the Democrat win 40 of 40 headless general elections.
 */
public enum Scenario {
    TOSS_UP("Toss-up", "An evenly divided country."),
    BLUE_ENVIRONMENT("2026 wave carries on", "The Democratic midterm wave of 2026 still shapes the map."),
    RED_ENVIRONMENT("The wave recedes", "Voters sour on the new Democratic Congress.");

    private final String label;
    private final String description;

    Scenario(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() { return label; }
    public String description() { return description; }
}
