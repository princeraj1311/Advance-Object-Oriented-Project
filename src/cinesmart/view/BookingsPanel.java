package cinesmart.view;

import cinesmart.model.Booking;
import cinesmart.service.BookingService;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
/**
 * BookingsPanel is the Stage 5 "My bookings" screen with the Stage 6 cancel button on it.
 *
 * Every booking of the signed in customer becomes one card: the ticket lines on the left, what was
 * paid and a coloured state on the right. The card is rebuilt from the service every time the
 * screen opens, so a booking paid in the payment screen or cancelled here is never shown twice or
 * shown stale.
 *
 * The cancel button is only enabled when {@link BookingService#cancelProblemWith} has no answer,
 * and the reason is printed under it when it does - the screen never hides a rule, it explains it.
 */
public final class BookingsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame owner;
    private final BookingService bookingService;
    private JPanel list;
    private JLabel summaryLabel;

    public BookingsPanel(MainFrame owner, BookingService bookingService) {
        this.owner = owner;
        this.bookingService = bookingService;
        setBackground(Theme.WINDOW_BG);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(scrollingList(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        refresh();
    }

    /** Re-reads every booking of the customer and rebuilds the list. */
    public void refresh() {
        list.removeAll();
        java.util.List<Booking> bookings = bookingService.bookingsForCurrentUser();
        if (bookings.isEmpty()) {
            list.add(emptyState());
        }
        for (Booking booking : bookings) {
            list.add(bookingCard(booking));
            list.add(Box.createVerticalStrut(14));
        }
        list.add(UiUtil.verticalGlue());
        summaryLabel.setText(summaryText(bookings));
        list.revalidate();
        list.repaint();
    }
    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.WINDOW_BG);
        panel.setBorder(UiUtil.padded(20, 26, 12, 26));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(UiUtil.heading("My bookings"));
        text.add(Box.createVerticalStrut(4));
        text.add(UiUtil.subtitle("Every ticket you bought, with the seats you can still cancel"));
        panel.add(text, BorderLayout.WEST);

        summaryLabel = UiUtil.muted("");
        summaryLabel.setFont(Theme.titleFont(15));
        panel.add(summaryLabel, BorderLayout.EAST);
        return panel;
    }

    /** The list is rebuilt often, so it lives inside a scroller that is created only once. */
    private JScrollPane scrollingList() {
        list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(UiUtil.padded(0, 26, 12, 26));

        JScrollPane scroller = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        scroller.getVerticalScrollBar().setUnitIncrement(16);
        return scroller;
    }

    private JPanel footer() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        bar.setOpaque(false);
        bar.setBorder(UiUtil.padded(0, 26, 0, 26));
        bar.add(UiUtil.primaryButton("Book more seats", event -> owner.openSeatSelection(null)));
        bar.add(UiUtil.outlineButton("Back to dashboard", event -> owner.openHome()));
        return bar;
    }

    /** Shown when the customer never booked anything: honest instead of an empty white card. */
    private JPanel emptyState() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Theme.SURFACE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(20, 20, 20, 20)));
        card.add(UiUtil.heading("No bookings yet"), BorderLayout.NORTH);
        card.add(UiUtil.paragraph("Pick a film and a show time, choose your seats on the map and "
                + "pay for them - the ticket then appears here.",
                Theme.bodyFont(), Theme.TEXT_MUTED, 700), BorderLayout.CENTER);
        return card;
    }
    /** One booking as one card: state on the left, the ticket in the middle, the money on the right. */
    private JPanel bookingCard(Booking booking) {
        JPanel card = new JPanel(new BorderLayout(20, 0));
        card.setBackground(Theme.SURFACE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(16, 20, 16, 20)));
        card.add(stateColumn(booking), BorderLayout.WEST);
        card.add(ticketColumn(booking), BorderLayout.CENTER);
        card.add(actionColumn(booking), BorderLayout.EAST);
        return card;
    }

    /** The coloured state chip plus the ids an auditor would ask for. */
    private JPanel stateColumn(Booking booking) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel badge = new JLabel(booking.getStatus().label());
        badge.setOpaque(true);
        badge.setHorizontalAlignment(JLabel.CENTER);
        badge.setFont(Theme.smallBoldFont());
        badge.setForeground(Color.WHITE);
        badge.setBackground(badgeColor(booking));
        badge.setBorder(UiUtil.padded(5, 10, 5, 10));
        badge.setAlignmentX(LEFT_ALIGNMENT);
        column.add(badge);
        column.add(Box.createVerticalStrut(8));
        column.add(UiUtil.muted(booking.getBookingId()));
        column.add(Box.createVerticalStrut(2));
        column.add(UiUtil.muted("Booked " + booking.getBookedAt()));
        return column;
    }

    /** The ticket lines: the film, the cinema, the time and the seats. */
    private JPanel ticketColumn(Booking booking) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        java.util.List<String> lines = bookingService.ticketLines(booking);
        // The last line repeats the booking id and the state, which the chip on the left already
        // shows - on the card it would only be printed twice.
        for (int i = 0; i < lines.size() - 1; i++) {
            JLabel text = new JLabel(lines.get(i));
            text.setFont(Theme.bodyFont());
            text.setForeground(Theme.TEXT);
            text.setAlignmentX(LEFT_ALIGNMENT);
            column.add(text);
        }
        if (booking.isCancelled() && !booking.getCancelledAt().isEmpty()) {
            JLabel refund = UiUtil.muted("Cancelled on " + booking.getCancelledAt()
                    + "  \u00b7  Rs " + booking.getAmount() + " refunded");
            refund.setForeground(Theme.RED);
            refund.setAlignmentX(LEFT_ALIGNMENT);
            column.add(Box.createVerticalStrut(6));
            column.add(refund);
        }
        return column;
    }
    /** What was paid, and the Stage 6 button that may take it back. */
    private JPanel actionColumn(Booking booking) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel amount = new JLabel("Rs " + booking.getAmount());
        amount.setFont(Theme.titleFont(17));
        amount.setForeground(Theme.TEXT);
        amount.setAlignmentX(RIGHT_ALIGNMENT);
        column.add(amount);

        JLabel how = UiUtil.muted(booking.isPaid() ? booking.getMethod()
                : (booking.isCancelled() ? "Refunded" : "Not paid yet"));
        how.setAlignmentX(RIGHT_ALIGNMENT);
        column.add(how);
        column.add(Box.createVerticalStrut(10));

        // The very rule the button would refuse later decides whether it is usable at all.
        String problem = bookingService.cancelProblemWith(booking);
        JButton cancel = UiUtil.dangerButton("Cancel booking", event -> attemptCancel(booking));
        cancel.setAlignmentX(RIGHT_ALIGNMENT);
        cancel.setEnabled(problem == null);
        cancel.setToolTipText(problem == null
                ? "Give the seats back and refund the amount" : problem);
        column.add(cancel);
        if (problem != null) {
            UiUtil.Paragraph why = UiUtil.paragraph(problem, Theme.smallFont(), Theme.TEXT_MUTED, 215);
            why.setAlignmentX(RIGHT_ALIGNMENT);
            column.add(Box.createVerticalStrut(6));
            column.add(why);
        }
        return column;
    }

    /** Green when paid, orange while unpaid, grey once cancelled - read in one glance. */
    private Color badgeColor(Booking booking) {
        if (booking.isPaid()) {
            return Theme.GREEN;
        }
        return booking.isCancelled() ? Theme.TEXT_MUTED : Theme.ORANGE;
    }

    /* ------------------------------------------------------------------ *\
     * Stage 6: cancelling from this screen
     * ------------------------------------------------------------------ */

    /**
     * Ask, then cancel. The rule is checked before the question is asked, so the confirmation can
     * never promise something the service would refuse.
     */
    private void attemptCancel(Booking booking) {
        String problem = bookingService.cancelProblemWith(booking);
        if (problem != null) {
            UiUtil.warn(this, problem);
            return;
        }
        String question = "Cancel booking " + booking.getBookingId() + "?\n\n"
                + String.join("\n", bookingService.ticketLines(booking))
                + "\n\nThe seats go back into the map and Rs " + booking.getAmount()
                + " is refunded. This cannot be undone.";
        if (!UiUtil.confirm(this, question)) {
            return;
        }
        if (!bookingService.cancelBooking(booking)) {
            UiUtil.error(this, "The cancellation could not be saved. Please try again.");
            refresh();
            return;
        }
        refresh();
        owner.setStatusText("Booking " + booking.getBookingId() + " cancelled  \u00b7  Rs "
                + booking.getAmount() + " refunded");
        UiUtil.info(this, "Booking " + booking.getBookingId() + " was cancelled.\n\n"
                + "The seats are free again and Rs " + booking.getAmount() + " was refunded.");
    }

    /** "3 bookings  \u00b7  Rs 1,450 paid  \u00b7  1 cancelled" - the one line above the list. */
    private String summaryText(java.util.List<Booking> bookings) {
        if (bookings.isEmpty()) {
            return "";
        }
        int paid = 0;
        int cancelled = 0;
        int money = 0;
        for (Booking booking : bookings) {
            if (booking.isPaid()) {
                paid++;
                money += booking.getAmount();
            } else if (booking.isCancelled()) {
                cancelled++;
            }
        }
        StringBuilder text = new StringBuilder(bookings.size() + " booking");
        if (bookings.size() > 1) {
            text.append('s');
        }
        text.append("  \u00b7  Rs ").append(money).append(" paid");
        if (cancelled > 0) {
            text.append("  \u00b7  ").append(cancelled).append(" cancelled");
        }
        return text.toString();
    }
}