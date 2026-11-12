package game.mandate.core.engine;

import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.ElectionState;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.data.Json;
import game.mandate.core.diplomacy.HotSpot;
import game.mandate.core.executive.NomineeType;
import game.mandate.core.executive.Post;
import game.mandate.core.opening.OpeningAction;
import game.mandate.core.opening.Types.BillSize;
import game.mandate.core.opening.Types.Concession;
import game.mandate.core.opening.Types.Domain;
import game.mandate.core.opening.Types.PayFor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Commands to and from JSON, for the command log in save files. */
public final class CommandCodec {
    private CommandCodec() {}

    public static Map<String, Object> toJson(Command c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", c.getClass().getSimpleName());
        switch (c) {
            case Command.PlanCampaign x -> m.put("plan", ElectionState.planJson(x.plan()));
            case Command.ChooseDebatePosture x -> m.put("posture", x.posture().name());
            case Command.SetAgenda x -> m.put("bills", x.billIds());
            case Command.PushBill x -> { m.put("bill", x.billId()); m.put("strategy", x.strategy().name()); m.put("capital", x.capital()); }
            case Command.PublicAddress x -> m.put("issue", x.issue().name());
            case Command.BolsterDeterrence x -> { }
            case Command.Diplomacy x -> m.put("hotSpot", x.hotSpot().name());
            case Command.ResolveEvent x -> { m.put("event", x.instanceId()); m.put("option", x.optionIndex()); }
            case Command.Act x -> m.put("action", actionJson(x.action()));
            case Command.EndTurn x -> { }
        }
        return m;
    }

    static Map<String, Object> actionJson(OpeningAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", a.getClass().getSimpleName());
        switch (a) {
            case OpeningAction.DraftBill x -> { m.put("size", x.size().name()); m.put("payFor", x.payFor().name()); }
            case OpeningAction.AmendBill x -> { m.put("size", x.size().name()); m.put("payFor", x.payFor().name()); }
            case OpeningAction.Negotiate x -> { m.put("bloc", x.bloc()); m.put("concession", x.concession().name()); }
            case OpeningAction.CallVote x -> m.put("insist", x.insist());
            case OpeningAction.Nominate x -> { m.put("post", x.post().name()); m.put("nominee", x.type().name()); }
            case OpeningAction.Delegate x -> { m.put("domain", x.domain().name()); m.put("on", x.on()); }
            case OpeningAction.Decide x -> { m.put("decision", x.decisionId()); m.put("option", x.option()); }
            case OpeningAction.GoPublic x -> { }
            case OpeningAction.UseReconciliation x -> { }
            case OpeningAction.MeetOpposition x -> { }
            case OpeningAction.LeanOnFed x -> { }
            case OpeningAction.ReleaseOilReserve x -> { }
            case OpeningAction.EndWeek x -> { }
        }
        return m;
    }

    static OpeningAction actionFromJson(Map<String, Object> m) {
        String type = (String) m.get("type");
        return switch (type) {
            case "DraftBill" -> new OpeningAction.DraftBill(BillSize.valueOf((String) m.get("size")), PayFor.valueOf((String) m.get("payFor")));
            case "AmendBill" -> new OpeningAction.AmendBill(BillSize.valueOf((String) m.get("size")), PayFor.valueOf((String) m.get("payFor")));
            case "Negotiate" -> new OpeningAction.Negotiate((String) m.get("bloc"), Concession.valueOf((String) m.get("concession")));
            case "CallVote" -> new OpeningAction.CallVote((Boolean) m.get("insist"));
            case "Nominate" -> new OpeningAction.Nominate(Post.valueOf((String) m.get("post")), NomineeType.valueOf((String) m.get("nominee")));
            case "Delegate" -> new OpeningAction.Delegate(Domain.valueOf((String) m.get("domain")), (Boolean) m.get("on"));
            case "Decide" -> new OpeningAction.Decide(Json.integer(m.get("decision")), Json.integer(m.get("option")));
            case "GoPublic" -> new OpeningAction.GoPublic();
            case "UseReconciliation" -> new OpeningAction.UseReconciliation();
            case "MeetOpposition" -> new OpeningAction.MeetOpposition();
            case "LeanOnFed" -> new OpeningAction.LeanOnFed();
            case "ReleaseOilReserve" -> new OpeningAction.ReleaseOilReserve();
            case "EndWeek" -> new OpeningAction.EndWeek();
            default -> throw new IllegalArgumentException("Unknown action " + type);
        };
    }

    public static Command fromJson(Map<String, Object> m) {
        String type = (String) m.get("type");
        return switch (type) {
            case "PlanCampaign" -> new Command.PlanCampaign(ElectionState.planFromJson(Json.object(m.get("plan"))));
            case "ChooseDebatePosture" -> new Command.ChooseDebatePosture(DebatePosture.valueOf((String) m.get("posture")));
            case "SetAgenda" -> new Command.SetAgenda(Json.array(m.get("bills")).stream().map(o -> (String) o).toList());
            case "PushBill" -> new Command.PushBill((String) m.get("bill"), LegislativeStrategy.valueOf((String) m.get("strategy")), Json.num(m.get("capital")));
            case "PublicAddress" -> new Command.PublicAddress(Issue.valueOf((String) m.get("issue")));
            case "BolsterDeterrence" -> new Command.BolsterDeterrence();
            case "Diplomacy" -> new Command.Diplomacy(HotSpot.valueOf((String) m.get("hotSpot")));
            case "ResolveEvent" -> new Command.ResolveEvent(Json.integer(m.get("event")), Json.integer(m.get("option")));
            case "Act" -> new Command.Act(actionFromJson(Json.object(m.get("action"))));
            case "EndTurn" -> new Command.EndTurn();
            default -> throw new IllegalArgumentException("Unknown command " + type);
        };
    }
}
