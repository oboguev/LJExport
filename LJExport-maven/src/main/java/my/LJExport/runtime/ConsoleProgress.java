package my.LJExport.runtime;

import javax.swing.*;
import java.awt.*;

public class ConsoleProgress
{
    private final String headerText;

    private JDialog dialog;
    private JLabel headerLabel;
    private JLabel messageLabel;
    private JProgressBar progressBar;

    public ConsoleProgress(String headerText)
    {
        this.headerText = headerText;
    }

    public void begin()
    {
        SwingUtilities.invokeLater(() ->
        {
            JFrame frame = new JFrame();
            frame.setUndecorated(true);
            frame.setType(Window.Type.UTILITY);
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            dialog = new JDialog(frame, "Progress", false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.setSize(450, 120);
            dialog.setLayout(new BorderLayout());

            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            headerLabel = new JLabel(headerText, SwingConstants.CENTER);
            headerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(headerLabel);

            panel.add(Box.createVerticalStrut(10));

            messageLabel = new JLabel("Starting...", SwingConstants.CENTER);
            messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            panel.add(messageLabel);

            panel.add(Box.createVerticalStrut(10));

            progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            panel.add(progressBar);

            dialog.add(panel, BorderLayout.CENTER);

            dialog.setLocationRelativeTo(null);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            dialog.toFront();
            dialog.requestFocus();

            SwingUtilities.invokeLater(() ->
            {
                if (dialog != null)
                {
                    dialog.setAlwaysOnTop(false);
                }
            });
        });
    }

    public void update(String msg, double pct)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (messageLabel != null && progressBar != null)
            {
                messageLabel.setText(msg);
                int value = (int) Math.max(0, Math.min(100, pct));
                progressBar.setValue(value);
            }
        });
    }

    public void end()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (dialog != null)
            {
                dialog.setVisible(false);
                dialog.dispose();
                dialog = null;
            }
        });
    }
}
