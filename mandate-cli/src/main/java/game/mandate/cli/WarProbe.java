package game.mandate.cli;

import game.mandate.core.data.Tuning;
import game.mandate.core.military.MilitaryCatalog;
import game.mandate.core.military.MilitaryEngine;
import game.mandate.core.military.MilitaryParams;
import game.mandate.core.military.OperationalTheaterResolver;
import game.mandate.core.military.TheaterOutcome;
import game.mandate.core.military.TheaterScenario;
import game.mandate.core.rng.DeterministicRandomManager;

import java.util.Map;
import java.util.TreeMap;

/** Calibration report for the operational war model ({@code ./mandate war [runs]}): outcomes and losses by commitment and deterrence. */
final class WarProbe {
    private WarProbe() {}

    static void run(int runs) {
        MilitaryEngine m = new MilitaryEngine(MilitaryParams.from(Tuning.defaults()), MilitaryCatalog.load());
        System.out.printf("%d wars per row; Japanese basing, one month's warning, Defense competence 70%n", runs);
        for (TheaterScenario.Commitment c : TheaterScenario.Commitment.values()) for (double det : new double[]{25, 50, 80}) {
            Map<String, Integer> status = new TreeMap<>();
            double killed = 0, weeks = 0, ships = 0, carriers = 0, aircraft = 0, munitions = 0;
            DeterministicRandomManager r = new DeterministicRandomManager(5);
            for (int i = 0; i < runs; i++) {
                TheaterOutcome o = new OperationalTheaterResolver(m).resolve(new TheaterScenario(det, 100, true, 1, c, 70), r);
                status.merge(o.status().name(), 1, Integer::sum);
                killed += o.usKilled(); weeks += o.durationWeeks(); ships += o.shipsLost(); carriers += o.carriersLost();
                aircraft += o.aircraftLost(); munitions += o.munitionsExpended();
            }
            System.out.printf("%-16s deterrence %3.0f  %-40s killed %5.0f  weeks %4.1f  ships %4.1f  carriers %3.1f  aircraft %4.0f  munitions %5.0f%n",
                    c, det, status, killed / runs, weeks / runs, ships / runs, carriers / runs, aircraft / runs, munitions / runs);
        }
    }
}
