package cinesmart.view;


import cinesmart.model.GiftCard;
import cinesmart.service.GiftCardService;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * GiftCardsPanel is the screen behind the <b>Gift Cards</b> button: the wallet of the signed in
 * customer, and the counter where a new card is bought.
 *
 * It is deliberately a wallet rather than a checkout: buying a card here shows the generated code,
 * and spending it happens on the payment screen where there is a real amount to pay. That keeps one
 * place responsible for taking money off a card, which is {@link GiftCardService}.
 *
 * The screen never works out a balance itself. Every number printed comes from the card read back
 * out of {@code data/giftcards.txt}, so a card charged on the payment screen shows the new balance
 * here on the next visit.
 */
public final class GiftCardsPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame owner;
    private final GiftCardService cards;

    private JLabel totalLabel;
    private JPanel list;
    private JComboBoxLike denominations;

    /** Tiny holder so the screen can show the denominations without importing Swing types. */
    private static final class JComboBoxLike extends javax.swing.JComboBox<String> {
        private static final long serialVersionUID = 1L;

        JComboBoxLike(String[] items) {
            super(items);
        }
    }

    public GiftCardsPanel(MainFrame owner, GiftCardService cards) {
        this.owner = owner;
        this.cards = (cards == null) ? new GiftCardService() : cards;

        setBackground(Theme.WINDOW_BG);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        add(scrollingList(), BorderLayout.CENTER);
        add(buyBar(), BorderLayout.SOUTH);
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
        text.add(UiUtil.heading("Gift cards"));
        text.add(Box.createVerticalStrut(4));
        text.add(UiUtil.subtitle("Prepaid cards for tickets and food, valid in every hall"));
        panel.add(text, BorderLayout.WEST);

        totalLabel = UiUtil.muted("");
        totalLabel.setFont(Theme.titleFont(15));
        panel.add(totalLabel, BorderLayout.EAST);
        return panel;
    }

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

    /** The counter: pick a denomination and buy. */
    private JComponent buyBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 14));
        bar.setOpaque(false);
        bar.setBorder(UiUtil.padded(0, 26, 0, 26));
        bar.add(UiUtil.fieldLabel("Buy a card for"));

        String[] values = new String[GiftCardService.DENOMINATIONS.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = "Rs " + GiftCardService.DENOMINATIONS.get(i);
        }
        denominations = new JComboBoxLike(values);
        denominations.setToolTipText("How much money to load the card with");
        bar.add(denominations);

        bar.add(UiUtil.primaryButton("Buy gift card", event -> buy()));
        bar.add(Box.createHorizontalStrut(10));
        bar.add(UiUtil.outlineButton("Back to dashboard", event -> owner.openHome()));
        return bar;
    }

    /* ------------------------------------------------------------------ *\
     * Filling the wallet, and buying
     * ------------------------------------------------------------------ */

    /** Re-reads the cards of the signed in customer. Called on open and after a purchase. */
    public void refresh() {
        List<GiftCard> mine = cards.cardsOfCurrentUser();
        list.removeAll();
        if (mine.isEmpty()) {
            list.add(emptyState());
        }
        for (GiftCard card : mine) {
            list.add(cardRow(card));
            list.add(Box.createVerticalStrut(12));
        }
        list.add(UiUtil.verticalGlue());
        list.revalidate();
        list.repaint();
        totalLabel.setText(mine.size() + " card" + (mine.size() == 1 ? "" : "s")
                + "  \u00b7  Rs " + cards.totalBalance() + " in total");
    }

    /** Shown when nothing was ever bought: an invitation, not a blank page. */
    private JComponent emptyState() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setBackground(Theme.SURFACE);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        card.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(20, 20, 20, 20)));
        card.add(UiUtil.heading("No gift cards yet"), BorderLayout.NORTH);
        card.add(UiUtil.paragraph("Buy a card with the button below and it appears here with its "
                + "code and balance. Pay with it on the payment screen or at the food counter, "
                + "and the balance is reduced straight away.",
                Theme.bodyFont(), Theme.TEXT_MUTED, 760), BorderLayout.CENTER);
        return card;
    }

    /** One card: the code, what is left on it, when it expires, and the buttons for it. */
    private JComponent cardRow(GiftCard card) {
        boolean usable = card.isUsableOn(java.time.LocalDate.now());

        JPanel row = new JPanel(new BorderLayout(20, 0));
        row.setBackground(usable ? Theme.SURFACE : Theme.SURFACE_SOFT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        row.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(14, 18, 14, 18)));
        row.add(codeColumn(card, usable), BorderLayout.WEST);
        row.add(detailColumn(card, usable), BorderLayout.CENTER);
        row.add(actionColumn(card, usable), BorderLayout.EAST);
        return row;
    }

    private JComponent codeColumn(GiftCard card, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        JLabel code = new JLabel(card.getCode());
        code.setFont(Theme.titleFont(17));
        code.setForeground(usable ? Theme.TEXT : Theme.TEXT_MUTED);
        column.add(code);
        column.add(Box.createVerticalStrut(6));

        JLabel balance = new JLabel("Rs " + card.getBalance());
        balance.setFont(Theme.titleFont(21));
        balance.setForeground(usable ? Theme.GREEN : Theme.TEXT_MUTED);
        column.add(balance);

        column.add(UiUtil.muted("of Rs " + card.getDenomination() + "  \u00b7  "
                + card.getStatus().label()));
        return column;
    }

    /** Issued, expires, spent so far, and the last movement under it. */
    private JComponent detailColumn(GiftCard card, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        for (String line : new String[] {"Issued " + card.getIssuedAt(), card.expiryText(),
            "Spent so far: Rs " + card.spent()}) {
            JLabel text = UiUtil.muted(line);
            text.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            column.add(text);
        }
        List<String> history = card.historyLines();
        if (!history.isEmpty()) {
            column.add(Box.createVerticalStrut(6));
            JLabel recent = UiUtil.muted("Recent use: " + history.get(history.size() - 1));
            recent.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            recent.setForeground(usable ? Theme.TEXT : Theme.TEXT_MUTED);
            column.add(recent);
        }
        return column;
    }

    /** Copy the code, or look at the whole movement list. */
    private JComponent actionColumn(GiftCard card, boolean usable) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        javax.swing.JButton copy = UiUtil.outlineButton("Copy code",
                event -> copyCode(card.getCode()));
        copy.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
        column.add(copy);

        javax.swing.JButton history = UiUtil.outlineButton("History",
                event -> UiUtil.info(this, "Gift card " + card.getCode() + "\n\n"
                        + String.join("\n", cards.cardLines(card))));
        history.setAlignmentX(java.awt.Component.RIGHT_ALIGNMENT);
        history.setEnabled(!card.historyLines().isEmpty());
        column.add(Box.createVerticalStrut(8));
        column.add(history);
        return column;
    }

    /**
     *
     * Nothing happens except through the service: it refuses an admin, a signed out customer and a
     * zero amount, and only a card that was really written to the file produces a success dialog.
     */
    private void buy() {
        int amount = 0;
        Object chosen = denominations.getSelectedItem();
        if (chosen != null) {
            try {
                amount = Integer.parseInt(chosen.toString().replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {
                amount = 0;
            }
        }
        GiftCardService.Purchase result = cards.buy(amount);
        if (!result.isSaved()) {
            UiUtil.showProblems(this, "The gift card could not be bought", result.getProblems());
            return;
        }
        GiftCard card = result.getCard();
        refresh();
        owner.setStatusText("Gift card " + card.getCode() + " bought  \u00b7  "
                + card.balanceText());
        UiUtil.info(this, "Gift card bought\n\n"
                + "Code      " + card.getCode() + "\n"
                + "Value     Rs " + card.getDenomination() + "\n"
                + "Expires   " + card.getExpiresOn() + "\n\n"
                + "Pay with it on the payment screen or at the food counter. It now appears\n"
                + "in your gift cards list.");
    }

    /** Puts the code on the system clipboard; a locked clipboard is never a failed click. */
    private void copyCode(String code) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(code), null);
            owner.setStatusText("Gift card " + code + " copied");
        } catch (Exception e) {
            UiUtil.warn(this, "The code could not be copied automatically.\n\nIt is " + code);
        }
    }
}
