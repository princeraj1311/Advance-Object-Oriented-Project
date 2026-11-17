package cinesmart.service;


import cinesmart.model.Booking;
import cinesmart.model.Customer;
import cinesmart.model.Movie;
import cinesmart.model.SeatMap;
import cinesmart.model.SeatHold;
import cinesmart.model.ShowTime;
import cinesmart.model.Theatre;
import cinesmart.model.User;
import cinesmart.util.AppConfig;
import cinesmart.util.DataStore;
import cinesmart.util.EventBus;
import cinesmart.util.IDGenerator;
import cinesmart.util.SeatEvent;
import cinesmart.util.Session;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * BookingService started as the Stage 4 service: it turns "the customer tapped some seats" into
 * saved data, and it is the only place that is allowed to say whether a booking may happen at all.
 *
 * Two rules shape the class:
 * 1. the DataStore is the truth, so every check re-reads the show by id instead of trusting the
 *    object the screen happens to hold (the same show may have been booked by another window);
 * 2. nothing is written unless every check passed, and a failed write puts the seats back, so a
 *    half saved booking cannot exist - the same all-or-nothing idea as a database transaction.
 *
 * Stage 5 added the other half of the life cycle to the same class: holding seats now also OPENS a
 * booking (unpaid), the customer's bookings can be listed and printed as tickets, and Stage 6 added
 * cancelling one. Payment itself lives in {@link PaymentService}, because how the money is taken
 * (card, UPI, net banking) is a different job from whether a seat may be sold.
 *
 * SeatSelectionPanel, PaymentPanel and BookingsPanel therefore contain no booking rule at all:
 * they ask {@link #problemWith}, {@link #holdSeats}, {@link #cancelProblemWith} and
 * {@link #cancelBooking}, and show what comes back.
 */
public class BookingService {

    /**
     * What a successful hold returns: enough detail to print the seats and to explain them in a
     * dialog, without the view going back to the store for names and prices.
     */
    public static final class Receipt {

        private final String showId;
        private final String movieTitle;
        private final String theatreName;
        private final String screenName;
        private final String whenText;
        private final List<String> seats;
        private final int pricePerSeat;
        private final int amount;
        /** Stage 5: the booking that was opened together with the seat hold. */
        private final String bookingId;

        Receipt(String bookingId, String showId, String movieTitle, String theatreName,
                String screenName, String whenText, List<String> seats, int pricePerSeat) {
            this.bookingId = bookingId;
            this.showId = showId;
            this.movieTitle = movieTitle;
            this.theatreName = theatreName;
            this.screenName = screenName;
            this.whenText = whenText;
            this.seats = new ArrayList<>(seats);
            this.pricePerSeat = pricePerSeat;
            this.amount = pricePerSeat * seats.size();
        }

        public String getBookingId() {
            return bookingId;
        }

        public String getShowId() {
            return showId;
        }

        public List<String> getSeats() {
            return new ArrayList<>(seats);
        }

        public int getSeatCount() {
            return seats.size();
        }

        public int getAmount() {
            return amount;
        }

        public String seatsText() {
            return String.join(", ", seats);
        }

        /** The few lines shown after the seats are saved, and later by the Stage 5 ticket. */
        public String ticketText() {
            StringBuilder text = new StringBuilder();
            text.append(theatreName).append("  \u00b7  ").append(screenName).append('\n');
            text.append(movieTitle).append('\n');
            text.append(whenText).append('\n');
            text.append("Seat").append(seats.size() == 1 ? "" : "s").append(": ")
                    .append(seatsText()).append('\n');
            text.append(seats.size()).append(
                    " x Rs ").append(pricePerSeat).append("  =  Rs ").append(amount);
            return text.toString();
        }

        /** "C7, C8 - Rs 420" - the summary line the header and later the booking list show. */
        public String summaryText() {
            return seatsText() + "  \u00b7  Rs " + amount;
        }
    }

    private final DataStore store;

    /** How long a seat stay HELD before it auto-releases (Feature 1 config constant). */
    private static final int HOLD_MINUTES = AppConfig.SEAT_HOLD_MINUTES;

    /** Feature 3: notification layer. */
    private final NotificationService notificationService = new NotificationService();

    public BookingService() {
        this(DataStore.get());
    }

    /** Constructor injection, exactly like the other services, so a test can supply its own store. */
    public BookingService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *\
     * Stage 5: the bookings of the signed in customer
     * ------------------------------------------------------------------ */

    /** "My bookings": every purchase of the signed in customer, newest first. */
    public List<Booking> bookingsForCurrentUser() {
        return store.bookingsForUser(Session.currentUser() == null ? ""
                : Session.currentUser().getUserName());
    }

    /** One booking by id, but only when it belongs to the signed in customer. */
    public Booking bookingOf(String bookingId) {
        Booking booking = store.findBooking(bookingId);
        if (booking == null) {
            return null;
        }
        User owner = Session.currentUser();
        String name = (owner == null) ? "" : owner.getUserName();
        return booking.getUserName().equals(name) ? booking : null;
    }

    /**
     * The booking the payment screen should open: the newest one that is still unpaid. Returns
     * null when everything is paid or cancelled, which is the honest answer - there is no money to
     * collect then.
     */
    public Booking pendingBooking() {
        for (Booking booking : bookingsForCurrentUser()) {
            if (booking.isAwaitingPayment()) {
                return booking;
            }
        }
        return null;
    }

    /**
     * Joins a booking with the catalogue and prints it as a ticket: this is what the customer gets
     * after a payment, what "My bookings" shows for every card, and the text inside the
     * cancellation dialog. The model itself never knows the film or the cinema.
     */
    public List<String> ticketLines(Booking booking) {
        List<String> lines = new ArrayList<>();
        if (booking == null) {
            return lines;
        }
        ShowTime show = store.findShowTime(booking.getShowId());
        Movie movie = (show == null) ? null : store.findMovie(show.getMovieId());
        Theatre theatre = (show == null) ? null : store.findTheatre(show.getTheatreId());
        lines.add((movie == null) ? "Film removed from the catalogue" : movie.getTitle());
        lines.add((theatre == null) ? "Cinema removed from the catalogue"
                : theatre.getName() + "  \u00b7  " + show.getScreenName());
        lines.add(showTimeText(show));
        lines.add("Seats: " + booking.seatsText());
        lines.add(booking.seatCount() + " x Rs " + priceOf(show) + "  =  Rs " + booking.getAmount());
        lines.add("Booking " + booking.getBookingId() + "  \u00b7  " + booking.getStatus().label());
        return lines;
    }

    /** "Sat, 30 Sep 2026  18:30" - or a dash when the show is gone from the catalogue. */
    private String showTimeText(ShowTime show) {
        if (show == null) {
            return "Show removed from the catalogue";
        }
        try {
            return show.dateTime().format(java.time.format.DateTimeFormatter
                    .ofPattern("EEE, dd MMM yyyy  HH:mm"));
        } catch (Exception e) {
            return show.getDate() + "  " + show.getStartTime();
        }
    }

    /** The price a booking of this show is worth, or 0 when the show no longer exists. */
    public int priceOf(ShowTime show) {
        if (show == null) {
            return 0;
        }
        return show.getPrice();
    }

    /* ------------------------------------------------------------------ *\
     * Opening the map of one show
     * ------------------------------------------------------------------ */

    public ShowTime findShow(String showId) {
        return store.findShowTime(showId);
    }

    /**
     * A seat map filled with the seats that are already gone.
     *
     * A show booked through Stage 4 stores the seat names, so those exact seats are marked grey.
     * A show that only knows a count (any Stage 3 line, and all the sample data) gets that many
     * seats filled in deterministically, so the map stays stable between visits instead of
     * shuffling every time it opens. A show that has both - seats held through CineSmart plus a
     * sold count from Stage 3 - shows both groups, because the named seats are only part of the
     * count the file keeps.
     */
    public SeatMap seatMapFor(ShowTime show) {
        if (show == null) {
            return new SeatMap("", 10, 0, new LinkedHashSet<String>());
        }
        ShowTime live = store.findShowTime(show.getShowId());
        ShowTime source = (live == null) ? show : live;
        Set<String> taken = new LinkedHashSet<>(source.reservedSeatLabels());
        int unnamed = Math.max(0, source.getBookedSeats() - taken.size());
        if (unnamed > 0) {
            taken.addAll(SeatMap.seatsForCount(source.getShowId(), source.getTotalSeats(),
                    unnamed, taken));
        }
        return new SeatMap(source.getShowId(), source.getTotalSeats(), source.getPrice(), taken);
    }

    /** One honest sentence about the grey seats, shown above the map when the screen opens. */
    public String seatDataNote(ShowTime show) {
        if (show == null) {
            return "";
        }
        int held = show.reservedSeatCount();
        if (held > 0 && held < show.getBookedSeats()) {
            return show.getBookedSeats() + " of " + show.getTotalSeats()
                    + " seats are sold, " + held + " of them held through CineSmart.";
        }
        if (held > 0) {
            return held + " seats of this show are already held.";
        }
        if (show.getBookedSeats() > 0) {
            return show.getBookedSeats() + " of " + show.getTotalSeats()
                    + " seats of this show are already sold.";
        }
        return "Every seat of this hall is still free.";
    }

    /* ------------------------------------------------------------------ *\
     * The rules of a booking
     * ------------------------------------------------------------------ */

    /**
     * @return null when the chosen seats may be held, otherwise the one reason why not
     */
    public String problemWith(ShowTime show, SeatMap map) {
        if (show == null) {
            return "No show time was selected - please pick a film and a time first.";
        }
        if (map == null) {
            return "The seat map is not ready yet.";
        }
        ShowTime live = store.findShowTime(show.getShowId());
        if (live == null) {
            return "This show is not in the programme any more - please pick another one.";
        }
        if (live.isPast()) {
            return "This show has already started, so its seats cannot be booked any more.";
        }
        if (map.selectedCount() == 0) {
            return "Please tap at least one free seat on the map.";
        }
        if (map.selectedCount() > SeatMap.MAX_SEATS) {
            return "One booking can hold at most " + SeatMap.MAX_SEATS + " seats.";
        }
        if (live.seatsAvailable() < map.selectedCount()) {
            return "Only " + live.seatsAvailable() + (live.seatsAvailable() == 1
                    ? " seat left" : " seats left") + " in this show - please choose fewer.";
        }
        for (String seat : map.selectedSeats()) {
            if (!map.exists(seat)) {
                return "Seat " + seat + " does not exist in this auditorium.";
            }
            if (live.isSeatReserved(seat)) {
                return "Seat " + seat + " was booked while you were choosing "
                        + "the others. Please pick another seat.";
            }
            SeatHold heldByAnother = store.activeHoldForSeat(live.getShowId(), seat);
            String userName = Session.currentUser() == null ? ""
                    : Session.currentUser().getUserName();
            if (heldByAnother != null && !heldByAnother.getUserName().equalsIgnoreCase(userName)) {
                return "Seat " + seat + " is held by someone else. Please pick another seat.";
            }
        }
        return null;
    }

    /**
     * Writes the chosen seats onto the show and saves data/showtimes.txt.
     *
     * @return the receipt, or null when a rule was broken or the file could not be written - in
     *         that case the seats are released again, so a half saved booking cannot exist
     */
    public Receipt holdSeats(ShowTime show, SeatMap map) {
        if (problemWith(show, map) != null) {
            return null;
        }
        ShowTime live = store.findShowTime(show.getShowId());
        if (live == null) {
            return null;
        }
        List<String> seats = map.selectedSeats();
        String userName = Session.currentUser() == null ? ""
                : Session.currentUser().getUserName();

        // --- Atomic hold phase: try every seat; roll back any that succeeded ---
        List<SeatHold> createdHolds = new ArrayList<>();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expires = now.plusMinutes(HOLD_MINUTES);
            DateTimeFormatter stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (String seat : seats) {
                SeatHold hold = new SeatHold(IDGenerator.bookingId(),
                        live.getShowId(), seat, userName,
                        now.format(stamp), expires.format(stamp));
                if (!store.addSeatHold(hold)) {
                    releaseHolds(createdHolds);
                    return null;
                }
                createdHolds.add(hold);
            }
        } catch (Exception e) {
            releaseHolds(createdHolds);
            return null;
        }

        // --- Booking phase: open the unpaid booking that the payment screen will pay for ---
        Booking booking = new Booking(IDGenerator.bookingId(), userName,
                live.getShowId(), seats, live.getPrice() * seats.size());
        if (!store.addBooking(booking)) {
            releaseHolds(createdHolds);
            return null;
        }

        Movie movie = store.findMovie(live.getMovieId());
        Theatre theatre = store.findTheatre(live.getTheatreId());
        return new Receipt(booking.getBookingId(), live.getShowId(),
                movie == null ? live.getMovieId() : movie.getTitle(),
                theatre == null ? live.getTheatreId() : theatre.getName(),
                live.getScreenName(),
                live.dateText() + "  \u00b7  " + live.startTimeText(),
                seats, live.getPrice());
    }

    /** Releases every hold in the list (used to roll back a failed hold). */
    private void releaseHolds(List<SeatHold> holds) {
        for (SeatHold hold : holds) {
            store.removeSeatHold(hold);
        }
    }

    /* ------------------------------------------------------------------ *\
     * The seat preference behind the smart suggestions
     * ------------------------------------------------------------------ */

    /** "No Preference" for a guest or an admin, the saved rule for a customer. */
    public String seatPreference() {
        if (Session.currentUser() instanceof Customer) {
            return ((Customer) Session.currentUser()).getSeatPreference();
        }
        return SeatMap.PREFERENCE_ANY;
    }

    /** Saves the rule on the profile, so the next booking starts from the same choice. */
    public boolean saveSeatPreference(String preference) {
        if (!(Session.currentUser() instanceof Customer)) {
            return false; // an admin browses read-only, so there is no profile to write to
        }
        Customer customer = (Customer) Session.currentUser();
        customer.setSeatPreference(SeatMap.normalise(preference));
        return store.updateUser(customer);
    }

    /* ------------------------------------------------------------------ *\
     * Stage 6: cancelling a booking
     * ------------------------------------------------------------------ */

    /**
     * @return null when the booking may be cancelled, otherwise the one reason why not. Kept
     *         separate from {@link #cancelBooking} so the button can be greyed out with the very
     *         same rule that would refuse the click - a disabled button and a service check can
     *         never disagree.
     */
    public String cancelProblemWith(Booking booking) {
        User current = Session.currentUser();
        if (booking == null) {
            return "That booking could not be found - it may have been removed.";
        }
        if (!(current instanceof Customer) || !booking.getUserName().equals(current.getUserName())) {
            return "You can only cancel your own bookings.";
        }
        if (booking.isCancelled()) {
            return "Booking " + booking.getBookingId() + " was already cancelled on "
                    + booking.getCancelledAt() + ".";
        }
        ShowTime live = store.findShowTime(booking.getShowId());
        if (live == null) {
            return "The show of this booking is no longer in the programme, "
                    + "so it cannot be cancelled here.";
        }
        if (live.isPast()) {
            return "This show started on " + live.dateText() + " at " + live.startTimeText()
                    + ", so it can no longer be cancelled.";
        }
        // Feature 2: block cancellation within the cutoff window before showtime.
        LocalDateTime showTime = live.dateTime();
        LocalDateTime cutoff = showTime.minusMinutes(AppConfig.CANCELLATION_CUTOFF_MINUTES);
        if (LocalDateTime.now().isAfter(cutoff)) {
            return "This show starts in less than " + AppConfig.CANCELLATION_CUTOFF_MINUTES
                    + " minutes, so it can no longer be cancelled.";
        }
        return null;
    }

    /**
     * Cancels a booking: the seats go back into the map, the film's sold counter drops again and
     * data/bookings.txt keeps the booking as CANCELLED instead of deleting it, so the history and
     * the refunded amount stay visible.
     *
     * Both writes must succeed - a booking that is cancelled on screen but still holds seats in
     * showtimes.txt would be worse than a refused cancellation.
     *
     * @return true when the booking was cancelled
     */
    public boolean cancelBooking(Booking booking) {
        if (cancelProblemWith(booking) != null) {
            return false;
        }
        ShowTime live = store.findShowTime(booking.getShowId());
        boolean wasPaid = booking.isPaid();

        // 1. give the seats back; only the seats of this booking, not seats of somebody else
        live.releaseSeats(booking.getSeats());
        if (!store.updateShowTime(live)) {
            return false;
        }

        // 2. the film sold these tickets only because this booking was paid, so take them back
        if (wasPaid) {
            Movie movie = store.findMovie(live.getMovieId());
            if (movie != null) {
                movie.releaseBooking(booking.seatCount());
                store.saveMovies();
            }
        }

        // 3. keep the booking, marked as cancelled, so "My bookings" can still show the refund
        booking.markCancelled();
        boolean saved = store.updateBooking(booking);
        if (saved) {
            // Feature 2: broadcast the seat release so every seat map updates live.
            EventBus.post(new SeatEvent(SeatEvent.Type.SEAT_RELEASED,
                    booking.getShowId()));
            // Feature 3: notify the customer.
            try {
                ShowTime show = store.findShowTime(booking.getShowId());
                notificationService.notifyBookingCancelled(booking.getUserName(), booking, show);
            } catch (Exception e) {
                System.err.println("Notification failed: " + e.getMessage());
            }
        }
        return saved;
    }
}

