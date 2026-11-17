package cinesmart.util;


/**
 * GeoUtil turns two points on the earth into the distance a customer would actually travel.
 *
 * Stage 10 needs this to answer one question honestly - "which cinema hall is nearest to me?" -
 * and a desktop program cannot ask a phone where it is. So the customer tells us once (they pick
 * their area), each hall carries real latitude/longitude, and the distance between them is the
 * great-circle distance on a sphere.
 *
 * The formula is the haversine one: it stays accurate for the short city-to-city distances this
 * app cares about, where the simpler flat-earth approximation drifts badly.
 */
public final class GeoUtil {

    /** Mean radius of the earth in kilometres. */
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private GeoUtil() {
        // Utility class.
    }

    /**
     * The distance in kilometres between two points.
     *
     * @return the distance, or -1 when either point is missing, so a caller can say
     *         "distance unknown" instead of printing a confident wrong number
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        if (!isRealPosition(lat1, lon1) || !isRealPosition(lat2, lon2)) {
            return -1;
        }
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** A point is usable when both numbers are real and not the "unknown" zero pair. */
    public static boolean isRealPosition(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            return false;
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }
        return latitude != 0 || longitude != 0;
    }

    /**
     * "850 m", "4.2 km" or "12 km" - the way a cinema app would say it. Anything under a kilometre
     * is rounded to the nearest 50 m, because nobody needs "837 m" and everybody needs "walking
     * distance or not".
     *
     * @param km the distance in kilometres, or a negative number when it is unknown
     */
    public static String distanceText(double km) {
        if (km < 0) {
            return "distance unknown";
        }
        if (km < 1) {
            long metres = Math.round(km * 1000 / 50.0) * 50; // nearest 50 m
            return metres + " m away";
        }
        if (km < 10) {
            return String.format("%.1f km away", km);
        }
        return String.format("%.0f km away", km);
    }
}