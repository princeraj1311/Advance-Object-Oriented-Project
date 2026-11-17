package cinesmart.service;

/** Result of requesting an email sign-in code. */
public final class LoginChallengeResult {

    private final boolean success;
    private final boolean accountDetailsMismatch;
    private final String message;
    private final String maskedEmail;

    private LoginChallengeResult(boolean success, boolean accountDetailsMismatch,
                                 String message, String maskedEmail) {
        this.success = success;
        this.accountDetailsMismatch = accountDetailsMismatch;
        this.message = message == null ? "" : message;
        this.maskedEmail = maskedEmail == null ? "" : maskedEmail;
    }

    public static LoginChallengeResult success(String message, String maskedEmail) {
        return new LoginChallengeResult(true, false, message, maskedEmail);
    }

    public static LoginChallengeResult failure(String message) {
        return new LoginChallengeResult(false, false, message, "");
    }

    public static LoginChallengeResult accountMismatch(String message) {
        return new LoginChallengeResult(false, true, message, "");
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isAccountDetailsMismatch() {
        return accountDetailsMismatch;
    }

    public String getMessage() {
        return message;
    }

    public String getMaskedEmail() {
        return maskedEmail;
    }
}
