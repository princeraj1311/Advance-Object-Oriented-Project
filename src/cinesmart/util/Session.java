package cinesmart.util;


import cinesmart.model.User;

/**
 * Session remembers "who is using the application right now" plus the few global choices
 * (city, remember-me, accent colour).
 *
 * All members are static on purpose: the whole desktop application is ONE running program,
 * so one shared session object is enough and every screen can ask Session for the current
 * user instead of passing the user object through ten constructors.
 */
public final class Session {

    /** Cities offered in the header JComboBox (theatre data in Stage 3 uses the same list). */
    public static final String[] CITIES = {
        "Bengaluru", "Chennai", "Hyderabad", "Mumbai", "Delhi NCR", "Kochi", "Pune", "Kolkata"
    };

    /** The rules SeatMap knows, offered in the combo box of the seat screen (Stage 4). */
    public static final String[] SEAT_PREFERENCES = {
        "No Preference", "Near Center", "Away From Screen", "Couple/Adjacent Seats", "Easy Exit"
    };

    private static User currentUser;
    private static String selectedCity = CITIES[0];
    private static boolean rememberMe = false;
    private static String rememberedUserName = "";

    private Session() {
        // Utility class.
    }

    public static User currentUser() {
        return currentUser;
    }

    public static void login(User user) {
        currentUser = user;
    }

    /** Clears the logged in user (called by Logout). */
    public static void logout() {
        currentUser = null;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    /** True when the logged in user is an Admin - used to enable the Admin menu. */
    public static boolean isAdmin() {
        return currentUser != null && "Admin".equals(currentUser.getRole());
    }

    /** First word of the full name, e.g. "Aarav" - used in the greeting label. */
    public static String firstName() {
        if (currentUser == null) {
            return "Guest";
        }
        String name = UiUtil.clean(currentUser.getFullName());
        if (name.isEmpty()) {
            return currentUser.getUserName();
        }
        return name.split("\\s+")[0];
    }

    public static String selectedCity() {
        return selectedCity;
    }

    public static void setSelectedCity(String city) {
        if (city != null && !city.trim().isEmpty()) {
            selectedCity = city.trim();
        }
    }

    public static boolean isRememberMe() {
        return rememberMe;
    }

    public static void setRememberMe(boolean on) {
        rememberMe = on;
    }

    public static String rememberedUserName() {
        return rememberedUserName;
    }

    public static void setRememberedUserName(String userName) {
        rememberedUserName = (userName == null) ? "" : userName;
    }

    /* ---- The detected / chosen area, restored on the next start ---- */

    /** The localityId of the last detected or chosen place; empty until one is chosen. */
    private static String savedLocalityId = "";

    /** The same place written as "Area, City", used when the catalogue id no longer matches. */
    private static String savedLocalityName = "";

    public static String savedLocalityId() {
        return savedLocalityId;
    }

    public static void setSavedLocalityId(String localityId) {
        savedLocalityId = (localityId == null) ? "" : localityId.trim();
    }

    public static String savedLocalityName() {
        return savedLocalityName;
    }

    public static void setSavedLocalityName(String name) {
        savedLocalityName = (name == null) ? "" : name.trim();
    }

    /**
     * True when the customer has already told the app where they are. The header uses this to say
     * "Detect my location" the first time and "Change location" afterwards.
     */
    public static boolean hasSavedLocality() {
        return !savedLocalityId.isEmpty() || !savedLocalityName.isEmpty();
    }
}
