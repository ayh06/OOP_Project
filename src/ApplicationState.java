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
    
    // Submit GPU job
    public void addJob(Job job) {

        // NEW: Check simulation date and time
        if (!SimulationClock.isInitialized()) {
            throw new IllegalStateException(
                "Please initialize the simulation date and time first.");
        }

        if (!(currentUser instanceof Student)) {
            throw new IllegalStateException(
                "Log in as a student to submit a job.");
        }

        if (job == null ||
                !job.getStudentId().equals(currentUser.getUsername())) {

            throw new IllegalArgumentException(
                "Job must belong to the logged-in student.");
        }

        if (findJobRecord(job.getJobId()) != null) {
            throw new IllegalArgumentException(
                "Job ID already exists.");
        }

        TimeAllowance allowance = findAllowance(job.getStudentId());

        if (allowance == null) {
            throw new IllegalStateException(
                "Student allowance not found.");
        }

        allowance.reserve(job);
        jobs.add(job);
    }

    // Find job
    public Job findJob(String jobId) {

        requireLoggedIn();

        Job job = findJobRecord(jobId);

        if (job != null) {
            requireOwnAccountOrAdministrator(job.getStudentId());
        }

        return job;
    }

    private Job findJobRecord(String jobId) {

        for (Job job : jobs) {
            if (job.getJobId().equals(jobId)) {
                return job;
            }
        }

        return null;
    }

    // Get users
    public ArrayList<User> getUsers() {

        requireAdministrator();

        return new ArrayList<>(users);
    }

    // Get requests
    public ArrayList<AccessRequest> getRequests() {

        requireAdministrator();

        return new ArrayList<>(requests);
    }

    // Get pending requests
    public ArrayList<AccessRequest> getPendingRequests() {

        requireAdministrator();

        ArrayList<AccessRequest> pending = new ArrayList<>();

        for (AccessRequest request : requests) {

            if ("Pending".equals(request.getStatus())) {
                pending.add(request);
            }
        }

        return pending;
    }

    // Get GPUs
    public ArrayList<GPU> getGpus() {

        requireLoggedIn();

        return new ArrayList<>(gpus);
    }

    // Get visible jobs
    public ArrayList<Job> getJobs() {

        requireLoggedIn();

        ArrayList<Job> visible = new ArrayList<>();

        for (Job job : jobs) {

            if (isAdministratorLoggedIn()
                    || job.getStudentId().equals(currentUser.getUsername())) {

                visible.add(job);
            }
        }

        return visible;
    }

