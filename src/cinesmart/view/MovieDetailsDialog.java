package cinesmart.view;


import cinesmart.model.Movie;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.service.CatalogueService;
import cinesmart.service.CastService;
import cinesmart.service.ReviewService;
import cinesmart.util.PosterLoader;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.net.URI;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

/**
 * MovieDetailsDialog is what opens when a poster card is clicked: everything the site shows about
 * the film - the story, the cast and crew with their photographs, the customer reviews and the
 * critic reviews - plus every cinema in the selected city that screens it, with the available times
 * under each cinema.
 *
 * The body is two tabs so that the credits and the reviews never push the show times, which are the
 * part a customer is here to use, off the bottom of the screen.
 *
 * This dialog is the visible proof of the Stage 3 relationship work: a film, a cinema, a show time,
 * a credited person and a review are five separate files joined here through {@link CatalogueService}
 * and the two new services, which is why nothing in this class needs to know how data is stored.
 *
 * Choosing a time calls the Consumer the dashboard supplied. Stage 2 passes a placeholder
 * message; Stage 4 passes the seat map screen - the dialog itself never changes.
 */
public final class MovieDetailsDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final Movie movie;
    private final String city;
    private final CatalogueService catalogue;
    private final Consumer<ShowTime> onPick;

    public MovieDetailsDialog(Window owner, Movie movie, String city,
                              CatalogueService catalogue, Consumer<ShowTime> onPick) {
        super(owner, "Movie details", ModalityType.APPLICATION_MODAL);
        this.movie = movie;
        this.city = city;
        this.catalogue = catalogue;
        this.onPick = onPick;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setBackground(Theme.WINDOW_BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        setPreferredSize(new Dimension(860, 700));
        pack();
        UiUtil.center(this);
    }

    /* ------------------------------------------------------------------ *
     * Title area with the mini poster
     * ------------------------------------------------------------------ */

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(true);
        header.setBackground(Theme.SURFACE);
        header.setBorder(paddedCardBorder());

        header.add(new PosterBanner(movie, 104, 150), BorderLayout.WEST);
        header.add(buildHeaderText(), BorderLayout.CENTER);
        header.add(closeButton(), BorderLayout.EAST);
        return header;
    }

    /** Card outline plus the padding every card in the app uses. */
    private javax.swing.border.Border paddedCardBorder() {
        return javax.swing.BorderFactory.createCompoundBorder(
                Theme.cardBorder(), UiUtil.padded(16, 16, 16, 16));
    }

    private JButton closeButton() {
        JButton button = UiUtil.outlineButton("Close", event -> dispose());
        button.setAlignmentY(Component.TOP_ALIGNMENT);
        return button;
    }

    private JComponent buildHeaderText() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(movie.getTitle());
        title.setFont(Theme.titleFont(24));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        String rating = movie.ratingSourceName() + " " + movie.ratingText() + " / 10";
        if (movie.isTmdbMovie()) {
            rating += " (" + movie.getTotalReviews() + " votes)";
        }
        JLabel meta = UiUtil.subtitle(movie.metaLine() + "  \u00b7  " + movie.getCertification()
                + "  \u00b7  \u2605 " + rating);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(meta);

        if (movie.isTmdbMovie()) {
            JButton reviewsLink = UiUtil.outlineButton("View TMDb reviews",
                    event -> openTmdbReviews());
            reviewsLink.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(reviewsLink);
        }

        JLabel status = new JLabel(statusLine());
        status.setFont(Theme.smallBoldFont());
        status.setForeground(movie.isReleased() ? Theme.GREEN : Theme.ORANGE);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        status.setBorder(UiUtil.padded(10, 0, 4, 0));
        panel.add(status);

        panel.add(UiUtil.gap(4));
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void openTmdbReviews() {
        String url = movie.tmdbReviewsUrl();
        try {
            if (url.isEmpty() || !Desktop.isDesktopSupported()) {
                throw new IllegalStateException("A web browser is not available.");
            }
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception problem) {
            UiUtil.error(this, "Could not open the TMDb review page: " + problem.getMessage());
        }
    }

    /** "Now showing - 8 shows in Bengaluru" or "Coming soon - opens 12 Dec 2026". */
    private String statusLine() {
        if (!movie.isReleased()) {
            return "Coming soon - opens " + movie.releaseDateText();
        }
        int shows = catalogue.bookableShows(movie, city).size();
        int cinemas = catalogue.showTimesByTheatre(movie, city).size();
        if (shows == 0) {
            return "No bookable show left in " + city;
        }
        return "Now showing - " + shows + (shows == 1 ? " show" : " shows") + " in " + city
                + " across " + cinemas + (cinemas == 1 ? " cinema" : " cinemas");
    }

    /* ------------------------------------------------------------------ *
     * Body: the two tabs
     * ------------------------------------------------------------------ */

    /**
     * The body is a tabbed pane: everything you read about the film on the left tab, everything
     * you do about it on the right.
     *
     * A single long scroll with the credits, the reviews and ten cinemas on it would be the honest
     * layout of a web page, but on a 760 px dialog it buries the show times - the only part of
     * this screen a customer actually needs - below a screenful of reviews. Two tabs keep the
     * booking path exactly one click from the title, which is what it was before the reviews were
     * added.
     */
    private JComponent buildBody() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(Theme.smallBoldFont());
        tabs.setBackground(Theme.WINDOW_BG);
        tabs.setForeground(Theme.TEXT);
        tabs.setBorder(UiUtil.padded(8, 0, 0, 0));
        tabs.addTab("About the film", scrollable(aboutStack()));
        tabs.addTab("Shows in " + city, scrollable(showsStack()));
        return tabs;
    }

    /** The About tab: the story, the two credits rows, then the reviews and the critic reviews. */
    private JComponent aboutStack() {
        JPanel stack = stack();
        stack.add(storyCard());
        stack.add(UiUtil.gap(16));
        stack.add(sectionCard("Cast & crew", new CastPanel(movie, new CastService())));
        stack.add(UiUtil.gap(16));
        stack.add(sectionCard(null, new ReviewsPanel(movie, new ReviewService())));
        return stack;
    }

    /** The Shows tab: the cinema cards with their bookable times, exactly as before. */
    private JComponent showsStack() {
        JPanel stack = stack();
        stack.add(showTimeCards());
        return stack;
    }

    /** A vertical stack that is only as tall as its cards, never stretched to the viewport. */
    private JPanel stack() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(UiUtil.padded(14, 6, 14, 6));
        return panel;
    }

    /**
     * Puts a block inside a white card with an optional caption above it.
     *
     * One helper for both tabs so the cast rows and the review cards sit on exactly the same white
     * surface as the story card - which is what makes the tab read as one screen rather than three
     * separate widgets that happen to be stacked.
     */
    private JComponent sectionCard(String caption, JComponent content) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(paddedCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel inside = new JPanel();
        inside.setOpaque(false);
        inside.setLayout(new BoxLayout(inside, BoxLayout.Y_AXIS));
        if (caption != null && !caption.isEmpty()) {
            JLabel label = UiUtil.fieldLabel(caption);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            inside.add(label);
            inside.add(UiUtil.gap(10));
        }
        inside.add(content);
        inside.add(UiUtil.verticalGlue());
        card.add(inside, BorderLayout.CENTER);
        return fullWidth(card, caption == null ? 240 : 120);
    }

    /**
     * Wraps a stack in a scroll pane.
     *
     * The stack goes into NORTH of a BorderLayout panel: BoxLayout would otherwise stretch every
     * card to fill the viewport and the cards would look empty and enormous.
     */
    private JComponent scrollable(JComponent stack) {
        JPanel viewport = new JPanel(new BorderLayout());
        viewport.setOpaque(false);
        viewport.add(stack, BorderLayout.NORTH);
        return new JScrollPane(viewport, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    /** The wide card: story, director, cast - the text Movie already knows how to format. */
    private JComponent storyCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(paddedCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caption = UiUtil.fieldLabel("Story");
        card.add(caption, BorderLayout.NORTH);

        JLabel story = new JLabel(movie.detailHtml());
        story.setFont(Theme.bodyFont());
        story.setForeground(Theme.TEXT_MUTED);
        story.setVerticalAlignment(SwingConstants.TOP);
        story.setBorder(UiUtil.padded(8, 0, 0, 0));
        card.add(story, BorderLayout.CENTER);
        fullWidth(card, 190);
        return card;
    }

    /**
     * BoxLayout only widens a card when its maximum width is unbounded, so every card in this
     * dialog sets it here. The height hint is used only when the preferred height is smaller.
     */
    private static JComponent fullWidth(JPanel card, int minimumHeight) {
        Dimension preferred = card.getPreferredSize();
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                Math.max(minimumHeight, preferred.height)));
        return card;
    }

    /** One block per cinema: name, address, then the buttons for the available times. */
    private JComponent showTimeCards() {
        JPanel holder = new JPanel();
        holder.setOpaque(false);
        holder.setLayout(new BoxLayout(holder, BoxLayout.Y_AXIS));
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);

        Map<Theatre, List<ShowTime>> grouped = catalogue.showTimesByTheatre(movie, city);
        if (grouped.isEmpty()) {
            holder.add(emptyCard());
            return holder;
        }

        JLabel caption = UiUtil.fieldLabel("Show times in " + city);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        holder.add(caption);
        holder.add(UiUtil.gap(8));

        for (Map.Entry<Theatre, List<ShowTime>> entry : grouped.entrySet()) {
            holder.add(theatreCard(entry.getKey(), entry.getValue()));
            holder.add(UiUtil.gap(10));
        }
        return holder;
    }

    private JComponent emptyCard() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE_SOFT);
        card.setBorder(paddedCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(UiUtil.muted("This film is not screening in " + city
                + " right now. Pick another city in the top bar."));
        fullWidth(card, 90);
        return card;
    }

    /** One cinema block: the venue on the left/top, its times as buttons underneath. */
    private JComponent theatreCard(Theatre theatre, List<ShowTime> shows) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(paddedCardBorder());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(theatre.displayName());
        name.setFont(Theme.titleFont(15));
        name.setForeground(Theme.TEXT);
        card.add(name, BorderLayout.NORTH);

        JLabel meta = UiUtil.muted(theatre.metaLine() + "   \u00b7   " + theatre.priceFromText());
        card.add(meta, BorderLayout.WEST);

        JPanel times = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        times.setOpaque(false);
        for (ShowTime show : shows) {
            times.add(showButton(show));
        }
        card.add(times, BorderLayout.EAST);
        fullWidth(card, 96);
        return card;
    }

    /**
     * A show-time button. Past and sold-out shows stay visible but disabled, because hiding them
     * makes customers think the data is wrong.
     */
    private JButton showButton(ShowTime show) {
        if (!show.isBookable()) {
            String reason = show.isSoldOut() ? "sold out" : "started";
            JButton disabled = UiUtil.outlineButton(show.startTimeText() + "  (" + reason + ")",
                    event -> {
                        // disabled button: nothing to do, kept so the schedule reads complete
                    });
            disabled.setEnabled(false);
            disabled.setToolTipText(show.dateText() + " " + show.getScreenName() + " - " + reason);
            return disabled;
        }
        JButton button = UiUtil.primaryButton(show.startTimeText() + "  \u00b7  " + show.priceText(),
                event -> {
                    // The dialog is modal, so it has to close before the seat map behind it is
                    // reachable; the caller (DashboardPanel) then shows that screen.
                    dispose();
                    onPick.accept(show);
                });
        button.setToolTipText(show.dateText() + " \u00b7 " + show.getScreenName() + " \u00b7 "
                + show.seatsAvailable() + " of " + show.getTotalSeats() + " seats free");
        return button;
    }

    /* ------------------------------------------------------------------ *
     * Footer
     * ------------------------------------------------------------------ */

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        footer.setOpaque(true);
        footer.setBackground(Theme.SURFACE_SOFT);

        footer.add(UiUtil.muted("Pick a time to continue: the seat map of that show opens next,"
                + " and the payment screen after it."));
        footer.add(UiUtil.horizontalGlue());
        footer.add(UiUtil.outlineButton("Close", event -> dispose()));
        return footer;
    }

    /* ------------------------------------------------------------------ *
     * Mini poster next to the title
     * ------------------------------------------------------------------ */

    /**
     * The poster of the film next to the title: the real picture once PosterLoader has it, and the
     * same gradient artwork the cards use until then (or for ever, if the picture cannot be had),
     * so a film looks identical on the grid and in this dialog.
     */
    private static final class PosterBanner extends JComponent {

        private static final long serialVersionUID = 1L;
        private final Movie movie;
        private BufferedImage poster;

        PosterBanner(Movie movie, int width, int height) {
            this.movie = movie;
            Dimension size = new Dimension(width, height);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            loadPoster();
        }

        /** Same two-step lookup as the card: memory first, then a background download. */
        private void loadPoster() {
            String url = movie.getPosterUrl();
            if (url.isEmpty()) {
                return;
            }
            BufferedImage ready = PosterLoader.cached(url);
            if (ready != null) {
                poster = ready;
                return;
            }
            PosterLoader.load(url, image -> {
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
            int width = getWidth();
            int height = getHeight();
            Color base = Theme.posterColor(movie.getMovieId().hashCode());

            if (poster != null) {
                PosterLoader.paintCover(g2, poster, 0, 0, width, height);
                // Foot scrim so the rating stays readable over a bright poster.
                int scrimTop = (int) (height * 0.6f);
                g2.setPaint(new java.awt.GradientPaint(0, scrimTop, new Color(0, 0, 0, 0),
                        0, height, new Color(0, 0, 0, 200)));
                g2.fillRect(0, scrimTop, width, height - scrimTop);
            } else {
                g2.setPaint(Theme.gradient(base.brighter(), base.darker(), false));
                g2.fillRect(0, 0, width, height);

                g2.setFont(Theme.titleFont(34));
                g2.setColor(new Color(255, 255, 255, 235));
                FontMetrics metrics = g2.getFontMetrics();
                String monogram = movie.monogram();
                g2.drawString(monogram, (width - metrics.stringWidth(monogram)) / 2,
                        height / 2 + 6);
            }

            g2.setFont(Theme.smallBoldFont());
            g2.setColor(new Color(255, 255, 255, 220));
            g2.drawString("\u2605 " + movie.ratingText(), 8, height - 10);
            g2.dispose();
        }
    }
}
