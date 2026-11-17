package dev.tabiji.optimizer.travel.transit;

/** Coarse service categories; each gets its own speed, waiting-time and fare assumptions. */
public enum LineType {
    /** Metro lines (Tokyo Metro, Toei, Osaka Metro, Kyoto Municipal Subway). */
    SUBWAY,
    /** Frequent urban rail inside a city (JR Yamanote, Osaka Loop, Yurikamome). */
    URBAN_RAIL,
    /** Rapid / regional lines between cities (JR Kyoto Line, Keihan, Hankyu, Kintetsu, Yokosuka Line). */
    REGIONAL_RAIL,
    /** High-speed rail. */
    SHINKANSEN
}
