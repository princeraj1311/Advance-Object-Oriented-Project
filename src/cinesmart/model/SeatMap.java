package cinesmart.model;


import cinesmart.util.UiUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * SeatMap is the shape of one auditorium plus every seat rule of Stage 4: which seats exist,
 * which are taken, how many a customer may take, and which free seats best match the seat
 * preference saved on the customer's profile.
 *
 * The layout is DERIVED from the seat count instead of being typed by an admin, because the
 * Stage 3 data file only stores how many seats a screen has:
 *   10 -  60 seats ->  8 per row      101 - 150 seats -> 12 per row
 *   61 - 100 seats -> 10 per row      151 - 240 seats -> 14 per row, 241 and above -> 16
 * Rows are lettered from the screen (A is the front row) and every row is split into three
 * blocks by two aisles, which is what makes "easy exit" and "couple seats" computable at all.
 *
 * Like the rest of the project this class decides and the view only displays: the seat panel
 * asks for {@link SeatState} per seat and never repeats a rule.
 */
public final class SeatMap {

    /** A single booking may not fill a row: ten seats keeps a booking a booking. */
    public static final int MAX_SEATS = 10;

    /** The same five strings as Session.SEAT_PREFERENCES; profile text is matched to these. */
    public static final String PREFERENCE_ANY = "No Preference";
    public static final String PREFERENCE_CENTER = "Near Center";
    public static final String PREFERENCE_AWAY = "Away From Screen";
    public static final String PREFERENCE_COUPLE = "Couple/Adjacent Seats";
    public static final String PREFERENCE_EXIT = "Easy Exit";

    /** The only thing a seat button needs to know: how to colour this seat. */
    public enum SeatState {
        AVAILABLE, SELECTED, SUGGESTED, HELD, BOOKED
    }

    private final String showId;
    private final int price;
    private final int totalSeats;
    private final int columns;
    /** Rows that hold the full number of columns. */
    private final int fullRows;
    /** Seats on the last row when it cannot be full; that row is centred, like a real hall. */
    private final int shortRowSeats;
    private final int rows;
    /** Seats to the LEFT of the first / second aisle: the gap is drawn after them. */
    private final int aisleOne;
    private final int aisleTwo;

    private final Set<String> booked = new LinkedHashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Set<String> suggested = new LinkedHashSet<>();

    /** Why the current suggestion was picked; shown under the smart-seat buttons. */
    private String explanation = "";

    /**
     * @param takenSeats the seats already held for the show. Stage 3 data stores only a COUNT, so
     *                   BookingService hands in the list produced by
     *                   {@link #seatsForCount(String, int, int)} when the file names no seats.
     */
    public SeatMap(String showId, int totalSeats, int price, Set<String> takenSeats) {
        this.showId = UiUtil.clean(showId);
        this.price = price;
        this.totalSeats = Math.max(1, totalSeats);
        this.columns = columnsFor(this.totalSeats);
        this.fullRows = this.totalSeats / columns;
        this.shortRowSeats = this.totalSeats % columns;
        this.rows = fullRows + (shortRowSeats > 0 ? 1 : 0);
        this.aisleOne = Math.max(1, (int) Math.round(columns * 0.36));
        this.aisleTwo = Math.min(columns - 1, Math.max(this.aisleOne + 1, columns - this.aisleOne));
        if (takenSeats != null) {
            for (String seat : takenSeats) {
                String label = UiUtil.clean(seat).toUpperCase();
                if (!label.isEmpty()) {
                    booked.add(label);
                }
            }
        }
    }

    /* ------------------------------------------------------------------ *\
     * Layout: how the auditorium is shaped
     * ------------------------------------------------------------------ */

    /** 8 to 16 seats per row: wide enough to look like a hall, narrow enough to fit a screen. */
    public static int columnsFor(int totalSeats) {
        if (totalSeats <= 60) {
            return 8;
        }
        if (totalSeats <= 100) {
            return 10;
        }
        if (totalSeats <= 150) {
            return 12;
        }
        if (totalSeats <= 240) {
            return 14;
        }
        return 16;
    }

    /** Row A is the front row, because the screen is drawn above row A. */
    public static String rowLetter(int row) {
        return String.valueOf((char) ('A' + row));
    }

    public static String label(int row, int col) {
        return rowLetter(row) + (col + 1);
    }

    /** "C7" into {row 2, column 6}; null when the text is not a seat at all. */
    private static int[] parse(String seat) {
        String text = UiUtil.clean(seat).toUpperCase();
        if (text.length() < 2 || !Character.isLetter(text.charAt(0))) {
            return null;
        }
        try {
            return new int[]{text.charAt(0) - 'A', Integer.parseInt(text.substring(1)) - 1};
        } catch (Exception notASeat) {
            return null;
        }
    }

    /** Seat order as it reads in a cinema: A1, A2 ... A10, B1 - never A10 before A2. */
    public static int byPosition(String first, String second) {
        int[] a = parse(first);
        int[] b = parse(second);
        if (a == null || b == null) {
            return String.valueOf(first).compareTo(String.valueOf(second));
        }
        return (a[0] != b[0]) ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]);
    }

    /**
     * Stage 3 stores only a booked COUNT, but a map that shows a hundred free seats in a half
     * empty hall would be a lie. The taken seats are therefore picked from the show id, so the
     * same show always shows the same taken seats.
     */
    public static List<String> seatsForCount(String showId, int totalSeats, int count) {
        return seatsForCount(showId, totalSeats, count, Collections.emptySet());
    }

    /**
     * The same preview, but it never picks a seat that is already in {@code alreadyTaken}.
     *
     * A show that has been through Stage 4 names only the seats held through CineSmart, while the
     * rest of the sold count still comes from Stage 3. Both groups have to show as taken, otherwise
     * the map offers seats that are already sold.
     */
    public static List<String> seatsForCount(String showId, int totalSeats, int count,
                                             Set<String> alreadyTaken) {
        Set<String> taken = new LinkedHashSet<>();
        if (alreadyTaken != null) {
            for (String seat : alreadyTaken) {
                String label = UiUtil.clean(seat).toUpperCase();
                if (!label.isEmpty()) {
                    taken.add(label);
                }
            }
        }
        List<String> free = new SeatMap(showId, totalSeats, 0, null).allSeats();
        free.removeAll(taken);
        int wanted = Math.max(0, Math.min(count, free.size()));
        if (wanted == 0) {
            return new ArrayList<>();
        }
        Collections.shuffle(free, new Random(UiUtil.clean(showId).toUpperCase().hashCode() * 31L
                + totalSeats));
        List<String> preview = new ArrayList<>(free.subList(0, wanted));
        Collections.sort(preview, SeatMap::byPosition);
        return preview;
    }

    public String getShowId() {
        return showId;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public int getPrice() {
        return price;
    }

    /** How many seats to draw before the aisle gap appears. */
    public int seatsBeforeFirstAisle() {
        return aisleOne;
    }

    public int seatsBeforeSecondAisle() {
        return aisleTwo;
    }

    private int firstColumnOf(int row) {
        if (shortRowSeats > 0 && row == rows - 1) {
            return (columns - shortRowSeats) / 2;
        }
        return 0;
    }

    private int lastColumnOf(int row) {
        if (shortRowSeats > 0 && row == rows - 1) {
            return firstColumnOf(row) + shortRowSeats - 1;
        }
        return columns - 1;
    }

    /** The seats of one row, left to right (the last row is shorter and centred). */
    public List<String> rowSeats(int row) {
        List<String> seats = new ArrayList<>();
        if (row < 0 || row >= rows) {
            return seats;
        }
        for (int col = firstColumnOf(row); col <= lastColumnOf(row); col++) {
            seats.add(label(row, col));
        }
        return seats;
    }

    public List<String> allSeats() {
        List<String> seats = new ArrayList<>(totalSeats);
        for (int row = 0; row < rows; row++) {
            seats.addAll(rowSeats(row));
        }
        return seats;
    }

    public boolean exists(String seat) {
        int[] at = parse(seat);
        return at != null && at[0] >= 0 && at[0] < rows
                && at[1] >= firstColumnOf(at[0]) && at[1] <= lastColumnOf(at[0]);
    }

    /** A seat the customer can step out of without climbing over anybody. */
    public boolean isAisleSeat(String seat) {
        int[] at = parse(seat);
        return at != null && isAisleSeat(at[0], at[1]);
    }

    private boolean isAisleSeat(int row, int col) {
        return col == firstColumnOf(row) || col == lastColumnOf(row)
                || col == aisleOne - 1 || col == aisleOne
                || col == aisleTwo - 1 || col == aisleTwo;
    }

    /* ------------------------------------------------------------------ *\
     * Selection: the rules a customer may not break
     * ------------------------------------------------------------------ */

    /**
     * The one question the seat buttons ask. A seat that does not exist answers BOOKED so the
     * map of a hall with a short last row still reads as "that space is not a seat".
     */
    public SeatState stateOf(String seat) {
        if (!exists(seat) || booked.contains(seat)) {
            return SeatState.BOOKED;
        }
        if (selected.contains(seat)) {
            return SeatState.SELECTED;
        }
        if (suggested.contains(seat)) {
            return SeatState.SUGGESTED;
        }
        return SeatState.AVAILABLE;
    }

    public boolean isAvailable(String seat) {
        return exists(seat) && !booked.contains(seat);
    }

    /**
     * Click a seat: free seats join the booking, a selected seat leaves it again.
     *
     * @return null when the click was allowed, otherwise the reason to show the customer
     */
    public String toggle(String seat) {
        if (!exists(seat)) {
            return "Seat " + seat + " is not part of this auditorium.";
        }
        if (booked.contains(seat)) {
            return "Seat " + seat + " is already taken.";
        }
        if (selected.contains(seat)) {
            selected.remove(seat);
            return null;
        }
        if (selected.size() >= MAX_SEATS) {
            return "One booking can hold at most " + MAX_SEATS + " seats. Remove one first.";
        }
        selected.add(seat);
        suggested.remove(seat); // a seat the customer chose is no longer a mere suggestion
        return null;
    }

    /** The seats a customer picked, in the order a ticket prints them. */
    public List<String> selectedSeats() {
        List<String> sorted = new ArrayList<>(selected);
        Collections.sort(sorted, SeatMap::byPosition);
        return sorted;
    }

    public int selectedCount() {
        return selected.size();
    }

    public String selectedSeatsText() {
        return String.join(", ", selectedSeats());
    }

    public int selectedAmount() {
        return price * selected.size();
    }

    public void clearSelection() {
        selected.clear();
        suggested.clear();
        explanation = "";
    }

    /** Seats the customer may still sit in. Must agree with ShowTime.seatsAvailable(). */
    public int freeCount() {
        int free = 0;
        for (String seat : allSeats()) {
            if (!booked.contains(seat)) {
                free++;
            }
        }
        return free;
    }

    public int bookedCount() {
        return allSeats().size() - freeCount();
    }

    /* ------------------------------------------------------------------ *\
     * The smart seat suggestion engine (the recommendation of Stage 4)
     * ------------------------------------------------------------------ */

    /** Maps whatever the profile stores onto one of the five known rules. */
    public static String normalise(String preference) {
        String text = UiUtil.clean(preference);
        String[] known = {PREFERENCE_CENTER, PREFERENCE_AWAY, PREFERENCE_COUPLE,
            PREFERENCE_EXIT, PREFERENCE_ANY};
        for (String option : known) {
            if (option.equalsIgnoreCase(text)) {
                return option;
            }
        }
        return PREFERENCE_ANY;
    }

    /**
     * Picks {@code count} free seats that fit {@code preference} and marks them green.
     *
     * The engine scores a whole unbroken run inside one seating block first (the aisles split a
     * row in three, and nobody wants to sit across an aisle from their friends), and only when no
     * run of that length is left does it fall back to the best single seats. Seats the customer
     * already chose are never suggested again, so pressing the button twice adds seats instead of
     * undoing the choice.
     *
     * @param preference one of Session.SEAT_PREFERENCES; unknown text acts like "No Preference"
     * @return the suggested seats in ticket order, empty when the hall is full
     */
    public List<String> suggest(int count, String preference) {
        String rule = normalise(preference);
        int wanted = Math.max(1, Math.min(MAX_SEATS, count));
        int runLength = PREFERENCE_COUPLE.equals(rule) ? Math.min(2, wanted) : wanted;

        suggested.clear();
        List<String> chosen = new ArrayList<>();
        while (chosen.size() < wanted) {
            int need = Math.min(runLength, wanted - chosen.size());
            List<String> run = bestRun(need, chosen, rule);
            if (run != null) {
                chosen.addAll(run);
            } else {
                String single = bestSingle(chosen, rule);
                if (single == null) {
                    break; // nothing free left - report what was found instead
                }
                chosen.add(single);
                runLength = 1; // too broken up for runs, so take the best seats left
            }
        }
        suggested.addAll(chosen);
        explanation = explain(chosen, rule);
        return suggestion();
    }

    /** The seats the last call to {@link #suggest} marked green. */
    public List<String> suggestion() {
        List<String> sorted = new ArrayList<>(suggested);
        Collections.sort(sorted, SeatMap::byPosition);
        return sorted;
    }

    public boolean isSuggested(String seat) {
        return suggested.contains(seat);
    }

    /** Turns the green seats into the customer's choice. Returns how many seats were added. */
    public int acceptSuggestion() {
        int added = 0;
        for (String seat : suggestion()) {
            if (selected.size() >= MAX_SEATS || !isAvailable(seat) || selected.contains(seat)) {
                continue;
            }
            selected.add(seat);
            added++;
        }
        suggested.clear();
        return added;
    }

    public void clearSuggestion() {
        suggested.clear();
        explanation = "";
    }

    /** The sentence under the buttons that explains WHY these seats were picked. */
    public String explanation() {
        return explanation;
    }

    /**
     * A seat the engine may offer: free, not already chosen by the customer and not already part
     * of this booking, so pressing "suggest" twice keeps adding seats instead of repeating itself.
     */
    private boolean suggestable(String seat, List<String> chosen) {
        return !booked.contains(seat) && !selected.contains(seat) && !chosen.contains(seat);
    }

    /** The best unbroken run of {@code length} seats inside one block of one row. */
    private List<String> bestRun(int length, List<String> chosen, String rule) {
        if (length < 1) {
            return null;
        }
        int[][] blocks = {{0, aisleOne - 1}, {aisleOne, aisleTwo - 1}, {aisleTwo, columns - 1}};
        List<String> best = null;
        double bestScore = Double.MAX_VALUE;
        for (int row = 0; row < rows; row++) {
            for (int block = 0; block < blocks.length; block++) {
                int from = Math.max(blocks[block][0], firstColumnOf(row));
                int to = Math.min(blocks[block][1], lastColumnOf(row));
                for (int start = from; start + length - 1 <= to; start++) {
                    List<String> seats = new ArrayList<>(length);
                    for (int col = start; col < start + length; col++) {
                        String seat = label(row, col);
                        if (!suggestable(seat, chosen)) {
                            seats = null;
                            break;
                        }
                        seats.add(seat);
                    }
                    if (seats != null) {
                        double score = score(row, start, length, rule);
                        if (score < bestScore) {
                            bestScore = score;
                            best = seats;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The single best free seat, used when the hall is too full left for a whole run. */
    private String bestSingle(List<String> chosen, String rule) {
        String best = null;
        double bestScore = Double.MAX_VALUE;
        for (int row = 0; row < rows; row++) {
            for (int col = firstColumnOf(row); col <= lastColumnOf(row); col++) {
                String seat = label(row, col);
                if (!suggestable(seat, chosen)) {
                    continue;
                }
                double score = score(row, col, 1, rule);
                if (score < bestScore) {
                    bestScore = score;
                    best = seat;
                }
            }
        }
        return best;
    }

    /**
     * One rule, one number: the LOWEST score wins, and everything is measured from the middle of
     * the hall, because "the best seat in the house" means the same thing everywhere.
     */
    private double score(int row, int startCol, int length, String rule) {
        double centreRow = (rows - 1) / 2.0;
        double centreCol = (columns - 1) / 2.0;
        double offCentre = Math.abs((startCol + (length - 1) / 2.0) - centreCol);
        switch (rule) {
            case PREFERENCE_AWAY:
                return -row * 7.0 + offCentre * 1.2; // the back rows beat the middle rows
            case PREFERENCE_EXIT:
                return (touchesAisle(row, startCol, length) ? 0 : 40)
                        + row * 0.8 + offCentre * 0.15;
            case PREFERENCE_COUPLE:
                return Math.abs(row - centreRow) * 3.0 + offCentre * 1.6;
            case PREFERENCE_CENTER:
            case PREFERENCE_ANY:
            default:
                return Math.abs(row - centreRow) * 2.5 + offCentre * 1.3;
        }
    }

    private boolean touchesAisle(int row, int startCol, int length) {
        for (int col = startCol; col < startCol + length; col++) {
            if (isAisleSeat(row, col)) {
                return true;
            }
        }
        return false;
    }

    /** Plain English answer to "why these seats?", shown right under the smart seat buttons. */
    private String explain(List<String> seats, String rule) {
        if (seats.isEmpty()) {
            return "No free seat matches \"" + rule + "\" any more - please pick from the map.";
        }
        char firstRow = 'Z';
        char lastRow = 'A';
        for (String seat : seats) {
            char letter = seat.charAt(0);
            firstRow = (char) Math.min(firstRow, letter);
            lastRow = (char) Math.max(lastRow, letter);
        }
        String rowsText = (firstRow == lastRow) ? "in row " + firstRow
                : "in rows " + firstRow + " to " + lastRow;
        String reason;
        switch (rule) {
            case PREFERENCE_AWAY:
                reason = "at the back of the hall, farthest from the screen";
                break;
            case PREFERENCE_COUPLE:
                reason = "side by side and away from the aisles";
                break;
            case PREFERENCE_EXIT:
                reason = "on an aisle, the quickest way out";
                break;
            case PREFERENCE_CENTER:
                reason = "in the middle of the hall, the best view and sound";
                break;
            default:
                reason = "the best free seats left in this hall";
                break;
        }
        return seats.size() + (seats.size() == 1 ? " seat " : " seats ") + rowsText
                + ", " + reason + ".";
    }
}
