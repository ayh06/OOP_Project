import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Locale;
import javax.swing.BorderFactory;
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

/** Reused by both dashboards; ApplicationState supplies only authorised jobs. */
public class JobTablePanel extends JPanel implements UiSupport.Refreshable {
    private final AppController controller;
    private final DefaultTableModel model = UiSupport.model(
        "Job ID", "Student", "Title", "Size", "Status", "GPU", "Wait (min)", "Expected finish");
    private final JTable table = UiSupport.table(model, "jobs.table");
    private final JComboBox<String> statusFilter = UiSupport.named(new JComboBox<>(
        new String[] {"All", "Queued", "Running", "Completed", "Cancelled"}), "jobs.filter");
    private final JTextField search = UiSupport.named(new JTextField(20), "jobs.search");
    private final JTextArea details = UiSupport.named(UiSupport.readOnlyText(7), "jobs.details");
    private final JTextField reason = UiSupport.named(new JTextField(24), "jobs.cancelReason");
    private final JButton cancel = UiSupport.button("Cancel selected job", "jobs.cancel");
    private final ArrayList<Job> rows = new ArrayList<>();
    private boolean updating;

    public JobTablePanel(AppController controller) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("Status")); toolbar.add(statusFilter);
        toolbar.add(new JLabel("Search")); toolbar.add(search);
        add(toolbar, BorderLayout.NORTH);

        JScrollPane tableScroll = UiSupport.scrollTable(table);
        tableScroll.setMinimumSize(new Dimension(300, 160));
        JScrollPane detailScroll = new JScrollPane(details);
        detailScroll.setBorder(BorderFactory.createTitledBorder("Selected job details"));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailScroll);
        split.setResizeWeight(0.68);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);

        JPanel cancellation = new JPanel(new BorderLayout(8, 0));
        cancellation.add(new JLabel("Cancellation reason"), BorderLayout.WEST);
        cancellation.add(reason, BorderLayout.CENTER);
        cancellation.add(cancel, BorderLayout.EAST);
        add(cancellation, BorderLayout.SOUTH);
        statusFilter.addActionListener(e -> refresh());
        UiSupport.onTextChange(search, this::refresh);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating) showDetails();
        });
        cancel.addActionListener(e -> {
            Job job = selectedJob();
            if (job == null) return;
            if (controller.change(this,
                    () -> controller.getScheduler().cancelJob(job.getJobId(), reason.getText()),
                    "Job cancelled.")) {
                reason.setText("");
            }
        });
        refresh();
    }

    @Override public void refresh() {
        Job selected = selectedJob();
        String selectedId = selected == null ? null : selected.getJobId();
        updating = true;
        try {
            table.clearSelection();
            ArrayList<Object[]> tableRows = new ArrayList<>();
            rows.clear();
            String filter = (String) statusFilter.getSelectedItem();
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            for (Job job : controller.getScheduler().getJobs()) {
                if (!"All".equals(filter) && !job.getStatus().equals(filter)) continue;
                String searchable = job.getJobId() + " " + job.getStudentId() + " "
                    + job.getTitle() + " " + job.getApplicationName();
                if (!searchable.toLowerCase(Locale.ROOT).contains(query)) continue;
                rows.add(job);
                String wait = "—";
                String finish;
                if (job.isTerminal()) {
                    finish = UiSupport.time(job.getFinishTime());
                } else {
                    wait = String.valueOf(controller.getScheduler().getPredictedWaitMinutes(job.getJobId()));
                    finish = UiSupport.time(controller.getScheduler().getPredictedFinishTime(job.getJobId()));
                }
                tableRows.add(new Object[] {job.getJobId(), job.getStudentId(), job.getTitle(),
                    job.getTaskSize(), job.getStatus(), job.getAssignedGpuId() == null ? "—"
                    : job.getAssignedGpuId(), wait, finish});
            }
            UiSupport.replaceTableRows(table, model, tableRows);
            if (selectedId != null) {
                for (int i = 0; i < rows.size(); i++) {
                    if (rows.get(i).getJobId().equals(selectedId)) {
                        int view = table.convertRowIndexToView(i);
                        if (view >= 0) table.setRowSelectionInterval(view, view);
                    }
                }
            }
        } finally {
            updating = false;
        }
        showDetails();
    }

    private Job selectedJob() {
        int view = table.getSelectedRow();
        if (view < 0) return null;
        int index = table.convertRowIndexToModel(view);
        return index < rows.size() ? rows.get(index) : null;
    }

    private void showDetails() {
        Job job = selectedJob();
        cancel.setEnabled(job != null && !job.isTerminal());
        if (job == null) {
            details.setText("Select a job to view its description and execution history.");
            return;
        }
        String text = "Job: " + job.getJobId() + " | Student: " + job.getStudentId()
            + " | Application: " + job.getApplicationName()
            + "\nDuration: " + job.getEstimatedDuration() + " minutes | Status: " + job.getStatus()
            + "\nSubmitted: " + UiSupport.time(job.getSubmissionTime())
            + " | Started: " + UiSupport.time(job.getStartTime())
            + " | Finished: " + UiSupport.time(job.getFinishTime())
            + "\nTime spent waiting: " + job.getQueueWait()
            + " min | Execution so far: " + job.getElapsedRuntime() + " min"
            + (job.isTerminal() ? " | Charge: " + job.getChargeMinutes() + " min" : "")
            + "\nDescription: " + job.getDescription();
        if (!job.isTerminal()) {
            text += "\nPredicted start: " + UiSupport.time(controller.getScheduler()
                .getPredictedStartTime(job.getJobId()));
        }
        if ("Cancelled".equals(job.getStatus())) {
            text += "\nCancelled at " + UiSupport.time(job.getCancellationTime())
                + " by " + job.getCancelledBy() + ": " + job.getCancellationReason();
        }
        details.setText(text);
        details.setCaretPosition(0);
    }
}

