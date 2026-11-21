import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerDateModel;

public class MainFrame extends JFrame {

    private final AppController controller;

    private final JPanel content = new JPanel(new BorderLayout());

    private final JLabel identity = new JLabel();
    private final JLabel clock = new JLabel();
    private final JLabel status = new JLabel();

    private final JButton logout =
            UiSupport.button("Log out", "window.logout");

    private final JButton setTime =
            new JButton("Set Time");

    private final JSpinner dateSpinner =
            new JSpinner(new SpinnerDateModel());

    private final JSpinner timeSpinner =
            new JSpinner(new SpinnerDateModel());

    private JPanel activePanel;

    public MainFrame(ApplicationState state,
                     Scheduler scheduler,
                     FileStateStorage storage) {

        super("GPU Lab - Simulation Dashboard");

        controller = new AppController(state, scheduler, storage);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1020, 700));
        setSize(1240, 820);
        setLayout(new BorderLayout(0, 8));

        // Date and time pickers
        dateSpinner.setEditor(
                new JSpinner.DateEditor(dateSpinner, "dd/MM/yyyy"));

        timeSpinner.setEditor(
                new JSpinner.DateEditor(timeSpinner, "HH:mm"));

        JPanel header = new JPanel(new BorderLayout(15, 0));

        header.setBorder(
                BorderFactory.createEmptyBorder(12, 16, 12, 16));

        identity.setFont(
                identity.getFont().deriveFont(java.awt.Font.BOLD, 17f));

        header.add(identity, BorderLayout.WEST);

        JPanel actions =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));

        JButton save =
                UiSupport.button("Save", "window.save");

        actions.add(new JLabel("Date:"));
        actions.add(dateSpinner);

        actions.add(new JLabel("Time:"));
        actions.add(timeSpinner);

        actions.add(setTime);
        actions.add(clock);
        actions.add(save);
        actions.add(logout);

        header.add(actions, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        status.setBorder(
                BorderFactory.createEmptyBorder(6, 16, 12, 16));

        add(status, BorderLayout.SOUTH);

        // Set starting date and time
        setTime.addActionListener(e -> {

            try {

                Date selectedDate = (Date) dateSpinner.getValue();
                Date selectedTime = (Date) timeSpinner.getValue();

                LocalDateTime date =
                        selectedDate.toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime();

                LocalDateTime time =
                        selectedTime.toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDateTime();

                LocalDateTime start =
                        LocalDateTime.of(
                                date.toLocalDate(),
                                time.toLocalTime().withSecond(0).withNano(0));

                controller.initializeClock(this, start);
                
                JOptionPane.showMessageDialog(
                        this, "Simulation time initialized!");

                refresh();

            } catch (Exception ex) {

                JOptionPane.showMessageDialog(
                        this,
                        ex.getMessage(),
                        "Time Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        // Save
        save.addActionListener(e -> controller.save(this));

        // Logout
        logout.addActionListener(e -> {

            if (controller.logout(this)) {
                showLogin("");
            }
        });

        // Close window
        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent event) {

                if (controller.canClose(MainFrame.this)) {
                    dispose();
                }
            }
        });

        controller.setOnRefresh(this::refresh);
        if (SimulationClock.isInitialized()) {

            LocalDateTime start = SimulationClock.getStartDateTime();

            Date savedDate = Date.from(
                start.atZone(ZoneId.systemDefault()).toInstant()
            );

            dateSpinner.setValue(savedDate);
            timeSpinner.setValue(savedDate);
        }
        showLogin("");

        setLocationRelativeTo(null);
    }

    private void showLogin(String message) {

        showPanel(new LoginPanel(
                controller,
                this::showDashboard,
                this::showRegistration,
                message));
    }

    private void showRegistration() {

        showPanel(new RegistrationPanel(
                controller,
                this::showLogin));
    }

    private void showDashboard() {

        if (controller.getState().getCurrentUser()
                instanceof Administrator) {

            showPanel(new AdminDashboardPanel(controller));

        } else if (controller.getState().getCurrentUser()
                instanceof Student) {

            showPanel(new StudentDashboardPanel(controller));

        } else {

            showLogin("");
        }
    }

    private void showPanel(JPanel panel) {

        activePanel = panel;

        content.removeAll();
        content.add(panel, BorderLayout.CENTER);

        refresh();

        content.revalidate();
        content.repaint();
    }

    private void refresh() {

        User user = controller.getState().getCurrentUser();

        identity.setText(
                user == null
                        ? "GPU Lab | Sign in"
                        : "GPU Lab | " + user.getName()
                        + " | " + user.getRole());

        clock.setText(
                "Simulation time: "
                + SimulationClock.getFormattedTime());

        // Disable date/time selection after initialization
        boolean notInitialized = !SimulationClock.isInitialized();

        dateSpinner.setEnabled(notInitialized);
        timeSpinner.setEnabled(notInitialized);
        setTime.setEnabled(notInitialized);

        logout.setVisible(user != null);

        status.setText(controller.getStatus());

        status.setForeground(
                controller.hasUnsavedChanges()
                        ? UiSupport.ERROR
                        : UiSupport.TEAL);

        if (user != null
                && activePanel instanceof UiSupport.Refreshable) {

            ((UiSupport.Refreshable) activePanel).refresh();
        }
    }
}