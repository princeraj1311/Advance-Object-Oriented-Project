package cinesmart.model;


import cinesmart.util.PasswordHasher;
import cinesmart.util.UiUtil;

/**
 * User is the ABSTRACT parent of everyone who can sign in (Customer and Admin).
 *
 * Abstraction: the rest of the program works with a "User" reference and never needs to
 * know which concrete class it holds, while {@link #getWelcomeMessage()} shows polymorphism
 * because each subclass answers in its own way.
 *
 * Encapsulation: all fields are private and reachable only through getters/setters, and the
 * plain password is never stored - only a salt plus a SHA-256 hash.
 */
public abstract class User {

    /** Field separator used by data/users.txt. */
    public static final String SEPARATOR_TEXT = "|";
    /** The same separator escaped, because String.split() works with regular expressions. */
    public static final String SEPARATOR_REGEX = "\\|";
    /** userName | fullName | email | phone | city | salt | passwordHash | authMethod |
     *  phoneVerified | role | (customer extras dateOfBirth,gender,seatPreference) */
    public static final int COMMON_FIELD_COUNT = 10;

    /** How this account was created. */
    public enum AuthMethod {
        LEGACY_USERNAME_PASSWORD,   // old username/password login
        PHONE,                      // verified by OTP (what we are building)
        GOOGLE                      // OAuth 2.0
    }

    private String userName;
    private String fullName;
    private String email;
    private String phone;
    private String city;
    private String salt;
    private String passwordHash;
    /** How this account was created (Feature 1: phone OTP / Google OAuth / legacy username+password). */
    private String authMethod;
    /** True when the phone number (for PHONE accounts) is verified. */
    private boolean phoneVerified;
    /** Consumer gender (for the new OTP-based login flow). */
    private String gender;
    /** Consumer age (for the new OTP-based login flow). */
    private int age;

    /** Empty constructor used by subclasses that fill the object step by step. */
    protected User() {
        this.userName = "";
        this.fullName = "";
        this.email = "";
        this.phone = "";
        this.city = "";
        this.salt = "";
        this.passwordHash = "";
        this.authMethod = AuthMethod.LEGACY_USERNAME_PASSWORD.name();
        this.phoneVerified = false;
        this.gender = "";
        this.age = -1;
    }

    /** Full constructor used when somebody registers. */
    protected User(String userName, String fullName, String email, String phone, String city) {
        this();
        setUserName(userName);
        setFullName(fullName);
        setEmail(email);
        setPhone(phone);
        setCity(city);
    }

    /** Constructor used when one line is read back from the data file. */
    protected User(String[] dataParts) {
        this();
        // Read the common columns first (positions 0-7).
        setUserName(part(dataParts, 0));
        setFullName(part(dataParts, 1));
        setEmail(part(dataParts, 2));
        setPhone(part(dataParts, 3));
        setCity(part(dataParts, 4));
        this.salt = part(dataParts, 5); // may be empty for legacy accounts
        this.passwordHash = part(dataParts, 6);
        // authMethod may be missing on legacy accounts.
        this.authMethod = partsLength(dataParts) > 8 ? part(dataParts, 8)
                : AuthMethod.LEGACY_USERNAME_PASSWORD.name();
        // phoneVerified may be missing on legacy accounts (defaults in this()).
        this.phoneVerified = partsLength(dataParts) > 9
                && part(dataParts, 9).equalsIgnoreCase("true");
        // role is the LAST column.
        String role = "";
        if (partsLength(dataParts) > 9) {
            role = part(dataParts, 9);
        } else if (partsLength(dataParts) > 7) {
            role = part(dataParts, 7);
        }
        this.setRole(role);
    }

    /** Sets the role by name (Customer or Admin). */
    private void setRole(String role) {
        if (Customer.ROLE.equalsIgnoreCase(role)) {
            // kept as Customer
        } else if (Admin.ROLE.equalsIgnoreCase(role)) {
            // kept as Admin
        } else {
            // unknown role: treat as legacy username/password user
            this.authMethod = AuthMethod.LEGACY_USERNAME_PASSWORD.name();
        }
    }

    /** Minimum number of columns a user record must carry. */
    private static int partsLength(String[] parts) {
        return (parts == null) ? 0 : parts.length;
    }

    /* ------------------------------------------------------------------ *
     * Abstract behaviour, implemented differently by each subclass
     * ------------------------------------------------------------------ */

    /** "Customer" or "Admin". */
    public abstract String getRole();

    /** Polymorphism: a customer and an admin are greeted differently. */
    public abstract String getWelcomeMessage();

    /* ------------------------------------------------------------------ *
     * Password handling
     * ------------------------------------------------------------------ */

    /** Creates a fresh salt and stores only the hash of the password. */
    public void setPassword(String plainPassword) {
        this.salt = PasswordHasher.newSalt();
        this.passwordHash = PasswordHasher.hash(plainPassword == null ? "" : plainPassword, salt);
    }

    /** Checks a typed password against the stored hash. */
    public boolean verifyPassword(String plainPassword) {
        return PasswordHasher.matches(plainPassword, salt, passwordHash);
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    /* ------------------------------------------------------------------ *
     * Getters and setters (the setters clean the input)
     * ------------------------------------------------------------------ */

    public String getUserName() {
        return userName;
    }

    public final void setUserName(String userName) {
        this.userName = UiUtil.clean(userName).toLowerCase();
    }

    public String getFullName() {
        return fullName;
    }

    public final void setFullName(String fullName) {
        this.fullName = UiUtil.clean(fullName);
    }

    public String getEmail() {
        return email;
    }

    public final void setEmail(String email) {
        this.email = UiUtil.clean(email).toLowerCase();
    }

    public String getPhone() {
        return phone;
    }

    public final void setPhone(String phone) {
        this.phone = UiUtil.clean(phone);
    }

    public String getCity() {
        return city;
    }

    public final void setCity(String city) {
        this.city = UiUtil.clean(city);
    }

    /* ------------------------------------------------------------------ *
     * Saving an object as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    /**
     * One object = one line inside data/users.txt.
     * Columns: userName|fullName|email|phone|city|salt|passwordHash|authMethod|phoneVerified|role
     */
    public String toDataLine() {
        return String.join(SEPARATOR_TEXT,
                userName, fullName, email, phone, city, salt, passwordHash,
                authMethod, String.valueOf(phoneVerified), getRole());
    }

    /**
     * Factory method: reads one line and decides which subclass to build, using the role
     * column. Backwards-compatible: old files that only have 8 columns still load.
     */
    public static User fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty user line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < COMMON_FIELD_COUNT) {
            throw new IllegalArgumentException("Incomplete user record: " + line);
        }
        // role is the LAST column (index 9 for new, or parts[7] for legacy 8-col files)
        String role = "";
        if (parts.length > 9) {
            role = part(parts, 9);
        } else if (parts.length > 7) {
            role = part(parts, 7);
        }
        if (Customer.ROLE.equalsIgnoreCase(role)) {
            return new Customer(parts);
        }
        if (Admin.ROLE.equalsIgnoreCase(role)) {
            return new Admin(parts);
        }
        // Unknown role -> treat as legacy username/password user.
        return new Customer(parts);
    }

    /** Safe read from a String array: returns "" when the column is missing. */
    protected static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    /** toString() override - handy while debugging. */
    @Override
    public String toString() {
        return getRole() + "[" + userName + ", " + fullName + "]";
    }
}

