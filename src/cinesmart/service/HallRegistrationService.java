package cinesmart.service;


import cinesmart.model.CinemaHall;
import cinesmart.util.DataStore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HallRegistrationService {

    private final DataStore store;

    public HallRegistrationService() { this.store = DataStore.get(); }
    public HallRegistrationService(DataStore store) { this.store = (store == null) ? DataStore.get() : store; }

    public OtpResult registerBusiness(String registrationNumber, String name, String address,
                                      String city, String ownerName, String ownerPhone, String ownerEmail) {
        if (store.findHallByRegistrationNumber(registrationNumber) != null) {
            return OtpResult.failure("Registration number already exists.");
        }
        CinemaHall hall = new CinemaHall(registrationNumber, name, address, city, ownerName, ownerPhone, ownerEmail);
        if (!store.addHall(hall)) {
            return OtpResult.failure("Could not save hall registration.");
        }
        return OtpResult.success(registrationNumber,
                "Hall registered. Phone verification is pending.");
    }

    public OtpResult uploadCertificate(String hallId, String certificatePath) {
        CinemaHall hall = store.findHallById(hallId);
        if (hall == null) return OtpResult.failure("Hall not found.");
        java.io.File certFile = new java.io.File(certificatePath);
        if (!certFile.exists()) return OtpResult.failure("Certificate file not found.");
        long fileSize = certFile.length();
        if (fileSize > 5 * 1024 * 1024) return OtpResult.failure("Certificate file too large. Max 5MB.");
        String ext = certFile.getName().substring(certFile.getName().lastIndexOf(".") + 1).toLowerCase();
        if (!ext.equals("pdf") && !ext.equals("jpg") && !ext.equals("png")) {
            return OtpResult.failure("Invalid certificate format. Allowed: PDF, JPG, PNG.");
        }
        try {
            java.io.File certDir = new java.io.File("data/certificates", hallId);
            if (!certDir.exists()) certDir.mkdirs();
            java.io.File destFile = new java.io.File(certDir, "cert_" + System.currentTimeMillis() + "." + ext);
            java.nio.file.Files.copy(certFile.toPath(), destFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            hall.setCertificatePath(destFile.getAbsolutePath());
            store.updateHall(hall);
            return OtpResult.success(certificatePath, "Certificate uploaded successfully.");
        } catch (IOException e) {
            return OtpResult.failure("Could not save certificate: " + e.getMessage());
        }
    }

    public OtpResult registerBankDetails(String hallId, String accountHolder, String accountNumber,
                                          String ifscCode, String upiId) {
        CinemaHall hall = store.findHallById(hallId);
        if (hall == null) return OtpResult.failure("Hall not found.");
        if (!ifscCode.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
            return OtpResult.failure("Invalid IFSC code format. Format: ABCD0ABCDEF");
        }
        if (upiId != null && !upiId.isEmpty() && !upiId.matches("^[A-Za-z0-9._-]{2,60}@[A-Za-z]{2,20}$")) {
            return OtpResult.failure("Invalid UPI ID format.");
        }
        hall.setStatus(CinemaHall.Status.APPROVED);
        store.updateHall(hall);
        return OtpResult.success("", "Hall approved and bank details registered. Hall can now publish shows and receive bookings.");
    }

    public OtpResult approveHall(String hallId, String adminId, String reason) {
        CinemaHall hall = store.findHallById(hallId);
        if (hall == null) return OtpResult.failure("Hall not found.");
        if (hall.getStatus() != CinemaHall.Status.PENDING) return OtpResult.failure("Hall not in PENDING status.");
        hall.setStatus(CinemaHall.Status.APPROVED);
        store.updateHall(hall);
        return OtpResult.success(hallId, "Hall approved by admin. Reason: " + reason);
    }

    public OtpResult rejectHall(String hallId, String adminId, String reason) {
        CinemaHall hall = store.findHallById(hallId);
        if (hall == null) return OtpResult.failure("Hall not found.");
        if (hall.getStatus() != CinemaHall.Status.PENDING) return OtpResult.failure("Hall not in PENDING status.");
        hall.setStatus(CinemaHall.Status.REJECTED);
        store.updateHall(hall);
        return OtpResult.success(hallId, "Hall rejected. Reason: " + reason);
    }
}

class OtpResult {
    private final String recordId;
    private final String message;
    private final boolean success;

    private OtpResult(String recordId, String message, boolean success) {
        this.recordId = recordId;
        this.message = message;
        this.success = success;
    }

    public static OtpResult success(String recordId, String message) {
        return new OtpResult(recordId, message, true);
    }

    public static OtpResult failure(String message) {
        return new OtpResult("", message, false);
    }

    public boolean isSuccess() { return success; }
    public String getRecordId() { return recordId; }
    public String getMessage() { return message; }
}
