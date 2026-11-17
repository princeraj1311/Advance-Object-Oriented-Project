package cinesmart.model;


import java.time.LocalDate;
import java.time.Period;

/**
 * Customer extends User: everything a user has (name, login, city ...) is inherited, and
 * this class only adds what a movie goer needs - date of birth, gender and seat preference.
 *
 * Data line layout (Customer adds 3 columns to the 8 common columns):
 * userName|fullName|email|phone|city|salt|passwordHash|Customer|dateOfBirth|gender|seatPreference
 */
public class Customer extends User {

    /** Role text written in the data file and shown in the UI. */
    public static final String ROLE = "Customer";

    private String dateOfBirth; // stored as yyyy-MM-dd
    private String gender;
    private String seatPreference;

    /** Empty constructor. */
    public Customer() {
        super();
        this.dateOfBirth = "";
        this.gender = "";
        this.seatPreference = "No Preference";
    }

    /** Constructor used by the registration form. */
    public Customer(String userName, String fullName, String email, String phone,
                    String city, String dateOfBirth, String gender) {
        super(userName, fullName, email, phone, city);
        setDateOfBirth(dateOfBirth);
        setGender(gender);
        this.seatPreference = "No Preference";
    }

    /** Constructor used when reading a stored line (see User.fromDataLine). */
    public Customer(String[] dataParts) {
        super(dataParts);
        setDateOfBirth(part(dataParts, 8));
        setGender(part(dataParts, 9));
        setSeatPreference(part(dataParts, 10));
    }

    @Override
    public String getRole() {
        return ROLE;
    }

    /** Polymorphism example: what a customer sees on the dashboard. */
    @Override
    public String getWelcomeMessage() {
        return "Welcome back, " + firstName() + "! Pick a movie and enjoy the show.";
    }

    /** Adds the customer-only columns after the common columns. */
    @Override
    public String toDataLine() {
        return super.toDataLine() + SEPARATOR_TEXT + dateOfBirth
                + SEPARATOR_TEXT + gender + SEPARATOR_TEXT + seatPreference;
    }

    /** Age in whole years - used by the "must be 10 or older" registration rule. */
    public int getAge() {
        try {
            return Period.between(LocalDate.parse(dateOfBirth), LocalDate.now()).getYears();
        } catch (Exception e) {
            return -1; // no / bad date of birth
        }
    }

    /** First word of the full name, used in greetings. */
    public String firstName() {
        String name = getFullName().trim();
        return name.isEmpty() ? getUserName() : name.split("\\s+")[0];
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public final void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = (dateOfBirth == null) ? "" : dateOfBirth.trim();
    }

    public String getGender() {
        return gender;
    }

    public final void setGender(String gender) {
        this.gender = (gender == null) ? "" : gender.trim();
    }

    public String getSeatPreference() {
        return seatPreference;
    }

    /** Seat preference is applied by the Smart Seat Suggestion engine in Stage 4. */
    public final void setSeatPreference(String seatPreference) {
        this.seatPreference = (seatPreference == null || seatPreference.trim().isEmpty())
                ? "No Preference" : seatPreference.trim();
    }
}
