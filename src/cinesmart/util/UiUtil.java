package cinesmart.util;


import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.text.MaskFormatter;

/**
 * UiUtil = reusable helpers for the screens.
 *
 * Every panel needs the same labels, text fields and message dialogs, so the code is
 * written once here instead of being copied into each screen. This also keeps the screen
 * classes short enough to read and to explain.
 */
public final class UiUtil {

    private UiUtil() {
        // Utility class.
    }

    /* ------------------------------------------------------------------ *
     * Labels
     * ------------------------------------------------------------------ */

    /** Big bold screen title such as "Login to CineSmart". */
    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.titleFont(22));
        label.setForeground(Theme.TEXT);
        return label;
    }

    /** Small grey explanation line under a heading. */
    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 13));
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    /** Bold label placed above a form field. */
    public static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 12));
        label.setForeground(Theme.TEXT);
        return label;
    }

    /** Plain grey helper text. */
    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.PLAIN, 12));
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    /** Centre aligned grey text (used for "or" separators and footers). */
    public static JLabel centered(String text) {
        JLabel label = muted(text);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    /** Small hint label under a form field. */
    public static JLabel hint(String text) {
        JLabel label = new JLabel((text == null || text.isEmpty()) ? " " : text);
        label.setFont(Theme.font(Font.PLAIN, 11));
        label.setForeground(Theme.TEXT_MUTED);
        return label;
    }

    /** Sets hint text plus colour in one call: ok = green, otherwise red. */
    public static void setHint(JLabel hintLabel, String text, boolean ok) {
        hintLabel.setText("<html>&nbsp;" + text + "</html>");
        hintLabel.setForeground(ok ? Theme.GREEN : Theme.RED);
    }

    /**
     * Helper text that wraps at a fixed pixel width and re-measures itself every time it is set.
     *
     * A JLabel with HTML does not really wrap: Swing lays the paragraph out at its own preferred
     * width and paints it over whatever stands next to it, which is how a long sentence ends up cut
     * off at the edge of a card. A read only text area wraps exactly inside the width it is given.
     */
    public static Paragraph paragraph(String text, Font font, Color color, int width) {
        return new Paragraph(text, font, color, width);
    }

    /** What {@link #paragraph} returns; call setText again after the words change. */
    public static final class Paragraph extends JTextArea {

        private static final long serialVersionUID = 1L;

        private final int width;

        private Paragraph(String text, Font font, Color color, int width) {
            super(text);
            this.width = width;
            setFont(font);
            setForeground(color);
            setLineWrap(true);
            setWrapStyleWord(true);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setBorder(null);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            fit();
        }

        /** Sets new words and grows or shrinks to the number of lines they need. */
        @Override
        public void setText(String text) {
            super.setText(text);
            if (width > 0) {
                fit();
            }
        }

        /** A text area with line wrap reports the height of the text at its current width. */
        private void fit() {
            // Forget the height measured last time: getPreferredSize would hand it straight back.
            setPreferredSize(null);
            setSize(width, Short.MAX_VALUE);
            Dimension size = new Dimension(width, getPreferredSize().height);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }
    }

    /* ------------------------------------------------------------------ *
     * Input components
     * ------------------------------------------------------------------ */

    public static JTextField textField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setBorder(inputBorder());
        return field;
    }

    public static JPasswordField passwordField(int columns) {
        JPasswordField field = new JPasswordField(columns);
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setBorder(inputBorder());
        return field;
    }

    public static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setFont(Theme.font(Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(inputBorder());
        return area;
    }

    /** The soft rounded border used by every text field in the application. */
    public static Border inputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 9, 6, 9));
    }

    /** Draws a red border around a field whose value is invalid. */
    public static void markInvalid(JComponent component, boolean invalid) {
        component.setBorder(invalid
                ? BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(Theme.RED, 2, true),
                        BorderFactory.createEmptyBorder(5, 8, 5, 8))
                : inputBorder());
    }

    /* ------------------------------------------------------------------ *
     * Buttons (one definition each = the same look on every screen)
     * ------------------------------------------------------------------ */

    /** The main call-to-action button: Login, Register, Book Now, Pay Now. */
    public static JButton primaryButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.titleFont(13));
        button.setBackground(Theme.accent());
        button.setForeground(Color.WHITE);
        styleButton(button, action, null);
        return button;
    }

    /** Secondary button with the same size but a plain background. */
    public static JButton outlineButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.titleFont(13));
        button.setBackground(Theme.SURFACE);
        button.setForeground(Theme.TEXT);
        styleButton(button, action, BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true), padded(9, 16, 9, 16)));
        return button;
    }

    /** Destructive action: Cancel Booking, Delete, Logout. */
    public static JButton dangerButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.titleFont(13));
        button.setBackground(Theme.RED);
        button.setForeground(Color.WHITE);
        styleButton(button, action, null);
        return button;
    }

    /**
     * A button that sits on the blue header band.
     *
     * The header is a gradient, so a button there needs a translucent white background and a white
     * outline or it would look like a grey box pasted onto the picture. This is the one place that
     * knows that, which is why the header buttons all go through it.
     */
    public static JButton headerButton(String text, ActionListener action) {
        JButton button = new JButton(text);
        button.setFont(Theme.titleFont(13));
        button.setForeground(Color.WHITE);
        // Opaque, not transparent: a translucent background is only painted when the button is
        // opaque, and a transparent one would show the gradient straight through the label.
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(new Color(255, 255, 255, 45));
        button.setDisabledIcon(null);
        styleButton(button, action, BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 120), 1, true),
                padded(7, 14, 7, 14)));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(255, 255, 255, 80));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(255, 255, 255, 45));
            }
        });
        return button;
    }

    /** The behaviour every button shares: comfortable padding and a pointing hand. */
    private static void styleButton(JButton button, ActionListener action, Border border) {
        button.setFocusPainted(false);
        button.setBorder((border == null) ? padded(9, 18, 9, 18) : border);
        if (action != null) {
            button.addActionListener(action);
        }
        muteWhenDisabled(button);
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        });
    }

    /**
     * Greys the whole button while it is disabled. The look and feel greys only the text of a
     * disabled button and paints our own background unchanged, which made a disabled "Hold these
     * seats" look as clickable as an enabled one.
     */
    private static void muteWhenDisabled(JButton button) {
        Color background = button.getBackground();
        Color foreground = button.getForeground();
        button.addPropertyChangeListener("enabled", event -> {
            boolean enabled = Boolean.TRUE.equals(event.getNewValue());
            button.setBackground(enabled ? background : Theme.SURFACE_SOFT);
            button.setForeground(enabled ? foreground : Theme.TEXT_MUTED);
        });
    }

    /**
     * MaskFormatter for a fixed number of digits, e.g. a 10 digit mobile number.
     * MaskFormatter's constructor throws ParseException, so we handle it here and return
     * null (the caller then simply uses a normal JTextField).
     */
    public static MaskFormatter digitsMask(int digitCount) {
        try {
            StringBuilder pattern = new StringBuilder();
            for (int i = 0; i < digitCount; i++) {
                pattern.append('#'); // '#' means "one mandatory digit"
            }
            MaskFormatter formatter = new MaskFormatter(pattern.toString());
            formatter.setAllowsInvalid(false);
            formatter.setOverwriteMode(true);
            return formatter;
        } catch (Exception e) {
            System.err.println("Could not build digit mask: " + e.getMessage());
            return null;
        }
    }

    /**
     * Mask for a date typed as 2003-05-14 (year-month-day, the ISO order Java can parse
     * directly). A MaskFormatter gives the shape of a correct answer for free, so the user
     * cannot even type the parts in the wrong order.
     */
    public static MaskFormatter dateMask() {
        try {
            MaskFormatter formatter = new MaskFormatter("####-##-##");
            formatter.setAllowsInvalid(false);
            formatter.setOverwriteMode(true);
            return formatter;
        } catch (Exception e) {
            System.err.println("Could not build the date mask: " + e.getMessage());
            return null;
        }
    }

    /** A text field that accepts exactly ten digits - the mobile number shape. */
    public static JFormattedTextField mobileField() {
        MaskFormatter mask = digitsMask(10);
        JFormattedTextField field = new JFormattedTextField((MaskFormatter) mask);
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setBorder(inputBorder());
        field.setColumns(14);
        return field;
    }

    /** A text field that only accepts a yyyy-MM-dd date. Falls back to a plain field. */
    public static JFormattedTextField dateField() {
        MaskFormatter mask = dateMask();
        JFormattedTextField field = new JFormattedTextField((MaskFormatter) mask);
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setBorder(inputBorder());
        field.setColumns(12);
        return field;
    }

    /** Reads the digits typed into a JFormattedTextField (mask placeholders removed). */
    public static String formattedDigits(Component component) {
        if (component instanceof javax.swing.JFormattedTextField) {
            Object value = ((javax.swing.JFormattedTextField) component).getValue();
            String text = (value == null) ? "" : value.toString();
            return text.replaceAll("[^0-9]", "");
        }
        if (component instanceof JTextField) {
            return ((JTextField) component).getText().replaceAll("[^0-9]", "");
        }
        return "";
    }

    /** Method overloading #1: plain trimmed text of a text field. */
    public static String textOf(JTextField field) {
        return (field == null) ? "" : field.getText().trim();
    }

    /** Method overloading #2: password field text (spaces inside a password are kept). */
    public static String textOf(JPasswordField field) {
        return (field == null) ? "" : new String(field.getPassword());
    }

    /* ------------------------------------------------------------------ *
     * Layout helpers
     * ------------------------------------------------------------------ */

    /** Invisible vertical space of the given size (used between form sections). */
    public static java.awt.Component gap(int pixels) {
        return Box.createVerticalStrut(pixels);
    }

    /** Invisible space that pushes components apart inside a vertical Box. */
    public static java.awt.Component verticalGlue() {
        return Box.createVerticalGlue();
    }

    /** Invisible space that pushes components apart inside a horizontal Box. */
    public static java.awt.Component horizontalGlue() {
        return Box.createHorizontalGlue();
    }

    public static Border padded(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    /** A titled box border, used to group related fields inside a form. */
    public static Border titled(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                title,
                javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION,
                javax.swing.border.TitledBorder.DEFAULT_POSITION,
                Theme.titleFont(13),
                Theme.TEXT);
    }

    /* ------------------------------------------------------------------ *
     * JOptionPane wrappers (one place = consistent dialogs everywhere)
     * ------------------------------------------------------------------ */

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "CineSmart",
                JOptionPane.INFORMATION_MESSAGE);
    }

    /** About dialog: shows the CineSmart logo instead of the standard Swing info icon. */
    public static void about(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "About CineSmart",
                JOptionPane.PLAIN_MESSAGE, Logo.badge(56));
    }

    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "CineSmart",
                JOptionPane.WARNING_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "CineSmart",
                JOptionPane.ERROR_MESSAGE);
    }

    /** Shows several validation problems in ONE dialog instead of one at a time. */
    public static void showProblems(Component parent, String title, java.util.List<String> problems) {
        StringBuilder text = new StringBuilder("<html><b>" + title + "</b><br><br>");
        for (String problem : problems) {
            text.append("&bull; ").append(problem).append("<br>");
        }
        JOptionPane.showMessageDialog(parent, text.toString(), "CineSmart",
                JOptionPane.ERROR_MESSAGE);
    }

    /** Yes / No question used before cancelling a booking, logging out, etc. */
    public static boolean confirm(Component parent, String question) {
        int choice = JOptionPane.showConfirmDialog(parent, question, "CineSmart",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Short cut so screen code can write UiUtil.clean(...) instead of the long name. */
    public static String clean(String value) {
        return ValidationUtil.clean(value);
    }

    /** Places a window in the middle of the screen. */
    public static void center(Window window) {
        window.setLocationRelativeTo(null);
    }

    public static void paintBackground(JComponent component, Color color) {
        component.setOpaque(true);
        component.setBackground(color);
    }
}

