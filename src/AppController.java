import java.awt.Component;
import java.io.IOException;
import java.time.LocalDateTime;

/** UI coordination only: domain rules remain in ApplicationState and Scheduler. */
public class AppController {

    private final ApplicationState state;
    private final Scheduler scheduler;
    private final FileStateStorage storage;
    private final UiSupport.Dialogs dialogs;

    private Runnable onRefresh = () -> { };
    private boolean dirty;
    private String status;

    public AppController(ApplicationState state, Scheduler scheduler,
                         FileStateStorage storage) {
        this(state, scheduler, storage, UiSupport.swingDialogs());
    }

    AppController(ApplicationState state, Scheduler scheduler,
                  FileStateStorage storage, UiSupport.Dialogs dialogs) {

        if (state == null || scheduler == null || storage == null || dialogs == null) {
            throw new IllegalArgumentException(
                "Shared application objects are required.");
        }

        this.state = state;
        this.scheduler = scheduler;
        this.storage = storage;
        this.dialogs = dialogs;

        status = storage.getLastLoadMessage();

        if (status.isEmpty()) {
            status = "Ready.";
        }
    }

    public ApplicationState getState() {
        return state;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public String getStatus() {
        return status;
    }

    public boolean hasUnsavedChanges() {
        return dirty;
    }

    public void setOnRefresh(Runnable action) {
        onRefresh = action;
    }

    // Perform an action and save changes
    public boolean change(Component parent, Runnable action,
                          String successMessage) {

        if (dirty && !trySave(parent)) {
            onRefresh.run();
            return false;
        }

        dirty = true;

        try {
            action.run();

        } catch (IllegalArgumentException
                | IllegalStateException
                | ArithmeticException exception) {

            trySave(parent);

            status = "Action not completed: " + exception.getMessage();

            onRefresh.run();

            dialogs.error(parent, exception.getMessage());

            return false;
        }

        boolean saved = trySave(parent);

        status = successMessage +
                (saved ? " Saved." : " Not yet saved.");

        onRefresh.run();

        return true;
    }

    // Save application
    public boolean save(Component parent) {

        boolean saved = trySave(parent);

        onRefresh.run();

        return saved;
    }

    private boolean trySave(Component parent) {

        try {

            storage.save(state);

            dirty = false;

            status = "Saved at " +
                    SimulationClock.getFormattedTime() + ".";

            return true;

        } catch (IOException exception) {

            dirty = true;

            status = "Changes are in memory but have not been saved. Use Save to retry.";

            dialogs.error(parent,
                    status +
                    "\nDo not repeat the action.\n\n" +
                    exception.getMessage());

            return false;
        }
    }

    // Logout
    public boolean logout(Component parent) {

        if (dirty && !save(parent)) {
            return false;
        }

        state.logout();

        status = "Logged out.";

        onRefresh.run();

        return true;
    }

    // Initialize simulation date and time
    public boolean initializeClock(Component parent,
                                   LocalDateTime dateTime) {

        if (!state.isAdministratorLoggedIn()) {

            dialogs.error(parent,
                    "Administrator login required.");

            return false;
        }

        if (SimulationClock.isInitialized()) {

            dialogs.error(parent,
                    "Clock is already initialized.");

            return false;
        }

        if (!state.getAllJobs().isEmpty()) {

            dialogs.error(parent,
                    "Cannot initialize the clock after jobs have been accepted.");

            return false;
        }

        return change(parent, () -> {

            SimulationClock.setStartDateTime(dateTime);

        }, "Simulation clock initialized.");
    }

    // Advance simulation by one minute
    public boolean advanceMinute(Component parent) {

        return change(parent, () -> {

            if (!state.isAdministratorLoggedIn()) {

                throw new IllegalStateException(
                        "Administrator login required.");
            }

            if (!SimulationClock.isInitialized()) {

                throw new IllegalStateException(
                        "Set the date and time first.");
            }

            SimulationClock.advanceMinute();

            scheduler.update();

        }, "Advanced one minute.");
    }

    // Close application
    public boolean canClose(Component parent) {

        if (!dirty) {
            return true;
        }

        int choice = dialogs.closeChoice(parent);

        if (choice == 0) {
            return save(parent);
        }

        return choice == 2;
    }
}