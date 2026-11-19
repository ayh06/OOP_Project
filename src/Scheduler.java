import java.util.ArrayList;

public class ApplicationState {

    private final ArrayList<User> users = new ArrayList<>();
    private final ArrayList<AccessRequest> requests = new ArrayList<>();
    private final ArrayList<Job> jobs = new ArrayList<>();
    private final ArrayList<GPU> gpus = new ArrayList<>();
    private final ArrayList<TimeAllowance> allowances = new ArrayList<>();

    private User currentUser;
