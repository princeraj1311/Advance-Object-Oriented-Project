package cinesmart.util;


import java.util.Calendar;

/**
 * ValidationUtil = one place for every input rule in CineSmart.
 *
 * The GUI classes only ask "is this valid?" and show the message returned here, so the
 * same rule can be reused by the admin forms, the payment screen and the tests.
 * Regular expressions are used because they are short and easy to defend in a viva.
 */
public final class ValidationUtil {

    /** 3-15 letters / digits / underscore, no spaces. */
    private static final String USERNAME_PATTERN = "^[A-Za-z0-9_]{3,15}$";

    /** Simple but realistic e-mail rule: text @ text . text */
    private static final String EMAIL_PATTERN =
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,10}$";

    /** Indian mobile style: 10 digits starting with 6-9. */
    private static final String PHONE_PATTERN = "^[6-9][0-9]{9}$";

    /** UPI ids look like name@bank (e.g. cine.smart@okbank). */
    private static final String UPI_PATTERN = "^[A-Za-z0-9._-]{2,60}@[A-Za-z]{2,20}$";

    /** Card numbers are 12-19 digits (spaces are removed before checking). */
    private static final String CARD_PATTERN = "^[0-9]{12,19}$";

    private ValidationUtil() {
        // Utility class: nothing to instantiate.
    }

    /* ------------------------------------------------------------------ *
     * Basic text checks
     * ------------------------------------------------------------------ */

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Removes leading/trailing spaces and converts null into "". */
    public static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static boolean isValidUserName(String value) {
        return value != null && value.matches(USERNAME_PATTERN);
    }

    public static boolean isValidFullName(String value) {
        String text = clean(value);
        // Letters, spaces, dot, apostrophe and hyphen only, 3 to 40 characters.
        return text.length() >= 3 && text.length() <= 40
                && text.matches("^[A-Za-z][A-Za-z .'-]*$");
    }

    public static boolean isValidEmail(String value) {
        return value != null && value.matches(EMAIL_PATTERN);
    }

    public static boolean isValidPhone(String value) {
        return value != null && value.matches(PHONE_PATTERN);
    }

    public static boolean isValidUpiId(String value) {
        return value != null && value.matches(UPI_PATTERN);
    }

    public static boolean isValidCardNumber(String value) {
        String digits = value == null ? "" : value.replaceAll("[\\s-]", "");
        // Step 1: right length and digits only. Step 2: Luhn checksum (see below).
        return digits.matches(CARD_PATTERN) && passesLuhn(digits);
    }

    public static boolean isValidCvv(String value) {
        return value != null && value.matches("^[0-9]{3,4}$");
    }

    public static boolean isValidPin(String value) {
        return value != null && value.matches("^[0-9]{4,6}$");
    }

    /**
     * Luhn checksum = the real algorithm every bank card uses.
     * Doubling alternate digits from the right and checking that the total divides by 10
     * rejects mistyped card numbers even when they have the correct length.
     */
    public static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleNext = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return sum % 10 == 0;
    }

    /** Accepts MM/YY and rejects cards that are already expired. */
    public static boolean isValidExpiry(String value) {
        String text = clean(value);
        if (!text.matches("^(0[1-9]|1[0-2])/[0-9]{2}$")) {
            return false;
        }
        int month = Integer.parseInt(text.substring(0, 2));
        int year = 2000 + Integer.parseInt(text.substring(3));
        Calendar now = Calendar.getInstance();
        Calendar expiry = Calendar.getInstance();
        // The card stays valid through the whole of its month, so we test the next month.
        expiry.set(year, month, 1, 0, 0, 0);
        return expiry.after(now);
    }

    /* ------------------------------------------------------------------ *
     * Password rules
     * ------------------------------------------------------------------ */

    /** Minimum rule used by registration: 6+ characters with a letter and a digit. */
    public static boolean isAcceptablePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 20) {
            return false;
        }
        return countLetters(password) > 0 && countDigits(password) > 0;
    }

    /**
     * Scores a password from 0 (weak) to 4 (very strong).
     * Drives the little JProgressBar on the registration form.
     */
    public static int passwordScore(String password) {
        if (isBlank(password)) {
            return 0;
        }
        int score = 0;
        if (password.length() >= 6) {
            score++;
        }
        if (password.length() >= 10) {
            score++;
        }
        if (countDigits(password) > 0 && countLetters(password) > 0) {
            score++;
        }
        if (countUpperCase(password) > 0 && countSpecial(password) > 0) {
            score++;
        }
        return score; // 0..4
    }

    /** Human readable label for {@link #passwordScore(String)}. */
    public static String passwordStrengthLabel(int score) {
        switch (score) {
            case 0:
            case 1:
                return "Weak";
            case 2:
                return "Fair";
            case 3:
                return "Good";
            default:
                return "Strong";
        }
    }

    /* ------------------------------------------------------------------ *
     * Booking related checks (used again from Stage 4 onwards)
     * ------------------------------------------------------------------ */

    public static boolean isValidSeatCount(int count, int maxAllowed) {
        return count >= 1 && count <= maxAllowed;
    }

    /**
     * Hides the middle of an e-mail address, e.g. "prince.rajan@gmail.com" becomes
     * "pr****an@gmail.com". Used wherever an account's e-mail is shown on screen so that a
     * shoulder surfer (or a viva screenshot) cannot read the full address.
     */
    public static String maskEmail(String email) {
        String text = clean(email);
        int at = text.indexOf('@');
        if (at < 2) {
            return "not recorded";
        }
        String name = text.substring(0, at);
        String domain = text.substring(at);
        if (name.length() <= 4) {
            return name.charAt(0) + "****" + domain;
        }
        return name.substring(0, 2) + "****" + name.substring(name.length() - 2) + domain;
    }

    /** True when the given yyyy-MM-dd text is today or in the future. */
    public static boolean isTodayOrFuture(String yyyyMmDd) {
        try {
            java.time.LocalDate given = java.time.LocalDate.parse(yyyyMmDd);
            return !given.isBefore(java.time.LocalDate.now());
        } catch (Exception e) {
            return false; // unparsable text is simply treated as invalid
        }
    }

    /* ------------------------------------------------------------------ *
     * small private counting helpers
     * ------------------------------------------------------------------ */

    private static int countLetters(String text) {
        return countMatching(text, Character::isLetter);
    }

    private static int countDigits(String text) {
        return countMatching(text, Character::isDigit);
    }

    private static int countUpperCase(String text) {
        return countMatching(text, Character::isUpperCase);
    }

    private static int countSpecial(String text) {
        return countMatching(text, c -> !Character.isLetterOrDigit(c));
    }

    /** Tiny reusable counter so the four helpers above do not repeat the loop. */
    private static int countMatching(String text, java.util.function.IntPredicate test) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            if (test.test(text.charAt(i))) {
                total++;
            }
        }
        return total;
    }
}

