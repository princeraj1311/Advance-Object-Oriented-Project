package cinesmart.service;


import cinesmart.model.GiftCard;
import cinesmart.util.DataStore;
import cinesmart.util.IDGenerator;
import cinesmart.util.Session;
import cinesmart.util.UiUtil;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GiftCardService sells prepaid cards and spends them.
 *
 * It follows the two rules of this project:
 * <ol>
 *   <li>the DataStore is the truth - a card is read back from {@code data/giftcards.txt} by its code
 *       before a single rupee is taken off it, so two windows cannot spend the same balance;</li>
 *   <li>nothing is written unless every check passed, so a card is never left with a balance that
 *       does not match a purchase that really happened.</li>
 * </ol>
 *
 * Buying a card is deliberately NOT free: {@link #buy} returns the price the customer would pay at
 * a counter, and the screen says so. This is a simulation of the feature, not a way to obtain money.
 */
public class GiftCardService {

    /** Denominations the screen offers, in rupees. */
    public static final List<Integer> DENOMINATIONS =
            Collections.unmodifiableList(new ArrayList<Integer>() {
                private static final long serialVersionUID = 1L;
                {
                    for (int value : GiftCard.DENOMINATIONS) {
                        add(value);
                    }
                }
            });

    private final DataStore store;

    public GiftCardService() {
        this(null);
    }

    public GiftCardService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *\
     * Buying a card
     * ------------------------------------------------------------------ */

    /** The answer of {@link #buy}: the new card, or the reasons it was not issued. */
    public static final class Purchase {

        private final GiftCard card;
        private final List<String> problems;

        Purchase(GiftCard card, List<String> problems) {
            this.card = card;
            this.problems = problems;
        }

        public GiftCard getCard() {
            return card;
        }

        public boolean isSaved() {
            return problems.isEmpty() && card != null;
        }

        public List<String> getProblems() {
            return Collections.unmodifiableList(problems);
        }
    }

    /**
     * Issues a new card worth {@code denomination} rupees to the signed in customer.
     *
     * A card is only issued to a signed in customer, never to a guest: the card belongs to somebody,
     * and the file has to record who. The code is generated (never typed), so two cards can never
     * share one.
     */
    public Purchase buy(int denomination) {
        List<String> problems = new ArrayList<>();
        if (Session.isAdmin()) {
            problems.add("An admin account does not buy gift cards. Sign in as a customer.");
        } else if (!Session.isLoggedIn()) {
            problems.add("Please sign in before buying a gift card.");
        } else if (denomination <= 0 || denomination > GiftCard.MAX_VALUE) {
            problems.add("A gift card must be worth between Rs 1 and Rs "
                    + GiftCard.MAX_VALUE + ".");
        }
        if (!problems.isEmpty()) {
            return new Purchase(null, problems);
        }

        // Cards last a year, the way a real prepaid cinema card does.
        String expiresOn = LocalDate.now().plusYears(1).toString();
        GiftCard card = new GiftCard(IDGenerator.giftCardId(),
                IDGenerator.giftCardCode(), Session.currentUser().getUserName(),
                denomination, expiresOn);
        if (!store.addGiftCard(card)) {
            return new Purchase(null, Collections.singletonList(
                    "The gift card could not be saved, so none was charged. Please try again."));
        }
        return new Purchase(card, new ArrayList<String>());
    }

    /* ------------------------------------------------------------------ *\
     * Spending a card
     * ------------------------------------------------------------------ */

    /** The answer of {@link #spend}: what was taken off, or the reasons it was refused. */
    public static final class Redemption {

        private final GiftCard card;
        private final int amountPaid;
        private final int remaining;
        private final List<String> problems;

        Redemption(GiftCard card, int amountPaid, int remaining, List<String> problems) {
            this.card = card;
            this.amountPaid = amountPaid;
            this.remaining = remaining;
            this.problems = problems;
        }

        public GiftCard getCard() {
            return card;
        }

        /** Rupees actually taken off the card. */
        public int getAmountPaid() {
            return amountPaid;
        }

        /** What was left on the card afterwards - the "pay the rest by card" number. */
        public int getRemaining() {
            return remaining;
        }

        public boolean isPaid() {
            return problems.isEmpty() && amountPaid > 0;
        }

        public List<String> getProblems() {
            return Collections.unmodifiableList(problems);
        }

        /** "Paid Rs 800 from gift card CSGIFT-4821-KQ (Rs 1200 left)". */
        public String successText() {
            return "Paid Rs " + amountPaid + " from gift card " + card.getCode()
                    + "  \u00b7  Rs " + remaining + " left";
        }
    }

    /**
     * Takes as much as it can off a gift card, up to {@code amount}.
     *
     * A card may cover only PART of an order, exactly like a real one: a Rs 2000 card pays for a
     * Rs 850 booking and Rs 1150 is left. What this method will NOT do is go negative, hand over a
     * card that is not the customer's, or spend an expired card - the three rules a customer would
     * expect and the three that are checked here.
     *
     * @param code   the gift card code typed or picked on screen
     * @param amount the money the order costs
     * @param what   a short description saved in the card history, e.g. "Tickets CS2026.."
     */
    public Redemption spend(String code, int amount, String what) {
        List<String> problems = new ArrayList<>();
        if (Session.isAdmin()) {
            problems.add("An admin account does not pay with gift cards.");
            return new Redemption(null, 0, 0, problems);
        }
        if (amount <= 0) {
            problems.add("There is nothing to pay with a gift card.");
            return new Redemption(null, 0, 0, problems);
        }
        GiftCard card = findByCode(code);
        if (card == null) {
            problems.add("No gift card with the code " + UiUtil.clean(code).toUpperCase() + ".");
            return new Redemption(null, 0, 0, problems);
        }
        if (!card.getOwnerName().equals(currentUserName())) {
            problems.add("Gift card " + card.getCode() + " belongs to another account.");
            return new Redemption(card, 0, card.getBalance(), problems);
        }
        LocalDate today = LocalDate.now();
        if (card.isExpiredOn(today)) {
            problems.add("Gift card " + card.getCode() + " expired on " + card.getExpiresOn() + ".");
            return new Redemption(card, 0, card.getBalance(), problems);
        }
        if (card.getBalance() <= 0) {
            problems.add("Gift card " + card.getCode() + " has no balance left.");
            return new Redemption(card, 0, 0, problems);
        }

        int taking = Math.min(card.getBalance(), amount);
        // spend() only changes the balance when it can really do it, so a refused spend leaves
        // the card untouched on disk as well as in memory.
        if (!card.spend(taking, what)) {
            problems.add("Gift card " + card.getCode() + " could not be charged.");
            return new Redemption(card, 0, card.getBalance(), problems);
        }
        if (!store.updateGiftCard(card)) {
            // Put the money back before reporting the failure, so the file still matches the order.
            card.refund(taking, what);
            return new Redemption(card, 0, card.getBalance(), Collections.singletonList(
                    "The gift card could not be saved, so nothing was charged."));
        }
        return new Redemption(card, taking, card.getBalance(), new ArrayList<String>());
    }

    /** Puts money back on a card when a booking is cancelled - Stage 6 refunding to a gift card. */
    public boolean refund(String code, int amount, String what) {
        GiftCard card = findByCode(code);
        if (card == null || amount <= 0) {
            return false;
        }
        card.refund(amount, what);
        return store.updateGiftCard(card);
    }

    /* ------------------------------------------------------------------ *\
     * Reading cards
     * ------------------------------------------------------------------ */

    /**
     * The wallet of the signed in customer, newest first.
     *
     * A card of another account is never listed here, which is what keeps "my gift cards" honest
     * without needing a separate permission check on top of the owner check in spend().
     */
    public List<GiftCard> cardsOfCurrentUser() {
        return store.giftCardsForUser(currentUserName());
    }

    /** Every card on file, for an admin looking at what has been sold. */
    public List<GiftCard> allCards() {
        return store.getGiftCards();
    }

    /** The cards of this account that could still pay for something right now. */
    public List<GiftCard> usableCardsOfCurrentUser() {
        List<GiftCard> usable = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (GiftCard card : cardsOfCurrentUser()) {
            if (card.isUsableOn(today)) {
                usable.add(card);
            }
        }
        return usable;
    }

    /** Looks a card up by its code, ignoring case and spaces; null when there is no such card. */
    public GiftCard findByCode(String code) {
        String wanted = UiUtil.clean(code).toUpperCase();
        if (wanted.isEmpty()) {
            return null;
        }
        for (GiftCard card : store.getGiftCards()) {
            if (card.getCode().equals(wanted)) {
                return card;
            }
        }
        return null;
    }

    /** Total money still sitting on the cards of the signed in customer. */
    public int totalBalance() {
        int total = 0;
        for (GiftCard card : cardsOfCurrentUser()) {
            total += card.getBalance();
        }
        return total;
    }

    /** The wallet card printed as lines, for the dialog and the screenshot tool. */
    public List<String> cardLines(GiftCard card) {
        List<String> lines = new ArrayList<>();
        if (card == null) {
            return lines;
        }
        lines.add(card.getCode() + "  \u00b7  " + card.getStatus().label());
        lines.add(card.balanceText());
        lines.add("Issued " + card.getIssuedAt());
        lines.add(card.expiryText());
        for (String entry : card.historyLines()) {
            lines.add("  " + entry);
        }
        return lines;
    }

    private String currentUserName() {
        return Session.currentUser() == null ? "" : Session.currentUser().getUserName();
    }
}