package game.mandate.core.congress;

import game.mandate.core.state.Party;

/** Seat counts and Senate rules. Blocs are derived from seats, so a midterm reshapes the whip count. */
public final class Congress {
    public static final int HOUSE_SIZE = 435, SENATE_SIZE = 100, HOUSE_MAJORITY = 218, CLOTURE = 60;

    private int houseDem;
    private int senateDem;
    private boolean filibuster = true;

    public Congress(int houseDem, int senateDem) {
        this.houseDem = clamp(houseDem, 0, HOUSE_SIZE);
        this.senateDem = clamp(senateDem, 0, SENATE_SIZE);
    }

    public int house(Party p) { return p == Party.DEMOCRAT ? houseDem : HOUSE_SIZE - houseDem; }
    public int senate(Party p) { return p == Party.DEMOCRAT ? senateDem : SENATE_SIZE - senateDem; }

    public void setHouse(Party p, int seats) { houseDem = clamp(p == Party.DEMOCRAT ? seats : HOUSE_SIZE - seats, 0, HOUSE_SIZE); }
    public void setSenate(Party p, int seats) { senateDem = clamp(p == Party.DEMOCRAT ? seats : SENATE_SIZE - seats, 0, SENATE_SIZE); }

    public Party houseMajority() { return houseDem >= HOUSE_MAJORITY ? Party.DEMOCRAT : Party.REPUBLICAN; }

    /** With 50-50 the vice president's party controls; the caller supplies it. */
    public Party senateMajority(Party vicePresidentParty) {
        if (senateDem == 50) return vicePresidentParty;
        return senateDem > 50 ? Party.DEMOCRAT : Party.REPUBLICAN;
    }

    public boolean unified(Party president) { return houseMajority() == president && senateMajority(president) == president; }

    public boolean filibuster() { return filibuster; }
    public void abolishFilibuster() { filibuster = false; }

    @Override
    public String toString() {
        return "House D " + houseDem + "-R " + (HOUSE_SIZE - houseDem) + ", Senate D " + senateDem + "-R " + (SENATE_SIZE - senateDem)
                + (filibuster ? "" : " (no filibuster)");
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
