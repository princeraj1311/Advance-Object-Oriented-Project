package cinesmart.model;

import cinesmart.util.UiUtil;

/** Informational cinema listing sourced from OpenStreetMap; it is not a bookable theatre. */
public final class CinemaVenue {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 7;

    private String venueId = "";
    private String city = "";
    private String name = "";
    private String address = "";
    private double latitude;
    private double longitude;
    private String sourceUrl = "";

    public CinemaVenue(String venueId, String city, String name, String address, double latitude,
                       double longitude, String sourceUrl) {
        setVenueId(venueId);
        setCity(city);
        setName(name);
        setAddress(address);
        setLatitude(latitude);
        setLongitude(longitude);
        setSourceUrl(sourceUrl);
    }

    public CinemaVenue(String[] parts) {
        this(part(parts, 0), part(parts, 1), part(parts, 2), part(parts, 3),
                parseDouble(part(parts, 4)), parseDouble(part(parts, 5)), part(parts, 6));
    }

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, venueId, city, name, address,
                String.valueOf(latitude), String.valueOf(longitude), sourceUrl);
    }

    public static CinemaVenue fromDataLine(String line) {
        String[] parts = line == null ? new String[0] : line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("A cinema listing needs " + FIELD_COUNT
                    + " columns.");
        }
        return new CinemaVenue(parts);
    }

    private static String part(String[] parts, int index) {
        return parts == null || index >= parts.length ? "" : parts[index].trim();
    }

    private static double parseDouble(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getVenueId() { return venueId; }
    public void setVenueId(String value) { venueId = UiUtil.clean(value); }
    public String getCity() { return city; }
    public void setCity(String value) { city = UiUtil.clean(value); }
    public String getName() { return name; }
    public void setName(String value) { name = UiUtil.clean(value).replace("|", "/"); }
    public String getAddress() { return address; }
    public void setAddress(String value) { address = UiUtil.clean(value).replace("|", "/"); }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) {
        latitude = value < -90 || value > 90 ? 0 : value;
    }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) {
        longitude = value < -180 || value > 180 ? 0 : value;
    }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String value) {
        String clean = UiUtil.clean(value);
        sourceUrl = clean.startsWith("https://www.openstreetmap.org/") ? clean : "";
    }
}
