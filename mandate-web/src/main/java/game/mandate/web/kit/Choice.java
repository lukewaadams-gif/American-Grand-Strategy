package game.mandate.web.kit;

/** A large option: a number key, a title, a wrapped explanation and a risk tag ("about 53%", "Gamble: 40%"). */
public class Choice extends El {
    private final ActionListener action;
    private boolean enabled = true;
    private final String title;

    public Choice(String key, String title, String detail, String tag, Color tagColor, ActionListener a) {
        super("button");
        this.action = a;
        this.title = title;
        attr("type", "button");
        cls("choice");
        El head = El.div("choice-head");
        if (key != null) head.add(El.span("choice-key").text(key).attr("aria-hidden", "true"));
        head.add(El.span("choice-title").text(title));
        if (tag != null) head.add(El.span("tag").text(tag).css("--tone", (tagColor == null ? Theme.get().info : tagColor).css()).css("--pill", Theme.get().pillFill(tagColor == null ? Theme.get().info : tagColor).css()));
        add(head);
        if (detail != null && !detail.isEmpty()) add(El.div("choice-detail").text(detail));
        label((key == null ? "" : "Option " + key + ": ") + title + (tag == null ? "" : " (" + tag + ")") + (detail == null || detail.isEmpty() ? "" : ". " + detail));
        on("click", ev -> { if (enabled && action != null) action.actionPerformed(ev); });
    }

    public Choice selected(boolean on) { toggle("selected", on); attr("aria-pressed", on ? "true" : "false"); return this; }

    public Choice disabledBecause(String reason) {
        enabled = reason == null;
        attr("aria-disabled", enabled ? null : "true");
        toggle("disabled", !enabled);
        if (reason != null) tip(reason);
        return this;
    }

    public void setEnabled(boolean on) { disabledBecause(on ? null : "Not available"); }

    public boolean isEnabled() { return enabled; }
    public String getText() { return title; }

    public void doClick() { if (enabled && action != null) action.actionPerformed(null); }
}
