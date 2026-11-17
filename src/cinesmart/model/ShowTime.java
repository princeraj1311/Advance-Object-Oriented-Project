package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ShowTime joins a Movie to a Theatre at a moment in time (Stage 3). This class IS the
 * "association" between the two: it does not store a Movie object, only the movieId, which is
 * how relational data works and it keeps the text files small.
 *
 * ShowTime is Comparable, so Collections.sort(...) can put every show of a city in time order
 * without writing the comparison twice.
 *
 * Data line layout (9 required columns + 1 optional Stage 4 column):
 * showId|movieId|theatreId|date|startTime|screenName|price|totalSeats|bookedSeats|reservedSeats
 *
 * The last column is the Stage 4 seat-level detail ("A1;C7;C8"). It is optional on purpose: a
 * file written by Stage 3 has only nine columns and still loads, which is how a real project
 * grows a file format without breaking the data the user already has.
 */
public class ShowTime implements Comparable<ShowTime> {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** Minimum number of columns a show-time line must carry. */
    public static final int FIELD_COUNT = 9;

    /** Seat labels inside the optional tenth column are kept apart with this character. */
    public static final String SEAT_SEPARATOR_TEXT = ";";

    /** How many days ahead the sample data (and the date filter) offers shows. */
    public static final int DAYS_AHEAD = 3;

    private String showId;
    private String scheduleId;   // template this show was generated from, empty for legacy shows
    private String movieId;
    private String theatreId;
    private String date;       // yyyy-MM-dd
    private String startTime;  // HH:mm, 24 hour clock
    private String screenName;
    private int price;
    private int totalSeats;
    private int bookedSeats;

    /** Lifecycle state of this date-specific show instance. */
    public enum Status {
        UPCOMING("Upcoming"),
        COMPLETED("Completed");

        private final String label;
        Status(String label) { this.label = label; }
        public String label() { return label; }
        public static Status parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Status status : values()) {
                if (status.name().equalsIgnoreCase(wanted)) {
                    return status;
                }
            }
            return UPCOMING;
        }
    }

    /**
     * Stage 4: the exact seats already held for this show. Kept as a set so "is A7 taken?" is a
     * direct answer instead of a scan, and kept sorted on write so the file stays stable.
     */
    private final Set<String> reservedSeats = new LinkedHashSet<>();

    private Status status = Status.UPCOMING;

    public ShowTime() {
        this.showId = "";
        this.scheduleId = "";
        this.movieId = "";
        this.theatreId = "";
        this.date = LocalDate.now().toString();
        this.startTime = "19:00";
        this.screenName = "Screen 1";
        this.price = 150;
        this.totalSeats = 120;
        this.bookedSeats = 0;
        this.status = Status.UPCOMING;
    }

    /** Constructor used by the sample data generator. */
    public ShowTime(String showId, String movieId, String theatreId, String date, String startTime,
                    String screenName, int price, int totalSeats, int bookedSeats) {
        this();
        setShowId(showId);
        setMovieId(movieId);
        setTheatreId(theatreId);
        setDate(date);
        setStartTime(startTime);
        setScreenName(screenName);
        setPrice(price);
        setTotalSeats(totalSeats);
        setBookedSeats(bookedSeats);
    }

    /** Constructor used when one line is read back from data/showtimes.txt. */
    public ShowTime(String[] parts) {
        this();
        setShowId(part(parts, 0));
        setMovieId(part(parts, 1));
        setTheatreId(part(parts, 2));
        setDate(part(parts, 3));
        setStartTime(part(parts, 4));
        setScreenName(part(parts, 5));
        setPrice(toInt(part(parts, 6), 150));
        setTotalSeats(toInt(part(parts, 7), 120));
        setBookedSeats(toInt(part(parts, 8), 0));
        setReservedSeats(part(parts, 9)); // empty on a Stage 3 file, which is still valid
        // New optional columns (Stage 11): scheduleId and status, absent on legacy files.
        if (parts.length > 10) {
            setScheduleId(part(parts, 10));
        }
        if (parts.length > 11) {
            setStatus(Status.parse(part(parts, 11)));
        }
    }

    /* ------------------------------------------------------------------ *
     * Behaviour
     * ------------------------------------------------------------------ */

    /** The date and time together, used for sorting and for the "is it over?" check. */
    public LocalDateTime dateTime() {
        try {
            return LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(startTime));
        } catch (Exception e) {
            return LocalDateTime.of(LocalDate.now(), LocalTime.of(23, 59));
        }
    }

    /** Seats nobody has taken yet - the number shown on the show-time button. */
    public int seatsAvailable() {
        return Math.max(0, totalSeats - bookedSeats);
    }

    public boolean isSoldOut() {
        return seatsAvailable() == 0;
    }

    /** True when the show already started, so it must not be bookable any more. */
    public boolean isPast() {
        return dateTime().isBefore(LocalDateTime.now());
    }

    /** Bookable = in the future and has seats left. Used by the home screen buttons. */
    public boolean isBookable() {
        return !isPast() && !isSoldOut();
    }

    /** "7:45 pm" - what customers expect to read, while the file keeps 24 hour time. */
    public String startTimeText() {
        try {
            switch (startTime) {
                case "09:00":
                    return "9:00 AM - 12:00 PM";
                case "14:30":
                    return "2:30 PM - 5:00 PM";
                case "21:00":
                    return "9:00 PM - 12:00 AM";
                default:
                    return LocalTime.parse(startTime).format(DateTimeFormatter.ofPattern("h:mm a"));
            }
        } catch (Exception e) {
            return startTime;
        }
    }

    /** "Today", "Tomorrow" or "Wed 05 Oct" - friendlier than a raw ISO date. */
    public String dateText() {
        try {
            LocalDate day = LocalDate.parse(date);
            if (day.equals(LocalDate.now())) {
                return "Today";
            }
            if (day.equals(LocalDate.now().plusDays(1))) {
                return "Tomorrow";
            }
            return day.format(DateTimeFormatter.ofPattern("EEE dd MMM"));
        } catch (Exception e) {
            return date;
        }
    }

    /** The label of a show-time button: "Today 7:45 pm . Screen 3 . 84 seats". */
    public String label() {
        return dateText() + "  " + startTimeText() + "  \u00b7  " + screenName
                + "  \u00b7  " + seatsAvailable() + " seats";
    }

    public String priceText() {
        return "Rs " + price;
    }

    /** Called by Stage 4/5 once seats are confirmed for this show. */
    public void bookSeats(int seatCount) {
        setBookedSeats(bookedSeats + Math.max(0, seatCount));
    }

    /* ------------------------------------------------------------------ *\
     * Stage 4: seats by name, not only by count
     * ------------------------------------------------------------------ */

    /**
     * True when this particular seat is already held. An unknown label counts as free, because
     * SeatMap only ever asks about seats that exist on this screen.
     */
    public boolean isSeatReserved(String seatLabel) {
        return reservedSeats.contains(UiUtil.clean(seatLabel).toUpperCase());
    }

    /** The held seats in label order, so two runs of the program write the same file line. */
    public List<String> reservedSeatLabels() {
        List<String> sorted = new ArrayList<>(reservedSeats);
        Collections.sort(sorted, SeatMap::byPosition);
        return sorted;
    }

    /** Adds seats to the hold and keeps the summary count honest (see syncBookedCount). */
    public void reserveSeats(Collection<String> seatLabels) {
        if (seatLabels == null) {
            return;
        }
        int added = 0;
        for (String label : seatLabels) {
            String seat = UiUtil.clean(label).toUpperCase();
            // Set.add answers true only for a seat that was not held yet, so holding the same seat
            // twice cannot count it twice.
            if (!seat.isEmpty() && reservedSeats.add(seat)) {
                added++;
            }
        }
        // Every seat named now is also one more sold seat. Seats that exist only as a count (any
        // Stage 3 line) are untouched, and Stage 6 gives exactly these back out again on a cancel.
        setBookedSeats(bookedSeats + added);
    }

    /** Stage 6 uses this when a booking is cancelled before the show starts. */
    public void releaseSeats(Collection<String> seatLabels) {
        if (seatLabels == null) {
            return;
        }
        int freed = 0;
        for (String label : seatLabels) {
            if (reservedSeats.remove(UiUtil.clean(label).toUpperCase())) {
                freed++;
            }
        }
        // Lower the summary count by exactly what was released, so a show whose seat list was
        // never filled in (a Stage 3 file) is not wrongly emptied by a release.
        setBookedSeats(bookedSeats - freed);
        syncBookedCount(); // the count is never allowed to fall below the seats still held by name
    }

    public void clearReservedSeats() {
        reservedSeats.clear();
        setBookedSeats(0);
    }

    /**
     * A show read from a Stage 3 file knows HOW MANY seats are gone but not WHICH ones. The
     * count is the truth that matters to the customer, so the count only ever grows from the
     * seat list, never shrinks because the list has not been filled in yet.
     */
    private void syncBookedCount() {
        if (reservedSeats.size() > bookedSeats) {
            setBookedSeats(reservedSeats.size());
        }
    }

    /** Natural order: earliest show first, ties broken by theatre then screen. */
    @Override
    public int compareTo(ShowTime other) {
        int byTime = dateTime().compareTo(other.dateTime());
        if (byTime != 0) {
            return byTime;
        }
        return (theatreId + screenName).compareTo(other.theatreId + other.screenName);
    }

    /* ------------------------------------------------------------------ *
     * Saving an object as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT,
                showId, movieId, theatreId, date, startTime, screenName,
                String.valueOf(price), String.valueOf(totalSeats), String.valueOf(bookedSeats),
                reservedSeatsText(), scheduleId, status.name());
    }

    public static ShowTime fromDataLine(String line) {
        String[] parts = line.split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("Incomplete show-time record: " + line);
        }
        return new ShowTime(parts);
    }

    /* ------------------------------------------------------------------ *
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getShowId() {
        return showId;
    }

    public void setShowId(String showId) {
        this.showId = UiUtil.clean(showId);
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = UiUtil.clean(scheduleId);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = (status == null) ? Status.UPCOMING : status;
    }

    /** True when this show has not started yet. */
    public boolean isUpcoming() {
        return status == Status.UPCOMING && !isPast();
    }

    /** Mark this show COMPLETED (called by the daily archive job). */
    public void markCompleted() {
        this.status = Status.COMPLETED;
    }

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = UiUtil.clean(movieId);
    }

    public String getTheatreId() {
        return theatreId;
    }

    public void setTheatreId(String theatreId) {
        this.theatreId = UiUtil.clean(theatreId);
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        String clean = UiUtil.clean(date);
        try {
            LocalDate.parse(clean);
            this.date = clean;
        } catch (Exception e) {
            this.date = LocalDate.now().toString();
        }
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        String clean = UiUtil.clean(startTime);
        try {
            LocalTime.parse(clean);
            this.startTime = clean;
        } catch (Exception e) {
            this.startTime = "19:00";
        }
    }

    public String getScreenName() {
        return screenName;
    }

    public void setScreenName(String screenName) {
        this.screenName = UiUtil.clean(screenName);
    }

    public int getPrice() {
        return price;
    }

    public final void setPrice(int price) {
        this.price = (price < 50 || price > 3000) ? 150 : price;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public final void setTotalSeats(int totalSeats) {
        this.totalSeats = (totalSeats < 10 || totalSeats > 400) ? 120 : totalSeats;
    }

    public int getBookedSeats() {
        return bookedSeats;
    }

    /**
     * Never allows more booked seats than the screen has, so a hand edited file cannot make
     * the availability negative. totalSeats must therefore be set BEFORE this setter.
     */
    public final void setBookedSeats(int bookedSeats) {
        this.bookedSeats = Math.min(Math.max(0, bookedSeats), totalSeats);
    }

    /** The tenth data column, e.g. "A1;C7;C8" (empty when nobody has held a seat yet). */
    public String reservedSeatsText() {
        return String.join(SEAT_SEPARATOR_TEXT, reservedSeatLabels());
    }

    /** Reads the tenth data column back. Unknown text is dropped rather than trusted. */
    public final void setReservedSeats(String text) {
        reservedSeats.clear();
        String clean = UiUtil.clean(text);
        if (clean.isEmpty()) {
            return;
        }
        for (String part : clean.split(SEAT_SEPARATOR_TEXT)) {
            String seat = part.trim().toUpperCase();
            if (!seat.isEmpty()) {
                reservedSeats.add(seat);
            }
        }
        syncBookedCount();
    }

    /** How many seats of this show are held by name (0 while the data is count-only). */
    public int reservedSeatCount() {
        return reservedSeats.size();
    }

    @Override
    public String toString() {
        return label();
    }

    /* ------------------------------------------------------------------ *
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static int toInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
