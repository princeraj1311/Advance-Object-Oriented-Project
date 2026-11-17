package cinesmart.service;


/**
 * AuthResult is what the service layer hands back to the screens: a yes/no, a message and,
 * on success, the user that just signed in.
 *
 * Why not throw exceptions for "wrong password"? Because a wrong password is a normal,
 * expected situation. Returning a small result object keeps the GUI code readable and shows
 * the difference between a business result and a real failure.
 */
public class AuthResult {

    private final boolean success;
    private final String message;
    private final cinesmart.model.User user;

    /** Private constructor forces callers to use the two factory methods below. */
    private AuthResult(boolean success, String message, cinesmart.model.User user) {
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public static AuthResult success(cinesmart.model.User user, String message) {
        return new AuthResult(true, message, user);
    }

    public static AuthResult failure(String message) {
        return new AuthResult(false, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    /** The signed in user, or null when something went wrong. */
    public cinesmart.model.User getUser() {
        return user;
    }
}
