package dev.tabiji.optimizer.solution;

import java.util.Objects;

/** A candidate that is not part of the plan, with the reason. */
public record UnscheduledCandidate(String candidateId, boolean mandatory, UnscheduledReason reason) {

    public UnscheduledCandidate {
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(reason, "reason");
    }
}
