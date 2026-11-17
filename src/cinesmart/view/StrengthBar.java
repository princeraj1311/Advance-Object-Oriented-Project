package cinesmart.view;


import cinesmart.util.Theme;
import cinesmart.util.ValidationUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;

/**
 * StrengthBar is a small custom Swing component: it paints a track plus a coloured bar that
 * fills up as the typed password becomes stronger (the score comes from
 * ValidationUtil.passwordStrength, so the rule lives in ONE place).
 *
 * Writing our own paintComponent() instead of using a JProgressBar shows that a component is
 * just "size + painting", and the bar can be re-used by every password field in the project.
 */
public final class StrengthBar extends JComponent {

    private static final int TRACK_HEIGHT = 8;

    /** 0 (weak) to 4 (very strong), exactly the scale used by ValidationUtil. */
    private int score = 0;

    /** The same score as a 0.0 - 1.0 fraction, which is what the bar paints. */
    private double strength = 0.0;

    public StrengthBar() {
        setOpaque(false);
        setPreferredSize(new Dimension(220, TRACK_HEIGHT + 18));
    }

    /** Called by the DocumentListener every time the user types one more character. */
    public void evaluate(String password) {
        score = ValidationUtil.passwordScore(password);
        strength = score / 4.0;
        setToolTipText("Password strength: " + ValidationUtil.passwordStrengthLabel(score));
        repaint();
    }

    public double strength() {
        return strength;
    }

    /** Label text shown under the bar. */
    public String strengthText() {
        if (score == 0) {
            return "Enter a password";
        }
        return ValidationUtil.passwordStrengthLabel(score) + " password";
    }

    private Color strengthColor() {
        if (strength < 0.40) {
            return Theme.RED;
        }
        if (strength < 0.75) {
            return Theme.ORANGE;
        }
        return Theme.GREEN;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int width = getWidth();
        int trackTop = 2;
        g2.setColor(Theme.BORDER);
        g2.fillRoundRect(0, trackTop, width, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);

        int filled = (int) Math.round(width * strength);
        if (filled > 0) {
            g2.setColor(strengthColor());
            g2.fillRoundRect(0, trackTop, filled, TRACK_HEIGHT, TRACK_HEIGHT, TRACK_HEIGHT);
        }

        g2.setColor(Theme.TEXT_MUTED);
        g2.setFont(Theme.smallFont());
        g2.drawString(strengthText(), 0, TRACK_HEIGHT + 14);
        g2.dispose();
    }
}
