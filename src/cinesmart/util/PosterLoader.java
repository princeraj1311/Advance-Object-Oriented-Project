package cinesmart.util;


import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * PosterLoader fetches the film posters over the network ONCE and keeps them in two caches.
 *
 * Why a separate class? Every poster on the home grid, in the details dialog and in the
 * recommendations needs the SAME picture, and none of those screens may be allowed to block while
 * a file comes down from the internet. Loading a poster inside a paint method would freeze the
 * window for seconds on a slow college Wi-Fi, so the work happens here once, on a worker thread.
 *
 * Three-step lookup, cheapest first:
 *   1. memory  - the decoded image, already in this JVM (the repeat open of the same film)
 *   2. disk    - data/posters/<name>.jpg, written after the first successful download, so the
 *                second start works with NO internet at all (important for a viva demo)
 *   3. network - TMDB / Wikimedia, only when the first two steps miss
 *
 * Nothing here ever throws at a caller and nothing here ever runs on the Event Dispatch Thread:
 * a failure just means the screen keeps painting its own gradient artwork, so a film without a
 * reachable poster still looks finished.
 *
 * The poster URL lives in the movie's own data line (see Movie), not here, because which picture a
 * film shows is a property of the film. This class only knows how to turn a URL into an image.
 */
public final class PosterLoader {

    /** Pictures come from two hosts: the open TMDB image CDN and Wikipedia's fair-use posters. */
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    /** Decoded images already in this run. */
    private static final Map<String, BufferedImage> MEMORY = new ConcurrentHashMap<>();

    /** URLs that already failed, so a dead link is attempted once and never again. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    /** Set once the "you are offline" note has been printed, so it is printed only once. */
    private static boolean offlineReported;

    /** Downloads running right now - lets a tool (the screenshot script) wait for the pictures. */
    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicInteger();

    /**
     * Screens waiting for a poster that is already being fetched. The home grid, the coming-soon
     * tab, the recommendations and the details dialog all ask for the same film at the same time;
     * they queue here so ONE download serves them all instead of four copies of the same jpg.
     */
    private static final Map<String, List<Consumer<BufferedImage>>> WAITING =
            new ConcurrentHashMap<>();

    /** Worker threads for the downloads - daemon threads so they never keep the app alive. */
    private static final ExecutorService WORKERS =
            Executors.newFixedThreadPool(3, runnable -> {
                Thread thread = new Thread(runnable, "poster-loader");
                thread.setDaemon(true);
                return thread;
            });

    private PosterLoader() {
        // Utility class.
    }

    /* ------------------------------------------------------------------ *
     * The two cache steps that do not touch the network
     * ------------------------------------------------------------------ */

    /**
     * The image if it is already decoded in this run, otherwise null. Never blocks and never
     * downloads, so it is safe to call straight from paintComponent on the EDT.
     */
    public static BufferedImage cached(String url) {
        return url == null || url.isBlank() ? null : MEMORY.get(url.trim());
    }

    /** True when this URL is known to be broken, so the screens skip asking again. */
    public static boolean isKnownBad(String url) {
        return url == null || url.isBlank() || FAILED.contains(url.trim());
    }

    /** How many posters are held in memory - shown in the About box. */
    public static int cachedCount() {
        return MEMORY.size();
    }

    /* ------------------------------------------------------------------ *
     * The full lookup: memory, then disk, then network
     * ------------------------------------------------------------------ */

    /**
     * Returns the poster for a URL as soon as it is available, calling {@code onReady} on the
     * Event Dispatch Thread. Call this from a constructor: it returns immediately, shows the
     * caller's placeholder artwork until the picture arrives, and then repaints.
     *
     * @param url     the poster address, may be blank (then nothing happens at all)
     * @param onReady receives the decoded image, or null when it could not be fetched; always
     *                called on the EDT, and exactly once
     */
    public static void load(String url, Consumer<BufferedImage> onReady) {
        if (url == null || url.isBlank()) {
            deliver(onReady, null);
            return;
        }
        String key = url.trim();

        BufferedImage hit = MEMORY.get(key);
        if (hit != null) {
            deliver(onReady, hit);
            return;
        }
        if (FAILED.contains(key)) {
            deliver(onReady, null); // already tried once, do not hammer a dead host
            return;
        }

        // Join the download that is already running for this address, or start the only one. The flag
        // tells us which of the two happened, and compute() makes that decision atomically.
        final boolean[] startDownload = {false};
        List<Consumer<BufferedImage>> queue = WAITING.compute(key, (ignored, waiting) -> {
            if (waiting == null) {
                startDownload[0] = true;
                return new java.util.concurrent.CopyOnWriteArrayList<>();
            }
            return waiting;
        });
        queue.add(onReady);

        if (!startDownload[0]) {
            return; // somebody else is already fetching this poster and will call us back
        }

        IN_FLIGHT.incrementAndGet();
        WORKERS.execute(() -> {
            try {
                BufferedImage image = fetch(key);
                // Hand the result to every screen that asked for this film.
                List<Consumer<BufferedImage>> waiting = WAITING.remove(key);
                if (waiting != null) {
                    for (Consumer<BufferedImage> callback : waiting) {
                        deliver(callback, image);
                    }
                }
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });
    }

    /** How many poster downloads are still running right now (0 when everything has landed). */
    public static int pending() {
        return IN_FLIGHT.get();
    }

    /**
     * Waits until no poster download is running any more, or until the time runs out. Only useful
     * to a screenshot script, which has to paint the real artwork instead of the placeholder; the
     * application itself never waits and never blocks on this.
     *
     * @return true when everything finished, false if the time ran out first
     */
    public static boolean awaitIdle(long millis) {
        long deadline = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < deadline) {
            if (IN_FLIGHT.get() == 0) {
                return true;
            }
            try {
                Thread.sleep(60);
            } catch (InterruptedException stopped) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return IN_FLIGHT.get() == 0;
    }

    /**
     * Runs the three cache steps off the EDT. Returns null when the poster cannot be had, and
     * remembers the failure so the same broken address is not retried all evening.
     */
    private static BufferedImage fetch(String url) {
        BufferedImage fromDisk = readDiskCache(url);
        if (fromDisk != null) {
            MEMORY.put(url, fromDisk);
            return fromDisk;
        }
        BufferedImage downloaded = download(url);
        if (downloaded != null) {
            MEMORY.put(url, downloaded);
            writeDiskCache(url, downloaded);
            return downloaded;
        }
        FAILED.add(url);
        return null;
    }

    /* ------------------------------------------------------------------ *
     * Step 3 - the network
     * ------------------------------------------------------------------ */

    /**
     * Downloads one poster. TMDB and Wikimedia both answer a plain GET of a file, so there is no
     * JSON to parse and no API key to configure - which is exactly why these two hosts are used.
     * Returns null on any problem at all: no network, 404, timeout, or a body that is not an image.
     */
    private static BufferedImage download(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "CineSmart/1.0 (student project)")
                    .GET()
                    .build();

            HttpResponse<byte[]> response =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                System.err.println("Poster " + response.statusCode() + ": " + url);
                return null;
            }
            // ImageIO returns null for bytes that are not a picture it understands, which is
            // exactly the check we want - an HTML error page must not become a "poster".
            return ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
        } catch (IOException noNetwork) {
            // Being offline is the normal case for this fallback, not a fault worth shouting
            // about: the card simply keeps its own artwork. Say it once, not once per film.
            if (!offlineReported) {
                offlineReported = true;
                System.out.println("No internet - posters cannot be downloaded, "
                        + "the screens will use their own artwork. Cached posters still show.");
            }
            return null;
        } catch (Exception problem) {
            System.err.println("Could not load poster " + url + ": " + problem.getMessage());
            return null;
        }
    }

    /* ------------------------------------------------------------------ *
     * Step 2 - the disk cache (this is what makes the demo work offline)
     * ------------------------------------------------------------------ */

    /**
     * The cache folder: data/posters next to the other data files, so it follows the same rule as
     * DataStore - it is created in the folder the program was started from.
     */
    private static File cacheFolder() {
        File folder = new File(System.getProperty("user.dir"), "data/posters");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            return null; // cannot cache, the app still works, it just downloads again next time
        }
        return folder;
    }

    /** A safe file name built from the URL, so two films can never overwrite each other. */
    private static Path cacheFile(String url) {
        File folder = cacheFolder();
        if (folder == null) {
            return null;
        }
        String tail = lastSegment(url);
        if (tail.toLowerCase().endsWith(".jpg") || tail.toLowerCase().endsWith(".jpeg")) {
            tail = tail.substring(0, tail.lastIndexOf('.')); // avoid RRR_Poster.jpg.jpg
        }
        String name = Integer.toHexString(url.hashCode()) + "-" + tail + ".jpg";
        return new File(folder, name.length() > 90 ? name.substring(0, 90) : name).toPath();
    }

    /** The last part of the address, e.g. "RRR_Poster.jpg" - keeps the file name readable. */
    private static String lastSegment(String url) {
        String tail = url;
        int query = tail.indexOf('?');
        if (query >= 0) {
            tail = tail.substring(0, query); // Wikipedia adds tracking parameters
        }
        int slash = tail.lastIndexOf('/');
        tail = slash >= 0 ? tail.substring(slash + 1) : tail;
        return tail.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static BufferedImage readDiskCache(String url) {
        Path path = cacheFile(url);
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(path)) {
            return ImageIO.read(in);
        } catch (Exception problem) {
            return null; // a half written file simply means "download again"
        }
    }

    private static void writeDiskCache(String url, BufferedImage image) {
        Path path = cacheFile(url);
        if (path == null) {
            return;
        }
        try {
            Path temp = path.resolveSibling(path.getFileName() + ".part");
            ImageIO.write(image, "jpg", temp.toFile());
            // Write to a .part file and move it into place, so a crash mid-write can never leave
            // a truncated jpg behind that the next start would happily try to decode.
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception problem) {
            System.err.println("Could not cache poster " + url + ": " + problem.getMessage());
        }
    }

    /* ------------------------------------------------------------------ *
     * Drawing
     * ------------------------------------------------------------------ */

    /**
     * Draws a poster so it FILLS the given area without stretching: the picture is scaled until it
     * covers the box and the overflow is cut off evenly, which is what every streaming site does
     * with a portrait poster on a landscape tile.
     */
    public static void paintCover(Graphics2D g2, BufferedImage poster, int x, int y,
                                 int width, int height) {
        double scale = Math.max(width / (double) poster.getWidth(),
                height / (double) poster.getHeight());
        int drawWidth = (int) Math.round(poster.getWidth() * scale);
        int drawHeight = (int) Math.round(poster.getHeight() * scale);
        int drawX = x + (width - drawWidth) / 2;
        int drawY = y + (height - drawHeight) / 2;
        g2.drawImage(poster, drawX, drawY, drawWidth, drawHeight, null);
    }

    /** Hands the result to a caller on the EDT - the only place a screen may be touched. */
    private static void deliver(Consumer<BufferedImage> onReady, BufferedImage image) {
        if (onReady == null) {
            return;
        }
        if (SwingUtilities.isEventDispatchThread()) {
            onReady.accept(image);
        } else {
            SwingUtilities.invokeLater(() -> onReady.accept(image));
        }
    }
}