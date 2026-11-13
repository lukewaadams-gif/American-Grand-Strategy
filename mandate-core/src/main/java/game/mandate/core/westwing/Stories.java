package game.mandate.core.westwing;

import game.mandate.core.effects.Effect;
import game.mandate.core.news.News;
import game.mandate.core.news.RdFx;
import game.mandate.core.rng.DeterministicRandomManager;
import game.mandate.core.westwing.WestWingEngine.Context;
import game.mandate.core.westwing.WestWingEngine.Outcome;
import game.mandate.core.westwing.WestWingState.Storyline;

import java.util.ArrayList;
import java.util.List;

import static game.mandate.core.westwing.WestWingData.*;
import static game.mandate.core.westwing.WestWingEngine.*;

/**
 * Developing stories (Resolute Desk's stories/Storylines): a story starts when its conditions hold, gathers heat
 * week by week from what fuels it, grows a stage when it runs hot and fades when it cools; at each stage the
 * president chooses a response whose odds depend on who carries it out. A story that outgrows its last stage takes
 * over the presidency.
 */
final class Stories {
    static final double GROW = 60, FADE = 30;
    static final int REST = 104, MAX = 2;

    private final WestWingEngine e;

    Stories(WestWingEngine e) { this.e = e; }

    void week(WestWingState s, Context c, Outcome out, DeterministicRandomManager rng) {
        s.storyCool.replaceAll((k, v) -> Math.max(0, v - 1));
        for (Storyline l : new ArrayList<>(s.storylines)) if (l.status == Storyline.ACTIVE) step(s, c, l, out, rng);
        long active = s.storylines.stream().filter(l -> l.status == Storyline.ACTIVE).count();
        if (active >= MAX) return;
        int year = c.date().getYear();
        for (StoryDef d : e.data.stories()) {
            if (s.storyCool.getOrDefault(d.id(), 0) > 0 || year < d.from() || year > d.to()) continue;
            boolean running = false;
            for (Storyline l : s.storylines) if (l.def.equals(d.id()) && l.status == Storyline.ACTIVE) running = true;
            if (running) continue;
            if (RdFx.any(d.trigger(), c.var(), rng, "ww.story.p")) { start(s, c, d, out, rng); return; }
        }
    }

    private void start(WestWingState s, Context c, StoryDef d, Outcome out, DeterministicRandomManager rng) {
        Storyline l = new Storyline();
        l.def = d.id(); l.stage = 0; l.status = Storyline.ACTIVE; l.stageWeek = s.week; l.started = s.week;
        l.heat = clamp(52 + rng.gaussian("ww.story") * 4, 30, 70);
        boolean f = rng.uniform("ww.story") < 0.8;
        List<String> first = e.data.names(f ? "first_f" : "first_m"), last = e.data.names("more_lasts");
        l.who = first.get(rng.nextInt("ww.story", first.size())) + " " + last.get(rng.nextInt("ww.story", last.size()));
        l.state = STATE_CODE[rng.nextInt("ww.story", STATE_CODE.length)];
        s.storylines.add(l);
        s.record.add(c.date() + ": a developing story: " + d.name().toLowerCase());
        announce(s, c, d, l, out);
    }

    String fill(Storyline l, String text) {
        int i = java.util.Arrays.asList(STATE_CODE).indexOf(l.state);
        String name = i >= 0 ? STATE_NAME[i] : l.state;
        return text.replace("{who}", l.who).replace("{place}", name).replace("{state}", name).replace("{st}", l.state);
    }

    private void announce(WestWingState s, Context c, StoryDef d, Storyline l, Outcome out) {
        StoryStage st = d.stages().get(l.stage);
        out.news().add(news(c, l.stage == 0 ? News.Kind.BAD : News.Kind.BAD, fill(l, st.headline()), fill(l, st.story()), 1.6 + 0.7 * l.stage, null));
        int n = d.stages().size();
        List<String> opts = new ArrayList<>(), adv = new ArrayList<>();
        int best = 0;
        double bestChange = Double.MAX_VALUE;
        for (int i = 0; i < st.options().size(); i++) {
            StoryOption o = st.options().get(i);
            double p = e.odds(s, c, o.kind(), o.base());
            opts.add(fill(l, o.label()));
            adv.add(o.adviser() + ": \"" + oddsText(p) + "\"");
            double change = p * o.heatOk() + (1 - p) * (o.heatFail() + (o.base() < 1 ? 6 : 0));
            if (change < bestChange) { bestChange = change; best = i; }
        }
        String tail = d.name() + ", stage " + (l.stage + 1) + " of " + n + ". " + (l.stage == n - 1 ? "If it doesn't cool now, it will take over the presidency." : "Left alone, stories like this grow.");
        if (!opts.isEmpty())
            e.ask(s, "story", fill(l, st.headline()), fill(l, st.story()) + " " + tail, opts.toArray(new String[0]), adv.toArray(new String[0]), null,
                    l.def + "|" + l.started + "|" + l.stage, best, 1);
        if (l.stage >= 1) out.effects().add(new Effect.PresidentStrain(0, 2 + l.stage));
    }

    private void step(WestWingState s, Context c, Storyline l, Outcome out, DeterministicRandomManager rng) {
        StoryDef d = e.data.story(l.def);
        if (d == null) { l.status = Storyline.ENDED; return; }
        StoryStage st = d.stages().get(l.stage);
        l.heat = clamp(l.heat + RdFx.fuel(d.fuel(), c.var()) - 0.5 + rng.gaussian("ww.story") * 0.8, 0, 100);
        out.effects().addAll(RdFx.effects(st.fx()));
        if (s.week - l.stageWeek < st.weeks()) return;
        if (l.heat >= GROW) {
            if (l.stage + 1 < d.stages().size()) { l.stage++; l.stageWeek = s.week; l.lingers = 0; announce(s, c, d, l, out); }
            else runaway(s, c, d, l, out);
        } else if (l.heat <= FADE || l.lingers >= 2) fade(s, c, d, l, out);
        else { l.lingers++; l.stageWeek = s.week - st.weeks() + 3; }
    }

    void decide(WestWingState s, Context c, String ref, int code, Outcome out, DeterministicRandomManager rng) {
        String[] r = ref.split("\\|");
        Storyline l = null;
        for (Storyline x : s.storylines) if (x.def.equals(r[0]) && String.valueOf(x.started).equals(r[1])) l = x;
        if (l == null || l.status != Storyline.ACTIVE || l.stage != Integer.parseInt(r[2])) return;
        StoryDef d = e.data.story(l.def);
        StoryOption o = d.stages().get(l.stage).options().get(code);
        boolean ok = rng.uniform("ww.story") < e.odds(s, c, o.kind(), o.base());
        l.heat = clamp(l.heat + (ok ? o.heatOk() : o.heatFail() + (o.base() < 1 ? 6 : 0)), 0, 100);
        out.effects().addAll(RdFx.effects(ok ? o.fxOk() : o.fxFail()));
        out.news().add(news(c, ok ? News.Kind.GOOD : News.Kind.BAD, fill(l, ok ? o.newsOk() : o.newsFail()), null, 1.3, null));
        l.choices++;
    }

    private void fade(WestWingState s, Context c, StoryDef d, Storyline l, Outcome out) {
        l.status = Storyline.FADED; l.ended = s.week;
        s.storyCool.put(l.def, REST);
        out.news().add(news(c, News.Kind.GOOD, fill(l, d.fadeText()), null, 1.0, null));
        s.decisions.removeIf(x -> x.kind.equals("story") && x.ref != null && x.ref.startsWith(l.def + "|" + l.started + "|"));
    }

    private void runaway(WestWingState s, Context c, StoryDef d, Storyline l, Outcome out) {
        l.status = Storyline.RUNAWAY; l.ended = s.week;
        s.storyCool.put(l.def, 2 * REST);
        out.effects().addAll(RdFx.effects(d.endFx()));
        out.news().add(news(c, News.Kind.BAD, fill(l, d.endHead()), fill(l, d.endBody()), 4.5, null));
        out.effects().add(new Effect.PresidentStrain(0, 8));
        s.record.add(c.date() + ": " + d.name().toLowerCase() + " took over the presidency");
        e.info(s, c, "story", fill(l, d.endHead()), fill(l, d.endBody()));
        s.decisions.removeIf(x -> x.kind.equals("story") && x.ref != null && x.ref.startsWith(l.def + "|" + l.started + "|"));
    }
}
