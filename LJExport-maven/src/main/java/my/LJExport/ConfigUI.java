package my.LJExport;

// http://docs.oracle.com/javase/tutorial/uiswing
// http://help.eclipse.org/kepler/index.jsp?topic=%2Forg.eclipse.wb.doc.user%2Fhtml%2Findex.html
// https://web.archive.org/web/20130820022641/http://www.codemaps.org/s/WindowBuilder

import javax.swing.*;
import javax.swing.event.*;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class ConfigUI
{
    public String xxx;

    static String promptPassword(String prompt) throws Exception
    {
        final JPasswordField jpf = new JPasswordField();
        JOptionPane jop = new JOptionPane(jpf,
                JOptionPane.QUESTION_MESSAGE,
                JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = jop.createDialog(prompt);
        dialog.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentShown(ComponentEvent e)
            {
                SwingUtilities.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        jpf.requestFocusInWindow();
                    }
                });
            }
        });
        dialog.setVisible(true);
        int result = (Integer) jop.getValue();
        dialog.dispose();
        char[] password = null;
        if (result == JOptionPane.OK_OPTION)
        {
            password = jpf.getPassword();
            return new String(password);
        }
        else
        {
            return null;
        }
    }

    /**
     * @wbp.parser.entryPoint
     */
    void doit() throws Exception
    {
        JFrame theGUI = new JFrame();
    }
}
