import cinesmart.model.Booking;
import cinesmart.model.CastMember;
import cinesmart.model.Customer;
import cinesmart.model.Movie;
import cinesmart.model.Review;
import cinesmart.model.GiftCard;
import cinesmart.model.Locality;
import cinesmart.model.Offer;
import cinesmart.model.SeatMap;
import cinesmart.service.BookingService;
import cinesmart.service.CastService;
import cinesmart.service.GiftCardService;
import cinesmart.service.LocationService;
import cinesmart.service.OfferService;
import cinesmart.service.PaymentService;
import cinesmart.service.PaymentService.PaymentDetails;
import cinesmart.service.ReviewService;
import cinesmart.util.DataStore;
import cinesmart.util.Session;
import java.time.LocalDate;
import java.util.List;

/** Scratch harness: proves the new services work against real data files. */
public class Smoke {

    private static int passed;
    private static int failed;

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    public static void main(String[] args) {
        DataStore store = DataStore.get();
        Session.login(new Customer("demo", "Demo Customer", "demo@cinesmart.in",
                "9000000002", "Kochi", "2003-05-14", "Female"));

        System.out.println("\n== Localities ==");
        LocationService locations = new LocationService();
        List<Locality> all = locations.allLocalities();
        check("catalogue loaded (" + all.size() + " rows)", all.size() >= 26);
        check("every row has coordinates", all.stream().allMatch(Locality::hasPosition));
        check("search 'koc' finds Kochi",
                locations.search("koc").stream().anyMatch(p -> p.getCity().equals("Kochi")));
        check("search 'kor' finds Koramangala",
                locations.search("kor").stream().anyMatch(p -> p.getArea().equals("Koramangala")));
        check("city centre of Kochi exists", locations.cityCentreOf("Kochi") != null);
        check("popular chips count == 8", locations.popularPlaces().size() == 8);
        check("areas of Kochi", locations.areasOf("Kochi").toString().equals("[Kakkanad, Marine Drive]"));

        System.out.println("\n== Detection ==");
        LocationService.Detection byPin = locations.detect();
        check("-Dcinesmart.location pin wins", byPin.isPrecise());
        System.out.println("        -> " + byPin.summary());
        LocationService.Nearby hall = locations.nearestHall("Kochi", byPin.getPlace());
        check("nearest Kochi hall found", hall != null);
        check("nearest hall is Q Cinemas", hall != null && hall.getName().equals("Q Cinemas"));
        System.out.println("        -> " + (hall == null ? "?" : hall.getName() + " " + hall.getDistanceText()));
        check("remember() saves the place",
                locations.remember(byPin.getPlace()) && locations.savedPlace() != null);

        System.out.println("\n== Offers ==");
        OfferService offers = new OfferService();
        check("8 offers seeded", store.getOffers().size() == 8);
        check("flat offer reads 'Flat Rs 50 off'",
                "Flat Rs 50 off".equals(store.findOffer("FIRST50").valueText()));
        check("capped percent reads '20% off, up to Rs 200'",
                "20% off, up to Rs 200".equals(store.findOffer("CINE20").valueText()));
        check("uncapped percent reads '30% off'",
                "30% off".equals(new Offer("X", "X", "t", "d", Offer.Kind.PERCENT,
                        30, 0, 0, "", "", Offer.Scope.ANY).valueText()));
        check("a percentage cap is printed",
                "15% off, up to Rs 150".equals(store.findOffer("WEEKEND15").valueText()));
        check("'all' filter shows the food-only code too",
                offers.offersFor("Bengaluru", Offer.Scope.ANY, LocalDate.now()).stream()
                        .anyMatch(o -> o.getCode().equals("SNACK75")));
        check("'tickets only' filter hides the food code",
                offers.offersFor("Bengaluru", Offer.Scope.TICKETS, LocalDate.now()).stream()
                        .noneMatch(o -> o.getCode().equals("SNACK75")));
        check("'food only' filter shows only food-capable codes",
                offers.offersFor("Bengaluru", Offer.Scope.FOOD, LocalDate.now()).stream()
                        .allMatch(o -> o.worksFor(Offer.Scope.FOOD)));
        OfferService.Result flat = offers.check("first50", "Kochi", Offer.Scope.TICKETS, 800);
        check("lowercase code accepted, Rs 50 off", flat.isAllowed() && flat.getDiscount() == 50);
        OfferService.Result pct = offers.check("CINE20", "Kochi", Offer.Scope.TICKETS, 800);
        check("20% of 800 = 160 (under the 200 cap)", pct.isAllowed() && pct.getDiscount() == 160);
        OfferService.Result capped = offers.check("CINE20", "Kochi", Offer.Scope.TICKETS, 2000);
        check("20% of 2000 = 400 capped to 200", capped.isAllowed() && capped.getDiscount() == 200);
        OfferService.Result tooSmall = offers.check("CINE20", "Kochi", Offer.Scope.TICKETS, 100);
        check("below Rs 500 minimum refused", !tooSmall.isAllowed());
        check("expired code refused", !offers.check("EXPIRED50", "Kochi", Offer.Scope.ANY, 800).isAllowed());
        check("unknown code refused", !offers.check("NOPE", "Kochi", Offer.Scope.ANY, 800).isAllowed());
        check("food code refused on tickets",
                !offers.check("SNACK75", "Kochi", Offer.Scope.TICKETS, 800).isAllowed());
        check("food code ok on food", offers.check("SNACK75", "Kochi", Offer.Scope.FOOD, 800).isAllowed());
        check("Kochi code refused in Bengaluru",
                !offers.check("KOCHI100", "Bengaluru", Offer.Scope.ANY, 800).isAllowed());
        check("Kochi code ok in Kochi",
                offers.check("KOCHI100", "Kochi", Offer.Scope.ANY, 800).isAllowed());
        rest();
    }
    /** Second half, kept separate so each edit stays a sensible size. */
    static void rest() {
        DataStore store = DataStore.get();
        LocationService locations = new LocationService();
        OfferService offers = new OfferService();

        System.out.println("\n== Offers part 2 ==");
        // One use per customer, and it must survive a reload from the file.
        OfferService.Result redeemed = offers.redeem("FIRST50", "Kochi", Offer.Scope.ANY, 800);
        check("redeem records the use", redeemed.isAllowed());
        check("same code refused the second time",
                !offers.check("FIRST50", "Kochi", Offer.Scope.ANY, 800).isAllowed());
        List<Offer> visible = offers.offersFor("Kochi", Offer.Scope.ANY, LocalDate.now());
        check("used offer still listed for the screen", visible.stream()
                .anyMatch(o -> o.getCode().equals("FIRST50")));
        check("expired offer not listed", visible.stream()
                .noneMatch(o -> o.getCode().equals("EXPIRED50")));

        System.out.println("\n== Gift cards ==");
        GiftCardService cards = new GiftCardService();
        GiftCardService.Purchase bought = cards.buy(2000);
        check("card bought", bought.isSaved());
        GiftCard card = bought.getCard();
        System.out.println("        -> " + card.getCode() + "  " + card.balanceText());
        check("balance equals denomination", card.getBalance() == 2000);
        check("owner recorded", card.getOwnerName().equals("demo"));
        check("buy refused for Rs 0", !cards.buy(0).isSaved());
        check("buy refused above the limit", !cards.buy(999999).isSaved());

        GiftCardService.Redemption partial = cards.spend(card.getCode(), 800, "CS-TEST-1");
        check("partial spend of 800", partial.isPaid() && partial.getAmountPaid() == 800);
        check("balance now 1200", partial.getRemaining() == 1200);
        check("history recorded", !card.historyLines().isEmpty());

        // Spending from a second "window" must see the reduced balance, because it is re-read.
        GiftCard again = cards.findByCode(card.getCode().toLowerCase());
        check("re-read from store shows 1200", again.getBalance() == 1200);
        GiftCardService.Redemption rest = cards.spend(card.getCode(), 5000, "CS-TEST-2");
        check("spend capped by balance", rest.getAmountPaid() == 1200);
        check("empty card cannot pay", !cards.spend(card.getCode(), 100, "CS-TEST-3").isPaid());
        check("unknown code refused", !cards.spend("CSGIFT-XXXX-YY", 100, "x").isPaid());
        check("wallet lists the card", cards.cardsOfCurrentUser().size() == 1);

        cards.refund(card.getCode(), 200, "Refund of CS-TEST-2");
        check("refund restores balance", cards.findByCode(card.getCode()).getBalance() == 200);

        System.out.println("\n== Round trip ==");
        check("Offer line round-trips",
                Offer.fromDataLine(store.getOffers().get(0).toDataLine()).getCode()
                        .equals(store.getOffers().get(0).getCode()));
        check("GiftCard line round-trips",
                GiftCard.fromDataLine(card.toDataLine()).getBalance() == card.getBalance());
        check("Locality line round-trips",
                Locality.fromDataLine(locations.allLocalities().get(0).toDataLine()).displayName()
                        .equals(locations.allLocalities().get(0).displayName()));
        check("10-column offer line (old file) loads",
                Offer.fromDataLine("OFF09|X|Short|No description|FLAT|10|0|0|||").getCode().equals("X"));

        paymentJourney();
        System.out.println("\n" + passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    /**
     * The Stage 5 journey with a discount and a gift card, to prove the new code did not break it.
     */
    private static void paymentJourney() {
        System.out.println("\n== Payment with an offer ==");
        DataStore store = DataStore.get();
        PaymentService payments = new PaymentService();
        BookingService bookings = new BookingService();
        OfferService offers = new OfferService();

        // Hold two seats on a real show, exactly like the seat map does.
        Booking booking = hold(bookings, 0, 500, "A1", "A2");
        check("seats held, booking created", booking != null && booking.seatCount() == 2);
        int full = booking.getAmount();
        System.out.println("        -> booking " + booking.getBookingId() + " for Rs " + full);

        // A card number that passes Luhn: 4111 1111 1111 1111.
        PaymentDetails card = new PaymentDetails().method(PaymentService.METHOD_CARD)
                .cardNumber("4111111111111111").cardHolder("Demo Customer")
                .cvv("123").expiry("12/30");

        OfferService.Result offer = offers.redeem("CINE20", "Kochi", Offer.Scope.TICKETS, full);
        check("20% offer accepted on the booking", offer.isAllowed());
        PaymentService.PaymentResult paid = payments.pay(booking, card, offer.getDiscount());
        check("payment succeeded with the discount", paid.isSuccess());
        check("booking is now PAID", paid.getBooking() != null && paid.getBooking().isPaid());

        int expected = full - offer.getDiscount();
        Booking stored = store.findBooking(booking.getBookingId());
        check("saved amount is the discounted one (" + expected + " of " + full + ")",
                stored != null && stored.getAmount() == expected);
        check("paid method recorded", stored != null
                && PaymentService.METHOD_CARD.equals(stored.getMethod()));
        check("transaction id written", stored != null && !stored.getPaymentRef().isEmpty());
        check("movie booking count increased", paid.getBooking() != null);

        System.out.println("\n== Payment with a gift card ==");
        GiftCardService cards = new GiftCardService();
        GiftCardService.Purchase purchase = cards.buy(5000);
        check("Rs 5000 card bought", purchase.isSaved());
        String code = purchase.getCard().getCode();

        Booking second = hold(bookings, 1, 0, "B1");
        int due = second.getAmount();
        // Two refusals first, and both must leave the booking untouched so the real payment can run.
        check("gift card with no code refused",
                !payments.pay(second, new PaymentDetails()
                        .method(PaymentService.METHOD_GIFT_CARD)).isSuccess());
        check("unknown gift card refused",
                !payments.pay(second, new PaymentDetails()
                        .method(PaymentService.METHOD_GIFT_CARD)
                        .giftCardCode("CSGIFT-XXXX-YY")).isSuccess());
        check("a refused gift card leaves the booking unpaid",
                store.findBooking(second.getBookingId()).isAwaitingPayment());
        check("a refused gift card is not charged",
                cards.findByCode(code).getBalance() == 5000);

        PaymentService.PaymentResult giftPaid = payments.pay(second, new PaymentDetails()
                .method(PaymentService.METHOD_GIFT_CARD).giftCardCode(code));
        check("gift card payment succeeded", giftPaid.isSuccess());
        check("card reduced by the amount", cards.findByCode(code).getBalance() == 5000 - due);
        check("booking amount unchanged by the card",
                store.findBooking(second.getBookingId()).getAmount() == due);
        check("gift card is the recorded method",
                PaymentService.METHOD_GIFT_CARD.equals(
                        store.findBooking(second.getBookingId()).getMethod()));

        System.out.println("\n== Gift card must cover the whole amount ==");
        GiftCardService.Purchase small = cards.buy(100);
        Booking third = hold(bookings, 2, 0, "C1");
        List<String> refused = payments.problemsWith(third,
                new PaymentDetails().method(PaymentService.METHOD_GIFT_CARD)
                        .giftCardCode(small.getCard().getCode()));
        check("a card that cannot cover it is refused", !refused.isEmpty());
        System.out.println("        -> " + refused.get(0));
        check("the booking is still unpaid", store.findBooking(
                third.getBookingId()).isAwaitingPayment());

        System.out.println("\n== Backwards compatibility ==");
        Booking fourth = hold(bookings, 3, 0, "D1");
        PaymentService.PaymentResult plain = payments.pay(fourth, card);
        check("old two-argument pay() still works", plain.isSuccess());
        check("old pay() charges the full amount",
                store.findBooking(fourth.getBookingId()).getAmount() == fourth.getAmount());

        creditsAndReviews();
    }

    /**
     * The two new screen features: the credits of a film and what readers and critics wrote about
     * it. Everything is read through the services, never through the file.
     */
    private static void creditsAndReviews() {
        DataStore store = DataStore.get();
        CastService credits = new CastService();
        ReviewService reviews = new ReviewService();

        System.out.println("\n== Cast and crew ==");
        check("credits seeded (" + store.getCastMembers().size() + " rows)",
                store.getCastMembers().size() >= 100);
        Movie inception = store.findMovie("M001");
        check("six actors on the first film", credits.castOf(inception).size() == 6);
        check("four crew names on the first film", credits.crewOf(inception).size() == 4);
        check("the director comes off the credits",
                "Christopher Nolan".equals(credits.directorOf(inception)));
        CastMember leo = credits.castOf(inception).get(0);
        check("an actor is credited with the part", "as Dom Cobb".equals(leo.subtitle()));
        check("initials of a two word name", "LD".equals(leo.initials()));
        check("a role is credited with the job",
                "Cinematography".equals(credits.crewOf(inception).get(3).subtitle()));
        check("most cast have a photograph",
                credits.castOf(inception).stream().filter(CastMember::hasPhoto).count() >= 5);
        check("a film with no credits file gives an empty list, not null",
                credits.castOf(store.findMovie("M999")).isEmpty());
        System.out.println("\n== Reviews ==");
        check("reviews seeded (" + store.getReviews().size() + " rows)",
                store.getReviews().size() >= 40);
        check("four user reviews on the first film",
                reviews.audienceReviews(inception).size() == 4);
        check("three critic reviews on the first film",
                reviews.criticReviews(inception).size() == 3);
        check("audience reviews come most-liked first",
                reviews.audienceReviews(inception).get(0).getLikes() >= 846);
        check("the headline reads 41.2K",
                "Summary of 41.2K reviews.".equals(reviews.summaryText(inception)));
        check("critic score counts the critics",
                reviews.criticScoreText(inception).endsWith("from 3 critics"));
        check("four sentiment chips", reviews.topTags(inception).size() == 4);
        check("chips come most-used first",
                "MindBending".equals(reviews.topTags(inception).get(0).name()));
        check("a big count is shortened",
                "24.2K".equals(reviews.topTags(inception).get(0).countText()));
        Review top = reviews.audienceReviews(inception).get(0);
        check("a review scores itself 10/10", "10/10".equals(top.ratingText()));
        check("a review knows when it was posted", top.timeAgoText().endsWith("ago"));
        check("hashtags carry their #", top.tagList().contains("#MindBending"));
        check("a long body is shortened on a word boundary",
                top.shortBody(60).endsWith("...more") && !top.shortBody(60).contains("  "));
        check("the full body is kept in the file", top.getBody().length() > 100);
        System.out.println("\n== A film nobody has seen ==");
        Movie coming = store.findMovie("M011");
        check("a coming-soon film has no reviews",
                reviews.audienceReviews(coming).isEmpty() && reviews.criticReviews(coming).isEmpty());
        check("and reports no reception", !coming.hasReception());
        check("its score line says so",
                "No critic reviews yet".equals(reviews.criticScoreText(coming)));

        System.out.println("\n== Damaged and older files ==");
        check("7-column cast line reads back",
                "A B".equals(CastMember.fromDataLine("M099|CAST|1|A B|Actor|X|").getName()));
        check("a cast line without a photo loads",
                !CastMember.fromDataLine("M099|CREW|2|C D|Director||").hasPhoto());
        check("an unknown group falls back to CAST",
                CastMember.fromDataLine("M099|WAT|3|E F|Producer||").isCast());
        check("11-column review line reads back",
                "8/10".equals(Review.fromDataLine(
                        "RV1|M099|CRITIC|Paper|8|10|1|2026-10-01||Head|Body here").ratingText()));
        check("a critic review has no hashtags",
                Review.fromDataLine("RV2|M099|CRITIC|Paper|8|1|0|2026-10-01||H|B").tagList().isEmpty());
        check("an impossible score becomes unrated",
                Review.fromDataLine("RV3|M099|AUDIENCE|U|13|1|0|2026-10-01||H|B").getRating() == 0);
        check("a broken date does not throw", "nonsense".equals(
                Review.fromDataLine("RV4|M099|AUDIENCE|U|5|1|0|nonsense||H|B").timeAgoText()));
        check("12-column movie line (old file) still loads",
                Movie.fromDataLine("M099|Old|English|Drama|100|UA|2026-01-01|7.0|D|C|B|0")
                        .getTotalReviews() == 0);
        Movie legacyLine = Movie.fromDataLine(
                "M001|Inception|English|Sci-Fi|169|UA|2026-01-01|8.8|D|L|Nolan|DiCaprio");
        check("a 12-column film line never claims '0 reviews' above real cards",
                "7 reviews".equals(reviews.reviewCountText(legacyLine))
                        && "Summary of 7 reviews.".equals(reviews.summaryText(legacyLine)));
        check("a stored headline total still wins over the card count",
                "41.2K reviews".equals(reviews.reviewCountText(inception)));
        check("a null film asks for no review count safely",
                "0 reviews".equals(reviews.reviewCountText(null)));
        check("13-column movie line (poster era) still loads",
                Movie.fromDataLine("M099|Old|English|Drama|100|UA|2026-01-01|7.0|D|C|B|0|http://x")
                        .hasPoster());
        check("a 15-column movie line round-trips the reviews",
                "26.9K reviews".equals(Movie.fromDataLine(
                        "M099|Old|English|Drama|100|UA|2026-01-01|7.0|D|C|B|0||26900"
                                + "|SuperDirection=15387").reviewCountText()));
        Movie tmdbMovie = new Movie("TMDB_123", "Remote", "English", "Drama", 0, "NR",
                "2026-08-01", 7.5, "", "", "Overview");
        tmdbMovie.setTotalReviews(12000);
        check("TMDb reception is labelled as votes",
                "12.0K votes".equals(tmdbMovie.reviewCountText()));
        check("TMDb review link uses the numeric movie id",
                "https://www.themoviedb.org/movie/123/reviews"
                        .equals(tmdbMovie.tmdbReviewsUrl()));
        check("a broken tag pair is skipped, not thrown",
                Movie.fromDataLine("M099|Old|English|Drama|100|UA|2026-01-01|7.0|D|C|B|0||0"
                        + "|Good=5;broken;=6;Bad=x").topReviewTags(5).size() == 1);
        check("an unknown movie asks for no cast safely",
                credits.castOf(null).isEmpty() && reviews.audienceReviews(null).isEmpty());
    }

    /**
     * Holds seats on one show through the real service calls and returns the booking it opened.
     *
     * A seat that is already taken is skipped, so the helper never fails because of the demo data's
     * random booked counts - the test is about payment, not about luck.
     */
    private static Booking hold(BookingService bookings, int showIndex, int minimumAmount,
                                String... wanted) {
        DataStore store = DataStore.get();
        for (int i = showIndex; i < store.getShowTimes().size(); i++) {
            cinesmart.model.ShowTime show = store.getShowTimes().get(i);
            if (show.isPast()) {
                continue;
            }
            SeatMap map = bookings.seatMapFor(show);
            for (String seat : map.allSeats()) {
                if (!map.isAvailable(seat)) {
                    continue;
                }
                map.toggle(seat);
                if (map.selectedCount() == wanted.length) {
                    break;
                }
            }
            if (map.selectedCount() != wanted.length) {
                continue;
            }
            if (show.getPrice() * wanted.length < minimumAmount) {
                continue;
            }
            BookingService.Receipt receipt = bookings.holdSeats(show, map);
            if (receipt != null) {
                return store.findBooking(receipt.getBookingId());
            }
        }
        return null;
    }
}
