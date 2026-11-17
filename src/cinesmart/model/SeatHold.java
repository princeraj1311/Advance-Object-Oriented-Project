package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SeatHold is a temporary reservation of one seat for one user on one show (Feature 1).
 *
 * A hold is NOT a booking: the seat stays AVAILABLE to the map until the hold expires (5 min by
 * default) or is converted to BOOKED on successful payment. Two users cannot hold the same seat
 * because hold creation is atomic in {@link cinesmart.service.BookingService}.
 *
 * Data line layout (6 columns):
 * holdId|showId|seatNumber|userName|heldAt|expiresAt
 *
 *   heldAt / expiresAt - yyyy-MM-dd HH:mm
 */
public class SeatHold {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 6;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private String holdId;
    private String showId;
    private String seatNumber;
    private String userName;
    private String heldAt;    // yyyy-MM-dd HH:mm
    private String expiresAt; // yyyy-MM-dd HH:mm

    public SeatHold() {
        this.holdId = "";
        this.showId = "";
        this.seatNumber = "";
        this.userName = "";
        this.heldAt = "";
        this.expiresAt = "";
    }

    public SeatHold(String holdId, String showId, String seatNumber, String userName,
                    String heldAt, String expiresAt) {
        this();
        setHoldId(holdId);
        setShowId(showId);
        setSeatNumber(seatNumber);
        setUserName(userName);
        setHeldAt(heldAt);
        setExpiresAt(expiresAt);
    }

    public SeatHold(String[] parts) {
        this();
        setHoldId(part(parts, 0));
        setShowId(part(parts, 1));
        setSeatNumber(part(parts, 2));
        setUserName(part(parts, 3));
        setHeldAt(part(parts, 4));
        setExpiresAt(part(parts, 5));
    }

    /** True when the hold is still live (not yet expired). */
    public boolean isActive() {
        if (expiresAt == null || expiresAt.isEmpty()) {
            return false;
        }
        try {
            return LocalDateTime.now().isBefore(LocalDateTime.parse(expiresAt, STAMP));
        } catch (Exception e) {
            return false;
        }
    }

    /** Seconds remaining on this hold, or 0 when expired. */
    public long secondsRemaining() {
        if (!isActive()) {
            return 0;
        }
        try {
            return java.time.Duration.between(LocalDateTime.now(),
                    LocalDateTime.parse(expiresAt, STAMP)).getSeconds();
        } catch (Exception e) {
            return 0;
        }
    }

    public String getHoldId() { return holdId; }
    public void setHoldId(String holdId) { this.holdId = UiUtil.clean(holdId); }
    public String getShowId() { return showId; }
    public void setShowId(String showId) { this.showId = UiUtil.clean(showId); }
    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = UiUtil.clean(seatNumber).toUpperCase(); }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = UiUtil.clean(userName); }
    public String getHeldAt() { return heldAt; }
    public void setHeldAt(String heldAt) { this.heldAt = UiUtil.clean(heldAt); }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = UiUtil.clean(expiresAt); }

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, holdId, showId, seatNumber, userName, heldAt, expiresAt);
    }

    public static SeatHold fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty seat hold line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a seat hold line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new SeatHold(parts);
    }

    @Override
    public String toString() {
        return holdId + "  " + seatNumber + "  " + userName + "  expires " + expiresAt;
    }

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }
}
