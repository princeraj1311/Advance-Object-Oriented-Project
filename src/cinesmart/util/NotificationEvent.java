package cinesmart.util;


/**
 * NotificationEvent pushes an in-app notification to the user in real time (Feature 3c).
 */
public final class NotificationEvent {

    public final cinesmart.model.Notification notification;

    public NotificationEvent(cinesmart.model.Notification notification) {
        this.notification = notification;
    }
}
