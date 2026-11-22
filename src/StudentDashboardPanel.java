import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import javax.swing.BoxLayout;
import javax.swing.Scrollable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class StudentDashboardPanel extends JPanel implements UiSupport.Refreshable {
    private AppController controller;
    private JLabel balance = UiSupport.named(new JLabel(), "student.balance");
    private JobTablePanel jobs;

    public StudentDashboardPanel(AppController controller) {
        super(new BorderLayout(12, 12));
        if (!(controller.getState().getCurrentUser() instanceof Student)) {
            throw new IllegalStateException("Student login required.");
        }
        this.controller = controller;
        setBorder(BorderFactory.createEmptyBorder(5, 15, 8, 15));
        balance.setFont(balance.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        balance.setBorder(BorderFactory.createEmptyBorder(8, 5, 8, 5));
        DashboardRows rows = new DashboardRows();
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        JPanel summary = UiSupport.section("My allowance");
        summary.add(balance);
        addDashboardRow(rows, summary, 80);

        JTextField title = UiSupport.named(new JTextField(20), "submit.title");
        JTextField app = UiSupport.named(new JTextField(20), "submit.application");
        JTextArea description = UiSupport.named(new JTextArea(6, 20), "submit.description");
        description.setLineWrap(true); description.setWrapStyleWord(true);
        JComboBox<String> size = UiSupport.named(new JComboBox<>(
            new String[] {"Small", "Medium", "Large", "Very large"}), "submit.size");
        JLabel duration = new JLabel("5 GPU minutes will be reserved.");
        JPanel form = new JPanel(new GridBagLayout());
        UiSupport.formRow(form, 0, "Title", title);
        UiSupport.formRow(form, 1, "Application", app);
        UiSupport.formRow(form, 2, "Description", new JScrollPane(description));
        UiSupport.formRow(form, 3, "Task size", size);
        UiSupport.formRow(form, 4, "", duration);
        JPanel submission = UiSupport.section("Submit a job");
        submission.add(form, BorderLayout.NORTH);
        JButton submit = UiSupport.button("Submit job", "submit.button");
        JPanel action = new JPanel(); action.add(submit);
        submission.add(action, BorderLayout.SOUTH);
        addDashboardRow(rows, submission, 350);

        jobs = new JobTablePanel(controller);
        jobs.setBorder(BorderFactory.createTitledBorder("My jobs"));
        addDashboardRow(rows, jobs, 520);

        JScrollPane scroll = UiSupport.named(
            new JScrollPane(rows), "student.dashboard.scroll");
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        size.addActionListener(e -> duration.setText(Job.durationForSize((String) size.getSelectedItem())
            + " GPU minutes will be reserved."));
        submit.addActionListener(e -> {
            if (controller.change(this,
                    () -> controller.getScheduler().submitJob(title.getText(), app.getText(),
                        description.getText(), (String) size.getSelectedItem()), "Job submitted.")) {
                title.setText(""); app.setText(""); description.setText("");
            }
        });
        refresh();
    }

    private void addDashboardRow(JPanel rows, JPanel row, int height) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(1000, height));
        row.setMinimumSize(new Dimension(0, height));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        rows.add(row);
        rows.add(javax.swing.Box.createVerticalStrut(10));
    }

    private static class DashboardRows extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() {
            return new Dimension(1100, 650);
        }

        public int getScrollableUnitIncrement(
                Rectangle visible, int orientation, int direction) {
            return 24;
        }

        public int getScrollableBlockIncrement(
                Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 24);
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    @Override public void refresh() {
        User user = controller.getState().getCurrentUser();
        if (!(user instanceof Student)) return;
        TimeAllowance allowance = controller.getState().getAllowance(user.getUsername());
        balance.setText("Allocated: " + allowance.getAllocatedMinutes()
            + " min     Used: " + allowance.getUsedMinutes()
            + " min     Reserved: " + allowance.getReservedMinutes()
            + " min     Available: " + allowance.getAvailableMinutes() + " min");
        jobs.refresh();
    }
}

