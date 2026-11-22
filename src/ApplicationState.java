import java.util.ArrayList;

public class ApplicationState {

    private final ArrayList<User> users = new ArrayList<>();
    private final ArrayList<AccessRequest> requests = new ArrayList<>();
    private final ArrayList<Job> jobs = new ArrayList<>();
    private final ArrayList<GPU> gpus = new ArrayList<>();
    private final ArrayList<TimeAllowance> allowances = new ArrayList<>();

    private User currentUser;

    // Creates a new simulation
    public ApplicationState() {
        users.add(new Administrator());

        for (int i = 1; i <= 4; i++) {
            gpus.add(new GPU("G" + i));
        }
    }

    // Register student
    public AccessRequest register(String universityId, String name,
                                  String email, String phone, String password) {

        AccessRequest request = new AccessRequest(
            universityId, name, email, phone, password);

        String id = request.getUniversityId();

        if (findUser(id) != null || findRequest(id) != null) {
            throw new IllegalArgumentException(
                "ID already has an account or request.");
        }

        requests.add(request);
        return request;
    }

    // Login
    public boolean login(String username, String password) {

        if (currentUser != null) {
            throw new IllegalStateException(
                "Log out before logging in again.");
        }

        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        User account = findUser(username.trim());

        if (account == null || !account.matchesPassword(password)) {
            return false;
        }

        currentUser = account;
        return true;
    }

    // Logout
    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isAdministratorLoggedIn() {
        return currentUser instanceof Administrator;
    }

    // Approve student
    public Student approveRequest(String universityId) {

        requireAdministrator();

        AccessRequest request = requirePendingRequest(universityId);

        if (findUser(request.getUniversityId()) != null) {
            throw new IllegalStateException("Account already exists.");
        }

        Student student = request.createStudent();

        TimeAllowance allowance =
            new TimeAllowance(student.getUniversityId(), 100);

        users.add(student);
        allowances.add(allowance);

        request.approve();

        return student;
    }

// Reject student
    public void rejectRequest(String universityId) {

        requireAdministrator();

        requirePendingRequest(universityId).reject();
    }

    // Adjust GPU allowance
    public void adjustAllowance(String universityId,
                                int newTotal, String reason) {

        requireAdministrator();

        TimeAllowance allowance = findAllowance(
            User.requireText(universityId, "University ID"));

        if (allowance == null) {
            throw new IllegalArgumentException(
                "Student allowance not found.");
        }

        allowance.setAllocatedMinutes(
            newTotal, currentUser.getUsername(), reason);
    }

    // Find user
    public User findUser(String username) {

        if (username == null) {
            return null;
        }

        for (User user : users) {
            if (user.getUsername().equals(username.trim())) {
                return user;
            }
        }

        return null;
    }

    // Find registration request
    public AccessRequest findRequest(String universityId) {

        if (universityId == null) {
            return null;
        }

        for (AccessRequest request : requests) {
            if (request.getUniversityId().equals(universityId.trim())) {
                return request;
            }
        }

        return null;
    }

    // Find allowance
    TimeAllowance findAllowance(String universityId) {

        for (TimeAllowance allowance : allowances) {
            if (allowance.getStudentId().equals(universityId)) {
                return allowance;
            }
        }

        return null;
    }

    // Get student allowance
    public TimeAllowance getAllowance(String universityId) {

        String id = User.requireText(
            universityId, "University ID");

        requireOwnAccountOrAdministrator(id);

        TimeAllowance allowance = findAllowance(id);

        if (allowance == null) {
            throw new IllegalArgumentException(
                "Student allowance not found.");
        }

        return allowance;
    }

    // Find GPU
    public GPU findGpu(String gpuId) {

        for (GPU gpu : gpus) {
            if (gpu.getGpuId().equals(gpuId)) {
                return gpu;
            }
        }

        return null;
    }
