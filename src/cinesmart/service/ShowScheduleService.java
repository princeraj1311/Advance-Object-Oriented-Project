package cinesmart.service;


import cinesmart.model.Movie;
import cinesmart.model.ShowSchedule;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.util.AppConfig;
import cinesmart.util.DataStore;
import cinesmart.util.IDGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ShowScheduleService rolls the show calendar forward day by day.
 *
 * generateShows(daysAhead) is idempotent: for every active ShowSchedule and each date from
 * today to today + daysAhead, it creates a Show instance (with a fresh, all-AVAILABLE seat
 * map) ONLY if a Show for that (schedule, date) does not already exist. Schedules whose movie
 * run has ended (validTill) and time slots already passed today are skipped.
 *
 * Every day starts with zero bookings because each generated Show is brand new with no
 * reserved seats.
 */
public class ShowScheduleService {

    private final DataStore store;

    public ShowScheduleService() {
        this(DataStore.get());
    }

    public ShowScheduleService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /** How many days ahead to generate (configurable constant). */
    public int daysAhead() {
        return AppConfig.SHOWS_DAYS_AHEAD;
    }

    /** Creates three daily templates per live TMDb film across the app's local theatres. */
    public synchronized int ensureRemoteMovieSchedules() {
        List<Movie> remoteMovies = store.getRemoteMovies();
        List<Theatre> theatres = store.getTheatres();
        if (remoteMovies.isEmpty() || theatres.isEmpty()) {
            return 0;
        }
        String[] slots = {"09:00", "14:30", "21:00"};
        int created = 0;
        for (int movieIndex = 0; movieIndex < remoteMovies.size(); movieIndex++) {
            Movie movie = remoteMovies.get(movieIndex);
            String numericId = movie.getMovieId().substring("TMDB_".length());
            for (int offset = 0; offset < 3; offset++) {
                Theatre theatre = theatres.get((movieIndex * 3 + offset) % theatres.size());
                String slot = slots[(movieIndex + offset) % slots.length];
                String scheduleId = "TM" + numericId + "_" + theatre.getTheatreId() + "_" + slot;
                if (store.findShowSchedule(scheduleId) != null) {
                    continue;
                }
                ShowSchedule schedule = new ShowSchedule(scheduleId, movie.getMovieId(),
                        theatre.getTheatreId(), "Screen " + (offset + 1), slot,
                        theatre.getBasePrice(), true, movie.getReleaseDate(), "");
                if (store.addShowSchedule(schedule)) {
                    created++;
                }
            }
        }
        return created;
    }

    /**
     * Replaces obsolete local schedule slots with the current three daily slots. Existing shows
     * and bookings remain unchanged; only future schedule templates are added.
     */
    public synchronized int migrateUnsupportedLegacySchedules() {
        Map<String, ShowSchedule> legacyPairs = new LinkedHashMap<>();
        for (ShowSchedule schedule : store.getShowSchedules()) {
            if (schedule == null || !schedule.isActive()
                    || isSupportedSlot(schedule.getTimeSlot())
                    || schedule.getMovieId().startsWith("TMDB_")
                    || store.findMovie(schedule.getMovieId()) == null
                    || store.findTheatre(schedule.getTheatreId()) == null) {
                continue;
            }
            String pair = schedule.getMovieId() + "|" + schedule.getTheatreId();
            legacyPairs.putIfAbsent(pair, schedule);
        }

        String[] slots = {"09:00", "14:30", "21:00"};
        int created = 0;
        for (ShowSchedule legacy : legacyPairs.values()) {
            Movie movie = store.findMovie(legacy.getMovieId());
            for (String slot : slots) {
                if (hasSupportedSchedule(legacy.getMovieId(), legacy.getTheatreId(), slot)) {
                    continue;
                }
                String scheduleId = "MIG_" + legacy.getMovieId() + "_"
                        + legacy.getTheatreId() + "_" + slot.replace(":", "");
                if (store.findShowSchedule(scheduleId) != null) {
                    continue;
                }
                String validFrom = legacy.getValidFrom();
                if (validFrom.isEmpty() || validFrom.compareTo(movie.getReleaseDate()) < 0) {
                    validFrom = movie.getReleaseDate();
                }
                ShowSchedule migrated = new ShowSchedule(scheduleId, legacy.getMovieId(),
                        legacy.getTheatreId(), legacy.getScreenName(), slot, legacy.getPrice(),
                        true, validFrom, legacy.getValidTill());
                if (store.addShowSchedule(migrated)) {
                    created++;
                }
            }
        }
        return created;
    }

    private boolean hasSupportedSchedule(String movieId, String theatreId, String slot) {
        for (ShowSchedule schedule : store.getShowSchedules()) {
            if (schedule.isActive() && schedule.getMovieId().equals(movieId)
                    && schedule.getTheatreId().equals(theatreId)
                    && slot.equals(schedule.getTimeSlot())) {
                return true;
            }
        }
        return false;
    }

    /** Only the three requested daily start times may be newly generated or sold. */
    public static boolean isSupportedSlot(String time) {
        return "09:00".equals(time) || "14:30".equals(time) || "21:00".equals(time);
    }

    /**
     * Generate Show instances for the rolling window [today, today + daysAhead).
     *
     * @param today the "current" date (pass LocalDate.now() in production; tests can pass a
     *              custom date to simulate the next day).
     * @return the number of brand-new Show instances created.
     */
    public synchronized int generateShows(LocalDate today) {
        if (today == null) {
            today = LocalDate.now();
        }
        int created = 0;
        LocalDateTime now = LocalDateTime.now();

        for (ShowSchedule schedule : store.getShowSchedules()) {
            if (!schedule.isActiveOn(today) || !isSupportedSlot(schedule.getTimeSlot())) {
                continue; // movie run ended or schedule inactive
            }
            for (int day = 0; day < daysAhead(); day++) {
                LocalDate showDate = today.plusDays(day);
                if (!schedule.isActiveOn(showDate)) {
                    continue;
                }
                // Skip time slots that have already passed today.
                if (day == 0) {
                    LocalTime slotTime;
                    try {
                        slotTime = LocalTime.parse(schedule.getTimeSlot());
                    } catch (Exception e) {
                        slotTime = LocalTime.of(10, 0);
                    }
                    if (LocalDateTime.of(showDate, slotTime).isBefore(now)) {
                        continue;
                    }
                }
                String showId = showIdFor(schedule, showDate);
                if (store.findShowTime(showId) != null) {
                    continue; // already exists - idempotent
                }
                Movie movie = store.findMovie(schedule.getMovieId());
                Theatre theatre = store.findTheatre(schedule.getTheatreId());
                if (movie == null || theatre == null) {
                    continue; // dangling reference, skip
                }
                ShowTime show = new ShowTime(showId,
                        schedule.getMovieId(), schedule.getTheatreId(),
                        showDate.toString(), schedule.getTimeSlot(),
                        schedule.getScreenName(), schedule.getPrice(),
                        theatreSeatCount(theatre), 0);
                show.setScheduleId(schedule.getScheduleId());
                show.setStatus(ShowTime.Status.UPCOMING);
                store.addShowTime(show);
                created++;
            }
        }
        return created;
    }

    /**
     * Expire unbooked instances created under the old four-slot timetable. Existing bookings
     * remain untouched; new sales are filtered by CatalogueService.
     */
    public synchronized int retireUnbookedLegacySlots() {
        int retired = 0;
        for (ShowTime show : store.getShowTimes()) {
            if (show.getStatus() == ShowTime.Status.UPCOMING
                    && !isSupportedSlot(show.getStartTime())
                    && show.reservedSeatCount() == 0
                    && store.activeHoldsForShow(show.getShowId()).isEmpty()) {
                show.markCompleted();
                if (store.updateShowTime(show)) {
                    retired++;
                }
            }
        }
        return retired;
    }

    /** Default entry point: generate for [today, today + daysAhead). */
    public int generateShows() {
        return generateShows(LocalDate.now());
    }

    /** Mark every show whose dateTime is in the past as COMPLETED. Never touches shows with
     * bookings. Called by the daily archive job. */
    public synchronized int archivePastShows() {
        int archived = 0;
        LocalDateTime now = LocalDateTime.now();
        List<ShowTime> toArchive = new ArrayList<>();
        for (ShowTime show : store.getShowTimes()) {
            if (show.getStatus() == ShowTime.Status.UPCOMING && show.dateTime().isBefore(now)) {
                toArchive.add(show);
            }
        }
        for (ShowTime show : toArchive) {
            // Only archive empty past shows (no reserved seats). Shows with bookings stay.
            if (show.reservedSeatCount() == 0) {
                show.markCompleted();
                store.updateShowTime(show);
                archived++;
            }
        }
        return archived;
    }

    /* ------------------------------------------------------------------ *
     * Helpers                                                            *
     * ------------------------------------------------------------------ */

    /** Deterministic id for a generated show: "S" + scheduleId + date. */
    private String showIdFor(ShowSchedule schedule, LocalDate date) {
        return "S" + schedule.getScheduleId() + date.toString().replace("-", "");
    }

    /** Seat count for a theatre: uses the first screen's layout. Shows store totalSeats
     *  themselves; this is only used for generated legacy-free shows. */
    private int theatreSeatCount(Theatre theatre) {
        if (theatre == null) {
            return 120;
        }
        int screens = theatre.getScreens();
        return (screens > 0) ? 100 + (screens * 10) : 120;
    }
}
