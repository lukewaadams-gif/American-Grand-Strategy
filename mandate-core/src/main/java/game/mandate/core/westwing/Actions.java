package game.mandate.core.westwing;

import game.mandate.core.campaign.Issue;
import game.mandate.core.economy.Channel;
import game.mandate.core.economy.FiscalClass;
import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.news.RdFx;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.state.Party;
import game.mandate.core.westwing.WestWingEngine.Context;
import game.mandate.core.westwing.WestWingEngine.Outcome;
import game.mandate.core.westwing.WestWingState.Action;

import static game.mandate.core.westwing.WestWingData.*;
import static game.mandate.core.westwing.WestWingEngine.*;

/**
 * Executive orders, agency rules and national emergencies (Resolute Desk's executive/Actions, Implement and
 * Congressional): what each costs in public reaction and on the Hill, how a rule waits out its comment period, the
 * rollout, the lawsuits, and Congress's answer (the Congressional Review Act and votes to end an emergency).
 */
final class Actions {
    private final WestWingEngine e;

    Actions(WestWingEngine e) { this.e = e; }

    /** Legal risk after the counsel's work. */
    static double legal(WestWingState s, double base) { return clamp(base * (1.15 - 0.3 * sq(s, S_COUNSEL)), 0, 0.98); }

    /** Whether a library action can be taken now, or why not. */
    String refusal(WestWingState s, Context c, ActionDef d) {
        for (Action a : s.actions) if (d.id().equals(a.def) && a.standing()) return "It is already " + ACTION_STATUS[a.status].toLowerCase();
        if (c.date().getYear() < d.min()) return "Not yet";
        if (d.kind() == Action.EMERGENCY) {
            int k = emergencyIndex(d);
            if (k >= 0 && s.emergency[k]) return "That emergency is already declared";
        } else if (d.needs() >= 0 && !s.emergency[d.needs()]) return "It needs a national emergency over " + EMERG_NAME[d.needs()];
        if (d.id().equals("eo_paris_in") || d.id().equals("eo_paris_out")) {
            String other = d.id().equals("eo_paris_in") ? "eo_paris_out" : "eo_paris_in";
            for (Action a : s.actions) if (other.equals(a.def) && a.standing()) return "It contradicts an order in force";
        }
        return null;
    }

    int emergencyIndex(ActionDef d) {
        int k = 0;
        for (ActionDef x : e.data.library()) { if (x.kind() != Action.EMERGENCY) continue; if (x.id().equals(d.id())) return k; k++; }
        return -1;
    }

    private Action fresh(WestWingState s, Context c) {
        Action a = new Action();
        a.id = "a" + s.nextId++;
        a.issued = s.week; a.statusWeek = s.week;
        return a;
    }

    void issue(WestWingState s, Context c, String id, Outcome out, DeterministicRandomManager rng) {
        ActionDef d = e.data.action(id);
        String why = refusal(s, c, d);
        if (why != null) throw new IllegalArgumentException(why);
        Action a = fresh(s, c);
        a.def = d.id(); a.kind = d.kind(); a.area = d.area(); a.lean = d.lean(); a.name = d.name(); a.support = d.support(); a.legal = d.legal(); a.fx = d.fx();
        a.needs = d.needs();
        s.actions.add(a);
        switch (d.kind()) {
            case Action.RULE -> {
                a.status = Action.COMMENT;
                a.finalWeek = s.week + (int) Math.round(14 - 6 * pq(s, P_OMB));
                out.news().add(news(c, News.Kind.INFO, "The administration proposes a rule: " + lower(d.name()), "It is open for public comment for about " + (a.finalWeek - s.week) + " weeks.", 1.0, "Executive"));
                react(s, c, a, 0.4, out);
            }
            case Action.EMERGENCY -> {
                s.emergency[emergencyIndex(d)] = true;
                a.status = Action.LIVE;
                out.news().add(news(c, News.Kind.BAD, "The president declares a national emergency over " + EMERG_NAME[emergencyIndex(d)], d.desc(), 2.8, null));
                react(s, c, a, 1, out);
                takeEffect(s, c, a, out, rng);
                emergencyVote(s, c, a, rng);
            }
            default -> {
                a.status = Action.LIVE;
                out.news().add(news(c, News.Kind.INFO, "The president signs an executive order: " + lower(d.name()), d.desc(), 1.4, null));
                react(s, c, a, 1, out);
                takeEffect(s, c, a, out, rng);
            }
        }
        s.record.add(c.date() + ": " + (d.kind() == Action.RULE ? "proposed a rule: " : d.kind() == Action.EMERGENCY ? "declared an emergency: " : "signed an order: ") + lower(d.name()));
    }

    /** An order of the president's own design (Resolute Desk's {@code Actions.custom}). */
    void custom(WestWingState s, Context c, int area, Party lean, double scope, int basis, Outcome out, DeterministicRandomManager rng) {
        if (area < 0 || area >= RdFx.ISSUES.length) throw new IllegalArgumentException("No such issue");
        if (basis < 0 || basis > 2) throw new IllegalArgumentException("No such basis");
        boolean anyEmergency = false;
        for (boolean x : s.emergency) anyEmergency |= x;
        if (basis == 2 && !anyEmergency) throw new IllegalArgumentException("No emergency is declared");
        scope = clamp(scope, 0.3, 1.5);
        Issue i = RdFx.issue(area);
        String what = i.label().toLowerCase();
        Action a = fresh(s, c);
        a.custom = true; a.kind = Action.ORDER; a.area = area; a.lean = lean; a.scope = scope;
        a.name = (scope > 1.1 ? "A sweeping order on " : scope < 0.7 ? "A narrow order on " : "An order on ") + what;
        a.support = clamp(50 + (c.issueApproval().getOrDefault(i, c.approval()) - 45) * 0.2 - 7 * (scope - 0.6) + rng.gaussian("ww.custom") * 3 - (lean != c.president() ? 2 : 0), 25, 75);
        a.legal = clamp(0.08 + 0.3 * scope + (basis == 1 ? 0.3 : basis == 2 ? 0.12 : 0), 0, 0.95);
        int group = area == 4 ? 12 : area == 2 ? 24 : area == 12 ? 28 : 4;
        a.fx = (lean == Party.REPUBLICAN ? "gdp=0.01" : "def=0.01") + " g" + group + "=" + (lean == Party.DEMOCRAT ? 1 : -1);
        a.status = Action.LIVE;
        s.actions.add(a);
        out.news().add(news(c, News.Kind.INFO, "The president signs " + lower(a.name), null, 1.2, null));
        react(s, c, a, 1, out);
        takeEffect(s, c, a, out, rng);
        s.record.add(c.date() + ": signed " + lower(a.name));
    }

    /** An order or rule on a policy (Resolute Desk's {@code Policies.propose} with channel O or R). */
    void policy(WestWingState s, Context c, String policy, int from, int to, boolean rule, int area, double legalRisk, String name, Party lean, Outcome out,
                DeterministicRandomManager rng) {
        Action a = fresh(s, c);
        a.policy = policy; a.polFrom = from; a.polTo = to; a.kind = rule ? Action.RULE : Action.ORDER;
        a.area = area; a.lean = lean; a.name = name; a.support = 50; a.legal = legalRisk; a.fx = "";
        s.actions.add(a);
        if (rule) {
            a.status = Action.COMMENT;
            a.finalWeek = s.week + (int) Math.round(14 - 6 * pq(s, P_OMB));
            out.news().add(news(c, News.Kind.INFO, "The administration proposes a rule: " + name, "It takes effect after about " + (a.finalWeek - s.week) + " weeks of public comment.", 1.0, "Executive"));
            react(s, c, a, 0.4, out);
        } else {
            a.status = Action.LIVE;
            out.news().add(news(c, News.Kind.INFO, "The president signs an executive order: " + name, null, 1.4, null));
            react(s, c, a, 1, out);
            takeEffect(s, c, a, out, rng);
        }
        s.record.add(c.date() + ": " + (rule ? "proposed a rule: " : "signed an order: ") + name);
    }

    /**
     * The public's and the Hill's reaction (Resolute Desk's {@code Actions.react}). The party's record moves with every
     * order and with a rule when it takes effect (Resolute Desk left rules out of it).
     */
    void react(WestWingState s, Context c, Action a, double k, Outcome out) {
        if (k >= 0.6) out.effects().add(new Effect.PartyRecord(a.lean == Party.DEMOCRAT ? -0.5 : a.lean == Party.REPUBLICAN ? 0.5 : 0,
                a.lean == Party.DEMOCRAT ? -0.45 : a.lean == Party.REPUBLICAN ? 0.5 : 0, 0.5 * Math.min(1, k)));
        boolean mine = a.lean == null || a.lean == c.president();
        double sup = a.support - 50;
        Issue i = RdFx.issue(a.area);
        if (sup != 0) {
            out.effects().add(new Effect.Performance(k * sup * 0.012 * (mine ? 1 : 0.6) * Math.min(1.3, a.scope)));
            out.effects().add(new Effect.IssueStanding(i, k * sup * 0.006 * a.scope));
        }
        out.effects().add(RdFx.salience(i, 0.08 * k));
        out.effects().add(new Effect.BlocRelations(true, (mine ? 0.6 : -2) * k));
        if (a.lean == c.president()) out.effects().add(new Effect.BlocRelations(false, -1.0 * k));
    }

    /** It takes effect: its effects, the rollout, and perhaps a lawsuit. */
    void takeEffect(WestWingState s, Context c, Action a, Outcome out, DeterministicRandomManager rng) {
        apply(s, c, a, true, out);
        if (roll(s, c, a.name, a.area, 0.7, out, rng)) a.failed = true;
        double sue = a.legal * 1.3 + (a.support < 50 ? 0.1 : 0);
        if (a.legal > 0.04 && rng.uniform("ww.sue") < sue) e.courts.sue(s, c, a, out, rng);
    }

    /** Switches an action's effects on or off. Group, state and workforce effects are one-time; the rest are undone. */
    void apply(WestWingState s, Context c, Action a, boolean on, Outcome out) {
        if (on == a.applied) return;
        a.applied = on;
        if (a.policy != null) {
            out.effects().add(new Effect.PolicyByOrder(a.policy, a.polFrom, a.polTo, a.kind == Action.RULE, a.name, !on));
            return;
        }
        double sign = on ? 1 : -1;
        double[] ch = new double[Channel.COUNT];
        double def = 0, tariff = 0;
        boolean any = false;
        for (String tok : a.fx.trim().split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) continue;
            String k = tok.substring(0, eq);
            double v = Double.parseDouble(tok.substring(eq + 1)) * a.scope;
            switch (k) {
                case "gdp" -> { ch[Channel.TFP_LEVEL.ordinal()] += v; any = true; }
                case "unemp" -> { ch[Channel.NAIRU.ordinal()] += v; any = true; }
                case "infl" -> { ch[Channel.GOODS.ordinal()] += v; ch[Channel.SERVICES.ordinal()] += v; any = true; }
                case "oil" -> { ch[Channel.OIL.ordinal()] += v; any = true; }
                case "def" -> { def += v; any = true; }
                case "tariff" -> tariff += v;
                case "wf" -> { if (on) for (int p = 0; p < WestWingState.NPOST; p++) s.wf[p] *= 1 + v / 100; }
                case "ready" -> { if (on) for (int p = 0; p < WestWingState.NPOST; p++) s.ready[p] = clamp(s.ready[p] + v, 5, 100); }
                default -> {
                    if (k.startsWith("st:")) { if (on) out.effects().add(new Effect.StateStanding(k.substring(3), v)); }
                    else if (k.length() > 1 && k.charAt(0) == 'g' && Character.isDigit(k.charAt(1))) {
                        int g = Integer.parseInt(k.substring(1));
                        if (g >= 0 && g < RdFx.GROUPS.length && (on || !a.custom)) out.effects().add(new Effect.GroupStanding(RdFx.GROUPS[g], on ? v : -0.7 * v));
                    }
                }
            }
        }
        if (any) {
            for (int i = 0; i < ch.length; i++) ch[i] *= sign;
            out.effects().add(new Effect.PolicyStep(a.id + (on ? "" : "-off"), a.name, FiscalClass.PURCHASES, sign * def, new double[]{1}, 4, 0, ch));
        }
        if (tariff != 0) out.effects().add(new Effect.Tariff(sign * tariff));
    }

    /**
     * A botched rollout (Resolute Desk's {@code Implement.roll}): the department that carries out policy on the issue
     * fails with a chance that grows as its readiness falls. Returns whether it failed.
     */
    boolean roll(WestWingState s, Context c, String what, int area, double w, Outcome out, DeterministicRandomManager rng) {
        int post = AREA_POST[Math.max(0, Math.min(AREA_POST.length - 1, area))];
        double p = clamp((66 - s.ready[post]) / 110, 0.02, 0.45);
        if (rng.uniform("ww.rollout") >= p) return false;
        s.failures++;
        String how = ROLLOUT[rng.nextInt("ww.rollout", ROLLOUT.length)];
        out.news().add(news(c, News.Kind.BAD, "The " + lower(what) + " is off to a rough start: " + how, POST_NAME[post] + " was not ready for it.", 1.4 * w + 0.4, null));
        Issue i = RdFx.issue(area);
        out.effects().add(new Effect.IssueStanding(i, -0.15 * w));
        out.effects().add(new Effect.Performance(-0.4 * w));
        if (rng.uniform("ww.rollout") < 0.4)
            e.trouble.add(s, c, WestWingState.Scandal.FAILURE, "The " + lower(what) + " rollout", "Reporters document " + how + " at " + POST_NAME[post] + ".", 1.5, null, false, out);
        return true;
    }

    void revoke(WestWingState s, Context c, String id, Outcome out) {
        Action a = s.action(id);
        if (a == null || !a.standing()) throw new IllegalArgumentException("Nothing to revoke");
        boolean comment = a.status == Action.COMMENT;
        apply(s, c, a, false, out);
        a.status = Action.REVOKED; a.statusWeek = s.week;
        out.effects().add(new Effect.Performance(-0.15));
        out.news().add(news(c, News.Kind.INFO, (comment ? "The administration withdraws its proposed rule: " : "The president revokes ") + lower(a.name), null, 0.8, "Executive"));
        if (a.kind == Action.EMERGENCY && a.def != null) {
            int k = emergencyIndex(e.data.action(a.def));
            if (k >= 0) {
                s.emergency[k] = false;
                for (Action x : s.actions) if (x.needs == k && x.status == Action.LIVE) { apply(s, c, x, false, out); x.status = Action.REVOKED; x.statusWeek = s.week; }
            }
        }
    }

    /** Rules whose comment period is over take effect; a hostile Congress may move to overturn them. */
    void week(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        for (Action a : s.actions) {
            if (a.status != Action.COMMENT || s.week < a.finalWeek) continue;
            a.status = Action.LIVE; a.statusWeek = s.week;
            out.news().add(news(c, News.Kind.GOOD, "A new rule takes effect: " + lower(a.name), null, 1.2, "Executive"));
            react(s, c, a, 0.6, out);
            takeEffect(s, c, a, out, rng);
            cra(s, c, a, rng);
        }
    }

    // ---- Congress pushes back ----------------------------------------------------------------------------------------

    /** The Congressional Review Act: with both chambers against the president, a resolution to overturn a partisan rule. */
    private void cra(WestWingState s, Context c, Action a, DeterministicRandomManager rng) {
        if (!c.opposesBoth() || a.lean != c.president()) return;
        if (rng.uniform("ww.cra") > 0.6) return;
        e.ask(s, "cra", "Congress votes to overturn your rule", "Both chambers passed a resolution under the Congressional Review Act to overturn the "
                        + lower(a.name) + ". You can veto it; they are unlikely to override.",
                new String[]{"Veto the resolution", "Sign it and let the rule go"},
                new String[]{"Legislative director: \"They don't have the votes to override.\"", "Chief of staff: \"It isn't worth the fight.\""}, null, a.id, 0, 1);
    }

    void craAnswer(WestWingState s, Context c, String id, int k, Outcome out, DeterministicRandomManager rng) {
        Action a = s.action(id);
        if (a == null || a.status != Action.LIVE) return;
        if (k == 1 || rng.uniform("ww.cra") < 0.05) {
            apply(s, c, a, false, out);
            a.status = Action.UNDONE; a.statusWeek = s.week;
            out.news().add(news(c, News.Kind.BAD, "Congress overturns the " + lower(a.name), k == 0 ? "Enough members of your party crossed over to override the veto." : null, 1.6, null));
        } else out.news().add(news(c, News.Kind.INFO, "The president vetoes Congress's attempt to overturn the " + lower(a.name), null, 0.8, "Executive"));
    }

    /**
     * Ending a national emergency takes both chambers. Resolute Desk let a resolution through when the president held
     * the House; here it needs a Congress the president's party holds neither chamber of.
     */
    private void emergencyVote(WestWingState s, Context c, Action a, DeterministicRandomManager rng) {
        if (!c.opposesBoth()) return;
        if (rng.uniform("ww.emergency") > 0.65) return;
        e.ask(s, "emergency", "Congress votes to end your emergency", "Both chambers passed a resolution ending the national emergency you declared ("
                        + lower(a.name) + ").",
                new String[]{"Veto it", "Accept it and end the emergency"},
                new String[]{"Counsel: \"The law is on our side.\"", "Legislative director: \"Some of our own voted for it. Let it go.\""}, null, a.id, 0, 1);
    }

    void emergencyAnswer(WestWingState s, Context c, String id, int k, Outcome out) {
        Action a = s.action(id);
        if (a == null || a.status != Action.LIVE) return;
        if (k == 0) {
            out.effects().add(new Effect.Performance(-0.2));
            out.news().add(news(c, News.Kind.INFO, "The president vetoes the resolution ending the emergency", null, 1.0, null));
        } else revoke(s, c, id, out);
    }

    static String lower(String s) { return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1); }
}
