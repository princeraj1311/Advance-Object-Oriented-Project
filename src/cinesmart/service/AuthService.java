package cinesmart.service;


import cinesmart.model.Customer;
import java.util.List;

/**
 * AuthService is an INTERFACE that describes what an authentication service must be able to
 * do. UserService implements it, and the controllers hold an "AuthService" reference.
 *
 * This is the "programming to an interface" idea: if a database backed service or a mock
 * service is written later, the login screen does not need a single change.
 */
public interface AuthService {

    /**
     * Checks the user name, the password and the selected role.
     *
     * @param userName     text typed by the user
     * @param password     text typed by the user
     * @param expectedRole "Customer" or "Admin" chosen from the combo box
     * @return an AuthResult that always carries a human readable message
     */
    AuthResult login(String userName, String password, String expectedRole);

    /** Sends an email code after validating the account's role, name and login identifier. */
    LoginChallengeResult requestLoginCode(String identifier, String fullName, String email,
                                          String expectedRole);

    /** Verifies the one-time email code and completes sign-in. */
    AuthResult verifyLoginCode(String identifier, String fullName, String email,
                               String expectedRole, String code);

    /** Saves a new customer after all rules have been satisfied. */
    AuthResult register(Customer customer, String password, String confirmPassword,
                        boolean acceptedTerms);

    /** Every rule violation as a list of messages (empty list = the form is correct). */
    List<String> registrationProblems(Customer customer, String password,
                                     String confirmPassword, boolean acceptedTerms);

    /** Live check used while the user name is being typed. */
    boolean isUserNameTaken(String userName);
}
