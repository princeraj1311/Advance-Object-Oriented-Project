package cinesmart.service;


import cinesmart.model.Booking;
import cinesmart.model.GiftCard;
import cinesmart.model.Movie;
import cinesmart.model.SeatHold;
import cinesmart.model.ShowTime;
import cinesmart.model.User;
import cinesmart.util.DataStore;
import cinesmart.util.EventBus;
import cinesmart.util.IDGenerator;
import cinesmart.util.SeatEvent;
import cinesmart.util.Session;
import cinesmart.util.ValidationUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * PaymentService is the Stage 5 service: it decides whether a held booking may be paid for, and it
 * marks it as paid.
 *
 * It follows the same two rules as {@link BookingService}:
 * 1. the DataStore is the truth - the booking is read back by id, so a booking that was paid (or
 *    cancelled) in another window cannot be paid for twice;
 * 2. nothing is written unless every check passed, so the amount is taken for exactly one booking
 *    and the ticket exists only after the money was recorded.
 *
 * The card, UPI and net banking rules are the ones already written for this project in
 * {@link ValidationUtil} (Luhn checksum, expiry, UPI shape, 4 digit PIN). This class only decides
 * WHICH of them applies to the chosen payment method, so the three methods are checked by one form
 * and one list of problems.
 */
public class PaymentService {

    public static final String METHOD_CARD = "Card";
    public static final String METHOD_UPI = "UPI";
    public static final String METHOD_NET_BANKING = "Net Banking";
    /**
     * A prepaid gift card.
     *
     * A card must cover the WHOLE amount after the discount. Partial cover is not offered on purpose:
     * it would mean charging the difference to a card the customer did not choose, which is exactly
     * the surprise a cinema app should never spring on anybody.
     */
    public static final String METHOD_GIFT_CARD = "Gift Card";

    /** The methods offered on the payment screen, in the order they are shown. */
    public static final List<String> METHODS = java.util.Collections.unmodifiableList(
            Arrays.asList(METHOD_CARD, METHOD_UPI, METHOD_NET_BANKING, METHOD_GIFT_CARD));

    /**
     * What the customer typed on the payment form. The fields that do not belong to the chosen
     * method are simply left empty, so one object carries all three methods.
     */
    public static final class PaymentDetails {

        private String method = METHOD_CARD;
        private String cardNumber = "";
        private String cardHolder = "";
        private String cvv = "";
        private String expiry = "";
        private String upiId = "";
        private String pin = "";
        /** The gift card code, only read when the gift card method is chosen. */
        private String giftCardCode = "";

        /** Setters return this, so a form can build the object in one statement. */
        public PaymentDetails method(String value) {
            this.method = ValidationUtil.clean(value);
            return this;
        }

        /** Upper case, because a gift card code is printed in capitals on the slip. */
        public PaymentDetails giftCardCode(String value) {
            this.giftCardCode = ValidationUtil.clean(value).toUpperCase();
            return this;
        }

        public String getGiftCardCode() {
            return giftCardCode;
        }

        public PaymentDetails cardNumber(String value) {
            this.cardNumber = digitsOf(value);
            return this;
        }

        public PaymentDetails cardHolder(String value) {
            this.cardHolder = ValidationUtil.clean(value);
            return this;
        }

        public PaymentDetails cvv(String value) {
            this.cvv = digitsOf(value);
            return this;
        }

        public PaymentDetails expiry(String value) {
            this.expiry = ValidationUtil.clean(value);
            return this;
        }

        public PaymentDetails upiId(String value) {
            this.upiId = ValidationUtil.clean(value).toLowerCase();
            return this;
        }

        public PaymentDetails pin(String value) {
            this.pin = digitsOf(value);
            return this;
        }

        public String getMethod() {
            return method;
        }

        /** Masked card number for the receipt: "4111 1111 1111 1111" becomes "4111 **** **** 1111". */
        public String maskedCard() {
            if (cardNumber.length() < 4) {
                return "";
            }
            String tail = cardNumber.substring(cardNumber.length() - 4);
            return "**** **** **** " + tail;
        }

        /** Only digits survive, so "4111 1111 1111 1111" and "4111111111111111" are the same card. */
        private static String digitsOf(String value) {
            return ValidationUtil.clean(value).replaceAll("[^0-9]", "");
        }
    }

    /**
     * The answer of {@link #pay}: either the booking is paid and the ticket can be printed, or
     * nothing was changed and the problems say why not.
     */
    public static final class PaymentResult {

        private final boolean success;
        private final Booking booking;
        private final String transactionId;
        private final List<String> problems;

        PaymentResult(boolean success, Booking booking, String transactionId, List<String> problems) {
            this.success = success;
            this.booking = booking;
            this.transactionId = transactionId;
            this.problems = problems;
        }

        public boolean isSuccess() {
            return success;
        }

        /** The paid booking (the same object that is now in the store), or null after a failure. */
        public Booking getBooking() {
            return booking;
        }

        /** The transaction id printed on the ticket, or "" after a failure. */
        public String getTransactionId() {
            return transactionId;
        }

        public List<String> getProblems() {
            return new ArrayList<>(problems);
        }
    }
    /* ------------------------------------------------------------------ *\
     * The rules
     * ------------------------------------------------------------------ */

    private final DataStore store;

    public PaymentService() {
        this(DataStore.get());
    }

    /** Constructor injection, exactly like the other services, so a test can supply its own store. */
    /** Stage 5 takes the money; it shares the same DataStore through its own service. */
    private final NotificationService notificationService = new NotificationService();

    public PaymentService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /**
     * Every reason the payment cannot go through, in one list, so the form can mark every wrong
     * field at once instead of making the customer discover them one by one.
     *
     * @return an empty list when the payment may go through
     */
    public List<String> problemsWith(Booking booking, PaymentDetails details) {
        return problemsWith(booking, details, 0);
    }

    /**
     * The same check, knowing how much an offer has already taken off.
     *
     * This matters for exactly one thing: a gift card has to cover the amount that is REALLY due.
     * A customer with Rs 800 on a card who uses a Rs 50 offer on an Rs 800 booking still pays, and
     * the card has to have Rs 750 on it - not Rs 800, which would refuse a payment that is actually
     * affordable.
     *
     * @param discount rupees already taken off by an offer code; 0 when no code was used
     */
    public List<String> problemsWith(Booking booking, PaymentDetails details, int discount) {
        List<String> problems = new ArrayList<>();
        Booking live = liveBookingOf(booking);
        if (live == null) {
            problems.add("That booking could not be found - it may have been removed.");
            return problems; // nothing else can be checked without a booking
        }
        if (!belongsToCurrentUser(live)) {
            problems.add("You can only pay for your own bookings.");
            return problems;
        }
        if (live.isCancelled()) {
            problems.add("Booking " + live.getBookingId() + " was cancelled, so nothing is due.");
            return problems;
        }
        if (live.isPaid()) {
            problems.add("Booking " + live.getBookingId() + " is already paid ("
                    + live.getPaymentRef() + ").");
            return problems;
        }
        if (live.getAmount() <= 0) {
            problems.add("This booking has no amount to pay - please pick the seats again.");
        }

        ShowTime show = store.findShowTime(live.getShowId());
        if (show == null) {
            problems.add("The show of this booking is no longer in the programme.");
            return problems;
        }
        if (show.isPast()) {
            problems.add("This show already started on " + show.dateText() + " at "
                    + show.startTimeText() + ", so the seats cannot be paid for.");
            return problems;
        }
        // A seat is held separately until payment succeeds; only then is it marked reserved on
        // the show itself.
        for (String seat : live.getSeats()) {
            SeatHold hold = store.activeHoldForSeat(show.getShowId(), seat);
            if (hold == null || !hold.getUserName().equalsIgnoreCase(live.getUserName())) {
                problems.add("Seat " + seat + " is no longer held for this booking.");
            }
        }

        int amountDue = Math.max(0, live.getAmount() - Math.max(0, discount));
        problems.addAll(paymentDetailProblems(details, amountDue));
        return problems;
    }

    /**
     * Only the fields of the chosen method are checked, so unused boxes never complain.
     *
     * @param amountDue the booking amount after any offer discount, because that is the figure a
     *                  gift card has to cover
     */
    private List<String> paymentDetailProblems(PaymentDetails details, int amountDue) {
        List<String> problems = new ArrayList<>();
        if (details == null) {
            problems.add("Please choose a payment method.");
            return problems;
        }
        String method = details.getMethod();
        if (METHOD_CARD.equals(method)) {
            if (!ValidationUtil.isValidCardNumber(details.cardNumber)
                    || !ValidationUtil.passesLuhn(details.cardNumber)) {
                problems.add("The card number is not valid (check the digits, it must pass the"
                        + " Luhn checksum like every real card).");
            }
            if (!ValidationUtil.isValidCvv(details.cvv)) {
                problems.add("The CVV must be the 3 digit code on the back of the card.");
            }
            if (!ValidationUtil.isValidExpiry(details.expiry)) {
                problems.add("The expiry date must be MM/YY and still be in the future.");
            }
            if (details.cardHolder.isEmpty()) {
                problems.add("Please type the name printed on the card.");
            }
        } else if (METHOD_UPI.equals(method)) {
            if (!ValidationUtil.isValidUpiId(details.upiId)) {
                problems.add("A UPI id looks like yourname@bank (for example priya@okbank).");
            }
        } else if (METHOD_NET_BANKING.equals(method)) {
            if (!ValidationUtil.isValidPin(details.pin)) {
                problems.add("The 4 digit net banking PIN is not correct.");
            }
        } else if (METHOD_GIFT_CARD.equals(method)) {
            problems.addAll(giftCardProblems(details.getGiftCardCode(), amountDue));
        } else {
            problems.add("Please choose card, UPI, net banking or gift card.");
        }
        return problems;
    }

    /**
     * The rules for paying with a gift card, checked against the amount actually owed.
     *
     * The amount passed in is the DISCOUNTED one, because that is what has to come off the card: a
     * customer who used a Rs 50 offer on an Rs 800 booking must find Rs 750 left on their card, not
     * Rs 800. A card that cannot cover the whole remainder is refused rather than partly used.
     */
    private List<String> giftCardProblems(String code, int amountDue) {
        List<String> problems = new ArrayList<>();
        if (code == null || code.trim().isEmpty()) {
            problems.add("Please type the gift card code, for example CSGIFT-4821-KQ.");
            return problems;
        }
        GiftCard card = new GiftCardService(store).findByCode(code);
        if (card == null) {
            problems.add("No gift card with the code " + code.toUpperCase() + ".");
            return problems;
        }
        String user = Session.currentUser() == null ? "" : Session.currentUser().getUserName();
        if (!card.getOwnerName().equals(user)) {
            problems.add("Gift card " + card.getCode() + " belongs to another account.");
        } else if (card.isExpiredOn(java.time.LocalDate.now())) {
            problems.add("Gift card " + card.getCode() + " expired on " + card.getExpiresOn() + ".");
        } else if (!card.canCover(amountDue)) {
            problems.add("Gift card " + card.getCode() + " has Rs " + card.getBalance()
                    + " left, but Rs " + amountDue + " is due. "
                    + "A gift card has to cover the whole amount - please pay by another method.");
        }
        return problems;
    }
    /* ------------------------------------------------------------------ *\
     * Paying
     * ------------------------------------------------------------------ */

    /**
     * Takes the payment and turns the held booking into the ticket.
     *
     * Order matters: first every rule is checked, then the transaction id is made, then the
     * booking is marked paid and written. If that write fails, nothing is left behind but an
     * unpaid booking with its seats still held - the customer simply tries again, and is never
     * charged for a ticket that was not saved.
     *
     * @return the paid booking, or a result carrying the problems when nothing was changed
     */
    public PaymentResult pay(Booking booking, PaymentDetails details) {
        return pay(booking, details, 0);
    }

    /**
     * The same payment, after an offer has been checked and a gift card chosen.
     *
     * The discount is passed in ALREADY validated by {@link OfferService} and the gift card code by
     * the form; this method still re-checks the card before spending it, because between checking and
     * paying the balance may have changed. That double check is the whole point: the screen's word is
     * never trusted over the store's.
     *
     * @param discount rupees taken off by an offer code; 0 when no code was used
     * @return the paid booking, or a result carrying the problems when nothing was changed
     */
    public PaymentResult pay(Booking booking, PaymentDetails details, int discount) {
        List<String> problems = problemsWith(booking, details, discount);
        if (!problems.isEmpty()) {
            return new PaymentResult(false, null, "", problems);
        }
        if (discount < 0 || discount > booking.getAmount()) {
            return new PaymentResult(false, null, "", java.util.Collections.singletonList(
                    "The discount is not valid for this booking."));
        }

        Booking live = liveBookingOf(booking);
        // The discount comes off the amount the booking is really worth, and that reduced figure is
        // what gets saved - so the ticket, the refund and My bookings all agree on the same number.
        int amountDue = live.getAmount() - discount;

        GiftCardService giftCards = new GiftCardService(store);
        if (METHOD_GIFT_CARD.equals(details.getMethod())) {
            // Already checked in problemsWith; spend() is the only thing that can still refuse it.
            if (!giftCards.spend(details.getGiftCardCode(), amountDue,
                    "Ticket " + live.getBookingId()).isPaid()) {
                return new PaymentResult(false, null, "", java.util.Collections.singletonList(
                        "The gift card could not be charged, so nothing was taken. Please try again."));
            }
        }

        String transactionId = IDGenerator.transactionId();
        live.setAmount(amountDue);
        live.markPaid(details.getMethod(), transactionId);
        if (!store.updateBooking(live)) {
            // Put the gift card money back: the ticket was not issued, so the card must be whole.
            if (METHOD_GIFT_CARD.equals(details.getMethod())) {
                giftCards.refund(details.getGiftCardCode(), amountDue,
                        "Reversed " + live.getBookingId());
            }
            live.setAmount(live.getAmount() + discount);
            return new PaymentResult(false, null, "",
                    java.util.Collections.singletonList("The booking could not be saved, "
                            + "so nothing was charged. Please try again."));
        }

        // Feature 1: convert HELD seats to BOOKED only if the hold still belongs to this user
        // and has not expired.
        convertHeldToBooked(live);

        // The film only counts tickets that were actually paid for - this is the number the
        // Stage 9 recommendations and the dashboard best-seller list read.
        ShowTime showForCount = store.findShowTime(live.getShowId());
        if (showForCount != null) {
            Movie movie = store.findMovie(showForCount.getMovieId());
            if (movie != null) {
                movie.registerBooking(live.seatCount());
                store.saveMovies();
            }
        }
        EventBus.post(new SeatEvent(SeatEvent.Type.SEAT_BOOKED, live.getShowId()));
        // Feature 3: notify the customer.
        try {
            ShowTime show = store.findShowTime(live.getShowId());
            notificationService.notifyBookingConfirmed(live.getUserName(), live, show);
        } catch (Exception e) {
            System.err.println("Notification failed: " + e.getMessage());
        }
        return new PaymentResult(true, live, transactionId, new ArrayList<String>());
    }

    /** Converts the booking's HELD seats to BOOKED (reserved) on the show, then removes the holds.
     *  A hold that has expired or belongs to another user is not converted - those seats simply
     *  stay free (they were already released by the expiry sweep). */
    private void convertHeldToBooked(Booking booking) {
        ShowTime show = store.findShowTime(booking.getShowId());
        if (show == null) {
            return;
        }
        List<String> seats = booking.getSeats();
        show.reserveSeats(seats);
        store.updateShowTime(show);
        // Remove the holds now that the seats are genuinely booked.
        for (String seat : seats) {
            SeatHold hold = store.activeHoldForSeat(show.getShowId(), seat);
            if (hold != null && hold.getUserName().equalsIgnoreCase(booking.getUserName())) {
                store.removeSeatHold(hold);
            }
        }
    }

    /* ------------------------------------------------------------------ *\
     * Small private helpers
     * ------------------------------------------------------------------ */

    /** Re-reads the booking by id, so the store - not the screen - decides what is being paid. */
    private Booking liveBookingOf(Booking booking) {
        return (booking == null) ? null : store.findBooking(booking.getBookingId());
    }

    /** Only a signed in customer pays for a booking, and only for their own. */
    private boolean belongsToCurrentUser(Booking booking) {
        User current = Session.currentUser();
        return current instanceof cinesmart.model.Customer
                && booking.getUserName().equals(current.getUserName());
    }
}