package cinesmart.service;


import cinesmart.model.Booking;
import cinesmart.model.Notification;
import cinesmart.model.ShowTime;
import cinesmart.util.AppConfig;
import cinesmart.util.DataStore;
import cinesmart.util.EventBus;
import cinesmart.util.IDGenerator;
import cinesmart.util.NotificationEvent;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NotificationService (Feature 3) is the single notification layer. It dispatches to one or more
 * NotificationChannels, so SMS or push can be added later by implementing the interface.
 */
public class NotificationService {

    public interface NotificationChannel {
        void send(Notification notification);
    }

    public static class InAppChannel implements NotificationChannel {
        @Override
        public void send(Notification notification) {
            if (notification == null) return;
            DataStore.get().addNotification(notification);
            EventBus.post(new NotificationEvent(notification));
        }
    }

    public static class EmailChannel implements NotificationChannel {
        @Override
        public void send(Notification notification) {
            if (notification == null) return;
            String to = notification.getUserId();
            String subject = notification.getType().label();
            String body = notification.getMessage();
            if (AppConfig.isSmtpConfigured()) {
                System.out.println("Email would be sent via SMTP to " + to + ": " + subject);
            }
            try (PrintWriter w = new PrintWriter(new FileWriter("data/emails.log", true))) {
                w.println("=== " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " ===");
                w.println("To: " + to);
                w.println("From: " + AppConfig.SMTP_FROM);
                w.println("Subject: " + subject);
                w.println(body);
                w.println();
            } catch (Exception e) {
                System.err.println("Could not write email log: " + e.getMessage());
            }
        }
    }

    private final List<NotificationChannel> channels;

    public NotificationService() {
        channels = new ArrayList<>();
        channels.add(new InAppChannel());
        channels.add(new EmailChannel());
    }

    public NotificationService(List<NotificationChannel> channels) {
        this.channels = (channels == null || channels.isEmpty())
                ? new ArrayList<>(Arrays.asList(new InAppChannel(), new EmailChannel()))
                : channels;
    }

    public void notifyBookingConfirmed(String userName, Booking booking, ShowTime show) {
        String seats = (booking == null) ? "" : booking.seatsText();
        MovieTitleInfo info = resolveMovie(show);
        String theatre = resolveTheatre(show);
        String when = (show == null) ? "" : show.dateText() + " at " + show.startTimeText();
        String message = "Booking confirmed: " + info.title + " at " + theatre + ", " + when
                + ". Seats: " + seats + ". Booking ID: "
                + (booking == null ? "" : booking.getBookingId())
                + ". Amount: Rs " + (booking == null ? 0 : booking.getAmount()) + ".";

        Notification n = new Notification(IDGenerator.notificationId(),
                userName, Notification.Type.BOOKING_CONFIRMED, message,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dispatch(n);
    }

    public void notifyBookingCancelled(String userName, Booking booking, ShowTime show) {
        String seats = (booking == null) ? "" : booking.seatsText();
        MovieTitleInfo info = resolveMovie(show);
        String message = "Booking cancelled: " + info.title + ". Seats released: " + seats
                + ". Booking ID: " + (booking == null ? "" : booking.getBookingId())
                + ". Refund: Rs " + (booking == null ? 0 : booking.getAmount()) + ".";

        Notification n = new Notification(IDGenerator.notificationId(),
                userName, Notification.Type.BOOKING_CANCELLED, message,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dispatch(n);
    }

    public void notifySeatHoldExpired(String userName, String showId) {
        String message = "Your held seats for show " + showId + " were released "
                + "(5 minute hold expired).";
        Notification n = new Notification(IDGenerator.notificationId(),
                userName, Notification.Type.SEAT_HOLD_EXPIRED, message,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        dispatch(n);
    }

    private void dispatch(Notification n) {
        for (NotificationChannel channel : channels) {
            try {
                channel.send(n);
            } catch (Exception e) {
                System.err.println("Notification channel failed: " + e.getMessage());
            }
        }
    }

    private MovieTitleInfo resolveMovie(ShowTime show) {
        MovieTitleInfo info = new MovieTitleInfo("a film");
        if (show == null) return info;
        cinesmart.model.Movie m = DataStore.get().findMovie(show.getMovieId());
        if (m != null) info.title = m.getTitle();
        return info;
    }

    private String resolveTheatre(ShowTime show) {
        if (show == null) return "a cinema";
        cinesmart.model.Theatre t = DataStore.get().findTheatre(show.getTheatreId());
        return (t == null) ? show.getTheatreId() : t.getName();
    }

    /** Tiny holder so resolveMovie stays readable. */
    private static class MovieTitleInfo {
        String title;
        MovieTitleInfo(String title) { this.title = title; }
    }
}
