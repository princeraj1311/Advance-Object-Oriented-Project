package cinesmart;


import cinesmart.util.Theme;
import cinesmart.view.MainFrame;
import cinesmart.view.SplashScreen;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Entry point of CineSmart.
 *
 * Order matters here:
 *  1. run everything Swing on the Event Dispatch Thread (invokeLater), otherwise the look and
 *     feel may be read from the wrong thread and the window can freeze;
 *  2. pick one look and feel so the program renders identically on every operating system;
 *  3. apply our colour palette (Theme.install) BEFORE any window is created;
 *  4. show the splash screen, then the single MainFrame with the login card on top.
 */
public final class CineSmartApp {

    private CineSmartApp() {
        // Main classes are never instantiated.
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(CineSmartApp::start);
    }

    /** Runs on the Event Dispatch Thread. */
    private static void start() {
        useCrossPlatformLookAndFeel();
        Theme.install();
        SplashScreen.showThen(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            frame.toFront();
        });
    }

    /**
     * The system look and feel differs per operating system, which makes screenshots and
     * viva demos inconsistent. Metal (the Java look and feel) looks the same everywhere.
     *
     * Public because docs/tools/ShotTool.java installs the same one before it screenshots a
     * window - a picture of a screen must look like the screen the customer really sees.
     */
    public static void useCrossPlatformLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Could not set the look and feel: " + e.getMessage());
        }
    }
}
