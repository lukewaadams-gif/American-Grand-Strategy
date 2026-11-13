package game.mandate.core.hill;

import game.mandate.core.data.Json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the president does in the Roosevelt Room (Resolute Desk's {@code HillUI}). Carried by {@code Command.Hill}.
 * Bills are drafted from one of the game's bills or a policy step ({@code pol:<policy>:<from>:<to>}).
 */
public sealed interface HillOrder {
    record Draft(String source) implements HillOrder {}
    /** Size 0.5-1.5, scope 0-1, pay-for 0 borrow / 1 tax the wealthy / 2 cuts, reconciliation, the chamber that goes first. */
    record Edit(String bill, double size, double scope, int payFor, boolean reconciliation, int firstChamber) implements HillOrder {}
    record Send(String bill) implements HillOrder {}
    record Withdraw(String bill) implements HillOrder {}
    record Deal(String bill, String member, HillEngine.Deal kind) implements HillOrder {}
    record GoPublic(String bill) implements HillOrder {}
    record Meet(String member) implements HillOrder {}
    record Desk(String bill, HillEngine.Desk choice) implements HillOrder {}
    record EndFilibuster() implements HillOrder {}
    record Answer(boolean yes) implements HillOrder {}
    record Budget(double defense, double other, double tax, double entitlements) implements HillOrder {}
    record SendBudget() implements HillOrder {}
    /** The answer to a pending decision: "cr", "shutdown", "debt", "debt1" or "subpoena". */
    record Choose(String kind, HillEngine.Choice choice) implements HillOrder {}
    /** Hand the Hill to the staff, or take it back. */
    record Delegate(boolean on) implements HillOrder {}

    /** The order in the president's words, for the ledger. */
    static String describe(HillOrder o) {
        return switch (o) {
            case Draft x -> "Draft a bill (" + x.source() + ")";
            case Edit x -> "Change bill " + x.bill();
            case Send x -> "Send bill " + x.bill() + " to Congress";
            case Withdraw x -> "Withdraw bill " + x.bill();
            case Deal x -> "Deal with member " + x.member() + " on bill " + x.bill() + ": " + x.kind().name().toLowerCase();
            case GoPublic x -> "Take bill " + x.bill() + " to the country";
            case Meet x -> "Meet member " + x.member();
            case Desk x -> (x.choice() == HillEngine.Desk.SIGN ? "Sign" : x.choice() == HillEngine.Desk.VETO ? "Veto" : "Let become law unsigned") + " bill " + x.bill();
            case EndFilibuster x -> "Ask the Senate to end the filibuster";
            case Answer x -> x.yes() ? "Grant a member's request" : "Turn down a member's request";
            case Budget x -> "Set the budget request";
            case SendBudget x -> "Send the budget request";
            case Choose x -> "Decide (" + x.kind() + "): " + x.choice().name().toLowerCase();
            case Delegate x -> x.on() ? "Hand the Hill to the staff" : "Take back the Hill";
        };
    }

    static Map<String, Object> toJson(HillOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Draft x -> m.put("source", x.source());
            case Edit x -> { m.put("bill", x.bill()); m.put("size", x.size()); m.put("scope", x.scope()); m.put("payFor", x.payFor()); m.put("recon", x.reconciliation()); m.put("first", x.firstChamber()); }
            case Send x -> m.put("bill", x.bill());
            case Withdraw x -> m.put("bill", x.bill());
            case Deal x -> { m.put("bill", x.bill()); m.put("member", x.member()); m.put("kind", x.kind().name()); }
            case GoPublic x -> m.put("bill", x.bill());
            case Meet x -> m.put("member", x.member());
            case Desk x -> { m.put("bill", x.bill()); m.put("choice", x.choice().name()); }
            case EndFilibuster x -> { }
            case Answer x -> m.put("yes", x.yes());
            case Budget x -> { m.put("defense", x.defense()); m.put("other", x.other()); m.put("tax", x.tax()); m.put("entitlements", x.entitlements()); }
            case SendBudget x -> { }
            case Choose x -> { m.put("kind", x.kind()); m.put("choice", x.choice().name()); }
            case Delegate x -> m.put("on", x.on());
        }
        return m;
    }

    static HillOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Draft" -> new Draft((String) m.get("source"));
            case "Edit" -> new Edit((String) m.get("bill"), Json.num(m.get("size")), Json.num(m.get("scope")), Json.integer(m.get("payFor")), (Boolean) m.get("recon"),
                    Json.integer(m.get("first")));
            case "Send" -> new Send((String) m.get("bill"));
            case "Withdraw" -> new Withdraw((String) m.get("bill"));
            case "Deal" -> new Deal((String) m.get("bill"), (String) m.get("member"), HillEngine.Deal.valueOf((String) m.get("kind")));
            case "GoPublic" -> new GoPublic((String) m.get("bill"));
            case "Meet" -> new Meet((String) m.get("member"));
            case "Desk" -> new Desk((String) m.get("bill"), HillEngine.Desk.valueOf((String) m.get("choice")));
            case "EndFilibuster" -> new EndFilibuster();
            case "Answer" -> new Answer((Boolean) m.get("yes"));
            case "Budget" -> new Budget(Json.num(m.get("defense")), Json.num(m.get("other")), Json.num(m.get("tax")), Json.num(m.get("entitlements")));
            case "SendBudget" -> new SendBudget();
            case "Choose" -> new Choose((String) m.get("kind"), HillEngine.Choice.valueOf((String) m.get("choice")));
            case "Delegate" -> new Delegate((Boolean) m.get("on"));
            default -> throw new IllegalArgumentException("Unknown Hill order " + m.get("type"));
        };
    }
}
