package cinesmart.view;


import cinesmart.util.Logo;
import cinesmart.util.Theme;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * SplashScreen: a small undecorated window that stays on screen for about two seconds while
 * the program prepares itself, exactly like a mobile application does.
 *
 * A javax.swing.Timer is used instead of Thread.sleep() because the timer fires on the Event
 * Dispatch Thread, so updating the progress bar is safe. Thread.sleep() on the EDT would
 * freeze the whole interface.
 */
public final class SplashScreen extends JWindow {

    private static final int WIDTH = 470;
    private static final int HEIGHT = 300;

    /** The messages shown while the bar moves - one per "phase" of starting up. */
    private static final String[] PHASES = {
        "Loading theme...",
        "Checking data storage...",
        "Preparing login screen...",
        "Welcome!"
    };

    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel phaseLabel = new JLabel(PHASES[0], SwingConstants.CENTER);
    private final Timer timer;

    /** What to do when the two seconds are over (open the main window). */
    private Runnable onFinished;

    public SplashScreen() {
        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(null);

        JPanel background = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(Theme.gradient(Theme.BLUE, Theme.BLUE_DARK, false));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 36));
                g2.fillOval(WIDTH - 120, -70, 190, 190);
                g2.fillOval(-60, HEIGHT - 90, 160, 160);
                g2.dispose();
            }
        };
        setContentPane(background);

        // The same picture as everywhere else: Logo loads and scales it, this only picks a size.
        JLabel logo = new JLabel(Logo.icon(76), SwingConstants.CENTER);
        logo.setBounds(0, 12, WIDTH, 76);
        background.add(logo);

        JLabel title = new JLabel("CineSmart", SwingConstants.CENTER);
        title.setFont(Theme.titleFont(34));
        title.setForeground(Color.WHITE);
        title.setBounds(0, 92, WIDTH, 44);
        background.add(title);

        JLabel subtitle = new JLabel("Movie Ticket Booking & Recommendation System",
                SwingConstants.CENTER);
        subtitle.setFont(Theme.bodyFont());
        subtitle.setForeground(new Color(255, 255, 255, 210));
        subtitle.setBounds(0, 136, WIDTH, 24);
        background.add(subtitle);

        progressBar.setBounds(60, 200, WIDTH - 120, 14);
        progressBar.setOpaque(false);
        progressBar.setStringPainted(true);
        background.add(progressBar);

        phaseLabel.setBounds(0, 224, WIDTH, 22);
        phaseLabel.setFont(Theme.smallFont());
        phaseLabel.setForeground(new Color(255, 255, 255, 220));
        background.add(phaseLabel);

        // 60 ms * 50 steps = about 3 seconds, then the main window is opened.
        timer = new Timer(60, event -> advance());
    }

    /** Starts the animation and remembers what to run at the end. */
    public void start(Runnable onFinished) {
        this.onFinished = onFinished;
        setVisible(true);
        timer.start();
    }

    /** Called by the Swing timer, not by us. */
    private void advance() {
        int value = Math.min(100, progressBar.getValue() + 2);
        progressBar.setValue(value);
        int phase = Math.min(PHASES.length - 1, value / 26);
        phaseLabel.setText(PHASES[phase]);
        if (value >= 100) {
            timer.stop();
            dispose();
            if (onFinished != null) {
                onFinished.run();
            }
        }
    }

    /**
     * Static helper used by the main method: show the splash, then run the callback on the
     * Event Dispatch Thread once the animation has finished.
     */
    public static void showThen(Runnable onFinished) {
        SplashScreen splash = new SplashScreen();
        splash.start(onFinished);
    }

    /** The splash size, exposed so other screens can align themselves with it. */
    public static Dimension preferredSplashSize() {
        return new Dimension(WIDTH, HEIGHT);
    }
}
