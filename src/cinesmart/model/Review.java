package cinesmart.model;


import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Review is one thing a reader wrote about a film - either an audience review ("I watched this at
 * the IMAX and ...") or a critic review published by a newspaper or a website.
 *
 * Both live in ONE class with a {@link Kind}, because the two are the same idea seen by different
 * people and they are shown side by side: same rating out of ten, same likes, same date. Keeping
 * them as one record also means one file, one line format and one list to load.
 *
 * The words are data, not behaviour: a Review knows how to score itself and how to write "2 days
 * ago", and {@link cinesmart.service.ReviewService} decides which reviews belong to which film and
 * what the audience verdict is.
 *
 * Data line layout (11 columns, all required):
 * RS01|M001|AUDIENCE|Arjun Menon|10|846|0|2026-10-03|#Blockbuster|Watched it twice|Wonderful ...
 *
 * The hashtag list is optional in practice - a critic review carries none - but the column itself
 * is always written so the file stays a rectangle.
 */
public class Review {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    /** How many columns a review record must have. */
    public static final int FIELD_COUNT = 11;

    /** Who wrote it. */
    public enum Kind {
        /** A customer review - shown under "Top reviews". */
        AUDIENCE("User review"),
        /** A published review from a critic - shown under "Critic reviews". */
        CRITIC("Critic review");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Unknown text in a file falls back to AUDIENCE rather than breaking the load. */
        public static Kind parse(String text) {
            String wanted = UiUtil.clean(text);
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(wanted)) {
                    return kind;
                }
            }
            return AUDIENCE;
        }
    }
private String reviewId;
    private String movieId;
    private Kind kind;
    /** The reader's name, or the publication's name for a critic. */
    private String authorName;
    /** Score out of ten, kept as a whole number because that is what people give. */
    private int rating;
    private int likes;
    private int dislikes;
    /** Day it was posted, as yyyy-MM-dd. */
    private String postedOn;
    /** Comma separated hashtags the writer used, without the #; may be empty. */
    private String tags;
    /** The one-line summary shown in bold; may be empty. */
    private String headline;
    /** The review itself. */
    private String body;

    /** Empty constructor used while a review is built step by step. */
    public Review() {
        this.reviewId = "";
        this.movieId = "";
        this.kind = Kind.AUDIENCE;
        this.authorName = "";
        this.rating = 0;
        this.likes = 0;
        this.dislikes = 0;
        this.postedOn = LocalDate.now().toString();
        this.tags = "";
        this.headline = "";
        this.body = "";
    }

    /** Constructor used by the seeded reviews. */
    public Review(String reviewId, String movieId, Kind kind, String authorName, int rating,
                  int likes, int dislikes, String postedOn, String tags, String headline,
                  String body) {
        this();
        setReviewId(reviewId);
        setMovieId(movieId);
        setKind(kind);
        setAuthorName(authorName);
        setRating(rating);
        setLikes(likes);
        setDislikes(dislikes);
        setPostedOn(postedOn);
        setTags(tags);
        setHeadline(headline);
        setBody(body);
    }

    /** Constructor used when one line is read back from data/reviews.txt. */
    public Review(String[] parts) {
        this();
        setReviewId(part(parts, 0));
        setMovieId(part(parts, 1));
        setKind(Kind.parse(part(parts, 2)));
        setAuthorName(part(parts, 3));
        setRating(toInt(part(parts, 4), 0));
        setLikes(toInt(part(parts, 5), 0));
        setDislikes(toInt(part(parts, 6), 0));
        setPostedOn(part(parts, 7));
        setTags(part(parts, 8));
        setHeadline(part(parts, 9));
        setBody(part(parts, 10));
    }
/* ------------------------------------------------------------------ *
     * Behaviour
     * ------------------------------------------------------------------ */

    /** "10/10" - the score chip on the right of the card. */
    public String ratingText() {
        return rating + "/10";
    }

    /** True for the critics, false for the customers - which card is drawn. */
    public boolean isCritic() {
        return kind == Kind.CRITIC;
    }

    /** True when this review may actually be shown: it needs a writer and some words. */
    public boolean isShowable() {
        return !authorName.isEmpty() && !(body.isEmpty() && headline.isEmpty());
    }

    /** Two letters for the placeholder avatar, e.g. "Money Control" -> "MC". */
    public String initials() {
        String clean = authorName.trim();
        if (clean.isEmpty()) {
            return "?";
        }
        String[] words = clean.split("\\s+");
        if (words.length >= 2) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase();
    }

    /**
     * "2 days ago", or "today" / "3 weeks ago" - the way people say it, not a raw date.
     *
     * A date we cannot read must not print "NaN days ago", so broken text falls back to the plain
     * date, which is still something a reader can make sense of.
     */
    public String timeAgoText() {
        LocalDate posted = postedDate();
        if (posted == null) {
            return postedOn;
        }
        long days = ChronoUnit.DAYS.between(posted, LocalDate.now());
        if (days <= 0) {
            return "today";
        }
        if (days == 1) {
            return "1 day ago";
        }
        if (days < 7) {
            return days + " days ago";
        }
        if (days < 14) {
            return "1 week ago";
        }
        if (days < 60) {
            return (days / 7) + " weeks ago";
        }
        return (days / 30) + " months ago";
    }
/**
     * The review text cut to a comfortable length for the card, with a "...more" tail.
     *
     * The card is a fixed height, so a long review has to be shortened somewhere; doing it here
     * means every screen shows the same shortened text and the full words stay in the file.
     */
    public String shortBody(int limit) {
        String clean = body.trim();
        if (limit <= 0 || clean.length() <= limit) {
            return clean;
        }
        // Cut on a word boundary so the reader never sees half a word.
        int cut = clean.lastIndexOf(' ', limit);
        if (cut < limit / 2) {
            cut = limit;
        }
        return clean.substring(0, cut).trim() + " ...more";
    }

    /** The hashtags as a list, each one already carrying its # so the chips can print it. */
    public java.util.List<String> tagList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String tag : tags.split(",")) {
            String clean = tag.trim().replace(" ", "");
            if (!clean.isEmpty()) {
                list.add(clean.startsWith("#") ? clean : "#" + clean);
            }
        }
        return list;
    }

    @Override
    public String toString() {
        return authorName + " " + ratingText();
    }

    /* ------------------------------------------------------------------ *
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, reviewId, movieId, kind.name(), authorName,
                String.valueOf(rating), String.valueOf(likes), String.valueOf(dislikes), postedOn,
                tags, headline, body);
    }

    /** Factory method: one line of data/reviews.txt becomes a Review object. */
    public static Review fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty review line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a review line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new Review(parts);
    }
/* ------------------------------------------------------------------ *
     * Getters and setters (the setters clean or repair the input)
     * ------------------------------------------------------------------ */

    public String getReviewId() {
        return reviewId;
    }

    public void setReviewId(String reviewId) {
        this.reviewId = UiUtil.clean(reviewId);
    }

    public String getMovieId() {
        return movieId;
    }

    public void setMovieId(String movieId) {
        this.movieId = UiUtil.clean(movieId);
    }

    public Kind getKind() {
        return kind;
    }

    public final void setKind(Kind kind) {
        this.kind = (kind == null) ? Kind.AUDIENCE : kind;
    }

    /** The reader's name, or the publication's name for a critic. */
    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = UiUtil.clean(authorName);
    }

    public int getRating() {
        return rating;
    }

    /** A score outside 1..10 is a typo, so it becomes "not rated" (0) instead of "13/10". */
    public final void setRating(int rating) {
        this.rating = (rating < 0 || rating > 10) ? 0 : rating;
    }

    public int getLikes() {
        return likes;
    }

    public final void setLikes(int likes) {
        this.likes = Math.max(0, likes);
    }

    public int getDislikes() {
        return dislikes;
    }

    public final void setDislikes(int dislikes) {
        this.dislikes = Math.max(0, dislikes);
    }

    public String getPostedOn() {
        return postedOn;
    }

    /** A day we cannot read is left as text; timeAgoText then shows it verbatim. */
    public void setPostedOn(String postedOn) {
        this.postedOn = UiUtil.clean(postedOn);
    }
public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = (tags == null) ? "" : tags.trim();
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = UiUtil.clean(headline);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = UiUtil.clean(body);
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

    /** A broken date means "unknown", never an exception during a paint call. */
    private LocalDate postedDate() {
        try {
            return LocalDate.parse(postedOn, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
