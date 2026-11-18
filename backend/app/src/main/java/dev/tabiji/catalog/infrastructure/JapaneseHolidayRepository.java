package dev.tabiji.catalog.infrastructure;

import dev.tabiji.catalog.domain.JapaneseHoliday;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JapaneseHolidayRepository extends JpaRepository<JapaneseHoliday, LocalDate> {

    List<JapaneseHoliday> findByDateBetween(LocalDate from, LocalDate to);
}
