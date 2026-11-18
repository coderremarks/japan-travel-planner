package dev.tabiji.itinerary.application;

import dev.tabiji.itinerary.domain.InputFingerprint;
import dev.tabiji.itinerary.domain.Itinerary;
import dev.tabiji.itinerary.domain.ItineraryPlan;
import dev.tabiji.itinerary.domain.PlanDocument;
import dev.tabiji.itinerary.infrastructure.ItineraryPlanRepository;
import dev.tabiji.optimizer.problem.Strategy;
import dev.tabiji.optimizer.solution.PlanningResult;
import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import dev.tabiji.optimizer.solver.PlanValidator;
import dev.tabiji.security.ratelimit.RateLimiter;
import dev.tabiji.security.ratelimit.RateLimiter.Policy;
import dev.tabiji.shared.error.ApiException;
import dev.tabiji.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs the optimizer for an itinerary the caller owns and stores the result as a snapshot. The
 * optimizer's output is re-checked by the independent {@link PlanValidator} before it is shown to
 * anyone: a plan that violates opening hours or the budget is a bug, never a result.
 */
@Service
public class PlanningService {

    public static final String ALGORITHM_VERSION = "tabiji-ils-1";

    private static final Logger log = LoggerFactory.getLogger(PlanningService.class);

    private final ItineraryService itineraries;
    private final ItineraryPlanRepository plans;
    private final PlanningInputFactory inputs;
    private final PlanAssembler assembler;
    private final ItineraryOptimizer optimizer;
    private final RateLimiter rateLimiter;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public record PlanView(PlanDocument plan, boolean stale) {}

    PlanningService(
            ItineraryService itineraries,
            ItineraryPlanRepository plans,
            PlanningInputFactory inputs,
            PlanAssembler assembler,
            ItineraryOptimizer optimizer,
            RateLimiter rateLimiter,
            JsonMapper jsonMapper,
            Clock clock) {
        this.itineraries = itineraries;
        this.plans = plans;
        this.inputs = inputs;
        this.assembler = assembler;
        this.optimizer = optimizer;
        this.rateLimiter = rateLimiter;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional
    public PlanView plan(UUID ownerId, UUID itineraryId, Strategy strategy) {
        if (strategy == Strategy.EXACT) {
            // Exponential; reserved for tests and benchmarks.
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unsupported strategy.");
        }
        Itinerary itinerary = itineraries.load(ownerId, itineraryId);
        rateLimiter.check(Policy.PLAN_PER_USER, ownerId.toString());

        PlanningInput input = inputs.build(itinerary, strategy);
        if (input.candidates().isEmpty()) {
            throw new ApiException(ErrorCode.NOTHING_TO_PLAN);
        }
        PlanningResult result = optimizer.optimize(input.problem(), strategy);
        assertValid(input, result);
        PlanningResult baseline = null;
        if (strategy == Strategy.OPTIMIZE) {
            baseline = optimizer.optimize(input.problem(), Strategy.KEEP_ORDER);
        }

        Instant now = clock.instant();
        PlanDocument document = assembler.assemble(itinerary.id(), input, result, baseline, now, ALGORITHM_VERSION);
        plans.save(new ItineraryPlan(
                itinerary.id(),
                result.status(),
                strategy,
                InputFingerprint.of(itinerary),
                ALGORITHM_VERSION,
                jsonMapper.writeValueAsString(document),
                now));
        log.info(
                "plan.generated itineraryId={} strategy={} status={} candidates={} elapsedMs={}",
                itinerary.id(),
                strategy,
                result.status(),
                input.candidates().size(),
                result.stats().elapsedMillis());
        return new PlanView(document, false);
    }

    @Transactional(readOnly = true)
    public PlanView latest(UUID ownerId, UUID itineraryId) {
        Itinerary itinerary = itineraries.load(ownerId, itineraryId);
        ItineraryPlan stored =
                plans.findById(itinerary.id()).orElseThrow(() -> new ApiException(ErrorCode.PLAN_NOT_FOUND));
        PlanDocument document = jsonMapper.readValue(stored.planJson(), PlanDocument.class);
        boolean stale = !stored.inputFingerprint().equals(InputFingerprint.of(itinerary));
        return new PlanView(document, stale);
    }

    private static void assertValid(PlanningInput input, PlanningResult result) {
        List<String> violations = PlanValidator.violations(input.problem(), result);
        if (!violations.isEmpty()) {
            log.error("Optimizer produced an invalid plan: {}", violations);
            throw new IllegalStateException("optimizer produced an invalid plan");
        }
    }
}
