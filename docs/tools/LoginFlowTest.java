import cinesmart.model.Admin;
import cinesmart.model.Customer;
import cinesmart.model.User;
import cinesmart.service.LoginChallengeResult;
import cinesmart.service.UserService;
import cinesmart.util.DataStore;
import java.util.regex.Pattern;

/** Account matching checks for customer phone and 12-digit admin-ID email sign-in. */
public class LoginFlowTest {

    public static void main(String[] args) {
        DataStore store = DataStore.get();
        UserService users = new UserService(store);
        Customer customer = null;
        Admin admin = null;
        for (User user : store.getUsers()) {
            if (user instanceof Customer && "demo".equals(user.getUserName())) {
                customer = (Customer) user;
            } else if (user instanceof Admin) {
                admin = (Admin) user;
            }
        }
        check("seed customer account exists", customer != null);
        check("seed admin account exists", admin != null);
        check("admin ID has exactly 12 digits",
                admin != null && Pattern.matches("[0-9]{12}", admin.getAdminId()));

        if (customer != null) {
            LoginChallengeResult wrongPhone = users.requestLoginCode("123456789",
                    customer.getFullName(), customer.getEmail(), Customer.ROLE);
            check("nine-digit customer phone is refused",
                    !wrongPhone.isSuccess() && wrongPhone.isAccountDetailsMismatch());
            LoginChallengeResult wrongEmail = users.requestLoginCode(customer.getPhone(),
                    customer.getFullName(), "wrong@example.com", Customer.ROLE);
            check("customer email must match the saved account", !wrongEmail.isSuccess());
            LoginChallengeResult wrongName = users.requestLoginCode(customer.getPhone(),
                    "Different Name", customer.getEmail(), Customer.ROLE);
            check("customer name must match the saved account", !wrongName.isSuccess());
        }
        if (admin != null) {
            LoginChallengeResult wrongAdminId = users.requestLoginCode("000000000000",
                    admin.getFullName(), admin.getEmail(), Admin.ROLE);
            check("admin ID must match the saved 12-digit ID", !wrongAdminId.isSuccess());
            User loaded = User.fromDataLine(admin.toDataLine());
            check("admin ID survives persistence round-trip",
                    loaded instanceof Admin
                            && admin.getAdminId().equals(((Admin) loaded).getAdminId()));
        }
        User legacyAdmin = User.fromDataLine("legacy-admin|Old Admin|admin@example.com|"
                + "9000000001|Bengaluru|||LEGACY_USERNAME_PASSWORD|false|Admin|EMP-101");
        check("old admin record receives a generated numeric ID",
                legacyAdmin instanceof Admin
                        && Pattern.matches("[0-9]{12}", ((Admin) legacyAdmin).getAdminId()));
        System.out.println("LoginFlowTest: ALL CHECKS PASSED");
    }

    private static void check(String name, boolean passed) {
        if (!passed) {
            throw new AssertionError(name);
        }
        System.out.println("PASS: " + name);
    }
}
