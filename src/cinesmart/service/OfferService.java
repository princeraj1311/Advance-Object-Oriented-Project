package cinesmart.service;


import cinesmart.model.Offer;
import cinesmart.util.DataStore;
import cinesmart.util.Session;
import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * OfferService decides whether a coupon may be used, and by how much it reduces an order.
 *
 * It follows the two rules of this project:
 * <ol>
 *   <li>the DataStore is the truth - the code is looked up in {@code data/offers.txt} on every
 *       attempt, so a code switched off in another window cannot be redeemed from a stale screen;</li>
 *   <li>nothing is written unless every check passed, so a discount is only ever recorded for a
 *       purchase that really happened.</li>
 * </ol>
 *
 * The arithmetic lives in the model ({@link Offer#discountOn}) and the permission to use a code
 * lives here. That split means the offer card on the Offers screen and the same code typed on the
 * payment screen can never disagree about what it is worth.
 */
public class OfferService {

    /** Longest code accepted from the customer, so a paste of a whole paragraph is refused. */
    public static final int MAX_CODE_LENGTH = 24;

    private final DataStore store;

    public OfferService() {
        this(null);
    }

    public OfferService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *\
     * Finding offers
     * ------------------------------------------------------------------ */

    /**
     * The offers a customer in this city may see for this kind of purchase, biggest saving first.
     *
     * A code already used on the account is still returned: the screen shows it greyed out with
     * "already used" rather than pretending it never existed, which is what a customer who saved a
     * screenshot of last week's offer expects to see.
     */
    public List<Offer> offersFor(String city, Offer.Scope scope, LocalDate today) {
        List<Offer> found = new ArrayList<>();
        for (Offer offer : store.getOffers()) {
            if (offer.isDisabledOn() || offer.isExpiredOn(today)) {
                continue; // a dead code is not an offer any more
            }
            if (!offer.worksInCity(city) || !offer.worksFor(scope)) {
                continue;
            }
            found.add(offer);
        }
        Collections.sort(found, (a, b) -> {
            int byValue = Integer.compare(b.getValue(), a.getValue());
            return (byValue != 0) ? byValue : a.getCode().compareTo(b.getCode());
        });
        return found;
    }

    /** Looks a code up, ignoring case and surrounding spaces; null when there is no such code. */
    public Offer findByCode(String code) {
        String wanted = UiUtil.clean(code).toUpperCase();
        if (wanted.isEmpty()) {
            return null;
        }
        for (Offer offer : store.getOffers()) {
            if (offer.getCode().equals(wanted)) {
                return offer;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ *\
     * Checking a code against an order
     * ------------------------------------------------------------------ */

    /**
     * The result of typing a code: either the saving it gives, or the reasons it cannot be used.
     *
     * {@link #isAllowed()} decides whether the amount may be reduced, and {@link #getProblems()}
     * explains a refusal - both are worked out once, here, so the screen never has to repeat a rule.
     */
    public static final class Result {

        private final Offer offer;
        private final int discount;
        private final List<String> problems;

        Result(Offer offer, int discount, List<String> problems) {
            this.offer = offer;
            this.discount = discount;
            this.problems = problems;
        }

        public Offer getOffer() {
            return offer;
        }

        /** Rupees taken off; always 0 when {@link #isAllowed()} is false. */
        public int getDiscount() {
            return discount;
        }

        public boolean isAllowed() {
            return problems.isEmpty() && discount > 0;
        }

        public List<String> getProblems() {
            return Collections.unmodifiableList(problems);
        }

        /** "FIRST50 applied - you saved Rs 50" for the payment screen. */
        public String successText() {
            return offer.getCode() + " applied \u00b7 you saved Rs " + discount;
        }

        /** The label shown while no code is typed, so it is never blank. */
        public String emptyText() {
            return "No offer code applied";
        }
    }

    /**
     * Checks a code against an order without changing anything - this is what the Apply button and
     * the hint label call, so typing a code can never mark it as used.
     */
    public Result check(String code, String city, Offer.Scope scope, int amount) {
        return check(code, city, scope, amount, LocalDate.now());
    }

    /** The same check with the day supplied, so a test can prove an expired code is refused. */
    public Result check(String code, String city, Offer.Scope scope, int amount, LocalDate today) {
        List<String> problems = new ArrayList<>();
        if (amount <= 0) {
            problems.add("There is nothing to discount yet.");
            return new Result(null, 0, problems);
        }
        Offer offer = findByCode(code);
        if (offer == null) {
            problems.add("No offer with the code " + UiUtil.clean(code).toUpperCase() + ".");
            return new Result(null, 0, problems);
        }
        problems.addAll(offer.problemsWith(currentUserName(), city, scope, amount, today));
        if (!problems.isEmpty()) {
            return new Result(offer, 0, problems);
        }
        return new Result(offer, offer.discountOn(amount), new ArrayList<String>());
    }

    /**
     * Checks a code and, when it is good, records the use so the same customer cannot use it twice.
     *
     * This is the method a purchase calls. It is deliberately the ONLY place that writes an offer,
     * so "used once per customer" can never be bypassed by calling the wrong method. The write happens
     * only after the discount has been accepted, and a failed save is reported rather than hidden.
     *
     * @return the same {@link Result}, with a discount set when the code was allowed
     */
    public Result redeem(String code, String city, Offer.Scope scope, int amount) {
        Result checked = check(code, city, scope, amount);
        if (!checked.isAllowed()) {
            return checked;
        }
        Offer offer = checked.getOffer();
        offer.markUsedBy(currentUserName());
        if (!store.updateOffer(offer)) {
            // The purchase must not go through on a discount the app failed to record.
            return new Result(offer, 0, Collections.singletonList(
                    "The offer could not be recorded, so it was not applied. Please try again."));
        }
        return checked;
    }

    /* ------------------------------------------------------------------ *\
     * Lines for the screens
     * ------------------------------------------------------------------ */

    /**
     * The full terms of one offer, one point per line - what the Offers screen prints under the code
     * so a customer can read the conditions before typing it.
     */
    public List<String> offerLines(Offer offer) {
        List<String> lines = new ArrayList<>();
        if (offer == null) {
            return lines;
        }
        lines.add(offer.getCode() + "  \u00b7  " + offer.getTitle());
        if (!offer.getDescription().isEmpty()) {
            lines.add(offer.getDescription());
        }
        lines.add(offer.valueText());
        lines.add(offer.minimumText());
        lines.add(offer.validityText());
        lines.add("Works for " + offer.getScope().label().toLowerCase());
        lines.add("Cities: " + (offer.getCities().isEmpty() ? "all" : offer.getCities()));
        lines.add(offer.usageText(currentUserName()));
        return lines;
    }

    /** The current customer, or an empty name when nobody is signed in. */
    private String currentUserName() {
        return (Session.currentUser() == null) ? "" : Session.currentUser().getUserName();
    }
}