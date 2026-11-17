import cinesmart.model.ShowTime;
import cinesmart.service.ShowScheduleService;
import cinesmart.util.DataStore;
import java.time.LocalDate;

/**
 * DateGeneratorTest - simulates the next day by passing a custom "today" to generateShows().
 *
 * Verifies:
 *   1. New day has all seats AVAILABLE (zero bookings).
 *   2. Yesterday's bookings are untouched.
 *
 * Run:
 *   javac -d out $(find src -name '*.java')
 *   javac -cp out -d /tmp/dgentest docs/tools/DateGeneratorTest.java
 *   cd /tmp/dgentest && java -cp <project>/out:/tmp/dgentest DateGeneratorTest
 */
public class DateGeneratorTest {

    public static void main(String[] args) {
        DataStore store = DataStore.get();
        ShowScheduleService svc = new ShowScheduleService(store);

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // Count bookings before.
        int bookingsBefore = store.getBookings().size();
        System.out.println("Bookings before: " + bookingsBefore);

        // Generate shows for tomorrow.
        int created = svc.generateShows(tomorrow);
        System.out.println("Generated " + created + " new shows for " + tomorrow);

        // Verify: new shows have zero reserved seats (all available).
        int badShows = 0;
        int unsupportedSlots = 0;
        for (ShowTime s : store.getShowTimes()) {
            if (s.getDate().equals(tomorrow.toString()) && s.reservedSeatCount() > 0) {
                badShows++;
            }
            if (s.getDate().equals(tomorrow.toString())
                    && !ShowScheduleService.isSupportedSlot(s.getStartTime())) {
                unsupportedSlots++;
            }
        }
        if (badShows == 0) {
            System.out.println("PASS: all new shows for tomorrow have 0 reserved seats (all AVAILABLE).");
        } else {
            System.out.println("FAIL: " + badShows + " shows have reserved seats.");
            System.exit(1);
        }

        if (unsupportedSlots == 0) {
            System.out.println("PASS: all generated screenings use one of the three allowed slots.");
        } else {
            System.out.println("FAIL: " + unsupportedSlots + " screenings use an old time slot.");
            System.exit(1);
        }

        // Verify: bookings count unchanged (yesterday's bookings untouched).
        int bookingsAfter = store.getBookings().size();
        if (bookingsAfter == bookingsBefore) {
            System.out.println("PASS: bookings count unchanged (" + bookingsAfter + ").");
        } else {
            System.out.println("FAIL: bookings changed from " + bookingsBefore + " to " + bookingsAfter);
            System.exit(1);
        }
        System.out.println("DateGeneratorTest: ALL CHECKS PASSED.");
    }
}
