package dev.tabiji.itinerary.infrastructure;

import dev.tabiji.itinerary.domain.ItineraryPlan;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Plans are only ever loaded after the owning itinerary passed the ownership check. */
public interface ItineraryPlanRepository extends JpaRepository<ItineraryPlan, UUID> {

    List<ItineraryPlan> findByItineraryIdIn(Collection<UUID> itineraryIds);
}
