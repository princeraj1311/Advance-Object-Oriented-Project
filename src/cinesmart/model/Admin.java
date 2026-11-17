package cinesmart.model;

import cinesmart.util.IDGenerator;

/**
 * Admin also extends User, but it adds staff-only information instead of customer
 * information. Both classes inherit the same login fields, which is exactly why
 * inheritance is the right tool here.
 */
public class Admin extends User {

    /** Role text written in the data file and shown in the UI. */
    public static final String ROLE = "Admin";

    /** Staff id shown on the admin dashboard, e.g. EMP-101. */
    private String staffId;
    private String adminId;

    /** Empty constructor. */
    public Admin() {
        super();
        this.staffId = "EMP-101";
        this.adminId = IDGenerator.adminId();
    }

    /** Constructor used when the default admin account is created. */
    public Admin(String userName, String fullName, String email, String phone, String city) {
        super(userName, fullName, email, phone, city);
        this.staffId = "EMP-101";
        this.adminId = IDGenerator.adminId();
    }

    /** Constructor used when reading a stored line. */
    public Admin(String[] dataParts) {
        super(dataParts);
        String stored = dataParts != null && dataParts.length > 10
                ? part(dataParts, 10) : "";
        this.staffId = stored.isEmpty() ? "EMP-101" : stored;
        setAdminId(part(dataParts, 11));
    }

    @Override
    public String getRole() {
        return ROLE;
    }

    /** Polymorphism example: the same method name, a different message. */
    @Override
    public String getWelcomeMessage() {
        return "Admin console ready, " + getFullName() + " (" + staffId + ").";
    }

    @Override
    public String toDataLine() {
        return super.toDataLine() + SEPARATOR_TEXT + staffId + SEPARATOR_TEXT + adminId;
    }

    /** Used by the menu bar: only an admin may open the dashboard screens. */
    public boolean canManageMovies() {
        return true;
    }

    public String getStaffId() {
        return staffId;
    }

    public void setStaffId(String staffId) {
        this.staffId = (staffId == null || staffId.trim().isEmpty()) ? "EMP-101" : staffId.trim();
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId != null && adminId.matches("[0-9]{12}")
                ? adminId : IDGenerator.adminId();
    }
}
