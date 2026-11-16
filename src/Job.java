
public class Job {

    // These details cannot change after the job is accepted.
    private final String jobId;
    private final String studentId;
    private final String title;
    private final String applicationName;
    private final String description;
    private final String taskSize;
    private final int submissionTime;

    // Execution information is updated only by start(), complete(), and cancel().
    private String status;
    private String assignedGpuId; // null means no GPU has been assigned.
    private int startTime = -1; // -1 means the job has not started.
    private int finishTime = -1; // -1 means there is no actual finish time.

    // Cancellation information is retained even when the job never ran.
    private int cancellationTime = -1; // -1 means not cancelled.
    private String cancelledBy;
    private String cancellationReason;

    public Job(String jobId, String studentId, String title,
               String applicationName, String description, String taskSize) {
        this(jobId, studentId, title, applicationName, description, taskSize,
            SimulationClock.getCurrentMinute());
    }

    private Job(String jobId, String studentId, String title,
                String applicationName, String description, String taskSize,
                int submissionTime) {
        this.jobId = requireText(jobId, "Job ID");
        this.studentId = requireText(studentId, "Student ID");
        this.title = requireText(title, "Title");
        this.applicationName = requireText(applicationName, "Application name");
        this.description = requireText(description, "Description");

        durationForSize(taskSize); // Reject an invalid size before accepting it.
        this.taskSize = taskSize;
        this.submissionTime = requireMinuteTime(submissionTime);
        this.status = "Queued";
    }

    // Storage restores historical fields without starting/completing the job again.
    static Job restore(String id, String studentId, String title, String app,
                       String description, String size, int submitted, String status,
                       String gpuId, int started, int finished, int cancelled,
                       String actor, String reason, int savedMinute) {
        Job job = new Job(id, studentId, title, app, description, size, submitted);
        boolean queued = "Queued".equals(status);
        boolean running = "Running".equals(status);
        boolean completed = "Completed".equals(status);
        boolean stopped = "Cancelled".equals(status);
        if (!(queued || running || completed || stopped) || submitted > savedMinute) {
            throw new IllegalArgumentException("Invalid saved job status or submission time.");
        }
        if (queued || (stopped && started == -1)) {
            if (started != -1 || finished != -1 || gpuId != null) {
                throw new IllegalArgumentException("Unstarted job has execution data.");
            }
        } else {
            requireText(gpuId, "Saved GPU ID");
            long expected = (long) started + job.getEstimatedDuration();
            if (started < submitted || started > savedMinute || expected > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid saved start time.");
            }
            if (running && (finished != -1 || expected <= savedMinute)) {
                throw new IllegalArgumentException("Running job is already due or has a finish time.");
            }
            if (completed && (finished != expected || finished > savedMinute)) {
                throw new IllegalArgumentException("Invalid saved completion time.");
            }
            if (stopped && (finished != cancelled || cancelled < started || cancelled >= expected)) {
                throw new IllegalArgumentException("Invalid running cancellation time.");
            }
        }
        if (stopped) {
            if (cancelled < submitted || cancelled > savedMinute) {
                throw new IllegalArgumentException("Invalid cancellation time.");
            }
            requireText(actor, "Cancelling user");
            requireText(reason, "Cancellation reason");
        } else if (cancelled != -1 || actor != null || reason != null) {
            throw new IllegalArgumentException("Uncancelled job has cancellation data.");
        }
        job.status = status;
        job.assignedGpuId = gpuId;
        job.startTime = started;
        job.finishTime = finished;
        job.cancellationTime = cancelled;
        job.cancelledBy = actor;
        job.cancellationReason = reason;
        return job;
    }

    /** Workload units and runtime have the same number in this simulation. */
    public static int durationForSize(String taskSize) {
        if (taskSize == null) {
            throw new IllegalArgumentException("Task size is required.");
        }

        switch (taskSize) {
            case "Small":
                return 5;
            case "Medium":
                return 15;
            case "Large":
                return 30;
            case "Very large":
                return 60;
            default:
                throw new IllegalArgumentException(
                    "Task size must be Small, Medium, Large, or Very large.");
        }
    }

    /** Called by GPU.assign() once the scheduler has selected a free GPU. */
    public void start(String gpuId) {
        int now = SimulationClock.getCurrentMinute();
        if (!"Queued".equals(status)) {
            throw new IllegalStateException("Only a queued job can start.");
        }
        String validGpuId = requireText(gpuId, "GPU ID");
        requireAtOrAfter(now, submissionTime);

        assignedGpuId = validGpuId;
        startTime = now;
        status = "Running";
    }

    /** The future scheduler calls this on the minute when the job finishes. */
    public void complete() {
        int now = SimulationClock.getCurrentMinute();
        if (!"Running".equals(status)) {
            throw new IllegalStateException("Only a running job can complete.");
        }
        requireAtOrAfter(now, startTime);

        int expectedFinish = startTime + getEstimatedDuration();
        if (now != expectedFinish) {
            throw new IllegalStateException(
                "Complete the job at its expected finish time: " + expectedFinish);
        }

        finishTime = now;
        status = "Completed";
    }

    /** Permission checks belong to the scheduler/account service, before this call. */
    public void cancel(String actorId, String reason) {
        int now = SimulationClock.getCurrentMinute();
        if (isTerminal()) {
            throw new IllegalStateException(
                "A completed or cancelled job cannot be cancelled again.");
        }

        String validActor = requireText(actorId, "Cancelling user");
        String validReason = requireText(reason, "Cancellation reason");
        requireAtOrAfter(now, startTime == -1 ? submissionTime : startTime);

        if ("Running".equals(status)) {
            int expectedFinish = startTime + getEstimatedDuration();
            if (now >= expectedFinish) {
                throw new IllegalStateException("This job is due for completion.");
            }
            finishTime = now;
        }

        cancellationTime = now;
        cancelledBy = validActor;
        cancellationReason = validReason;
        status = "Cancelled";
    }

    public boolean isTerminal() {
        return "Completed".equals(status) || "Cancelled".equals(status);
    }

    /** Counts execution only; waiting never contributes to runtime. */
    public int getElapsedRuntime() {
        int now = SimulationClock.getCurrentMinute();
        if (startTime == -1) {
            return 0;
        }

        int end = finishTime == -1 ? now : finishTime;
        requireAtOrAfter(end, startTime);
        int elapsed = end - startTime;
        return Math.min(elapsed, getEstimatedDuration());
    }

    public int getRemainingRuntime() {
        if (isTerminal()) {
            return 0;
        }
        return getEstimatedDuration() - getElapsedRuntime();
    }

    /** Actual time spent waiting, including a job cancelled while still queued. */
    public int getQueueWait() {
        int now = SimulationClock.getCurrentMinute();
        int end;
        if (startTime != -1) {
            end = startTime;
        } else if (cancellationTime != -1) {
            end = cancellationTime;
        } else {
            end = now;
        }

        requireAtOrAfter(end, submissionTime);
        return end - submissionTime;
    }

    /** Reports a charge; TimeAllowance will apply it exactly once later. */
    public int getChargeMinutes() {
        if ("Completed".equals(status)) {
            return getEstimatedDuration();
        }
        if ("Cancelled".equals(status) && startTime != -1) {
            return getElapsedRuntime();
        }
        return 0;
    }

    public String getJobId() { return jobId; }
    public String getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getApplicationName() { return applicationName; }
    public String getDescription() { return description; }
    public String getTaskSize() { return taskSize; }
    public int getWorkloadUnits() { return durationForSize(taskSize); }
    public int getEstimatedDuration() { return getWorkloadUnits(); }
    public String getStatus() { return status; }
    public int getSubmissionTime() { return submissionTime; }
    public String getAssignedGpuId() { return assignedGpuId; }
    public int getStartTime() { return startTime; }
    public int getFinishTime() { return finishTime; }
    public int getCancellationTime() { return cancellationTime; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }

    @Override
    public String toString() {
        return "Job " + jobId + " | " + title + " | " + status + " | GPU: " + (assignedGpuId == null ? "Unassigned" : assignedGpuId);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    /** Simulation minutes start at zero and never use the real-world clock. */
    private static int requireMinuteTime(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("Simulation time cannot be negative.");
        }
        return time;
    }

    private static void requireAtOrAfter(int time, int minimum) {
        requireMinuteTime(time);
        if (time < minimum) {
            throw new IllegalArgumentException("Simulation time cannot go backwards.");
        }
    }
}
