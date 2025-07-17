package my.LJExport.runtime.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class UIDialogQuestion
{
    public static String askQuestion(String questionText, String defaultButtonTitle,
            String buttonATitle, String buttonBTitle) throws Exception
    {
        if (questionText == null || buttonATitle == null || buttonBTitle == null)
        {
            throw new IllegalArgumentException("Question text and button titles must not be null.");
        }

        final String[] result = new String[1];

        final JDialog dialog = new JDialog((Frame) null, "Question", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        // Question icon + text
        JPanel messagePanel = new JPanel(new BorderLayout(10, 10));
        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.questionIcon"));

        JTextArea textArea = new JTextArea(questionText);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setOpaque(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(UIManager.getFont("Label.font"));

        // Set preferred width and calculate height accordingly
        int preferredWidth = 300;
        textArea.setSize(new Dimension(preferredWidth, Short.MAX_VALUE));
        int preferredHeight = textArea.getPreferredSize().height;
        textArea.setPreferredSize(new Dimension(preferredWidth, preferredHeight));

        messagePanel.add(iconLabel, BorderLayout.WEST);
        messagePanel.add(textArea, BorderLayout.CENTER);
        messagePanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Buttons
        JButton buttonA = new JButton(buttonATitle);
        JButton buttonB = new JButton(buttonBTitle);

        buttonA.addActionListener(e ->
        {
            result[0] = buttonATitle;
            dialog.dispose();
        });
        buttonB.addActionListener(e ->
        {
            result[0] = buttonBTitle;
            dialog.dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.add(buttonA);
        buttonPanel.add(buttonB);

        // Set default button
        if (defaultButtonTitle != null)
        {
            if (defaultButtonTitle.equals(buttonATitle))
            {
                dialog.getRootPane().setDefaultButton(buttonA);
            }
            else if (defaultButtonTitle.equals(buttonBTitle))
            {
                dialog.getRootPane().setDefaultButton(buttonB);
            }
        }

        // Handle window close to mark as abnormal
        dialog.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                result[0] = null;
                dialog.dispose();
            }

            @Override
            public void windowOpened(WindowEvent e)
            {
                dialog.requestFocus();
            }
        });

        // Compose dialog
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(messagePanel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setAlwaysOnTop(true);
        dialog.setVisible(true);

        // Check result
        if (result[0] == null)
        {
            throw new Exception("Dialog closed without button press");
        }

        return result[0];
    }
}
