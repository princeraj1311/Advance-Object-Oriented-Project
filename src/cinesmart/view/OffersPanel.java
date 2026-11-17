package cinesmart.view;


import cinesmart.model.Offer;
import cinesmart.service.OfferService;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * OffersPanel is the screen behind the <b>Offers</b> button: every running discount, its terms, and
 * a one-click way to copy the code.
 *
 * The screen reads the offers of the selected city through {@link OfferService}, so what is listed
 * here is exactly what the payment screen will accept - including the codes already used on this
 * account, which are shown greyed out with the reason rather than hidden.
 *
 * Copy is a clipboard write rather than a second screen, because the code has to end up in the
 * payment box; putting it on the clipboard makes that one paste.
 */
public final class OffersPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame owner;
    private final OfferService offers;

    private JComboBox<String> scopeCombo;
    private JLabel summaryLabel;
    private JPanel list;

    public OffersPanel(MainFrame owner, OfferService offers) {
        this.owner = owner;
        this.offers = (offers == null) ? new OfferService() : offers;

        setBackground(Theme.WINDOW_BG);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(scrollingList(), BorderLayout.CENTER);
        add(footer(), BorderLayout.SOUTH);
        refresh();
    }

    /* ------------------------------------------------------------------ *\
     * The three parts of the screen
     * ------------------------------------------------------------------ */

    private JComponent header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.WINDOW_BG);
        panel.setBorder(UiUtil.padded(20, 26, 12, 26));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(UiUtil.heading("Offers and coupons"));
        text.add(Box.createVerticalStrut(4));
        text.add(UiUtil.subtitle("Copy a code and paste it on the payment or food screen"));
        panel.add(text, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        scopeCombo = new JComboBox<>(new String[] {"Tickets and food", "Movie tickets only",
            "Food and canteen only"});
        scopeCombo.setSelectedIndex(0);
        scopeCombo.setToolTipText("Which kind of purchase these offers are for");
        scopeCombo.addActionListener(event -> refresh());
        right.add(scopeCombo);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    /** The list is rebuilt often, so it lives inside a scroller that is created only once. */
    private JComponent scrollingList() {
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

    private JComponent footer() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        bar.setOpaque(false);
        bar.setBorder(UiUtil.padded(0, 26, 0, 26));
        bar.add(UiUtil.outlineButton("Change my location", event -> owner.openLocationPicker()));
        bar.add(UiUtil.outlineButton("Back to dashboard", event -> owner.openHome()));
        summaryLabel = UiUtil.muted("");
        summaryLabel.setFont(Theme.titleFont(14));
        bar.add(Box.createHorizontalStrut(20));
        bar.add(summaryLabel);
        return bar;
    }
    /* ------------------------------------------------------------------ *\
     * Filling the list
     * ------------------------------------------------------------------ */

    /** Re-reads the offers of the selected city. Called on open and when the scope combo changes. */
    public void refresh() {
        Offer.Scope scope = selectedScope();
        List<Offer> found = offers.offersFor(Session.selectedCity(), scope, LocalDate.now());

        list.removeAll();
        if (found.isEmpty()) {
            list.add(emptyState());
        }
        for (Offer offer : found) {
            list.add(offerCard(offer));
            list.add(Box.createVerticalStrut(12));
        }
        list.add(UiUtil.verticalGlue());
        list.revalidate();
        list.repaint();
        summaryLabel.setText(found.size() + " offer" + (found.size() == 1 ? "" : "s")
                + " running in " + Session.selectedCity());
    }

    /** Which kind of purchase the combo box is asking about. */
    private Offer.Scope selectedScope() {
        Object chosen = scopeCombo.getSelectedItem();
        String text = (chosen == null) ? "" : chosen.toString();
        if (text.startsWith("Movie tickets")) {
            return Offer.Scope.TICKETS;
        }
        if (text.startsWith("Food")) {
            return Offer.Scope.FOOD;
        }
        return Offer.Scope.ANY;
    }

    /** Shown when a city has no running code at all: honest instead of an empty white card. */
    private JComponent emptyState() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Theme.SURFACE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(20, 20, 20, 20)));
        card.add(UiUtil.heading("No offers running in " + Session.selectedCity()),
                BorderLayout.NORTH);
        card.add(UiUtil.paragraph("Change your location or check back later - a running code is "
                + "listed here as soon as the cinema starts one.",
                Theme.bodyFont(), Theme.TEXT_MUTED, 700), BorderLayout.CENTER);
        return card;
    }

    /* ------------------------------------------------------------------ *\
     * One offer card
     * ------------------------------------------------------------------ */

    /**
     * One offer: the big code and its value on the left, the terms in the middle, Copy on the right.
     *
     * A card this app would refuse is still drawn, greyed out, with the reason printed - "expired"
     * or "already used on this account". A customer who saw a code yesterday must be able to find it
     * and read WHY it stopped working, otherwise the screen looks broken.
     */
    private JComponent offerCard(Offer offer) {
        String user = (Session.currentUser() == null) ? ""
                : Session.currentUser().getUserName();
        boolean usable = !offer.isUsedBy(user);

        JPanel card = new JPanel(new BorderLayout(20, 0));
        card.setBackground(usable ? Theme.SURFACE : Theme.SURFACE_SOFT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(14, 18, 14, 18)));
        card.add(valueColumn(offer, usable), BorderLayout.WEST);
        card.add(termsColumn(offer, user, usable), BorderLayout.CENTER);
        card.add(actionColumn(offer, user, usable), BorderLayout.EAST);
        return card;
    }

    /** The dashed code box and the saving, the way an offer is printed on a real coupon. */
    private JComponent valueColumn(Offer offer, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel code = new JLabel(offer.getCode());
        code.setFont(Theme.titleFont(usable ? 19 : 16));
        code.setForeground(usable ? Theme.RED : Theme.TEXT_MUTED);
        code.setBorder(BorderFactory.createDashedBorder(usable ? Theme.RED : Theme.BORDER,
                1, 4, 4, true));
        column.add(code);
        column.add(Box.createVerticalStrut(8));

        JLabel value = new JLabel(offer.valueText());
        value.setFont(Theme.titleFont(14));
        value.setForeground(usable ? Theme.TEXT : Theme.TEXT_MUTED);
        column.add(value);
        return column;
    }
    /** The title, the description and every condition, wrapped so nothing is cut off. */
    private JComponent termsColumn(Offer offer, String user, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(offer.getTitle());
        title.setFont(Theme.titleFont(14));
        title.setForeground(usable ? Theme.TEXT : Theme.TEXT_MUTED);
        column.add(title);
        column.add(Box.createVerticalStrut(4));

        if (!offer.getDescription().isEmpty()) {
            UiUtil.Paragraph about = UiUtil.paragraph(offer.getDescription(), Theme.smallFont(),
                    Theme.TEXT_MUTED, 420);
            column.add(about);
            column.add(Box.createVerticalStrut(6));
        }
        for (String line : new String[] {offer.minimumText(), offer.validityText(),
            "Works for " + offer.getScope().label().toLowerCase()}) {
            JLabel term = UiUtil.muted(line);
            term.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            column.add(term);
        }
        JLabel use = UiUtil.muted(offer.usageText(user));
        use.setFont(Theme.smallBoldFont());
        use.setForeground(usable ? Theme.GREEN : Theme.ORANGE);
        column.add(Box.createVerticalStrut(4));
        column.add(use);
        return column;
    }

    /** The Copy button, disabled for a code that would be refused anyway. */
    private JComponent actionColumn(Offer offer, String user, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        javax.swing.JButton copy = UiUtil.outlineButton("Copy code", event -> copyCode(offer));
        copy.setEnabled(usable);
        copy.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
        if (!usable) {
            copy.setToolTipText(offer.usageText(user));
        }
        column.add(copy);
        return column;
    }

    /**
     * Puts the code on the system clipboard.
     *
     * The clipboard belongs to the whole desktop, so it is wrapped in its own try/catch: a locked
     * clipboard must never turn into a failed click on an offer card.
     */
    private void copyCode(Offer offer) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(offer.getCode()), null);
            owner.setStatusText("Offer " + offer.getCode() + " copied  \u00b7  "
                    + offer.valueText());
        } catch (Exception e) {
            UiUtil.warn(this, "The code could not be copied automatically.\n\n"
                    + "It is " + offer.getCode() + " - please type it on the payment screen.");
        }
    }
}