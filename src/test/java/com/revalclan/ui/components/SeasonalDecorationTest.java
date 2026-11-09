package com.revalclan.ui.components;

import com.revalclan.ui.constants.Season;
import com.revalclan.ui.constants.UIConstants;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class SeasonalDecorationTest {
    // Repeated scrolling used to spend hundreds of milliseconds combining icing curves per frame.
    @Test(timeout = 5000)
    public void christmasRepaintsCompleteAtSidebarWidths() {
        UIConstants.setSeason(Season.CHRISTMAS);
        try {
            for (int scale : new int[]{1, 2}) {
                for (int width : new int[]{72, 120, 237, 240, 480}) {
                    BufferedImage image = new BufferedImage(width * scale, 160 * scale,
                        BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics();
                    try {
                        g.scale(scale, scale);
                        for (int repaint = 0; repaint < 20; repaint++) {
                            SeasonalDecoration.paintHeader(g, width, 140);
                            SeasonalDecoration.paintCard(g, width);
                            SeasonalDecoration.paintTab(g, width, false, "Profile");
                            SeasonalDecoration.paintTab(g, width, true, "Profile");
                        }
                    } finally {
                        g.dispose();
                    }
                    assertNotEquals("Icing must still be painted", 0, image.getRGB(10 * scale, 0));
                    assertEquals("Decoration must leave the content below it transparent",
                        0, image.getRGB(10 * scale, 159 * scale));
                }
            }
        } finally {
            UIConstants.setSeason(Season.NONE);
        }
    }
}
