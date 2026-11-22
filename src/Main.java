import java.io.IOException;
import java.awt.GraphicsEnvironment;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main{
    public static void main(String[] args) {
        
        FileStateStorage storage = new FileStateStorage(args.length == 0 ? "data" : args[0]);

        try {
            ApplicationState state = storage.load();
            Scheduler scheduler = new Scheduler(state);

            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // Swing's default appearance is available as a fallback.
                }
                new MainFrame(state, scheduler, storage).setVisible(true);
            });
        } catch (IOException exception) {
            System.err.println("Could not load the simulation: " + exception.getMessage());
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                "Could not load the saved simulation.\n" + exception.getMessage(),
                "GPU Lab — load failed", JOptionPane.ERROR_MESSAGE));
        }
    }
}

