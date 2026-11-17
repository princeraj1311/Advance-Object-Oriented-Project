package cinesmart.view;


import cinesmart.model.FoodOrder;
import cinesmart.model.MenuItem;
import cinesmart.model.Offer;
import cinesmart.model.Theatre;
import cinesmart.service.CanteenService;
import cinesmart.service.OfferService;
import cinesmart.util.GeoUtil;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.JTextField;

/**
 * CanteenPanel is the Stage 10 screen: the food menu of the cinema halls, and the order the
 * customer places.
 *
 * It answers the three questions the screen exists for, in this order:
 * <ol>
 *   <li><b>Which hall?</b> the halls of the city are listed nearest-first, with the real distance
 *       measured from the area the customer picked in the strip at the top;</li>
 *   <li><b>What do they sell?</b> the selected hall's own menu, grouped Popcorn, Beverages,
 *       Snacks, Combos, Desserts, every row showing the name, the price and an Add button;</li>
 *   <li><b>What do I pay?</b> the basket at the bottom, priced from the menu rather than from a
 *       number this screen remembered.</li>
 * </ol>
 *
 * The panel owns no rule: it asks {@link CanteenService} what is allowed and shows the answer,
 * exactly like every other screen in this project.
 */
public final class CanteenPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final MainFrame owner;
    private final CanteenService canteen;

    /** Where the customer says they are - the distance is measured from here. */
    private String area = "";
    /** The hall whose menu is on screen. */
    private CanteenService.NearbyTheatre selected;
    private final CanteenService.Basket basket = new CanteenService.Basket();

    private JComboBox<String> areaCombo;
    private JLabel areaNote;
    private JPanel hallList;
    private JLabel hallTitle;
    private JLabel hallSub;
    private JPanel menuArea;
    private JLabel basketSummary;
    private JLabel basketTotal;
    /** The offer code typed on the basket bar, and the line that says what it is worth. */
    private JTextField offerField;
    private JLabel offerHint;
    /** Rupees the applied offer takes off; 0 when no code is applied. Recalculated, never trusted. */
    private int discount;
    private JButton orderButton;
    private JButton clearButton;

    public CanteenPanel(MainFrame owner, CanteenService canteen) {
        this.owner = owner;
        this.canteen = (canteen == null) ? new CanteenService() : canteen;

        setBackground(Theme.WINDOW_BG);
        setLayout(new BorderLayout());
        setBorder(UiUtil.padded(16, 22, 12, 22));

        add(header(), BorderLayout.NORTH);
        add(middle(), BorderLayout.CENTER);
        add(basketBar(), BorderLayout.SOUTH);

        refresh();
    }

    /* ------------------------------------------------------------------ *\
     * Building the three parts of the screen
     * ------------------------------------------------------------------ */

    private JComponent header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.WINDOW_BG);
        panel.setBorder(UiUtil.padded(4, 4, 12, 4));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(UiUtil.heading("Food and canteen"));
        text.add(Box.createVerticalStrut(4));
        text.add(UiUtil.subtitle("Order at the counter of the cinema hall, picked by how close it is"));
        panel.add(text, BorderLayout.WEST);

        JPanel pick = new JPanel();
        pick.setOpaque(false);
        pick.setLayout(new BoxLayout(pick, BoxLayout.X_AXIS));
        pick.add(UiUtil.fieldLabel("Your area:"));
        pick.add(Box.createHorizontalStrut(8));
        areaCombo = new JComboBox<>();
        areaCombo.setPreferredSize(new Dimension(190, 30));
        areaCombo.addActionListener(event -> areaChosen());
        pick.add(areaCombo);
        pick.add(Box.createHorizontalStrut(10));
        areaNote = UiUtil.hint(" ");
        areaNote.setPreferredSize(new Dimension(300, 20));
        pick.add(areaNote);
        panel.add(pick, BorderLayout.EAST);
        return panel;
    }

    /** The hall list on the left and the menu on the right. */
    private JComponent middle() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        hallList = new JPanel();
        hallList.setLayout(new BoxLayout(hallList, BoxLayout.Y_AXIS));
        hallList.setBackground(Theme.SURFACE_SOFT);
        hallList.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(12, 12, 12, 12)));
        JScrollPane hallScroll = new JScrollPane(hallList);
        hallScroll.setBorder(BorderFactory.createEmptyBorder());
        hallScroll.getViewport().setBackground(Theme.SURFACE_SOFT);
        hallScroll.setPreferredSize(new Dimension(300, 400));
        row.add(hallScroll, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(0, 8));
        right.setOpaque(false);
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        hallTitle = UiUtil.heading("Menu");
        hallSub = UiUtil.subtitle(" ");
        titleBox.add(hallTitle);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(hallSub);
        right.add(titleBox, BorderLayout.NORTH);

        menuArea = new JPanel();
        menuArea.setLayout(new BoxLayout(menuArea, BoxLayout.Y_AXIS));
        menuArea.setBackground(Theme.WINDOW_BG);
        JScrollPane menuScroll = new JScrollPane(menuArea);
        menuScroll.setBorder(BorderFactory.createEmptyBorder());
        menuScroll.getViewport().setBackground(Theme.WINDOW_BG);
        right.add(menuScroll, BorderLayout.CENTER);

        row.add(right, BorderLayout.CENTER);
        return row;
    }

    /** The bottom bar: what is in the basket, what it costs, and the button that orders it. */
    private JComponent basketBar() {
        JPanel bar = new JPanel(new BorderLayout(14, 0));
        bar.setBackground(Theme.SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(10, 16, 10, 16)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        basketSummary = UiUtil.muted("Your basket is empty");
        basketSummary.setFont(Theme.titleFont(13));
        left.add(basketSummary);
        left.add(Box.createVerticalStrut(3));
        basketTotal = UiUtil.muted(" ");
        left.add(basketTotal);
        offerHint = UiUtil.hint(" ");
        left.add(Box.createVerticalStrut(2));
        left.add(offerHint);
        bar.add(left, BorderLayout.WEST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        offerField = UiUtil.textField(9);
        offerField.setToolTipText("Copy a code from the Offers screen and apply it to this basket");
        offerField.addActionListener(event -> applyOffer());
        buttons.add(UiUtil.fieldLabel("Offer:"));
        buttons.add(offerField);
        buttons.add(UiUtil.outlineButton("Apply", event -> applyOffer()));
        clearButton = UiUtil.outlineButton("Clear basket", event -> {
            basket.clear();
            discount = 0;
            offerField.setText("");
            UiUtil.setHint(offerHint, " ", true);
            updateBasket();
            fillMenu();
        });
        buttons.add(clearButton);
        JButton orders = UiUtil.outlineButton("My food orders", event -> owner.showFoodOrders());
        buttons.add(orders);
        orderButton = UiUtil.primaryButton("Place order", event -> placeOrder());
        buttons.add(orderButton);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    /* ------------------------------------------------------------------ *\
     * Refreshing: the hall list, the menu, and the basket
     * ------------------------------------------------------------------ */

    /** The one entry point: called on open and whenever the city or the area changes. */
    public void refresh() {
        String city = Session.selectedCity();

        List<String> areas = canteen.areasOf(city);
        areaCombo.removeAllItems();
        for (String name : areas) {
            areaCombo.addItem(name);
        }
        if (!areas.contains(area)) {
            // Default to the first area of the city, so the list is never "unknown distance".
            area = areas.isEmpty() ? "" : areas.get(0);
        }
        if (!area.isEmpty() && !areas.isEmpty()) {
            areaCombo.setSelectedItem(area);
        }
        updateAreaNote();

        String keep = (selected == null) ? null : selected.getTheatreId();
        fillHallList(city, keep);
        // A new city means a new set of offers, so nothing applied on the old one may survive.
        discount = 0;
        offerField.setText("");
        UiUtil.setHint(offerHint, " ", true);
        updateBasket();
    }

    /** Says plainly how the distance is worked out, so "nearest" is never a mystery. */
    private void updateAreaNote() {
        if (area.isEmpty()) {
            UiUtil.setHint(areaNote, "No halls in this city yet.", false);
            return;
        }
        double[] here = canteen.positionOfArea(Session.selectedCity(), area);
        if (GeoUtil.isRealPosition(here[0], here[1])) {
            UiUtil.setHint(areaNote, "Distances are measured from the middle of " + area + ".", true);
        } else {
            UiUtil.setHint(areaNote, "No position known for " + area + ".", false);
        }
    }

    private void areaChosen() {
        Object chosen = areaCombo.getSelectedItem();
        if (chosen == null) {
            return;
        }
        area = chosen.toString();
        updateAreaNote();
        String keep = (selected == null) ? null : selected.getTheatreId();
        fillHallList(Session.selectedCity(), keep);
    }

    /** Rebuilds the nearest-first hall list and opens the chosen hall's menu. */
    private void fillHallList(String city, String keepTheatreId) {
        hallList.removeAll();
        double[] here = (area.isEmpty()) ? new double[] { 0, 0 }
                : canteen.positionOfArea(city, area);

        List<CanteenService.NearbyTheatre> nearby = canteen.theatresNear(city, here[0], here[1]);
        if (nearby.isEmpty()) {
            JLabel none = UiUtil.muted("No cinema halls in " + city + " yet.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            hallList.add(none);
            hallTitle.setText("Menu");
            hallSub.setText(" ");
            menuArea.removeAll();
            selected = null;
            return;
        }

        hallList.add(UiUtil.muted("Cinemas in " + city + ", nearest first"));
        hallList.add(Box.createVerticalStrut(8));

        CanteenService.NearbyTheatre wanted = null;
        for (int i = 0; i < nearby.size(); i++) {
            CanteenService.NearbyTheatre hall = nearby.get(i);
            if (keepTheatreId != null && hall.getTheatreId().equals(keepTheatreId)) {
                wanted = hall;
            }
            if (wanted == null && i == 0) {
                wanted = hall;
            }
            hallList.add(hallRow(hall, i == 0));
            hallList.add(Box.createVerticalStrut(8));
        }

        selected = wanted;
        fillMenu();
    }

    /** One hall card: name, area, the real distance, and how many things it sells. */
    private JComponent hallRow(CanteenService.NearbyTheatre hall, boolean first) {
        boolean open = (selected != null && selected.getTheatreId().equals(hall.getTheatreId()));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        card.setBackground(open ? Theme.BLUE_SOFT : Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                open ? BorderFactory.createLineBorder(Theme.BLUE, 2) : Theme.cardBorder(),
                UiUtil.padded(9, 11, 9, 11)));

        JLabel name = new JLabel(hall.getName());
        name.setFont(Theme.titleFont(13));
        name.setForeground(Theme.TEXT);
        card.add(name);
        card.add(Box.createVerticalStrut(3));

        JLabel where = UiUtil.muted(hall.getArea() + ", " + hall.getCity());
        card.add(where);
        card.add(Box.createVerticalStrut(3));

        JLabel far = UiUtil.muted(hall.getDistanceText()
                + "  \u00b7  " + canteen.menuSize(hall.getTheatreId()) + " items");
        far.setFont(Theme.smallBoldFont());
        far.setForeground(Theme.BLUE);
        card.add(far);
        if (first && hall.hasDistance()) {
            card.add(Box.createVerticalStrut(3));
            card.add(UiUtil.muted("Nearest to you"));
        }

        card.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                selected = hall;
                fillHallList(Session.selectedCity(), hall.getTheatreId());
            }
        });
        return card;
    }

    /* ------------------------------------------------------------------ *\
     * The menu of the selected hall
     * ------------------------------------------------------------------ */

    /** Builds the whole menu of the open hall: one block per category, one row per item. */
    private void fillMenu() {
        menuArea.removeAll();
        if (selected == null) {
            menuArea.revalidate();
            menuArea.repaint();
            return;
        }
        Theatre hall = selected.getTheatre();
        hallTitle.setText(hall.getName());
        hallSub.setText(hall.getAddress() + ", " + hall.getCity() + "  \u00b7  "
                + selected.getDistanceText() + "  \u00b7  "
                + canteen.menuSize(hall.getTheatreId()) + " items on the menu");

        for (Map.Entry<String, List<MenuItem>> section : canteen.menuOf(hall.getTheatreId())) {
            JLabel heading = new JLabel(section.getKey());
            heading.setFont(Theme.titleFont(14));
            heading.setForeground(Theme.TEXT);
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);
            menuArea.add(heading);
            menuArea.add(Box.createVerticalStrut(6));

            for (MenuItem item : section.getValue()) {
                menuArea.add(itemRow(item));
                menuArea.add(Box.createVerticalStrut(6));
            }
            menuArea.add(Box.createVerticalStrut(8));
        }
        menuArea.revalidate();
        menuArea.repaint();
    }

    /** One menu line: the veg dot, the name, the price and the Add button. */
    private JComponent itemRow(MenuItem item) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setBackground(Theme.SURFACE);
        row.setBorder(BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(8, 11, 8, 11)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        left.add(vegDot(item.isVegetarian()));
        left.add(Box.createHorizontalStrut(10));

        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(item.getName());
        name.setFont(Theme.titleFont(13));
        name.setForeground(Theme.TEXT);
        words.add(name);
        words.add(Box.createVerticalStrut(2));
        words.add(UiUtil.muted(item.getCategory()));
        left.add(words);
        row.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        JLabel price = new JLabel(item.priceText());
        price.setFont(Theme.titleFont(13));
        price.setForeground(Theme.TEXT);
        right.add(price);

        int inBasket = basket.quantityOf(item.getItemId());
        if (inBasket > 0) {
            JLabel count = UiUtil.muted("in basket: " + inBasket);
            right.add(count);
        }
        JButton add = UiUtil.outlineButton("Add", event -> addToBasket(item));
        right.add(add);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** The little green or red square in front of every item name. */
    private JComponent vegDot(boolean vegetarian) {
        JPanel dot = new JPanel();
        dot.setPreferredSize(new Dimension(12, 12));
        dot.setMaximumSize(new Dimension(12, 12));
        Color dotColour = (vegetarian) ? Theme.GREEN : Theme.RED;
        dot.setBackground(dotColour);
        dot.setBorder(BorderFactory.createLineBorder(dotColour.darker(), 1));
        // BoxLayout inside a horizontal box lines components on the centre line unless told not to.
        dot.setAlignmentY(Component.CENTER_ALIGNMENT);
        JPanel holder = new JPanel();
        holder.setOpaque(false);
        holder.setLayout(new BoxLayout(holder, BoxLayout.X_AXIS));
        holder.add(dot);
        return holder;
    }

    /* ------------------------------------------------------------------ *\
     * The basket and placing the order
     * ------------------------------------------------------------------ */

    private void addToBasket(MenuItem item) {
        String problem = basket.canAdd(item, 1);
        if (problem != null) {
            UiUtil.warn(this, problem);
            return;
        }
        basket.add(item.getItemId(), 1);
        updateBasket();
        // Rebuild the rows so the "in basket" counts next to each item are true straight away.
        fillMenu();
        owner.setStatusText("Added " + item.getName() + "  \u00b7  basket "
                + basket.itemCount() + " item(s)  \u00b7  Rs " + basket.totalOf(canteen));
    }

    /** Everything on the basket bar is written from the model, so it cannot drift from the menu. */
    private void updateBasket() {
        boolean empty = basket.isEmpty();
        basketSummary.setText(empty ? "Your basket is empty"
                : basket.itemCount() + " item(s) from "
                        + (selected == null ? "" : selected.getName()));
        basketTotal.setText(empty ? "Pick something from the menu on the right"
                : totalText());
        orderButton.setEnabled(!empty);
        clearButton.setEnabled(!empty);
    }

    /**
     * The basket total, with the discount spelled out when a code is applied.
     *
     * Both numbers are shown - "Rs 1000  (Rs 75 offer saved Rs 925)" - so the customer can check the
     * arithmetic instead of trusting a screen that changed the price.
     */
    private String totalText() {
        int total = basket.totalOf(canteen);
        if (discount <= 0) {
            return "Total  Rs " + total;
        }
        return "Total  Rs " + (total - discount) + "   (Rs " + total
                + "  \u2212  Rs " + discount + " offer)";
    }

    /**
     * Checks the typed code against the basket and shows what it is worth.
     *
     * An empty box is not an error, it simply means "no offer". The code is only recorded when the
     * order is really placed, so changing your mind in the basket never uses the offer up.
     */
    private void applyOffer() {
        if (basket.isEmpty()) {
            discount = 0;
            UiUtil.setHint(offerHint, "Add something to the basket first.", false);
            return;
        }
        if (offerField.getText().trim().isEmpty()) {
            discount = 0;
            UiUtil.setHint(offerHint, "No offer code applied.", true);
            updateBasket();
            return;
        }
        OfferService.Result result = new OfferService().check(offerField.getText(),
                Session.selectedCity(), Offer.Scope.FOOD, basket.totalOf(canteen));
        if (result.isAllowed()) {
            discount = result.getDiscount();
            UiUtil.setHint(offerHint, result.getOffer().getTitle()
                    + " \u00b7 you save Rs " + discount + " on this food order.", true);
        } else {
            discount = 0;
            UiUtil.setHint(offerHint, String.join("  ", result.getProblems()), false);
        }
        updateBasket();
    }

    /** Asks the service, then shows what it says. Nothing is written by this screen. */
    private void placeOrder() {
        if (selected == null) {
            UiUtil.warn(this, "Please choose the cinema hall you are ordering from.");
            return;
        }
        Theatre hall = selected.getTheatre();

        // The code is redeemed HERE, when the order is really placed, and never when it was typed:
        // filling a basket and then clearing it must not use up the customer's offer.
        OfferService offers = new OfferService();
        int discountToUse = 0;
        OfferService.Result applied = null;
        if (!offerField.getText().trim().isEmpty()) {
            applied = offers.redeem(offerField.getText(), Session.selectedCity(),
                    Offer.Scope.FOOD, basket.totalOf(canteen));
            if (!applied.isAllowed()) {
                discount = 0;
                UiUtil.setHint(offerHint, String.join("  ", applied.getProblems()), false);
                updateBasket();
                UiUtil.showProblems(this, "The food order could not be placed",
                        applied.getProblems());
                return;
            }
            discountToUse = applied.getDiscount();
        }

        CanteenService.OrderResult result = canteen.placeOrder(hall, basket, "", discountToUse);
        if (!result.isSaved()) {
            discount = 0;
            updateBasket();
            UiUtil.showProblems(this, "The food order could not be placed", result.getProblems());
            return;
        }

        FoodOrder order = result.getOrder();
        StringBuilder text = new StringBuilder();
        for (String line : canteen.orderLines(order)) {
            text.append(line).append("\n");
        }
        for (String line : basket.receiptLines(canteen)) {
            text.append(line).append("\n");
        }
        if (discountToUse > 0) {
            text.append("Offer ").append(applied.getOffer().getCode())
                    .append("  -  Rs ").append(discountToUse).append("\n");
        }
        text.append("Total  ").append(order.totalText());

        basket.clear();
        discount = 0;
        offerField.setText("");
        UiUtil.setHint(offerHint, " ", true);
        updateBasket();
        fillMenu();
        owner.setStatusText("Food order " + order.getOrderId() + "  \u00b7  "
                + order.hallName() + "  \u00b7  " + order.totalText());
        UiUtil.info(this, "Food order placed\n\n" + text
                + "\n\nCollect it at the counter of " + order.hallName());
    }
}