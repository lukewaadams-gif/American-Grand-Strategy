package game.mandate.web.kit;

import org.teavm.jso.JSObject;

/**
 * The page's layers: the screen, toasts (in a live region, so screen readers announce them), and one modal dialog.
 * A modal traps nothing the player needs: Escape closes a closable one, and focus returns where it was.
 */
public class Stage extends El {
    private final El baseHolder = El.div("stage-base");
    private final El toasts = El.div("toasts");
    private El modal;
    private El base;
    private Runnable onModalClosed;
    private boolean reduceMotion;

    public Stage(JSObject host) {
        super(host);
        cls("stage");
        toasts.attr("aria-live", "polite").attr("aria-relevant", "additions").role("status");
        add(baseHolder, toasts);
    }

    public void setBase(El c) {
        baseHolder.clear();
        base = c;
        baseHolder.add(c);
    }

    public El base() { return base; }

    public void reduceMotion(boolean on) { reduceMotion = on; toggle("reduce-motion", on); }

    public void showModal(El content, int maxWidth, boolean closable, Runnable onClosed) {
        closeModalQuietly();
        El scrim = El.div("scrim");
        El dialog = El.div("dialog").role("dialog").attr("aria-modal", "true").css("max-width", maxWidth + "px");
        String name = content.attr("aria-label");
        if (name != null) dialog.label(name);
        dialog.add(content);
        scrim.add(dialog);
        if (closable) {
            scrim.on("click", ev -> { if (Dom.contains(dialog.node, eventTarget(ev))) return; closeModal(); });
            scrim.attr("data-closable", "true");
        }
        modal = scrim;
        onModalClosed = onClosed;
        add(scrim);
        Dom.later(() -> Dom.focusFirst(dialog.node), 0);
    }

    @org.teavm.jso.JSBody(params = "ev", script = "return ev.target;")
    static native JSObject eventTarget(JSObject ev);

    public boolean modalOpen() { return modal != null; }

    public El modal() { return modal; }

    public boolean modalClosable() { return modal != null && "true".equals(modal.attr("data-closable")); }

    void closeModalQuietly() {
        if (modal == null) return;
        Dom.remove(modal.node);
        kids.remove(modal);
        modal = null;
        onModalClosed = null;
    }

    public void closeModal() {
        if (modal == null) return;
        Dom.remove(modal.node);
        kids.remove(modal);
        modal = null;
        Runnable r = onModalClosed;
        onModalClosed = null;
        if (r != null) r.run();
    }

    public void toast(String title, String body, Color tone) {
        Theme t = Theme.get();
        Card c = new Card(null).stripe(tone);
        c.cls("toast");
        // A toast never hides the game for long: it can be dismissed, and at most three show at once.
        Btn close = Btn.quiet("\u00d7", e -> { Dom.remove(c.node); toasts.kids.remove(c); });
        close.cls("toast-close").label("Dismiss: " + title).tip("Dismiss (it stays in the Timeline)");
        c.with(Layouts.side(t.u(2), null, Text.strong(title, Theme.Size.BODY), close));
        if (body != null && !body.isBlank()) c.with(Text.muted(body).lines(3));
        c.label(title + (body == null ? "" : ". " + body));
        Dom.append(toasts.node, c.node);
        toasts.kids.add(c);
        while (toasts.kids.size() > 3) { El old = toasts.kids.remove(0); Dom.remove(old.node); }
        Dom.later(() -> { Dom.remove(c.node); toasts.kids.remove(c); }, reduceMotion ? 9000 : 6500);
    }

    public int toastCount() { return toasts.kids.size(); }
}
