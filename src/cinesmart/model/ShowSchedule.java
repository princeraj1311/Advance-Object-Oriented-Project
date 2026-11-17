package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;

/**
 * ShowSchedule is the template behind a screening: which film plays in which theatre/screen at
 * which time of day, and for how long the film's run lasts. It contains NO date - every calendar
 * day gets its own Show instance generated from this schedule (see ShowScheduleService).
 *
 * Data line layout (9 columns):
 * scheduleId|movieId|theatreId|screenName|timeSlot|price|active|validFrom|validTill
 */
public class ShowSchedule {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 9;

    private String scheduleId;
    private String movieId;
    private String theatreId;
    private String screenName;
    private String timeSlot;     // HH:mm
    private int price;
    private boolean active = true;
    private String validFrom;    // yyyy-MM-dd, empty = unbounded start
    private String validTill;    // yyyy-MM-dd, empty = unbounded end

    public ShowSchedule() {
        this.scheduleId = "";
        this.movieId = "";
        this.theatreId = "";
        this.screenName = "Screen 1";
        this.timeSlot = "09:00";
        this.price = 150;
        this.active = true;
        this.validFrom = "";
        this.validTill = "";
    }

    public ShowSchedule(String scheduleId, String movieId, String theatreId, String screenName,
                        String timeSlot, int price, boolean active, String validFrom, String validTill) {
        this();
        setScheduleId(scheduleId);
        setMovieId(movieId);
        setTheatreId(theatreId);
        setScreenName(screenName);
        setTimeSlot(timeSlot);
        setPrice(price);
        setActive(active);
        setValidFrom(validFrom);
        setValidTill(validTill);
    }

    public ShowSchedule(String[] parts) {
        this();
        setScheduleId(part(parts, 0));
        setMovieId(part(parts, 1));
        setTheatreId(part(parts, 2));
        setScreenName(part(parts, 3));
        setTimeSlot(part(parts, 4));
        setPrice(toInt(part(parts, 5), 150));
        setActive("true".equalsIgnoreCase(part(parts, 6)));
        setValidFrom(part(parts, 7));
        setValidTill(part(parts, 8));
    }

    /** True when this schedule should generate a show for the given date. */
    public boolean isActiveOn(LocalDate date) {
        if (!active) {
            return false;
        }
        if (validFrom != null && !validFrom.isEmpty()) {
            try {
                if (date.isBefore(LocalDate.parse(validFrom))) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        if (validTill != null && !validTill.isEmpty()) {
            try {
                if (date.isAfter(LocalDate.parse(validTill))) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    public String getScheduleId() { return scheduleId; }
    public void setScheduleId(String scheduleId) { this.scheduleId = UiUtil.clean(scheduleId); }
    public String getMovieId() { return movieId; }
    public void setMovieId(String movieId) { this.movieId = UiUtil.clean(movieId); }
    public String getTheatreId() { return theatreId; }
    public void setTheatreId(String theatreId) { this.theatreId = UiUtil.clean(theatreId); }
    public String getScreenName() { return screenName; }
    public void setScreenName(String screenName) { this.screenName = UiUtil.clean(screenName); }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = UiUtil.clean(timeSlot); }
    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = (price < 50 || price > 3000) ? 150 : price; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getValidFrom() { return validFrom; }
    public void setValidFrom(String validFrom) { this.validFrom = UiUtil.clean(validFrom); }
    public String getValidTill() { return validTill; }
    public void setValidTill(String validTill) { this.validTill = UiUtil.clean(validTill); }

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT,
                scheduleId, movieId, theatreId, screenName, timeSlot,
                String.valueOf(price), String.valueOf(active), validFrom, validTill);
    }

    public static ShowSchedule fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty schedule line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a schedule line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new ShowSchedule(parts);
    }

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
