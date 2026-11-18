package dev.tabiji.catalog.infrastructure;

import dev.tabiji.catalog.domain.BaseLocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BaseLocationRepository extends JpaRepository<BaseLocation, UUID> {

    List<BaseLocation> findAllByOrderByAreaCodeAscNameEnAsc();

    List<BaseLocation> findByAreaCodeOrderByNameEnAsc(String areaCode);
}
