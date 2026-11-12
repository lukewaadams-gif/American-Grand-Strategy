package game.mandate.core.opening;

import game.mandate.core.executive.Post;

/**
 * Vocabulary of the 12-week governing prototype. Kept in one file so the whole decision space can be
 * read at a glance: if a choice is not listed here, the player cannot make it.
 */
public final class Types {
    private Types() {}

    /**
     * Size of the flagship bill, as factors on the catalog bill it is built from (RD: bill "size and scope").
     * Bigger delivers more and is harder to pass: it moves the bill away from the centre and grows its deficit.
     */
    public enum BillSize {
        SMALL(1, 2.0 / 3, 0.5, 0.5), MEDIUM(2, 1.0, 1.0, 1.0), LARGE(3, 4.0 / 3, 5.0 / 3, 1.75);

        public final int value;
        public final double ideologyFactor;
        public final double deficitFactor;   // on the catalog bill's deficit (a medium, borrowed bill)
        public final double growthFactor;    // on the catalog bill's demand impulse

        BillSize(int value, double ideologyFactor, double deficitFactor, double growthFactor) {
            this.value = value;
            this.ideologyFactor = ideologyFactor;
            this.deficitFactor = deficitFactor;
            this.growthFactor = growthFactor;
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


    /** Concessions offered to a bloc. (RD: "calls, projects back home, appointments, endorsements or threats".) */
    public enum Concession { PROJECT, APPOINTMENT, THREAT }

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

    /** The economic challenge of the first 100 days: which shock arrives (seeded). */
    public enum EconomicChallenge {
        INFLATION_SURGE("An inflation surge", "Shipping disruptions and a cold snap push prices up: the next inflation report will run hot."),
        JOBS_SLUMP("A hiring slump", "Layoffs spread from tech and manufacturing: weekly jobless claims are climbing.");

        public final String title;
        public final String situation;

        EconomicChallenge(String title, String situation) {
            this.title = title;
            this.situation = situation;
        }
    }

    /** Domains the chief of staff can run for a casual player. */
    public enum Domain { LEGISLATION, CABINET, CRISIS, BUDGET }
}
