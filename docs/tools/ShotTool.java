import cinesmart.CineSmartApp;
import cinesmart.model.Movie;
import cinesmart.model.SeatMap;
import cinesmart.model.ShowTime;
import cinesmart.model.User;
import cinesmart.service.BookingService;
import cinesmart.service.CanteenService;
import cinesmart.service.CatalogueService;
import cinesmart.service.GiftCardService;
import cinesmart.service.LocationService;
import cinesmart.service.PaymentService;
import cinesmart.util.DataStore;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.view.MainFrame;
import cinesmart.view.LocationDialog;
import cinesmart.view.MovieDetailsDialog;
import cinesmart.view.SplashScreen;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Writes PNG screenshots of the finished screens without needing screen-recording permission:
 * each window is made displayable but fully transparent (so nothing flashes on the user's
 * desktop), laid out for real, and then its root pane is painted straight into a BufferedImage.
 *
 * Run:  javac -cp out -d /tmp/cs/shot /tmp/cs/shot/ShotTool.java
 *       java  -cp out:/tmp/cs/shot ShotTool
 */
public class ShotTool {

    private static final String TARGET =
            "/Users/princeraj/Downloads/Movie Ticket AOOP/docs/screenshots";
    private static File folder;

    public static void main(String[] args) throws Exception {
        // Same order as CineSmartApp.start(): the look and feel first, then the palette, otherwise
        // the screenshots would show the look and feel of the machine instead of the shipped one.
        CineSmartApp.useCrossPlatformLookAndFeel();
        Theme.install();
        // Posters first, on this (non-EDT) thread: once they are cached, every card finds its
        // picture instantly and the captures never have to wait for the network.
        warmPosters();
        SwingUtilities.invokeAndWait(ShotTool::capture);
        System.exit(0);
    }

    /**
     * Downloads every film poster before a single window is opened. This is the one place that
     * waits for the network, and it is safe here because it runs off the Event Dispatch Thread:
     * the app itself never does this, it shows the placeholder and repaints when a poster lands.
     */
    private static void warmPosters() {
        for (Movie movie : DataStore.get().getMovies()) {
            if (movie.hasPoster()) {
                cinesmart.util.PosterLoader.load(movie.getPosterUrl(), image -> { });
            }
        }
        // The cast and crew portraits go through the same loader and the same cache, so they are
        // warmed here too: otherwise every credits tile would be captured as an initial.
        for (cinesmart.model.CastMember person : DataStore.get().getCastMembers()) {
            if (person.hasPhoto()) {
                cinesmart.util.PosterLoader.load(person.getPhotoUrl(), image -> { });
            }
        }
        if (cinesmart.util.PosterLoader.awaitIdle(60000)) {
            System.out.println("posters ready: " + cinesmart.util.PosterLoader.cachedCount());
        } else {
            System.out.println("WARNING: some posters did not download - "
                    + cinesmart.util.PosterLoader.pending() + " still running");
        }
    }

    private static void capture() {
        try {
            folder = new File(TARGET);
            folder.mkdirs();

            splash();

            MainFrame frame = new MainFrame();
            reveal(frame, 1180, 760);
            shot(frame, "01-login.png");

            frame.showRegister();
            frame.validate();
            shot(frame, "02-register.png");

            frame.showLogin();
            User demo = DataStore.get().findUser("demo");
            if (demo != null) {
                Session.login(demo);
            }
            frame.openHome();
            frame.validate();
            shot(frame, "03-home-now-showing.png");

            click(frame, "Coming Soon");
            frame.validate();
            shot(frame, "04-home-coming-soon.png");

            click(frame, "Now Showing");
            JTextField search = findSearchField(frame);
            if (search != null) {
                search.setText("dark");
            }
            frame.validate();
            shot(frame, "05-home-search.png");

            if (search != null) {
                search.setText("");
            }
            pickCity(frame, "Kochi");
            frame.validate();
            shot(frame, "06-home-city-kochi.png");

            details(frame);

            seats(frame);

            paymentAndBookings(frame);

            canteen(frame);

            locationOffersAndGiftCards(frame);

            frame.dispose();
            MainFrame adminFrame = new MainFrame();
            reveal(adminFrame, 1180, 760);
            User admin = DataStore.get().findUser("admin");
            if (admin != null) {
                Session.login(admin);
            }
            adminFrame.openHome();
            adminFrame.validate();
            shot(adminFrame, "08-home-admin.png");
            adminFrame.dispose();
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    private static void splash() {
        SplashScreen splash = new SplashScreen();
        reveal(splash, SplashScreen.preferredSplashSize().width,
                SplashScreen.preferredSplashSize().height);
        shot(splash, "00-splash.png");
        splash.dispose();
    }

    private static void details(MainFrame frame) {
        CatalogueService catalogue = new CatalogueService();
        Movie movie = catalogue.movieById("M002");
        if (movie == null) {
            return;
        }
        MovieDetailsDialog dialog = new MovieDetailsDialog(frame, movie,
                Session.selectedCity(), catalogue, show -> {
                });
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        // the real dialog is application-modal; a modeless one so this capture cannot block
        dialog.setModalityType(java.awt.Dialog.ModalityType.MODELESS);
        reveal(dialog, 880, 720);
        shot(dialog, "07-movie-details.png");
        creditsAndReviews(dialog);
        dialog.dispose();
    }

    /**
     * The two blocks added to the details dialog: the credits with their photographs, then the
     * customer reviews and the critic reviews.
     *
     * The About tab is scrolled in four steps rather than captured once, because the cast rows and
     * the review cards cannot both be inside a 720 px window - one screenshot of the top would show
     * the story and nothing else, and one of the bottom would have no cast at all.
     */
    private static void creditsAndReviews(MovieDetailsDialog dialog) {
        JTabbedPane tabs = find(dialog.getContentPane(), JTabbedPane.class);
        if (tabs == null || tabs.getTabCount() < 1) {
            return;
        }
        tabs.setSelectedIndex(0);
        dialog.validate();
        JScrollPane scroll = find(dialog.getContentPane(), JScrollPane.class);
        int deepest = (scroll == null) ? 0 : scroll.getVerticalScrollBar().getMaximum();
        // Four evenly spaced stops from the top of the tab to its very bottom.
        for (int step = 0; step < 4; step++) {
            if (scroll != null) {
                scroll.getVerticalScrollBar().setValue(deepest * step / 3);
                dialog.validate();
            }
            shot(dialog, String.format("20-movie-about-%d.png", step + 1));
        }
    }

    /** The first component of the wanted type anywhere below the given container. */
    private static <T extends java.awt.Component> T find(Container root, Class<T> type) {
        for (java.awt.Component child : root.getComponents()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
            if (child instanceof Container) {
                T found = find((Container) child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Stage 4: the seat map of a real bookable show, then the smart suggestion in green and finally
     * the same seats taken over into the customer's selection. Nothing is held, so the data files
     * are not touched by this capture.
     */
    private static void seats(MainFrame frame) {
        CatalogueService catalogue = new CatalogueService();
        ShowTime show = firstBookableShow(catalogue);
        if (show == null) {
            System.out.println("no bookable show for the seat screenshots");
            return;
        }
        frame.openSeatSelection(show);
        frame.validate();
        shot(frame, "09-seat-selection.png");

        click(frame, "Suggest best seats");
        frame.validate();
        shot(frame, "10-seat-suggestion.png");

        click(frame, "Use these seats");
        frame.validate();
        shot(frame, "11-seat-chosen.png");
    }

    /**
 * Stage 10: opens the canteen the way a customer does - through the Booking menu entry - and
     * captures the hall list with the real distances, that hall's own menu, and a basket with
     * something in it.
     *
     * The basket is filled through the real service and the screen is then refreshed, so the
     * capture shows exactly the state a customer would see after clicking Add a few times. Every
     * field and method it reaches is the real one, not a stand-in.
     */
    private static void canteen(MainFrame frame) {
        // The screen reads the city from Session, so the capture must set it the way the header
        // combo box does - otherwise the area on screen would belong to a different city.
        pickCity(frame, "Bengaluru");
        frame.openCanteen();
        frame.validate();
        shot(frame, "15-canteen-halls.png");

        CanteenService service = new CanteenService();
        try {
            java.lang.reflect.Field panelField =
                    MainFrame.class.getDeclaredField("canteenPanel");
            panelField.setAccessible(true);
            Object panel = panelField.get(frame);

            java.lang.reflect.Field basketField = panel.getClass().getDeclaredField("basket");
            basketField.setAccessible(true);
            CanteenService.Basket basket = (CanteenService.Basket) basketField.get(panel);
            basket.clear();
            basket.add(service.itemOf("ORION2").getItemId(), 2); // Truffle Popcorn x 2
            basket.add(service.itemOf("ORION4").getItemId(), 1); // Cold Brew Coffee

            java.lang.reflect.Field areaField = panel.getClass().getDeclaredField("area");
            areaField.setAccessible(true);
            areaField.set(panel, "Koramangala");

            // refresh() rebuilds the area list for Bengaluru, the hall list with the real
            // distances and the basket bar - exactly what the screen does for a real customer.
            callVoid(panel, "refresh");
        } catch (Exception problem) {
            System.out.println("could not fill the basket for the screenshot: " + problem);
        }
        frame.validate();
        shot(frame, "16-canteen-menu-basket.png");
    }

    /** Calls a private no-argument method of the canteen panel, so the capture uses real code. */
    private static void callVoid(Object panel, String method) throws Exception {
        java.lang.reflect.Method m = panel.getClass().getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(panel);
    }

    /**
     * The location popup, the Offers screen and the Gift Cards wallet.
     *
     * The popup is a modal JDialog, so it is made visible and then captured off its own root pane -
     * the same way every other capture works, just on a different window.
     */
    private static void locationOffersAndGiftCards(MainFrame frame) {
        // The location button only makes sense with a city set, and detection reads the pin that
        // ShotTool itself is started with, so the popup shows a real result rather than a blank.
        pickCity(frame, "Bengaluru");

        LocationDialog picker = new LocationDialog(frame, new LocationService(), false);
        reveal(picker, 460, 560);
        picker.validate();
        shot(picker, "17-location-detect.png");
        picker.dispose();

        frame.openOffers();
        frame.validate();
        shot(frame, "18-offers.png");

        // Buy a real card through the real service so the wallet is not an empty state.
        GiftCardService cards = new GiftCardService();
        if (cards.cardsOfCurrentUser().isEmpty()) {
            cards.buy(2000);
        }
        frame.openGiftCards();
        frame.validate();
        shot(frame, "19-gift-cards.png");

        pickCity(frame, "Kochi");
    }

    /**
     * Stage 5 and Stage 6: the payment screen with a real booking waiting, then My bookings with a paid
 * ticket and a cancelled one.
 *
 * The seats are held through the real services instead of by clicking, because these captures must
 * not pop up the modal "seats held" dialog. Run this tool from a scratch folder (ShotTool uses the
 * working directory for data/), so the project's own data folder is never touched.
 */
    private static void paymentAndBookings(MainFrame frame) {
        CatalogueService catalogue = new CatalogueService();
        BookingService bookings = new BookingService();
        PaymentService payments = new PaymentService();
        List<ShowTime> shows = new ArrayList<>();
        for (Movie movie : catalogue.nowShowing()) {
            shows.addAll(catalogue.bookableShows(movie, Session.selectedCity()));
        }
        if (shows.size() < 2) {
            System.out.println("not enough bookable shows for the payment screenshots");
            return;
        }

        // 1. hold seats on the first show -> the payment screen has something to pay for
        ShowTime first = shows.get(0);
        SeatMap map = bookings.seatMapFor(first);
        for (String seat : map.allSeats()) {
            if (map.isAvailable(seat) && map.toggle(seat) == null) {
                if (map.selectedCount() == 3) {
                    break;
                }
            }
        }
        BookingService.Receipt receipt = bookings.holdSeats(first, map);
        if (receipt == null) {
            System.out.println("could not hold seats for the payment screenshot");
            return;
        }
        // Walk into Stage 5 the way the customer does - through the seat screen's own handover and
        // not by calling MainFrame directly. That way this capture fails loudly if the journey from
        // the seat map to the payment screen is ever broken again.
        frame.openSeatSelection(first);
        frame.validate();
        shot(frame, "12-seat-held-awaiting-payment.png");
        if (!frame.goToPayment()) {
            System.out.println("could not reach the payment screen for the screenshot");
            return;
        }
        frame.validate();
        shot(frame, "12-payment.png");

        // 2. pay with a test card -> the booking becomes the ticket in My bookings
        PaymentService.PaymentResult paid = payments.pay(
                bookings.bookingOf(receipt.getBookingId()),
                new PaymentService.PaymentDetails().method(PaymentService.METHOD_CARD)
                        .cardNumber("4111111111111111").cardHolder("Demo Customer")
                        .cvv("123").expiry("12/30"));
        if (!paid.isSuccess()) {
            System.out.println("payment refused: " + paid.getProblems());
            return;
        }

        // 3. a second booking on another show, still waiting for payment
        ShowTime second = shows.get(1);
        SeatMap map2 = bookings.seatMapFor(second);
        for (String seat : map2.allSeats()) {
            if (map2.isAvailable(seat) && map2.toggle(seat) == null) {
                if (map2.selectedCount() == 2) {
                    break;
                }
            }
        }
        BookingService.Receipt waiting = bookings.holdSeats(second, map2);

        frame.openBookings();
        frame.validate();
        shot(frame, "13-my-bookings.png");

        // 4. cancel it -> the same screen with a grey cancelled card and the refund written out
        if (waiting != null) {
            bookings.cancelBooking(bookings.bookingOf(waiting.getBookingId()));
            frame.openBookings();
            frame.validate();
            shot(frame, "14-booking-cancelled.png");
        }
    }

    /** The first show of the chosen city that is still bookable, so the map has free seats. */
    private static ShowTime firstBookableShow(CatalogueService catalogue) {
        for (Movie movie : catalogue.nowShowing()) {
            List<ShowTime> shows = catalogue.bookableShows(movie, Session.selectedCity());
            if (!shows.isEmpty()) {
                return shows.get(0);
            }
        }
        return null;
    }

    /** Displayable but invisible, so Swing performs a real layout pass without any window showing. */
    private static void reveal(Window window, int width, int height) {
        window.setSize(width, height);
        try {
            // opacity is only allowed on undecorated windows on macOS; the title bar is not part
            // of the captured image anyway, because the root pane is what gets painted.
            if (window instanceof java.awt.Frame) {
                ((java.awt.Frame) window).setUndecorated(true);
            } else if (window instanceof java.awt.Dialog) {
                ((java.awt.Dialog) window).setUndecorated(true);
            }
        } catch (Exception ignored) {
            // already displayable or unsupported - fall through
        }
        try {
            window.setOpacity(0f);
        } catch (Exception ignored) {
            // platform without per-window alpha - setVisible still performs the layout pass
        }
        window.setVisible(true);
        window.validate();
    }

    private static void shot(Window window, String fileName) {
        JRootPane root = rootOf(window);
        int width = window.getWidth();
        int height = window.getHeight();
        if (root != null && root.getWidth() > 0) {
            width = root.getWidth();
            height = root.getHeight();
        }
        if (width <= 0 || height <= 0) {
            System.out.println("SKIP " + fileName + " - nothing to paint");
            return;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        if (root != null) {
            root.paint(graphics);   // menu bar + layered pane, everything Swing draws
        } else {
            window.paint(graphics);
        }
        graphics.dispose();
        try {
            ImageIO.write(image, "png", new File(folder, fileName));
            System.out.println(fileName + " " + width + "x" + height
                    + " distinctColours=" + distinctColours(image));
        } catch (Exception error) {
            System.out.println("FAILED " + fileName + " " + error);
        }
    }

    /** getRootPane lives on the Swing window classes, not on java.awt.Window. */
    private static JRootPane rootOf(Window window) {
        if (window instanceof javax.swing.JFrame) {
            return ((javax.swing.JFrame) window).getRootPane();
        }
        if (window instanceof javax.swing.JDialog) {
            return ((javax.swing.JDialog) window).getRootPane();
        }
        if (window instanceof javax.swing.JWindow) {
            return ((javax.swing.JWindow) window).getRootPane();
        }
        return null;
    }

    /** A blank capture has 1 or 2 distinct colours; a real screen has hundreds. */
    private static int distinctColours(BufferedImage image) {
        Set<Integer> colours = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y += 3) {
            for (int x = 0; x < image.getWidth(); x += 3) {
                colours.add(image.getRGB(x, y));
            }
        }
        return colours.size();
    }

    private static void click(Container root, String buttonText) {
        for (JButton button : all(root, JButton.class)) {
            if (buttonText.equals(button.getText())) {
                button.doClick();
                return;
            }
        }
        System.out.println("no button found: " + buttonText);
    }

    private static void pickCity(Container root, String city) {
        for (JComboBox<?> combo : all(root, JComboBox.class)) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (city.equals(String.valueOf(combo.getItemAt(i)))) {
                    combo.setSelectedItem(city);
                    return;
                }
            }
        }
        System.out.println("no city combo entry: " + city);
    }

    private static JTextField findSearchField(Container root) {
        for (JTextField field : all(root, JTextField.class)) {
            if ("Search films".equals(field.getAccessibleContext().getAccessibleName())) {
                return field;
            }
        }
        System.out.println("no search field found");
        return null;
    }

    private static <T> List<T> all(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T> void collect(Container root, Class<T> type, List<T> found) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) {
                found.add(type.cast(component));
            }
            if (component instanceof JComponent && component instanceof Container) {
                collect((Container) component, type, found);
            }
        }
    }
}

