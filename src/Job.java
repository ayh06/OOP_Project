public class Job {

    private final String jobId;
    private final String studentId;
    private final String title;
    private final String applicationName;
    private final String description;
    private final String taskSize;
    private final int submissionTime;

    private String status;
    private String assignedGpuId;

    // -1 means the event has not happened.
    private int startTime = -1;
    private int finishTime = -1;
    private int cancellationTime = -1;

    private String cancelledBy;
    private String cancellationReason;

    public Job(String jobId, String studentId, String title,
               String applicationName, String description,
               String taskSize) {

        this.jobId = requireText(jobId, "Job ID");
        this.studentId = requireText(studentId, "Student ID");
        this.title = requireText(title, "Title");
        this.applicationName = requireText(
            applicationName, "Application name"
        );
        this.description = requireText(description, "Description");

        // Validate the size before accepting the job.
        durationForSize(taskSize);
        this.taskSize = taskSize;

        this.submissionTime = requireMinuteTime(
            SimulationClock.getCurrentMinute()
        );
        this.status = "Queued";
    }

    public static int durationForSize(String taskSize) {
        if (taskSize == null) {
            throw new IllegalArgumentException(
                "Task size is required."
            );
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
                    "Task size must be Small, Medium, Large, or Very large."
                );
        }
    }

    // Called internally by GPU.assign().
    public void start(String gpuId) {
        int now = SimulationClock.getCurrentMinute();

        if (!"Queued".equals(status)) {
            throw new IllegalStateException(
                "Only a queued job can start."
            );
        }

        String validGpuId = requireText(gpuId, "GPU ID");
        requireAtOrAfter(now, submissionTime);

        assignedGpuId = validGpuId;
        startTime = now;
        status = "Running";
    }

    // Call at the expected finish minute.
    public void complete() {
        int now = SimulationClock.getCurrentMinute();

        if (!"Running".equals(status)) {
            throw new IllegalStateException(
                "Only a running job can complete."
            );
        }

        requireAtOrAfter(now, startTime);

        int expectedFinish = startTime + getEstimatedDuration();

        if (now != expectedFinish) {
            throw new IllegalStateException(
                "Complete the job at its expected finish time: "
                + expectedFinish
            );
        }

        finishTime = now;
        status = "Completed";
    }

    public void cancel(String actorId, String reason) {
        int now = SimulationClock.getCurrentMinute();

        if (isTerminal()) {
            throw new IllegalStateException(
                "A completed or cancelled job cannot be cancelled again."
            );
        }

        String validActor = requireText(actorId, "Cancelling user");
        String validReason = requireText(
            reason, "Cancellation reason"
        );

        requireAtOrAfter(
            now, startTime == -1 ? submissionTime : startTime
        );

        if ("Running".equals(status)) {
            int expectedFinish = startTime + getEstimatedDuration();

            if (now >= expectedFinish) {
                throw new IllegalStateException(
                    "This job is due for completion."
                );
            }

            finishTime = now;
        }

        cancellationTime = now;
        cancelledBy = validActor;
        cancellationReason = validReason;
        status = "Cancelled";
    }

    public boolean isTerminal() {
        return "Completed".equals(status)
            || "Cancelled".equals(status);
    }

    public int getElapsedRuntime() {
        int now = SimulationClock.getCurrentMinute();

        if (startTime == -1) {
            return 0;
        }

        int end = finishTime == -1 ? now : finishTime;
        requireAtOrAfter(end, startTime);

        return Math.min(
            end - startTime, getEstimatedDuration()
        );
    }

    public int getRemainingRuntime() {
        if (isTerminal()) {
            return 0;
        }

        return getEstimatedDuration() - getElapsedRuntime();
    }

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

    // Reports a charge; it does not deduct a student's balance.
    public int getChargeMinutes() {
        if ("Completed".equals(status)) {
            return getEstimatedDuration();
        }

        if ("Cancelled".equals(status) && startTime != -1) {
            return getElapsedRuntime();
        }

        return 0;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getTitle() {
        return title;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getDescription() {
        return description;
    }

    public String getTaskSize() {
        return taskSize;
    }

    public int getWorkloadUnits() {
        return durationForSize(taskSize);
    }

    public int getEstimatedDuration() {
        return getWorkloadUnits();
    }

    public String getStatus() {
        return status;
    }

    public int getSubmissionTime() {
        return submissionTime;
    }

    public String getAssignedGpuId() {
        return assignedGpuId;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getFinishTime() {
        return finishTime;
    }

    public int getCancellationTime() {
        return cancellationTime;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    @Override
    public String toString() {
        return "Job " + jobId
            + " | " + title
            + " | " + status
            + " | GPU: "
            + (assignedGpuId == null ? "Unassigned" : assignedGpuId);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                label + " is required."
            );
        }

        return value.trim();
    }

    private static int requireMinuteTime(int time) {
        if (time < 0) {
            throw new IllegalArgumentException(
                "Simulation time cannot be negative."
            );
        }

        return time;
    }

    private static void requireAtOrAfter(int time, int minimum) {
        requireMinuteTime(time);

        if (time < minimum) {
            throw new IllegalArgumentException(
                "Simulation time cannot go backwards."
            );
        }
    }
}
