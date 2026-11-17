package cinesmart.util;


import java.util.ArrayList;
import java.util.List;

/**
 * EventBus is a lightweight in-process pub/sub for CineSmart (Feature 2 & 3 real-time updates).
 *
 * All delivery is Swing-safe: subscribers are invoked on the Event Dispatch Thread, so they can
 * safely touch UI components. Services call {@link #post} from worker threads without knowing
 * who is listening.
 *
 * Subscribers implement {@link Subscriber} and register with {@link #subscribe(Class, Subscriber)}.
 */
public final class EventBus {

    private EventBus() {
        // Utility class.
    }

    /** One topic per event class. */
    private static final java.util.Map<Class<?>, List<Subscriber<?>>> TOPICS =
            new java.util.LinkedHashMap<>();

    /** Subscribes to events of the given type. */
    @SuppressWarnings("unchecked")
    public static <T> void subscribe(Class<T> eventType, Subscriber<T> subscriber) {
        synchronized (TOPICS) {
            List<Subscriber<?>> list = TOPICS.computeIfAbsent(eventType,
                    k -> new ArrayList<>());
            list.add((Subscriber<T>) subscriber);
        }
    }

    /** Removes a subscriber (call this when a screen is closed to avoid leaks). */
    @SuppressWarnings("unchecked")
    public static <T> void unsubscribe(Class<T> eventType, Subscriber<T> subscriber) {
        synchronized (TOPICS) {
            List<Subscriber<?>> list = TOPICS.get(eventType);
            if (list != null) {
                list.remove(subscriber);
            }
        }
    }

    /** Publishes an event to all subscribers of its type, on the EDT. */
    public static <T> void post(T event) {
        List<Subscriber<?>> list;
        synchronized (TOPICS) {
            list = new ArrayList<>(TOPICS.getOrDefault(event.getClass(),
                    java.util.Collections.emptyList()));
        }
        if (list.isEmpty()) {
            return;
        }
        Runnable delivery = () -> {
            for (Subscriber<?> s : list) {
                try {
                    @SuppressWarnings("unchecked")
                    Subscriber<T> typed = (Subscriber<T>) s;
                    typed.onEvent(event);
                } catch (Exception e) {
                    System.err.println("EventBus delivery failed: " + e.getMessage());
                }
            }
        };
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            delivery.run();
        } else {
            javax.swing.SwingUtilities.invokeLater(delivery);
        }
    }

    /** A callback for one event type. */
    @FunctionalInterface
    public interface Subscriber<T> {
        void onEvent(T event);
    }
}
