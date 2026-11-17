package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Booking is the Stage 5 record of one purchase: who booked it, which show, which seats and what
 * it costs.
 *
 * The booking stores ids, never objects - {@link #getShowId()} points at a ShowTime exactly like a
 * ShowTime points at a movie - so the film title and the cinema name are joined by
 * {@link cinesmart.service.BookingService} when a ticket is printed, and this class stays free of
 * Swing and of the catalogue.
 *
 * A booking lives in one of three states, and the file always remembers which one:
 * AWAITING_PAYMENT (the seats are held, nobody has paid yet), PAID (the money was taken and the
 * booking IS the ticket) and CANCELLED (Stage 6 gave the seats back and refunded the amount).
 *
 * Data line layout (10 columns):
 * CS20260930124512|demo|S0030|E6;E8;F6;F7|800|Card|TXN20260930124531|PAID|2026-09-30 12:45|
 */
public class Booking implements Comparable<Booking> {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** Seats inside one column are kept apart with this character, like showtimes.txt does. */
    public static final String SEAT_SEPARATOR_TEXT = ";";
    /** Columns a booking line must carry. */
    public static final int FIELD_COUNT = 10;

    /** The three states of a booking; the text in the file is exactly this name. */
    public enum Status {
        AWAITING_PAYMENT("Awaiting payment"),
        PAID("Paid"),
        CANCELLED("Cancelled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        /** What the customer reads on the booking card. */
        public String label() {
            return label;
        }

        /** Unknown text in a file falls back to the first state instead of breaking the load. */
        public static Status parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Status status : values()) {
                if (status.name().equalsIgnoreCase(wanted)) {
                    return status;
                }
            }
            return AWAITING_PAYMENT;
        }
    }

    private String bookingId;
    private String userName;
    private String showId;
    private final List<String> seats = new ArrayList<>();
    private int amount;
    private String method;
    private String paymentRef;
    private Status status;
    private String bookedAt;    // yyyy-MM-dd HH:mm
    private String cancelledAt; // empty until Stage 6 cancels

    public Booking() {
        this.bookingId = "";
        this.userName = "";
        this.showId = "";
        this.method = "";
        this.paymentRef = "";
        this.status = Status.AWAITING_PAYMENT;
        this.bookedAt = "";
        this.cancelledAt = "";
    }

    /** Constructor used when the seats are held and the booking is opened. */
    public Booking(String bookingId, String userName, String showId, List<String> seats, int amount) {
        this();
        setBookingId(bookingId);
        setUserName(userName);
        setShowId(showId);
        setSeats(seats);
        setAmount(amount);
        this.bookedAt = LocalDateTime.now().format(BOOKING_STAMP);
    }

    /** Constructor used when one line is read back from data/bookings.txt. */
    public Booking(String[] parts) {
        this();
        setBookingId(part(parts, 0));
        setUserName(part(parts, 1));
        setShowId(part(parts, 2));
        setSeats(part(parts, 3));
        setAmount(toInt(part(parts, 4), 0));
        setMethod(part(parts, 5));
        setPaymentRef(part(parts, 6));
        setStatus(Status.parse(part(parts, 7)));
        setBookedAt(part(parts, 8));
        setCancelledAt(part(parts, 9));
    }
    /* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /** How many tickets this booking is worth. */
    public int seatCount() {
        return seats.size();
    }

    /** "E6, E8, F6, F7" - the seats in the order they were taken. */
    public String seatsText() {
        return String.join(", ", seats);
    }

    public boolean isPaid() {
        return status == Status.PAID;
    }

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    public boolean isAwaitingPayment() {
        return status == Status.AWAITING_PAYMENT;
    }

    /** Payment succeeded: remember how and under which transaction id. */
    public void markPaid(String method, String paymentRef) {
        this.method = UiUtil.clean(method);
        this.paymentRef = UiUtil.clean(paymentRef);
        this.status = Status.PAID;
        this.cancelledAt = "";
    }

    /** Stage 6: the booking is finished and the seats were given back. */
    public void markCancelled() {
        this.status = Status.CANCELLED;
        this.cancelledAt = LocalDateTime.now().format(BOOKING_STAMP);
    }

    /** "Paid by card (TXN...)" while paid, empty while the booking is not paid for. */
    public String paymentText() {
        if (!isPaid()) {
            return "";
        }
        return "Paid by " + method + (paymentRef.isEmpty() ? "" : "  (" + paymentRef + ")");
    }
    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getBookingId() {
        return bookingId;
    }

    public final void setBookingId(String bookingId) {
        this.bookingId = UiUtil.clean(bookingId);
    }

    public String getUserName() {
        return userName;
    }

    /** Stored in lower case, so "Demo" and "demo" always find the same bookings. */
    public final void setUserName(String userName) {
        this.userName = UiUtil.clean(userName).toLowerCase();
    }

    public String getShowId() {
        return showId;
    }

    public final void setShowId(String showId) {
        this.showId = UiUtil.clean(showId);
    }

    public List<String> getSeats() {
        return new ArrayList<>(seats);
    }

    /** From a list; the setter drops anything that is not a seat label. */
    public final void setSeats(List<String> values) {
        seats.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            String seat = UiUtil.clean(value).toUpperCase();
            if (!seat.isEmpty()) {
                seats.add(seat);
            }
        }
    }

    /** Reads the seat column back ("E6;E8;F6;F7"). */
    public final void setSeats(String text) {
        seats.clear();
        String clean = UiUtil.clean(text);
        if (clean.isEmpty()) {
            return;
        }
        for (String part : clean.split(SEAT_SEPARATOR_TEXT)) {
            String seat = part.trim().toUpperCase();
            if (!seat.isEmpty()) {
                seats.add(seat);
            }
        }
    }

    public String getSeatsText() {
        return String.join(SEAT_SEPARATOR_TEXT, seats);
    }

    public int getAmount() {
        return amount;
    }

    /** Never negative: a refund belongs to a cancelled booking, not to minus money. */
    public final void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public String getMethod() {
        return method;
    }

    public final void setMethod(String method) {
        this.method = UiUtil.clean(method);
    }

    public String getPaymentRef() {
        return paymentRef;
    }

    public final void setPaymentRef(String paymentRef) {
        this.paymentRef = UiUtil.clean(paymentRef);
    }

    public Status getStatus() {
        return status;
    }

    public final void setStatus(Status status) {
        this.status = (status == null) ? Status.AWAITING_PAYMENT : status;
    }

    public String getBookedAt() {
        return bookedAt;
    }

    public final void setBookedAt(String bookedAt) {
        this.bookedAt = UiUtil.clean(bookedAt);
    }

    public String getCancelledAt() {
        return cancelledAt;
    }

    public final void setCancelledAt(String cancelledAt) {
        this.cancelledAt = UiUtil.clean(cancelledAt);
    }
    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, bookingId, userName, showId, getSeatsText(),
                String.valueOf(amount), method, paymentRef, status.name(), bookedAt, cancelledAt);
    }

    /** Reads one file line back into an object; a broken line is reported, never fatal. */
    public static Booking fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty booking line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a booking line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new Booking(parts);
    }

    /** Newest booking first, which is the order the list on screen wants. */
    @Override
    public int compareTo(Booking other) {
        return other.bookedAt.compareTo(bookedAt);
    }

    @Override
    public String toString() {
        return bookingId + "  " + status.label() + "  " + seatsText() + "  Rs " + amount;
    }

    /* ------------------------------------------------------------------ *\
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static final DateTimeFormatter BOOKING_STAMP =
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