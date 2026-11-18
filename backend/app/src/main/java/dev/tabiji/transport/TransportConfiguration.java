package dev.tabiji.transport;

import dev.tabiji.optimizer.travel.DistanceModel;
import dev.tabiji.optimizer.travel.DistanceModelEstimator;
import dev.tabiji.optimizer.travel.ModeChoicePolicy;
import dev.tabiji.optimizer.travel.TravelEstimator;
import dev.tabiji.optimizer.travel.TravelMatrixBuilder;
import dev.tabiji.optimizer.travel.transit.TransitAssumptions;
import dev.tabiji.optimizer.travel.transit.TransitNetwork;
import dev.tabiji.optimizer.travel.transit.TransitNetworkEstimator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Wires the travel-estimation adapters behind the optimizer's {@link TravelEstimator} port. Replacing a
 * provider (e.g. a timetable-based transit API) means registering a different {@code TravelEstimator}
 * bean for that mode — nothing in the optimizer or the planning service changes.
 */
@Configuration(proxyBeanMethods = false)
public class TransportConfiguration {

    @Bean
    TransitNetwork sampleTransitNetwork(
            @Value("classpath:transit/sample-network.json") Resource networkFile, JsonMapper jsonMapper) {
        return SampleTransitNetworkLoader.load(networkFile, jsonMapper);
    }

    @Bean
    TravelEstimator transitEstimator(TransitNetwork network) {
        return new TransitNetworkEstimator(network, TransitAssumptions.DEFAULT);
    }

    @Bean
    TravelEstimator walkAndTaxiEstimator() {
        return new DistanceModelEstimator(DistanceModel.DEFAULT);
    }

    @Bean
    TravelMatrixBuilder travelMatrixBuilder(List<TravelEstimator> estimators) {
        return new TravelMatrixBuilder(estimators, ModeChoicePolicy.DEFAULT);
    }

    @Bean
    TransitLineNames transitLineNames(TransitNetwork network) {
        return new TransitLineNames(network);
    }
}
