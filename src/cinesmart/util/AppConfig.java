package cinesmart.util;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AppConfig is the single place for CineSmart's runtime constants.
 *
 * Values are resolved in this order (first wins):
 *   1. a {@code .env} file next to the working directory (${user.dir}/.env)
 *   2. a Java system property (-Dkey=value on the java command line)
 *   3. the hard-coded default below
 *
 * Nothing here touches Swing, so tests and services can read settings without a display.
 */
public final class AppConfig {

    private AppConfig() {
        // Utility class.
    }

    /** The file we look for, relative to the folder the app is launched from. */
    private static final File ENV_FILE = new File(System.getProperty("user.dir"), ".env");

    /** Lazy-loaded, parse-once map of key -> value. */
    private static volatile Map<String, String> values;

    /* ------------------------------------------------------------------ *
     * Constants with defaults (override via .env or -Dkey=value)         *
     * ------------------------------------------------------------------ */

    /** How many minutes a seat stays HELD before it auto-releases. */
    public static final int SEAT_HOLD_MINUTES = asInt("seatHoldMinutes", 5);

    /** How many minutes before showtime cancellation is blocked. */
    public static final int CANCELLATION_CUTOFF_MINUTES = asInt("cancellationCutoffMinutes", 30);

    /** How many days ahead generateShows() creates Show instances. */
    public static final int SHOWS_DAYS_AHEAD = Math.max(3, asInt("showsDaysAhead", 3));

    /** Hour (0-23) for the daily 00:05-style regeneration sweep. */
    public static final int DAILY_GEN_HOUR = asInt("dailyGenHour", 0);

    /** Minute for the daily regeneration sweep. */
    public static final int DAILY_GEN_MINUTE = asInt("dailyGenMinute", 5);

    /** How many days old an empty past show must be before it is archived. */
    public static final int ARCHIVE_PAST_SHOW_DAYS = asInt("archivePastShowDays", 30);

    /** SMTP host used to deliver sign-in verification codes. */
    public static final String SMTP_HOST = asString("smtpHost", "smtp.gmail.com");

    /** SMTP port. */
    public static final int SMTP_PORT = asInt("smtpPort", 587);

    /** SMTP username. */
    public static final String SMTP_USER = asString("smtpUser", "");

    /** SMTP password. */
    public static final String SMTP_PASS = asString("smtpPass", "");

    /** From address used in notification emails. */
    public static final String SMTP_FROM = asString("smtpFrom", "");

    /** Email stored on the first-run admin account. */
    public static final String ADMIN_EMAIL = asString("adminEmail", "admin@cinesmart.in");

    /** TMDb API key for current movie ratings and release information. */
    public static final String TMDB_API_KEY = asString("tmdbApiKey", "");

    /** OpenStreetMap Overpass endpoint used to find cinema listings by city. */
    public static final String OSM_OVERPASS_URL = asString("osmOverpassUrl",
            "https://overpass-api.de/api/interpreter");

    /* ------------------------------------------------------------------ *
     * Loading                                                            *
     * ------------------------------------------------------------------ */

    /** Reads the .env file (if present) plus system properties, lazily and exactly once. */
    private static Map<String, String> values() {
        if (values == null) {
            synchronized (AppConfig.class) {
                if (values == null) {
                    values = load();
                }
            }
        }
        return values;
    }

    private static Map<String, String> load() {
        Map<String, String> map = new LinkedHashMap<>();
        // System properties take precedence over .env defaults but are read here too.
        for (Map.Entry<Object, Object> e : System.getProperties().entrySet()) {
            String key = String.valueOf(e.getKey());
            if (key != null && key.startsWith("cinesmart.")) {
                map.put(key.substring("cinesmart.".length()), String.valueOf(e.getValue()));
            }
        }
        // .env file: simple KEY=VALUE, lines starting with # are comments, blank lines skipped.
        try (BufferedReader reader = new BufferedReader(new FileReader(ENV_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // Strip surrounding quotes if present.
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                if (!key.isEmpty()) {
                    map.put(key, value);
                }
            }
        } catch (IOException ignored) {
            // No .env file - that is fine, defaults apply.
        }
        return map;
    }

    /** Returns true when SMTP sign-in codes can be sent. */
    public static boolean isSmtpConfigured() {
        return !SMTP_HOST.isEmpty() && !SMTP_USER.isEmpty() && !SMTP_PASS.isEmpty();
    }

    /* ------------------------------------------------------------------ *
     * Helpers                                                            *
     * ------------------------------------------------------------------ */

    private static int asInt(String key, int fallback) {
        String text = values().get(key);
        if (text == null || text.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String asString(String key, String fallback) {
        String text = values().get(key);
        return (text == null || text.isEmpty()) ? fallback : text;
    }
}
