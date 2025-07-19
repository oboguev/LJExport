package my.LJExport.runtime.ui;

import javax.swing.*;
import java.awt.*;

public class ProgressDialog
{
    private final String headerText;

    private JDialog dialog;
    private JLabel headerLabel;
    private JLabel messageLabel;
    private JProgressBar progressBar;
    private long startTime = -1;
    private JLabel etaLabel;

    public ProgressDialog(String headerText)
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

            etaLabel = new JLabel("ETA: <estimating>");
            // Create a flow panel with left alignment to hold ETA label
            JPanel etaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            etaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            etaPanel.add(etaLabel);

            // Add spacing and etaPanel to the main layout
            panel.add(Box.createRigidArea(new Dimension(0, 5)));
            panel.add(etaPanel);
            dialog.add(panel, BorderLayout.CENTER);
            dialog.pack();

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

    private String last_msg = null;
    private Double last_pct = null;

    public void update(String msg, double pct)
    {
        if (last_msg != null && last_msg.equals(msg) && last_pct != null && Math.abs(pct - last_pct) < 0.1)
            return;

        boolean pctReset = (last_pct == null || pct == 0 || pct < last_pct);
        if (pctReset)
            startTime = System.currentTimeMillis();

        last_msg = msg;
        last_pct = pct;

        SwingUtilities.invokeLater(() ->
        {
            if (messageLabel != null && progressBar != null)
            {
                messageLabel.setText(msg);
                int value = (int) Math.max(0, Math.min(100, pct));
                progressBar.setValue(value);

                if (pct < 1.0)
                {
                    etaLabel.setText("ETA: <estimating>");
                }
                else
                {
                    long now = System.currentTimeMillis();
                    long elapsed = now - startTime;
                    double remainingRatio = (100.0 - pct) / pct;
                    long etaMillis = (long) (elapsed * remainingRatio);
                    etaLabel.setText("ETA: " + formatDuration(etaMillis));
                }
            }
        });
    }

    public void end()
    {
        last_msg = null;
        last_pct = null;

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

    private static String formatDuration(long millis)
    {
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = totalSeconds / (3600 * 24);

        if (days > 0)
            return String.format("%d days and %02d:%02d:%02d", days, hours, minutes, seconds);
        else if (hours > 0)
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        else
            return String.format("%d:%02d", minutes, seconds);
    }
}
