package cinesmart.view;


import cinesmart.model.Movie;
import cinesmart.util.PosterLoader;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * MovieCardPanel is one poster card of the home grid - the repeated tile a user clicks to
 * open a film.
 *
 * It paints its own poster area rather than showing only labels, because a movie app lives or
 * dies on how the tiles look. The picture is the film's REAL poster, downloaded once by
 * {@link PosterLoader} (memory + disk cache, so the second start needs no internet). Until that
 * download finishes - and forever if it fails, there is no network, or the film has no poster
 * address - the card paints the gradient artwork it always could, with the two first letters of
 * the title on top. A card therefore NEVER shows an empty box or a broken image.
 *
 * The card does not decide what "opened" means: the dashboard passes a Runnable that opens the
 * details dialog, which keeps this class free of navigation logic.
 */
public final class MovieCardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final int CARD_WIDTH = 226;
    public static final int CARD_HEIGHT = 340;
    private static final int POSTER_HEIGHT = 178;

    private final Movie movie;
    private JLabel statusLabel;

    /**
     * @param movie  the film to display
     * @param onOpen runs when the card, the poster or the bottom button is clicked
     */
    public MovieCardPanel(Movie movie, Runnable onOpen) {
        this.movie = movie;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(Theme.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
                Theme.cardBorder(), UiUtil.padded(0, 0, 8, 0)));
        setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMaximumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setMinimumSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(clicker(onOpen));

        add(buildPoster(onOpen), BorderLayout.NORTH);
        add(buildInfo(onOpen), BorderLayout.CENTER);
    }

    public Movie getMovie() {
        return movie;
    }

    /** The dashboard uses this to explain why a film cannot be booked right now. */
    public void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    /** One mouse adapter reused by the card and the poster, so one click = one action. */
    private static java.awt.event.MouseAdapter clicker(Runnable onOpen) {
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                onOpen.run();
            }
        };
    }

    /* ------------------------------------------------------------------ *
     * Poster area
     * ------------------------------------------------------------------ */

    private JComponent buildPoster(Runnable onOpen) {
        PosterArt art = new PosterArt();
        Dimension size = new Dimension(CARD_WIDTH, POSTER_HEIGHT);
        art.setPreferredSize(size);
        art.setMinimumSize(size);
        art.setMaximumSize(size);
        art.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        art.addMouseListener(clicker(onOpen));
        // Ask for the real poster now; the card paints its own artwork until it arrives, so the
        // grid never waits and the window never freezes. load() returns immediately.
        art.requestPoster();
        return art;
    }

    /**
     * Paints the poster: the downloaded picture when there is one, otherwise the gradient whose
     * colour comes from the film id, so it never changes between screens or between runs.
     */
    private final class PosterArt extends JComponent {

        private static final long serialVersionUID = 1L;

        /** The real poster, or null while it is still downloading / unavailable. */
        private BufferedImage poster;

        /** Starts the one-off download and asks for a repaint when it lands. */
        void requestPoster() {
            String url = movie.getPosterUrl();
            if (url.isEmpty()) {
                return; // no poster for this film - the gradient below is the poster
            }
            BufferedImage ready = PosterLoader.cached(url); // already downloaded this run?
            if (ready != null) {
                poster = ready;
                return;
            }
            PosterLoader.load(url, image -> { // comes back on the EDT
                if (image != null) {
                    poster = image;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            Color base = Theme.posterColor(movie.getMovieId().hashCode());

            if (poster != null) {
                // The real artwork, scaled to cover the tile so it is never stretched. No title
                // is drawn over it: the card prints the film name in clear text directly below,
                // and the poster's own lettering is usually still visible in the covered crop.
                PosterLoader.paintCover(g2, poster, 0, 0, width, height);
            } else {
                paintFallbackArt(g2, base, width, height);
            }

            drawBadge(g2, "\u2605 " + movie.ratingText(), width, 10, 22,
                    new Color(0, 0, 0, 110), Color.WHITE, Theme.titleFont(12));
            drawBadge(g2, movie.getCertification(), width, 38, 19,
                    new Color(255, 255, 255, 215), base.darker().darker(), Theme.smallBoldFont());

            g2.dispose();
        }

        /**
         * The artwork used until the poster arrives, or forever if it cannot be had: a gradient
         * taken from the film id, film-strip perforations and the two first letters of the title.
         */
        private void paintFallbackArt(Graphics2D g2, Color base, int width, int height) {
            g2.setPaint(Theme.gradient(base.darker(), base, true));
            g2.fillRect(0, 0, width, height);

            // Soft diagonal band so the flat gradient reads like printed artwork.
            g2.setColor(new Color(255, 255, 255, 26));
            for (int stripe = -height; stripe < width; stripe += 26) {
                g2.fill(new Polygon(new int[]{stripe, stripe + 12, stripe + 12 - height,
                                stripe - height},
                        new int[]{0, 0, height, height}, 4));
            }

            // Film-strip perforations: a cinema cue drawn, not an image file.
            g2.setColor(new Color(255, 255, 255, 60));
            for (int hole = 12; hole < width - 16; hole += 30) {
                g2.fillRoundRect(hole, height - 20, 18, 10, 4, 4);
            }

            // Two first letters of the title: the classic "no artwork yet" look.
            String initials = movie.monogram();
            if (!initials.isEmpty()) {
                g2.setFont(Theme.titleFont(44));
                g2.setColor(new Color(255, 255, 255, 225));
                FontMetrics metrics = g2.getFontMetrics();
                g2.drawString(initials, (width - metrics.stringWidth(initials)) / 2, 112);
            }
        }

        /** Small rounded label drawn on top of the poster (rating and certification). */
        private void drawBadge(Graphics2D g2, String text, int width, int top, int height,
                               Color background, Color foreground, java.awt.Font font) {
            if (text == null || text.isEmpty()) {
                return;
            }
            g2.setFont(font);
            FontMetrics metrics = g2.getFontMetrics();
            int badgeWidth = metrics.stringWidth(text) + 16;
            g2.setColor(background);
            g2.fillRoundRect(width - badgeWidth - 10, top, badgeWidth, height, 12, 12);
            g2.setColor(foreground);
            g2.drawString(text, width - badgeWidth - 2, top + metrics.getAscent() + 3);
        }
    }

    /* ------------------------------------------------------------------ *
     * Text and button under the poster
     * ------------------------------------------------------------------ */

    private JComponent buildInfo(Runnable onOpen) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(UiUtil.padded(10, 12, 2, 12));

        panel.add(titleLabel());

        JLabel meta = UiUtil.muted(movie.metaLine());
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(meta);

        statusLabel = UiUtil.muted(" ");
        statusLabel.setFont(Theme.smallBoldFont());
        statusLabel.setForeground(Theme.BLUE);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setBorder(UiUtil.padded(6, 0, 4, 0));
        panel.add(statusLabel);

        panel.add(Box.createVerticalGlue());

        JButton button = movie.isReleased()
                ? UiUtil.primaryButton("Book Now", event -> onOpen.run())
                : UiUtil.outlineButton("View Details", event -> onOpen.run());
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFont(Theme.smallBoldFont());
        button.setBorder(UiUtil.padded(7, 14, 7, 14));
        Dimension size = new Dimension(CARD_WIDTH - 26, 32);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
        panel.add(button);

        return panel;
    }

    /** Title label: HTML wrapping keeps long names inside the card without manual measuring. */
    private JLabel titleLabel() {
        JLabel label = new JLabel("<html><div style='width:" + (CARD_WIDTH - 32) + "px'>"
                + movie.getTitle() + "</div></html>");
        label.setFont(Theme.titleFont(14));
        label.setForeground(Theme.TEXT);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setPreferredSize(new Dimension(CARD_WIDTH - 24, 38));
        label.setMaximumSize(new Dimension(CARD_WIDTH - 24, 38));
        return label;
    }
}
