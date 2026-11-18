package dev.tabiji.catalog.infrastructure;

import dev.tabiji.catalog.domain.Area;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, String> {

    List<Area> findAllByOrderBySortOrderAsc();
}
