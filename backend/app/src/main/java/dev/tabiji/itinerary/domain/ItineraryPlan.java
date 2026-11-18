package dev.tabiji.itinerary.domain;

import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The latest generated plan of an itinerary as an immutable JSONB snapshot (ADR-0006), with the
 * fingerprint of the inputs it was computed from.
 */
@Entity
@Table(name = "itinerary_plan")
public class ItineraryPlan {

    @Id
    @Column(name = "itinerary_id")
    private UUID itineraryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Strategy strategy;

    @Column(name = "input_fingerprint", nullable = false, length = 64)
    private String inputFingerprint;

    @Column(name = "algorithm_version", nullable = false, length = 32)
    private String algorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "plan_json", nullable = false)
    private String planJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ItineraryPlan() {
        // for JPA
    }

    public ItineraryPlan(
            UUID itineraryId,
            PlanStatus status,
            Strategy strategy,
            String inputFingerprint,
            String algorithmVersion,
            String planJson,
            Instant generatedAt) {
        this.itineraryId = itineraryId;
        this.status = status;
        this.strategy = strategy;
        this.inputFingerprint = inputFingerprint;
        this.algorithmVersion = algorithmVersion;
        this.planJson = planJson;
        this.generatedAt = generatedAt;
    }

    public UUID itineraryId() {
        return itineraryId;
    }

    public PlanStatus status() {
        return status;
    }

    public Strategy strategy() {
        return strategy;
    }

    public String inputFingerprint() {
        return inputFingerprint;
    }

    public String algorithmVersion() {
        return algorithmVersion;
    }

    public String planJson() {
        return planJson;
    }

    public Instant generatedAt() {
        return generatedAt;
    }
}
