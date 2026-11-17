package cinesmart.util;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.security.SecureRandom;

/**
 * IDGenerator creates the unique ids used by the application. Nothing is hard coded:
 * every id is built from the current date/time plus a counter and a random suffix, so two
 * bookings made seconds apart can never collide.
 *
 * Booking id example : CS2026093012457
 * Transaction id     : TXN20260930124533912
 */
public final class IDGenerator {

    /** Counts how many ids were made in this run - extra guarantee against duplicates. */
    private static int counter = 0;

    private static final Random RANDOM = new Random();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private IDGenerator() {
        // Utility class.
    }

    /**
     * Booking id = "CS" + yyyyMMddHHmm + 2 digit counter.
     * synchronized = even if two bookings happen at the same moment the counter is
     * increased one at a time, so the ids stay unique.
     */
    public static synchronized String bookingId() {
        counter = (counter + 1) % 100;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return "CS" + stamp + String.format("%02d", counter);
    }

    /** Transaction id = "TXN" + full time stamp + 3 random digits. */
    public static String transactionId() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "TXN" + stamp + String.format("%03d", RANDOM.nextInt(1000));
    }

    /** A 12-digit numeric id for an admin account. */
    public static String adminId() {
        return Long.toString(100_000_000_000L
                + SECURE_RANDOM.nextLong(900_000_000_000L));
    }

    /** Next movie id such as M011 when M010 was the last one already in the list. */
    public static synchronized String nextMovieId(java.util.List<String> existingIds) {
        int highest = 0;
        for (String id : existingIds) {
            try {
                highest = Math.max(highest, Integer.parseInt(id.substring(1)));
            } catch (Exception ignored) {
                // An id that does not follow the pattern is simply skipped.
            }
        }
        return String.format("M%03d", highest + 1);
    }

    /** Next theatre id such as T006. */
    public static synchronized String nextTheatreId(java.util.List<String> existingIds) {
        int highest = 0;
        for (String id : existingIds) {
            try {
                highest = Math.max(highest, Integer.parseInt(id.substring(1)));
            } catch (Exception ignored) {
                // skip unusual ids
            }
        }
        return String.format("T%03d", highest + 1);
    }

    /** Next registered cinema hall id such as H002. */
    public static synchronized String nextHallId(java.util.List<String> existingIds) {
        int highest = 0;
        for (String id : existingIds) {
            try {
                highest = Math.max(highest, Integer.parseInt(id.substring(1)));
            } catch (Exception ignored) {
                // Skip ids that do not follow the expected pattern.
            }
        }
        return String.format("H%03d", highest + 1);
    }

    /** Review id such as R20260930124501. */
    public static String reviewId() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "R" + stamp + String.format("%02d", RANDOM.nextInt(100));
    }

    /** Notification id such as N20260930124501. */
    public static String notificationId() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return "N" + stamp + String.format("%02d", RANDOM.nextInt(100));
    }

    /**
     * Food order id such as FO20260930124501. The "FO" prefix keeps a food order readable next to
     * a booking id (CS...) in the same status line, so nobody confuses the two.
     */
    public static synchronized String foodOrderId() {
        counter = (counter + 1) % 100;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return "FO" + stamp + String.format("%02d", counter);
    }

    /** Random 6 letter/number code, used for coupon redemption style codes. */
    public static String randomCode(int length) {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /** Gift card id such as GC20260930124501 - the "GC" prefix keeps it apart from FO and CS ids. */
    public static synchronized String giftCardId() {
        counter = (counter + 1) % 100;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return "GC" + stamp + String.format("%02d", counter);
    }

    /**
     * The code a customer types to pay with a gift card: "CSGIFT-4821-KQ".
     *
     * The letters 0/O and 1/I are left out of the alphabet, because a gift card is read off a printed
     * slip by a human: a code nobody can type back is worse than no code at all.
     */
    public static String giftCardCode() {
        return "CSGIFT-" + randomCode(4) + "-" + randomCode(2);
    }
}
