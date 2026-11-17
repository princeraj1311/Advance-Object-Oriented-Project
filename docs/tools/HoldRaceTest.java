import cinesmart.model.SeatMap;
import cinesmart.model.ShowTime;
import cinesmart.service.BookingService;
import cinesmart.util.DataStore;
import cinesmart.util.Session;
import cinesmart.model.Customer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HoldRaceTest - proves Feature 1 prevents double booking.
 *
 * Simulates 2 users booking the same seat at the same time. Only one should succeed.
 *
 * Run:
 *   javac -d out $(find src -name '*.java')
 *   javac -cp out -d /tmp/holdrace docs/tools/HoldRaceTest.java
 *   cd /tmp/holdrace && java -cp <project>/out:/tmp/holdrace HoldRaceTest
 */
public class HoldRaceTest {

    public static void main(String[] args) throws Exception {
        DataStore store = DataStore.get();
        // Use a fresh scratch data dir by running with -Duser.dir=/tmp/scratch if desired.
        // Here we just reuse the default store.

        // Find an upcoming show to test against.
        ShowTime show = null;
        for (ShowTime s : store.getShowTimes()) {
            if (s.isUpcoming() && !s.isSoldOut()) {
                show = s;
                break;
            }
        }
        if (show == null) {
            System.out.println("FAIL: no upcoming show found");
            System.exit(1);
        }
        System.out.println("Testing show: " + show.getShowId());

        // Pick a seat that is definitely free.
        String seat = "A1";
        if (show.isSeatReserved(seat)) {
            // find any free seat
            for (char r = 'A'; r <= 'Z'; r++) {
                for (int c = 1; c <= 8; c++) {
                    String cand = "" + r + c;
                    if (!show.isSeatReserved(cand)) {
                        seat = cand;
                        break;
                    }
                }
                if (!seat.equals("A1")) break;
            }
        }
        System.out.println("Testing seat: " + seat);

        // Two threads, two "users", same seat.
        final String finalSeat = seat;
        final ShowTime finalShow = show;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        Runnable task = () -> {
            try {
                start.await();
                // Simulate a customer session.
                Customer c = new Customer("user" + Thread.currentThread().getId(),
                        "Test User", "test@test.com", "9999999999", "Mumbai",
                        "2000-01-01", "Male");
                Session.login(c);
                BookingService bs = new BookingService(store);
                SeatMap map = new SeatMap(finalShow.getShowId(), finalShow.getTotalSeats(),
                        finalShow.getPrice(), new java.util.LinkedHashSet<>());
                // Force select the seat.
                // SeatMap has no public select method in this API, so we use problemWith + holdSeats
                // by checking the seat directly via the store.
                // Instead, test at the store level: try to add a hold for the seat.
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                java.time.LocalDateTime exp = now.plusMinutes(5);
                java.time.format.DateTimeFormatter stamp =
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                cinesmart.model.SeatHold hold = new cinesmart.model.SeatHold(
                        cinesmart.util.IDGenerator.bookingId(),
                        finalShow.getShowId(), finalSeat, c.getUserName(),
                        now.format(stamp), exp.format(stamp));
                boolean ok = store.addSeatHold(hold);
                if (ok) {
                    successes.incrementAndGet();
                    System.out.println("  Thread " + Thread.currentThread().getId()
                            + " -> HOLD SUCCEEDED");
                } else {
                    failures.incrementAndGet();
                    System.out.println("  Thread " + Thread.currentThread().getId()
                            + " -> HOLD FAILED (seat already taken)");
                }
            } catch (Exception e) {
                System.err.println("Thread error: " + e.getMessage());
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        start.countDown();
        t1.join();
        t2.join();

        System.out.println();
        System.out.println("Results: " + successes.get() + " succeeded, "
                + failures.get() + " failed");
        if (successes.get() == 1 && failures.get() == 1) {
            System.out.println("PASS: only one user could hold the seat.");
        } else {
            System.out.println("FAIL: expected 1 success and 1 failure.");
            System.exit(1);
        }
    }
}
