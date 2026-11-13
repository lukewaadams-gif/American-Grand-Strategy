package game.mandate.web;

import game.mandate.core.command.Command;
import game.mandate.core.engine.GameEngine;
import game.mandate.core.opening.OpeningAction;
import game.mandate.web.Session.Notice;
import game.mandate.web.Session.Tone;

import java.time.LocalDate;
import java.util.Optional;

/** Picks out the moments worth a toast from the game's log, and names things for the player. */
public final class Notable {
    private Notable() {}

    static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    /** "Feb 5, 2029", written out (the desktop's formatter pattern, without the formatter, which the browser lacks). */
    public static String date(LocalDate d) { return MONTHS[d.getMonthValue() - 1] + " " + d.getDayOfMonth() + ", " + d.getYear(); }

    /** A log line ("2029-02-05  Week 3: The House passes ...") as a notice, if it is one the player should not miss. */
    public static Optional<Notice> classify(String line) {
        String s = line.length() > 12 && Character.isDigit(line.charAt(0)) ? line.substring(12) : line;
        int colon = s.indexOf(": ");
        String body = s.startsWith("Week ") && colon > 0 ? s.substring(colon + 2) : s;
        if (body.startsWith("Decision: ")) return Optional.empty();
        if (body.contains(" passes the ") && !body.contains("stopgap")) return Optional.of(new Notice("Vote passed", body, Tone.GOOD));
        if (body.contains(" rejects the ")) return Optional.of(new Notice("Vote failed", body, Tone.BAD));
        if (body.startsWith("You sign the ")) return Optional.of(new Notice("Signed into law", body, Tone.GOOD));
        if (body.contains("The Senate confirms")) return Optional.of(new Notice("Nominee confirmed", body, Tone.GOOD));
        if (body.startsWith("The Senate rejects")) return Optional.of(new Notice("Nominee rejected", body, Tone.BAD));
        if (body.startsWith("CRISIS: ")) return Optional.of(new Notice("A crisis begins", body.substring(8), Tone.BAD));
        if (body.startsWith("ECONOMY: ")) return Optional.of(new Notice("Economic shock", body.substring(9), Tone.WARN));
        if (body.contains("armed clash")) return Optional.of(new Notice("Armed clash", body, Tone.BAD));
        if (body.startsWith("The other side escalates")) return Optional.of(new Notice("The crisis escalates", body, Tone.BAD));
        if (body.contains("stands down") || body.contains("deal ends")) return Optional.of(new Notice("Crisis over", body, Tone.GOOD));
        if (body.startsWith("Funding lapses")) return Optional.of(new Notice("Government shutdown", body, Tone.BAD));
        if (body.contains("government reopens") || body.contains("Government reopens") || body.contains("reopens on your terms"))
            return Optional.of(new Notice("The government reopens", body, Tone.GOOD));
        if (body.contains("in open revolt")) return Optional.of(new Notice("Party revolt", body, Tone.BAD));
        if (body.startsWith("Opposition leaders shift to")) return Optional.of(new Notice("The opposition changes course", body, Tone.WARN));
        if (body.startsWith("The Fed raises") || body.startsWith("The Fed cuts")) return Optional.of(new Notice("The Fed moves", body, Tone.INFO));
        if (body.startsWith("EVENT: ")) return Optional.of(new Notice("Event", body.substring(7), Tone.WARN));
        if (body.startsWith("Debate: ")) return Optional.of(new Notice("Debate result", body.substring(8), body.contains("You won") ? Tone.GOOD : body.contains("opponent won") ? Tone.BAD : Tone.INFO));
        if (body.startsWith("Signed into law: ")) return Optional.of(new Notice("Signed into law", body, Tone.GOOD));
        if (body.startsWith("Your opponent's campaign: ")) return Optional.of(new Notice("Your opponent changes course", body.substring(26), Tone.WARN));
        if (body.startsWith("Midterms: ")) return Optional.of(new Notice("Midterm results", body, Tone.WARN));
        return Optional.empty();
    }

    /** A command in the player's words, for "Undo: ...". */
    public static String describe(Command c) {
        return switch (c) {
            case Command.Act a -> describe(a.action());
            case Command.EndTurn e -> "the turn you just ended";
            case Command.Pledge p -> "the promise";
            case Command.SetCampaignOrders o -> "the change of orders";
            case Command.PlanCampaign p -> "this week's plan";
            case Command.Campaign o -> "the campaign order";
            case Command.Presidency o -> "the change to the week";
            case Command.Hill o -> "the move on the Hill";
            case Command.WestWing o -> "the West Wing's order";
            case Command.ChooseDebatePosture p -> "the debate approach";
            case Command.SetAgenda a -> "the agenda";
            case Command.ResolveEvent e -> "the event answer";
            case Command.PushBill p -> "pushing the bill";
            case Command.PublicAddress p -> "the address";
            case Command.BolsterDeterrence b -> "the deterrence order";
            case Command.Diplomacy d -> "the diplomacy";
        };
    }

    static String describe(OpeningAction a) {
        return switch (a) {
            case OpeningAction.DraftBill d -> "drafting the bill";
            case OpeningAction.AmendBill d -> "amending the bill";
            case OpeningAction.Negotiate n -> "the deal with the " + n.bloc();
            case OpeningAction.GoPublic g -> "going public";
            case OpeningAction.CallVote v -> "the vote";
            case OpeningAction.UseReconciliation r -> "moving to reconciliation";
            case OpeningAction.Nominate n -> "the nomination";
            case OpeningAction.MeetOpposition m -> "the meeting";
            case OpeningAction.LeanOnFed l -> "leaning on the Fed";
            case OpeningAction.ReleaseOilReserve r -> "the oil release";
            case OpeningAction.Delegate d -> "the delegation";
            case OpeningAction.Decide d -> "the decision";
            case OpeningAction.EndWeek e -> "ending the week";
        };
    }

    public static String phase(GameEngine g) {
        return switch (g.phase()) {
            case GENERAL_CAMPAIGN_2028 -> "the 2028 campaign";
            case TRANSITION -> "the transition";
            case FIRST_100_DAYS -> "the first hundred days, week " + g.state().hundredDays().week();
            case GOVERNING -> "governing";
            case REELECTION_CAMPAIGN_2032 -> "the 2032 campaign";
            case EPILOGUE, GAME_OVER -> "finished";
        };
    }
}
