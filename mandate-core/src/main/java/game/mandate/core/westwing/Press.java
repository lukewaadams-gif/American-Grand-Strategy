package game.mandate.core.westwing;

import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.news.RdFx;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.westwing.WestWingEngine.Context;
import game.mandate.core.westwing.WestWingEngine.Outcome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.westwing.WestWingData.*;
import static game.mandate.core.westwing.WestWingEngine.*;

/**
 * The press (Resolute Desk's press/QA, Speech, Social, Polls and home/HomeFront's press): each outlet's tone, press
 * relations, press conferences and interviews three questions long, Oval Office addresses and the State of the Union
 * five steps long, posts, and polls the party pays for.
 */
final class Press {
    private final WestWingEngine e;

    Press(WestWingEngine e) { this.e = e; }

    double toneTarget(WestWingState s, Context c, int o) {
        return clamp(50 + e.data.outlets().get(o).lean() * c.side() * 32 + (c.approval() - 45) * 0.4 + (s.press - 50) * 0.3, 5, 95);
    }

    void week(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        for (int o = 0; o < WestWingState.NOUTLET; o++) s.tone[o] += (toneTarget(s, c, o) - s.tone[o]) * 0.05;
        s.press += (50 + (sq(s, S_PRESS) - 0.6) * 30 - s.press) * 0.03;
        // the State of the Union, in the week holding February 4
        LocalDate d = c.date(), sotu = LocalDate.of(d.getYear(), 2, 4);
        if (s.sotuYear != d.getYear() && !d.isAfter(sotu) && !d.plusDays(6).isBefore(sotu)) {
            s.sotuYear = d.getYear();
            if (s.spStep < 0 && s.qaType < 0) startSpeech(s, c, true, out);
        }
    }

    // ---- questions ----------------------------------------------------------------------------------------------------

    private List<String> topics(WestWingState s, Context c) {
        List<String> t = new ArrayList<>();
        for (WestWingState.Scandal x : s.scandals) if (!x.over && x.severity >= 2) { t.add("about " + Trouble.lowerFirst(x.title)); break; }
        if (c.inflation() > 3) t.add("about prices: inflation is " + one(c.inflation()) + "%");
        if (c.unemployment() > 5) t.add("about jobs: unemployment is " + one(c.unemployment()) + "%");
        if (c.ownBill() != null) t.add("about the " + c.ownBill() + " in Congress");
        t.add("about " + worstIssue(c).label().toLowerCase() + ", where you're weakest");
        t.add("about your approval rating, " + Math.round(c.approval()) + "%");
        t.add("about your health and stamina");
        return t;
    }

    void startQA(WestWingState s, Context c, int type, int outlet, Outcome out) {
        if (s.qaType >= 0 || s.spStep >= 0) throw new IllegalArgumentException("Finish what you started first");
        if (type == 0 && s.week - s.lastPresser < 2) throw new IllegalArgumentException("You held a press conference last week");
        if (type == 1) {
            if (outlet < 0 || outlet >= WestWingState.NOUTLET || !e.data.outlets().get(outlet).interviews()) throw new IllegalArgumentException("That outlet does not do sit-down interviews");
            if (s.week - s.lastInterview < 2) throw new IllegalArgumentException("You gave an interview last week");
        }
        s.qaType = type; s.qaStep = 0; s.qaScore = 0; s.qaOutlet = outlet;
        List<String> t = topics(s, c);
        if (t.size() > 3) shuffle(t, new Local(mix(c.seed(), s.week * 7L + type, 0, 0, 0)));
        for (int i = 0; i < 3; i++) s.qaTopic[i] = t.get(Math.min(i, t.size() - 1));
        out.effects().add(new Effect.PresidentStrain(type == 0 ? -3 : -2, 0));
        ask(s, c);
    }

    private static void shuffle(List<String> t, Local r) {
        for (int i = t.size() - 1; i > 0; i--) { int j = r.n(i + 1); String x = t.get(i); t.set(i, t.get(j)); t.set(j, x); }
    }

    private void ask(WestWingState s, Context c) {
        int o = s.qaType == 1 ? s.qaOutlet : PRESSER_OUTLETS[(s.week + s.qaStep * 3) % PRESSER_OUTLETS.length];
        Outlet out = e.data.outlets().get(o);
        String who;
        if (s.qaType == 1) who = out.name().startsWith("The ") ? "The host" : "The interviewer";
        else {
            Local r = new Local(mix(c.seed(), 13L * o + 101, s.qaStep + s.week, 0, 0));
            who = e.pick(r, r.u() < 0.5 ? "first_f" : "first_m") + " " + e.pick(r, "lasts") + " of " + out.name();
        }
        WestWingState.Decision d = e.ask(s, "qa", (s.qaType == 0 ? "Press conference: question " : "Interview with " + out.name() + ": question ") + (s.qaStep + 1) + " of 3",
                who + " asks " + s.qaTopic[s.qaStep] + ".", ANSWERS, ANSWER_ADVICE, null, String.valueOf(o), 0, 0);
        d.a = o;
    }

    void answer(WestWingState s, Context c, int code, Outcome out, DeterministicRandomManager rng) {
        if (s.qaType < 0) return;
        int o = s.qaType == 1 ? s.qaOutlet : PRESSER_OUTLETS[(s.week + s.qaStep * 3) % PRESSER_OUTLETS.length];
        double hostile = Math.max(0, -e.data.outlets().get(o).lean() * c.side());
        double skill = c.skill(code == 0 ? INT : code == 1 ? DIS : code == 2 ? DEB : CHA);
        boolean ok = rng.uniform("ww.qa") < clamp(0.35 + (skill - 55) / 80 - 0.2 * hostile, 0.1, 0.92);
        s.qaScore += ok ? (code == 2 ? 0.6 : code == 3 ? 0.8 : 0.5) * (1 + 0.5 * hostile) : (code == 3 ? -1.0 : -0.6);
        if (code == 2) { s.press = Math.max(0, s.press - 2); out.effects().add(new Effect.WingMood(0, 1)); }
        if (code == 3 && ok) {
            Issue top = Trouble.topIssue(c);
            out.effects().add(RdFx.salience(top, 0.1));
            out.effects().add(new Effect.IssueStanding(top, 0.05));
        }
        if (!ok && c.skill(DIS) < 55 && rng.uniform("ww.qa") < 0.4) {
            out.news().add(news(c, News.Kind.BAD, "The president's answer on " + s.qaTopic[s.qaStep].replace("about ", "") + " becomes a gaffe", null, 1.8, null));
            s.qaScore -= 0.6;
        }
        s.qaStep++;
        if (s.qaStep < 3) { ask(s, c); return; }
        double score = s.qaScore + rng.gaussian("ww.qa") * 0.3;
        boolean interview = s.qaType == 1;
        double k2 = interview ? e.data.outlets().get(s.qaOutlet).audience() / 8 : 1.2;
        out.effects().add(new Effect.Performance(clamp(score, -2, 2.5) * 0.35 * Math.min(1.6, k2)));
        if (interview) { s.press += 0.5; s.interviews++; s.lastInterview = s.week; }
        else { s.press += 2; s.pressers++; s.lastPresser = s.week; }
        if (interview && hostile > 0.3 && score > 0) {
            out.effects().add(new Effect.GroupStanding("sub", 0.5));
            out.effects().add(new Effect.GroupStanding("wom", 0.5));
        }
        String verdict = score > 1.5 ? "sharp and in command" : score > 0.3 ? "steady" : score > -0.8 ? "uneven" : "rattled";
        String what = interview ? "interview with " + e.data.outlets().get(s.qaOutlet).name() : "press conference";
        out.news().add(news(c, score > 0.3 ? News.Kind.GOOD : score > -0.8 ? News.Kind.INFO : News.Kind.BAD, "Reviews of the president's " + what + ": " + verdict, null, 1.2, null));
        s.qaType = -1;
    }

    // ---- speeches -------------------------------------------------------------------------------------------------------

    void address(WestWingState s, Context c, String issue, Outcome out) {
        if (s.spStep >= 0 || s.qaType >= 0) throw new IllegalArgumentException("Finish what you started first");
        if (s.week - s.lastAddress < 8) throw new IllegalArgumentException("The networks gave you airtime " + (s.week - s.lastAddress) + " weeks ago; wait until " + (8 - (s.week - s.lastAddress)) + " more have passed");
        if (issue != null) out.effects().add(RdFx.salience(Issue.valueOf(issue), 0.15));
        startSpeech(s, c, false, out);
    }

    private void startSpeech(WestWingState s, Context c, boolean sotu, Outcome out) {
        s.spSotu = sotu; s.spStep = 0; s.spScore = 0; s.spAttacks = 0; s.spOpening = 0;
        if (sotu) out.news().add(news(c, News.Kind.INFO, "The president delivers the State of the Union", null, 1.6, null));
        e.ask(s, "speech", (sotu ? "The State of the Union" : "An address from the Oval Office") + ": the opening", "How do you begin?", SPEECH_OPEN,
                new String[]{"Communications director: \"Talk to everyone. That's the job tonight.\"", "Political director: \"Give our people something to cheer.\"",
                        "Chief of staff: \"Tell them the truth about where we are.\"", "Communications director: \"Only if it's really funny.\""}, null, "open", 0, 0);
    }

    /** The issues by how much voters care, highest first. */
    private static List<Issue> bySalience(Context c) {
        List<Issue> l = new ArrayList<>(List.of(Issue.values()));
        l.sort((a, b) -> Double.compare(c.salience().getOrDefault(b, 0.0), c.salience().getOrDefault(a, 0.0)));
        return l;
    }

    private void line(WestWingState s, Context c, int n) {
        List<Issue> iss = bySalience(c);
        Issue a = iss.get((n - 1) * 2), b = iss.get((n - 1) * 2 + 1);
        List<String> o = new ArrayList<>(), adv = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        o.add("Your record on " + a.label().toLowerCase() + " (" + Math.round(c.issueApproval().getOrDefault(a, c.approval())) + "% approve)");
        adv.add("Domestic policy adviser: \"Remind them what we've done.\""); codes.add(a.ordinal() * 4);
        o.add("A new plan on " + b.label().toLowerCase()); adv.add("Economic adviser: \"Give them something new.\""); codes.add(b.ordinal() * 4 + 1);
        o.add("Take the fight to the other side on " + a.label().toLowerCase()); adv.add("Political director: \"Draw the contrast.\""); codes.add(a.ordinal() * 4 + 2);
        if (c.ownBill() != null && !c.ownBillPublic() && n == 1) { o.add("Ask Congress to pass the " + c.ownBill()); adv.add("Legislative director: \"Put them on the spot.\""); codes.add(3); }
        int[] cs = new int[codes.size()];
        for (int i = 0; i < cs.length; i++) cs[i] = codes.get(i);
        e.ask(s, "speech", (s.spSotu ? "The State of the Union" : "An address from the Oval Office") + ": part " + n + " of 3", "What comes next?",
                o.toArray(new String[0]), adv.toArray(new String[0]), cs, "line", 0, 0);
    }

    void speechStep(WestWingState s, Context c, int code, Outcome out, DeterministicRandomManager rng) {
        if (s.spStep < 0) return;
        if (s.spStep == 0) {
            s.spOpening = code;
            s.spScore += code == 3 ? (c.skill(CHA) - 55) / 20 + rng.gaussian("ww.speech") * 0.6 : 0.4;
        } else if (s.spStep <= 3) {
            int kind = code % 4;
            if (kind == 3 && code == 3) {
                if (c.ownBillId() != null) out.effects().add(new Effect.BillPublic(c.ownBillId()));
                s.spScore += 0.4;
            } else {
                Issue i = Issue.values()[code / 4];
                out.effects().add(RdFx.salience(i, 0.08 * (s.spSotu ? 1.5 : 1)));
                switch (kind) {
                    case 0 -> s.spScore += (c.issueApproval().getOrDefault(i, c.approval()) - 48) / 10 * 0.5;
                    case 1 -> { s.spScore += 0.3; out.effects().add(new Effect.IssueStanding(i, 0.05 * c.skill(SPE) / 70)); }
                    default -> { s.spAttacks++; s.spScore += s.spOpening == 1 ? 0.5 : s.spOpening == 0 ? -0.5 : 0; }
                }
            }
        } else {
            s.spScore += code == 0 ? (s.spOpening == 1 ? -0.2 : 0.5) : code == 1 ? 0.4 : (s.spOpening == 1 ? 0.6 : 0.1);
            finish(s, c, out, rng);
            return;
        }
        s.spStep++;
        if (s.spStep <= 3) line(s, c, s.spStep);
        else e.ask(s, "speech", (s.spSotu ? "The State of the Union" : "An address from the Oval Office") + ": the close", "How do you end?", SPEECH_CLOSE,
                new String[]{"Communications director: \"Send them home hopeful.\"", "Legislative director: \"Tell them what you need from them.\"",
                        "Political director: \"Make them feel what's at stake.\""}, null, "close", 0, 0);
    }

    private void finish(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        double spe = c.skill(SPE) / 70;
        double score = s.spScore * spe + (c.skill(SPE) - 60) / 25 + rng.gaussian("ww.speech") * 0.5;
        out.effects().add(new Effect.Performance(clamp(score, -2, 3) * 0.5 * (s.spSotu ? 1.4 : 1)));
        if (s.spAttacks > 0) {
            out.effects().add(new Effect.BlocRelations(true, 1.5 * s.spAttacks));
            out.effects().add(new Effect.BlocRelations(false, -2.5 * s.spAttacks));
            out.effects().add(new Effect.WingMood(0, 2 * s.spAttacks));
        } else out.effects().add(new Effect.BlocRelations(false, 1));
        s.speeches++;
        if (!s.spSotu) s.lastAddress = s.week;
        out.effects().add(new Effect.PresidentStrain(-3, 0));
        String verdict = score > 1.5 ? "a triumph" : score > 0.5 ? "a solid success" : score > -0.5 ? "unremarkable" : "a flop";
        String head = s.spSotu ? Fmt("The State of the Union is %s: %d ovations", verdict, (int) Math.round(clamp(30 + 12 * score + (s.spAttacks > 0 ? 6 : 0), 5, 90)))
                : "Reviews of the president's address: " + verdict;
        out.news().add(news(c, score > 0.5 ? News.Kind.GOOD : score > -0.5 ? News.Kind.INFO : News.Kind.BAD, head, null, s.spSotu ? 2.2 : 1.4, null));
        s.record.add(c.date() + ": " + (s.spSotu ? "the State of the Union" : "an Oval Office address") + ", " + verdict);
        s.spStep = -1;
    }

    private static String Fmt(String f, String v, int n) { return game.mandate.core.data.Fmt.format(f, v, n); }

    // ---- posts and polls ----------------------------------------------------------------------------------------------

    void post(WestWingState s, Context c, String issue, int tone, Outcome out, DeterministicRandomManager rng) {
        if (tone < 0 || tone >= POST_TONE.length) throw new IllegalArgumentException("No such tone");
        Issue i = Issue.valueOf(issue);
        if (s.postWeek != s.week) { s.postWeek = s.week; s.postsThisWeek = 0; }
        if (s.postsThisWeek >= 5) throw new IllegalArgumentException("Five posts a week is plenty");
        s.postsThisWeek++;
        double reach = (c.skill(SMD) / 70) * (1 - 0.15 * (s.postsThisWeek - 1));
        double risk = (tone >= 2 ? 0.08 : 0.02) + Math.max(0, 60 - c.skill(DIS)) / 300;
        if (rng.uniform("ww.post") < risk) {
            out.effects().add(new Effect.Performance(-0.5));
            out.news().add(news(c, News.Kind.BAD, "The president's post on " + i.label().toLowerCase() + " backfires", null, 1.5, null));
            return;
        }
        s.followers += 0.15 * reach;
        switch (tone) {
            case 0 -> { out.effects().add(new Effect.Performance(0.12 * reach)); out.effects().add(new Effect.IssueStanding(i, 0.02 * reach)); }
            case 1 -> out.effects().add(new Effect.IssueStanding(i, 0.03 * reach));
            case 2 -> { out.effects().add(new Effect.WingMood(0, 1.5 * reach)); out.effects().add(new Effect.BlocRelations(false, -1)); out.effects().add(RdFx.salience(i, 0.04)); }
            case 3 -> { out.effects().add(new Effect.Performance(0.08 * reach)); out.effects().add(new Effect.GroupStanding("genz", 0.2 * reach)); }
            default -> { out.effects().add(new Effect.Performance(0.1 * reach * c.skill(CHA) / 70)); out.effects().add(new Effect.GroupStanding("genz", 0.3 * reach)); }
        }
        out.effects().add(RdFx.salience(i, 0.03));
    }

    void poll(WestWingState s, Context c, String issue, Outcome out) {
        Issue i = Issue.valueOf(issue);
        double app = c.issueApproval().getOrDefault(i, c.approval()), sal = c.salience().getOrDefault(i, i.baseSalience()) / i.baseSalience() * 0.5;
        out.effects().add(new Effect.PartyFunds(-0.5));
        s.lastPoll = Math.round(app) + "% approve of your handling of " + i.label().toLowerCase() + ", and " + (sal > 1 ? "a lot of" : sal > 0.6 ? "many" : "relatively few")
                + " voters say it is one of the most important problems facing the country. Overall approval is " + Math.round(c.approval()) + "%."
                + groupsLine(c, app);
        out.news().add(news(c, News.Kind.INFO, "The party's pollster reports on " + i.label().toLowerCase(), s.lastPoll, 0.3, "Executive"));
    }

    /** Resolute Desk's poll breakdown: ten groups, each at its approval shifted by how the issue runs against the headline. */
    private static String groupsLine(Context c, double issueApproval) {
        if (c.groups() == null) return "";
        int[] show = {21, 4, 5, 0, 1, 12, 10, 8, 9, 22};
        String[] names = {"suburban voters", "women", "men", "Gen Z", "seniors", "Hispanic voters", "Black voters", "whites without a degree", "whites with a degree", "rural voters"};
        StringBuilder b = new StringBuilder(" By group: ");
        for (int i = 0; i < show.length; i++) {
            if (i > 0) b.append(", ");
            b.append(names[i]).append(' ').append(Math.round(clamp(c.groups()[show[i]] + (issueApproval - c.approval()), 2, 98))).append('%');
        }
        return b.append('.').toString();
    }

    /** The issue that hurts most: approval below 45, weighted by how much it matters. */
    static Issue worstIssue(Context c) {
        Issue w = Issue.ECONOMY;
        double lo = Double.MAX_VALUE;
        for (Issue i : Issue.values()) {
            double sal = c.salience().getOrDefault(i, 0.0), v = c.issueApproval().getOrDefault(i, c.approval()) * sal - 45 * sal;
            if (v < lo) { lo = v; w = i; }
        }
        return w;
    }

    private static String one(double v) { return game.mandate.core.data.Fmt.format("%.1f", v); }
}
