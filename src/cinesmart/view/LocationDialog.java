package cinesmart.view;


import cinesmart.model.Locality;
import cinesmart.service.LocationService;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * LocationDialog is the "Select your region" popup: a search box, a red <b>Detect my location</b>
 * line, a row of popular cities, and the full list of places underneath.
 *
 * It is a plain {@link JDialog} rather than a card of the main window because it has to appear on
 * top of whatever screen asked for it - the dashboard, the canteen, the offers - and a customer
 * picking a city expects a popup, exactly as on the website.
 *
 * The dialog owns no rule. Every list it shows comes from {@link LocationService}, and the place it
 * returns is the one the caller asked to remember. Detection runs through the same service, so the
 * red line can never disagree with the search box.
 */
public final class LocationDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final LocationService locations;
    private final JTextField searchField;
    private final JLabel resultLabel;
    private JPanel places;
    private JPanel popular;
    /** The place the customer settled on, or null when the dialog was cancelled. */
    private Locality chosen;

    public LocationDialog(java.awt.Window owner, LocationService locations) {
        this(owner, locations, true);
    }

    /**
     * The same dialog, optionally not modal.
     *
     * The non-modal form exists for the screenshot tool: a modal dialog blocks inside
     * {@code setVisible}, so a tool that wanted to photograph it would hang forever. A real
     * customer always gets the modal one, which is what makes the popup behave like the website's.
     *
     * @param modal true for the real popup, false only for the screenshot tool
     */
    public LocationDialog(java.awt.Window owner, LocationService locations, boolean modal) {
        super(owner, "Select your region",
                modal ? ModalityType.APPLICATION_MODAL : ModalityType.MODELESS);
        this.locations = (locations == null) ? new LocationService() : locations;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(Theme.SURFACE);

        searchField = UiUtil.textField(24);
        searchField.setToolTipText("Search for your city or area");
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                fillPlaces();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                fillPlaces();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                fillPlaces();
            }
        });
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    pickFirstResult();
                }
            }
        });

        resultLabel = UiUtil.hint(" ");
        places = new JPanel();
        popular = new JPanel();

        setLayout(new BorderLayout());
        add(searchPanel(), BorderLayout.NORTH);
        add(listPanel(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);

        fillPopular();
        fillPlaces();
        setPreferredSize(new Dimension(460, 560));
        pack();
        UiUtil.center(this);
    }

    /* ------------------------------------------------------------------ *\
     * The three parts of the popup
     * ------------------------------------------------------------------ */

    /** The search box, and under it the red "Detect my location" line. */
    private JComponent searchPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(UiUtil.padded(16, 18, 6, 18));
        panel.add(UiUtil.heading("Select your region"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(UiUtil.subtitle("Your halls, offers and distances are shown for this place"));
        panel.add(Box.createVerticalStrut(12));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        panel.add(searchField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(detectRow());
        panel.add(Box.createVerticalStrut(12));
        panel.add(UiUtil.fieldLabel("Popular cities"));
        panel.add(Box.createVerticalStrut(6));
        panel.add(popularRow());
        return panel;
    }

    /**
     * The detect line. It is a JLabel styled like a link rather than a JButton because it is an
     * ACTION with a result to report, not a command: the service runs, and the label under it says
     * exactly what it worked out and why.
     */
    private JComponent detectRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel dot = new JLabel("\u25C9");
        dot.setForeground(Theme.RED);
        dot.setFont(Theme.titleFont(14));
        row.add(dot);

        JLabel link = new JLabel("Detect my location");
        link.setFont(Theme.titleFont(14));
        link.setForeground(Theme.RED);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText("Works out where this computer is, using its own settings");
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                detect();
            }
        });
        row.add(link);
        return row;
    }

    /** The popular city chips, laid out in rows that wrap when the window is narrow. */
    private JComponent popularRow() {
        popular.setOpaque(false);
        popular.setLayout(new WrapLayout(WrapLayout.LEFT, 6, 6));
        popular.setAlignmentX(Component.LEFT_ALIGNMENT);
        return popular;
    }

    private JComponent listPanel() {
        places.setLayout(new BoxLayout(places, BoxLayout.Y_AXIS));
        places.setBorder(UiUtil.padded(6, 18, 6, 18));

        JScrollPane scroller = new JScrollPane(places, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getViewport().setBackground(Theme.SURFACE);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        return scroller;
    }

    private JComponent footer() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(UiUtil.padded(4, 18, 14, 18));
        bar.add(resultLabel, BorderLayout.WEST);
        JButton close = UiUtil.outlineButton("Cancel", event -> dispose());
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /* ------------------------------------------------------------------ *\
     * Filling the list, and what a click means
     * ------------------------------------------------------------------ */

    /** The popular city chips. Each one is the city-level row, which always has coordinates. */
    private void fillPopular() {
        popular.removeAll();
        for (Locality place : locations.popularPlaces()) {
            JButton chip = UiUtil.outlineButton(place.getCity(), event -> choose(place));
            chip.setToolTipText("Show halls and offers in " + place.getCity());
            chip.setAlignmentX(Component.LEFT_ALIGNMENT);
            popular.add(chip);
        }
        popular.revalidate();
        popular.repaint();
    }

    /**
     * The list under the search box, rebuilt on every keystroke.
     *
     * With an empty box the whole catalogue is shown, so the popup is useful before a single
     * character is typed; typing narrows it to the best twelve matches.
     */
    private void fillPlaces() {
        places.removeAll();
        java.util.List<Locality> found = locations.search(searchField.getText());
        if (found.isEmpty()) {
            JLabel none = UiUtil.muted("No place called \"" + searchField.getText().trim()
                    + "\" in our catalogue.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            places.add(none);
        }
        for (Locality place : found) {
            places.add(placeRow(place));
            places.add(Box.createVerticalStrut(4));
        }
        places.add(UiUtil.verticalGlue());
        places.revalidate();
        places.repaint();
        resultLabel.setText(found.size() + " place" + (found.size() == 1 ? "" : "s") + " found");
    }

    /**
     * One place: the name on the left, the city on the right, and the distance to the nearest hall
     * of that city underneath. That last line is why the row is worth reading - it turns a name into
     * "is that near me".
     */
    private JComponent placeRow(Locality place) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBackground(Theme.SURFACE);
        row.setBorder(BorderFactory.createCompoundBorder(UiUtil.padded(7, 9, 7, 9),
                javax.swing.BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER)));

        JLabel name = new JLabel(place.getCity()
                + (place.isCityOnly() ? "" : "  \u00b7  " + place.getArea()));
        name.setFont(Theme.bodyFont());
        name.setForeground(Theme.TEXT);
        row.add(name, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        LocationService.Nearby hall = locations.nearestHall(place.getCity(), place);
        if (hall != null) {
            JLabel near = UiUtil.muted(hall.getDistanceText());
            near.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(near);
            JLabel which = UiUtil.muted(hall.getName());
            which.setAlignmentX(Component.RIGHT_ALIGNMENT);
            right.add(which);
        }
        row.add(right, BorderLayout.EAST);

        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                choose(place);
            }
        });
        return row;
    }

    /** Enter picks the best match, so a keyboard user never has to reach for the mouse. */
    private void pickFirstResult() {
        java.util.List<Locality> found = locations.search(searchField.getText());
        if (!found.isEmpty()) {
            choose(found.get(0));
        }
    }

    /** A place was picked: remember it and close. The caller reads {@link #getChosen()}. */
    private void choose(Locality place) {
        this.chosen = place;
        locations.remember(place);
        dispose();
    }

    /**
     * The red "Detect my location" line.
     *
     * When the service works, the nearest hall of the detected city is named right in the popup, so
     * the customer can see the app is about to switch city and object if it guessed wrong. When it
     * cannot work anything out, the line says so instead of quietly picking a city.
     */
    private void detect() {
        LocationService.Detection found = locations.detect();
        if (!found.isFound()) {
            UiUtil.setHint(resultLabel, found.getSource()
                    + " - please search for your city below.", false);
            return;
        }
        Locality place = found.getPlace();
        LocationService.Nearby hall = locations.nearestHall(place.getCity(), place);
        UiUtil.setHint(resultLabel, (hall == null)
                ? found.getSource()
                : found.getSource() + "  \u00b7  nearest hall " + hall.getName()
                        + ", " + hall.getDistanceText(), true);
        choose(place);
    }

    /** The place the customer picked, or null when they cancelled. */
    public Locality getChosen() {
        return chosen;
    }
}