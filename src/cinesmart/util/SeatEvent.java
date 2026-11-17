package cinesmart.util;


/**
 * SeatEvent broadcasts seat status changes so every seat map viewing the same show updates
 * live without a page refresh (Feature 2 real-time seat colors).
 */
public final class SeatEvent {

    public enum Type {
        SEAT_HELD,       // someone started holding a seat
        SEAT_BOOKED,     // payment succeeded, HELD became BOOKED
        SEAT_RELEASED,   // hold expired or was cancelled
        HOLDS_EXPIRED,   // expiry sweep released one or more holds
    }

    public final Type type;
    /** The show whose seats changed; null means "all shows". */
    public final String showId;
    /** The seat label affected (may be null for HOLDS_EXPIRED). */
    public final String seatNumber;
    /** The user whose hold changed (may be null). */
    public final String userName;

    public SeatEvent(Type type, String showId, String seatNumber, String userName) {
        this.type = type;
        this.showId = showId;
        this.seatNumber = seatNumber;
        this.userName = userName;
    }

    public SeatEvent(Type type, String showId) {
        this(type, showId, null, null);
    }
}
