import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Scrollable;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

public class AdminDashboardPanel extends JPanel implements UiSupport.Refreshable {
    private final AppController controller;
    private final JLabel overview = UiSupport.named(new JLabel(), "admin.summary");
    private final JTextArea[] gpuDetails = new JTextArea[4];
    private final DefaultTableModel queueModel = UiSupport.model(
        "Position", "Job ID", "Student", "Size", "Submitted", "Predicted start", "Predicted finish", "Wait (min)");
    private final JTable queueTable = UiSupport.table(queueModel, "queue.table");
    private final DefaultTableModel requestModel = UiSupport.model(
        "University ID", "Name", "Email", "Phone", "Status");
    private final JTable requestTable = UiSupport.table(requestModel, "requests.table");
    private final JComboBox<String> requestFilter = UiSupport.named(new JComboBox<>(
        new String[] {"Pending", "All", "Approved", "Rejected"}), "requests.filter");
    private final JButton approve = UiSupport.button("Approve selected", "requests.approve");
    private final JButton reject = UiSupport.button("Reject selected", "requests.reject");
    private final DefaultTableModel studentModel = UiSupport.model(
        "University ID", "Name", "Email", "Phone", "Allocated", "Used", "Reserved", "Available");
    private final JTable studentTable = UiSupport.table(studentModel, "students.table");
    private final JTextField newTotal = UiSupport.named(new JTextField(8), "students.newTotal");
    private final JTextField adjustmentReason = UiSupport.named(new JTextField(24), "students.reason");
    private final JTextField studentSearch = UiSupport.named(new JTextField(20), "students.search");
    private final JButton adjust = UiSupport.button("Set total allocation", "students.adjust");
    private final DefaultTableModel historyModel = UiSupport.model(
        "Time", "Previous total", "New total", "Administrator", "Reason");
    private final JTable historyTable = UiSupport.table(historyModel, "students.history");
    private final DefaultTableModel completedModel = UiSupport.model(
        "Job ID", "Student", "Title", "Application", "GPU", "Submitted",
        "Started", "Finished", "Runtime (min)", "Wait (min)", "Charged (min)");
    private final JTable completedTable = UiSupport.table(completedModel, "completed.table");
    private final JobTablePanel jobs;
    private boolean updating;

    public AdminDashboardPanel(AppController controller) {
        super(new BorderLayout(10, 10));
        if (!controller.getState().isAdministratorLoggedIn()) {
            throw new IllegalStateException("Administrator login required.");
        }
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(5, 15, 8, 15));
        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.add(UiSupport.heading("Administrator dashboard"), BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton refresh = UiSupport.button("Refresh", "admin.refresh");
        JButton advance = UiSupport.button("Advance 1 minute", "admin.advance");
        actions.add(refresh); actions.add(advance);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        jobs = new JobTablePanel(controller);
        add(buildOverview(), BorderLayout.CENTER);
        refresh.addActionListener(e -> refresh());
        advance.addActionListener(e -> controller.advanceMinute(this));
        refresh();
    }

    private JPanel buildOverview() {
        DashboardRows rows = new DashboardRows();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

        JPanel summary = UiSupport.section("General information");
        overview.setFont(overview.getFont().deriveFont(java.awt.Font.BOLD, 14f));
        summary.add(overview);
        addDashboardRow(rows, summary, 80);

        JPanel cards = new JPanel(new GridLayout(1, 4, 10, 0));
        for (int i = 0; i < 4; i++) {
            JPanel card = UiSupport.section("G" + (i + 1) + " | NVIDIA H100");
            gpuDetails[i] = UiSupport.named(UiSupport.readOnlyText(6), "gpu.G" + (i + 1));
            gpuDetails[i].setBackground(UiSupport.BACKGROUND);
            card.add(gpuDetails[i]);
            cards.add(card);
        }
        addDashboardRow(rows, cards, 205);

        JPanel waiting = UiSupport.section("Waiting jobs — shared FCFS queue");
        waiting.add(UiSupport.scrollTable(queueTable));
        addDashboardRow(rows, waiting, 230);

        JPanel completed = UiSupport.section("Completed tasks");
        completed.add(UiSupport.scrollTable(completedTable));
        addDashboardRow(rows, completed, 245);

        JPanel allowances = UiSupport.section("Student allowances");
        allowances.add(buildStudents());
        addDashboardRow(rows, allowances, 460);

        JPanel requests = UiSupport.section("Registration requests");
        requests.add(buildRequests());
        addDashboardRow(rows, requests, 350);

        JPanel allJobs = UiSupport.section("All jobs");
        allJobs.add(jobs);
        addDashboardRow(rows, allJobs, 520);

        JScrollPane scroll = UiSupport.named(new JScrollPane(rows), "admin.dashboard.scroll");
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(scroll);
        return panel;
    }

    private void addDashboardRow(JPanel rows, JPanel row, int height) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(1000, height));
        row.setMinimumSize(new Dimension(0, height));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        rows.add(row);
        rows.add(javax.swing.Box.createVerticalStrut(10));
    }

    // Follow the viewport width while allowing the five rows to scroll vertically.
    private static class DashboardRows extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(1100, 650);
        }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private JPanel buildRequests() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("Show")); toolbar.add(requestFilter);
        toolbar.add(new JLabel("Approval activates the account with 100 GPU minutes."));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(UiSupport.scrollTable(requestTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(reject); actions.add(approve);
        panel.add(actions, BorderLayout.SOUTH);

        requestFilter.addActionListener(e -> refreshRequests());
        requestTable.getSelectionModel().addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) refreshRequestButtons();
        });
        approve.addActionListener(e -> {
            String id = selectedId(requestTable);
            if (id != null) controller.change(this,
                () -> controller.getState().approveRequest(id), "Student approved.");
        });
        reject.addActionListener(e -> {
            String id = selectedId(requestTable);
            if (id != null) controller.change(this,
                () -> controller.getState().rejectRequest(id), "Request rejected.");
        });
        return panel;
    }

     private JPanel buildStudents() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
        
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchBar.add(new JLabel("Search university ID:"));
        searchBar.add(studentSearch);
        panel.add(searchBar, BorderLayout.NORTH);
        UiSupport.onTextChange(studentSearch,this::refreshStudents);
        
        JPanel students = UiSupport.section("Approved students — select one to adjust their allowance");
        students.add(UiSupport.scrollTable(studentTable), BorderLayout.CENTER);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fields.add(new JLabel("New TOTAL minutes")); fields.add(newTotal);
        fields.add(new JLabel("Reason")); fields.add(adjustmentReason); fields.add(adjust);
        students.add(fields, BorderLayout.SOUTH);
        JPanel history = UiSupport.section("Selected student's allocation history");
        history.add(UiSupport.scrollTable(historyTable));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, students, history);
        split.setResizeWeight(0.62); split.setDividerLocation(245);
        panel.add(split);
        studentTable.getSelectionModel().addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting()) showStudentHistory();
        });
        adjust.addActionListener(e -> {
            String id = selectedId(studentTable);
            if (id == null) return;
            boolean changed = controller.change(this, () -> {
                int total;
                try {
                    total = Integer.parseInt(newTotal.getText().trim());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Enter a whole number for the new total allocation.");
                }
                controller.getState().adjustAllowance(id, total, adjustmentReason.getText());
            }, "Allocation updated.");
            if (changed) adjustmentReason.setText("");
        });
        return panel;
    }

    @Override public void refresh() {
        if (!controller.getState().isAdministratorLoggedIn()) return;
        refreshOverview();
        refreshRequests();
        refreshStudents();
        jobs.refresh();
    }

    private void refreshOverview() {
        ArrayList<Job> allJobs = controller.getScheduler().getJobs();
        int running = 0, completed = 0;
        long busyMinutes = 0;
        ArrayList<Object[]> completedRows = new ArrayList<>();
        for (Job job : allJobs) {
            // Includes completed work and elapsed running/cancelled work, never queue time.
            busyMinutes += job.getElapsedRuntime();
            if ("Completed".equals(job.getStatus())) {
                completed++;
                completedRows.add(new Object[] {job.getJobId(), job.getStudentId(),
                    job.getTitle(), job.getApplicationName(), job.getAssignedGpuId(),
                    UiSupport.time(job.getSubmissionTime()), UiSupport.time(job.getStartTime()),
                    UiSupport.time(job.getFinishTime()), job.getElapsedRuntime(),
                    job.getQueueWait(), job.getChargeMinutes()});
            }
        }
        ArrayList<Job> queue = controller.getScheduler().getQueue();
        int now = SimulationClock.getCurrentMinute();
        String utilization = now == 0 ? "N/A"
            : String.format(java.util.Locale.ROOT, "%.1f%%", busyMinutes * 100.0 / (4.0 * now));

        for (int i = 0; i < 4; i++) {
            GPU gpu = controller.getState().findGpu("G" + (i + 1));
            Job current = gpu.getCurrentJob();
            if (current != null) running++;
            String text = current == null ? "Available\nNo running job."
                : "Running: " + current.getTitle()
                    + "\nJob: " + current.getJobId()
                    + "\nStudent: " + current.getStudentId()
                    + "\nRemaining: " + gpu.getRemainingRuntime() + " min"
                    + "\nExpected finish: " + UiSupport.time(gpu.getExpectedFinish());
            gpuDetails[i].setText(text);
            gpuDetails[i].setCaretPosition(0);
        }
        overview.setText("Running GPUs: " + running + "/4     Queued jobs: " + queue.size()
            + "     Completed jobs: " + completed + "     Overall utilization: " + utilization);
        UiSupport.replaceTableRows(completedTable, completedModel, completedRows);
        ArrayList<Object[]> queueRows = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            Job job = queue.get(i);
            queueRows.add(new Object[] {i + 1, job.getJobId(), job.getStudentId(), job.getTaskSize(),
                UiSupport.time(job.getSubmissionTime()), UiSupport.time(controller.getScheduler()
                    .getPredictedStartTime(job.getJobId())), UiSupport.time(controller.getScheduler()
                    .getPredictedFinishTime(job.getJobId())), controller.getScheduler()
                    .getPredictedWaitMinutes(job.getJobId())});
        }
        UiSupport.replaceTableRows(queueTable, queueModel, queueRows);
    }

    private void refreshRequests() {
        String selected = selectedId(requestTable);
        updating = true;
        try {
            requestTable.clearSelection();
            ArrayList<Object[]> requestRows = new ArrayList<>();
            String filter = (String) requestFilter.getSelectedItem();
            for (AccessRequest request : controller.getState().getRequests()) {
                if (!"All".equals(filter) && !request.getStatus().equals(filter)) continue;
                requestRows.add(new Object[] {request.getUniversityId(), request.getName(),
                    request.getEmail(), request.getPhone(), request.getStatus()});
            }
            UiSupport.replaceTableRows(requestTable, requestModel, requestRows);
            restoreSelection(requestTable, selected);
        } finally { updating = false; }
        refreshRequestButtons();
    }

    private void refreshRequestButtons() {
        String id = selectedId(requestTable);
        AccessRequest request = id == null ? null : controller.getState().findRequest(id);
        boolean pending = request != null && "Pending".equals(request.getStatus());
        approve.setEnabled(pending); reject.setEnabled(pending);
    }

    private void refreshStudents() {
        String selected = selectedId(studentTable);
        String query = studentSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
    
        updating = true;
    
        try {
            studentTable.clearSelection();
            ArrayList<Object[]> studentRows = new ArrayList<>();
            for (User user : controller.getState().getUsers()) {
                if (!(user instanceof Student)) {
                    continue;
                }
    
                Student student = (Student) user;
                String universityId = student.getUniversityId();
                if (!universityId.toLowerCase(java.util.Locale.ROOT)
                        .contains(query)) {
                    continue;
                }
    
                TimeAllowance allowance =
                    controller.getState().getAllowance(universityId);
    
                studentRows.add(new Object[] {universityId,student.getName(),student.getEmail(),student.getPhone(),allowance.getAllocatedMinutes(),allowance.getUsedMinutes(),allowance.getReservedMinutes(),allowance.getAvailableMinutes()});
            }
    
            UiSupport.replaceTableRows(studentTable,studentModel,studentRows);
    
            restoreSelection(studentTable, selected);
    
        } finally {
            updating = false;
        }
    
        showStudentHistory();
}

    private void showStudentHistory() {
        String id = selectedId(studentTable);
        adjust.setEnabled(id != null);
        ArrayList<Object[]> historyRows = new ArrayList<>();
        if (id == null) {
            newTotal.setText("");
            UiSupport.replaceTableRows(historyTable, historyModel, historyRows);
            return;
        }
        TimeAllowance allowance = controller.getState().getAllowance(id);
        newTotal.setText(String.valueOf(allowance.getAllocatedMinutes()));
        for (AllowanceChange change : allowance.getChanges()) {
            historyRows.add(new Object[] {UiSupport.time(change.getChangedAt()),
                change.getPreviousTotal(), change.getNewTotal(), change.getAdminId(), change.getReason()});
        }
        UiSupport.replaceTableRows(historyTable, historyModel, historyRows);
    }

    private String selectedId(JTable table) {
        int view = table.getSelectedRow();
        return view < 0 ? null : String.valueOf(table.getModel().getValueAt(
            table.convertRowIndexToModel(view), 0));
    }

    private void restoreSelection(JTable table, String id) {
        if (id == null) return;
        for (int i = 0; i < table.getModel().getRowCount(); i++) {
            if (id.equals(String.valueOf(table.getModel().getValueAt(i, 0)))) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) table.setRowSelectionInterval(view, view);
            }
        }
    }
}
