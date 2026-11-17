package cinesmart.view;


import cinesmart.model.Booking;
import cinesmart.model.Offer;
import cinesmart.service.BookingService;
import cinesmart.service.OfferService;
import cinesmart.service.PaymentService;
import cinesmart.service.PaymentService.PaymentDetails;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * PaymentPanel is the Stage 5 screen the held seats lead to: it shows what is being paid for on the
 * left and how it is being paid on the right, and one click turns the booking into the ticket.
 *
 * The screen owns no rule. It collects what was typed into a
 * {@link PaymentService.PaymentDetails}, asks {@link PaymentService#pay} and shows the answer:
 * every problem in one dialog when the payment is refused, the printed ticket when it worked.
 *
 * The three payment methods share this one form. Choosing a method hides the fields of the other
 * two, so a customer never wonders why the PIN box is greyed out - and the service only checks the
 * fields of the chosen method.
 */
public final class PaymentPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame owner;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final OfferService offerService;

    /** The booking being paid for. null means "nothing is waiting for payment". */
    private Booking booking;

    private JLabel amountLabel;
    private JLabel ticketArea;
    private JLabel resultHint;
    private JComboBox<String> methodCombo;
    private JTextField cardNumberField;
    private JTextField cardHolderField;
    private JTextField cvvField;
    private JTextField expiryField;
    private JTextField upiField;
    private JTextField pinField;
    private JPanel cardBox;
    private JPanel upiBox;
    private JPanel pinBox;
    private JPanel giftCardBox;
    private JTextField giftCardField;
    /** The offer code box and the line that says what the code is worth right now. */
    private JTextField offerField;
    private JLabel offerHint;
    /** Rupees the applied offer takes off; 0 when no code is applied. Recalculated, never trusted. */
    private int discount;
    private JButton payButton;
    /** Feature 1: countdown showing how long the held seats remain reserved. */
    private JLabel countdownLabel;
    private javax.swing.Timer holdCountdownTimer;
    private long holdExpiresAtMillis;

    public PaymentPanel(MainFrame owner, BookingService bookingService,
                        PaymentService paymentService) {
        this.owner = owner;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.offerService = new OfferService();
        setBackground(Theme.WINDOW_BG);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(body(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        showBooking(bookingService.pendingBooking());
    }

    /* ------------------------------------------------------------------ *\
     * Building blocks
     * ------------------------------------------------------------------ */

    private JPanel header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.WINDOW_BG);
        panel.setBorder(UiUtil.padded(20, 26, 12, 26));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel title = UiUtil.heading("Payment and confirmation");
        JLabel subtitle = UiUtil.subtitle("Pay for the seats you are holding and get your ticket");
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        text.add(title);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitle);
        panel.add(text, BorderLayout.WEST);

        amountLabel = UiUtil.muted("");
        amountLabel.setFont(Theme.titleFont(20));
        amountLabel.setForeground(Theme.accent());

        // Feature 1: visible countdown timer for the seat hold.
        countdownLabel = UiUtil.muted(" ");
        countdownLabel.setFont(Theme.titleFont(14));
        countdownLabel.setForeground(Theme.ORANGE);
        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.add(amountLabel);
        east.add(countdownLabel);
        panel.add(east, BorderLayout.EAST);
        return panel;
    }

    /** Two cards side by side: what is being bought, and how it is paid for. */
    private JComponent body() {
        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        columns.setBorder(UiUtil.padded(0, 26, 12, 26));

        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = 0;
        left.weightx = 0.45;
        left.fill = GridBagConstraints.BOTH;
        left.insets = new Insets(0, 0, 0, 12);
        columns.add(summaryCard(), left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = 0;
        right.weightx = 0.55;
        right.fill = GridBagConstraints.BOTH;
        columns.add(formCard(), right);

        JScrollPane scroller = new JScrollPane(columns, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.setOpaque(false);
        scroller.getViewport().setOpaque(false);
        return scroller;
    }
    /** The left card: the ticket that is being bought, line by line. */
    /** Navigation strip: payment is always reachable, and so are the seats behind it. */
    private JPanel footer() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        bar.setOpaque(false);
        bar.setBorder(UiUtil.padded(0, 26, 0, 26));
        bar.add(UiUtil.primaryButton("My bookings", event -> owner.openBookings()));
        bar.add(UiUtil.outlineButton("Back to seats", event -> owner.openSeatSelection(null)));
        bar.add(UiUtil.outlineButton("Back to dashboard", event -> owner.openHome()));
        return bar;
    }

    private JPanel summaryCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(), UiUtil.padded(18, 20, 18, 20)));
        card.add(UiUtil.heading("Your booking"), BorderLayout.NORTH);

        JPanel ticket = new JPanel();
        ticket.setOpaque(false);
        ticket.setLayout(new BoxLayout(ticket, BoxLayout.Y_AXIS));
        ticket.add(Box.createVerticalStrut(12));
        ticketArea = new JLabel();
        ticketArea.setFont(Theme.bodyFont());
        ticketArea.setForeground(Theme.TEXT);
        ticketArea.setAlignmentX(LEFT_ALIGNMENT);
        ticket.add(ticketArea);
        card.add(ticket, BorderLayout.CENTER);

        UiUtil.Paragraph note = UiUtil.paragraph(
                "The seats are held for you while the booking is unpaid. "
                        + "They stay blocked for other customers until you pay or cancel the booking.",
                Theme.smallFont(), Theme.TEXT_MUTED, 380);
        card.add(note, BorderLayout.SOUTH);
        return card;
    }

    /** The right card: method, the fields of that method, and the Pay button. */
    private JPanel formCard() {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(), UiUtil.padded(18, 20, 18, 20)));
        card.add(UiUtil.heading("Pay now"), BorderLayout.NORTH);

        FormPanel form = new FormPanel();
        methodCombo = new JComboBox<>(PaymentService.METHODS.toArray(new String[0]));
        methodCombo.setPreferredSize(new Dimension(240, 34));
        form.addRow("Payment method", methodCombo);

        cardNumberField = UiUtil.textField(20);
        cardHolderField = UiUtil.textField(18);
        cvvField = UiUtil.textField(4);
        expiryField = UiUtil.textField(7);
        upiField = UiUtil.textField(22);
        pinField = UiUtil.textField(8);
        giftCardField = UiUtil.textField(18);

        cardBox = box("Card details", fields(
                new String[] {"Card number", "Name on card", "CVV", "Expiry (MM/YY)"},
                new JTextField[] {cardNumberField, cardHolderField, cvvField, expiryField}));
        upiBox = box("UPI",
                fields(new String[] {"UPI id (name@bank)"}, new JTextField[] {upiField}));
        pinBox = box("Net banking",
                fields(new String[] {"4 digit net banking PIN"}, new JTextField[] {pinField}));
        giftCardBox = box("Gift card",
                fields(new String[] {"Gift card code"}, new JTextField[] {giftCardField}));
        giftCardField.setToolTipText("For example CSGIFT-4821-KQ - it must cover the whole amount");
        form.addWide(cardBox, false);
        form.addWide(upiBox, false);
        form.addWide(pinBox, false);
        form.addWide(giftCardBox, false);
        form.addGap(4);
        form.addWide(offerRow(), false);
        form.addGap(4);

        resultHint = UiUtil.hint(" ");
        form.addWide(resultHint, false);

        card.add(form, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        payButton = UiUtil.primaryButton("Pay and get ticket", event -> attemptPayment());
        actions.add(payButton);
        card.add(actions, BorderLayout.SOUTH);

        methodCombo.addActionListener(event -> updateMethodFields());
        updateMethodFields(); // so the form opens with only the fields of the chosen method
        return card;
    }

    /**
     * The offer box: type a code, press Apply, and the amount above drops by what the code is worth.
 *
     * Apply asks {@link OfferService#check} and NOT redeem, so typing a code and then changing it
 * never "uses" the offer up - the code is only recorded when the payment actually goes through. That
 * is why this is a separate button rather than something that fires on every keystroke.
 */
    private JPanel offerRow() {
        JPanel panel = new JPanel(new BorderLayout(8, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);

        JPanel line = new JPanel(new java.awt.BorderLayout(8, 0));
        line.setOpaque(false);
        line.add(UiUtil.fieldLabel("Offer code"), java.awt.BorderLayout.WEST);

        JPanel right = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        offerField = UiUtil.textField(12);
        offerField.setToolTipText("Copy a code from the Offers screen, then apply it here");
        offerField.addActionListener(event -> applyOffer());
        right.add(offerField);
        right.add(UiUtil.outlineButton("Apply", event -> applyOffer()));
        JButton seeAll = UiUtil.outlineButton("See all offers", event -> owner.openOffers());
        right.add(seeAll);
        line.add(right, java.awt.BorderLayout.CENTER);
        panel.add(line, java.awt.BorderLayout.NORTH);

        offerHint = UiUtil.hint(" ");
        panel.add(offerHint, java.awt.BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Checks the typed code and shows what it is worth, without spending it.
     *
     * The amount shown above is recalculated from the booking every time, so a code can never leave
     * a stale discount on screen - the number the customer reads and the number that will be charged
     * come from the same place.
     */
    private void applyOffer() {
        if (booking == null || !booking.isAwaitingPayment()) {
            UiUtil.setHint(offerHint, "There is nothing to discount right now.", false);
            return;
        }
        OfferService.Result result = offerService.check(offerField.getText(),
                Session.selectedCity(), Offer.Scope.TICKETS, booking.getAmount());
        if (result.isAllowed()) {
            discount = result.getDiscount();
            UiUtil.setHint(offerHint, result.getOffer().getTitle() + " \u00b7 you save Rs "
                    + discount + " on this booking.", true);
        } else {
            discount = 0;
            UiUtil.setHint(offerHint, String.join("  ", result.getProblems()), false);
        }
        updateSummary();
    }

    /** One titled group of fields, so the card shows three small sections, not nine loose boxes. */
    private JPanel box(String title, JComponent... fields) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(UiUtil.titled(title));
        panel.setAlignmentX(LEFT_ALIGNMENT);
        for (JComponent field : fields) {
            panel.add(field);
        }
        return panel;
    }

    /**
     * "Caption on the left, field on the right" rows inside one method box. The caption column is a
     * fixed width so the boxes line up with each other, exactly like the bigger forms do.
     */
    private JPanel fields(String[] captions, JTextField[] inputs) {
        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);
        GridBagConstraints caption = new GridBagConstraints();
        caption.gridx = 0;
        caption.anchor = GridBagConstraints.WEST;
        caption.insets = new Insets(4, 0, 4, 10);
        GridBagConstraints input = new GridBagConstraints();
        input.gridx = 1;
        input.weightx = 1.0;
        input.fill = GridBagConstraints.HORIZONTAL;
        input.insets = new Insets(4, 0, 4, 0);
        for (int i = 0; i < captions.length; i++) {
            caption.gridy = i;
            input.gridy = i;
            rows.add(UiUtil.hint(captions[i]), caption);
            rows.add(inputs[i], input);
        }
        return rows;
    }
    /* ------------------------------------------------------------------ *\
     * Showing a booking
     * ------------------------------------------------------------------ */

    /**
     * Opens the screen for one booking. Called with the booking the seat screen just created, with
     * the newest unpaid booking when the menu item is used, and with null after everything is paid
     * - in which case the card explains what to do instead of showing a dead form.
     */
    public void showBooking(Booking toPay) {
        this.booking = toPay;
        // A new booking must never inherit the discount the previous one had applied.
        discount = 0;
        if (offerField != null) {
            offerField.setText("");
            UiUtil.setHint(offerHint, " ", true);
        }
        updateSummary();
        startCountdown(toPay);
    }

    /** Feature 1: starts the visible countdown timer for the held seats. */
    private void startCountdown(Booking toPay) {
        if (holdCountdownTimer != null) {
            holdCountdownTimer.stop();
        }
        if (toPay == null || !toPay.isAwaitingPayment()) {
            countdownLabel.setText(" ");
            return;
        }
        // 5 minutes from now (matches AppConfig.SEAT_HOLD_MINUTES).
        holdExpiresAtMillis = System.currentTimeMillis()
                + (long) cinesmart.util.AppConfig.SEAT_HOLD_MINUTES * 60 * 1000;
        holdCountdownTimer = new javax.swing.Timer(1000, event -> {
            long remaining = holdExpiresAtMillis - System.currentTimeMillis();
            if (remaining <= 0) {
                countdownLabel.setText("Seats released");
                holdCountdownTimer.stop();
                // Release the holds and refresh.
                if (booking != null) {
                    BookingService bs = bookingService;
                    cinesmart.util.DataStore.get().releaseHoldsForUser(
                            booking.getUserName());
                    booking = null;
                    updateSummary();
                }
                UiUtil.warn(this, "Your held seats have been released. "
                        + "Please choose seats again.");
            } else {
                long min = remaining / 60000;
                long sec = (remaining % 60000) / 1000;
                countdownLabel.setText(String.format("Hold expires in %02d:%02d", min, sec));
            }
        });
        holdCountdownTimer.start();
        countdownLabel.setText(String.format("Hold expires in %02d:%02d",
                cinesmart.util.AppConfig.SEAT_HOLD_MINUTES, 0));
    }

    /**
     * The amount in the header: "Rs 800", or "Rs 750  (Rs 50 offer)" once a code is applied.
     *
     * Both numbers are printed, because a customer must be able to check the arithmetic of a
     * discount against the ticket price rather than trust it.
     */
    private String amountText() {
        if (discount <= 0) {
            return "Rs " + booking.getAmount();
        }
        return "Rs " + (booking.getAmount() - discount) + "  (Rs " + discount + " offer)";
    }

    private void updateSummary() {
        boolean payable = booking != null && booking.isAwaitingPayment();
        payButton.setEnabled(payable);
        methodCombo.setEnabled(payable);

        if (booking == null) {
            ticketArea.setText("<html><b>Nothing is waiting for payment</b><br><br>"
                    + "Hold some seats on the seat map first - the booking appears here"
                    + " as soon as the seats are yours.</html>");
            amountLabel.setText("");
            UiUtil.setHint(resultHint, " ", true);
            return;
        }

        ticketArea.setText(html(bookingService.ticketLines(booking)));
        amountLabel.setText(amountText());
        if (payable) {
            UiUtil.setHint(resultHint, "Seats held - pay before the show to keep them.", true);
        } else if (booking.isPaid()) {
            UiUtil.setHint(resultHint, "This booking is already paid (" + booking.getPaymentRef()
                    + "). Open My bookings for the ticket.", true);
        } else {
            UiUtil.setHint(resultHint, "This booking was cancelled - nothing is due.", false);
        }
    }

    /** Shows only the fields of the chosen method, so no dead box is ever on screen. */
    private void updateMethodFields() {
        String method = methodCombo.getSelectedItem() == null ? ""
                : methodCombo.getSelectedItem().toString();
        cardBox.setVisible(PaymentService.METHOD_CARD.equals(method));
        upiBox.setVisible(PaymentService.METHOD_UPI.equals(method));
        pinBox.setVisible(PaymentService.METHOD_NET_BANKING.equals(method));
        giftCardBox.setVisible(PaymentService.METHOD_GIFT_CARD.equals(method));
        cardBox.revalidate();
        upiBox.revalidate();
        pinBox.revalidate();
        giftCardBox.revalidate();
    }

    /* ------------------------------------------------------------------ *\
     * Paying
     * ------------------------------------------------------------------ */

    /**
     * Reads the form into one {@link PaymentDetails}. Only the fields of the chosen method are
     * filled in, so a number typed earlier can never leak into a UPI payment.
     */
    private PaymentDetails readForm() {
        return new PaymentDetails()
                .method(methodCombo.getSelectedItem() == null ? "" : methodCombo.getSelectedItem().toString())
                .cardNumber(cardNumberField.getText())
                .cardHolder(UiUtil.textOf(cardHolderField))
                .cvv(cvvField.getText())
                .expiry(UiUtil.textOf(expiryField))
                .upiId(UiUtil.textOf(upiField))
                .pin(pinField.getText())
                .giftCardCode(UiUtil.textOf(giftCardField));
    }

    /** Nothing but "read the form, ask the service, show the answer". */
    private void attemptPayment() {
        // The code is redeemed HERE, at the moment the money is actually taken, and never when it was
        // typed: a customer who applies a code and then walks away has not used it up.
        //
        // An empty box is not an error - it just means "no offer", so the payment goes ahead at the
        // full amount. Only a box with something WRONG in it stops the payment.
        int discountToUse = 0;
        OfferService.Result applied = null;
        if (!offerField.getText().trim().isEmpty()) {
            applied = offerService.redeem(offerField.getText(),
                    Session.selectedCity(), Offer.Scope.TICKETS, booking.getAmount());
            if (!applied.isAllowed()) {
                discount = 0;
                UiUtil.setHint(resultHint, "The payment was not completed.", false);
                UiUtil.showProblems(this, "The payment could not go through", applied.getProblems());
                updateSummary();
                return;
            }
            discountToUse = applied.getDiscount();
        }

        PaymentService.PaymentResult result = paymentService.pay(booking, readForm(),
                discountToUse);
        if (!result.isSuccess()) {
            discount = 0;
            UiUtil.setHint(resultHint, "The payment was not completed.", false);
            UiUtil.showProblems(this, "The payment could not go through", result.getProblems());
            updateSummary();
            return;
        }

        if (holdCountdownTimer != null) {
            holdCountdownTimer.stop();
        }
        countdownLabel.setText(" ");

        booking = result.getBooking();
        clearFields();
        updateSummary();
        String paidWith = (applied != null && applied.isAllowed())
                ? "\nOffer " + applied.getOffer().getCode() + " saved Rs "
                        + applied.getDiscount()
                : "";
        UiUtil.info(this, "Payment successful\n\n"
                + String.join("\n", bookingService.ticketLines(booking)) + "\n\n"
                + "Transaction " + result.getTransactionId() + paidWith
                + "\nYour ticket is also in My bookings.");
        owner.openBookings();
    }

    /** Card numbers and PINs are never kept in the form after a payment. */
    private void clearFields() {
        cardNumberField.setText("");
        cardHolderField.setText("");
        cvvField.setText("");
        expiryField.setText("");
        upiField.setText("");
        pinField.setText("");
    }

    /** Plain lines to the little HTML block the summary label shows. */
    private String html(List<String> lines) {
        StringBuilder text = new StringBuilder("<html>");
        for (String line : lines) {
            text.append(escape(line)).append("<br>");
        }
        return text.append("</html>").toString();
    }

    /** Film titles may contain & or <, which would break the HTML of the label. */
    private String escape(String line) {
        return line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}