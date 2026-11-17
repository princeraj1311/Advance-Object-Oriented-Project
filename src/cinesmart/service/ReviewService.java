package cinesmart.service;


import cinesmart.model.Movie;
import cinesmart.model.Review;
import cinesmart.util.DataStore;
import cinesmart.util.UiUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ReviewService answers everything the details screen says about what people thought of a film:
 * the headline count, the sentiment chips, the reader comments and the critic reviews.
 *
 * The rules that are NOT in the model live here - which reviews belong to which film, which of
 * them are shown first, and the two scores a reader of the screen expects to see. A panel that
 * averaged the ratings itself would round differently on a second screen.
 *
 * Constructor injection, exactly like the other services, so a test can hand in its own store.
 */
public class ReviewService {

    /** How many tags the chips row shows before it needs a "more" arrow. */
    public static final int CHIP_LIMIT = 4;

    private final DataStore store;

    public ReviewService() {
        this(DataStore.get());
    }

    public ReviewService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *
     * The two lists of cards
     * ------------------------------------------------------------------ */

    /** The customer reviews of a film: most liked first, because that is what people want. */
    public List<Review> audienceReviews(Movie movie) {
        return reviewsOf(movie, Review.Kind.AUDIENCE);
    }

    /** The published critic reviews of a film, in the order the file lists them. */
    public List<Review> criticReviews(Movie movie) {
        return reviewsOf(movie, Review.Kind.CRITIC);
    }

    /**
     * One kind of review of one film, already filtered down to the cards worth showing.
     *
     * A review with no writer and no words is dropped rather than drawn as an empty box, and the
     * result is unmodifiable so a panel cannot reorder the shared list.
     */
    public List<Review> reviewsOf(Movie movie, Review.Kind kind) {
        List<Review> found = new ArrayList<>();
        if (movie == null || kind == null) {
            return found;
        }
        String movieId = movie.getMovieId();
        for (Review review : store.getReviews()) {
            if (review.getKind() == kind && review.isShowable()
                    && review.getMovieId().equalsIgnoreCase(movieId)) {
                found.add(review);
            }
        }
        if (kind == Review.Kind.AUDIENCE) {
            found.sort(Comparator.comparingInt(Review::getLikes).reversed());
        }
        return Collections.unmodifiableList(found);
    }

    /** True when the film has at least one card to show in either section. */
    public boolean hasReviews(Movie movie) {
        return !audienceReviews(movie).isEmpty() || !criticReviews(movie).isEmpty();
    }

/* ------------------------------------------------------------------ *
     * The numbers above the cards
     * ------------------------------------------------------------------ */

    /** "Summary of 26.9K reviews." - the sentence above the sentiment chips. */
    public String summaryText(Movie movie) {
        if (movie == null) {
            return "No reviews yet.";
        }
        if (movie.isTmdbMovie()) {
            return movie.ratingSourceName() + " audience rating based on "
                    + reviewCountText(movie) + ".";
        }
        return "Summary of " + reviewCountText(movie) + ".";
    }

    /**
     * The count in the "Top reviews" heading.
     *
     * The film line carries the headline number (41.2K on the first film), but a movies.txt written
     * before this stage has no such column and so reports zero - and printing "0 reviews" directly
     * above four review cards would simply be a lie. When the stored total is zero we count the
     * rows we can actually show instead. Above zero the stored total wins, because that is the
     * curated headline number and it stands for reviews we do not keep line by line.
     */
    public String reviewCountText(Movie movie) {
        if (movie == null) {
            return "0 reviews";
        }
        if (movie.getTotalReviews() > 0) {
            return movie.reviewCountText();
        }
        int shown = audienceReviews(movie).size() + criticReviews(movie).size();
        return shown + (shown == 1 ? " review" : " reviews");
    }

    /** The chips under the summary, most used first. */
    public List<Movie.ReviewTag> topTags(Movie movie) {
        return (movie == null) ? Collections.emptyList() : movie.topReviewTags(CHIP_LIMIT);
    }

    /** The average score the customers gave, 0 when nobody rated the film. */
    public double audienceScore(Movie movie) {
        return average(audienceReviews(movie));
    }

    /** The average score the critics gave, 0 when nobody reviewed it. */
    public double criticScore(Movie movie) {
        return average(criticReviews(movie));
    }

    /**
     * "8.1/10 from 6 critics" - or a plain "No critic reviews yet" when there are none.
     *
     * The count is written out because one critic saying 9/10 means something very different from
     * six critics saying 9/10, and a bare score hides that difference.
     */
    public String criticScoreText(Movie movie) {
        List<Review> critics = criticReviews(movie);
        if (critics.isEmpty()) {
            return "No critic reviews yet";
        }
        double score = average(critics);
        if (score <= 0) {
            return critics.size() + (critics.size() == 1 ? " critic review" : " critic reviews");
        }
        return String.format("%.1f/10 from %d critics", score, critics.size());
    }

    /** "8.4/10 from 12 user reviews" - the same idea for the customers. */
    public String audienceScoreText(Movie movie) {
        List<Review> users = audienceReviews(movie);
        if (users.isEmpty()) {
            return "No user reviews yet";
        }
        double score = average(users);
        if (score <= 0) {
            return users.size() + (users.size() == 1 ? " user review" : " user reviews");
        }
        return String.format("%.1f/10 from %d user reviews", score, users.size());
    }

    /**
     * How many readers used one hashtag across a film's customer reviews.
     *
     * The chips on the details screen show the film's overall hashtags from its data line, but a
     * per-film count of one specific word is what a "top reviews" screen would filter on, so the
     * rule lives here rather than inside a button.
     */
    public int countTag(Movie movie, String tag) {
        String wanted = UiUtil.clean(tag).replace("#", "").toLowerCase();
        if (wanted.isEmpty()) {
            return 0;
        }
        int found = 0;
        for (Review review : audienceReviews(movie)) {
            for (String one : review.tagList()) {
                if (one.replace("#", "").equalsIgnoreCase(wanted)) {
                    found++;
                }
            }
        }
        return found;
    }

    /** Only reviews with a score can be averaged; the rest are skipped rather than counted as 0. */
    private double average(List<Review> reviews) {
        int total = 0;
        int scored = 0;
        for (Review review : reviews) {
            if (review.getRating() > 0) {
                total += review.getRating();
                scored++;
            }
        }
        return (scored == 0) ? 0 : Math.round(total * 10.0 / scored) / 10.0;
    }
}
