package cinesmart.service;


import cinesmart.model.Movie;
import cinesmart.model.CinemaVenue;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.util.DataStore;
import cinesmart.util.IDGenerator;
import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CatalogueService answers every question the home screen has: "which films match this
 * search", "which cinemas are in this city", "when does this film play here". The Swing panels
 * never loop over lists themselves - the view shows, the service decides.
 *
 * Comparators live here (not inside a panel) because they are business rules: an IMDb sort and
 * a "newest first" sort must behave the same for the customer screen and the admin screen.
 */
public class CatalogueService {

    /** The "everything" entry of the filter combo boxes. */
    public static final String FILTER_ALL = "All";

    public static final String SORT_RATING = "IMDb rating (high to low)";
    public static final String SORT_POPULAR = "Most booked";
    public static final String SORT_TITLE = "Title (A to Z)";
    public static final String SORT_DURATION = "Duration (shortest first)";
    public static final String SORT_RELEASE = "Release (newest first)";

    /** Fills the "Sort by" combo box. */
    public static final String[] SORT_OPTIONS = {
        SORT_RATING, SORT_POPULAR, SORT_TITLE, SORT_DURATION, SORT_RELEASE
    };

    private final DataStore store;

    public CatalogueService() {
        this(DataStore.get());
    }

    /** Constructor injection, exactly like UserService, so a test can supply its own store. */
    public CatalogueService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *
     * Films
     * ------------------------------------------------------------------ */

    public List<Movie> allMovies() {
        return store.getMovies();
    }

    /** Films that are out today (the ones you can actually book). */
    public List<Movie> nowShowing() {
        List<Movie> list = new ArrayList<>();
        for (Movie movie : store.getMovies()) {
            if (movie.isReleased()) {
                list.add(movie);
            }
        }
        return list;
    }

    /** Films announced but not released yet, oldest announcement first. */
    public List<Movie> comingSoon() {
        List<Movie> list = new ArrayList<>();
        for (Movie movie : store.getMovies()) {
            if (!movie.isReleased()) {
                list.add(movie);
            }
        }
        list.sort(Comparator.comparing(Movie::getReleaseDate));
        return list;
    }

    /**
     * The search used by the home screen. Every empty/"All" filter means "do not filter on
     * this column", so one method covers all the combinations instead of five similar loops.
     */
    public List<Movie> find(String text, String language, String genre, String sortBy) {
        return filter(text, language, genre, DUR_ANY, sortBy, true);
    }

    /* ---------------- the duration filter of the home screen ---------------- */

    public static final String DUR_ANY = "Any length";
    public static final String DUR_SHORT = "Under 1h 40m";
    public static final String DUR_MEDIUM = "1h 40m - 2h 20m";
    public static final String DUR_LONG = "Over 2h 20m";

    /** Fills the "Duration" combo box. "Any length" stays on top like every other filter. */
    public static final String[] DURATION_BANDS = {DUR_ANY, DUR_SHORT, DUR_MEDIUM, DUR_LONG};

    /**
     * Every filter of the home screen in one place: free text, language, genre, duration band and
     * the Now showing / Coming soon tab (released). Business rules stay here so the Swing panel
     * only has to display what it is given, and so the admin screen can reuse the same rules.
     */
    public List<Movie> filter(String text, String language, String genre, String durationBand,
                              String sortBy, boolean released) {
        String needle = UiUtil.clean(text).toLowerCase();
        List<Movie> matches = new ArrayList<>();
        for (Movie movie : store.getMovies()) {
            if (movie.isReleased() != released) {
                continue; // the tab decides: bookable films now, announced films later
            }
            if (matchesText(movie, needle) && matchesColumn(movie.getLanguage(), language)
                    && matchesColumn(movie.getGenre(), genre)
                    && matchesDuration(movie, durationBand)) {
                matches.add(movie);
            }
        }
        matches.sort(comparatorFor(sortBy));
        return matches;
    }

    /** The typed word may appear in the title, the genre, the cast or the director. */
    private static boolean matchesText(Movie movie, String needle) {
        return needle.isEmpty()
                || movie.getTitle().toLowerCase().contains(needle)
                || movie.getGenre().toLowerCase().contains(needle)
                || movie.getCast().toLowerCase().contains(needle)
                || movie.getDirector().toLowerCase().contains(needle);
    }

    private static boolean matchesColumn(String value, String filter) {
        if (isAll(filter)) {
            return true;
        }
        String wanted = UiUtil.clean(filter);
        for (String part : value.split(",")) {
            if (part.trim().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** A film with no known length only matches "Any length" - it is not secretly short or long. */
    private static boolean matchesDuration(Movie movie, String band) {
        int minutes = movie.getDurationMinutes();
        if (band == null || DUR_ANY.equalsIgnoreCase(band)) {
            return true;
        }
        if (minutes <= 0) {
            return false;
        }
        switch (band) {
            case DUR_SHORT:
                return minutes < 100;
            case DUR_MEDIUM:
                return minutes >= 100 && minutes <= 140;
            case DUR_LONG:
                return minutes > 140;
            default:
                return true;
        }
    }

    /** Turns a sort option of the combo box into a Comparator object. */
    public Comparator<Movie> comparatorFor(String sortBy) {
        String option = (sortBy == null) ? SORT_RATING : sortBy;
        switch (option) {
            case SORT_TITLE:
                return Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER);
            case SORT_DURATION:
                return Comparator.comparingInt(Movie::getDurationMinutes);
            case SORT_RELEASE:
                return Comparator.comparing(Movie::getReleaseDate, Comparator.reverseOrder());
            case SORT_POPULAR:
                return Comparator.comparingInt(Movie::getTimesBooked).reversed();
            case SORT_RATING:
            default:
                return Comparator.comparingDouble(Movie::getRating).reversed();
        }
    }

    public Movie movieById(String movieId) {
        return store.findMovie(movieId);
    }

    /** Languages that really exist in the catalogue - the combo box is never hard coded. */
    public List<String> languageOptions() {
        return distinctColumn(store.getMovies(), true);
    }

    public List<String> genreOptions() {
        return distinctColumn(store.getMovies(), false);
    }

    private List<String> distinctColumn(List<Movie> movies, boolean language) {
        List<String> values = new ArrayList<>();
        for (Movie movie : movies) {
            String value = language ? movie.getLanguage() : movie.getGenre();
            for (String part : (language ? new String[] {value} : value.split(","))) {
                String option = part.trim();
                if (!option.isEmpty() && !values.contains(option)) {
                    values.add(option);
                }
            }
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        values.add(0, FILTER_ALL); // "All" always stays at the top of the combo box
        return values;
    }

    private static boolean isAll(String filter) {
        return filter == null || filter.trim().isEmpty() || FILTER_ALL.equalsIgnoreCase(filter);
    }

    /* ------------------------------------------------------------------ *
     * Cinemas
     * ------------------------------------------------------------------ */

    public List<Theatre> allTheatres() {
        return store.getTheatres();
    }

    /** Cinemas of one city, alphabetically - the order the show-time blocks are printed in. */
    public List<Theatre> theatresIn(String city) {
        List<Theatre> list = new ArrayList<>();
        for (Theatre theatre : store.getTheatres()) {
            if (theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))) {
                list.add(theatre);
            }
        }
        list.sort(Comparator.comparing(Theatre::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    public Theatre theatreById(String theatreId) {
        return store.findTheatre(theatreId);
    }

    /* ------------------------------------------------------------------ *
     * Show times: the join between a film and a city
     * ------------------------------------------------------------------ */

    public List<ShowTime> allShowTimes() {
        return store.getShowTimes();
    }

    /**
     * Every screening of one film in one city. The city lives on the Theatre, so this method
     * performs the join: show -> theatreId -> theatre -> city. ShowTime.compareTo already puts
     * the result in date and time order.
     */
    public List<ShowTime> showTimesFor(Movie movie, String city) {
        List<ShowTime> list = new ArrayList<>();
        if (movie == null) {
            return list;
        }
        for (ShowTime showTime : store.getShowTimes()) {
            if (!showTime.getMovieId().equals(movie.getMovieId())) {
                continue;
            }
            if (!ShowScheduleService.isSupportedSlot(showTime.getStartTime())) {
                continue;
            }
            Theatre theatre = store.findTheatre(showTime.getTheatreId());
            if (theatre != null && theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))) {
                list.add(showTime);
            }
        }
        Collections.sort(list);
        return list;
    }

    /** The same data grouped under its cinema, so the screen prints one block per cinema. */
    public Map<Theatre, List<ShowTime>> showTimesByTheatre(Movie movie, String city) {
        Map<Theatre, List<ShowTime>> grouped = new LinkedHashMap<>();
        for (ShowTime showTime : showTimesFor(movie, city)) {
            Theatre theatre = store.findTheatre(showTime.getTheatreId());
            if (theatre == null) {
                continue; // a show whose cinema was deleted: skip it instead of crashing
            }
            List<ShowTime> bucket = grouped.get(theatre);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(theatre, bucket);
            }
            bucket.add(showTime);
        }
        return grouped;
    }

    /** Shows a customer may still book (in the future and seats left). */
    public List<ShowTime> bookableShows(Movie movie, String city) {
        List<ShowTime> list = new ArrayList<>();
        for (ShowTime showTime : showTimesFor(movie, city)) {
            if (showTime.isBookable()) {
                list.add(showTime);
            }
        }
        return list;
    }

    /** True when the film plays in this city at all - used to pick the first selected film. */
    public boolean playsInCity(Movie movie, String city) {
        return !showTimesFor(movie, city).isEmpty();
    }

    /**
     * The grid rule of the home screen: the Now Showing tab lists only the films the chosen city
     * actually screens - a film you cannot watch here is not shown as a dead poster - while the
     * Coming Soon tab lists every announced film, because nothing is screening yet.
     * The rule lives here, not in the panel, so the admin and recommendation screens reuse it.
     */
    public List<Movie> filterForCity(String text, String language, String genre,
                                     String durationBand, String sortBy, String city,
                                     boolean released) {
        List<Movie> matches = new ArrayList<>();
        for (Movie movie : filter(text, language, genre, durationBand, sortBy, released)) {
            if (!released || playsInCity(movie, city)) {
                matches.add(movie);
            }
        }
        return matches;
    }

    /* ------------------------------------------------------------------ *
     * Small numbers for the stats cards on the home screen
     * ------------------------------------------------------------------ */

    public int countNowShowing() {
        return nowShowing().size();
    }

    public int countComingSoon() {
        return comingSoon().size();
    }

    public int countTheatresIn(String city) {
        return theatresIn(city).size();
    }

    /** Number of public OpenStreetMap cinema entries cached for a city (informational only). */
    public int countPublicCinemasIn(String city) {
        return store.cinemaVenuesIn(city).size();
    }

    public List<CinemaVenue> publicCinemasIn(String city) {
        return store.cinemaVenuesIn(city);
    }

    /** Screenings starting today in one city. */
    public int countShowsToday(String city) {
        String today = LocalDate.now().toString();
        int total = 0;
        for (ShowTime showTime : store.getShowTimes()) {
            Theatre theatre = store.findTheatre(showTime.getTheatreId());
            if (theatre != null && theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))
                    && showTime.getDate().equals(today)
                    && ShowScheduleService.isSupportedSlot(showTime.getStartTime())) {
                total++;
            }
        }
        return total;
    }

    /** Seats still free across every bookable show of the city. */
    public int countSeatsFree(String city) {
        int free = 0;
        for (ShowTime showTime : store.getShowTimes()) {
            Theatre theatre = store.findTheatre(showTime.getTheatreId());
            if (theatre != null && theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))
                    && showTime.isBookable()
                    && ShowScheduleService.isSupportedSlot(showTime.getStartTime())) {
                free += showTime.seatsAvailable();
            }
        }
        return free;
    }

    /* ------------------------------------------------------------------ *
     * Changing the catalogue (the admin screen of Stage 8 calls these)
     * ------------------------------------------------------------------ */

    /**
     * Adds a film. The id is generated, never typed, so it can never collide.
     * The poster address is optional - an empty one simply leaves the card with its own artwork.
     */
    public boolean addMovie(String title, String language, String genre, int durationMinutes,
                            String certification, String releaseDate, double rating,
                            String director, String cast, String story) {
        return addMovie(title, language, genre, durationMinutes, certification, releaseDate,
                rating, director, cast, story, "");
    }

    /** The same, with a poster address - the form that can set the artwork of a new film. */
    public boolean addMovie(String title, String language, String genre, int durationMinutes,
                            String certification, String releaseDate, double rating,
                            String director, String cast, String story, String posterUrl) {
        if (UiUtil.clean(title).isEmpty()) {
            return false;
        }
        Movie movie = new Movie(IDGenerator.nextMovieId(existingMovieIds()), title, language,
                genre, durationMinutes, certification, releaseDate, rating, director, cast, story,
                posterUrl);
        return store.addMovie(movie);
    }

    /** Deleting a film also removes its shows (see DataStore.deleteMovie). */
    public boolean deleteMovie(Movie movie) {
        return movie != null && store.deleteMovie(movie.getMovieId());
    }

    /** Adds a cinema, again with a generated id. */
    public boolean addTheatre(String name, String city, String address, int screens,
                              int basePrice, String amenities) {
        if (UiUtil.clean(name).isEmpty() || UiUtil.clean(city).isEmpty()) {
            return false;
        }
        Theatre theatre = new Theatre(IDGenerator.nextTheatreId(existingTheatreIds()), name, city,
                address, screens, basePrice, amenities);
        return store.addTheatre(theatre);
    }

    public boolean deleteTheatre(Theatre theatre) {
        return theatre != null && store.deleteTheatre(theatre.getTheatreId());
    }

    /** Stage 4 needs the next show id when an admin adds a screening. */
    public String nextShowId() {
        int highest = 0;
        for (ShowTime showTime : store.getShowTimes()) {
            try {
                highest = Math.max(highest,
                        Integer.parseInt(showTime.getShowId().substring(1)));
            } catch (Exception ignored) {
                // an id that does not follow the pattern is simply skipped
            }
        }
        return String.format("S%04d", highest + 1);
    }

    private List<String> existingMovieIds() {
        List<String> ids = new ArrayList<>();
        for (Movie movie : store.getMovies()) {
            ids.add(movie.getMovieId());
        }
        return ids;
    }

    private List<String> existingTheatreIds() {
        List<String> ids = new ArrayList<>();
        for (Theatre theatre : store.getTheatres()) {
            ids.add(theatre.getTheatreId());
        }
        return ids;
    }
}

