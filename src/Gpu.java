/** One simulated GPU, identified by a nonblank string such as "GPU-01". */
public class GPU {

    private final String gpuId;
    private Job currentJob; // null means idle.

    public GPU(String gpuId) {
        if (gpuId == null || gpuId.trim().isEmpty()) {
            throw new IllegalArgumentException("GPU ID is required.");
        }
        this.gpuId = gpuId.trim();
    }

    public String getGpuId() {
        return gpuId;
    }

    public Job getCurrentJob() {
        return currentJob;
    }

    public boolean isAvailable() {
        return currentJob == null;
    }

    /** The scheduler chooses the job; this class only checks and assigns it. */
    public void assign(Job job) {
        if (!isAvailable()) {
            throw new IllegalStateException("GPU " + gpuId + " is already busy.");
        }
        if (job == null) {
            throw new IllegalArgumentException("A job is required.");
        }

        // If start() rejects the job, currentJob stays null.
        job.start(gpuId);
        currentJob = job;
    }

    /** Clear the assignment only after the job completes or is cancelled. */
    public void release() {
        if (currentJob != null && !currentJob.isTerminal()) {
            throw new IllegalStateException(
                "Complete or cancel the current job before releasing the GPU.");
        }
        currentJob = null;
    }

    // Reconnect saved running work without starting it a second time.
    void restoreRunningJob(Job job) {
        if (!isAvailable() || job == null || !"Running".equals(job.getStatus())
                || !gpuId.equals(job.getAssignedGpuId())) {
            throw new IllegalArgumentException("Invalid saved GPU assignment.");
        }
        currentJob = job;
    }

    public int getRemainingRuntime() {
        if (isAvailable()) {
            return 0;
        }
        return currentJob.getRemainingRuntime();
    }

    /** Expected finish in simulation minutes; -1 means this GPU is idle. */
    public int getExpectedFinish() {
        if (isAvailable()) {
            return -1;
        }
        return currentJob.getStartTime() + currentJob.getEstimatedDuration();
    }
}

