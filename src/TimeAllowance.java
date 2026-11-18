import java.util.ArrayList;

public class TimeAllowance {
    private final String studentId;
    private int allocatedMinutes, usedMinutes;
    private final ArrayList<Job> reservations = new ArrayList<>();
    private final ArrayList<String> settledIds = new ArrayList<>();
    private final ArrayList<AllowanceChange> changes = new ArrayList<>();

    public TimeAllowance(String studentId, int initialMinutes) {
        this.studentId = User.requireText(studentId, "Student ID");
        if (initialMinutes < 0) {
            throw new IllegalArgumentException("Initial allowance cannot be negative.");
        }
        allocatedMinutes = initialMinutes;
    }

    public String getStudentId() { return studentId; }

    public int getAllocatedMinutes() { return allocatedMinutes; }
    public int getUsedMinutes() { return usedMinutes; }

    public int getReservedMinutes() {
        int total = 0;
        for (Job job : reservations) total += job.getEstimatedDuration();
        return total;
    }

    public int getAvailableMinutes() {
        return allocatedMinutes - usedMinutes - getReservedMinutes();
    }

    public ArrayList<AllowanceChange> getChanges() {
        return new ArrayList<>(changes);
    }

    public void reserve(Job job) {
        requireOwnJob(job);
        String id = job.getJobId();
        if (!"Queued".equals(job.getStatus()) || findReservation(id) != null
                || settledIds.contains(id)) {
            throw new IllegalStateException("Job cannot be reserved again or after starting.");
        }
        if (job.getEstimatedDuration() > getAvailableMinutes()) {
            throw new IllegalStateException("Insufficient available minutes.");
        }
        reservations.add(job);
    }

    public void settleJob(Job job) {
        requireOwnJob(job);
        if (!job.isTerminal()) {
            throw new IllegalStateException("Job has not ended.");
        }
        if (settledIds.contains(job.getJobId())) return;
        if (findReservation(job.getJobId()) != job) {
            throw new IllegalStateException("Job has no matching reservation.");
        }
        usedMinutes += job.getChargeMinutes();
        reservations.remove(job);
        settledIds.add(job.getJobId());
    }

    // Called by Administrator after checking the logged-in account.
    void setAllocatedMinutes(int total, String adminId, String reason) {
        if (total < (long) usedMinutes + getReservedMinutes()) {
            throw new IllegalArgumentException("Total cannot be below used plus reserved minutes.");
        }
        AllowanceChange change = new AllowanceChange(
            allocatedMinutes, total, adminId, reason);
        changes.add(change);
        allocatedMinutes = total;
    }

    // Rebuild used/reserved values from the SAME restored Job objects.
    static TimeAllowance restore(String studentId, int total,
                                 ArrayList<Job> jobs, ArrayList<AllowanceChange> history,
                                 int savedMinute) {
        TimeAllowance allowance = new TimeAllowance(studentId, total);
        int previous = 100; // Approval grants 100 minutes in this application.
        int previousTime = 0;
        for (AllowanceChange change : history) {
            if (change.getPreviousTotal() != previous || change.getChangedAt() < previousTime
                    || change.getChangedAt() > savedMinute || !"admin".equals(change.getAdminId())) {
                throw new IllegalArgumentException("Invalid allowance-change history.");
            }
            previous = change.getNewTotal();
            previousTime = change.getChangedAt();
            allowance.changes.add(change);
        }
        if (previous != total) {
            throw new IllegalArgumentException("Allocation differs from its change history.");
        }
        long used = 0;
        long reserved = 0;
        for (Job job : jobs) {
            if (!studentId.equals(job.getStudentId())) continue;
            if (job.isTerminal()) {
                used += job.getChargeMinutes();
                allowance.settledIds.add(job.getJobId());
            } else {
                reserved += job.getEstimatedDuration();
                allowance.reservations.add(job);
            }
        }
        if (used + reserved > total) {
            throw new IllegalArgumentException("Saved jobs exceed the student's allocation.");
        }
        allowance.usedMinutes = (int) used;
        return allowance;
    }

    boolean sameAccountingAs(TimeAllowance other) {
        if (allocatedMinutes != other.allocatedMinutes || usedMinutes != other.usedMinutes
                || reservations.size() != other.reservations.size()
                || settledIds.size() != other.settledIds.size()
                || !settledIds.containsAll(other.settledIds)) return false;
        for (Job job : reservations) {
            Job match = other.findReservation(job.getJobId());
            if (match == null || match.getEstimatedDuration() != job.getEstimatedDuration()) return false;
        }
        return true;
    }

    private Job findReservation(String id) {
        for (Job job : reservations) {
            if (job.getJobId().equals(id)) return job;
        }
        return null;
    }

    private void requireOwnJob(Job job) {
        if (job == null || !studentId.equals(job.getStudentId())) {
            throw new IllegalArgumentException("Job must belong to this student.");
        }
    }
}
