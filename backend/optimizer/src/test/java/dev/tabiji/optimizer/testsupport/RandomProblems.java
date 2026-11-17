package dev.tabiji.optimizer.testsupport;

import dev.tabiji.optimizer.problem.Candidate;
import dev.tabiji.optimizer.problem.PlanningProblem;
import dev.tabiji.optimizer.problem.TimeWindow;
import dev.tabiji.optimizer.problem.TripDay;
import dev.tabiji.optimizer.travel.Leg;
import dev.tabiji.optimizer.travel.LegSource;
import dev.tabiji.optimizer.travel.TravelMatrix;
import dev.tabiji.optimizer.travel.TravelMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Seeded generator of realistic-but-random planning problems: a hotel plus places scattered over a
 * city-sized area, opening hours with closed days and lunch breaks, must-visits, budgets, a few missing
 * routes. Every instance is reproducible from its seed, so a failing property test can be replayed.
 */
public final class RandomProblems {

    private RandomProblems() {}

    public static PlanningProblem generate(long seed, int candidates, int days) {
        SplittableRandom random = new SplittableRandom(seed);
        int locations = candidates + 1;
        double[] x = new double[locations];
        double[] y = new double[locations];
        for (int i = 0; i < locations; i++) {
            x[i] = random.nextDouble() * 10.0;
            y[i] = random.nextDouble() * 10.0;
        }
        TravelMatrix.Builder matrix = TravelMatrix.builder(locations);
        for (int i = 0; i < locations; i++) {
            for (int j = 0; j < locations; j++) {
                if (i == j) {
                    continue;
                }
                boolean missingRoute = i != 0 && j != 0 && random.nextInt(50) == 0;
                if (missingRoute) {
                    continue;
                }
                double km = Math.hypot(x[i] - x[j], y[i] - y[j]);
                boolean walk = km < 1.5;
                int minutes = walk ? (int) Math.ceil(km * 15) : 8 + (int) Math.round(km * 3.5);
                int cost = walk ? 0 : 170 + (int) Math.round(km * 15) / 10 * 10;
                matrix.set(
                        i,
                        j,
                        Leg.estimate(
                                walk ? TravelMode.WALK : TravelMode.TRANSIT,
                                minutes,
                                cost,
                                (int) (km * 1000),
                                LegSource.DISTANCE_MODEL));
            }
        }

        List<TripDay> tripDays = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            int start = 8 * 60 + random.nextInt(5) * 30;
            int end = 17 * 60 + random.nextInt(9) * 30;
            tripDays.add(new TripDay(TimeWindow.of(start, end), 0, 0));
        }

        List<Candidate> places = new ArrayList<>();
        for (int c = 0; c < candidates; c++) {
            List<List<TimeWindow>> hours = new ArrayList<>();
            for (int d = 0; d < days; d++) {
                hours.add(randomHours(random));
            }
            int visit = 30 + random.nextInt(8) * 15;
            int admission = random.nextInt(4) == 0 ? 0 : random.nextInt(16) * 100;
            int score = 1 + random.nextInt(10);
            boolean mandatory = random.nextInt(6) == 0;
            places.add(new Candidate("p" + c, c + 1, visit, admission, score, mandatory, hours));
        }
        int maxVisits = 2 + random.nextInt(5);
        long budget = random.nextInt(3) == 0 ? PlanningProblem.UNLIMITED_BUDGET : 1_000 + random.nextInt(60) * 100L;
        return new PlanningProblem(places, tripDays, matrix.build(), maxVisits, budget);
    }

    private static List<TimeWindow> randomHours(SplittableRandom random) {
        int kind = random.nextInt(10);
        if (kind == 0) {
            return List.of();
        }
        if (kind == 1) {
            return List.of(TimeWindow.allDay());
        }
        int open = 7 * 60 + random.nextInt(9) * 30;
        int close = Math.min(23 * 60, open + 120 + random.nextInt(19) * 30);
        if (kind == 2 && close - open > 300) {
            int lunch = open + 180;
            return List.of(TimeWindow.of(open, lunch), TimeWindow.of(lunch + 60, close));
        }
        return List.of(TimeWindow.of(open, close));
    }
}
