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

