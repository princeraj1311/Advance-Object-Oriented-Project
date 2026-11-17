package cinesmart.model;

import cinesmart.util.UiUtil;

public class CinemaHall {

    private String hallId;
    private String registrationNumber;
    private String name;
    private String address;
    private String city;
    private String ownerName;
    private String ownerPhone;
    private String ownerEmail;
    private String certificatePath;
    private Status status;

    public enum Status {
        PENDING, APPROVED, REJECTED
    }

    public CinemaHall() {
        this.hallId = "";
        this.registrationNumber = "";
        this.name = "";
        this.address = "";
        this.city = "";
        this.ownerName = "";
        this.ownerPhone = "";
        this.ownerEmail = "";
        this.certificatePath = "";
        this.status = Status.PENDING;
    }

    public CinemaHall(String registrationNumber, String name, String address, String city,
                      String ownerName, String ownerPhone, String ownerEmail) {
        this();
        this.registrationNumber = registrationNumber;
        this.name = name;
        this.address = address;
        this.city = city;
        this.ownerName = ownerName;
        this.ownerPhone = ownerPhone;
        this.ownerEmail = ownerEmail;
        this.certificatePath = "";
    }

    public String getHallId() { return hallId; }
    public void setHallId(String hallId) { this.hallId = hallId; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getCertificatePath() { return certificatePath; }
    public void setCertificatePath(String certificatePath) { this.certificatePath = certificatePath; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    @Override
    public String toString() {
        return "Hall " + hallId + " - " + name + " (" + status + ")";
    }

    public String toDataLine() {
        return String.join("|", clean(hallId), clean(registrationNumber), clean(name),
                clean(address), clean(city), clean(ownerName), clean(ownerPhone),
                clean(ownerEmail), clean(certificatePath), status.name());
    }

    public static CinemaHall fromDataLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            throw new IllegalArgumentException("empty hall line");
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 10) {
            throw new IllegalArgumentException("a hall line needs 10 columns, this one has "
                    + parts.length);
        }
        CinemaHall hall = new CinemaHall();
        hall.hallId = parts[0];
        hall.registrationNumber = parts[1];
        hall.name = parts[2];
        hall.address = parts[3];
        hall.city = parts[4];
        hall.ownerName = parts[5];
        hall.ownerPhone = parts[6];
        hall.ownerEmail = parts[7];
        hall.certificatePath = parts[8];
        hall.status = Status.valueOf(parts[9]);
        return hall;
    }

    private static String clean(String value) {
        return UiUtil.clean(value);
    }
}
