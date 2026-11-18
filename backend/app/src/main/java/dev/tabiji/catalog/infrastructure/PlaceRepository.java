package dev.tabiji.catalog.infrastructure;

import dev.tabiji.catalog.domain.Place;
import dev.tabiji.catalog.domain.PlaceCategory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** All queries are derived or Criteria-based (see {@link PlaceSpecifications}) — no string-built SQL. */
public interface PlaceRepository extends JpaRepository<Place, UUID>, JpaSpecificationExecutor<Place> {

    List<Place> findByIdIn(Collection<UUID> ids);

    List<Place> findByAreaCodeInAndCategoryIn(
            Collection<String> areaCodes, Collection<PlaceCategory> categories, Sort sort);
}
