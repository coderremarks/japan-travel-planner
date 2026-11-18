package dev.tabiji.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** A national holiday from the Cabinet Office of Japan's official open data. */
@Entity
@Table(name = "japanese_holiday")
public class JapaneseHoliday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate date;

    @Column(name = "name_ja", nullable = false)
    private String nameJa;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    protected JapaneseHoliday() {
        // for JPA
    }

    public LocalDate date() {
        return date;
    }

    public String nameJa() {
        return nameJa;
    }

    public String nameEn() {
        return nameEn;
    }
}
