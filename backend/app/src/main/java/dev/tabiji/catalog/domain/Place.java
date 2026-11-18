package dev.tabiji.catalog.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A visitable place with bilingual texts, location, typical visit length, approximate admission and
 * opening rules. Read-only reference data, maintained through Flyway migrations.
 */
@Entity
@Table(name = "place")
public class Place {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "area_code", nullable = false)
    private String areaCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlaceCategory category;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ja", nullable = false)
    private String nameJa;

    @Column(name = "summary_en", nullable = false)
    private String summaryEn;

    @Column(name = "summary_ja", nullable = false)
    private String summaryJa;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "visit_minutes", nullable = false)
    private int visitMinutes;

    @Column(name = "admission_yen", nullable = false)
    private int admissionYen;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_policy", nullable = false)
    private HolidayPolicy holidayPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false)
    private DataSource dataSource;

    @Column(name = "verified_on")
    private LocalDate verifiedOn;

    @ElementCollection
    @CollectionTable(name = "place_opening_hours", joinColumns = @JoinColumn(name = "place_id"))
    private Set<WeeklyHours> openingHours = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "place_closure", joinColumns = @JoinColumn(name = "place_id"))
    private Set<Closure> closures = new HashSet<>();

    protected Place() {
        // for JPA
    }

    public UUID id() {
        return id;
    }

    public String slug() {
        return slug;
    }

    public String areaCode() {
        return areaCode;
    }

    public PlaceCategory category() {
        return category;
    }

    public String nameEn() {
        return nameEn;
    }

    public String nameJa() {
        return nameJa;
    }

    public String summaryEn() {
        return summaryEn;
    }

    public String summaryJa() {
        return summaryJa;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    public int visitMinutes() {
        return visitMinutes;
    }

    public int admissionYen() {
        return admissionYen;
    }

    public HolidayPolicy holidayPolicy() {
        return holidayPolicy;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public LocalDate verifiedOn() {
        return verifiedOn;
    }

    public Set<WeeklyHours> openingHours() {
        return Set.copyOf(openingHours);
    }

    public Set<Closure> closures() {
        return Set.copyOf(closures);
    }
}
