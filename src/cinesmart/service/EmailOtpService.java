package cinesmart.service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Delivers short-lived, single-use email sign-in codes. */
final class EmailOtpService {

    private static final long EXPIRY_MILLIS = 10 * 60 * 1000L;
    private static final long RESEND_WAIT_MILLIS = 60 * 1000L;
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    synchronized LoginChallengeResult send(String accountKey, String email) {
        long now = System.currentTimeMillis();
        Challenge existing = challenges.get(accountKey);
        if (existing != null && now - existing.sentAt < RESEND_WAIT_MILLIS) {
            return LoginChallengeResult.failure("A verification code was just sent. Please wait "
                    + "before requesting another.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        try {
            SmtpEmailSender.send(email, "CineSmart sign-in verification code",
                    "Your CineSmart verification code is " + code + ". It expires in 10 minutes "
                    + "and can be used once.");
        } catch (IOException e) {
            challenges.remove(accountKey);
            return LoginChallengeResult.failure("Could not send the verification email. "
                    + e.getMessage());
        }
        challenges.put(accountKey, new Challenge(code, now));
        return LoginChallengeResult.success("Verification code sent.", email);
    }

    synchronized boolean verify(String accountKey, String enteredCode) {
        Challenge challenge = challenges.get(accountKey);
        long now = System.currentTimeMillis();
        if (challenge == null || now - challenge.sentAt >= EXPIRY_MILLIS
                || challenge.attempts >= MAX_ATTEMPTS) {
            challenges.remove(accountKey);
            return false;
        }

        boolean matches = MessageDigest.isEqual(
                challenge.code.getBytes(StandardCharsets.UTF_8),
                (enteredCode == null ? "" : enteredCode.trim()).getBytes(StandardCharsets.UTF_8));
        if (matches) {
            challenges.remove(accountKey);
            return true;
        }
        challenge.attempts++;
        if (challenge.attempts >= MAX_ATTEMPTS) {
            challenges.remove(accountKey);
        }
        return false;
    }

    private static final class Challenge {
        private final String code;
        private final long sentAt;
        private int attempts;

        private Challenge(String code, long sentAt) {
            this.code = code;
            this.sentAt = sentAt;
        }
    }
}
