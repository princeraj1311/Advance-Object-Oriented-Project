package cinesmart.view;


import cinesmart.model.Booking;
import cinesmart.model.Movie;
import cinesmart.model.SeatMap;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.service.BookingService;
import cinesmart.service.CatalogueService;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import cinesmart.util.EventBus;
import cinesmart.util.SeatEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * SeatSelectionPanel is the Stage 4 screen: the auditorium of the show that was picked, the seats
 * a customer taps, and the smart suggestion that fills them in from the seat preference saved on
 * the profile.
 *
 * The panel shows, it does not decide. Every rule lives in {@link SeatMap} (does this seat exist,
 * is it free, may this many seats be taken, which seats fit the preference) and in
 * {@link BookingService} (may this booking be saved), so the same rules hold when Stage 5 pays and
 * when Stage 8 lets an admin watch the hall.
 *
 * The layout is derived from the seat count of the show, so one screen class handles every cinema:
 * a 60 seat hall is eight seats wide, a 400 seat hall is sixteen - see {@link SeatMap#columnsFor}.
 */
public final class SeatSelectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Seat buttons are squares of a fixed size so the map never changes shape while clicking. */
    private static final int SEAT_WIDTH = 32;
    private static final int SEAT_HEIGHT = 27;
    private static final int SEAT_GAP = 4;
    private static final int AISLE_GAP = 20;

    private final MainFrame frame;
    private final CatalogueService catalogue;
    private final BookingService booking;

    /** The show whose map is on screen; null until {@link #openFor} is called. */
    private ShowTime show;
    private SeatMap seatMap;

    private JLabel titleLine;
    private JLabel metaLine;
    private JLabel noteLine;
    private JPanel seatArea;
    private JScrollPane mapScroll;
    private final Map<String, JButton> seatButtons = new LinkedHashMap<>();

    private JLabel seatsLine;
    private JLabel countLine;
    private JLabel amountLine;
    private JLabel freeLine;
    private JLabel suggestLine;
    private JLabel perSeatLine;
    private JComboBox<String> preferenceCombo;
    private JSpinner seatCountSpinner;
    private JButton acceptButton;
    private JButton clearButton;
    private JButton confirmButton;
    private JButton paymentButton;
    private JLabel hintLabel;
    /**
     * True while {@link #openFor} is filling the form, so the combo box listener does not treat the
     * data that was loaded from the profile as a new choice by the customer.
     */
    private boolean loading;

    public SeatSelectionPanel(MainFrame frame, CatalogueService catalogue,
                              BookingService booking) {
        this.frame = frame;
        this.catalogue = (catalogue == null) ? new CatalogueService() : catalogue;
        this.booking = (booking == null) ? new BookingService() : booking;

        setLayout(new BorderLayout());
        setBackground(Theme.WINDOW_BG);
        setBorder(UiUtil.padded(16, 22, 12, 22));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildMap(), BorderLayout.CENTER);
        add(buildSummary(), BorderLayout.EAST);
        add(buildLegend(), BorderLayout.SOUTH);

        openFor(null);

        // Feature 2: live seat updates - repaint when any show's seats change.
        EventBus.subscribe(SeatEvent.class, event -> {
            if (show != null && event.showId != null
                    && event.showId.equalsIgnoreCase(show.getShowId())) {
                // Refresh the map from the store without losing the show context.
                openFor(show);
            }
        });
    }

    /* ------------------------------------------------------------------ *\
     * Showing the map of one show
     * ------------------------------------------------------------------ */

    /**
     * The one entry point of this card: MainFrame calls it when a show time was picked. The map is
     * rebuilt from the store every time, so a seat held in another window shows as taken at once.
     */
    public void openFor(ShowTime showTime) {
        this.show = (showTime == null)
                ? null : booking.findShow(showTime.getShowId());
        this.seatMap = booking.seatMapFor(this.show);

        Movie movie = (show == null) ? null : catalogue.movieById(show.getMovieId());
        Theatre theatre = (show == null) ? null : catalogue.theatreById(show.getTheatreId());

        titleLine.setText(show == null ? "Pick a show time first"
                : (movie == null ? show.getMovieId() : movie.getTitle()) + "   \u00b7   "
                        + show.priceText() + " per seat");
        metaLine.setText(show == null
                ? "Open a film from the home dashboard and tap one of its show times."
                : (theatre == null ? show.getTheatreId() : theatre.getName()) + "  \u00b7  "
                        + show.getScreenName() + "  \u00b7  " + show.dateText() + " at "
                        + show.startTimeText() + "  \u00b7  " + show.getTotalSeats() + " seats");
        noteLine.setText(show == null ? " " : booking.seatDataNote(show)
                + (Session.isAdmin() ? "  Admin view: the map is read only." : ""));

        loading = true;
        preferenceCombo.setSelectedItem(SeatMap.normalise(booking.seatPreference()));
        loading = false;
        seatCountSpinner.setValue(Integer.valueOf(Math.min(4, SeatMap.MAX_SEATS)));
        confirmButton.setEnabled(show != null && show.isBookable());

        rebuildSeatRows();
        repaintSeats();
        frame.setStatusText(show == null ? "Seat selection"
                : "Choosing seats for " + show.getShowId() + " - " + show.label());
    }

    /* ------------------------------------------------------------------ *\
     * Header: what am I looking at
     * ------------------------------------------------------------------ */

    private JComponent buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(true);
        header.setBackground(Theme.SURFACE);
        header.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(12, 16, 12, 16)));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JButton back = UiUtil.outlineButton("\u2039  Back to movies",
                event -> frame.openHome());
        back.setAlignmentY(Component.TOP_ALIGNMENT);
        top.add(back, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.setBorder(UiUtil.padded(0, 12, 0, 0));

        titleLine = new JLabel("Pick a show time first");
        titleLine.setFont(Theme.titleFont(19));
        titleLine.setForeground(Theme.TEXT);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(titleLine);

        metaLine = UiUtil.muted("");
        metaLine.setFont(Theme.bodyFont());
        metaLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(metaLine);

        noteLine = UiUtil.muted(" ");
        noteLine.setFont(Theme.smallFont());
        noteLine.setForeground(Theme.ORANGE);
        noteLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(noteLine);

        top.add(text, BorderLayout.CENTER);
        header.add(top);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }

    /** The four colours, spelled out - customers always ask what the grey squares mean. */
    private JComponent buildLegend() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        legend.setOpaque(false);
        legend.setBorder(UiUtil.padded(12, 2, 0, 2));
        legend.add(swatch("Available", Theme.SEAT_AVAILABLE));
        legend.add(swatch("Your choice", Theme.SEAT_SELECTED));
        legend.add(swatch("Suggested for you", Theme.SEAT_SUGGESTED));
        legend.add(swatch("Already taken", Theme.SEAT_BOOKED));
        legend.add(UiUtil.muted("Rows start at the screen \u00b7 one booking holds up to "
                + SeatMap.MAX_SEATS + " seats"));
        return legend;
    }

    /** A small coloured square next to a word. */
    private JComponent swatch(String caption, Color color) {
        JPanel swatch = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        swatch.setOpaque(false);

        JLabel box = new JLabel(" ");
        box.setOpaque(true);
        box.setBackground(color);
        box.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        box.setPreferredSize(new Dimension(16, 13));
        box.setMaximumSize(box.getPreferredSize());
        swatch.add(box);

        JLabel label = new JLabel(caption);
        label.setFont(Theme.smallFont());
        label.setForeground(Theme.TEXT_MUTED);
        swatch.add(label);
        return swatch;
    }

    /* ------------------------------------------------------------------ *\
     * The auditorium
     * ------------------------------------------------------------------ */

    private JComponent buildMap() {
        seatArea = new JPanel();
        seatArea.setLayout(new BoxLayout(seatArea, BoxLayout.Y_AXIS));
        seatArea.setOpaque(true);
        seatArea.setBackground(Theme.SURFACE);
        seatArea.setBorder(UiUtil.padded(14, 10, 20, 10));

        mapScroll = new JScrollPane(seatArea);
        mapScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        mapScroll.getVerticalScrollBar().setUnitIncrement(18);
        mapScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        mapScroll.getViewport().setBackground(Theme.SURFACE);
        mapScroll.setPreferredSize(new Dimension(560, 380));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(UiUtil.padded(12, 0, 0, 14));
        wrapper.add(mapScroll, BorderLayout.CENTER);
        return wrapper;
    }

    /** Throws the old buttons away and builds one row per row of this auditorium. */
    private void rebuildSeatRows() {
        seatButtons.clear();
        seatArea.removeAll();

        int columns = (seatMap == null) ? 10 : seatMap.getColumns();
        int width = columns * (SEAT_WIDTH + SEAT_GAP) + 2 * AISLE_GAP + 44;
        mapScroll.setPreferredSize(new Dimension(Math.max(470, width + 24), 380));

        seatArea.add(centred(new ScreenBand(width)));
        seatArea.add(Box.createVerticalStrut(18));

        if (seatMap != null) {
            for (int row = 0; row < seatMap.getRows(); row++) {
                seatArea.add(centred(buildRow(row)));
                seatArea.add(Box.createVerticalStrut(3));
            }
        }
        seatArea.revalidate();
        seatArea.repaint();
    }

    /** BoxLayout gives every row the same width, so centring has to be asked for by hand. */
    private static JComponent centred(JComponent component) {
        component.setAlignmentX(Component.CENTER_ALIGNMENT);
        component.setMaximumSize(component.getPreferredSize());
        return component;
    }

    /**
     * One row: its letter, then the seats with a wider gap where the two aisles are. A seat that
     * does not exist (the short last row of a hall of 105 seats) is left out, which is what makes
     * that row read as a centred row instead of a broken one.
     */
    private JPanel buildRow(int row) {
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, SEAT_GAP, 0));
        rowPanel.setOpaque(false);

        JLabel letter = new JLabel(SeatMap.rowLetter(row));
        letter.setFont(Theme.smallBoldFont());
        letter.setForeground(Theme.TEXT_MUTED);
        letter.setPreferredSize(new Dimension(26, SEAT_HEIGHT));
        rowPanel.add(letter);

        for (int col = 0; col < seatMap.getColumns(); col++) {
            if (col == seatMap.seatsBeforeFirstAisle()
                    || col == seatMap.seatsBeforeSecondAisle()) {
                rowPanel.add(Box.createHorizontalStrut(AISLE_GAP));
            }
            String label = SeatMap.label(row, col);
            if (seatMap.exists(label)) {
                SeatButton seat = new SeatButton(label);
                seatButtons.put(label, seat);
                rowPanel.add(seat);
            }
        }
        return rowPanel;
    }

    /** Colours every seat again from the state the model reports, then refreshes the summary. */
    private void repaintSeats() {
        for (Map.Entry<String, JButton> entry : seatButtons.entrySet()) {
            SeatMap.SeatState state = (seatMap == null)
                    ? SeatMap.SeatState.BOOKED : seatMap.stateOf(entry.getKey());
            ((SeatButton) entry.getValue()).applyState(state);
        }
        updateSummary();
    }

    /** Clicking a seat is the only event the map produces; the model decides what it means. */
    private void onSeatClicked(String seat) {
        if (seatMap == null) {
            return;
        }
        String problem = seatMap.toggle(seat);
        repaintSeats();
        if (problem != null) {
            UiUtil.warn(this, problem);
        }
    }

    /** One seat on the map: a button whose colour is decided by SeatMap, never by this class. */
    private final class SeatButton extends JButton {

        private static final long serialVersionUID = 1L;
        private final String seatLabel;

        SeatButton(String seatLabel) {
            super(seatLabel);
            this.seatLabel = seatLabel;
            Dimension size = new Dimension(SEAT_WIDTH, SEAT_HEIGHT);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setFont(Theme.font(Font.BOLD, 10));
            setFocusPainted(false);
            setBorderPainted(false);
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setHorizontalAlignment(SwingConstants.CENTER);
            addActionListener(event -> onSeatClicked(seatLabel));
        }

        /** The one place a seat colour is chosen, and it only translates the model's answer. */
        void applyState(SeatMap.SeatState state) {
            switch (state) {
                case SELECTED:
                    UiUtil.paintBackground(this, Theme.SEAT_SELECTED);
                    setForeground(Color.WHITE);
                    setToolTipText(seatLabel + " - your choice, click to give it back");
                    break;
                case SUGGESTED:
                    UiUtil.paintBackground(this, Theme.SEAT_SUGGESTED);
                    setForeground(Theme.GREEN.darker());
                    setToolTipText(seatLabel + " - suggested for you, click to take it");
                    break;
                case BOOKED:
                    UiUtil.paintBackground(this, Theme.SEAT_BOOKED);
                    setForeground(Theme.SURFACE_SOFT);
                    setToolTipText(seatLabel + " - already taken");
                    break;
                case AVAILABLE:
                default:
                    UiUtil.paintBackground(this, Theme.SEAT_AVAILABLE);
                    setForeground(Theme.TEXT);
                    setToolTipText(seatLabel + " - free" + (seatMap != null
                            && seatMap.isAisleSeat(seatLabel) ? " (aisle seat)" : ""));
                    break;
            }
        }
    }

    /** The screen above the front row: pure decoration, so all it does is paint. */
    private static final class ScreenBand extends JPanel {

        private static final long serialVersionUID = 1L;
        private static final String CAPTION = "S C R E E N   \u00b7   this side is the front";

        ScreenBand(int width) {
            setOpaque(false);
            setPreferredSize(new Dimension(width, 32));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setPaint(Theme.gradient(Theme.SCREEN_BLUE, Theme.accent(), true));
            g2.fillRoundRect(0, 8, getWidth() - 1, 18, 24, 24);
            g2.setFont(Theme.smallBoldFont());
            g2.setColor(new Color(255, 255, 255, 235));
            int textWidth = g2.getFontMetrics().stringWidth(CAPTION);
            g2.drawString(CAPTION, (getWidth() - textWidth) / 2, 21);
            g2.dispose();
        }
    }

    /* ------------------------------------------------------------------ *\
     * The card on the right: what is chosen and what it costs
     * ------------------------------------------------------------------ */

    private JComponent buildSummary() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(14, 16, 12, 16)));
        card.setPreferredSize(new Dimension(338, 420));

        card.add(colour("Your booking", Theme.titleFont(17), Theme.TEXT));
        card.add(UiUtil.gap(8));

        seatsLine = new JLabel("<html><div style='width:290px'>No seat chosen yet</div></html>");
        seatsLine.setFont(Theme.titleFont(13));
        seatsLine.setForeground(Theme.TEXT);
        seatsLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(seatsLine);
        card.add(UiUtil.gap(6));

        countLine = colour("0 seats chosen", Theme.smallFont(), Theme.TEXT_MUTED);
        card.add(countLine);
        freeLine = colour(" ", Theme.smallFont(), Theme.TEXT_MUTED);
        card.add(freeLine);
        card.add(UiUtil.gap(10));
        card.add(fareCard());
        card.add(UiUtil.gap(14));
        card.add(suggestionCard());
        card.add(UiUtil.gap(14));

        clearButton = UiUtil.outlineButton("Clear all seats", event -> clearSeats());
        card.add(clearButton);
        card.add(UiUtil.gap(8));
        confirmButton = UiUtil.primaryButton("Hold these seats", event -> holdSeats());
        card.add(confirmButton);
        card.add(UiUtil.gap(6));
        // Stage 5: once the seats are held this is the on-screen way into the payment screen, so
        // the customer never has to know the Booking menu exists to finish a purchase.
        paymentButton = UiUtil.primaryButton("Proceed to payment", event -> goToPayment());
        paymentButton.setVisible(false);
        card.add(paymentButton);
        hintLabel = colour(hintLine(), Theme.smallFont(), Theme.TEXT_MUTED);
        card.add(hintLabel);
        return card;
    }

    /**
     * The small note under the buttons. It changes once seats are held, because before the hold it
     * explains the hold and afterwards it explains the payment that comes next.
     */
    private String hintLine() {
        return (pendingForThisShow() == null)
                ? "Held seats are blocked for every other customer from now on."
                : "Your seats are held. Pay now to turn them into a ticket.";
    }

    /** A full width line inside the card; BoxLayout asks for the maximum size to be set. */
    private JLabel colour(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Short.MAX_VALUE, label.getFont().getSize() + 10));
        return label;
    }

    /** The two money lines: what one seat costs and what the chosen seats cost together. */
    private JComponent fareCard() {
        JPanel fare = new JPanel(new BorderLayout());
        fare.setOpaque(true);
        fare.setBackground(Theme.SURFACE_SOFT);
        fare.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), UiUtil.padded(8, 10, 8, 10)));
        fare.setAlignmentX(Component.LEFT_ALIGNMENT);
        fare.setPreferredSize(new Dimension(306, 52));
        fare.setMaximumSize(fare.getPreferredSize());

        perSeatLine = UiUtil.muted("Rs 0 per seat");
        amountLine = new JLabel("Rs 0");
        amountLine.setFont(Theme.titleFont(20));
        amountLine.setForeground(Theme.accent());
        amountLine.setHorizontalAlignment(SwingConstants.RIGHT);

        fare.add(perSeatLine, BorderLayout.WEST);
        fare.add(amountLine, BorderLayout.EAST);
        return fare;
    }

    /** The block that turns the seat preference of the profile into seats on the map. */
    private JComponent suggestionCard() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(true);
        box.setBackground(Theme.BLUE_SOFT);
        box.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), UiUtil.padded(10, 10, 10, 10)));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.setPreferredSize(new Dimension(306, 196));
        box.setMaximumSize(box.getPreferredSize());

        box.add(UiUtil.fieldLabel("Smart seat suggestion"));

        preferenceCombo = new JComboBox<>(Session.SEAT_PREFERENCES);
        preferenceCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        preferenceCombo.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        preferenceCombo.setToolTipText("Read from your profile; using a rule saves it there");
        preferenceCombo.addActionListener(event -> {
            if (!loading) {
                suggestSeats();
            }
        });
        box.add(preferenceCombo);
        box.add(UiUtil.gap(6));

        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        countRow.setOpaque(false);
        countRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        countRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        countRow.add(UiUtil.muted("Seats to fill"));
        seatCountSpinner = new JSpinner(new SpinnerNumberModel(4, 1, SeatMap.MAX_SEATS, 1));
        seatCountSpinner.setMaximumSize(new Dimension(64, 26));
        countRow.add(seatCountSpinner);
        box.add(countRow);
        box.add(UiUtil.gap(4));

        box.add(UiUtil.outlineButton("Suggest best seats", event -> suggestSeats()));
        acceptButton = UiUtil.primaryButton("Use these seats", event -> acceptSuggestion());
        acceptButton.setEnabled(false);
        box.add(acceptButton);
        box.add(UiUtil.gap(4));

        suggestLine = new JLabel("<html><div style='width:280px'>Pick a rule and press Suggest,"
                + " or tap the seats yourself.</div></html>");
        suggestLine.setFont(Theme.smallFont());
        suggestLine.setForeground(Theme.TEXT_MUTED);
        suggestLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(suggestLine);
        return box;
    }

    /* ------------------------------------------------------------------ *\
     * What the buttons do
     * ------------------------------------------------------------------ */

    /**
     * Asks the model for the best free seats under the chosen rule, shows them in green and saves
     * the rule on the profile so the next booking starts from the same answer.
     */
    private void suggestSeats() {
        if (seatMap == null) {
            UiUtil.warn(this, "Please pick a show time first.");
            return;
        }
        int wanted = ((Number) seatCountSpinner.getValue()).intValue();
        String preference = String.valueOf(preferenceCombo.getSelectedItem());
        List<String> seats = seatMap.suggest(wanted, preference);
        String saved = booking.saveSeatPreference(preference)
                ? " Rule saved to your profile." : "";
        suggestLine.setText("<html><div style='width:280px'>" + seatMap.explanation() + saved
                + "</div></html>");
        repaintSeats();
        if (seats.isEmpty()) {
            UiUtil.info(this, seatMap.explanation());
        }
    }

    /** Green seats become the customer's choice. */
    private void acceptSuggestion() {
        if (seatMap == null) {
            return;
        }
        int added = seatMap.acceptSuggestion();
        repaintSeats();
        if (added == 0) {
            UiUtil.info(this, "Those suggested seats are not free any more.");
        }
    }

    private void clearSeats() {
        if (seatMap == null) {
            return;
        }
        seatMap.clearSelection();
        suggestLine.setText("<html><div style='width:280px'>Pick a rule and press Suggest,"
                + " or tap the seats yourself.</div></html>");
        repaintSeats();
    }

    /**
     * The one place this screen writes data, and only after the service said yes. Afterwards the
     * map is rebuilt, so the held seats turn grey and the free count is true again.
     */
    private void holdSeats() {
        if (show == null || seatMap == null) {
            UiUtil.warn(this, "Please pick a show time first.");
            return;
        }
        if (Session.isAdmin()) {
            UiUtil.info(this, "An admin account browses the seat map read only.\n\n"
                    + "Seats are held by customers from their own accounts.");
            return;
        }
        String problem = booking.problemWith(show, seatMap);
        if (problem != null) {
            UiUtil.warn(this, problem);
            return;
        }
        BookingService.Receipt receipt = booking.holdSeats(show, seatMap);
        if (receipt == null) {
            UiUtil.error(this, "The seats could not be saved, so nothing was booked. "
                    + "Please try again.");
            openFor(show);
            return;
        }
        // Stage 5: the hold has opened a booking, so the customer is offered the payment screen now
        // instead of being told to look for it. Saying no is fine - the seats stay held and the
        // "Proceed to payment" button in the card leads there on the next click.
        boolean pay = UiUtil.confirm(this, "Seats held for " + Session.firstName() + ":\n\n"
                + receipt.ticketText()
                + "\n\nThe seats are blocked for every other customer now.\nPay now?");
        frame.setStatusText("Seats held for " + receipt.getShowId() + ": "
                + receipt.summaryText());
        // Rebuilding from the store is what turns the held seats grey and makes the counts true.
        openFor(show);
        if (pay) {
            goToPayment();
        }
    }

    /* ------------------------------------------------------------------ *\
     * Stage 5: handing the held booking to the payment screen
     * ------------------------------------------------------------------ */

    /** The unpaid booking of this customer for the show on screen, or null when there is none. */
    private Booking pendingForThisShow() {
        if (show == null) {
            return null;
        }
        for (Booking candidate : booking.bookingsForCurrentUser()) {
            if (candidate.isAwaitingPayment() && candidate.getShowId().equals(show.getShowId())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The one place this screen opens Stage 5. The question after a hold and the button in the
     * booking card both come through here, and so does the screenshot tool, so the journey that is
     * captured is the journey the customer actually walks.
     *
     * @return false when there is nothing to pay for on this show, so no screen is opened
     */
    public boolean goToPayment() {
        Booking pending = pendingForThisShow();
        if (pending == null) {
            UiUtil.info(this, "There are no seats held on this show waiting for payment.\n\n"
                    + "Hold the seats first, and they will appear here ready to pay.");
            return false;
        }
        frame.openPayment(pending.getBookingId());
        return true;
    }

    /* ------------------------------------------------------------------ *\
     * The summary lines, written from the model every time something changes
     * ------------------------------------------------------------------ */

    private void updateSummary() {
        int chosen = (seatMap == null) ? 0 : seatMap.selectedCount();
        seatsLine.setText("<html><div style='width:290px'>" + ((chosen == 0)
                ? "No seat chosen yet - tap the map or use the suggestion."
                : "Seats: " + seatMap.selectedSeatsText()) + "</div></html>");
        countLine.setText(chosen + (chosen == 1 ? " seat chosen" : " seats chosen")
                + "  \u00b7  up to " + SeatMap.MAX_SEATS + " per booking");
        perSeatLine.setText((show == null ? "Rs 0" : show.priceText()) + " per seat");
        amountLine.setText("Rs " + ((seatMap == null) ? 0 : seatMap.selectedAmount()));
        freeLine.setText(show == null ? " "
                : seatMap.bookedCount() + " held by others  \u00b7  " + seatMap.freeCount()
                        + " of " + show.getTotalSeats() + " still free");
        clearButton.setEnabled(chosen > 0);
        acceptButton.setEnabled(seatMap != null && !seatMap.suggestion().isEmpty());
        confirmButton.setEnabled(show != null && show.isBookable() && chosen > 0
                && !Session.isAdmin());

        // Stage 5: one primary action at a time. Before the hold the card offers to hold the seats,
        // after it the same spot offers to pay for them - so the next step is impossible to miss and
        // the card keeps the height it was designed with.
        Booking pending = pendingForThisShow();
        confirmButton.setVisible(pending == null);
        paymentButton.setVisible(pending != null);
        paymentButton.setText("Proceed to payment  \u00b7  Rs " + (pending == null ? 0 : pending.getAmount()));
        if (hintLabel != null) {
            hintLabel.setText(hintLine());
        }
    }
}