package cinesmart.model;


import cinesmart.util.UiUtil;

/**
 * MenuItem is one thing a cinema hall sells at its food counter (Stage 10).
 *
 * Every hall has its OWN menu: the item carries the theatreId it belongs to, so the screen can ask
 * "what does this hall serve?" and the answer is that hall's food, priced the way that hall prices
 * it. Items are never shared between halls, which is what makes two menus different rather than
 * two copies of the same list.
 *
 * Data line layout (6 columns):
 * itemId|theatreId|name|category|price|vegetarian
 *
 * itemId|T002|Butter Popcorn|Popcorn|320|true
 */
public class MenuItem {

    public static final String SEPARATOR_TEXT = "|";
    public static final String SEPARATOR_REGEX = "\\|";
    public static final int FIELD_COUNT = 6;

    /** The sections a menu is grouped into on screen, in the order they are shown. */
    public static final String[] CATEGORIES = {
        "Popcorn", "Beverages", "Snacks", "Combos", "Desserts"
    };

    private String itemId;
    private String theatreId;
    private String name;
    private String category;
    private int price;
    private boolean vegetarian;

    public MenuItem() {
        this.itemId = "";
        this.theatreId = "";
        this.name = "";
        this.category = CATEGORIES[0];
        this.price = 0;
        this.vegetarian = false;
    }

    /** Constructor used by the sample canteen data. */
    public MenuItem(String itemId, String theatreId, String name, String category, int price,
                    boolean vegetarian) {
        this();
        setItemId(itemId);
        setTheatreId(theatreId);
        setName(name);
        setCategory(category);
        setPrice(price);
        setVegetarian(vegetarian);
    }

    /** Constructor used when one line is read back from data/canteen.txt. */
    public MenuItem(String[] parts) {
        this();
        setItemId(part(parts, 0));
        setTheatreId(part(parts, 1));
        setName(part(parts, 2));
        setCategory(part(parts, 3));
        setPrice(toInt(part(parts, 4), 0));
        setVegetarian(Boolean.parseBoolean(part(parts, 5)));
    }

    /* ------------------------------------------------------------------ *\
     * Behaviour
     * ------------------------------------------------------------------ */

    /** "Butter Popcorn" - the name on the menu row. */
    public String displayName() {
        return name;
    }

    /** "Rs 320" - the price on the menu row. */
    public String priceText() {
        return "Rs " + price;
    }

    /** The green/red dot on the menu row. */
    public boolean isVegetarian() {
        return vegetarian;
    }

    /** Small print under the name: "Popcorn \u00b7 veg". */
    public String metaLine() {
        return category + (vegetarian ? " \u00b7 veg" : " \u00b7 non-veg");
    }

    /** Combo boxes show the name only. */
    @Override
    public String toString() {
        return name + "  (" + priceText() + ")";
    }

    /* ------------------------------------------------------------------ *\
     * Saving as one text line (and reading it back)
     * ------------------------------------------------------------------ */

    public String toDataLine() {
        return String.join(SEPARATOR_TEXT, itemId, theatreId, name, category,
                String.valueOf(price), String.valueOf(vegetarian));
    }

    public static MenuItem fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty menu line");
        }
        String[] parts = line.trim().split(SEPARATOR_REGEX, -1);
        if (parts.length < FIELD_COUNT) {
            throw new IllegalArgumentException("a menu line needs " + FIELD_COUNT
                    + " columns, this one has " + parts.length);
        }
        return new MenuItem(parts);
    }

    /* ------------------------------------------------------------------ *\
     * Getters and setters
     * ------------------------------------------------------------------ */

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = UiUtil.clean(itemId);
    }

    public String getTheatreId() {
        return theatreId;
    }

    public void setTheatreId(String theatreId) {
        this.theatreId = UiUtil.clean(theatreId);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = UiUtil.clean(name);
    }

    public String getCategory() {
        return category;
    }

    /** An unknown category falls back to the first one, so a hand-edited file still groups. */
    public final void setCategory(String category) {
        String wanted = UiUtil.clean(category);
        for (String known : CATEGORIES) {
            if (known.equalsIgnoreCase(wanted)) {
                this.category = known;
                return;
            }
        }
        this.category = (wanted.isEmpty()) ? CATEGORIES[0] : wanted;
    }

    public int getPrice() {
        return price;
    }

    /** A negative price is never shown; zero is allowed for a free item. */
    public final void setPrice(int price) {
        this.price = Math.max(0, price);
    }

    public void setVegetarian(boolean vegetarian) {
        this.vegetarian = vegetarian;
    }

    /* ------------------------------------------------------------------ *\
     * Safe conversions
     * ------------------------------------------------------------------ */

    private static String part(String[] parts, int index) {
        if (parts == null || index >= parts.length || parts[index] == null) {
            return "";
        }
        return parts[index].trim();
    }

    private static int toInt(String text, int fallback) {
        try {
            return Integer.parseInt(UiUtil.clean(text));
        } catch (Exception e) {
            return fallback;
        }
    }
}