package game.mandate.core.events;

import game.mandate.core.diplomacy.HotSpot;

import java.time.LocalDate;

/** The read-only facts event conditions may test. Events see the world; they never change it directly. */
public interface WorldView {
    LocalDate date();
    int playerDiscipline();
    boolean unifiedGovernment();
    double tension(HotSpot h);
}
