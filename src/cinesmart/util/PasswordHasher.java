package cinesmart.util;


import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * PasswordHasher keeps plain passwords OUT of the data file.
 *
 * For every user we generate a random "salt" and store SHA-256(salt + ":" + password).
 * Two users with the same password therefore get two different stored values, which is
 * the standard textbook reason for salting.
 *
 * Only standard JDK classes are used (MessageDigest + SecureRandom) so the logic can be
 * explained easily in a viva.
 */
public final class PasswordHasher {

    /** One strong random generator reused for every salt (it is thread safe). */
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Utility class: no objects should ever be created from this class.
    }

    /** Creates a random 16 character hex salt such as "9f2ac0b31d7e4455". */
    public static String newSalt() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /** Returns the SHA-256 fingerprint of "salt:plainPassword" as a hex string. */
    public static String hash(String plainPassword, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] mixed = (salt + ":" + plainPassword).getBytes("UTF-8");
            return toHex(digest.digest(mixed));
        } catch (Exception e) {
            // SHA-256 and UTF-8 always exist in the JDK, so this should never happen.
            // We still handle it instead of letting the program crash.
            throw new IllegalStateException("Password hashing failed: " + e.getMessage(), e);
        }
    }

    /** Compares a typed password with the stored hash. */
    public static boolean matches(String plainPassword, String salt, String storedHash) {
        if (plainPassword == null || salt == null || storedHash == null) {
            return false;
        }
        // isEqual() compares byte arrays for us (and does not stop at the first difference).
        byte[] typed = hash(plainPassword, salt).getBytes();
        byte[] stored = storedHash.getBytes();
        return MessageDigest.isEqual(typed, stored);
    }

    /** Small helper: turns bytes into lower case hex text. */
    private static String toHex(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                text.append('0');
            }
            text.append(hex);
        }
        return text.toString();
    }

    /** Unused-but-documented helper kept for future features (token generation). */
    public static String randomHex(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }
}
