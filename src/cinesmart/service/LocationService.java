package cinesmart.service;


import cinesmart.model.Locality;
import cinesmart.model.Theatre;
import cinesmart.util.DataStore;
import cinesmart.util.GeoUtil;
import cinesmart.util.Session;
import cinesmart.util.UiUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LocationService is the service behind the "Detect my location" button and the city picker.
 *
 * <b>How detection works without a GPS.</b> A browser can ask the phone's location chip; a desktop
 * JVM cannot, and pretending otherwise would be dishonest. Instead this service reads what the
 * operating system already knows about the machine and turns it into a position:
 * <ol>
 *   <li>a manual pin - the system property {@code -Dcinesmart.location=12.9716,77.5946}, which is
 *       how the screenshots and the tests are made reproducible;</li>
 *   <li>the city named in a system property such as {@code user.timezone} or {@code user.country}
 *       (for example {@code Asia/Kolkata} or {@code IN});</li>
 *   <li>the account's own saved locality, so the app opens where the customer left it;</li>
 *   <li>nothing at all - in which case the detection fails cleanly and the picker opens instead.</li>
 * </ol>
 *
 * Every branch ends at the same place: a {@link Locality} row of {@code data/localities.txt}, which
 * carries real coordinates. So the distance the app shows is a genuine haversine distance measured
 * from where the customer is, and it is the same number {@link GeoUtil} would give for a phone.
 *
 * The class follows the two rules of this project: the DataStore is the truth (localities are read
 * back from the file, never from a hard coded list) and nothing is written unless it was asked for.
 */
public class LocationService {

    /** The system property that pins the position, so a demo or a test is reproducible. */
    public static final String LOCATION_PROPERTY = "cinesmart.location";
    /** How many hits a free-text search shows at most. */
    public static final int MAX_SEARCH_RESULTS = 12;

    /** Cities shown as chips under the search box, in this order. */
    public static final String[] POPULAR_CITIES = {
        "Mumbai", "Delhi NCR", "Bengaluru", "Hyderabad", "Chennai",
        "Kolkata", "Pune", "Kochi"
    };

    private final DataStore store;

    public LocationService() {
        this(null);
    }

    public LocationService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *\
     * Detecting where the customer is
     * ------------------------------------------------------------------ */

    /**
     * What detection found: the place, how it was worked out, and whether it is worth trusting.
     *
     * A result carries its own explanation because "why does the app think I am in Kochi?" is the
     * first question a customer asks. When {@link #isPrecise()} is false the screen shows the
     * picker rather than silently sorting halls by a guess.
     */
    public static final class Detection {

        private final Locality place;
        private final String source;
        private final boolean precise;

        Detection(Locality place, String source, boolean precise) {
            this.place = place;
            this.source = source;
            this.precise = precise;
        }

        /** The matched place, or null when nothing could be worked out. */
        public Locality getPlace() {
            return place;
        }

        /** "From your system time zone (Asia/Kolkata)" - shown under the result. */
        public String getSource() {
            return source;
        }

        /** True when the position is known exactly (a manual pin was given). */
        public boolean isPrecise() {
            return precise;
        }

        public boolean isFound() {
            return place != null;
        }

        /** "Marine Drive, Kochi - from your system setting user.timezone" for the status bar. */
        public String summary() {
            return (place == null) ? "Location could not be detected"
                    : place.displayName() + "  \u00b7  " + source;
        }
    }

    /**
     * Works out where the machine is, without ever touching the network.
     *
     * The order of the branches is the order of trust: an explicit pin beats a system hint, a system
     * hint beats what the account last chose, and when nothing matches the method returns a failed
     * result instead of falling back to a random city.
     */
    public Detection detect() {
        Locality pinned = detectFromSystemProperty();
        if (pinned != null) {
            return new Detection(pinned, "From the " + LOCATION_PROPERTY + " system property", true);
        }

        for (String key : new String[] {"user.timezone", "user.country", "user.language"}) {
            String value = readSystemProperty(key);
            if (value.isEmpty()) {
                continue;
            }
            Locality match = matchByCityToken(value);
            if (match != null) {
                return new Detection(match, "From your system setting " + key
                        + " (" + value + ")", false);
            }
        }

        Locality saved = savedPlace();
        if (saved != null) {
            return new Detection(saved, "From the location saved on this account", false);
        }
        return new Detection(null, "No location could be worked out on this computer", false);
    }

    /** Reads the "-Dcinesmart.location=lat,lon" pin and turns it into the nearest place. */
    private Locality detectFromSystemProperty() {
        String raw = readSystemProperty(LOCATION_PROPERTY);
        if (raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            Locality match = nearestLocality(latitude, longitude);
            if (match != null && match.hasPosition()) {
                return match;
            }
        } catch (NumberFormatException ignored) {
            // A malformed pin is treated as no pin at all, which is the honest reading of it.
        }
        return null;
    }

    /**
     * Matches a system value against the catalogue.
     *
     * "Asia/Kolkata" ends in a city name that exists in the catalogue, so every segment of the value
     * is tried. A value such as "America/Los_Angeles" must NOT match anything, which is why only an
     * exact city-name match is accepted and never a "contains" test on the raw string.
     */
    private Locality matchByCityToken(String value) {
        String text = UiUtil.clean(value).toLowerCase();
        if (text.isEmpty()) {
            return null;
        }
        for (String segment : text.split("[/,_\\-\\s]+")) {
            for (Locality place : store.getLocalities()) {
                if (place.getCity().toLowerCase().equals(segment) && place.hasPosition()) {
                    return place;
                }
            }
        }
        return null;
    }

    /** The locality the customer last chose, restored from data/settings.properties. */
    public Locality savedPlace() {
        Locality byId = store.findLocality(Session.savedLocalityId());
        if (byId != null) {
            return byId;
        }
        String name = Session.savedLocalityName();
        return name.isEmpty() ? null : findByName(name);
    }

    /** Remembers the chosen place so the next start opens in the same neighbourhood. */
    public boolean remember(Locality place) {
        if (place == null) {
            return false;
        }
        Session.setSavedLocalityId(place.getLocalityId());
        Session.setSavedLocalityName(place.displayName());
        store.settings().setProperty("localityId", place.getLocalityId());
        store.settings().setProperty("locality", place.displayName());
        if (place.hasPosition()) {
            store.settings().setProperty("latitude", String.format("%.6f", place.getLatitude()));
            store.settings().setProperty("longitude", String.format("%.6f", place.getLongitude()));
        }
        return true;
    }
/* ------------------------------------------------------------------ *\
     * Searching and choosing a place (the city picker)
     * ------------------------------------------------------------------ */

    /**
     * The free-text search of the "Select your region" box.
     *
     * City matches are put before area matches and a prefix beats a word buried in the middle, so
     * typing "kor" shows "Koramangala" before "Whitefield". An empty box returns the whole
     * catalogue, which is what makes the list under the search box usable without typing.
     */
    public List<Locality> search(String query) {
        String wanted = UiUtil.clean(query).toLowerCase();
        List<Locality> exact = new ArrayList<>();
        List<Locality> startsWith = new ArrayList<>();
        List<Locality> contains = new ArrayList<>();
        for (Locality place : sortedLocalities()) {
            if (wanted.isEmpty()) {
                contains.add(place);
                continue;
            }
            String city = place.getCity().toLowerCase();
            String area = place.getArea().toLowerCase();
            if (city.equals(wanted) || area.equals(wanted)) {
                exact.add(place);
            } else if (city.startsWith(wanted) || area.startsWith(wanted)) {
                startsWith.add(place);
            } else if (place.searchText().contains(wanted)) {
                contains.add(place);
            }
        }
        List<Locality> found = new ArrayList<>(exact);
        found.addAll(startsWith);
        found.addAll(contains);
        return (found.size() > MAX_SEARCH_RESULTS)
                ? new ArrayList<>(found.subList(0, MAX_SEARCH_RESULTS)) : found;
    }

    /** Every place, cities first and areas A-Z - the "View all cities" list. */
    public List<Locality> allLocalities() {
        return sortedLocalities();
    }

    /**
     * The chips under the search box: the city-level row of each popular city, so clicking one always
     * gives a real position to measure from rather than an area that may not exist.
     */
    public List<Locality> popularPlaces() {
        List<Locality> chips = new ArrayList<>();
        for (String city : POPULAR_CITIES) {
            Locality place = cityCentreOf(city);
            chips.add((place == null) ? new Locality("", city, "", 0, 0) : place);
        }
        return chips;
    }

    /** The row of the catalogue that represents the middle of a city. */
    public Locality cityCentreOf(String city) {
        String wanted = UiUtil.clean(city);
        for (Locality place : store.getLocalities()) {
            if (place.isCityOnly() && place.getCity().equalsIgnoreCase(wanted)) {
                return place;
            }
        }
        // No city-level row: fall back to the first area of that city, which still has coordinates.
        for (Locality place : store.getLocalities()) {
            if (place.getCity().equalsIgnoreCase(wanted) && place.hasPosition()) {
                return place;
            }
        }
        return null;
    }

    /** The areas of one city - used to fill the area box on the canteen screen. */
    public List<String> areasOf(String city) {
        List<String> areas = new ArrayList<>();
        String wanted = UiUtil.clean(city);
        for (Locality place : store.getLocalities()) {
            if (place.getCity().equalsIgnoreCase(wanted) && !place.isCityOnly()
                    && !containsIgnoreCase(areas, place.getArea())) {
                areas.add(place.getArea());
            }
        }
        Collections.sort(areas);
        return areas;
    }

    /** Looks a place up by "Area, City" text, which is what a saved setting holds. */
    public Locality findByName(String displayName) {
        String wanted = UiUtil.clean(displayName);
        for (Locality place : store.getLocalities()) {
            if (place.displayName().equalsIgnoreCase(wanted)) {
                return place;
            }
        }
        return null;
    }

    /**
     * The catalogue place nearest to a point, or null when the point is not a real position.
     *
     * This is the method that turns a coordinate into something a person recognises, and it is used
     * both by the {@code -Dcinesmart.location} pin and by "detect" when only coordinates are known.
     */
    public Locality nearestLocality(double latitude, double longitude) {
        if (!GeoUtil.isRealPosition(latitude, longitude)) {
            return null;
        }
        Locality nearest = null;
        double bestKm = Double.MAX_VALUE;
        for (Locality place : store.getLocalities()) {
            if (!place.hasPosition()) {
                continue;
            }
            double km = GeoUtil.distanceKm(latitude, longitude,
                    place.getLatitude(), place.getLongitude());
            if (km >= 0 && km < bestKm) {
                bestKm = km;
                nearest = place;
            }
        }
        return nearest;
    }

    /**
     * The nearest cinema hall to a place, or null when the place has no position.
     *
     * This is what the header's "nearest hall" hint and the canteen screen both ask for, and it
     * deliberately reuses {@link GeoUtil} rather than inventing a second distance formula.
     */
    public Nearby nearestHall(String city, Locality from) {
        if (from == null || !from.hasPosition()) {
            return null;
        }
        Nearby best = null;
        for (Theatre theatre : store.getTheatres()) {
            if (!theatre.getCity().equalsIgnoreCase(UiUtil.clean(city)) || !theatre.hasPosition()) {
                continue;
            }
            double km = GeoUtil.distanceKm(from.getLatitude(), from.getLongitude(),
                    theatre.getLatitude(), theatre.getLongitude());
            Nearby candidate = new Nearby(theatre, km);
            if (best == null || candidate.compareTo(best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /** One hall plus how far it is from where the customer is. */
    public static final class Nearby implements Comparable<Nearby> {

        private final Theatre theatre;
        private final double distanceKm;

        Nearby(Theatre theatre, double distanceKm) {
            this.theatre = theatre;
            this.distanceKm = distanceKm;
        }

        public Theatre getTheatre() {
            return theatre;
        }

        public String getName() {
            return theatre.getName();
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public String getDistanceText() {
            return GeoUtil.distanceText(distanceKm);
        }

        @Override
        public int compareTo(Nearby other) {
            return Double.compare(distanceKm, other.distanceKm);
        }
    }

    /* ------------------------------------------------------------------ *\
     * Small private helpers
     * ------------------------------------------------------------------ */

    /** The catalogue in its own order: cities first, then areas A-Z (Locality.compareTo). */
    private List<Locality> sortedLocalities() {
        List<Locality> sorted = new ArrayList<>(store.getLocalities());
        Collections.sort(sorted);
        return sorted;
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** A missing system property is an empty string, never an exception. */
    private static String readSystemProperty(String key) {
        try {
            String value = System.getProperty(key);
            return (value == null) ? "" : value.trim();
        } catch (SecurityException e) {
            return ""; // a locked-down JVM denies the read; detection then falls through
        }
    }
}
