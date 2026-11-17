package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * GiftCard is the prepaid card a customer buys and then pays for tickets and food with.
 *
 * The card keeps a running balance rather than a spent/total pair, because that is what makes a
 * refund or a failed payment safe: the balance only ever changes inside {@link #spend}, which
 * refuses to take more than is there. Every spend is also appended to the history column, so the
 * card says where its money went - a gift card that cannot be audited is not a gift card, it is a
 * discount.
 *
 * Data line layout (9 columns):
 * GC0001|CSGIFT-4821-KQ|demo|2000|650|2026-10-01 19:20|2027-04-01|ACTIVE|2006-10-01 20:05 Rs 1350 CS2026..
 */
public class GiftCard {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** Entries inside the history column are kept apart with this character. */
    public static final String HISTORY_SEPARATOR_TEXT = ";";
    public static final int FIELD_COUNT = 9;

    /** Most money a card can be loaded with, and most one order may take from it. */
    public static final int MAX_VALUE = 50000;

    /** The denominations the gift card screen offers, the way a counter sells them. */
    public static final int[] DENOMINATIONS = {500, 1000, 2000, 5000};

    /** Where a card is in its short life. */
    public enum Status {
        ACTIVE("Active"),
        EXPIRED("Expired"),
        BLOCKED("Blocked");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unknown text in a file falls back to ACTIVE instead of breaking the load. */
        public static Status parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Status status : values()) {
                if (status.name().equalsIgnoreCase(wanted)) {
                    return status;
                }
            }
            return ACTIVE;
        }
    }

    private String cardId;
    private String code;
    private String ownerName;
    /** What was paid for the card when it was bought. */
    private int denomination;
    /** What is still on it. Never negative: spend() refuses rather than going negative. */
    private int balance;
    private String issuedAt;
    /** Last day the card pays for something, as yyyy-MM-dd. */
    private String expiresOn;
    private Status status;
    /** "date Rs amount ref;date Rs amount ref;" - what was spent and when. */
    private String history;

    public GiftCard() {
        this.cardId = "";
        this.code = "";
        this.ownerName = "";
        this.denomination = 0;
        this.balance = 0;
        this.issuedAt = "";
        this.expiresOn = "";
        this.status = Status.ACTIVE;
        this.history = "";
    }

    /** Constructor used when a customer buys a card. */
    public GiftCard(String cardId, String code, String ownerName, int denomination,
                    String expiresOn) {
        this();
        setCardId(cardId);
        setCode(code);
        setOwnerName(ownerName);
        setDenomination(denomination);
        this.balance = denomination;
        this.issuedAt = LocalDateTime.now().format(STAMP);
        setExpiresOn(expiresOn);
    }

    /** Constructor used when one line is read back from data/giftcards.txt. */
    public GiftCard(String[] parts) {
        this();
        setCardId(part(parts, 0));
        setCode(part(parts, 1));
        setOwnerName(part(parts, 2));
        setDenomination(toInt(part(parts, 3), 0));
        setBalance(toInt(part(parts, 4), 0));
        setIssuedAt(part(parts, 5));
        setExpiresOn(part(parts, 6));
        setStatus(Status.parse(part(parts, 7)));
        setHistory(part(parts, 8));
    }
/* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /** Whether this card may pay for something right now. */
    public boolean isUsableOn(LocalDate today) {
        if (status != Status.ACTIVE || balance <= 0) {
            return false;
        }
        return !isExpiredOn(today);
    }

    public boolean isExpiredOn(LocalDate today) {
        return today != null && !expiresOn.isEmpty() && today.isAfter(expiryDate());
    }

    /** A malformed date must not expire every card, so bad text means "never expires". */
    private LocalDate expiryDate() {
        try {
            return LocalDate.parse(expiresOn, DATE);
        } catch (Exception e) {
            return LocalDate.MAX;
        }
    }

    /** Whether this card may cover an order of {@code amount} rupees right now. */
    public boolean canCover(int amount) {
        return amount > 0 && balance >= amount;
    }

    /**
     * Takes {@code amount} rupees off the card and appends the spend to its history.
     *
     * The method refuses to go negative on purpose and returns false, so the caller shows a
     * message instead of writing a card that owes money. The balance is only changed after the
     * check passes, so a refused spend leaves the card exactly as it was.
     *
     * @param reference the booking or food order id the money was spent on
     * @return false when there is not enough on the card
     */
    public boolean spend(int amount, String reference) {
        if (amount <= 0 || balance < amount) {
            return false;
        }
        balance -= amount;
        appendHistory(amount, reference);
        return true;
    }

    /** Puts money back on the card, used when a cancelled booking is refunded to it. */
    public void refund(int amount, String reference) {
        if (amount <= 0) {
            return;
        }
        balance = Math.min(MAX_VALUE, balance + amount);
        history = history + LocalDateTime.now().format(STAMP) + " +Rs " + amount + " "
                + UiUtil.clean(reference) + HISTORY_SEPARATOR_TEXT;
    }

    /** One line per movement, oldest first, for the history list on the screen. */
    public java.util.List<String> historyLines() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (history.isEmpty()) {
            return lines;
        }
        for (String entry : history.split(HISTORY_SEPARATOR_TEXT)) {
            String text = entry.trim();
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        return lines;
    }

    /** How much has been used so far - the number a customer checks against their receipt. */
    public int spent() {
        return Math.max(0, denomination - balance);
    }

    /** "Rs 650 of Rs 2000 left" - the balance line on the wallet card. */
    public String balanceText() {
        return "Rs " + balance + " of Rs " + denomination + " left";
    }

    /** The expiry line, or a plain statement when the card never expires. */
    public String expiryText() {
        return expiresOn.isEmpty() ? "No expiry date" : "Expires on " + expiresOn;
    }

    @Override
    public String toString() {
        return code + "  Rs " + balance;
    }

    /** Appends one movement, so the history column stays a truthful, readable log. */
    private void appendHistory(int amount, String reference) {
        history = history + LocalDateTime.now().format(STAMP) + " -Rs " + amount + " "
                + UiUtil.clean(reference) + HISTORY_SEPARATOR_TEXT;
    }

    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, cardId, code, ownerName, String.valueOf(denomination),
                String.valueOf(balance), issuedAt, expiresOn, status.name(), history);
    }

    public static GiftCard fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty gift card line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a gift card line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new GiftCard(parts);
    }

    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = UiUtil.clean(cardId);
    }

    public String getCode() {
        return code;
    }

    /** Codes are typed by hand, so they are stored in one exact capitalised shape. */
    public final void setCode(String code) {
        this.code = UiUtil.clean(code).toUpperCase();
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = UiUtil.clean(ownerName).toLowerCase();
    }

    public int getDenomination() {
        return denomination;
    }

    public final void setDenomination(int denomination) {
        this.denomination = clamp(denomination);
    }

    public int getBalance() {
        return balance;
    }

    public final void setBalance(int balance) {
        this.balance = clamp(balance);
    }

    public String getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(String issuedAt) {
        this.issuedAt = UiUtil.clean(issuedAt);
    }

    public String getExpiresOn() {
        return expiresOn;
    }

    public void setExpiresOn(String expiresOn) {
        this.expiresOn = (expiresOn == null) ? "" : expiresOn.trim();
    }

    public Status getStatus() {
        return status;
    }

    public final void setStatus(Status status) {
        this.status = (status == null) ? Status.ACTIVE : status;
    }

    public String getHistory() {
        return history;
    }

    public void setHistory(String history) {
        this.history = (history == null) ? "" : history.trim();
    }

    /** Money is never negative and never above the card limit, whatever the file says. */
    private static int clamp(int money) {
        return Math.max(0, Math.min(MAX_VALUE, money));
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
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