package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Offer is one discount the cinema runs - the coupon codes a customer types on the payment screen
 * and on the food basket.
 *
 * An offer is data, not behaviour: it knows what it is worth and what it needs, and
 * {@link cinesmart.service.OfferService} decides whether THIS customer may use it on THIS order.
 * That split keeps the rules in one service and the model a plain record.
 *
 * Two optional trailing columns keep old files loadable: the eleventh column is the comma separated
 * list of user names who already used the code (one use per customer), and the twelfth is the day
 * the code was switched off by hand.
 *
 * Data line layout (10 required columns + 2 optional ones):
 * OFF01|FIRST50|Flat Rs 50 off|Your first booking at CineSave50|FLAT|50|300|50||TICKETS|demo|
 */
public class Offer {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 10;

    /** How the discount is worked out. */
    public enum Kind {
        /** A fixed number of rupees off, whatever the order is worth. */
        FLAT,
        /** A percentage off, usually capped by {@code maxDiscount}. */
        PERCENT;

        /** Unknown text in a file falls back to FLAT rather than breaking the load. */
        public static Kind parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(wanted)) {
                    return kind;
                }
            }
            return FLAT;
        }
    }

    /** What the code is good for. */
    public enum Scope {
        TICKETS("Movie tickets"),
        FOOD("Food and canteen"),
        ANY("Tickets and food");

        private final String label;

        Scope(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Scope parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Scope scope : values()) {
                if (scope.name().equalsIgnoreCase(wanted)) {
                    return scope;
                }
            }
            return ANY;
        }
    }

    private String offerId;
    private String code;
    private String title;
    private String description;
    private Kind kind;
    /** Rupees for FLAT, percentage points for PERCENT. */
    private int value;
    /** The order has to be worth at least this much, in rupees. */
    private int minOrder;
    /** The most the code may take off; 0 means no cap. */
    private int maxDiscount;
    /** Comma separated cities the code works in; empty means every city. */
    private String cities;
    /** Last day the code may be used, as yyyy-MM-dd. */
    private String validTill;
    private Scope scope;
    /** Comma separated user names who already used it - one use per customer. */
    private String usedBy;
    /** Optional day the code was switched off by hand; empty means still on. */
    private String disabledOn;

    public Offer() {
        this.offerId = "";
        this.code = "";
        this.title = "";
        this.description = "";
        this.kind = Kind.FLAT;
        this.value = 0;
        this.minOrder = 0;
        this.maxDiscount = 0;
        this.cities = "";
        this.validTill = "";
        this.scope = Scope.ANY;
        this.usedBy = "";
        this.disabledOn = "";
    }

    /** Constructor used by the sample offers. */
    public Offer(String offerId, String code, String title, String description, Kind kind, int value,
                 int minOrder, int maxDiscount, String cities, String validTill, Scope scope) {
        this();
        setOfferId(offerId);
        setCode(code);
        setTitle(title);
        setDescription(description);
        setKind(kind);
        setValue(value);
        setMinOrder(minOrder);
        setMaxDiscount(maxDiscount);
        setCities(cities);
        setValidTill(validTill);
        setScope(scope);
    }

    /** Constructor used when one line is read back from data/offers.txt. */
    public Offer(String[] parts) {
        this();
        setOfferId(part(parts, 0));
        setCode(part(parts, 1));
        setTitle(part(parts, 2));
        setDescription(part(parts, 3));
        setKind(Kind.parse(part(parts, 4)));
        setValue(toInt(part(parts, 5), 0));
        setMinOrder(toInt(part(parts, 6), 0));
        setMaxDiscount(toInt(part(parts, 7), 0));
        setCities(part(parts, 8));
        setValidTill(part(parts, 9));
        setScope(Scope.parse(part(parts, 10)));
        setUsedBy(part(parts, 11));
        setDisabledOn(part(parts, 12));
    }
/* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /**
     * The biggest this code may take off an order worth {@code amount} rupees.
     *
     * Three rules, in this order: never more than the order is worth, never more than the cap when
     * the offer has one, and never anything at all below the minimum order value. Returning 0 for a
     * failed rule is deliberate - the caller asks {@link #problemsWith} for the reason when the
     * number is zero, so one place decides and one place explains.
     */
    public int discountOn(int amount) {
        if (amount <= 0 || amount < minOrder) {
            return 0;
        }
        int discount = (kind == Kind.PERCENT)
                ? (int) Math.round(amount * (double) value / 100.0)
                : value;
        if (maxDiscount > 0) {
            discount = Math.min(discount, maxDiscount);
        }
        return Math.min(discount, amount);
    }

    /** Whether the minimum order value is met - the rule discountOn silently skips. */
    public boolean meetsMinimum(int amount) {
        return amount >= minOrder;
    }

    /**
     * "Flat Rs 50 off" or "20% off, up to Rs 200" - the value on the offer card.
     *
     * The cap is only printed when the offer has one, and only a percentage offer can have one
     * printed this way: a flat Rs 50 discount is Rs 50 whatever the order is worth.
     */
    public String valueText() {
        if (kind == Kind.PERCENT) {
            return value + "% off" + (maxDiscount > 0 ? ", up to Rs " + maxDiscount : "");
        }
        return "Flat Rs " + value + " off";
    }

    /** "On orders of Rs 300 or more" - the minimum order line. */
    public String minimumText() {
        return (minOrder <= 0) ? "No minimum order value"
                : "On orders of Rs " + minOrder + " or more";
    }

    /** "Valid till 2026-12-31", or "Runs until further notice" when the date is missing. */
    public String validityText() {
        return validTill.isEmpty() ? "Runs until further notice" : "Valid till " + validTill;
    }

    /** Whether the code works in this city. An empty city list means "everywhere". */
    public boolean worksInCity(String cityName) {
        String wanted = UiUtil.clean(cityName);
        if (cities.isEmpty()) {
            return true;
        }
        for (String city : cities.split(",")) {
            if (city.trim().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the code may be used on this kind of purchase.
     *
     * {@code wanted} is the FILTER the customer is looking through, not the purchase being made, so
     * "Tickets and food" (ANY) and "no filter" (null) both mean "show me everything". Only a real
     * filter such as TICKETS narrows the list. Getting this wrong would hide the food-only codes
     * from the Offers screen entirely, which is exactly the bug this comment exists to prevent.
     */
    public boolean worksFor(Scope wanted) {
        return wanted == null || wanted == Scope.ANY || scope == Scope.ANY || scope == wanted;
    }

    /** Every problem with using this code, in one list; empty means it may be used. */
    public java.util.List<String> problemsWith(String userName, String cityName, Scope wanted,
                                               int amount, LocalDate today) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (isDisabledOn()) {
            problems.add("Offer " + code + " is switched off.");
        }
        if (isExpiredOn(today)) {
            problems.add("Offer " + code + " expired on " + validTill + ".");
        }
        if (!worksInCity(cityName)) {
            problems.add("Offer " + code + " does not run in " + UiUtil.clean(cityName) + ".");
        }
        if (!worksFor(wanted)) {
            problems.add("Offer " + code + " is only valid for " + scope.label().toLowerCase() + ".");
        }
        if (!meetsMinimum(amount)) {
            problems.add("Offer " + code + " needs an order of Rs " + minOrder
                    + " or more (this one is Rs " + amount + ").");
        }
        if (isUsedBy(userName)) {
            problems.add("Offer " + code + " has already been used on this account.");
        }
        return problems;
    }

    /** One use per customer - the same rule every stage of this project follows. */
    public boolean isUsedBy(String userName) {
        String wanted = UiUtil.clean(userName).toLowerCase();
        if (wanted.isEmpty()) {
            return false;
        }
        for (String name : usedBy.split(",")) {
            if (name.trim().toLowerCase().equals(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Records the use. The name is appended, so an earlier use is never overwritten. */
    public void markUsedBy(String userName) {
        String name = UiUtil.clean(userName).toLowerCase();
        if (name.isEmpty() || isUsedBy(name)) {
            return;
        }
        usedBy = usedBy.isEmpty() ? name : usedBy + "," + name;
    }

    /** "Already used on this account" - the small line under the code. */
    public String usageText(String userName) {
        return isUsedBy(userName) ? "Already used on this account"
                : "Not used on this account yet";
    }

    public boolean isDisabledOn() {
        return !disabledOn.isEmpty();
    }

    public boolean isExpiredOn(LocalDate today) {
        return today != null && !validTill.isEmpty() && today.isAfter(expiryDate());
    }

    /** A malformed date must not make every offer look expired, so bad text means "no expiry". */
    private LocalDate expiryDate() {
        try {
            return LocalDate.parse(validTill, DATE);
        } catch (DateTimeParseException e) {
            return LocalDate.MAX;
        }
    }

    @Override
    public String toString() {
        return code + " - " + title;
    }

    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, offerId, code, title, description, kind.name(),
                String.valueOf(value), String.valueOf(minOrder), String.valueOf(maxDiscount),
                cities, validTill, scope.name(), usedBy, disabledOn);
    }

    public static Offer fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty offer line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("an offer line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new Offer(parts);
    }

    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getOfferId() {
        return offerId;
    }

    public void setOfferId(String offerId) {
        this.offerId = UiUtil.clean(offerId);
    }

    public String getCode() {
        return code;
    }

    /** Codes are shown in capitals, so "first50" typed by a customer still matches "FIRST50". */
    public final void setCode(String code) {
        this.code = UiUtil.clean(code).toUpperCase();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = UiUtil.clean(title);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = UiUtil.clean(description);
    }

    public Kind getKind() {
        return kind;
    }

    public final void setKind(Kind kind) {
        this.kind = (kind == null) ? Kind.FLAT : kind;
    }

    public int getValue() {
        return value;
    }

    public final void setValue(int value) {
        this.value = Math.max(0, value);
    }

    public int getMinOrder() {
        return minOrder;
    }

    public final void setMinOrder(int minOrder) {
        this.minOrder = Math.max(0, minOrder);
    }

    public int getMaxDiscount() {
        return maxDiscount;
    }

    public final void setMaxDiscount(int maxDiscount) {
        this.maxDiscount = Math.max(0, maxDiscount);
    }

    public String getCities() {
        return cities;
    }

    public void setCities(String cities) {
        this.cities = (cities == null) ? "" : cities.trim();
    }

    public String getValidTill() {
        return validTill;
    }

    public void setValidTill(String validTill) {
        this.validTill = (validTill == null) ? "" : validTill.trim();
    }

    public Scope getScope() {
        return scope;
    }

    public final void setScope(Scope scope) {
        this.scope = (scope == null) ? Scope.ANY : scope;
    }

    public String getUsedBy() {
        return usedBy;
    }

    public void setUsedBy(String usedBy) {
        this.usedBy = (usedBy == null) ? "" : usedBy.trim();
    }

    public String getDisabledOn() {
        return disabledOn;
    }

    public void setDisabledOn(String disabledOn) {
        this.disabledOn = (disabledOn == null) ? "" : disabledOn.trim();
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /* ------------------------------------------------------------------ *\
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static int toInt(String text, int fallback) {
        try {
            return Integer.parseInt(UiUtil.clean(text));
        } catch (Exception e) {
            return fallback;
        }
    }
}