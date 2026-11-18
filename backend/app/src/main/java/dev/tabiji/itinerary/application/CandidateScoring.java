package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.domain.PlaceCategory;
import java.util.Set;

/**
 * How the traveller's choices become optimizer scores. After scheduling every must-visit, the optimizer
 * maximises the total score (then minimises travel time and cost), so the numbers encode priorities:
 *
 * <ul>
 *   <li>A place the traveller added scores {@value #CHOSEN}, plus {@value #INTEREST_BONUS} if it matches
 *       one of their interests.</li>
 *   <li>A suggested place scores {@value #SUGGESTED}, plus {@value #INTEREST_BONUS} for an interest
 *       match.</li>
 *   <li>{@code CHOSEN} is larger than everything a plan could gain by dropping one chosen place: the
 *       interest bonuses of all other stops plus the largest possible set of suggestions
 *       ({@link #MAX_SUGGESTIONS}). A plan with more of the traveller's own places therefore always
 *       wins, so suggestions only ever fill free time and can never displace a chosen place. Among
 *       plans with the same number of chosen places, interest matches and suggestions decide.</li>
 *   <li>Must-visits are hard constraints; their score does not change the outcome.</li>
 * </ul>
 *
 * {@code CandidateScoringTest} checks the inequality and demonstrates it on a concrete trip.
 */
public final class CandidateScoring {

    static final int CHOSEN = 1_000;
    static final int SUGGESTED = 2;
    static final int INTEREST_BONUS = 3;

    /** Upper bound for {@code app.planning.max-suggestions}; the guarantee above depends on it. */
    public static final int MAX_SUGGESTIONS = 50;

    private CandidateScoring() {}

    public static int chosen(PlaceCategory category, Set<PlaceCategory> interests) {
        return CHOSEN + (interests.contains(category) ? INTEREST_BONUS : 0);
    }

    public static int suggested(PlaceCategory category, Set<PlaceCategory> interests) {
        return SUGGESTED + (interests.contains(category) ? INTEREST_BONUS : 0);
    }
}
