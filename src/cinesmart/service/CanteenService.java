package cinesmart.service;


import cinesmart.model.FoodOrder;
import cinesmart.model.MenuItem;
import cinesmart.model.Theatre;
import cinesmart.model.User;
import cinesmart.util.DataStore;
import cinesmart.util.GeoUtil;
import cinesmart.util.IDGenerator;
import cinesmart.util.Session;
import cinesmart.util.UiUtil;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CanteenService is the Stage 10 service: the food menu of every cinema hall, which hall is nearest
 * to the customer, and the food order they place.
 *
 * It follows the same two rules as every other service in this project:
 * 1. the DataStore is the truth - a menu is read back from data/canteen.txt by item id, so a price
 *    changed in another window cannot be paid at the old one;
 * 2. nothing is written unless every check passed, so a total is never shown for an order that was
 *    not saved.
 *
 * "Nearest to the user" is answered with real geography: {@link GeoUtil} measures the distance
 * from where the customer says they are to each hall's stored coordinates. A desktop program has
 * no GPS, so the customer picks their area once and the app measures from there.
 */
public class CanteenService {

    /** Most different things one order may hold, so a basket can never run away. */
    public static final int MAX_ITEMS_PER_ORDER = 20;
    /** Most of one item in one order. */
    public static final int MAX_QTY_PER_ITEM = 6;

    private final DataStore store;

    public CanteenService() {
        this(null);
    }

    public CanteenService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *\
     * Which halls, and which one is nearest
     * ------------------------------------------------------------------ */

    /**
     * One cinema hall together with how far it is from the customer. A hall without coordinates
     * keeps a distance of -1 and is sorted last, because "nearest" must never be a guess.
     */
    public static final class NearbyTheatre implements Comparable<NearbyTheatre> {

        private final Theatre theatre;
        private final double distanceKm;

        NearbyTheatre(Theatre theatre, double distanceKm) {
            this.theatre = theatre;
            this.distanceKm = distanceKm;
        }

        public Theatre getTheatre() {
            return theatre;
        }

        public String getTheatreId() {
            return theatre.getTheatreId();
        }

        public String getName() {
            return theatre.getName();
        }

        /** The city the hall is in, shown on the card so the customer can see it matches. */
        public String getCity() {
            return theatre.getCity();
        }

        /** "Whitefield" - the area the hall sits in. */
        public String getArea() {
            return theatre.getAddress();
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public boolean hasDistance() {
            return distanceKm >= 0;
        }

        /** "850 m away", "4.2 km away" or "distance unknown". */
        public String getDistanceText() {
            return GeoUtil.distanceText(distanceKm);
        }

        @Override
        public int compareTo(NearbyTheatre other) {
            // Halls with a known distance come first, nearest first; unknown ones keep file order.
            if (hasDistance() != other.hasDistance()) {
                return hasDistance() ? -1 : 1;
            }
            return Double.compare(distanceKm, other.distanceKm);
        }
    }

    /**
     * The halls of one city, nearest to the customer first.
     *
     * @param latitude  where the customer is, or 0 when they have not chosen an area yet
     * @param longitude where the customer is, or 0 when they have not chosen an area yet
     */
    public List<NearbyTheatre> theatresNear(String city, double latitude, double longitude) {
        List<NearbyTheatre> found = new ArrayList<>();
        boolean havePosition = GeoUtil.isRealPosition(latitude, longitude);
        for (Theatre theatre : store.getTheatres()) {
            if (theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))) {
                double km = (havePosition && theatre.hasPosition())
                        ? GeoUtil.distanceKm(latitude, longitude,
                                theatre.getLatitude(), theatre.getLongitude())
                        : -1;
                found.add(new NearbyTheatre(theatre, km));
            }
        }
        Collections.sort(found);
        return found;
    }

    /** The nearest hall of a city, or null when the city has none. */
    public NearbyTheatre nearestTheatre(String city, double latitude, double longitude) {
        List<NearbyTheatre> nearby = theatresNear(city, latitude, longitude);
        return nearby.isEmpty() ? null : nearby.get(0);
    }

    /** The areas the customer can pick as "where I am" - the areas of the halls in that city. */
    public List<String> areasOf(String city) {
        List<String> areas = new ArrayList<>();
        for (Theatre theatre : store.getTheatres()) {
            if (theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))
                    && !theatre.getAddress().isEmpty()
                    && !containsIgnoreCase(areas, theatre.getAddress())) {
                areas.add(theatre.getAddress());
            }
        }
        Collections.sort(areas);
        return areas;
    }

    /**
     * The point the distance is measured from. Picking an area means "the middle of that area",
     * which is the best a desktop program can do - and it is stated plainly on screen.
     */
    public double[] positionOfArea(String city, String area) {
        for (Theatre theatre : store.getTheatres()) {
            if (theatre.getCity().equalsIgnoreCase(UiUtil.clean(city))
                    && theatre.getAddress().equalsIgnoreCase(UiUtil.clean(area))
                    && theatre.hasPosition()) {
                return new double[] { theatre.getLatitude(), theatre.getLongitude() };
            }
        }
        return new double[] { 0, 0 };
    }

    private static boolean containsIgnoreCase(List<String> values, String wanted) {
        for (String value : values) {
            if (value.equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ *\
     * The menu of one hall
     * ------------------------------------------------------------------ */

    /**
     * Every item on one hall's menu, grouped ready for display: Popcorn first, then Beverages,
     * Snacks, Combos, Desserts - the order in {@link MenuItem#CATEGORIES}, not the file order, so
     * two halls show their menu the same way even though their data lines are in any order.
     */
    public List<Map.Entry<String, List<MenuItem>>> menuOf(String theatreId) {
        Map<String, List<MenuItem>> grouped = new LinkedHashMap<>();
        for (String category : MenuItem.CATEGORIES) {
            grouped.put(category, new ArrayList<MenuItem>());
        }
        for (MenuItem item : store.getMenuItems()) {
            if (item.getTheatreId().equalsIgnoreCase(UiUtil.clean(theatreId))) {
                List<MenuItem> list = grouped.get(item.getCategory());
                if (list == null) {
                    // An item with a hand-edited category still appears, under its own heading.
                    list = new ArrayList<MenuItem>();
                    grouped.put(item.getCategory(), list);
                }
                list.add(item);
            }
        }
        List<Map.Entry<String, List<MenuItem>>> menu = new ArrayList<>();
        for (Map.Entry<String, List<MenuItem>> section : grouped.entrySet()) {
            if (!section.getValue().isEmpty()) {
                menu.add(new AbstractMap.SimpleEntry<>(section.getKey(), section.getValue()));
            }
        }
        return menu;
    }

    /** How many different things this hall sells - the "12 items on the menu" line. */
    public int menuSize(String theatreId) {
        int count = 0;
        for (MenuItem item : store.getMenuItems()) {
            if (item.getTheatreId().equalsIgnoreCase(UiUtil.clean(theatreId))) {
                count++;
            }
        }
        return count;
    }

    /** One item read back from the store, so a price can never be paid at an old value. */
    public MenuItem itemOf(String itemId) {
        return store.findMenuItem(itemId);
    }

    /* ------------------------------------------------------------------ *\
     * The basket
     * ------------------------------------------------------------------ */

    /**
     * What the customer picked before confirming. The basket is deliberately NOT a model written to
     * a file: it is a screen state, and the real order is built from it only once, at the end.
     */
    public static final class Basket {

        /** itemId -> how many, in the order they were added, so the list never reshuffles. */
        private final Map<String, Integer> counts = new LinkedHashMap<>();

        /** Returns the problem with adding this, or null when it may be added. */
        public String canAdd(MenuItem item, int howMany) {
            if (item == null) {
                return "That item is no longer on the menu.";
            }
            if (howMany < 1) {
                return "Choose at least one.";
            }
            int already = quantityOf(item.getItemId());
            if (already + howMany > MAX_QTY_PER_ITEM) {
                return "You can order up to " + MAX_QTY_PER_ITEM + " of one item.";
            }
            if (counts.size() + (already == 0 ? 1 : 0) > MAX_ITEMS_PER_ORDER) {
                return "An order can hold up to " + MAX_ITEMS_PER_ORDER + " different items.";
            }
            return null;
        }

        public void add(String itemId, int howMany) {
            counts.put(itemId, quantityOf(itemId) + Math.max(1, howMany));
        }

        public void remove(String itemId) {
            counts.remove(itemId);
        }

        public void clear() {
            counts.clear();
        }

        public int quantityOf(String itemId) {
            Integer count = counts.get(itemId);
            return (count == null) ? 0 : count;
        }

        /** How many different things are in the basket. */
        public int size() {
            return counts.size();
        }

        /** How many things in total, counting quantities - the "3 items" on the button. */
        public int itemCount() {
            int total = 0;
            for (Integer count : counts.values()) {
                total += count;
            }
            return total;
        }

        public boolean isEmpty() {
            return counts.isEmpty();
        }

        /** The item ids in the order they were added. */
        public List<String> itemIds() {
            return new ArrayList<>(counts.keySet());
        }

        /**
         * The money, always worked out from the MENU and never from a number the screen was holding.
         * A price edited in another window is therefore reflected at once, and a basket entry whose
         * item has been removed from the menu is skipped rather than quietly priced at zero.
         */
        public int totalOf(CanteenService service) {
            int total = 0;
            for (String itemId : counts.keySet()) {
                MenuItem item = service.itemOf(itemId);
                if (item != null) {
                    total += item.getPrice() * quantityOf(itemId);
                }
            }
            return total;
        }

        /** "F002 x 2;F007 x 1" - the lines column of the saved order. */
        public String linesText() {
            StringBuilder text = new StringBuilder();
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                if (text.length() > 0) {
                    text.append(FoodOrder.LINE_SEPARATOR_TEXT);
                }
                text.append(entry.getKey()).append(" x ").append(entry.getValue());
            }
            return text.toString();
        }

        /** The printed order, with the names and prices the customer actually sees. */
        public List<String> receiptLines(CanteenService service) {
            List<String> lines = new ArrayList<>();
            for (String itemId : counts.keySet()) {
                MenuItem item = service.itemOf(itemId);
                if (item == null) {
                    continue;
                }
                int howMany = quantityOf(itemId);
                lines.add(item.getName() + "  x " + howMany + "  =  Rs "
                        + (item.getPrice() * howMany));
            }
            return lines;
        }
    }

    /* ------------------------------------------------------------------ *\
     * Placing the order
     * ------------------------------------------------------------------ */

    /** The answer of {@link #placeOrder}: the saved order, or the reasons nothing was saved. */
    public static final class OrderResult {

        private final boolean saved;
        private final FoodOrder order;
        private final List<String> problems;

        OrderResult(boolean saved, FoodOrder order, List<String> problems) {
            this.saved = saved;
            this.order = order;
            this.problems = problems;
        }

        public boolean isSaved() {
            return saved;
        }

        public FoodOrder getOrder() {
            return order;
        }

        public List<String> getProblems() {
            return problems;
        }
    }

    /**
     * Every reason an order may not be placed, collected at once so the screen can show them all in
     * one dialog instead of one mistake per attempt.
     *
     * @return the problems; empty when the order may be placed
     */
    public List<String> problemsWith(Theatre theatre, Basket basket, String bookingId) {
        List<String> problems = new ArrayList<>();
        if (Session.currentUser() == null) {
            problems.add("Please sign in before ordering food.");
            return problems; // nothing else about the basket matters to a guest
        }
        if (Session.isAdmin()) {
            problems.add("An admin account browses the food court read only. "
                    + "Customers order from their own account.");
        }
        if (theatre == null) {
            problems.add("Please choose the cinema hall you are ordering from.");
        }
        if (basket == null || basket.isEmpty()) {
            problems.add("Your basket is empty - add something from the menu first.");
            return problems;
        }
        // One order is for ONE hall: the basket belongs to the hall that is open on screen.
        String hall = theatre.getTheatreId();
        for (String itemId : basket.itemIds()) {
            MenuItem item = itemOf(itemId);
            if (item == null) {
                problems.add("An item in your basket is no longer on any menu.");
            } else if (!item.getTheatreId().equals(hall)) {
                problems.add(item.getName() + " is not on the menu of " + theatre.getName()
                        + ". Clear the basket and start again.");
            }
        }
        if (bookingId != null && !bookingId.isEmpty() && store.findBooking(bookingId) == null) {
            problems.add("The ticket booking this food is for could not be found.");
        }
        return problems;
    }

    /**
     * Turns a basket into a saved order.
     *
     * Order matters: every rule is checked first, then the id is made, then the line is written.
     * If that write fails the customer is told plainly and nothing is left behind, so there can
     * never be an order on screen that is not in data/foodorders.txt.
     *
     * @param bookingId the ticket this food is for, or "" to order without one
     */
    public OrderResult placeOrder(Theatre theatre, Basket basket, String bookingId) {
        return placeOrder(theatre, basket, bookingId, 0);
    }

    /**
     * The same order after an offer has been checked.
     *
     * The discount arrives already validated by {@link OfferService} and is re-clamped here against
     * the real basket total, so a screen that passes a wrong number still cannot produce an order
     * worth less than zero - the store has the last word, as it does everywhere else.
     *
     * @param discount rupees taken off by an offer code; 0 when no code was used
     */
    public OrderResult placeOrder(Theatre theatre, Basket basket, String bookingId, int discount) {
        List<String> problems = problemsWith(theatre, basket, bookingId);
        if (!problems.isEmpty()) {
            return new OrderResult(false, null, problems);
        }
        int total = basket.totalOf(this);
        int reduced = Math.max(0, total - Math.max(0, Math.min(discount, total)));

        // The total is worked out here, from the menu, at the moment of saving.
        FoodOrder order = new FoodOrder(IDGenerator.foodOrderId(),
                Session.currentUser().getUserName(),
                theatre.getTheatreId(),
                theatre.getName() + " \u00b7 " + theatre.getAddress(),
                basket.linesText(), reduced, bookingId);
        if (!store.addFoodOrder(order)) {
            return new OrderResult(false, null, Collections.singletonList(
                    "The food order could not be saved, so nothing was charged. Please try again."));
        }
        return new OrderResult(true, order, new ArrayList<String>());
    }

    /** "My food orders": every order of the signed in customer, newest first. */
    public List<FoodOrder> ordersForCurrentUser() {
        User current = Session.currentUser();
        return store.foodOrdersForUser(current == null ? "" : current.getUserName());
    }

    /** The saved order printed as lines, the way the confirmation dialog shows it. */
    public List<String> orderLines(FoodOrder order) {
        List<String> lines = new ArrayList<>();
        if (order == null) {
            return lines;
        }
        lines.add("Order " + order.getOrderId() + "  \u00b7  " + order.getStatus().label());
        lines.add(order.hallName());
        lines.add("Placed " + order.getPlacedAt());
        if (!order.getBookingId().isEmpty()) {
            lines.add("For ticket booking " + order.getBookingId());
        }
        return lines;
    }
}