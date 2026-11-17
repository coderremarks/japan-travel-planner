package dev.tabiji.optimizer.geo;

/**
 * An immutable WGS-84 coordinate.
 *
 * <p>Distances use the haversine formula on a spherical Earth. The error against the ellipsoidal
 * (Vincenty) distance is below 0.5&nbsp;%, which is far smaller than the uncertainty of any travel-time
 * estimate built on top of it.
 */
public record GeoPoint(double latitude, double longitude) {

    /** Mean Earth radius as defined by the IUGG, in metres. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    public GeoPoint {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be within [-90, 90]: " + latitude);
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be within [-180, 180]: " + longitude);
        }
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    /** Great-circle distance to {@code other} in metres. */
    public double distanceMetersTo(GeoPoint other) {
        double phi1 = Math.toRadians(latitude);
        double phi2 = Math.toRadians(other.latitude);
        double deltaPhi = phi2 - phi1;
        double deltaLambda = Math.toRadians(other.longitude - longitude);
        double sinHalfPhi = Math.sin(deltaPhi / 2.0);
        double sinHalfLambda = Math.sin(deltaLambda / 2.0);
        double a = sinHalfPhi * sinHalfPhi + Math.cos(phi1) * Math.cos(phi2) * sinHalfLambda * sinHalfLambda;
        // Clamp guards against a > 1 caused by floating-point rounding for antipodal points.
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
