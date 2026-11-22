import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class RegistrationPanel extends JPanel {
    public RegistrationPanel(AppController controller, Consumer<String> onBack) {
        super(new BorderLayout());
        JPanel card = new JPanel(new BorderLayout(12, 16));
        card.setPreferredSize(new Dimension(620, 460));
        card.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel heading = new JPanel(new BorderLayout(0, 10));
        heading.add(UiSupport.heading("Student registration"), BorderLayout.NORTH);
        heading.add(new JLabel("An administrator must approve your request before you can log in."),
            BorderLayout.SOUTH);
        card.add(heading, BorderLayout.NORTH);

        JTextField id = UiSupport.named(new JTextField(24), "registration.id");
        JTextField name = UiSupport.named(new JTextField(24), "registration.name");
        JTextField email = UiSupport.named(new JTextField(24), "registration.email");
        JTextField phone = UiSupport.named(new JTextField(24), "registration.phone");
        JPasswordField password = UiSupport.named(new JPasswordField(24), "registration.password");
        JPanel form = new JPanel(new GridBagLayout());
        UiSupport.formRow(form, 0, "University ID", id);
        UiSupport.formRow(form, 1, "Full name", name);
        UiSupport.formRow(form, 2, "Email", email);
        UiSupport.formRow(form, 3, "Phone", phone);
        UiSupport.formRow(form, 4, "Chosen password", password);
        card.add(form, BorderLayout.CENTER);

        JButton submit = UiSupport.button("Submit request", "registration.submit");
        JButton back = UiSupport.button("Back to login", "registration.back");
        JPanel actions = new JPanel(); actions.add(back); actions.add(submit);
        card.add(actions, BorderLayout.SOUTH);
        add(UiSupport.centered(card));

        submit.addActionListener(e -> {
            char[] chars = password.getPassword();
            String candidate = new String(chars);
            java.util.Arrays.fill(chars, '\0');
            boolean changed = controller.change(this,
                () -> controller.getState().register(id.getText(), name.getText(),
                    email.getText(), phone.getText(), candidate),
                "Registration request submitted.");
            if (changed) {
                password.setText("");
                onBack.accept(controller.hasUnsavedChanges()
                    ? "Request submitted. Use Save to finish saving it."
                    : "Request submitted. Please wait for administrator approval.");
            }
        });
        back.addActionListener(e -> { password.setText(""); onBack.accept(""); });
    }
}
