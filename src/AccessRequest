public class AccessRequest {
    private final String universityId, name, email, phone, password;
    private String status = "Pending";

    public AccessRequest(String universityId, String name, String email,
                         String phone, String password) {
        this.universityId = User.requireText(universityId, "University ID");
        this.name = User.requireText(name, "Name");
        this.email = User.requireText(email, "Email");
        this.phone = User.requireText(phone, "Phone");
        User.requireText(password, "Password");
        this.password = password;
    }

    public String getUniversityId() { return universityId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getStatus() { return status; }

    String passwordForStorage() { return password; }

    static AccessRequest restore(String id, String name, String email,
                                 String phone, String password, String status) {
        if (!"Pending".equals(status) && !"Approved".equals(status)
                && !"Rejected".equals(status)) {
            throw new IllegalArgumentException("Invalid request status.");
        }
        AccessRequest request = new AccessRequest(id, name, email, phone, password);
        request.status = status;
        return request;
    }

    Student createStudent() {
        requirePending();
        return new Student(universityId, name, email, phone, password);
    }

    void approve() { requirePending(); status = "Approved"; }
    void reject() { requirePending(); status = "Rejected"; }

    void requirePending() {
        if (!"Pending".equals(status)) {
            throw new IllegalStateException("Request has already been reviewed.");
        }
    }
}

