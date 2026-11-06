package game.mandate.core.opening;

/**
 * Vocabulary of the 12-week governing prototype. Kept in one file so the whole decision space can be
 * read at a glance: if a choice is not listed here, the player cannot make it.
 */
public final class Types {
    private Types() {}

    /** Size of the flagship bill. Bigger delivers more and is harder to pass. (RD: bill "size and scope".) */
    public enum BillSize {
        SMALL(1, 0.15, 0.10), MEDIUM(2, 0.30, 0.20), LARGE(3, 0.50, 0.35);

        public final int value;
        public final double deficit;     // % of GDP if fully borrowed
        public final double growth;      // demand impulse, points

        BillSize(int value, double deficit, double growth) {
            this.value = value;
            this.deficit = deficit;
            this.growth = growth;
        }

        public BillSize smaller() { return this == LARGE ? MEDIUM : SMALL; }
    }

    /** How the bill is paid for. (RD: "borrow, tax the wealthy and corporations, or cut spending elsewhere".) */
    public enum PayFor {
        BORROW("Borrow", 1.0, 0.0, 1.2),
        REVENUE("Raise taxes on the wealthy and corporations", 0.2, -0.10, 1.0),
        CUTS("Cut spending elsewhere", 0.3, 0.20, 0.6);

        public final String label;
        public final double deficitShare;
        public final double ideologyShift;
        public final double growthFactor;

        PayFor(String label, double deficitShare, double ideologyShift, double growthFactor) {
            this.label = label;
            this.deficitShare = deficitShare;
            this.ideologyShift = ideologyShift;
            this.growthFactor = growthFactor;
        }
    }

    /** RD's path of a bill, minus the auto-passing conference stage (see GAMEPLAY_DESIGN.md, change C-07). */
    public enum BillStage { NOT_DRAFTED, COMMITTEE, HOUSE_FLOOR, SENATE_FLOOR, LAW, DEAD }

    /** The three posts that matter most in a first quarter. (RD has 21; see change C-04.) */
    public enum Post { TREASURY, DEFENSE, STATE }

    /** RD's transition team: a loyalist, an expert, or a figure from the other side. */
    public enum NomineeType {
        LOYALIST(55, 90, 0.55), EXPERT(85, 60, 0.20), CROSSOVER(72, 45, 0.0);

        public final int competence;
        public final int loyalty;
        public final double controversy;

        NomineeType(int competence, int loyalty, double controversy) {
            this.competence = competence;
            this.loyalty = loyalty;
            this.controversy = controversy;
        }
    }

    public enum SeatStatus { VACANT, NOMINATED, HEARINGS_DONE, CONFIRMED, ACTING }

    /** Concessions offered to a bloc. (RD: "calls, projects back home, appointments, endorsements or threats".) */
    public enum Concession { PROJECT, APPOINTMENT, THREAT }

    /** RD's opposition strategy, re-evaluated weekly with hysteresis instead of quarterly (change C-09). */
    public enum OppositionStrategy { COOPERATE, SELECTIVE, OBSTRUCT }

    public enum CrisisKind {
        TAIWAN("Taiwan Strait", new String[]{"Live-fire drills", "Coast guard quarantine", "Blockade threat", "Shots fired", "Armed clash"}),
        GULF("Strait of Hormuz", new String[]{"Harassment of tankers", "Tanker seized", "Mines reported", "Missile attack on shipping", "Armed clash"});

        public final String label;
        public final String[] rungs;

        CrisisKind(String label, String[] rungs) {
            this.label = label;
            this.rungs = rungs;
        }
    }

    /** Crisis responses. Each is recommended by a different secretary (RD: "advisers take sides"). */
    public enum CrisisMove {
        DE_ESCALATE("Quiet diplomacy through a back channel", Post.STATE),
        HOLD("Sanctions and warnings with allies", null),
        SHOW_FORCE("Deploy a carrier group and escorts", Post.DEFENSE),
        STRIKE("Limited strike on the forces involved", Post.DEFENSE);

        public final String label;
        public final Post advocate;

        CrisisMove(String label, Post advocate) {
            this.label = label;
            this.advocate = advocate;
        }
    }

    public enum CrisisOutcome { NONE, ACTIVE, BACKED_DOWN, DEAL, STALEMATE, CLASH }

    /** RD's October 1 choices, moved to a March stopgap deadline. */
    public enum FundingOffer {
        CLEAN("A clean stopgap"),
        PRIORITIES("A stopgap that carries your priorities"),
        ACCEPT_CUTS("Accept the opposition's cuts"),
        DEFENSE_DEAL("A defense plus-up deal");

        public final String label;

        FundingOffer(String label) { this.label = label; }
    }

    /** Domains the chief of staff can run for a casual player. */
    public enum Domain { LEGISLATION, CABINET, CRISIS, BUDGET }
}
