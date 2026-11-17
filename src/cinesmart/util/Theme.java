package cinesmart.util;


import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.InsetsUIResource;

/**
 * Theme holds every colour and font used by CineSmart.
 *
 * Keeping the light colour scheme in ONE class means a design change is a one-line edit
 * instead of a search through twenty screen classes. {@link #install()} additionally pushes
 * the palette into UIManager so the standard Swing components (JTable, JComboBox, JTabbedPane,
 * JTree, JOptionPane, JFileChooser ...) inherit the same look without any custom painting.
 */
public final class Theme {

    /** Logical font family: "SansSerif" maps to a clean font on Windows, macOS and Linux. */
    public static final String FONT_FAMILY = "SansSerif";

    /* ---------------- surfaces (light theme) ---------------- */
    public static final Color WINDOW_BG = new Color(244, 246, 251);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_SOFT = new Color(248, 250, 254);
    public static final Color BORDER = new Color(221, 227, 240);
    public static final Color HOVER = new Color(236, 243, 255);

    /* ---------------- brand accents ---------------- */
    public static final Color BLUE = new Color(27, 110, 243);
    public static final Color BLUE_DARK = new Color(16, 78, 178);
    public static final Color BLUE_SOFT = new Color(232, 240, 255);
    public static final Color RED = new Color(226, 54, 46);
    public static final Color RED_SOFT = new Color(255, 235, 233);
    public static final Color ORANGE = new Color(247, 146, 15);
    public static final Color ORANGE_SOFT = new Color(255, 244, 224);
    public static final Color GREEN = new Color(16, 150, 86);
    public static final Color GREEN_SOFT = new Color(228, 247, 238);
    public static final Color PURPLE = new Color(122, 79, 216);

    /* ---------------- text ---------------- */
    public static final Color TEXT = new Color(22, 31, 55);
    public static final Color TEXT_MUTED = new Color(112, 124, 152);
    public static final Color TEXT_ON_ACCENT = Color.WHITE;

    /* ---------------- cinema seat states (Stage 4) ---------------- */
    public static final Color SEAT_AVAILABLE = new Color(226, 236, 250);
    public static final Color SEAT_SELECTED = ORANGE;
    public static final Color SEAT_BOOKED = new Color(188, 195, 208);
    public static final Color SEAT_SUGGESTED = new Color(160, 240, 190);
    public static final Color SCREEN_BLUE = new Color(150, 190, 255);

    /**
     * The accent colour can be changed by the user from Settings with a JColorChooser
     * (Stage 10), so it is kept in a field instead of being a constant.
     */
    private static Color accent = BLUE;

    private Theme() {
        // Utility class: no object should ever be created.
    }

    public static Color accent() {
        return accent;
    }

    public static void setAccent(Color color) {
        accent = (color == null) ? BLUE : color;
    }

    /** A darker version of the accent colour, used for button hover states. */
    public static Color accentDark() {
        return accent.darker();
    }

    public static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    public static Font titleFont(int size) {
        return font(Font.BOLD, size);
    }

    /** Normal reading size used by headings inside cards and table cells. */
    public static Font bodyFont() {
        return font(Font.PLAIN, 13);
    }

    /** Small grey helper text (hints, footers, captions). */
    public static Font smallFont() {
        return font(Font.PLAIN, 11);
    }

    /** Small bold text (column captions, the word "Select City"). */
    public static Font smallBoldFont() {
        return font(Font.BOLD, 11);
    }

    /**
     * Paints from one colour into another. horizontal = left to right, otherwise top to
     * bottom. Used by the splash screen, the header band and the seat map screen.
     */
    public static java.awt.Paint gradient(Color from, Color to, boolean horizontal) {
        return new java.awt.GradientPaint(0, 0, from, horizontal ? 1 : 0, horizontal ? 0 : 1, to);
    }

    /**
     * The colours a poster card may use. A film picks one of these using its movieId, so a film
     * always appears in the same colour on the grid, in the details dialog and in Stage 9
     * recommendations - consistency without needing poster image files.
     */
    private static final Color[] POSTER_COLORS = {
        BLUE, PURPLE, new Color(13, 148, 136), ORANGE, RED, BLUE_DARK,
        new Color(190, 24, 93), new Color(21, 128, 61), new Color(202, 138, 4),
        new Color(79, 70, 229), new Color(190, 24, 60), new Color(8, 145, 178)
    };

    /** One poster colour, chosen by the film id so the choice never changes between screens. */
    public static Color posterColor(int seed) {
        return POSTER_COLORS[Math.floorMod(seed, POSTER_COLORS.length)];
    }

    /** A lighter version of the poster colour, for the top of the gradient. */
    public static Color posterColorLight(int seed) {
        return posterColor(seed).brighter();
    }

    /** The soft rounded outline used by every card on screen. */
    public static javax.swing.border.Border cardBorder() {
        return javax.swing.BorderFactory.createLineBorder(BORDER, 1, true);
    }

    /** Convenience: the padding used by almost every card and form on screen. */
    public static Insets cardPadding() {
        return new Insets(18, 20, 18, 20);
    }

    /**
     * Applies the palette to the whole Swing application.
     * Called exactly once from Main, before any window is created.
     */
    public static void install() {
        ColorUIResource window = new ColorUIResource(WINDOW_BG);
        ColorUIResource surface = new ColorUIResource(SURFACE);
        ColorUIResource text = new ColorUIResource(TEXT);
        ColorUIResource white = new ColorUIResource(Color.WHITE);
        ColorUIResource line = new ColorUIResource(BORDER);
        ColorUIResource accentRes = new ColorUIResource(accent);
        FontUIResource body = new FontUIResource(font(Font.PLAIN, 13));
        FontUIResource bold = new FontUIResource(font(Font.BOLD, 13));

        // Backgrounds of the containers.
        String[] windowKeys = {
            "Panel.background", "OptionPane.background", "ToolBar.background",
            "MenuBar.background", "Menu.background", "PopupMenu.background",
            "TabbedPane.background", "SplitPane.background", "ScrollPane.background",
            "Separator.background", "Viewport.background", "RootPane.background",
            "ColorChooser.background", "FileChooser.background", "Tree.background",
            "EditorPane.background"
        };
        for (String key : windowKeys) {
            UIManager.put(key, window);
        }

        // Backgrounds where the user types or where rows are drawn.
        String[] surfaceKeys = {
            "TextField.background", "PasswordField.background", "FormattedTextField.background",
            "TextArea.background", "Table.background", "Spinner.background",
            "ComboBox.background", "TabbedPane.contentAreaColor", "ToolTip.background",
            "List.background"
        };
        for (String key : surfaceKeys) {
            UIManager.put(key, surface);
        }

        // Text colour of labels, inputs, lists, tables, menus and dialogs.
        String[] textKeys = {
            "Label.foreground", "TextField.foreground", "PasswordField.foreground",
            "FormattedTextField.foreground", "TextArea.foreground", "List.foreground",
            "Table.foreground", "Tree.foreground", "ComboBox.foreground", "CheckBox.foreground",
            "RadioButton.foreground", "TabbedPane.foreground", "Menu.foreground",
            "MenuItem.foreground", "Panel.foreground", "Spinner.foreground",
            "OptionPane.foreground", "TitledBorder.titleColor", "MenuBar.foreground",
            "EditorPane.foreground", "ToolTip.foreground"
        };
        for (String key : textKeys) {
            UIManager.put(key, text);
        }

        String[] plainKeys = {
            "Label.font", "TextField.font", "PasswordField.font", "FormattedTextField.font",
            "TextArea.font", "List.font", "Table.font", "Tree.font", "ComboBox.font",
            "Spinner.font", "Slider.font", "ToolTip.font", "OptionPane.font",
            "EditorPane.font"
        };
        String[] boldKeys = {
            "TableHeader.font", "TitledBorder.font", "TabbedPane.font", "Menu.font",
            "MenuItem.font", "MenuBar.font", "Button.font", "CheckBox.font",
            "RadioButton.font"
        };
        for (String key : plainKeys) {
            UIManager.put(key, body);
        }
        for (String key : boldKeys) {
            UIManager.put(key, bold);
        }

        // Highlight colour used by JList, JTable, JTree, JComboBox and the menus.
        String[] selectionKeys = {
            "List.selectionBackground", "Table.selectionBackground", "Tree.selectionBackground",
            "MenuItem.selectionBackground", "ComboBox.selectionBackground",
            "MenuBar.selectionBackground", "TextArea.selectionBackground"
        };
        for (String key : selectionKeys) {
            UIManager.put(key, accentRes);
        }
        String[] selectionTextKeys = {
            "List.selectionForeground", "Table.selectionForeground", "Tree.selectionForeground",
            "MenuItem.selectionForeground", "ComboBox.selectionForeground",
            "MenuBar.selectionForeground"
        };
        for (String key : selectionTextKeys) {
            UIManager.put(key, white);
        }

        // Focus, progress bar and spacing.
        String[] accentKeys = {
            "Slider.focus", "focus", "Button.default.background", "focusHilight"
        };
        for (String key : accentKeys) {
            UIManager.put(key, accentRes);
        }
        UIManager.put("Button.background", surface);
        UIManager.put("control", window);
        UIManager.put("info", new ColorUIResource(ORANGE));
        UIManager.put("Button.margin", new InsetsUIResource(8, 14, 8, 14));
        UIManager.put("CheckBox.margin", new InsetsUIResource(4, 6, 4, 6));
        UIManager.put("RadioButton.margin", new InsetsUIResource(4, 6, 4, 6));
        UIManager.put("TextField.margin", new InsetsUIResource(4, 6, 4, 6));
        UIManager.put("ProgressBar.foreground", accentRes);
        UIManager.put("ProgressBar.background", surface);
        UIManager.put("ProgressBar.selectionBackground", text);
        UIManager.put("ProgressBar.selectionForeground", white);
        UIManager.put("Separator.foreground", line);
        UIManager.put("SplitPane.dividerFocusColor", line);
    }
}

