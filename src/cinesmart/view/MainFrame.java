package cinesmart.view;


import cinesmart.model.Booking;
import cinesmart.model.FoodOrder;
import cinesmart.model.Notification;
import cinesmart.model.ShowTime;
import cinesmart.service.AuthResult;
import cinesmart.service.BookingService;
import cinesmart.service.CanteenService;
import cinesmart.service.CatalogueService;
import cinesmart.service.GiftCardService;
import cinesmart.service.LocationService;
import cinesmart.service.OfferService;
import cinesmart.service.PaymentService;
import cinesmart.service.OpenStreetMapCinemaService;
import cinesmart.service.RemoteMovieDataService;
import cinesmart.service.ShowScheduleService;
import cinesmart.service.UserService;
import cinesmart.util.AppConfig;
import cinesmart.util.DataStore;
import cinesmart.util.EventBus;
import cinesmart.util.Logo;
import cinesmart.util.NotificationEvent;
import cinesmart.util.SeatEvent;
import cinesmart.util.Session;
import cinesmart.util.Theme;
import cinesmart.util.UiUtil;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * MainFrame is the ONLY window of the application. Screens are not separate frames: they are
 * JPanels kept inside a CardLayout, so "navigation" simply means showing one card - quick,
 * flicker free, and one window to manage.
 *
 * Stage 2 held three cards (login, register, home dashboard) and Stage 4 added the seat map, all
 * by calling cards.add(panel, NAME) in the constructor below, which is why the names are
 * constants instead of scattered string literals.
 *
 * The header carries the city selector for the whole application: changing it calls
 * DashboardPanel.refresh(), so the dashboard, the cinema list and the stats always agree with
 * the city shown at the top of the window.
 */
public final class MainFrame extends javax.swing.JFrame {

    private static final long serialVersionUID = 1L;

    public static final String CARD_LOGIN = "login";
    public static final String CARD_REGISTER = "register";
    /** Stage 2: the home dashboard, the card both roles land on after a successful login. */
    public static final String CARD_HOME = "home";
    /** Stage 4: the seat map of the show that was picked. */
    public static final String CARD_SEATS = "seats";
    /** Stage 5: paying for the held seats and getting the ticket. */
    public static final String CARD_PAYMENT = "payment";
    /** Stage 5 + Stage 6: the customer's own tickets, with the cancel button. */
    public static final String CARD_BOOKINGS = "bookings";
    /** Stage 10: the food menu of the nearest cinema hall, and the order. */
    public static final String CARD_CANTEEN = "canteen";
    /** The running discounts of the selected city. */
    public static final String CARD_OFFERS = "offers";
    /** The prepaid cards of the signed in customer. */
    public static final String CARD_GIFT_CARDS = "giftcards";

    private final java.awt.CardLayout cardLayout = new java.awt.CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final UserService userService = new UserService();
    private final CatalogueService catalogueService = new CatalogueService();
    /** Stage 4 owns its own service; the card and the service share the same DataStore. */
    private final BookingService bookingService = new BookingService();
    /** Stage 5 takes the money; it shares the same DataStore through its own service. */
    private final PaymentService paymentService = new PaymentService();
    /** Stage 11: rolls the show calendar forward day by day. */
    private final ShowScheduleService showScheduleService = new ShowScheduleService();
    private final RemoteMovieDataService remoteMovieDataService = new RemoteMovieDataService();
    private final OpenStreetMapCinemaService openStreetMapCinemaService =
            new OpenStreetMapCinemaService();
    /** Stage 11: daily timer - regenerates tomorrow's shows and archives empty past shows. */
    private Timer dailyTimer;
    /** Feature 1: sweeps expired seat holds every 60 seconds. */
    private Timer holdExpiryTimer;
    /** Stage 10: the food menus of the halls and the order the customer places. */
    private final CanteenService canteenService = new CanteenService();
    /** Location, offers and gift cards: detection, coupons and prepaid payment. */
    private final LocationService locationService = new LocationService();
    private final OfferService offerService = new OfferService();
    private final GiftCardService giftCardService = new GiftCardService();

    private JLabel statusLabel;
    private String tmdbErrorStatus = "";
    private JButton notificationBell;
    private javax.swing.JPopupMenu notificationDropdown;
    private javax.swing.JWindow notificationToast;
    private javax.swing.Timer notificationToastTimer;
    private final EventBus.Subscriber<NotificationEvent> notificationSubscriber =
            this::onNotificationEvent;
    private JComboBox<String> cityCombo;
    /** The three header buttons: location, Offers and Gift Cards (see buildHeader). */
    private javax.swing.JButton locationButton;
    private javax.swing.JButton offersButton;
    private javax.swing.JButton giftCardsButton;
    private DashboardPanel dashboard;
    private SeatSelectionPanel seatPanel;
    private PaymentPanel paymentPanel;
    private BookingsPanel bookingsPanel;
    private CanteenPanel canteenPanel;
    private OffersPanel offersPanel;
    private GiftCardsPanel giftCardsPanel;
    /** Remembered so the Booking menu can return to the map of the show that is already open. */
    private ShowTime lastPickedShow;

    /** The two role dependent menus; see {@link #updateMenus()}. */
    private JMenu bookingMenu;
    private JMenu manageMenu;

    public MainFrame() {
        super("CineSmart - Movie Ticket Booking & Recommendation System");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        // Dock / taskbar icon: the operating system chooses the size it needs from this list.
        setIconImages(Logo.windowIcons());
        setJMenuBar(buildMenuBar());
        add(buildHeader(), BorderLayout.NORTH);

        cards.setBackground(Theme.WINDOW_BG);
        cards.add(new LoginPanel(this, userService), CARD_LOGIN);
        cards.add(new RegisterPanel(this, userService), CARD_REGISTER);
        dashboard = new DashboardPanel(this, catalogueService);
        cards.add(dashboard, CARD_HOME);
        seatPanel = new SeatSelectionPanel(this, catalogueService, bookingService);
        cards.add(seatPanel, CARD_SEATS);
        paymentPanel = new PaymentPanel(this, bookingService, paymentService);
        cards.add(paymentPanel, CARD_PAYMENT);
        bookingsPanel = new BookingsPanel(this, bookingService);
        cards.add(bookingsPanel, CARD_BOOKINGS);
        canteenPanel = new CanteenPanel(this, canteenService);
        cards.add(canteenPanel, CARD_CANTEEN);
        offersPanel = new OffersPanel(this, offerService);
        cards.add(offersPanel, CARD_OFFERS);
        giftCardsPanel = new GiftCardsPanel(this, giftCardService);
        cards.add(giftCardsPanel, CARD_GIFT_CARDS);
        add(cards, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1180, 760));
        pack();
        setMinimumSize(new Dimension(960, 640));
        UiUtil.center(this);

        // Stage 11: self-heal the show calendar on startup (covers "server was off for days"),
        // then archive empty past shows. generateShows is idempotent, so running it every start
        // is safe.
        int migratedSchedules = showScheduleService.migrateUnsupportedLegacySchedules();
        int remoteSchedules = showScheduleService.ensureRemoteMovieSchedules();
        int retiredLegacyShows = showScheduleService.retireUnbookedLegacySlots();
        int created = showScheduleService.generateShows();
        int archived = showScheduleService.archivePastShows();
        System.out.println("Show calendar: generated " + created + " shows, added "
                + remoteSchedules + " live movie schedules, retired " + retiredLegacyShows
                + " unbooked legacy shows, migrated " + migratedSchedules
                + " legacy schedules, archived " + archived + " past shows.");

        // Daily sweep at 00:05 (configurable): generate the next day's shows and archive the
        // day that just finished. Keeps the calendar rolling without a restart.
        startDailyTimer();

        // Sweep expired seat holds every 60 seconds so held seats return to AVAILABLE.
        startHoldExpiryTimer();

        EventBus.subscribe(NotificationEvent.class, notificationSubscriber);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                EventBus.unsubscribe(NotificationEvent.class, notificationSubscriber);
                if (notificationToastTimer != null) {
                    notificationToastTimer.stop();
                }
                if (dailyTimer != null) {
                    dailyTimer.cancel();
                }
                if (holdExpiryTimer != null) {
                    holdExpiryTimer.cancel();
                }
            }
        });

        showLogin();
        refreshRemoteMovieData();
        refreshOpenStreetMapListings();
    }

    /** Starts the daily regeneration/archive timer. Runs once a day at the configured hour:min. */
    private void startDailyTimer() {
        if (dailyTimer != null) {
            dailyTimer.cancel();
        }
        dailyTimer = new Timer("CineSmart-daily-sweep", true);
        dailyTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    int created = showScheduleService.generateShows();
                    int archived = showScheduleService.archivePastShows();
                    System.out.println("Stage 11 daily sweep: generated " + created
                            + " new shows, archived " + archived + " empty past shows.");
                } catch (Exception e) {
                    System.err.println("Daily sweep failed: " + e.getMessage());
                }
            }
        }, firstRunTime(), 24 * 60 * 60 * 1000L); // repeat every 24 hours
    }

    /** Sweeps expired seat holds every 60 seconds (Feature 1 auto-release). */
    private void startHoldExpiryTimer() {
        if (holdExpiryTimer != null) {
            holdExpiryTimer.cancel();
        }
        holdExpiryTimer = new Timer("CineSmart-hold-expiry", true);
        holdExpiryTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    int released = DataStore.get().expireHolds();
                    if (released > 0) {
                        System.out.println("Feature 1: released " + released
                                + " expired seat holds.");
                        // Notify any open seat map that holds changed.
                        EventBus.post(new SeatEvent(SeatEvent.Type.HOLDS_EXPIRED, null));
                    }
                } catch (Exception e) {
                    System.err.println("Hold expiry sweep failed: " + e.getMessage());
                }
            }
        }, 0, 60 * 1000L); // immediate, then every 60 seconds
    }

    /** Stops the daily timer - called when the window closes. */
    public void stopDailyTimer() {
        if (dailyTimer != null) {
            dailyTimer.cancel();
        }
    }

    /** Stops the hold-expiry timer - called when the window closes. */
    public void stopHoldExpiryTimer() {
        if (holdExpiryTimer != null) {
            holdExpiryTimer.cancel();
        }
    }

    /** Milliseconds until the next configured hour:minute (e.g. 00:05). */
    private long firstRunTime() {
        LocalTime target = LocalTime.of(AppConfig.DAILY_GEN_HOUR, AppConfig.DAILY_GEN_MINUTE);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime first = LocalDateTime.of(now.toLocalDate(), target);
        if (!first.isAfter(now)) {
            first = first.plusDays(1);
        }
        return java.time.Duration.between(now, first).toMillis();
    }

    /* ------------------------------------------------------------------ *
     * Navigation (the only way screens change)
     * ------------------------------------------------------------------ */

    public void showCard(String name) {
        cardLayout.show(cards, name);
    }

    public void showLogin() {
        statusLabel.setText("Please sign in to continue");
        updateMenus();
        showCard(CARD_LOGIN);
    }

    public void showRegister() {
        statusLabel.setText("Create a new customer account");
        showCard(CARD_REGISTER);
    }

    /** Called by LoginPanel after the service said the password is correct. */
    public void loginSucceeded(AuthResult result) {
        Session.login(result.getUser());
        openHome();
    }

    /**
     * Stage 2 replaced the Stage 1 welcome card with the real dashboard. Both roles land on the
     * same screen - an admin browses the catalogue read-only and manages it from the Manage menu
     * in Stage 8 - so logging in only means greeting the user and reloading the data.
     */
    public void openHome() {
        // Feature 1: releasing holds when leaving the payment page.
        if (Session.currentUser() != null) {
            DataStore.get().releaseHoldsForUser(Session.currentUser().getUserName());
        }
        statusLabel.setText(Session.isAdmin()
                ? "Signed in as admin " + Session.firstName() + " - browse read-only"
                : Session.currentUser().getWelcomeMessage());
        if (!tmdbErrorStatus.isEmpty()) {
            statusLabel.setText(tmdbErrorStatus);
        }
        updateMenus();
        dashboard.refresh();
        showCard(CARD_HOME);
    }

    /** Feature 3: toggles the notification dropdown. */
    private void toggleNotificationDropdown() {
        if (notificationDropdown == null) {
            notificationDropdown = new javax.swing.JPopupMenu();
        }
        notificationDropdown.removeAll();
        if (Session.currentUser() == null) {
            notificationDropdown.add(new javax.swing.JLabel("Sign in to see notifications"));
        } else {
            String userName = Session.currentUser().getUserName();
            java.util.List<cinesmart.model.Notification> list =
                    DataStore.get().getNotificationsForUser(userName);
            if (list.isEmpty()) {
                notificationDropdown.add(new javax.swing.JLabel("No notifications"));
            } else {
                for (cinesmart.model.Notification n : list) {
                    javax.swing.JMenuItem item = new javax.swing.JMenuItem(
                            (n.isRead() ? "" : "• ") + n.getMessage());
                    item.setEnabled(true);
                    final String nid = n.getNotificationId();
                    item.addActionListener(e -> DataStore.get().markNotificationRead(nid));
                    notificationDropdown.add(item);
                }
                notificationDropdown.addSeparator();
                javax.swing.JMenuItem markAll = new javax.swing.JMenuItem("Mark all as read");
                markAll.addActionListener(e -> DataStore.get().markAllReadForUser(userName));
                notificationDropdown.add(markAll);
            }
        }
        refreshNotificationBell();
        notificationDropdown.show(notificationBell, 0, notificationBell.getHeight());
    }

    private void refreshNotificationBell() {
        int unread = Session.currentUser() == null ? 0
                : DataStore.get().unreadCountForUser(Session.currentUser().getUserName());
        notificationBell.setText("🔔" + (unread > 0 ? " (" + unread + ")" : ""));
    }

    private void onNotificationEvent(NotificationEvent event) {
        Notification notification = event == null ? null : event.notification;
        if (notification == null || Session.currentUser() == null
                || !notification.getUserId().equalsIgnoreCase(
                        Session.currentUser().getUserName())) {
            return;
        }
        refreshNotificationBell();
        if (notification.getType() == Notification.Type.BOOKING_CONFIRMED
                || notification.getType() == Notification.Type.BOOKING_CANCELLED) {
            showNotificationToast(notification.getMessage());
        }
    }

    private void showNotificationToast(String message) {
        if (notificationToast != null) {
            notificationToast.dispose();
        }
        notificationToast = new javax.swing.JWindow(this);
        javax.swing.JPanel panel = new javax.swing.JPanel(new BorderLayout());
        panel.setBackground(Theme.SURFACE);
        panel.setBorder(javax.swing.BorderFactory.createCompoundBorder(Theme.cardBorder(),
                UiUtil.padded(14, 18, 14, 18)));
        javax.swing.JLabel label = new javax.swing.JLabel(message);
        label.setForeground(Theme.TEXT);
        label.setFont(Theme.smallBoldFont());
        panel.add(label, BorderLayout.CENTER);
        notificationToast.setContentPane(panel);
        notificationToast.setSize(360, 78);

        Point frameLocation = getLocationOnScreen();
        int x = frameLocation.x + Math.max(12, getWidth() - notificationToast.getWidth() - 24);
        int y = frameLocation.y + 58;
        notificationToast.setLocation(x, y);
        notificationToast.setVisible(true);

        if (notificationToastTimer != null) {
            notificationToastTimer.stop();
        }
        notificationToastTimer = new javax.swing.Timer(4_000, event -> {
            if (notificationToast != null) {
                notificationToast.setVisible(false);
                notificationToast.dispose();
                notificationToast = null;
            }
        });
        notificationToastTimer.setRepeats(false);
        notificationToastTimer.start();
    }

    private void refreshRemoteMovieData() {
        new javax.swing.SwingWorker<List<cinesmart.model.Movie>, Void>() {
            @Override
            protected List<cinesmart.model.Movie> doInBackground() throws Exception {
                return remoteMovieDataService.fetchLatest2026Movies();
            }

            @Override
            protected void done() {
                try {
                    List<cinesmart.model.Movie> movies = get();
                    if (!DataStore.get().replaceRemoteMovies(movies)) {
                        tmdbErrorStatus = "Could not save the TMDb movie refresh.";
                        statusLabel.setText(tmdbErrorStatus);
                        return;
                    }
                    int schedules = showScheduleService.ensureRemoteMovieSchedules();
                    int addedShows = showScheduleService.generateShows();
                    if (dashboard != null) {
                        dashboard.refresh();
                    }
                    tmdbErrorStatus = "";
                    statusLabel.setText("TMDb updated: " + movies.size() + " 2026 movies; "
                            + schedules + " schedules and " + addedShows + " shows added.");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("TMDb movie refresh was interrupted.");
                } catch (java.util.concurrent.ExecutionException problem) {
                    String message = rootMessage(problem.getCause());
                    System.err.println("TMDb movie data unavailable: " + message);
                    tmdbErrorStatus = AppConfig.TMDB_API_KEY.isBlank()
                            ? "Set tmdbApiKey in .env to load live movies."
                            : "TMDb unavailable: check the terminal message.";
                    statusLabel.setText(tmdbErrorStatus);
                }
            }
        }.execute();
    }

    private void refreshOpenStreetMapListings() {
        new javax.swing.SwingWorker<List<cinesmart.model.CinemaVenue>, Void>() {
            @Override
            protected List<cinesmart.model.CinemaVenue> doInBackground() throws Exception {
                return openStreetMapCinemaService.fetchCinemaListings(DataStore.get());
            }

            @Override
            protected void done() {
                try {
                    List<cinesmart.model.CinemaVenue> venues = get();
                    if (!venues.isEmpty() && !DataStore.get().replaceCinemaVenues(venues)) {
                        statusLabel.setText("Could not save the OpenStreetMap cinema listings.");
                        return;
                    }
                    if (dashboard != null) {
                        dashboard.refresh();
                    }
                    statusLabel.setText(venues.isEmpty()
                            ? "OpenStreetMap returned no cinema listings; cached data was kept."
                            : "OpenStreetMap directory updated: " + venues.size()
                                    + " public cinema listings.");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    statusLabel.setText("OpenStreetMap lookup was interrupted.");
                } catch (java.util.concurrent.ExecutionException problem) {
                    statusLabel.setText("Cinema directory unavailable: "
                            + rootMessage(problem.getCause()));
                }
            }
        }.execute();
    }

    private static String rootMessage(Throwable problem) {
        if (problem == null || problem.getMessage() == null || problem.getMessage().isBlank()) {
            return "unknown error";
        }
        return problem.getMessage();
    }

    /**
     * Stage 4: hands one show over to the seat map card. MainFrame is the only place that knows
     * both screens, so DashboardPanel and MovieDetailsDialog can pass a ShowTime along without
     * knowing how the seat screen is built - and the map is reloaded from the store on every
     * visit, so a seat held elsewhere shows as taken immediately.
     *
     * @param show the show time that was picked, or null to open the card in its waiting state
     */
    public void openSeatSelection(ShowTime show) {
        lastPickedShow = show;
        updateMenus();
        seatPanel.openFor(show);
        showCard(CARD_SEATS);
    }

    /** Stage 5: pays for one booking - the seats the customer is holding right now. */
    public void openPayment(String bookingId) {
        Booking booking = bookingService.bookingOf(bookingId);
        updateMenus();
        paymentPanel.showBooking(booking);
        statusLabel.setText(booking == null ? "Nothing is waiting for payment"
                : "Paying " + booking.seatCount() + " seat"
                        + (booking.seatCount() == 1 ? "" : "s") + "  \u00b7  Rs "
                        + booking.getAmount());
        showCard(CARD_PAYMENT);
    }

    /**
     * Stage 5: opens the payment screen for the seats this customer is holding on the show that is
     * open. The seat screen owns that decision (it knows which booking belongs to the show on
     * screen), so this only hands over to it - which is why the screenshot tool can use it too.
     *
     * @return false when nothing is waiting for payment, so no screen is opened
     */
    public boolean goToPayment() {
        return seatPanel.goToPayment();
    }

    /**
     * Stage 5 + Stage 6: "My bookings". The list is rebuilt every visit, so a payment or a
     * cancellation done a moment ago is on screen without a restart.
     */
    public void openBookings() {
        updateMenus();
        bookingsPanel.refresh();
        statusLabel.setText("Your bookings");
        showCard(CARD_BOOKINGS);
    }

    /** Lets any card write the status line of the header band. */
    public void setStatusText(String text) {
        statusLabel.setText(text);
    }

    /**
     * Stage 10: the food and canteen screen. The list is rebuilt on every visit, so a city change or
     * an order placed a moment ago is on screen without a restart.
     */
    public void openCanteen() {
        updateMenus();
        canteenPanel.refresh();
        statusLabel.setText("Food and canteen");
        showCard(CARD_CANTEEN);
    }

    /**
     * The "Select your region" popup.
     *
     * Picking a place changes the city, so every city-dependent screen is refreshed afterwards -
     * exactly like the city combo box in the header, which is why this calls the same helper rather
     * than repeating the work.
     */
    public void openLocationPicker() {
        LocationDialog dialog = new LocationDialog(this, locationService);
        dialog.setVisible(true);
        cinesmart.model.Locality chosen = dialog.getChosen();
        if (chosen == null) {
            return; // cancelled: nothing changed anywhere
        }
        applyLocation(chosen);
        statusLabel.setText("Location set to " + chosen.displayName());
    }

    /**
     * Applies a detected or chosen place to the whole window.
     *
     * One place decides the city, the saved setting and every screen, which is what keeps the header,
     * the dashboard and the canteen from disagreeing about which city is being shown.
     */
    private void applyLocation(cinesmart.model.Locality place) {
        Session.setSelectedCity(place.getCity());
        DataStore.get().settings().setProperty("city", place.getCity());
        locationService.remember(place);
        DataStore.get().saveSettings();
        if (cityCombo != null) {
            cityCombo.setSelectedItem(place.getCity()); // this also refreshes the dashboard
        }
        if (dashboard != null) {
            dashboard.refresh();
        }
    }

    /** The Offers tab: the running discounts of the selected city. */
    public void openOffers() {
        updateMenus();
        offersPanel.refresh();
        statusLabel.setText("Offers and coupons in " + Session.selectedCity());
        showCard(CARD_OFFERS);
    }

    /** The Gift Cards tab: the wallet of the signed in customer. */
    public void openGiftCards() {
        updateMenus();
        giftCardsPanel.refresh();
        statusLabel.setText("Your gift cards");
        showCard(CARD_GIFT_CARDS);
    }

    /** Stage 10: the food orders this customer already placed. */
    public void showFoodOrders() {
        List<FoodOrder> orders = canteenService.ordersForCurrentUser();
        if (orders.isEmpty()) {
            UiUtil.info(this, "You have not ordered any food yet.\n\n"
                    + "Pick a cinema hall, add something from its menu, then press Place order.");
            return;
        }
        StringBuilder text = new StringBuilder();
        for (FoodOrder order : orders) {
            text.append(String.join("\n", canteenService.orderLines(order))).append("\n");
            text.append("Lines: ").append(order.getLines()).append("\n");
            text.append("Total: ").append(order.totalText()).append("\n\n");
        }
        UiUtil.info(this, "Your food orders\n\n" + text.toString().trim());
    }

    /** The menu bar follows the role: booking items for a customer, manage items for an admin. */
    private void updateMenus() {
        boolean signedIn = Session.isLoggedIn();
        bookingMenu.setVisible(signedIn && !Session.isAdmin());
        manageMenu.setVisible(signedIn && Session.isAdmin());
        updateLocationButton();
    }

    /**
     * The location button says "Detect my location" until the app knows where the customer is, and
     * "Change location" afterwards, so the label itself tells them whether detection already ran.
     */
    private void updateLocationButton() {
        if (locationButton == null) {
            return;
        }
        locationButton.setText(Session.hasSavedLocality() ? "Change location" : "Detect my location");
    }

    /** Called by RegisterPanel when a new customer has been saved. */
    public void registerCompleted(AuthResult result) {
        UiUtil.info(this, result.getMessage() + "\n\nPlease login with your new account.");
        showLogin();
    }

    public void logout() {
        if (!Session.isLoggedIn()) {
            showLogin();
            return;
        }
        if (UiUtil.confirm(this, "Logout from " + Session.currentUser().getUserName() + "?")) {
            Session.logout();
            showLogin();
        }
    }

    /* ------------------------------------------------------------------ *
     * Header with the global city selector
     ------------------------------------------------------------------ */

    private JPanel buildHeader() {
        JPanel header = new GradientHeader();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.setBorder(UiUtil.padded(14, 22, 14, 22));

        // Picture + name in one label, both taken from the shared Logo class.
        header.add(Logo.wordmark(30));

        header.add(Box.createHorizontalStrut(28));
        JLabel cityLabel = new JLabel("Select City:");
        cityLabel.setFont(Theme.smallBoldFont());
        cityLabel.setForeground(new Color(255, 255, 255, 225));
        header.add(cityLabel);

        header.add(Box.createHorizontalStrut(8));
        cityCombo = new JComboBox<>(Session.CITIES);
        cityCombo.setSelectedItem(Session.selectedCity());
        cityCombo.setToolTipText("Films, theatres and show timings of this city");
        cityCombo.addActionListener(event -> {
            Object chosen = cityCombo.getSelectedItem();
            if (chosen != null) {
                Session.setSelectedCity(chosen.toString());
                DataStore.get().settings().setProperty("city", Session.selectedCity());
                DataStore.get().saveSettings();
                if (dashboard != null) {
                    // Stage 3: the stats, the cinema list and the grid are all city dependent.
                    dashboard.refresh();
                }
                if (Session.isLoggedIn()) {
                    statusLabel.setText("Now showing in " + Session.selectedCity());
                }
            }
        });
        header.add(cityCombo);

        header.add(Box.createHorizontalStrut(10));
        // The BookMyShow style entry points: a location button, Offers and Gift Cards.
        locationButton = UiUtil.headerButton("Detect my location", event -> openLocationPicker());
        locationButton.setToolTipText("Works out where you are, or lets you search for your city");
        header.add(locationButton);

        header.add(Box.createHorizontalStrut(8));
        offersButton = UiUtil.headerButton("Offers", event -> openOffers());
        offersButton.setToolTipText("The running discounts in " + Session.selectedCity());
        header.add(offersButton);

        header.add(Box.createHorizontalStrut(8));
        giftCardsButton = UiUtil.headerButton("Gift Cards", event -> openGiftCards());
        giftCardsButton.setToolTipText("Your prepaid cards and their balances");
        header.add(giftCardsButton);

        header.add(Box.createHorizontalGlue());
        // Feature 3: notification bell with unread count.
        notificationBell = UiUtil.headerButton("🔔", event -> toggleNotificationDropdown());
        notificationBell.setToolTipText("Notifications");
        header.add(notificationBell);
        header.add(Box.createHorizontalStrut(8));
        statusLabel = new JLabel("Please sign in to continue");
        statusLabel.setFont(Theme.smallFont());
        statusLabel.setForeground(new Color(255, 255, 255, 235));
        header.add(statusLabel);
        return header;
    }

    /* ------------------------------------------------------------------ *
     * Menu bar
     * ------------------------------------------------------------------ */

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu accountMenu = new JMenu("Account");
        accountMenu.setMnemonic('A');
        // ActionListener needs actionPerformed(ActionEvent); a no-arg method reference
        // such as this::showLogin does not match, so every item uses an explicit lambda.
        accountMenu.add(menuItem("Login", KeyStroke.getKeyStroke("control L"),
                event -> showLogin()));
        accountMenu.add(menuItem("Register", KeyStroke.getKeyStroke("control R"),
                event -> showRegister()));
        accountMenu.addSeparator();
        accountMenu.add(menuItem("Logout", KeyStroke.getKeyStroke("control Q"),
                event -> logout()));
        accountMenu.addSeparator();
        accountMenu.add(menuItem("Exit", KeyStroke.getKeyStroke("control E"),
                event -> dispose()));
        menuBar.add(accountMenu);
        menuBar.add(buildBookingMenu());
        menuBar.add(buildManageMenu());

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic('H');
        helpMenu.add(menuItem("About CineSmart", null, event -> UiUtil.about(this,
                "CineSmart v1.0\nMovie Ticket Booking & Recommendation System\n\n"
                        + "Built with Java Swing for the AOOP project.\n"
                        + "Logo loaded from: " + Logo.loadedFrom())));
        menuBar.add(helpMenu);

        return menuBar;
    }

    /** Small factory so every menu item looks and behaves the same. */
    private JMenuItem menuItem(String text, KeyStroke shortcut,
                               java.awt.event.ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        if (shortcut != null) {
            item.setAccelerator(shortcut);
        }
        item.addActionListener(action);
        return item;
    }

    /** The items only a customer needs; hidden for admins and before login (updateMenus()). */
    private JMenu buildBookingMenu() {
        bookingMenu = new JMenu("Booking");
        bookingMenu.setMnemonic('K');
        bookingMenu.add(menuItem("Home dashboard", KeyStroke.getKeyStroke("control D"),
                event -> openHome()));
        bookingMenu.addSeparator();
        bookingMenu.add(menuItem("Select seats", null,
                event -> openSeatSelection(lastPickedShow)));
        bookingMenu.add(menuItem("Payment and confirmation", null,
                event -> openPayment(pendingBookingId())));
        bookingMenu.add(menuItem("My bookings", null, event -> openBookings()));
        bookingMenu.add(menuItem("Food and canteen", null, event -> openCanteen()));
        bookingMenu.add(menuItem("Offers and coupons", null, event -> openOffers()));
        bookingMenu.add(menuItem("Gift cards", null, event -> openGiftCards()));
        bookingMenu.addSeparator();
        bookingMenu.add(menuItem("Change my location", KeyStroke.getKeyStroke("control L"),
                event -> openLocationPicker()));
        bookingMenu.add(menuItem("Recommendations", null,
                event -> laterStage("Recommendations", 9)));
        bookingMenu.setVisible(false);
        return bookingMenu;
    }

    /** The screens an admin is responsible for; Stage 8 builds the forms behind them. */
    private JMenu buildManageMenu() {
        manageMenu = new JMenu("Manage");
        manageMenu.setMnemonic('M');
        manageMenu.add(menuItem("Films", null, event -> laterStage("Manage films", 8)));
        manageMenu.add(menuItem("Cinemas", null, event -> laterStage("Manage cinemas", 8)));
        manageMenu.add(menuItem("Show times", null, event -> laterStage("Manage show times", 8)));
        manageMenu.add(menuItem("Users", null, event -> laterStage("Manage users", 8)));
        manageMenu.setVisible(false);
        return manageMenu;
    }

    /**
     * The Booking menu needs a booking to work with when it is used directly, so it falls back to
     * the newest unpaid one. Returns null when everything is paid or cancelled, and the payment
     * card then explains that there is nothing due.
     */
    private String pendingBookingId() {
        Booking pending = bookingService.pendingBooking();
        return (pending == null) ? null : pending.getBookingId();
    }

    /** Keeps the menu honest: the screen is in the plan, the code arrives in that stage. */
    private void laterStage(String screen, int stage) {
        UiUtil.info(this, screen + " is built in Stage " + stage + ".\n\nFinished so far: "
                + "Stage 1 login and registration, Stage 2 home dashboard, Stage 3 movie, "
                + "cinema and show-time data, Stage 4 seat selection with the smart suggestion, "
                + "Stage 5 payment with the ticket and My bookings, Stage 6 cancellation.");
    }

    /** The blue gradient band that sits on top of every screen. */
    private static final class GradientHeader extends JPanel {

        private static final long serialVersionUID = 1L;

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setPaint(Theme.gradient(Theme.BLUE, Theme.accent(), true));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }
}

