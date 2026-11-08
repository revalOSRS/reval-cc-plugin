package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JToolTip;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicPanelUI;
import net.runelite.client.ui.laf.RuneLiteLAF;
import okhttp3.OkHttpClient;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class ClanBrowserPaintingTest {
    @Test public void overlappingTooltipDoesNotExposeTheClearedBoardDuringRepaint() throws Exception {
        assumeFalse("Needs a display for Swing's paintImmediately path", GraphicsEnvironment.isHeadless());
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            RuneLiteLAF.setup();
            ClanBrowserWindow window = new ClanBrowserWindow(null,
                new BrowserApi(new OkHttpClient(), new Gson()), id -> null);
            try {
                // Record the pixels presented by Swing, rather than sampling the desktop.
                BufferedImage presented = new BufferedImage(1180, 820, BufferedImage.TYPE_INT_RGB);
                JLayeredPane layers = new JLayeredPane() {
                    @Override public Graphics getGraphics() { return presented.createGraphics(); }
                };
                layers.setDoubleBuffered(window.getLayeredPane().isDoubleBuffered());
                window.setLayeredPane(layers);
                layers.add(window.getContentPane(), JLayeredPane.FRAME_CONTENT_LAYER);
                BrowserModels.View view = new BrowserModels.View(); view.kind = "dryboard";
                BrowserModels.Row row = new BrowserModels.Row();
                row.player = "Player"; row.rank = 1; row.value = 100L;
                row.item = "Zorya's tome (uncharged)"; row.last = 20L; row.current = 120L;
                view.rows.add(row);
                LeaderboardView board = new LeaderboardView(view, null, player -> {});
                window.getContentPane().add(board, BorderLayout.CENTER);
                window.setFocusableWindowState(false);
                window.setLocation(-20000, -20000);
                window.setVisible(true); window.validate();
                JTable table = table(board);
                Point origin = SwingUtilities.convertPoint(table, 0, 0, layers);
                Point sample = new Point(origin.x + 5, origin.y + table.getRowHeight() - 5);

                // On Windows, a lightweight tooltip and its translucent shadow share
                // this layer with the board. A partial overlap repaints their common ancestor.
                JToolTip tip = table.createToolTip(); tip.setTipText(table.getToolTipText(
                    new java.awt.event.MouseEvent(table, java.awt.event.MouseEvent.MOUSE_MOVED,
                        0, 0, 100, 10, 0, false)));
                JPanel popup = new JPanel(new BorderLayout()); popup.setOpaque(false); popup.add(tip);
                popup.setBounds(origin.x + 60, origin.y + 20, 600, 30);
                layers.add(popup, JLayeredPane.POPUP_LAYER); popup.doLayout();
                Graphics initial = presented.createGraphics();
                layers.printAll(initial); initial.dispose();
                assertEquals(BrowserTheme.RECEIVED.getRGB(), presented.getRGB(sample.x, sample.y));

                int[] paints = {0};
                JPanel content = (JPanel) window.getContentPane();
                content.setUI(new BasicPanelUI() {
                    @Override public void update(Graphics graphics, JComponent component) {
                        super.update(graphics, component);
                        paints[0]++;
                        // The normal panel UI has just cleared its background. That
                        // intermediate frame must stay offscreen until the rows are painted.
                        assertEquals("Tooltip repaint exposed the cleared board",
                            BrowserTheme.RECEIVED.getRGB(), presented.getRGB(sample.x, sample.y));
                    }
                });
                for (int i = 0; i < 12; i++) {
                    popup.setLocation(origin.x + 60, origin.y + 20 + i * 2);
                    table.paintImmediately(0, 0, table.getWidth(), table.getHeight());
                    assertEquals(BrowserTheme.RECEIVED.getRGB(), presented.getRGB(sample.x, sample.y));
                }
                assertTrue("Must exercise the overlapping ancestor repaint", paints[0] >= 12);
            } finally {
                window.dispose();
                try { UIManager.setLookAndFeel(previous); }
                catch (javax.swing.UnsupportedLookAndFeelException error) { throw new AssertionError(error); }
            }
        });
    }

    private static JTable table(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JTable) return (JTable) child;
            if (child instanceof Container) {
                JTable table = table((Container) child);
                if (table != null) return table;
            }
        }
        return null;
    }
}
