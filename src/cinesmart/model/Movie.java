package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Movie is one film inside the cinema catalogue (Stage 3).
 *
 * Like {@link User} it knows how to write itself as ONE text line (toDataLine) and how to read
 * one line back (fromDataLine), so DataStore can keep films in data/movies.txt without knowing
 * anything about the film business.
 *
 * Data line layout (12 required columns, plus three optional ones):
 * movieId|title|language|genre|durationMinutes|certification|releaseDate|rating|director|
 * cast|story|timesBooked|posterUrl|totalReviews|reviewTags
 *
 * The poster address is the THIRTEENTH column and is optional, exactly like the held seats of a
 * ShowTime: a movies.txt written before posters existed still loads, the film simply falls back to
 * the artwork the screen paints itself. The last two columns are the audience reception - how many
 * reviews the film has and the hashtags readers used most - and they are optional in exactly the
 * same way. See {@link cinesmart.util.PosterLoader} for the poster picture itself - this class only
 * stores where to find it.
 */
public class Movie {

    /** Field separator inside data/movies.txt - the same character the user file uses. */
    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** How many columns a complete movie record must have. */
    public static final int FIELD_COUNT = 12;
    /** Where the optional poster column sits - one past the last required one. */
    public static final int POSTER_FIELD_INDEX = 12;
    /** The optional audience-reception columns, added after the poster. */
    public static final int REVIEW_COUNT_FIELD_INDEX = 13;
    public static final int REVIEW_TAGS_FIELD_INDEX = 14;

    /** Options offered by the filter combo boxes on the home screen. */
    public static final String[] LANGUAGES = {
        "Hindi", "English", "Tamil", "Telugu", "Kannada", "Malayalam", "Korean", "Japanese"
    };
    public static final String[] GENRES = {
        "Action", "Adventure", "Animation", "Comedy", "Documentary", "Drama", "Horror",
        "Mystery", "Romance", "Sci-Fi", "Sports", "Thriller"
    };
    /** Anything longer than this cannot be a feature film length, so it is treated as a typo. */
    public static final int MAX_DURATION_FILTER = 300;

    private String movieId;
    private String title;
    private String language;
    private String genre;
    private int durationMinutes;
    private String certification; // U, UA, UA 16+, A
    private String releaseDate;   // yyyy-MM-dd
    private double rating;        // IMDb style score out of 10
    private String director;
    private String cast;
    private String story;
    /** How many tickets were sold for this film - Stage 9 recommendations rank by this. */
    private int timesBooked;
    /**
     * Address of the film poster, may be empty. Stored with the film rather than in the view so
     * every screen shows the same picture for the same film, and so the admin form can change it.
     */
    private String posterUrl;
    /**
     * How many reviews this film has collected in total, e.g. 26900. The details screen shows it
     * as "26.9K reviews" above the reader comments. It is a property of the film, not a property
     * of any one Review, so it belongs here rather than being counted off the review file.
     */
    private int totalReviews;
    /**
     * The hashtags readers used most, with how many times each was used, e.g.
     * "SuperDirection=15387;GreatActing=15293". They drive the chips under the review summary.
     */
    private String reviewTags;

    /** Empty constructor used while building a film step by step (admin form, Stage 8). */
    public Movie() {
        this.movieId = "";
        this.title = "";
        this.language = "";
        this.genre = "";
        this.certification = "UA";
        this.releaseDate = LocalDate.now().toString();
        this.director = "";
        this.cast = "";
        this.story = "";
        this.posterUrl = "";
        this.totalReviews = 0;
        this.reviewTags = "";
    }

    /** Constructor used by the sample data and by the admin add-movie form. */
    public Movie(String movieId, String title, String language, String genre, int durationMinutes,
                 String certification, String releaseDate, double rating, String director,
                 String cast, String story) {
        this();
        setMovieId(movieId);
        setTitle(title);
        setLanguage(language);
        setGenre(genre);
        setDurationMinutes(durationMinutes);
        setCertification(certification);
        setReleaseDate(releaseDate);
        setRating(rating);
        setDirector(director);
        setCast(cast);
        setStory(story);
    }

    /**
     * The same constructor plus the poster address - used by the sample data and by the admin form
     * once posters exist. The 11 argument version above keeps working, so nothing else changes.
     */
    public Movie(String movieId, String title, String language, String genre, int durationMinutes,
                 String certification, String releaseDate, double rating, String director,
                 String cast, String story, String posterUrl) {
        this(movieId, title, language, genre, durationMinutes, certification, releaseDate, rating,
                director, cast, story);
        setPosterUrl(posterUrl);
    }

    /** Constructor used when one line is read back from data/movies.txt. */
    public Movie(String[] parts) {
        this();
        setMovieId(part(parts, 0));
        setTitle(part(parts, 1));
        setLanguage(part(parts, 2));
        setGenre(part(parts, 3));
        setDurationMinutes(toInt(part(parts, 4), 0));
        setCertification(part(parts, 5));
        setReleaseDate(part(parts, 6));
        setRating(toDouble(part(parts, 7), 0.0));
        setDirector(part(parts, 8));
        setCast(part(parts, 9));
        setStory(part(parts, 10));
        setTimesBooked(toInt(part(parts, 11), 0));
        // Optional 13th column: absent on a file written before posters existed, which is fine.
        setPosterUrl(parts.length > POSTER_FIELD_INDEX ? part(parts, POSTER_FIELD_INDEX) : "");
        // Optional 14th and 15th: the audience reception, absent on any older file as well.
        setTotalReviews(parts.length > REVIEW_COUNT_FIELD_INDEX
                ? toInt(part(parts, REVIEW_COUNT_FIELD_INDEX), 0) : 0);
        setReviewTags(parts.length > REVIEW_TAGS_FIELD_INDEX
                ? part(parts, REVIEW_TAGS_FIELD_INDEX) : "");
    }

    /* ------------------------------------------------------------------ *
     * Behaviour
     * ------------------------------------------------------------------ */

    /** True when the premiere date is today or in the past, so it can be booked. */
    public boolean isReleased() {
        try {
            return !LocalDate.parse(releaseDate).isAfter(LocalDate.now());
        } catch (Exception e) {
            return true; // no usable date -> treat as bookable rather than hiding the film
        }
    }

    /** "2h 18m" instead of "138", because that is how people read film lengths. */
    public String durationText() {
        if (durationMinutes <= 0) {
            return "length TBA";
        }
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        if (hours == 0) {
            return minutes + "m";
        }
        return hours + "h " + minutes + "m";
    }

    /** One line of small print under the title on a card: "Hindi - Drama - 2h 18m". */
    public String metaLine() {
        return language + " \u00b7 " + genre + " \u00b7 " + durationText();
    }

    /** Rating as shown on screen: one decimal place, e.g. "7.8". */
    public String ratingText() {
        return String.format("%.1f", rating);
    }

    public String ratingSourceName() {
        return isTmdbMovie() ? "TMDb" : "IMDb";
    }

    public boolean isTmdbMovie() {
        return movieId.startsWith("TMDB_");
    }

    public String tmdbReviewsUrl() {
        if (!isTmdbMovie()) {
            return "";
        }
        String numericId = movieId.substring("TMDB_".length());
        return numericId.matches("[0-9]+")
                ? "https://www.themoviedb.org/movie/" + numericId + "/reviews" : "";
    }

    /** Premiere in words, e.g. "05 Sep 2026". */
    public String releaseDateText() {
        try {
            return LocalDate.parse(releaseDate).format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        } catch (Exception e) {
            return releaseDate;
        }
    }

    /** First letters of the title words, used as the poster monogram (e.g. "CH"). */
    public String monogram() {
        String clean = title.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] words = clean.split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase();
    }

    /** Long description for the details dialog. HTML because JLabel renders simple HTML. */
    public String detailHtml() {
        StringBuilder html = new StringBuilder("<html><div style='width:340px'>");
        html.append("<b>").append(story.isEmpty() ? "Story to be announced." : story).append("</b>");
        html.append("<br><br>");
        html.append("Director: ").append(blank(director)).append("<br>");
        html.append("Cast: ").append(blank(cast)).append("<br>");
        html.append("Language: ").append(language).append(" &nbsp;|&nbsp; Genre: ").append(genre);
        html.append("<br>Duration: ").append(durationText());
        html.append(" &nbsp;|&nbsp; Certificate: ").append(certification);
        html.append("<br>Release: ").append(releaseDateText());
        html.append(" &nbsp;|&nbsp; ").append(ratingSourceName()).append(": ")
                .append(ratingText()).append(" / 10");
        if (isTmdbMovie()) {
            html.append(" (").append(totalReviews).append(" votes)");
        }
        if (timesBooked > 0) {
            html.append("<br>Tickets sold so far: ").append(timesBooked);
        }
        html.append("</div></html>");
        return html.toString();
    }

    /* ---------------- audience reception shown on the details screen ---------------- */

    /**
     * One hashtag readers used, with the number of times it was used - what a chip shows.
     *
     * A tiny value class rather than another file: it only ever exists as part of a chip and is
     * created by {@link #topReviewTags()}.
     */
    public static final class ReviewTag {

        private final String name;
        private final int count;

        ReviewTag(String name, int count) {
            this.name = name;
            this.count = count;
        }

        /** The tag without its #, e.g. "SuperDirection". */
        public String name() {
            return name;
        }

        /** How many readers used it. */
        public int count() {
            return count;
        }

        /** "#SuperDirection" - the red part of the chip. */
        public String label() {
            return "#" + name;
        }

        /** "15387", or "1.5K" for a big one, because a raw 15387 is too wide for a chip. */
        public String countText() {
            if (count >= 10000) {
                return String.format("%.1fK", count / 1000.0);
            }
            return String.valueOf(count);
        }

        @Override
        public String toString() {
            return label() + " " + countText();
        }
    }

    /**
     * The chips under "Summary of N reviews", most used first.
     *
     * The text is read here rather than by the screen, so the details dialog and any summary
     * printed later order and format the chips in exactly the same way. A tag with no count is
     * skipped rather than shown as zero, and a broken "name=number" pair never throws.
     */
    public List<ReviewTag> topReviewTags(int limit) {
        List<ReviewTag> tags = new ArrayList<>();
        for (String piece : reviewTags.split(";")) {
            int equals = piece.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String name = piece.substring(0, equals).trim().replace("#", "");
            int count;
            try {
                count = Integer.parseInt(piece.substring(equals + 1).trim());
            } catch (NumberFormatException e) {
                continue; // a damaged chip is simply not shown
            }
            if (!name.isEmpty() && count > 0) {
                tags.add(new ReviewTag(name, count));
            }
        }
        tags.sort((first, second) -> Integer.compare(second.count, first.count));
        if (limit > 0 && tags.size() > limit) {
            return new ArrayList<>(tags.subList(0, limit));
        }
        return tags;
    }

    /**
     * "26.9K reviews" - the headline above the comments.
     *
     * Under a thousand the exact number is shown, because "0.9K reviews" reads worse than "860
     * reviews"; above it the short form keeps the header one line wide.
     */
    public String reviewCountText() {
        if (totalReviews >= 10000) {
            return String.format("%.1fK %s", totalReviews / 1000.0, receptionUnit());
        }
        return totalReviews + (isTmdbMovie() ? " votes"
                : totalReviews == 1 ? " review" : " reviews");
    }

    private String receptionUnit() {
        return isTmdbMovie() ? "votes" : "reviews";
    }

    /** True when at least one review has been written, so the section may be shown at all. */
    public boolean hasReception() {
        return totalReviews > 0 || !reviewTags.trim().isEmpty();
    }

    /** Called by Stage 4/5 when a booking for this film is confirmed. */
    public void registerBooking(int seatCount) {
        this.timesBooked += Math.max(0, seatCount);
    }

    /** Stage 6: a cancelled booking stops counting, so the best-seller list stays honest. */
    public void releaseBooking(int seatCount) {
        this.timesBooked = Math.max(0, timesBooked - Math.max(0, seatCount));
    }

    private static String blank(String value) {
        return (value == null || value.trim().isEmpty()) ? "Not announced" : value.trim();
    }

    /* ------------------------------------------------------------------ *
     * Saving an object as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT,
                movieId, title, language, genre, String.valueOf(durationMinutes), certification,
                releaseDate, String.valueOf(rating), director, cast, story,
                String.valueOf(timesBooked), posterUrl, String.valueOf(totalReviews), reviewTags);
    }

    /** Factory method: one line of data/movies.txt becomes a Movie object. */
    public static Movie fromDataLine(String line) {
        String[] parts = line.split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("Incomplete movie record: " + line);
        }
        return new Movie(parts);
    }

    /* ------------------------------------------------------------------ *
     * Getters and setters (the setters clean or repair the input)
     * ------------------------------------------------------------------ */

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = UiUtil.clean(movieId);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = UiUtil.clean(title);
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = UiUtil.clean(language);
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = UiUtil.clean(genre);
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    /** Repaired instead of throwing: the film stays bookable and the value stays sane. */
    public final void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = (durationMinutes <= 0 || durationMinutes > MAX_DURATION_FILTER)
                ? 0 : durationMinutes;
    }

    public String getCertification() {
        return certification;
    }

    public void setCertification(String certification) {
        String clean = UiUtil.clean(certification).toUpperCase();
        this.certification = clean.isEmpty() ? "UA" : clean;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        String clean = UiUtil.clean(releaseDate);
        try {
            LocalDate.parse(clean); // only store a date we can read back
            this.releaseDate = clean;
        } catch (Exception e) {
            this.releaseDate = LocalDate.now().toString();
        }
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = (rating < 0 || rating > 10) ? 0 : Math.round(rating * 10) / 10.0;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = UiUtil.clean(director);
    }

    public String getCast() {
        return cast;
    }

    public void setCast(String cast) {
        this.cast = UiUtil.clean(cast);
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = UiUtil.clean(story);
    }

    public int getTimesBooked() {
        return timesBooked;
    }

    public void setTimesBooked(int timesBooked) {
        this.timesBooked = Math.max(0, timesBooked);
    }

    /** Where the poster lives, or an empty string when this film has no picture. */
    public String getPosterUrl() {
        return posterUrl;
    }

    /**
     * Only http and https addresses are kept. Anything else (a file path, a javascript: link, a
     * mangled half-typed value in the admin form) is dropped, so a bad value can never reach the
     * downloader or paint an error page into a card.
     */
    public void setPosterUrl(String posterUrl) {
        String clean = UiUtil.clean(posterUrl);
        this.posterUrl = (clean.isEmpty() || !clean.startsWith("http")) ? "" : clean;
    }

    /** True when this film has a poster address, so the screens know a picture can be fetched. */
    public boolean hasPoster() {
        return !posterUrl.isEmpty();
    }

    /** How many reviews this film has collected; 0 when nobody has written one. */
    public int getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(int totalReviews) {
        this.totalReviews = Math.max(0, totalReviews);
    }

    /** The raw "Tag=count;Tag=count" text; read it through topReviewTags instead. */
    public String getReviewTags() {
        return reviewTags;
    }

    public void setReviewTags(String reviewTags) {
        this.reviewTags = (reviewTags == null) ? "" : reviewTags.trim();
    }

    /** Used by combo boxes and lists - the title is what a person wants to read. */
    @Override
    public String toString() {
        return title + " (" + language + ")";
    }

    /* ------------------------------------------------------------------ *
     * Small, safe conversions: a damaged file must never crash the program
     * ------------------------------------------------------------------ */

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

    private static double toDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
