package dev.tabiji.optimizer.solver;

/**
 * Lexicographic objective — "greater is better":
 * <ol>
 *   <li>more must-visit places scheduled,</li>
 *   <li>higher total preference score,</li>
 *   <li>fewer travel minutes,</li>
 *   <li>lower total cost.</li>
 * </ol>
 * A lexicographic order needs no hand-tuned weights, and each comparison can be explained to the user in
 * one sentence ("this plan visits all your must-sees; among those it fits the most of your interests;
 * among those it travels least").
 */
record Objective(int mandatoryScheduled, long score, long travelMinutes, long costYen)
        implements Comparable<Objective> {

    @Override
    public int compareTo(Objective other) {
        int byMandatory = Integer.compare(mandatoryScheduled, other.mandatoryScheduled);
        if (byMandatory != 0) {
            return byMandatory;
        }
        int byScore = Long.compare(score, other.score);
        if (byScore != 0) {
            return byScore;
        }
        int byTravel = Long.compare(other.travelMinutes, travelMinutes);
        if (byTravel != 0) {
            return byTravel;
        }
        return Long.compare(other.costYen, costYen);
    }

    boolean isBetterThan(Objective other) {
        return compareTo(other) > 0;
    }
}
