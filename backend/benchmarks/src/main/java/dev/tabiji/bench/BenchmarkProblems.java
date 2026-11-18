package dev.tabiji.bench;

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
 * Seeded, city-scale planning problems for benchmarks: places scattered over 10 × 10 km around a hotel,
 * walking below 1.5 km and transit above, realistic opening hours with closed days and lunch breaks,
 * about one in six places a must-visit, and a budget that binds in a third of the instances.
 */
final class BenchmarkProblems {

    private BenchmarkProblems() {}

    static PlanningProblem generate(long seed, int candidates, int days) {
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
                if (i != j) {
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
        }
        List<TripDay> tripDays = new ArrayList<>();
        for (int d = 0; d < days; d++) {
            tripDays.add(new TripDay(TimeWindow.between(9, 0, 19, 0), 0, 0));
        }
        List<Candidate> places = new ArrayList<>();
        for (int c = 0; c < candidates; c++) {
            List<List<TimeWindow>> hours = new ArrayList<>();
            for (int d = 0; d < days; d++) {
                hours.add(randomHours(random));
            }
            int visit = 30 + random.nextInt(8) * 15;
            int admission = random.nextInt(4) == 0 ? 0 : random.nextInt(16) * 100;
            boolean mandatory = random.nextInt(6) == 0;
            places.add(new Candidate("p" + c, c + 1, visit, admission, 1 + random.nextInt(10), mandatory, hours));
        }
        long budget = random.nextInt(3) == 0 ? PlanningProblem.UNLIMITED_BUDGET : 2_000L + days * 2_500L;
        return new PlanningProblem(places, tripDays, matrix.build(), 2 + random.nextInt(4), budget);
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
