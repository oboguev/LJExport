package my.LJExport.runtime.ui;


import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class UIDialogQuestion {

    public static String askQuestion(String questionText, String defaultButtonTitle,
                                     String buttonATitle, String buttonBTitle) throws Exception {

        if (questionText == null || buttonATitle == null || buttonBTitle == null) {
            throw new IllegalArgumentException("Question text and button titles must not be null.");
        }

        final String[] result = new String[1];
        final JDialog dialog = new JDialog((Frame) null, "Question", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        // Top Panel: icon + text
        JPanel messagePanel = new JPanel(new BorderLayout(10, 10));
        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));
        JTextArea textArea = new JTextArea(questionText);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(UIManager.getFont("Label.font"));

        messagePanel.add(iconLabel, BorderLayout.WEST);
        messagePanel.add(textArea, BorderLayout.CENTER);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Bottom Panel: two buttons
        JButton buttonA = new JButton(buttonATitle);
        JButton buttonB = new JButton(buttonBTitle);

        buttonA.addActionListener(e -> {
            result[0] = buttonATitle;
            dialog.dispose();
        });

        buttonB.addActionListener(e -> {
            result[0] = buttonBTitle;
            dialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.add(buttonA);
        buttonPanel.add(buttonB);

        // Determine default button
        if (defaultButtonTitle != null) {
            if (defaultButtonTitle.equals(buttonATitle)) {
                dialog.getRootPane().setDefaultButton(buttonA);
            } else if (defaultButtonTitle.equals(buttonBTitle)) {
                dialog.getRootPane().setDefaultButton(buttonB);
            }
        }

        // Compose dialog
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(messagePanel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(null);

        // Ensure dialog gets focus
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                dialog.requestFocus();
            }
        });

        // Show dialog
        try {
            EventQueue.invokeLater(() -> dialog.setVisible(true));
            while (dialog.isVisible()) {
                Thread.sleep(50);
            }
        } catch (Exception e) {
            throw new Exception("Dialog display failed", e);
        }

        if (result[0] == null) {
            throw new Exception("Dialog closed without button press");
        }

        return result[0];
    }
}
