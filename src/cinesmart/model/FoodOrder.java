package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * FoodOrder is the Stage 10 record of one food order: who ordered, from which hall, what and how
 * much. It is the same shape of idea as {@link Booking} - an id, a customer, a total and a state
 * the file always remembers - but for the food counter instead of the seats.
 *
 * Like every other model here it stores IDS, never objects: the lines column holds
 * "F002 x 2;F007 x 1", and {@link cinesmart.service.CanteenService} joins the item ids back to the
 * menu when the order is printed. The hall name is stored as well, because an order must still
 * make sense as a receipt if the cinema is renamed or removed later.
 *
 * Data line layout (9 columns):
 * FO202609301245|demo|T002|PVR Orion - Koramangala|F002 x 2;F007 x 1|900|2026-09-30 12:45|PLACED|
 */
public class FoodOrder {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** Lines inside the order are kept apart with this character, like seats in a booking. */
    public static final String LINE_SEPARATOR_TEXT = ";";
    public static final int FIELD_COUNT = 9;

    /** Where an order is in its short life. */
    public enum Status {
        PLACED("Order placed"),
        READY("Ready at the counter"),
        CANCELLED("Cancelled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unknown text in a file falls back to PLACED instead of breaking the load. */
        public static Status parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Status status : values()) {
                if (status.name().equalsIgnoreCase(wanted)) {
                    return status;
                }
            }
            return PLACED;
        }
    }

    private String orderId;
    private String userName;
    private String theatreId;
    private String theatreLabel;
    private String lines;
    private int total;
    private String placedAt;
    private Status status;
    /** Optional link to the ticket booking the food is for, so it arrives before the film. */
    private String bookingId;

    public FoodOrder() {
        this.orderId = "";
        this.userName = "";
        this.theatreId = "";
        this.theatreLabel = "";
        this.lines = "";
        this.total = 0;
        this.placedAt = "";
        this.status = Status.PLACED;
        this.bookingId = "";
    }

    /** Constructor used when the customer confirms an order. */
    public FoodOrder(String orderId, String userName, String theatreId, String theatreLabel,
                     String lines, int total, String bookingId) {
        this();
        setOrderId(orderId);
        setUserName(userName);
        setTheatreId(theatreId);
        setTheatreLabel(theatreLabel);
        setLines(lines);
        setTotal(total);
        this.placedAt = LocalDateTime.now().format(ORDER_STAMP);
        this.bookingId = (bookingId == null) ? "" : bookingId.trim();
    }

    /** Constructor used when one line is read back from data/foodorders.txt. */
    public FoodOrder(String[] parts) {
        this();
        setOrderId(part(parts, 0));
        setUserName(part(parts, 1));
        setTheatreId(part(parts, 2));
        setTheatreLabel(part(parts, 3));
        setLines(part(parts, 4));
        setTotal(toInt(part(parts, 5), 0));
        setPlacedAt(part(parts, 6));
        setStatus(Status.parse(part(parts, 7)));
        setBookingId(part(parts, 8));
    }

    /* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /** The hall name on screen; falls back to the id when the name was somehow lost. */
    public String hallName() {
        return theatreLabel.isEmpty() ? theatreId : theatreLabel;
    }

    /** "Rs 900" - the order total. */
    public String totalText() {
        return "Rs " + total;
    }

    @Override
    public String toString() {
        return hallName() + "  " + status.label() + "  " + totalText();
    }

    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, orderId, userName, theatreId, theatreLabel, lines,
                String.valueOf(total), placedAt, status.name(), bookingId);
    }

    public static FoodOrder fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty food order line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a food order line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new FoodOrder(parts);
    }

    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = UiUtil.clean(orderId);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = UiUtil.clean(userName);
    }

    public String getTheatreId() {
        return theatreId;
    }

    public void setTheatreId(String theatreId) {
        this.theatreId = UiUtil.clean(theatreId);
    }

    /** "PVR Orion - Koramangala" - the hall and its area, as shown on the receipt. */
    public String getTheatreLabel() {
        return theatreLabel;
    }

    public void setTheatreLabel(String theatreLabel) {
        this.theatreLabel = UiUtil.clean(theatreLabel);
    }

    /** "F002 x 2;F007 x 1" - the raw lines column. */
    public String getLines() {
        return lines;
    }

    public void setLines(String lines) {
        this.lines = (lines == null) ? "" : lines.trim();
    }

    public int getTotal() {
        return total;
    }

    public final void setTotal(int total) {
        this.total = Math.max(0, total);
    }

    public String getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(String placedAt) {
        this.placedAt = UiUtil.clean(placedAt);
    }

    public Status getStatus() {
        return status;
    }

    public final void setStatus(Status status) {
        this.status = (status == null) ? Status.PLACED : status;
    }

    /** Empty when the food is not tied to a ticket booking. */
    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = (bookingId == null) ? "" : bookingId.trim();
    }

    /* ------------------------------------------------------------------ *\
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static final DateTimeFormatter ORDER_STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
