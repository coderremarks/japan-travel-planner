package dev.tabiji.itinerary.infrastructure;

import dev.tabiji.itinerary.domain.Itinerary;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Every lookup that serves a user request is scoped by owner. There is intentionally no
 * {@code findById} usage outside of security logging (see {@code ItineraryService#load}).
 */
public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    Optional<Itinerary> findByIdAndOwnerId(UUID id, UUID ownerId);

    Page<Itinerary> findByOwnerId(UUID ownerId, Pageable pageable);

    long countByOwnerId(UUID ownerId);
}
