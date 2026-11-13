package game.mandate.web.kit;

/** What a button does when pressed (the event is the browser's, rarely needed). */
@FunctionalInterface
public interface ActionListener {
    void actionPerformed(Object event);
}
