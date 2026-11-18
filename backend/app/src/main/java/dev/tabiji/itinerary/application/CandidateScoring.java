package dev.tabiji.itinerary.application;

import dev.tabiji.catalog.domain.PlaceCategory;
import java.util.Set;

/**
 * How the traveller's choices become optimizer scores — small integers, so that every decision can be
 * explained in one sentence:
 *
 * <ul>
 *   <li>A place the traveller added scores {@value #CHOSEN} (+{@value #INTEREST_BONUS} if it matches an
 *       interest). If not everything fits, places matching their interests are kept first.</li>
 *   <li>A suggested place scores {@value #SUGGESTED} + {@value #INTEREST_BONUS}: it fills free time but
 *       can never displace a place the traveller chose.</li>
 *   <li>Must-visits are hard constraints; their score is irrelevant to the outcome.</li>
 * </ul>
 */
public final class CandidateScoring {

    static final int CHOSEN = 10;
    static final int SUGGESTED = 2;
    static final int INTEREST_BONUS = 3;

    private CandidateScoring() {}

    public static int chosen(PlaceCategory category, Set<PlaceCategory> interests) {
        return CHOSEN + (interests.contains(category) ? INTEREST_BONUS : 0);
    }

    public static int suggested(PlaceCategory category, Set<PlaceCategory> interests) {
        return SUGGESTED + (interests.contains(category) ? INTEREST_BONUS : 0);
    }
}
