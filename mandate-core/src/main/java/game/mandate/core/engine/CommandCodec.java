package game.mandate.core.engine;

import game.mandate.core.campaign.DebatePosture;
import game.mandate.core.campaign.ElectionState;
import game.mandate.core.campaign.Issue;
import game.mandate.core.command.Command;
import game.mandate.core.congress.LegislativeStrategy;
import game.mandate.core.data.Json;
import game.mandate.core.diplomacy.HotSpot;

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
            case Command.EndTurn x -> { }
        }
        return m;
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
            case "EndTurn" -> new Command.EndTurn();
            default -> throw new IllegalArgumentException("Unknown command " + type);
        };
    }
}
