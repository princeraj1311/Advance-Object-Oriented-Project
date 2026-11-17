package cinesmart.service;


import cinesmart.model.Customer;
import cinesmart.model.Admin;
import cinesmart.model.User;
import cinesmart.util.DataStore;
import cinesmart.util.ValidationUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * UserService contains the login and registration rules. The Swing panels never compare
 * passwords or open files themselves - they only call the methods below. That separation is
 * the main idea of MVC: the view shows, the service decides.
 */
public class UserService implements AuthService {

    private final DataStore store;
    private final EmailOtpService emailOtpService = new EmailOtpService();

    public UserService() {
        // DataStore.get() returns the single shared storage object of the running program.
        this.store = DataStore.get();
    }

    /** Constructor injection: useful for tests and for a future database store. */
    public UserService(DataStore store) {
        this.store = (store == null) ? DataStore.get() : store;
    }

    /* ------------------------------------------------------------------ *
     * Login
     * ------------------------------------------------------------------ */

    @Override
    public AuthResult login(String userName, String password, String expectedRole) {
        if (ValidationUtil.isBlank(userName)) {
            return AuthResult.failure("Please enter your user name.");
        }
        if (ValidationUtil.isBlank(password)) {
            return AuthResult.failure("Please enter your password.");
        }

        User found = store.findUser(userName);
        if (found == null) {
            return AuthResult.failure("User name \"" + ValidationUtil.clean(userName)
                    + "\" is not registered. Please create an account first.");
        }
        if (!found.verifyPassword(password)) {
            return AuthResult.failure("Incorrect password. Please try again.");
        }
        // Role check: an admin cannot enter the customer screen and the other way round.
        if (expectedRole != null && !expectedRole.equalsIgnoreCase(found.getRole())) {
            return AuthResult.failure("This account is registered as " + found.getRole()
                    + ". Please select the role \"" + found.getRole() + "\" and login again.");
        }
        return AuthResult.success(found, "Signed in as " + found.getRole() + ", "
                + found.getFullName() + ".");
    }

    /* ------------------------------------------------------------------ *
     * Registration rules
     * ------------------------------------------------------------------ */

    @Override
    public LoginChallengeResult requestLoginCode(String identifier, String fullName, String email,
                                                 String expectedRole) {
        User user = findLoginAccount(identifier, fullName, email, expectedRole);
        if (user == null) {
            return LoginChallengeResult.accountMismatch("No account matches those details. Check your "
                    + "name, login ID, email and selected account type.");
        }
        return emailOtpService.send(challengeKey(user), user.getEmail());
    }

    @Override
    public AuthResult verifyLoginCode(String identifier, String fullName, String email,
                                      String expectedRole, String code) {
        User user = findLoginAccount(identifier, fullName, email, expectedRole);
        if (user == null) {
            return AuthResult.failure("No account matches those details. Please request a new "
                    + "verification code.");
        }
        if (!emailOtpService.verify(challengeKey(user), code)) {
            return AuthResult.failure("The verification code is invalid or expired. Please "
                    + "request a new code.");
        }
        return AuthResult.success(user, "Signed in as " + user.getRole() + ", "
                + user.getFullName() + ".");
    }

    private User findLoginAccount(String identifier, String fullName, String email,
                                  String expectedRole) {
        String cleanIdentifier = identifier == null ? "" : identifier.trim();
        String cleanName = normalizeName(fullName);
        String cleanEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (cleanIdentifier.isEmpty() || cleanName.isEmpty()
                || !ValidationUtil.isValidEmail(cleanEmail)) {
            return null;
        }
        boolean adminLogin = Admin.ROLE.equalsIgnoreCase(expectedRole);
        if (!adminLogin && !Customer.ROLE.equalsIgnoreCase(expectedRole)) {
            return null;
        }

        User match = null;
        for (User candidate : store.getUsers()) {
            if (adminLogin != (candidate instanceof Admin)
                    || !normalizeName(candidate.getFullName()).equals(cleanName)
                    || !candidate.getEmail().equalsIgnoreCase(cleanEmail)) {
                continue;
            }
            boolean identifierMatches = candidate instanceof Admin
                    ? ((Admin) candidate).getAdminId().equals(cleanIdentifier)
                    : candidate instanceof Customer
                            && ((Customer) candidate).getPhone().equals(cleanIdentifier);
            if (identifierMatches) {
                if (match != null) {
                    return null;
                }
                match = candidate;
            }
        }
        if (match instanceof Customer && !ValidationUtil.isValidPhone(cleanIdentifier)) {
            return null;
        }
        if (match instanceof Admin && !cleanIdentifier.matches("[0-9]{12}")) {
            return null;
        }
        return match;
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String challengeKey(User user) {
        return user.getRole() + ":" + user.getUserName();
    }

    @Override
    public List<String> registrationProblems(Customer customer, String password,
                                             String confirmPassword, boolean acceptedTerms) {
        List<String> problems = new ArrayList<>();
        if (customer == null) {
            problems.add("The registration form could not be read.");
            return problems;
        }

        if (!ValidationUtil.isValidUserName(customer.getUserName())) {
            problems.add("User name must be 3-15 characters (letters, digits or _ only).");
        }
        if (store.userNameExists(customer.getUserName())) {
            problems.add("User name \"" + customer.getUserName() + "\" is already taken.");
        }
        if (!ValidationUtil.isValidFullName(customer.getFullName())) {
            problems.add("Full name must be 3-40 letters (spaces and dots are allowed).");
        }
        if (!ValidationUtil.isValidEmail(customer.getEmail())) {
            problems.add("E-mail address is not valid, e.g. yourname@gmail.com");
        }
        if (store.emailExists(customer.getEmail(), customer.getUserName())) {
            problems.add("That e-mail address is already used by another account.");
        }
        if (!ValidationUtil.isValidPhone(customer.getPhone())) {
            problems.add("Mobile number must be 10 digits starting with 6-9.");
        } else if (phoneExists(customer.getPhone(), customer.getUserName())) {
            problems.add("That mobile number is already used by another account.");
        }
        if (ValidationUtil.isBlank(customer.getDateOfBirth())) {
            problems.add("Please choose your date of birth.");
        } else if (customer.getAge() < 10 || customer.getAge() > 110) {
            problems.add("Date of birth does not look correct.");
        }
        if (ValidationUtil.isBlank(customer.getGender())) {
            problems.add("Please choose a gender.");
        }
        if (!ValidationUtil.isAcceptablePassword(password)) {
            problems.add("Password must be 6-20 characters with at least one letter and one digit.");
        }
        if (password == null || !password.equals(confirmPassword)) {
            problems.add("Password and confirm password do not match.");
        }
        if (!acceptedTerms) {
            problems.add("Please accept the terms of use to continue.");
        }
        return problems; // an empty list means every rule passed
    }

    @Override
    public AuthResult register(Customer customer, String password, String confirmPassword,
                               boolean acceptedTerms) {
        List<String> problems = registrationProblems(customer, password, confirmPassword,
                acceptedTerms);
        if (!problems.isEmpty()) {
            return AuthResult.failure(problems.get(0)); // first problem explains the failure
        }
        customer.setPassword(password); // salt + hash, never the plain text
        if (!store.addUser(customer)) {
            return AuthResult.failure("Sorry, the account could not be saved. Please try again.");
        }
        return AuthResult.success(customer, "Account created for " + customer.getFullName()
                + " (" + customer.getUserName() + ").");
    }

    @Override
    public boolean isUserNameTaken(String userName) {
        return ValidationUtil.isValidUserName(userName) && store.userNameExists(userName);
    }

    /* ------------------------------------------------------------------ *
     * Extra helpers (home screen now, profile + admin panel later)
     * ------------------------------------------------------------------ */

    public List<User> allUsers() {
        return store.getUsers();
    }

    public int countByRole(String role) {
        int total = 0;
        for (User user : store.getUsers()) {
            if (user.getRole().equalsIgnoreCase(role)) {
                total++;
            }
        }
        return total;
    }

    public int countCustomers() {
        return countByRole(Customer.ROLE);
    }

    public int countAdmins() {
        return countByRole("Admin");
    }

    private boolean phoneExists(String phone, String ownUserName) {
        for (User user : store.getUsers()) {
            if (user instanceof Customer && user.getPhone().equals(phone)
                    && !user.getUserName().equalsIgnoreCase(ownUserName)) {
                return true;
            }
        }
        return false;
    }

    /** Updates the profile details of the signed in user. */
    public boolean updateProfile(User user, String fullName, String email, String phone,
                                 String city) {
        if (user == null) {
            return false;
        }
        if (!ValidationUtil.isValidFullName(fullName) || !ValidationUtil.isValidEmail(email)
                || !ValidationUtil.isValidPhone(phone)) {
            return false;
        }
        if (store.emailExists(email, user.getUserName())) {
            return false;
        }
        if (user instanceof Customer && phoneExists(phone, user.getUserName())) {
            return false;
        }
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setCity(city);
        return store.updateUser(user);
    }

    /** Changes the password of the signed in user after checking the old password. */
    public boolean changePassword(User user, String oldPassword, String newPassword) {
        if (user == null || !user.verifyPassword(oldPassword)) {
            return false;
        }
        if (!ValidationUtil.isAcceptablePassword(newPassword)) {
            return false;
        }
        user.setPassword(newPassword);
        return store.updateUser(user);
    }
}

