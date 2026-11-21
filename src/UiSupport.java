import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

public final class UiSupport {

    static final Color NAVY = new Color(24, 44, 65);
    static final Color TEAL = new Color(0, 108, 112);
    static final Color BACKGROUND = new Color(244, 247, 249);
    static final Color ERROR = new Color(170, 40, 40);

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private UiSupport() { }

    interface Refreshable {
        void refresh();
    }

    interface Dialogs {
        void error(Component parent, String message);
        int closeChoice(Component parent);
    }

    static Dialogs swingDialogs() {

        return new Dialogs() {

            public void error(Component parent, String message) {

                JOptionPane.showMessageDialog(
                    parent,
                    message,
                    "GPU Lab",
                    JOptionPane.ERROR_MESSAGE);
            }

            public int closeChoice(Component parent) {

                return JOptionPane.showOptionDialog(
                    parent,
                    "Some changes have not been saved. What would you like to do?",
                    "Unsaved changes",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    new String[] {
                        "Save and close",
                        "Keep open",
                        "Close without saving"
                    },
                    "Save and close");
            }
        };
    }

    static <T extends JComponent> T named(T component, String name) {

        component.setName(name);
        return component;
    }

    static JButton button(String text, String name) {

        JButton button = named(new JButton(text), name);
        button.setMargin(new Insets(7, 13, 7, 13));

        return button;
    }

    static JLabel heading(String text) {

        JLabel label = new JLabel(text);

        label.setFont(
            new Font(Font.SANS_SERIF, Font.BOLD, 22));

        label.setForeground(NAVY);

        return label;
    }

    static JPanel section(String title) {

        JPanel panel = new JPanel(new BorderLayout(10, 10));

        panel.setBorder(
            BorderFactory.createTitledBorder(title));

        return panel;
    }

    static void formRow(JPanel form, int row,
                        String label, JComponent input) {

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(7, 5, 7, 10);

        form.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;

        form.add(input, c);
    }

    static DefaultTableModel model(String... columns) {

        return new DefaultTableModel(columns, 0) {

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static JTable table(DefaultTableModel model, String name) {

        JTable table = named(new JTable(model), name);

        table.setRowHeight(27);

        table.setSelectionMode(
            javax.swing.ListSelectionModel.SINGLE_SELECTION);

        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        table.getTableHeader().setReorderingAllowed(false);

        return table;
    }

    static JScrollPane scrollTable(JTable table) {

        JScrollPane scroll = new JScrollPane(table);

        scroll.setColumnHeaderView(table.getTableHeader());

        return scroll;
    }

    static void replaceTableRows(
            JTable table,
            DefaultTableModel model,
            java.util.ArrayList<Object[]> rows) {

        javax.swing.RowSorter<? extends javax.swing.table.TableModel> sorter =
                table.getRowSorter();

        table.clearSelection();
        table.setRowSorter(null);

        try {

            model.setRowCount(0);

            for (Object[] row : rows) {
                model.addRow(row);
            }

        } finally {

            if (sorter != null) {
                sorter.allRowsChanged();
            }

            table.setRowSorter(sorter);
        }
    }

    static JTextArea readOnlyText(int rows) {

        JTextArea area = new JTextArea(rows, 30);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        area.setFont(
            new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        area.setMargin(new Insets(8, 8, 8, 8));

        return area;
    }

    static void onTextChange(JTextField field, Runnable action) {

        field.getDocument().addDocumentListener(
            new DocumentListener() {

                public void insertUpdate(DocumentEvent e) {
                    action.run();
                }

                public void removeUpdate(DocumentEvent e) {
                    action.run();
                }

                public void changedUpdate(DocumentEvent e) {
                    action.run();
                }
            });
    }

    // NEW: Display simulation date and time
    static String time(int minute) {

        if (minute < 0) {
            return "—";
        }

        if (!SimulationClock.isInitialized()) {
            return "Not initialized";
        }

        LocalDateTime dateTime =
                SimulationClock.getStartDateTime().plusMinutes(minute);

        return dateTime.format(DATE_FORMAT);
    }

    static JPanel centered(JPanel card) {

        JPanel wrapper =
                new JPanel(new java.awt.GridBagLayout());

        wrapper.setBackground(BACKGROUND);

        wrapper.add(card);

        return wrapper;
    }
}