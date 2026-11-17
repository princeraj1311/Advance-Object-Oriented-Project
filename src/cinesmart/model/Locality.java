package cinesmart.model;


import cinesmart.util.UiUtil;

/**
 * Locality is one place a customer can say they are in: a city plus the area inside it, with real
 * coordinates.
 *
 * This is the offline stand-in for a phone's GPS. A desktop program cannot ask the hardware where it
 * is, so {@link cinesmart.service.LocationService} works out the position from the system settings
 * and matches it against this catalogue. Every hall, every city and every popular area of the app
 * is a row here, which is what makes "detect my location" and "search for your city" possible
 * without a network connection.
 *
 * Data line layout (5 columns):
 * LOC001|Bengaluru|Whitefield|12.995743|77.757949
 */
public class Locality implements Comparable<Locality> {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 5;

    private String localityId;
    private String city;
    private String area;
    private double latitude;
    private double longitude;

    public Locality() {
        this.localityId = "";
        this.city = "";
        this.area = "";
    }

    /** Constructor used by the sample catalogue. */
    public Locality(String localityId, String city, String area, double latitude, double longitude) {
        this();
        setLocalityId(localityId);
        setCity(city);
        setArea(area);
        setLatitude(latitude);
        setLongitude(longitude);
    }

    /** Constructor used when one line is read back from data/localities.txt. */
    public Locality(String[] parts) {
        this();
        setLocalityId(part(parts, 0));
        setCity(part(parts, 1));
        setArea(part(parts, 2));
        setLatitude(toDouble(part(parts, 3), 0));
        setLongitude(toDouble(part(parts, 4), 0));
    }

    /* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /** "Whitefield, Bengaluru" - how the picker button and the offer lines read it. */
    public String displayName() {
        return area.isEmpty() ? city : area + ", " + city;
    }

    /**
     * A city level row carries no area, so the picker shows just "Bengaluru" for it instead of
     * printing an empty ", Bengaluru" every time.
     */
    public boolean isCityOnly() {
        return area.isEmpty();
    }

    /** True only when the row carries a real coordinate - the same rule as {@link Theatre}. */
    public boolean hasPosition() {
        return latitude != 0 || longitude != 0;
    }

    /** The name a free-text search should look at. */
    public String searchText() {
        return (city + " " + area).toLowerCase();
    }

    /** Cities first, then areas A-Z, so the picker list is stable and readable. */
    @Override
    public int compareTo(Locality other) {
        int byCity = city.compareToIgnoreCase(other.city);
        return (byCity != 0) ? byCity : area.compareToIgnoreCase(other.area);
    }

    /** Combo boxes and lists show the full place name. */
    @Override
    public String toString() {
        return displayName();
    }

    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, localityId, city, area,
                String.format("%.6f", latitude), String.format("%.6f", longitude));
    }

    public static Locality fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty locality line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a locality line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new Locality(parts);
    }

    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getLocalityId() {
        return localityId;
    }

    public void setLocalityId(String localityId) {
        this.localityId = UiUtil.clean(localityId);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = UiUtil.clean(city);
    }

    public String getArea() {
        return area;
    }

    public void setArea(String area) {
        this.area = UiUtil.clean(area);
    }

    public double getLatitude() {
        return latitude;
    }

    public final void setLatitude(double latitude) {
        this.latitude = (latitude < -90 || latitude > 90) ? 0 : latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public final void setLongitude(double longitude) {
        this.longitude = (longitude < -180 || longitude > 180) ? 0 : longitude;
    }

    /* ------------------------------------------------------------------ *\
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static double toDouble(String text, double fallback) {
        try {
            return Double.parseDouble(UiUtil.clean(text));
        } catch (Exception e) {
            return fallback;
        }
    }
}