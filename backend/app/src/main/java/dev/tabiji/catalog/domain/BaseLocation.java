package dev.tabiji.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Where a traveller stays (station / hotel area); every trip day starts and ends here. */
@Entity
@Table(name = "base_location")
public class BaseLocation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String slug;

    @Column(name = "area_code", nullable = false)
    private String areaCode;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ja", nullable = false)
    private String nameJa;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    protected BaseLocation() {
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

    public String nameEn() {
        return nameEn;
    }

    public String nameJa() {
        return nameJa;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }
}
