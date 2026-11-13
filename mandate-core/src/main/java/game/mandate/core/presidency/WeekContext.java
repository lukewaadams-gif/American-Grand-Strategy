package game.mandate.core.presidency;

import game.mandate.core.campaign.Issue;

import java.util.Map;

/**
 * What the president's week reads from the rest of the government, assembled by the game engine. Attributes are on
 * Resolute Desk's 0-100 scale. {@code staffQuality} is Resolute Desk's staff-quality factor (0 to 1) for the post that
 * helps an activity; until the West Wing's staff arrive (milestone 5) every post counts as a steady 0.6.
 */
public record WeekContext(String presidentLast, double speech, double charisma, double bigDonors, double establishment,
                          double approval, Map<Issue, Double> salience, Map<Issue, Double> issueApproval,
                          Map<String, Double> stateApproval, boolean midtermSeason, double staffQuality,
                          double policyStaff, double commsStaff, double politicalStaff) {
    /** One quality for the whole staff (before the West Wing is staffed). */
    public WeekContext(String presidentLast, double speech, double charisma, double bigDonors, double establishment, double approval,
                       Map<Issue, Double> salience, Map<Issue, Double> issueApproval, Map<String, Double> stateApproval, boolean midtermSeason, double staffQuality) {
        this(presidentLast, speech, charisma, bigDonors, establishment, approval, salience, issueApproval, stateApproval, midtermSeason, staffQuality,
                staffQuality, staffQuality, staffQuality);
    }
}
