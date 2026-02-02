package com.revalclan.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Icon factory for Reval panel.
 * Creates programmatic icons for navigation and admin UI elements.
 */
public class RevalIcons {
    
    /**
     * Creates the panel navigation icon (for the sidebar button)
     * Returns BufferedImage as required by RuneLite's NavigationButton
     */
    public static BufferedImage createPanelIcon() {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Gold crown/shield design representing clan
        Color gold = new Color(218, 165, 32);
        Color darkGold = new Color(184, 134, 11);
        
        // Shield shape
        Path2D shield = new Path2D.Float();
        shield.moveTo(8, 1);
        shield.lineTo(14, 3);
        shield.lineTo(14, 9);
        shield.curveTo(14, 12, 11, 14, 8, 15);
        shield.curveTo(5, 14, 2, 12, 2, 9);
        shield.lineTo(2, 3);
        shield.closePath();
        
        // Gradient fill
        GradientPaint gradient = new GradientPaint(2, 1, gold, 14, 15, darkGold);
        g2d.setPaint(gradient);
        g2d.fill(shield);
        
        // Border
        g2d.setColor(darkGold);
        g2d.setStroke(new BasicStroke(0.8f));
        g2d.draw(shield);
        
        // Letter R for Reval
        g2d.setColor(new Color(30, 30, 30));
        g2d.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics fm = g2d.getFontMetrics();
        String letter = "R";
        int textX = (size - fm.stringWidth(letter)) / 2;
        int textY = (size + fm.getAscent() - fm.getDescent()) / 2;
        g2d.drawString(letter, textX, textY);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * Creates a lock/admin icon
     */
    public static ImageIcon createLockIcon(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        
        float scale = size / 24f;
        float strokeWidth = 2.0f;
        
        g2d.scale(scale, scale);
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // Lock body (rounded rectangle)
        int x = 6;
        int y = 10;
        int width = 12;
        int height = 10;
        int arc = 2;
        g2d.draw(new RoundRectangle2D.Float(x, y, width, height, arc, arc));
        
        // Lock shackle (U-shape on top)
        Path2D shackle = new Path2D.Float();
        shackle.moveTo(8, 10);
        shackle.curveTo(8, 6, 10, 4, 12, 4);
        shackle.curveTo(14, 4, 16, 6, 16, 10);
        g2d.draw(shackle);
        
        // Keyhole (small circle)
        g2d.fill(new Ellipse2D.Float(10.5f, 13.5f, 3, 3));
        
        g2d.dispose();
        return new ImageIcon(image);
    }
    
}

