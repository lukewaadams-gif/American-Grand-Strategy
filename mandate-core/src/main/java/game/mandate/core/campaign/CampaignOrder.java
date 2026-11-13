package game.mandate.core.campaign;

import game.mandate.core.campaign.AirWar.DigitalMessage;
import game.mandate.core.campaign.AirWar.Messenger;
import game.mandate.core.campaign.AirWar.Network;
import game.mandate.core.campaign.AirWar.Platform;
import game.mandate.core.campaign.AirWar.Tone;
import game.mandate.core.data.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One change to the player's campaign, as Battleground 2028's headquarters offers it: a schedule slot, the auto-fill and
 * clear buttons, the staff toggles, rating points in a TV market or on national TV, digital spending and its platforms,
 * field offices and organisers, transfers, red boxes, the headquarters tier, the message, and the Ad Studio. Carried by
 * {@code Command.Campaign}; money is in $M a week; states are two-letter codes.
 */
public sealed interface CampaignOrder {
    /** One slot (0-20: day x 3 + morning/afternoon/evening). {@code state} for events in a state; {@code param} the congregation, community or show. */
    record Slot(int slot, Activity activity, String state, int param) implements CampaignOrder {}
    /** The scheduler fills the empty slots (BG "Auto-fill"). */
    record FillSchedule() implements CampaignOrder {}
    /** Clears the week (from today, in the final stretch). */
    record ClearSchedule() implements CampaignOrder {}
    /** Hands a part of the campaign to the staff, or takes it back. */
    record Staff(ElectionState.StaffArea area, boolean on) implements CampaignOrder {}
    /** Weekly rating points and tone in one TV market (BG allows up to 2,000). */
    record Airtime(int market, double grps, Tone tone) implements CampaignOrder {}
    /** Weekly rating points on a national network (up to 400). */
    record NationalTv(Network network, double grps) implements CampaignOrder {}
    /** Weekly digital spending in a state (up to $5M). */
    record Digital(String state, double perWeek) implements CampaignOrder {}
    /** How the digital budget splits across the six platforms (normalised). */
    record DigitalMix(List<Double> shares) implements CampaignOrder {
        public DigitalMix { shares = List.copyOf(shares); }
    }
    /** What one platform's ads say. */
    record PlatformMessage(Platform platform, DigitalMessage message) implements CampaignOrder {}
    /** Offices to open (or close) and organisers to hire (or lay off) in a state this week. */
    record Field(String state, int offices, int organizers) implements CampaignOrder {}
    /** Weekly transfers to a state party, which hires organisers with them (up to $5M). */
    record Transfer(String state, double perWeek) implements CampaignOrder {}
    /** Signals allied super PACs to spend in a state. */
    record RedBox(String state, boolean on) implements CampaignOrder {}
    /** Headquarters: 0 lean, 1 standard, 2 juggernaut. */
    record Headquarters(int tier) implements CampaignOrder {}
    /** The message issue; null lets the staff choose each week. */
    record Message(Issue issue) implements CampaignOrder {}
    /** Pulls all TV (local and national) and/or all digital. */
    record PullAds(boolean tv, boolean digital) implements CampaignOrder {}
    /** Ad Studio: produce an ad. {@code tone} 0 positive, 1 contrast, 2 attack; {@code group} a voter group or -1 for everyone. */
    record ProduceAd(int tone, Issue issue, int group, Messenger messenger) implements CampaignOrder {}
    /** Ad Studio: a focus group for an ad in the library. */
    record TestAd(int index) implements CampaignOrder {}
    /** Ad Studio: put an ad on the air (-1 for none). */
    record AirAd(int index) implements CampaignOrder {}

    static Map<String, Object> toJson(CampaignOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", o.getClass().getSimpleName());
        switch (o) {
            case Slot x -> { m.put("slot", x.slot()); m.put("activity", x.activity().name()); m.put("state", x.state()); m.put("param", x.param()); }
            case FillSchedule x -> { }
            case ClearSchedule x -> { }
            case Staff x -> { m.put("area", x.area().name()); m.put("on", x.on()); }
            case Airtime x -> { m.put("market", x.market()); m.put("grps", x.grps()); m.put("tone", x.tone().name()); }
            case NationalTv x -> { m.put("network", x.network().name()); m.put("grps", x.grps()); }
            case Digital x -> { m.put("state", x.state()); m.put("perWeek", x.perWeek()); }
            case DigitalMix x -> m.put("shares", new ArrayList<Object>(x.shares()));
            case PlatformMessage x -> { m.put("platform", x.platform().name()); m.put("message", x.message().name()); }
            case Field x -> { m.put("state", x.state()); m.put("offices", x.offices()); m.put("organizers", x.organizers()); }
            case Transfer x -> { m.put("state", x.state()); m.put("perWeek", x.perWeek()); }
            case RedBox x -> { m.put("state", x.state()); m.put("on", x.on()); }
            case Headquarters x -> m.put("tier", x.tier());
            case Message x -> m.put("issue", x.issue() == null ? null : x.issue().name());
            case PullAds x -> { m.put("tv", x.tv()); m.put("digital", x.digital()); }
            case ProduceAd x -> { m.put("tone", x.tone()); m.put("issue", x.issue().name()); m.put("group", x.group()); m.put("messenger", x.messenger().name()); }
            case TestAd x -> m.put("index", x.index());
            case AirAd x -> m.put("index", x.index());
        }
        return m;
    }

    static CampaignOrder fromJson(Map<String, Object> m) {
        String type = (String) m.get("type");
        return switch (type) {
            case "Slot" -> new Slot(Json.integer(m.get("slot")), Activity.valueOf((String) m.get("activity")), (String) m.get("state"),
                    m.get("param") == null ? 0 : Json.integer(m.get("param")));
            case "FillSchedule" -> new FillSchedule();
            case "ClearSchedule" -> new ClearSchedule();
            case "Staff" -> new Staff(ElectionState.StaffArea.valueOf((String) m.get("area")), (Boolean) m.get("on"));
            case "Airtime" -> new Airtime(Json.integer(m.get("market")), Json.num(m.get("grps")),
                    m.get("tone") == null ? Tone.POSITIVE : Tone.valueOf((String) m.get("tone")));
            case "NationalTv" -> new NationalTv(Network.valueOf((String) m.get("network")), Json.num(m.get("grps")));
            case "Digital" -> new Digital((String) m.get("state"), Json.num(m.get("perWeek")));
            case "DigitalMix" -> new DigitalMix(Json.array(m.get("shares")).stream().map(Json::num).toList());
            case "PlatformMessage" -> new PlatformMessage(Platform.valueOf((String) m.get("platform")), DigitalMessage.valueOf((String) m.get("message")));
            case "Field" -> new Field((String) m.get("state"), Json.integer(m.get("offices")), Json.integer(m.get("organizers")));
            case "Transfer" -> new Transfer((String) m.get("state"), Json.num(m.get("perWeek")));
            case "RedBox" -> new RedBox((String) m.get("state"), (Boolean) m.get("on"));
            case "Headquarters" -> new Headquarters(Json.integer(m.get("tier")));
            case "Message" -> new Message(m.get("issue") == null ? null : Issue.valueOf((String) m.get("issue")));
            case "PullAds" -> new PullAds((Boolean) m.get("tv"), (Boolean) m.get("digital"));
            case "ProduceAd" -> new ProduceAd(Json.integer(m.get("tone")), Issue.valueOf((String) m.get("issue")), Json.integer(m.get("group")),
                    Messenger.valueOf((String) m.get("messenger")));
            case "TestAd" -> new TestAd(Json.integer(m.get("index")));
            case "AirAd" -> new AirAd(Json.integer(m.get("index")));
            default -> throw new IllegalArgumentException("Unknown campaign order " + type);
        };
    }
}
