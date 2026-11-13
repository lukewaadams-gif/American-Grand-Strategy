package game.mandate.core.westwing;

import game.mandate.core.data.Json;
import game.mandate.core.state.Party;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One thing the president does in the West Wing or the Press Briefing Room (Resolute Desk's {@code WestWing} and
 * {@code PressRoom}). Carried by {@code Command.WestWing}. Options (0-2) index the three people or justices on offer.
 */
public sealed interface WestWingOrder {
    record Hire(int role, int option) implements WestWingOrder {}
    record Fire(String person) implements WestWingOrder {}
    record Nominate(int post, int option) implements WestWingOrder {}
    record Withdraw(int post) implements WestWingOrder {}
    /** "people" (the chief of staff fills jobs) or "pardons" (the counsel answers pardon requests). */
    record Delegate(String what, boolean on) implements WestWingOrder {}
    /** One of the library's orders, rules or emergencies. */
    record Issue(String action) implements WestWingOrder {}
    /** An order of the president's own: Resolute Desk's issue index, the side, its reach (0.3-1.5) and its legal basis (0 statute, 1 a stretch, 2 an emergency). */
    record Custom(int area, Party lean, double scope, int basis) implements WestWingOrder {}
    /** An order or rule that moves one policy to the next level (the game engine checks it and fills in the policy's details). */
    record PolicyAction(String policy, int to, boolean rule) implements WestWingOrder {}
    record Revoke(String action) implements WestWingOrder {}
    record JudgeStrategy(int strategy) implements WestWingOrder {}
    record NominateJustice(int option) implements WestWingOrder {}
    /** A mission for the vice president; a task force names one of this game's issues. */
    record ViceMission(int mission, String issue) implements WestWingOrder {}
    record PressConference() implements WestWingOrder {}
    record Interview(int outlet) implements WestWingOrder {}
    record Address(String issue) implements WestWingOrder {}
    record Post(String issue, int tone) implements WestWingOrder {}
    record Poll(String issue) implements WestWingOrder {}
    record Answer(String decision, int option) implements WestWingOrder {}
    /** The transition office: "interview", "check" or "announce" a prospect for a White House job. */
    record TransitionStep(String prospect, String action) implements WestWingOrder {}

    static Map<String, Object> toJson(WestWingOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Hire x -> { m.put("role", x.role()); m.put("option", x.option()); }
            case Fire x -> m.put("person", x.person());
            case Nominate x -> { m.put("post", x.post()); m.put("option", x.option()); }
            case Withdraw x -> m.put("post", x.post());
            case Delegate x -> { m.put("what", x.what()); m.put("on", x.on()); }
            case Issue x -> m.put("action", x.action());
            case Custom x -> { m.put("area", x.area()); m.put("lean", x.lean().name()); m.put("scope", x.scope()); m.put("basis", x.basis()); }
            case PolicyAction x -> { m.put("policy", x.policy()); m.put("to", x.to()); m.put("rule", x.rule()); }
            case Revoke x -> m.put("action", x.action());
            case JudgeStrategy x -> m.put("strategy", x.strategy());
            case NominateJustice x -> m.put("option", x.option());
            case ViceMission x -> { m.put("mission", x.mission()); m.put("issue", x.issue()); }
            case PressConference x -> { }
            case Interview x -> m.put("outlet", x.outlet());
            case Address x -> m.put("issue", x.issue());
            case Post x -> { m.put("issue", x.issue()); m.put("tone", x.tone()); }
            case Poll x -> m.put("issue", x.issue());
            case Answer x -> { m.put("decision", x.decision()); m.put("option", x.option()); }
            case TransitionStep x -> { m.put("prospect", x.prospect()); m.put("action", x.action()); }
        }
        return m;
    }

    static WestWingOrder fromJson(Map<String, Object> m) {
        return switch ((String) m.get("type")) {
            case "Hire" -> new Hire(Json.integer(m.get("role")), Json.integer(m.get("option")));
            case "Fire" -> new Fire((String) m.get("person"));
            case "Nominate" -> new Nominate(Json.integer(m.get("post")), Json.integer(m.get("option")));
            case "Withdraw" -> new Withdraw(Json.integer(m.get("post")));
            case "Delegate" -> new Delegate((String) m.get("what"), (Boolean) m.get("on"));
            case "Issue" -> new Issue((String) m.get("action"));
            case "Custom" -> new Custom(Json.integer(m.get("area")), Party.valueOf((String) m.get("lean")), Json.num(m.get("scope")), Json.integer(m.get("basis")));
            case "PolicyAction" -> new PolicyAction((String) m.get("policy"), Json.integer(m.get("to")), (Boolean) m.get("rule"));
            case "Revoke" -> new Revoke((String) m.get("action"));
            case "JudgeStrategy" -> new JudgeStrategy(Json.integer(m.get("strategy")));
            case "NominateJustice" -> new NominateJustice(Json.integer(m.get("option")));
            case "ViceMission" -> new ViceMission(Json.integer(m.get("mission")), (String) m.get("issue"));
            case "PressConference" -> new PressConference();
            case "Interview" -> new Interview(Json.integer(m.get("outlet")));
            case "Address" -> new Address((String) m.get("issue"));
            case "Post" -> new Post((String) m.get("issue"), Json.integer(m.get("tone")));
            case "Poll" -> new Poll((String) m.get("issue"));
            case "Answer" -> new Answer((String) m.get("decision"), Json.integer(m.get("option")));
            case "TransitionStep" -> new TransitionStep((String) m.get("prospect"), (String) m.get("action"));
            default -> throw new IllegalArgumentException("Unknown West Wing order " + m.get("type"));
        };
    }
}
