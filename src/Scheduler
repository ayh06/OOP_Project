import java.util.ArrayList;


public class Scheduler {

    private final ApplicationState state;
    private final ArrayList<GPU> gpus = new ArrayList<>();
    private long nextJobNumber = 1;

public Scheduler(ApplicationState state) {
        if (state == null) {
            throw new IllegalArgumentException("ApplicationState is required.");
        }
        this.state = state;

        // References to the existing GPUs, in increasing ID order.
        for (int i = 1; i <= 4; i++) {
            GPU gpu = state.findGpu("G" + i);
            if (gpu == null) {
                throw new IllegalStateException("Missing GPU: G" + i);
            }
            gpus.add(gpu);
        }
    }

    public Job submitJob(String title, String applicationName,
                         String description, String taskSize) {
        User user = state.getCurrentUser();
        if (!(user instanceof Student)) {
            throw new IllegalStateException("Log in as a student to submit a job.");
        }

        Job job = new Job(createJobId(), user.getUsername(), title,
            applicationName, description, taskSize);
        Math.addExact(SimulationClock.getCurrentMinute(), job.getEstimatedDuration());

        // Process existing work before checking the student's available balance.
        update();
        state.addJob(job); // Validates ownership, reserves minutes, stores the job.
        dispatchNow();
        return job;
    }

  private void checkForMissedCompletions() {
        int now = SimulationClock.getCurrentMinute();
        for (GPU gpu : gpus) {
            Job job = gpu.getCurrentJob();
            if (job != null && "Running".equals(job.getStatus())
                    && gpu.getExpectedFinish() < now) {
                throw new IllegalStateException(
                    "A finish minute was skipped. Call scheduler.update() after every clock advance.");
            }
        }
    }

/** Call immediately after EVERY SimulationClock.advanceMinute(). */
public void update() {
    checkForMissedCompletions();
    int now = SimulationClock.getCurrentMinute();

    // Finish ALL due jobs before assigning any waiting jobs.
    for (GPU gpu : gpus) {
        Job job = gpu.getCurrentJob();
        if (job == null) continue;

        if ("Running".equals(job.getStatus()) && gpu.getExpectedFinish() == now) {
            job.complete();
        }

        if (job.isTerminal()) {
            allowanceFor(job).settleJob(job);
            gpu.release();
        }
    }

    dispatchNow();
}

    private void dispatchNow() {
        ArrayList<Job> queue = orderedQueue();
        int next = 0;

        for (GPU gpu : gpus) {
            if (next == queue.size()) break;
            if (gpu.isAvailable()) {
                Job job = queue.get(next);
                Math.addExact(SimulationClock.getCurrentMinute(), job.getEstimatedDuration());
                gpu.assign(job);
                next++;
            }
        }
    }

    // Builds a queue from shared job records, avoiding a second stored queue.
    // Sort order: submission minute, then String job ID for ties.
    private ArrayList<Job> orderedQueue() {
        ArrayList<Job> queue = new ArrayList<>();
        for (Job job : state.getAllJobs()) {
            if (!"Queued".equals(job.getStatus())) continue;

            int position = 0;
            while (position < queue.size() && !comesBefore(job, queue.get(position))) {
                position++;
            }
            queue.add(position, job);
        }
        return queue;
    }

    private boolean comesBefore(Job first, Job second) {
        if (first.getSubmissionTime() != second.getSubmissionTime()) {
            return first.getSubmissionTime() < second.getSubmissionTime();
        }
        return first.getJobId().compareTo(second.getJobId()) < 0;
    }
private String createJobId() {
    while (nextJobNumber <= 9999999999L) {
        String number = String.valueOf(nextJobNumber++);
        while (number.length() < 10) number = "0" + number;
        String id = "J" + number;
        boolean exists = false;
        for (Job job : state.getAllJobs()) {
            if (job.getJobId().equals(id)) {
                exists = true;
                break;
            }
        }
        if (!exists) return id;
    }
    throw new IllegalStateException("No more generated job IDs are available.");
}

private TimeAllowance allowanceFor(Job job) {
    TimeAllowance allowance = state.findAllowance(job.getStudentId());
    if (allowance == null) {
        throw new IllegalStateException("Student allowance not found.");
    }
    return allowance;
}
public void dispatchWaitingJobs() {
    update();
}

public void cancelJob(String jobId, String reason) {
    // findJob checks login and prevents access to another student's job.
    Job job = requireVisibleJob(jobId);
    String validReason = User.requireText(reason, "Cancellation reason");
    User actor = state.getCurrentUser();
    if (!(actor instanceof Student) && !(actor instanceof Administrator)) {
        throw new IllegalStateException("Student or administrator login required.");
    }

    // A job due now must complete rather than be cancelled.
    update();
    job.cancel(actor.getUsername(), validReason);
    allowanceFor(job).settleJob(job);

    for (GPU gpu : gpus) {
        if (gpu.getCurrentJob() == job) {
            gpu.release();
        }
    }

    dispatchNow();
}

public ArrayList<Job> getJobs() {
    return state.getJobs();
}

public ArrayList<GPU> getGpus() {
    return state.getGpus();
}

// Administrator sees the full queue; a student sees only their queued jobs.
public ArrayList<Job> getQueue() {
    requireLoggedIn();
    ArrayList<Job> visible = new ArrayList<>();
    for (Job job : orderedQueue()) {
        if (state.isAdministratorLoggedIn()
                || job.getStudentId().equals(state.getCurrentUser().getUsername())) {
            visible.add(job);
        }
    }
    return visible;
}

// Position in the full queue: 1 means first; 0 means not queued.
public int getQueuePosition(String jobId) {
    Job target = requireVisibleJob(jobId);
    ArrayList<Job> queue = orderedQueue();
    for (int i = 0; i < queue.size(); i++) {
        if (queue.get(i) == target) return i + 1;
    }
    return 0;
}

public int getPredictedStartTime(String jobId) {
    return predict(requireVisibleJob(jobId))[0];
}

public int getPredictedFinishTime(String jobId) {
    return predict(requireVisibleJob(jobId))[1];
}

// Remaining wait from NOW, not the job's total elapsed queue time.
public int getPredictedWaitMinutes(String jobId) {
    Job job = requireVisibleJob(jobId);
    if (!"Queued".equals(job.getStatus())) return 0;
    return predict(job)[0] - SimulationClock.getCurrentMinute();
}

// Returns {start, finish}. Predictions never change jobs, GPUs or the clock.
private int[] predict(Job target) {
    checkForMissedCompletions();

    if (target.isTerminal()) {
        // A job cancelled while queued has start/finish values of -1.
        return new int[] {target.getStartTime(), target.getFinishTime()};
    }
    if ("Running".equals(target.getStatus())) {
        return new int[] {target.getStartTime(),
            Math.addExact(target.getStartTime(), target.getEstimatedDuration())};
    }

    int now = SimulationClock.getCurrentMinute();
    int[] freeAt = new int[gpus.size()];
    for (int i = 0; i < gpus.size(); i++) {
        GPU gpu = gpus.get(i);
        if (gpu.isAvailable() || gpu.getCurrentJob().isTerminal()) {
            freeAt[i] = now;
        } else {
            freeAt[i] = gpu.getExpectedFinish();
        }
    }

    // Simulate preceding queued jobs using only copied availability times.
    for (Job job : orderedQueue()) {
        int earliest = 0;
        for (int i = 1; i < freeAt.length; i++) {
            if (freeAt[i] < freeAt[earliest]) earliest = i;
        }
        // Equal availability retains the lower GPU ID.
        int start = freeAt[earliest];
        int finish = Math.addExact(start, job.getEstimatedDuration());
        if (job == target) return new int[] {start, finish};
        freeAt[earliest] = finish;
    }

    throw new IllegalStateException("Job is missing from the queue.");
}

private Job requireVisibleJob(String jobId) {
    Job job = state.findJob(User.requireText(jobId, "Job ID"));
    if (job == null) throw new IllegalArgumentException("Job not found.");
    return job;
}

private void requireLoggedIn() {
    if (state.getCurrentUser() == null) {
        throw new IllegalStateException("Log in first.");
    }
}
}
