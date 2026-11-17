package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Notification is one in-app message for one user (Feature 3a).
 *
 * Data line layout (6 columns):
 * notificationId|userId|type|message|isRead|createdAt
 */
public class Notification {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 6;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public enum Type {
        BOOKING_CONFIRMED("BOOKING_CONFIRMED"),
        BOOKING_CANCELLED("BOOKING_CANCELLED"),
        SEAT_HOLD_EXPIRED("SEAT_HOLD_EXPIRED");

        private final String label;
        Type(String label) { this.label = label; }
        public String label() { return label; }
        public static Type parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Type t : values()) {
                if (t.name().equalsIgnoreCase(wanted)) {
                    return t;
                }
            }
            return BOOKING_CONFIRMED;
        }
    }

    private String notificationId;
    private String userId;
    private Type type;
    private String message;
    private boolean isRead;
    private String createdAt;

    public Notification() {
        this.notificationId = "";
        this.userId = "";
        this.type = Type.BOOKING_CONFIRMED;
        this.message = "";
        this.isRead = false;
        this.createdAt = "";
    }

    public Notification(String notificationId, String userId, Type type, String message, String createdAt) {
        this();
        setNotificationId(notificationId);
        setUserId(userId);
        setType(type);
        setMessage(message);
        setIsRead(false);
        setCreatedAt(createdAt);
    }

    public Notification(String[] parts) {
        this();
        setNotificationId(part(parts, 0));
        setUserId(part(parts, 1));
        setType(Type.parse(part(parts, 2)));
        setMessage(part(parts, 3));
        setIsRead("true".equalsIgnoreCase(part(parts, 4)));
        setCreatedAt(part(parts, 5));
    }

    public String getNotificationId() { return notificationId; }
    public void setNotificationId(String id) { this.notificationId = UiUtil.clean(id); }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = UiUtil.clean(userId); }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = (type == null) ? Type.BOOKING_CONFIRMED : type; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = UiUtil.clean(message); }
    public boolean isRead() { return isRead; }
    public void setIsRead(boolean isRead) { this.isRead = isRead; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = UiUtil.clean(createdAt); }

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, notificationId, userId, type.name(),
                message, String.valueOf(isRead), createdAt);
    }

    public static Notification fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty notification line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a notification line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new Notification(parts);
    }

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }
}
