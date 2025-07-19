package my.LJExport.runtime.ui;

import javax.swing.*;
import java.awt.*;

public class ProgressDialog
{
    private final JDialog dialog;
    private final JLabel topLabel;
    private final JLabel midLabel;
    private final JProgressBar progressBar;
    private final JLabel etaLabel;

    private String last_msg;
    private Double last_pct;
    private long startTime = -1;

    public ProgressDialog(String title)
    {
        dialog = new JDialog((Frame) null, "Progress", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.insets = new Insets(5, 10, 5, 10);
        gbc.weightx = 1.0;

        // Centered top label
        topLabel = new JLabel(title, SwingConstants.CENTER);
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(topLabel, gbc);

        // Centered mid label
        midLabel = new JLabel(" ", SwingConstants.CENTER);
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(midLabel, gbc);

        // Wide progress bar
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 20)); // enforce width
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(progressBar, gbc);

        // Left-aligned ETA label
        etaLabel = new JLabel("ETA: <estimating>");
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.WEST;
        panel.add(etaLabel, gbc);

        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
    }

    public void begin()
    {
        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }

    public void end()
    {
        SwingUtilities.invokeLater(dialog::dispose);
    }

    public void update(String top, String msg, double pct)
    {
        SwingUtilities.invokeLater(() ->
        {
            if (top != null)
                topLabel.setText(top);
            midLabel.setText(msg);

            if (last_msg != null && last_msg.equals(msg) && last_pct != null && Math.abs(pct - last_pct) < 0.1)
                return;

            if (last_pct == null || pct == 0 || pct < last_pct)
                startTime = System.currentTimeMillis();

            last_msg = msg;
            last_pct = pct;

            int percent = (int) Math.round(pct);
            progressBar.setValue(percent);

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
        });
    }

    public void update(String msg, double pct)
    {
        update(null, msg, pct);
    }

    private static String formatDuration(long millis)
    {
        long totalSeconds = millis / 1000;
        long seconds = totalSeconds % 60;
        long minutes = (totalSeconds / 60) % 60;
        long hours = (totalSeconds / 3600) % 24;
        long days = totalSeconds / (3600 * 24);

        if (days > 0)
            return String.format("%d days %02d:%02d:%02d", days, hours, minutes, seconds);
        else if (hours > 0)
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        else
            return String.format("%d:%02d", minutes, seconds);
    }
}
