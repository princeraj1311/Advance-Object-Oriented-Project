package cinesmart.util;


import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

/**
 * Logo is the single place the CineSmart picture is loaded and scaled.
 *
 * Why a separate class? The splash screen, the blue header, the card titles, the dialogs and
 * the taskbar/Dock icon all need the SAME picture at DIFFERENT sizes. Loading the file in five
 * screen classes would be slow and would break as soon as the file moves, so the lookup
 * happens once here and the scaled copies are cached in a HashMap.
 *
 * Where the picture comes from, in this order:
 *   1. -Dcinesmart.logo=/any/path.png   (change the logo for a demo without rebuilding)
 *   2. classpath  /cinesmart/assets/logo.png   (the normal case, works from a jar too)
 *   3. a few paths relative to the project folder (covers running from an IDE without copying
 *      resources into the output folder)
 *   4. a mark drawn with Java2D (rounded blue badge + white play triangle), so the application
 *      still looks finished when the PNG is missing.
 */
public final class Logo {

    /** Inside the jar / output folder, next to the compiled classes. */
    public static final String RESOURCE = "/cinesmart/assets/logo.png";

    /** Used when the program is started with the project folder as its working directory. */
    private static final String[] FILE_FALLBACKS = {
        "src/cinesmart/assets/logo.png",
        "out/cinesmart/assets/logo.png",
        "cinesmart/assets/logo.png",
        "assets/logo.png"
    };

    /** Sizes offered to the window manager so the Dock/taskbar icon is sharp everywhere. */
    private static final int[] WINDOW_ICON_SIZES = {16, 24, 32, 48, 64, 128, 256};

    private static BufferedImage artwork;
    private static boolean loadTried;
    private static String loadedFrom = "Java2D fallback mark";
    private static final Map<String, Image> CACHE = new HashMap<>();

    private Logo() {
        // Utility class.
    }

    /* ------------------------------------------------------------------ *
     * Loading
     * ------------------------------------------------------------------ */

    /** The master picture (null when the file is missing - then the fallback is drawn). */
    public static synchronized BufferedImage artwork() {
        if (!loadTried) {
            loadTried = true;
            artwork = loadFromSystemProperty();
            if (artwork == null) {
                artwork = loadFromClasspath();
            }
            if (artwork == null) {
                artwork = loadFromFileSystem();
            }
        }
        return artwork;
    }

    /** True when a real picture was found (handy for the About box or a quick test). */
    public static boolean isReady() {
        return artwork() != null;
    }

    /** Human readable description of where the picture came from - shown in the About box. */
    public static synchronized String loadedFrom() {
        artwork(); // makes sure the lookup has already happened
        return loadedFrom;
    }

    private static BufferedImage loadFromSystemProperty() {
        String path = System.getProperty("cinesmart.logo");
        if (path == null || path.isBlank()) {
            return null;
        }
        BufferedImage image = read(new File(path));
        if (image != null) {
            loadedFrom = path;
        }
        return image;
    }

    private static BufferedImage loadFromClasspath() {
        try (InputStream in = Logo.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(in);
            if (image != null) {
                loadedFrom = "classpath " + RESOURCE;
            }
            return image;
        } catch (Exception e) {
            System.err.println("Logo: could not read " + RESOURCE + " - " + e.getMessage());
            return null;
        }
    }

    private static BufferedImage loadFromFileSystem() {
        for (String candidate : FILE_FALLBACKS) {
            BufferedImage image = read(new File(candidate));
            if (image != null) {
                loadedFrom = new File(candidate).getAbsolutePath();
                return image;
            }
        }
        return null;
    }

    private static BufferedImage read(File file) {
        try {
            return file.isFile() ? ImageIO.read(file) : null;
        } catch (Exception e) {
            return null; // a broken file must never stop the application from starting
        }
    }

    /* ------------------------------------------------------------------ *
     * Things to put on the screen
     * ------------------------------------------------------------------ */

    /** Plain logo with a transparent background - best on the blue header and splash screen. */
    public static Icon icon(int size) {
        return new LogoIcon(size, false);
    }

    /** Logo on a rounded blue badge - best on white cards and inside dialogs. */
    public static Icon badge(int size) {
        return new LogoIcon(size, true);
    }

    /** The logo plus the word "CineSmart", left aligned - the usual header label. */
    public static JLabel wordmark(int iconSize) {
        JLabel label = new JLabel(" CineSmart", icon(iconSize), SwingConstants.LEFT);
        label.setFont(Theme.titleFont(Math.max(14, iconSize - 6)));
        label.setForeground(Color.WHITE);
        return label;
    }

    /** Scaled copy of the picture at the exact pixel size asked for (cached). */
    public static Image image(int size) {
        return scaled(size, false);
    }

    /**
     * Icons for JFrame.setIconImages(): the operating system picks the size it needs, so all
     * common sizes are offered. This is what makes the Dock/taskbar show the logo instead of the
     * default Java coffee cup.
     */
    public static List<Image> windowIcons() {
        List<Image> images = new ArrayList<>();
        for (int size : WINDOW_ICON_SIZES) {
            images.add(scaled(size, true));
        }
        return images;
    }

    /** size + badge flag is the cache key, so each icon is calculated only once. */
    private static synchronized Image scaled(int size, boolean plate) {
        int side = Math.max(8, size);
        String key = side + (plate ? "-badge" : "-plain");
        Image cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        hints(g2);
        if (plate) {
            Paint oldPaint = g2.getPaint();
            g2.setPaint(Theme.gradient(Theme.BLUE, Theme.BLUE_DARK, false));
            g2.fillRoundRect(0, 0, side - 1, side - 1, side / 4, side / 4);
            g2.setPaint(oldPaint);
            int inner = (int) Math.round(side * 0.74);
            drawMark(g2, (side - inner) / 2, (side - inner) / 2, inner);
        } else {
            drawMark(g2, 0, 0, side);
        }
        g2.dispose();
        CACHE.put(key, canvas);
        return canvas;
    }

    /** Draws the loaded picture, or the Java2D mark when there is no picture. */
    private static void drawMark(Graphics2D g2, int x, int y, int size) {
        BufferedImage master = artwork();
        if (master != null) {
            g2.drawImage(master, x, y, size, size, null);
        } else {
            paintFallbackMark(g2, x, y, size);
        }
    }

    private static void hints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    /**
     * A logo drawn with shapes: a blue rounded square with a white play triangle. Used when the
     * PNG cannot be found, so the screens never show a broken-image icon.
     */
    private static void paintFallbackMark(Graphics2D g2, int x, int y, int size) {
        Paint oldPaint = g2.getPaint();
        g2.setPaint(Theme.gradient(Theme.BLUE, Theme.BLUE_DARK, false));
        g2.fillRoundRect(x, y, size - 1, size - 1, size / 4, size / 4);
        g2.setPaint(oldPaint);
        java.awt.Polygon triangle = new java.awt.Polygon();
        triangle.addPoint(x + size * 38 / 100, y + size * 26 / 100);
        triangle.addPoint(x + size * 38 / 100, y + size * 74 / 100);
        triangle.addPoint(x + size * 76 / 100, y + size * 50 / 100);
        g2.setColor(Color.WHITE);
        g2.fillPolygon(triangle);
    }

    /* ------------------------------------------------------------------ *
     * The Icon itself
     * ------------------------------------------------------------------ */

    /**
     * javax.swing.Icon instead of an ImageIcon because an Icon paints itself at any size with
     * anti-aliasing, which keeps the logo sharp in a menu (16 px) and on the splash screen
     * (90 px) without storing two files.
     */
    public static final class LogoIcon implements Icon {

        private static final long serialVersionUID = 1L;

        private final int size;
        private final boolean plate;

        LogoIcon(int size, boolean plate) {
            this.size = Math.max(8, size);
            this.plate = plate;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            hints(g2);
            g2.drawImage(scaled(size, plate), x, y, null);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
