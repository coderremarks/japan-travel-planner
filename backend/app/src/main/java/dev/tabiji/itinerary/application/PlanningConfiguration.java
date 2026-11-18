package dev.tabiji.itinerary.application;

import dev.tabiji.optimizer.solver.ItineraryOptimizer;
import dev.tabiji.optimizer.solver.SolverSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class PlanningConfiguration {

    /** Stateless and thread-safe, so one instance serves all requests. */
    @Bean
    ItineraryOptimizer itineraryOptimizer() {
        return new ItineraryOptimizer(SolverSettings.DEFAULT);
    }
}
