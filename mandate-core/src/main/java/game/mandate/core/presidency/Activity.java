package game.mandate.core.presidency;

/**
 * What the president can do with a slot of the week: Resolute Desk's sixteen activities ({@code sim/Schedule.java}),
 * in its order, with its stamina and stress per slot. {@link #TRAVEL} (in the air) is never chosen; a trip abroad
 * fills three slots in a row.
 */
public enum Activity {
    FREE("Open time", "Nothing scheduled. Staff fill it with calls and paperwork.", 0, 0, Arg.NONE, 6.8),
    REST("Rest", "Sleep in or nap. Restores stamina and lowers stress.", 6, -1.2, Arg.NONE, 7.4),
    EXERCISE("Exercise", "A workout. Restores a little stamina, lowers stress and helps your health over time.", 2.5, -1.2, Arg.NONE, 6.8),
    FAMILY("Family time", "Time with your family. Lowers stress a lot; the public likes seeing it.", 3, -1.8, Arg.NONE, 7.4),
    POLICY("Policy time", "Meetings with your policy staff on one issue. Builds your command of it and your standing on it.", -2.5, 0.6, Arg.ISSUE, 6.2),
    REMARKS("White House remarks", "A speech at the White House on one issue. Moves the news and your standing on that issue.", -3, 0.6, Arg.ISSUE, 6.2),
    TRIP("Trip", "Fly to a state for an event. Lifts your standing there and nationally, but long flights are tiring.", -5, 0.8, Arg.STATE, 5.6),
    FUNDRAISER("Fundraiser", "Raise money for your party. Fills the war chest; too many look bad.", -3.5, 0.6, Arg.NONE, 5.6),
    CEREMONY("Ceremony", "Bill signings, medals, championship teams. A small, safe lift.", -1.5, -0.3, Arg.NONE, 6.2),
    HILL("Hill meetings", "Meet members of Congress. Improves your relationships on the Hill.", -3, 0.8, Arg.NONE, 6.2),
    CAMP_DAVID("Camp David", "A weekend at Camp David. Big stamina boost, but bad optics in a crisis.", 8, -2.5, Arg.NONE, 7.4),
    WORK_LATE("Work late", "Stay at your desk into the night. Gets work done but costs sleep.", -4.5, 1.5, Arg.NONE, 4.8),
    TRAVEL("Travel", "In the air.", -2, 0.5, Arg.NONE, 6.2),
    CAMPAIGN("Campaign stop", "Rally in a state for your party's candidates before the midterms.", -5, 0.8, Arg.STATE, 6.2),
    CABINET("Cabinet meeting", "Meet the whole cabinet. Lifts their morale and their departments' readiness.", -2, 0.3, Arg.NONE, 6.2),
    ABROAD("Trip abroad", "Fly abroad to meet a foreign leader. A big lift in relations, but long flights and jet lag are hard on stamina.", -4, 1.0, Arg.COUNTRY, 6.2);

    /** What an activity needs to know: an issue, a state or a country. */
    public enum Arg { NONE, ISSUE, STATE, COUNTRY }

    public final String label;
    public final String description;
    public final double stamina;
    public final double stress;
    public final Arg arg;
    /** Hours of sleep the night after, when this fills the evening slot (Resolute Desk's {@code Week.sleepOf}). */
    public final double nightSleep;

    Activity(String label, String description, double stamina, double stress, Arg arg, double nightSleep) {
        this.label = label;
        this.description = description;
        this.stamina = stamina;
        this.stress = stress;
        this.arg = arg;
        this.nightSleep = nightSleep;
    }

    /** A trip or a campaign stop: the activities that fly the president to a state. */
    public boolean isTrip() { return this == TRIP || this == CAMPAIGN; }
}
