package dev.tabiji.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A destination area such as Tokyo or Kyoto. Read-only reference data. */
@Entity
@Table(name = "area")
public class Area {

    @Id
    private String code;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_ja", nullable = false)
    private String nameJa;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lng", nullable = false)
    private double centerLng;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Area() {
        // for JPA
    }

    public String code() {
        return code;
    }

    public String nameEn() {
        return nameEn;
    }

    public String nameJa() {
        return nameJa;
    }

    public double centerLat() {
        return centerLat;
    }

    public double centerLng() {
        return centerLng;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
