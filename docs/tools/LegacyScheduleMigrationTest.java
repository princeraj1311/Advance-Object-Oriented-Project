import cinesmart.model.ShowSchedule;
import cinesmart.model.ShowTime;
import cinesmart.service.ShowScheduleService;
import cinesmart.util.DataStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Checks that older unsupported schedule times migrate to the current three daily slots. */
public class LegacyScheduleMigrationTest {

    public static void main(String[] args) {
        DataStore store = DataStore.get();
        List<ShowSchedule> schedules = new ArrayList<>(store.getShowSchedules());
        if (schedules.isEmpty()) {
            throw new AssertionError("Expected the sample catalogue to include schedules.");
        }
        for (ShowSchedule schedule : schedules) {
            schedule.setTimeSlot("11:00");
        }
        if (!store.saveShowSchedules()) {
            throw new AssertionError("Could not save legacy schedule fixture.");
        }

        ShowScheduleService service = new ShowScheduleService(store);
        int migrated = service.migrateUnsupportedLegacySchedules();
        if (migrated == 0) {
            throw new AssertionError("Expected unsupported legacy schedules to be migrated.");
        }

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        int createdShows = service.generateShows(tomorrow);
        int unsupportedShows = 0;
        for (ShowTime show : store.getShowTimes()) {
            if (show.getDate().equals(tomorrow.toString())
                    && !ShowScheduleService.isSupportedSlot(show.getStartTime())) {
                unsupportedShows++;
            }
        }
        if (createdShows == 0 || unsupportedShows != 0) {
            throw new AssertionError("Migration should generate shows only in supported slots.");
        }
        System.out.println("LegacyScheduleMigrationTest: ALL CHECKS PASSED ("
                + migrated + " schedules migrated).");
    }
}
