package cinesmart.view;


import cinesmart.model.Movie;
import cinesmart.model.CinemaVenue;
import cinesmart.model.ShowTime;
import cinesmart.service.CatalogueService;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * DashboardPanel is the home screen a customer lands on after logging in (Stage 2): the live
 * stats of the selected city, the filter row, and the poster grid of films.
 *
 * It shows, it does not decide: every list comes from {@link CatalogueService}, which owns the
 * filtering and sorting rules. The panel keeps no copy of the catalogue, so changing a city or a
 * filter simply calls {@link #refresh()} and the screen is rebuilt from the service again - one
 * source of truth, so the grid can never disagree with the stats.
 *
 * Clicking a card opens {@link MovieDetailsDialog}; picking a show time there is where Stage 4
 * hands over to the seat map.
 */
public final class DashboardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame frame;
    private final CatalogueService catalogue;

    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private JLabel countLabel;
    private JPanel statsBar;
    private JPanel tabBar;
    private JPanel grid;
    private JTextField searchField;
    private JComboBox<String> languageCombo;
    private JComboBox<String> genreCombo;
    private JComboBox<String> durationCombo;
    private JComboBox<String> sortCombo;

    /** false = the "Now Showing" tab (bookable films), true = "Coming Soon" (announced films). */
    private boolean showingComingSoon;

    public DashboardPanel(MainFrame frame, CatalogueService catalogue) {
        this.frame = frame;
        this.catalogue = (catalogue == null) ? new CatalogueService() : catalogue;

        setLayout(new BorderLayout());
        setBackground(Theme.WINDOW_BG);
        setBorder(UiUtil.padded(18, 22, 0, 22));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildGrid(), BorderLayout.CENTER);
        refresh();
    }

    /* ------------------------------------------------------------------ *
     * Header: title, stats cards and the filter row
     * ------------------------------------------------------------------ */

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        titleLabel = UiUtil.heading("Movies");
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);

        subtitleLabel = UiUtil.subtitle("Pick a film to see its story and show times");
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(subtitleLabel);

        header.add(UiUtil.gap(14));
        statsBar = new JPanel(new GridLayout(1, 5, 12, 0));
        statsBar.setOpaque(false);
        statsBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        // A vertical BoxLayout also clamps WIDTH to maximumSize, so the row must be unbounded:
        // the header is the NORTH card of a BorderLayout, which gives it exactly its preferred
        // height, so an unbounded maximum can never stretch these cards.
        statsBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        header.add(statsBar);

        header.add(UiUtil.gap(14));
        header.add(buildFilterRow());
        header.add(UiUtil.gap(4));
        return header;
    }

    /** A card with the two tabs on top and every filter box underneath. */
    private JComponent buildFilterRow() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                Theme.cardBorder(), UiUtil.padded(10, 12, 10, 12)));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Unbounded so the row can span the window and so the filter boxes are never clipped
        // when the window is narrow and FlowLayout wraps them onto a second line.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
        tabBar = tabs;

        countLabel = UiUtil.muted(" ");
        countLabel.setFont(Theme.smallBoldFont());
        tabs.add(Box.createHorizontalGlue());
        tabs.add(UiUtil.outlineButton("Cinema directory", event -> showCinemaDirectory()));
        tabs.add(countLabel);
        card.add(tabs);
        card.add(UiUtil.gap(6));

        JPanel filters = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 2));
        filters.setOpaque(false);
        filters.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField = UiUtil.textField(20);
        searchField.putClientProperty("JTextField.placeholderText", "Title, genre, cast or director");
        searchField.getAccessibleContext().setAccessibleName("Search films");
        searchField.addActionListener(event -> refreshGrid());
        searchField.getDocument().addDocumentListener(searchListener());
        filters.add(UiUtil.fieldLabel("Search"));
        filters.add(searchField);

        filters.add(Box.createHorizontalStrut(4));
        filters.add(UiUtil.fieldLabel("Language"));
        languageCombo = new JComboBox<>(asArray(catalogue.languageOptions()));
        languageCombo.addActionListener(event -> refreshGrid());
        filters.add(languageCombo);

        filters.add(UiUtil.fieldLabel("Genre"));
        genreCombo = new JComboBox<>(asArray(catalogue.genreOptions()));
        genreCombo.addActionListener(event -> refreshGrid());
        filters.add(genreCombo);

        filters.add(UiUtil.fieldLabel("Duration"));
        durationCombo = new JComboBox<>(CatalogueService.DURATION_BANDS);
        durationCombo.addActionListener(event -> refreshGrid());
        filters.add(durationCombo);

        filters.add(UiUtil.fieldLabel("Sort by"));
        sortCombo = new JComboBox<>(CatalogueService.SORT_OPTIONS);
        sortCombo.addActionListener(event -> refreshGrid());
        filters.add(sortCombo);

        filters.add(UiUtil.outlineButton("Reset", event -> resetFilters()));
        card.add(filters);
        return card;
    }

    /** Rebuilds the two tab buttons so the active one is always the blue one. */
    private void paintTabs() {
        tabBar.removeAll();
        tabBar.add(nowShowingTab());
        tabBar.add(comingSoonTab());
        countLabel.setText(" ");
        tabBar.add(Box.createHorizontalGlue());
        tabBar.add(countLabel);
        tabBar.revalidate();
        tabBar.repaint();
    }

    private JButton nowShowingTab() {
        return tabButton("Now Showing", !showingComingSoon, event -> selectTab(false));
    }

    private JButton comingSoonTab() {
        return tabButton("Coming Soon", showingComingSoon, event -> selectTab(true));
    }

    private JButton tabButton(String text, boolean active,
                              java.awt.event.ActionListener action) {
        JButton button = active ? UiUtil.primaryButton(text, action)
                : UiUtil.outlineButton(text, action);
        button.setFont(Theme.smallBoldFont());
        return button;
    }

    private void selectTab(boolean comingSoon) {
        if (showingComingSoon == comingSoon) {
            return;
        }
        showingComingSoon = comingSoon;
        paintTabs();
        refreshHeader();
        refreshGrid();
    }

    /** A listener that refreshes the grid while the user types, so search feels instant. */
    private DocumentListener searchListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refreshGrid();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refreshGrid();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refreshGrid();
            }
        };
    }

    private static String[] asArray(List<String> values) {
        return values.toArray(new String[values.size()]);
    }

    /* ------------------------------------------------------------------ *
     * The poster grid
     * ------------------------------------------------------------------ */

    private JComponent buildGrid() {
        // WrapLayout, not FlowLayout: FlowLayout only asks for one row, so the scroll pane would
        // size the grid for one row and the second row of films could never be reached.
        grid = new JPanel(new WrapLayout(FlowLayout.LEFT, 16, 16));
        grid.setOpaque(false);
        JScrollPane scroll = new JScrollPane(grid,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setViewportBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /**
     * Rebuilds everything that depends on data: the city in the title, the stats, the grid.
     * MainFrame calls this when the city in the header changes; every filter control calls the
     * cheaper refreshGrid().
     */
    public void refresh() {
        refreshComboLists();
        paintTabs();
        refreshHeader();
        refreshStats();
        refreshGrid();
    }

    /** The filter lists come from the catalogue, so a new language needs no code change. */
    private void refreshComboLists() {
        keepChoice(languageCombo, asArray(catalogue.languageOptions()));
        keepChoice(genreCombo, asArray(catalogue.genreOptions()));
    }

    /** Replacing a combo model resets the user's choice, so the old value is put back. */
    private static void keepChoice(JComboBox<String> combo, String[] values) {
        Object previous = combo.getSelectedItem();
        combo.setModel(new DefaultComboBoxModel<>(values));
        if (previous != null) {
            combo.setSelectedItem(previous);
        }
    }

    private void refreshHeader() {
        String city = Session.selectedCity();
        if (showingComingSoon) {
            titleLabel.setText("Coming soon in " + city);
            subtitleLabel.setText("Announced films - read the story now, booking opens "
                    + "on the release date");
        } else {
            titleLabel.setText("Now showing in " + city);
            subtitleLabel.setText("Pick a film to see its story, its cinemas and its "
                    + "show times in " + city);
        }
    }

    private void refreshStats() {
        String city = Session.selectedCity();
        statsBar.removeAll();
        statsBar.add(new StatCard("Now showing", catalogue.countNowShowing(), "films out today",
                Theme.BLUE));
        statsBar.add(new StatCard("Coming soon", catalogue.countComingSoon(), "announced",
                Theme.ORANGE));
        statsBar.add(new StatCard("OSM cinemas", catalogue.countPublicCinemasIn(city),
                "public listings in " + city, Theme.PURPLE));
        statsBar.add(new StatCard("Shows today", catalogue.countShowsToday(city), "on " + city,
                Theme.GREEN));
        statsBar.add(new StatCard("Seats free", catalogue.countSeatsFree(city), "today in " + city,
                Theme.RED));
        statsBar.revalidate();
        statsBar.repaint();
    }

    private void showCinemaDirectory() {
        String city = Session.selectedCity();
        List<CinemaVenue> venues = catalogue.publicCinemasIn(city);
        if (venues.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No public OpenStreetMap cinema listings are cached for " + city
                    + ". Connect to the internet and restart the app to refresh the directory.",
                    "Cinema directory", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        StringBuilder text = new StringBuilder("Public venue listings for ")
                .append(city).append(" (informational only):\n\n");
        for (CinemaVenue venue : venues) {
            text.append(venue.getName());
            if (!venue.getAddress().isEmpty()) {
                text.append("\n  ").append(venue.getAddress());
            }
            text.append("\n  ").append(venue.getSourceUrl()).append("\n\n");
        }
        text.append("Data © OpenStreetMap contributors.");
        JOptionPane.showMessageDialog(this, text.toString(), "Cinema directory",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * The only code that changes the grid, so the grid can never show data that disagrees with
     * the filters. Called by the search box, every combo box, the tabs and refresh().
     */
    private void refreshGrid() {
        String city = Session.selectedCity();
        List<Movie> movies = catalogue.filterForCity(
                UiUtil.textOf(searchField),
                selected(languageCombo),
                selected(genreCombo),
                selected(durationCombo),
                selected(sortCombo),
                city,
                !showingComingSoon);

        grid.removeAll();
        for (Movie movie : movies) {
            MovieCardPanel card = new MovieCardPanel(movie, () -> openMovie(movie));
            applyStatus(card, movie);
            grid.add(card);
        }
        if (movies.isEmpty()) {
            grid.add(emptyCard());
        }
        countLabel.setText(movies.size() + (movies.size() == 1 ? " film" : " films")
                + (showingComingSoon ? " announced" : " in " + city));

        grid.revalidate();
        grid.repaint();
        scrollToTop();
    }

    private static String selected(JComboBox<String> combo) {
        Object value = combo.getSelectedItem();
        return (value == null) ? CatalogueService.FILTER_ALL : value.toString();
    }

    /** After a filter change the user should see the first row, not the middle of the list. */
    private void scrollToTop() {
        Component parent = grid.getParent();
        if (parent instanceof javax.swing.JViewport) {
            ((javax.swing.JViewport) parent).setViewPosition(new java.awt.Point(0, 0));
        }
    }

    /** Clears every filter back to its "everything" value. */
    private void resetFilters() {
        searchField.setText("");
        languageCombo.setSelectedIndex(0);
        genreCombo.setSelectedIndex(0);
        durationCombo.setSelectedItem(CatalogueService.DUR_ANY);
        sortCombo.setSelectedIndex(0);
        refreshGrid();
    }

    /**
     * The line under the title that answers the only question that matters on a poster: can I
     * watch this here, and from how much?
     */
    private void applyStatus(MovieCardPanel card, Movie movie) {
        String city = Session.selectedCity();
        if (!movie.isReleased()) {
            card.setStatus("Opens " + movie.releaseDateText(), Theme.ORANGE);
            return;
        }
        List<ShowTime> shows = catalogue.bookableShows(movie, city);
        if (shows.isEmpty()) {
            card.setStatus("No show left in " + city, Theme.RED);
            return;
        }
        card.setStatus(shows.size() + (shows.size() == 1 ? " show" : " shows")
                + " \u00b7 from Rs " + cheapestPrice(shows), Theme.GREEN);
    }

    private static int cheapestPrice(List<ShowTime> shows) {
        int cheapest = Integer.MAX_VALUE;
        for (ShowTime show : shows) {
            cheapest = Math.min(cheapest, show.getPrice());
        }
        return (cheapest == Integer.MAX_VALUE) ? 0 : cheapest;
    }

    private JComponent emptyCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE_SOFT);
        card.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                Theme.cardBorder(), UiUtil.padded(24, 26, 24, 26)));
        card.add(UiUtil.heading("No film matches those filters"));
        JLabel hint = UiUtil.muted("Clear the search box, or press Reset, or choose another "
                + "city in the top bar.");
        hint.setFont(Theme.bodyFont());
        card.add(hint, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(560, 110));
        return card;
    }

    /* ------------------------------------------------------------------ *
     * Opening a film
     * ------------------------------------------------------------------ */

    private void openMovie(Movie movie) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new MovieDetailsDialog(owner, movie, Session.selectedCity(), catalogue, this::pickShow)
                .setVisible(true);
        refreshStats(); // seat numbers move when a booking is confirmed (Stage 4/5)
    }

    /**
     * Stage 4 gave this method its real body: the dialog and the poster grid already hand over the
     * ShowTime that was picked, so the seat map is one navigation call away - and the map itself is
     * built by {@link SeatSelectionPanel} from the store, not from this object.
     */
    private void pickShow(ShowTime show) {
        frame.openSeatSelection(show);
    }

    /* ------------------------------------------------------------------ *
     * One number card of the stats strip
     * ------------------------------------------------------------------ */

    /** Caption, big number and a short explanation: the same shape for all five stats. */
    private static final class StatCard extends JPanel {

        private static final long serialVersionUID = 1L;

        StatCard(String caption, int value, String hint, Color accent) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
            setBackground(Theme.SURFACE);
            setBorder(javax.swing.BorderFactory.createCompoundBorder(
                    Theme.cardBorder(), UiUtil.padded(10, 14, 10, 14)));

            add(leftAligned(caption, Theme.smallBoldFont(), accent));

            JLabel number = new JLabel(String.valueOf(value));
            number.setFont(Theme.titleFont(26));
            number.setForeground(Theme.TEXT);
            number.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(number);

            add(leftAligned(hint, Theme.smallFont(), Theme.TEXT_MUTED));
        }

        private static JLabel leftAligned(String text, Font font, Color color) {
            JLabel label = new JLabel(text);
            label.setFont(font);
            label.setForeground(color);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            return label;
        }
    }
}
