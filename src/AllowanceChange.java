public class AllowanceChange {
    private final int previousTotal, newTotal, changedAt;
    private final String adminId, reason;

    public AllowanceChange(int previousTotal, int newTotal,
                           String adminId, String reason) {
        this(previousTotal, newTotal, adminId, reason, SimulationClock.getCurrentMinute());
    }

    private AllowanceChange(int previousTotal, int newTotal,
                            String adminId, String reason, int changedAt) {
        if (changedAt < 0) throw new IllegalArgumentException("Invalid change time.");
        if (previousTotal < 0 || newTotal < 0) {
            throw new IllegalArgumentException("Allocation cannot be negative.");
        }
        this.previousTotal = previousTotal;
        this.newTotal = newTotal;
        this.adminId = User.requireText(adminId, "Administrator ID");
        this.reason = User.requireText(reason, "Reason");
        this.changedAt = changedAt;
    }

    static AllowanceChange restore(int previousTotal, int newTotal,
                                   String adminId, String reason, int changedAt) {
        return new AllowanceChange(previousTotal, newTotal, adminId, reason, changedAt);
    }

    public int getPreviousTotal() { return previousTotal; }
    public int getNewTotal() { return newTotal; }
    public int getChangedAt() { return changedAt; }
    public String getAdminId() { return adminId; }
    public String getReason() { return reason; }
}
