import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class LoginPanel extends JPanel {
    public LoginPanel(AppController controller, Runnable onLogin,
                      Runnable onRegister, String message) {
        super(new BorderLayout());
        JPanel card = new JPanel(new BorderLayout(12, 18));
        card.setPreferredSize(new Dimension(530, 340));
        card.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));
        card.add(UiSupport.heading("Welcome to GPU Lab"), BorderLayout.NORTH);

        JTextField username = UiSupport.named(new JTextField(22), "login.username");
        JPasswordField password = UiSupport.named(new JPasswordField(22), "login.password");
        JPanel form = new JPanel(new GridBagLayout());
        UiSupport.formRow(form, 0, "Username / university ID", username);
        UiSupport.formRow(form, 1, "Password", password);
        card.add(form, BorderLayout.CENTER);

        JLabel feedback = UiSupport.named(new JLabel(message == null ? " " : message), "login.feedback");
        feedback.setForeground(UiSupport.TEAL);
        JButton login = UiSupport.button("Log in", "login.submit");
        JButton register = UiSupport.button("Register a student account", "login.register");
        JPanel actions = new JPanel();
        actions.add(login); actions.add(register);
        JPanel bottom = new JPanel(new BorderLayout(5, 10));
        bottom.add(feedback, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        add(UiSupport.centered(card));

        login.addActionListener(e -> {
            char[] chars = password.getPassword();
            String candidate = new String(chars);
            java.util.Arrays.fill(chars, '\0');
            password.setText("");
            try {
                if (controller.getState().login(username.getText(), candidate)) {
                    onLogin.run();
                } else {
                    feedback.setForeground(UiSupport.ERROR);
                    feedback.setText("Incorrect credentials or account not yet approved.");
                }
            } catch (IllegalStateException exception) {
                feedback.setForeground(UiSupport.ERROR);
                feedback.setText(exception.getMessage());
            }
        });
        password.addActionListener(e -> login.doClick());
        username.addActionListener(e -> password.requestFocusInWindow());
        register.addActionListener(e -> { password.setText(""); onRegister.run(); });
    }
}

