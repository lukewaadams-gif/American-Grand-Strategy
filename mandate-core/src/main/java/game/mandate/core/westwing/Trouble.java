package game.mandate.core.westwing;

import game.mandate.core.campaign.Issue;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.westwing.WestWingEngine.Context;
import game.mandate.core.westwing.WestWingEngine.Outcome;
import game.mandate.core.westwing.WestWingState.Person;
import game.mandate.core.westwing.WestWingState.Scandal;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.westwing.WestWingData.*;
import static game.mandate.core.westwing.WestWingEngine.*;

/**
 * Scandals and pardons (Resolute Desk's executive/Trouble, the part the executive owns): stories, leaks, ethics,
 * the president's past, how each grows or fades, the special counsel, and pardon requests. Congress's investigations
 * and impeachment are the Hill's; they read the pressure this builds.
 */
final class Trouble {
    private final WestWingEngine e;

    Trouble(WestWingEngine e) { this.e = e; }

    /** Resolute Desk's {@code Trouble.add}: a new scandal; a big one asks the president for a response. */
    void add(WestWingState s, Context c, int kind, String title, String body, double sev, Person p, boolean impeachable, Outcome out) {
        Scandal x = new Scandal();
        x.id = "s" + s.nextId++; x.kind = kind; x.title = title; x.body = body; x.severity = sev; x.started = s.week;
        x.person = p == null ? null : p.id; x.impeachable = impeachable;
        s.scandals.add(x);
        out.effects().add(new Effect.Scandal(0.35 * sev));
        if (sev >= 3) {
            out.news().add(news(c, News.Kind.BAD, title, body, 1.5 + 0.4 * sev, null));
            respond(s, c, x);
        } else out.news().add(news(c, News.Kind.BAD, title, body, 0.8 + 0.3 * sev, null));
        s.record.add(c.date() + ": scandal: " + title);
    }

    private void respond(WestWingState s, Context c, Scandal x) {
        List<String> o = new ArrayList<>(), adv = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        o.add("Release everything and get ahead of it"); adv.add("Communications director: \"Take the hit now and it's a one-week story.\""); codes.add(0);
        o.add("Deny it and attack the coverage"); adv.add("Political director: \"Our voters don't trust these outlets anyway.\""); codes.add(1);
        Person p = s.person(x.person);
        boolean serving = p != null && s.everyone().contains(p);
        if (serving) { o.add("Ask " + p.name + " to resign"); adv.add("Chief of staff: \"Cut it off before it reaches you.\""); codes.add(2); }
        o.add("Order an independent review"); adv.add("Counsel: \"Let someone credible look at it. It buys time and it looks serious.\""); codes.add(3);
        int[] cs = new int[codes.size()];
        for (int i = 0; i < cs.length; i++) cs[i] = codes.get(i);
        int rec = serving && x.severity >= 4 ? 2 : 0;
        e.ask(s, "scandal", x.title, x.body + " About " + Math.round(20 + x.severity * 6) + "% of Americans say they've heard a lot about it.",
                o.toArray(new String[0]), adv.toArray(new String[0]), cs, x.id, rec, 1);
    }

    void respond(WestWingState s, Context c, String id, int code, Outcome out, DeterministicRandomManager rng) {
        Scandal x = find(s, id);
        if (x == null || x.over) return;
        switch (code) {
            case 0 -> { x.severity = Math.max(0.5, x.severity - 1.2); out.effects().add(new Effect.Performance(-0.3)); }
            case 1 -> {
                out.effects().add(new Effect.BlocRelations(true, 1));
                if (rng.uniform("ww.scandal") < 0.35 + 0.04 * x.severity) {
                    x.severity += 2;
                    out.news().add(news(c, News.Kind.BAD, "New reporting contradicts the White House on " + lowerFirst(x.title), null, 1.6, null));
                }
            }
            case 2 -> {
                Person p = s.person(x.person);
                if (p != null) {
                    e.remove(s, p);
                    s.departures++;
                    out.news().add(news(c, News.Kind.INFO, p.name + " resigns", null, 1.4, null));
                }
                x.severity = Math.max(0.5, x.severity - 2.2);
                for (Person q : s.everyone()) q.morale = clamp(q.morale - 2, 0, 100);
            }
            default -> { x.stage = Math.max(x.stage, 1); x.severity *= 0.9; }
        }
    }

    private static Scandal find(WestWingState s, String id) {
        for (Scandal x : s.scandals) if (x.id.equals(id)) return x;
        return null;
    }

    /** A leak from the White House: what about, and whose aides. */
    void leak(WestWingState s, Context c, Person p, Outcome out, DeterministicRandomManager rng) {
        Issue top = topIssue(c);
        String[] about = {"shouting matches over " + top.label().toLowerCase(), "the president's temper and long days", "infighting between the chief of staff and the cabinet",
                "second thoughts about the administration's agenda", "who's up and who's down in the West Wing"};
        String what = about[rng.nextInt("ww.leak", about.length)];
        if (c.stamina() < 30) what = "how tired the president looks after long days";
        boolean named = p.trait == T_LEAKER || p.morale < 35;
        String source = named ? "aides close to " + p.name : "people inside the White House";
        double sev = 1 + rng.uniform("ww.leak") * 1.2;
        s.leaks++;
        add(s, c, Scandal.LEAK, "Leaks describe " + what, "The stories cite " + source + ".", sev, named ? p : null, false, out);
        if (sev < 3 && rng.uniform("ww.leak") < 0.5)
            e.ask(s, "leak", "Another leak from the West Wing", "Reporters have the inside story of " + what + ", from " + source + ".",
                    new String[]{"Hunt for the leaker", "Ignore it", "Tell the staff: one more leak and people go"},
                    new String[]{"Counsel: \"A leak investigation sends a message.\"", "Chief of staff: \"Every White House leaks. Don't feed it.\"",
                            "Press secretary: \"Say it once, in the staff meeting, and mean it.\""}, null, named ? p.id : "", 1, 1);
    }

    void leakAnswer(WestWingState s, Context c, String person, int code, Outcome out, DeterministicRandomManager rng) {
        switch (code) {
            case 0 -> {
                for (Person q : s.everyone()) q.morale = clamp(q.morale - 4, 0, 100);
                Person p = s.person(person);
                if (p != null && s.everyone().contains(p) && rng.uniform("ww.leak") < 0.45) {
                    e.remove(s, p);
                    s.departures++;
                    out.news().add(news(c, News.Kind.INFO, "The leak hunt ends with " + p.name + " pushed out", null, 1.2, null));
                }
            }
            case 2 -> { for (Person q : s.everyone()) { q.morale = clamp(q.morale - 2, 0, 100); q.loyalty = clamp(q.loyalty + 2, 0, 100); } }
            default -> { }
        }
    }

    void ethics(WestWingState s, Context c, Person p, Outcome out, DeterministicRandomManager rng) {
        String what = ETHICS[rng.nextInt("ww.ethics", ETHICS.length)];
        add(s, c, Scandal.ETHICS, title(p) + " " + p.name + " under fire", "Reporters say " + p.name + " " + what + ".", 2.5 + rng.uniform("ww.ethics") * 2.5, p, false, out);
    }

    void week(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        Person ag = s.post[P_DOJ];
        for (Scandal x : new ArrayList<>(s.scandals)) {
            if (x.over) continue;
            x.severity *= x.stage == 1 ? 0.965 : x.stage == 2 ? 0.995 : 0.975 - 0.02 * sq(s, S_PRESS);      // a special counsel keeps it alive
            double u = rng.uniform("ww.scandal");
            if (u < 0.012 * x.severity * (1.2 - 0.5 * sq(s, S_COUNSEL)) && x.kind != Scandal.LEAK) {
                x.severity += 1 + u;
                out.news().add(news(c, News.Kind.BAD, "New revelations in " + lowerFirst(x.title), null, 1.2 + 0.2 * x.severity, null));
            }
            out.effects().add(new Effect.Scandal(0.009 * x.severity));
            if (x.severity >= 7 && x.stage < 2 && x.impeachable && ag != null && ag.loyalty < 85 && rng.uniform("ww.scandal") < 0.06) {
                x.stage = 2;
                out.news().add(news(c, News.Kind.BAD, "The attorney general appoints a special counsel over " + lowerFirst(x.title), null, 4, null));
                s.record.add(c.date() + ": a special counsel was appointed over " + lowerFirst(x.title));
            }
            if (x.severity < 0.6) x.over = true;
        }
        // the president's past
        if (rng.uniform("ww.past") < 0.0006 + c.skill(SCA) / 60000)
            add(s, c, Scandal.PAST, PAST[rng.nextInt("ww.past", PAST.length)], "It is the kind of story that follows a president.", 2 + rng.uniform("ww.past") * 2.5, null, false, out);
        // pardon requests
        if (s.week >= s.nextPardon) {
            s.nextPardon = s.week + 8 + rng.nextInt("ww.pardon", 8);
            int i = rng.nextInt("ww.pardon", PARDONS.length);
            if (c.termWeek() < 30 && i == 1) i = 0;
            String[] t = PARDONS[i].split("\\|");
            int kind = Integer.parseInt(t[2]);
            int rec = kind == 0 || kind == 6 ? 0 : kind == 3 ? 2 : 1;
            if (s.delegPardons) pardonAnswer(s, c, kind, rec, out);
            else {
                WestWingState.Decision d = e.ask(s, "pardon", "A pardon request: " + t[0].toLowerCase(), t[1],
                        new String[]{"Grant a full pardon", "Deny it", "Commute the sentence instead"},
                        new String[]{"Counsel: \"It's within your power, and the case is strong.\"", "Chief of staff: \"Nothing good comes of this one.\"",
                                "Counsel: \"Commuting it shows mercy without the full controversy.\""}, null, String.valueOf(i), rec, 1);
                d.a = kind;
            }
        }
    }

    /** A pardon granted (code 0), denied (1) or a sentence commuted (2), by the request's kind. */
    void pardonAnswer(WestWingState s, Context c, int kind, int code, Outcome out) {
        if (code == 1) return;
        double m = code == 0 ? 1 : 0.5;
        s.pardons++;
        String what = code == 0 ? "pardons" : "commutes a sentence:";
        switch (kind) {
            case 0 -> { out.effects().add(new Effect.GroupStanding("blk", 1.2 * m)); out.effects().add(new Effect.Performance(0.1)); }
            case 1 -> {
                out.effects().add(new Effect.BlocRelations(true, 2));
                add(s, c, Scandal.PARDON, "The president " + what + " an ally", "Critics say the pardon rewards loyalty and covers up wrongdoing.", 4.5 * m, null, true, out);
            }
            case 2 -> {
                add(s, c, Scandal.PARDON, "The president " + what + " a major donor", "The donor gave millions to the president's party.", 5.5 * m, null, true, out);
                out.effects().add(new Effect.IssueStanding(Issue.DEMOCRACY, -0.3 * m));
            }
            case 3 -> {
                out.effects().add(new Effect.GroupStanding("vet", 1 * m));
                out.effects().add(new Effect.Performance(-0.2 * m));
                out.effects().add(new Effect.IssueStanding(Issue.CHINA, c.president() == Party.REPUBLICAN ? 0.05 : -0.1));
            }
            case 4 -> {
                out.effects().add(new Effect.GroupStanding("genz", 1 * m));
                out.effects().add(new Effect.IssueStanding(Issue.CHINA, -0.2 * m));
                if (s.post[P_CIA] != null) s.post[P_CIA].morale = clamp(s.post[P_CIA].morale - 10, 0, 100);
                if (s.post[P_DNI] != null) s.post[P_DNI].morale = clamp(s.post[P_DNI].morale - 8, 0, 100);
            }
            case 5 -> {
                out.effects().add(new Effect.BlocRelations(true, 1.5));
                add(s, c, Scandal.PARDON, "Pardons for rioters spark an outcry", "Opponents say the president is excusing political violence.", 3 * m, null, false, out);
                out.effects().add(new Effect.IssueStanding(Issue.CRIME, -0.3 * m));
            }
            default -> { out.effects().add(new Effect.GroupStanding("blk", 0.4 * m)); out.effects().add(new Effect.Performance(0.05)); }
        }
        if (kind == 0 || kind == 3 || kind == 4 || kind == 6)
            out.news().add(news(c, News.Kind.INFO, "The president " + (code == 0 ? "grants a pardon" : "commutes a sentence"), null, 1.0, "Executive"));
        s.record.add(c.date() + ": " + (code == 0 ? "granted a pardon" : "commuted a sentence"));
    }

    /** The issue voters care most about. */
    static Issue topIssue(Context c) {
        Issue best = Issue.ECONOMY;
        double hi = -1;
        for (Issue i : Issue.values()) { double v = c.salience().getOrDefault(i, 0.0); if (v > hi) { hi = v; best = i; } }
        return best;
    }

    static String lowerFirst(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
}
