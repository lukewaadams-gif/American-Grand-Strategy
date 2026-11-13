package game.mandate.web;

import game.mandate.web.kit.Dom;
import org.teavm.jso.JSObject;

/** The browser entry point: the game in the page's {@code #app} element. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        JSObject host = Dom.byId("app");
        Dom.clear(host);
        new App(new Session("mandate."), host);
    }
}
