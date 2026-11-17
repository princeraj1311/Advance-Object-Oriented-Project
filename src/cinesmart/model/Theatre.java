package cinesmart.model;


import cinesmart.util.UiUtil;

/**
 * Theatre is one cinema venue (Stage 3). A theatre belongs to exactly one city, which is what
 * the "Select City" box in the header filters on.
 *
 * Data line layout (7 required columns + 2 optional ones):
 * theatreId|name|city|address|screens|basePrice|amenities|latitude|longitude
 *
 * The two coordinates are optional, exactly like a movie's poster URL: a file written before
 * Stage 10 still loads, and such a theatre simply has no position on the map. They are real
 * latitude/longitude of the area the cinema is in, which is what {@link GeoUtil#distanceKm}
 * needs to work out which hall is nearest to the customer.
 */
public class Theatre {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 7;
    /** Columns a full Stage 10 line carries; only the first seven are required. */
    public static final int FIELD_COUNT_WITH_POSITION = 9;

    private String theatreId;
    private String name;
    private String city;
    private String address;
    private int screens;
    /** Ticket price before the show-time surcharge, used by the sample show times. */
    private int basePrice;
    private String amenities;
    /** Stage 10: where the hall is on the map, used to find the nearest one. */
    private double latitude;
    private double longitude;

    public Theatre() {
        this.theatreId = "";
        this.name = "";
        this.city = "";
        this.address = "";
        this.screens = 1;
        this.basePrice = 150;
        this.amenities = "";
    }

    /** Constructor used by the sample data and by the admin add-theatre form (Stage 8). */
    public Theatre(String theatreId, String name, String city, String address, int screens,
                   int basePrice, String amenities) {
        this();
        setTheatreId(theatreId);
        setName(name);
        setCity(city);
        setAddress(address);
        setScreens(screens);
        setBasePrice(basePrice);
        setAmenities(amenities);
    }

    /** Constructor used when one line is read back from data/theatres.txt. */
    public Theatre(String[] parts) {
        this();
        setTheatreId(part(parts, 0));
        setName(part(parts, 1));
        setCity(part(parts, 2));
        setAddress(part(parts, 3));
        setScreens(toInt(part(parts, 4), 1));
        setBasePrice(toInt(part(parts, 5), 150));
        setAmenities(part(parts, 6));
        // Optional columns 8 and 9 - a file written before Stage 10 simply stops at column 7.
        setLatitude(toDouble(part(parts, 7), 0));
        setLongitude(toDouble(part(parts, 8), 0));
    }

    /* ------------------------------------------------------------------ *
     * Behaviour
     * ------------------------------------------------------------------ */

    /** "PVR Phoenix Marketcity" - the label shown above a show-time row. */
    public String displayName() {
        return name;
    }

    /** Small print under the name: "Whitefield, Bengaluru - 6 screens". */
    public String metaLine() {
        return address + ", " + city + " \u00b7 " + screens
                + (screens == 1 ? " screen" : " screens");
    }

    /** "from Rs 190" - the price a customer sees before choosing a show. */
    public String priceFromText() {
        return "from Rs " + basePrice;
    }

    /* ------------------------------------------------------------------ *
     * Saving an object as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        StringBuilder line = new StringBuilder(String.join(SEPARATOR_TEXT,
                theatreId, name, city, address, String.valueOf(screens),
                String.valueOf(basePrice), amenities));
        // Only a theatre that really knows where it is carries the two extra columns, so a hall
        // without coordinates round-trips to the same short line it was read from.
        if (hasPosition()) {
            line.append(SEPARATOR_TEXT).append(formatLatitude())
                    .append(SEPARATOR_TEXT).append(formatLongitude());
        }
        return line.toString();
    }

    public static Theatre fromDataLine(String line) {
        String[] parts = line.split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("Incomplete theatre record: " + line);
        }
        return new Theatre(parts);
    }

    /* ------------------------------------------------------------------ *
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getTheatreId() {
        return theatreId;
    }

    public void setTheatreId(String theatreId) {
        this.theatreId = UiUtil.clean(theatreId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = UiUtil.clean(name);
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = UiUtil.clean(city);
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = UiUtil.clean(address);
    }

    public int getScreens() {
        return screens;
    }

    public final void setScreens(int screens) {
        this.screens = (screens < 1 || screens > 20) ? 1 : screens;
    }

    public int getBasePrice() {
        return basePrice;
    }

    public final void setBasePrice(int basePrice) {
        this.basePrice = (basePrice < 50 || basePrice > 2000) ? 150 : basePrice;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = UiUtil.clean(amenities);
    }

    public double getLatitude() {
        return latitude;
    }

    public final void setLatitude(double latitude) {
        // Latitude outside -90..90 means the value was junk or missing, not a real position.
        this.latitude = (latitude < -90 || latitude > 90) ? 0 : latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public final void setLongitude(double longitude) {
        this.longitude = (longitude < -180 || longitude > 180) ? 0 : longitude;
    }

    /**
     * True only when a real coordinate was stored. The city of "Kolkata, Marine Drive" is never at
     * exactly (0,0), so a zero pair reliably means "this hall has no position on the map yet".
     */
    public boolean hasPosition() {
        return latitude != 0 || longitude != 0;
    }

    /** Six decimals is about 10 cm - far more than the data deserves, and it keeps the file tidy. */
    private String formatLatitude() {
        return String.format("%.6f", latitude);
    }

    private String formatLongitude() {
        return String.format("%.6f", longitude);
    }

    /** Combo boxes and lists show the name only. */
    @Override
    public String toString() {
        return name + " - " + city;
    }

    /* ------------------------------------------------------------------ *
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double toDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
