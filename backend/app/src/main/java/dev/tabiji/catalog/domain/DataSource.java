package dev.tabiji.catalog.domain;

/** Provenance of catalogue data — shown to users so that samples are never mistaken for facts. */
public enum DataSource {
    /** Approximate values bundled for demonstration; not checked against an official source. */
    SAMPLE_UNVERIFIED,
    /** Checked against the operator's official website on {@code verified_on}. */
    OFFICIAL,
    /** Imported from an open-data provider (e.g. OpenStreetMap, ODPT). */
    OPEN_DATA
}
