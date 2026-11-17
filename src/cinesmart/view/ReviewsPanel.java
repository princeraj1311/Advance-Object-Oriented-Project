package cinesmart.view;


import cinesmart.model.Movie;
import cinesmart.model.Review;
import cinesmart.service.ReviewService;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * ReviewsPanel is the "Top reviews" and "Critic reviews" block of the movie details screen.
 *
 * It is the same block a streaming site shows: a headline with the number of reviews, the hashtags
 * readers used most, then the review cards themselves - and a second row for the reviews published
 * by critics, each credited to the publication rather than to a user.
 *
 * Nothing here decides anything. ReviewService answers which reviews belong to this film, in what
 * order and what the two scores are; Review writes its own "10/10", "2 days ago" and "...more".
 * The panel only draws what it is given, which is why the counts on two different screens can never
 * disagree.
 *
 * A film nobody has reviewed yet shows neither block: an empty "Top reviews" heading would only
 * tell the reader something they can already see from the "Coming soon" label.
 */
public final class ReviewsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** How many cards of each kind are drawn before the block stops. */
    public static final int CARD_LIMIT = 4;
    /** How much of a review body is shown before the "...more" tail. */
    public static final int BODY_CHARS = 190;
    /** The width the wrapping paragraph is measured at. */
    private static final int TEXT_WIDTH = 330;

    private final Movie movie;
    private final ReviewService reviews;

    public ReviewsPanel(Movie movie, ReviewService reviews) {
        this.movie = movie;
        this.reviews = (reviews == null) ? new ReviewService() : reviews;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        List<Review> audience = reviews.audienceReviews(movie);
        if (!audience.isEmpty()) {
            add(section("Top reviews", reviews.reviewCountText(movie) + "  \u203a",
                    reviews.summaryText(movie), chips(reviews.topTags(movie)), audience, false));
            add(UiUtil.gap(16));
        }

        List<Review> critics = reviews.criticReviews(movie);
        if (!critics.isEmpty()) {
            add(section("Critic reviews", reviews.criticScoreText(movie) + "  \u203a", null, null,
                    critics, true));
        }

        if (audience.isEmpty() && critics.isEmpty()) {
            String message = movie != null && movie.isTmdbMovie()
                    ? "TMDb shows the audience rating and vote count; open its review link above "
                            + "to read external reviews."
                    : "No reviews yet - nobody has written about this film.";
            JLabel none = UiUtil.muted(message);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(none);
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }
/* ------------------------------------------------------------------ *
     * The two blocks
     * ------------------------------------------------------------------ */

    /**
     * One titled block: heading on the left, the score on the right, then the cards.
     *
     * The heading and the count share a row so "Top reviews" and "26.9K reviews" read as one line,
     * the way they do on the site this screen is imitating.
     */
    private JComponent section(String title, String rightText, String summary,
                               JComponent chips, List<Review> cards, boolean critic) {
        JPanel holder = new JPanel();
        holder.setOpaque(false);
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel heading = new JLabel(title);
        heading.setFont(Theme.titleFont(16));
        heading.setForeground(Theme.TEXT);
        titleRow.add(heading, BorderLayout.WEST);

        JLabel right = new JLabel(rightText);
        right.setFont(Theme.bodyFont());
        right.setForeground(Theme.RED);
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        titleRow.add(right, BorderLayout.EAST);
        holder.add(titleRow);

        if (summary != null) {
            JLabel line = UiUtil.muted(summary);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            holder.add(UiUtil.gap(6));
            holder.add(line);
        }
        if (chips != null) {
            holder.add(UiUtil.gap(8));
            holder.add(chips);
        }
        holder.add(UiUtil.gap(10));

        JPanel row = new JPanel(new WrapLayout(WrapLayout.LEFT, 12, 12));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        int shown = 0;
        for (Review review : cards) {
            if (shown++ >= CARD_LIMIT) {
                break;
            }
            row.add(new ReviewCard(review, critic));
        }
        holder.add(row);
        holder.add(UiUtil.verticalGlue());
        return holder;
    }

    /**
     * The row of hashtag chips under the summary.
     *
     * Each chip shows the tag in red and how many readers used it in a small grey pill, which is
     * the one piece of visual language the site uses for this and the one readers recognise.
     */
    private JComponent chips(List<Movie.ReviewTag> tags) {
        JPanel row = new JPanel(new WrapLayout(WrapLayout.LEFT, 8, 8));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (Movie.ReviewTag tag : tags) {
            row.add(new TagChip(tag));
        }
        return row;
    }
/* ------------------------------------------------------------------ *
     * One review card
     * ------------------------------------------------------------------ */

    /**
     * ReviewCard is one white card: avatar and name on the left, the score on the right, then the
     * headline, the review text, and a footer with the likes and how long ago it was posted.
     *
     * A critic card is the same shape with two differences, which is exactly how the site tells
     * them apart: the name is a publication and the second line reads "See all reviews", not
     * "Booked on CineSmart". Drawing one card for both kinds is what keeps the two blocks looking
     * like a single feature instead of two bolted-together ones.
     */
    private static final class ReviewCard extends JPanel {

        private static final long serialVersionUID = 1L;

        private static final int CARD_WIDTH = 352;
        private static final int CARD_HEIGHT = 232;

        ReviewCard(Review review, boolean critic) {
            setOpaque(true);
            setBackground(Theme.SURFACE);
            setBorder(BorderFactory.createCompoundBorder(
                    Theme.cardBorder(), UiUtil.padded(14, 14, 12, 14)));
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
            setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
            setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));

            add(header(review, critic));
            add(UiUtil.gap(10));

            if (!review.getHeadline().isEmpty()) {
                JLabel headline = new JLabel(review.getHeadline());
                headline.setFont(Theme.smallBoldFont());
                headline.setForeground(Theme.TEXT);
                headline.setAlignmentX(Component.LEFT_ALIGNMENT);
                add(headline);
                add(UiUtil.gap(6));
            }

            // A wrapping text area, not a JLabel with HTML: Swing lays an HTML label out at its own
            // preferred width and paints over whatever stands next to it, which is how a long review
            // would end up spilling out of its card.
            UiUtil.Paragraph text = UiUtil.paragraph(review.shortBody(BODY_CHARS),
                    Theme.smallFont(), Theme.TEXT_MUTED, CARD_WIDTH - 56);
            text.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(text);
            add(Box.createVerticalGlue());
            add(footer(review));
        }
/** Avatar and name on the left, the red score on the right - the top line of every card. */
        private JComponent header(Review review, boolean critic) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

            JPanel who = new JPanel();
            who.setOpaque(false);
            who.setLayout(new BoxLayout(who, BoxLayout.X_AXIS));
            who.add(new Avatar(review));

            JPanel words = new JPanel();
            words.setOpaque(false);
            words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
            words.add(Box.createHorizontalGlue());

            JLabel name = new JLabel(review.getAuthorName());
            name.setFont(Theme.smallBoldFont());
            name.setForeground(Theme.TEXT);
            words.add(name);

            JLabel source = UiUtil.muted(critic
                    ? "See all reviews" : "Booked on CineSmart");
            source.setFont(Theme.smallFont());
            words.add(source);
            who.add(words);
            row.add(who, BorderLayout.WEST);

            JPanel score = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            score.setOpaque(false);
            score.add(new Stars(review.getRating()));
            JLabel text = new JLabel(review.ratingText());
            text.setFont(Theme.smallBoldFont());
            text.setForeground(Theme.TEXT);
            text.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 6, 0, 0));
            score.add(text);
            row.add(score, BorderLayout.EAST);
            return row;
        }

        /** The like count and the "2 days ago" line along the bottom of the card. */
        private JComponent footer(Review review) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

            // Plain triangles rather than emoji: an emoji renders as a colour picture whose size
            // follows the font, which makes two numbers on the same line look misaligned.
            JLabel likes = UiUtil.muted("\u25B2  " + review.getLikes()
                    + (review.getDislikes() > 0 ? "    \u25BC  " + review.getDislikes() : ""));
            likes.setFont(Theme.smallFont());
            row.add(likes, BorderLayout.WEST);

            JLabel when = UiUtil.muted(review.timeAgoText());
            when.setFont(Theme.smallFont());
            when.setHorizontalAlignment(SwingConstants.RIGHT);
            row.add(when, BorderLayout.EAST);
            return row;
        }
    }
/* ------------------------------------------------------------------ *
     * The three small painted pieces
     * ------------------------------------------------------------------ */

    /**
     * Avatar is the grey circle with the reviewer's initials on it.
     *
     * A review has no photograph in the data file, so the avatar is the whole identity - which is
     * why it is a neutral grey rather than a colour: a critic and a customer should not be told
     * apart by a hue before the reader has read a word.
     */
    private static final class Avatar extends JComponent {

        private static final long serialVersionUID = 1L;

        private final Review review;

        Avatar(Review review) {
            this.review = review;
            Dimension size = new Dimension(34, 34);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(new Color(214, 218, 228));
            g2.fill(new Ellipse2D.Float((getWidth() - size) / 2f,
                    (getHeight() - size) / 2f, size, size));
            g2.setColor(Color.WHITE);
            g2.setFont(Theme.smallBoldFont());
            FontMetrics metrics = g2.getFontMetrics();
            String initials = review.initials();
            g2.drawString(initials, (getWidth() - metrics.stringWidth(initials)) / 2,
                    getHeight() / 2 + metrics.getAscent() / 2 - 1);
            g2.dispose();
        }
    }

    /**
     * Stars paints one red star for the review, which is how the site scores out of ten - one star
     * meaning "recommended". A review with no score shows nothing rather than an empty star, so a
     * damaged file can never make the card look broken.
     */
    private static final class Stars extends JComponent {

        private static final long serialVersionUID = 1L;

        private final int rating;

        Stars(int rating) {
            this.rating = Math.max(0, Math.min(10, rating));
            Dimension size = new Dimension(rating > 0 ? 18 : 0, 18);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (rating <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.RED);
            g2.setFont(Theme.font(java.awt.Font.PLAIN, 15));
            g2.drawString("\u2605", 0, 14);
            g2.dispose();
        }
    }

    /**
     * TagChip is one rounded pill: the hashtag in red, the number of uses in a small grey badge.
     *
     * It is drawn rather than assembled from a label and a border so the two halves share one
     * rounded outline; two nested components would produce a squared-off badge inside a rounded
     * pill, which is the detail that makes the row look hand-made instead of designed.
     */
    private static final class TagChip extends JComponent {

        private static final long serialVersionUID = 1L;

        private final Movie.ReviewTag tag;

        TagChip(Movie.ReviewTag tag) {
            this.tag = tag;
            setPreferredSize(new Dimension(measure(tag), 30));
            setMinimumSize(getPreferredSize());
            setMaximumSize(getPreferredSize());
        }

        /** The width both halves need: the tag text plus the badge plus the two paddings. */
        private int measure(Movie.ReviewTag tag) {
            FontMetrics metrics = getFontMetrics(Theme.font(java.awt.Font.PLAIN, 13));
            return 16 + metrics.stringWidth(tag.label()) + 8 + 30 + 16;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            int height = getHeight() - 2;
            int badgeWidth = 30;
            int badgeLeft = getWidth() - 16 - badgeWidth;

            g2.setColor(Theme.SURFACE);
            g2.drawRoundRect(0, 0, getWidth() - 2, height, height, height);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 2, height, height, height);

            g2.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            FontMetrics metrics = g2.getFontMetrics();
            int baseline = height / 2 + metrics.getAscent() / 2 - 3;

            g2.setColor(Theme.RED);
            g2.drawString(tag.label(), 16, baseline);

            // The count sits in a small grey badge so a big number never competes with the tag.
            g2.setColor(new Color(238, 240, 246));
            g2.fillRoundRect(badgeLeft, 4, badgeWidth, height - 8, 8, 8);
            g2.setColor(Theme.TEXT_MUTED);
            g2.setFont(Theme.smallBoldFont());
            FontMetrics small = g2.getFontMetrics();
            String count = tag.countText();
            g2.drawString(count, badgeLeft + (badgeWidth - small.stringWidth(count)) / 2, baseline);
            g2.dispose();
        }
    }
}
