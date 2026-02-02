package com.revalclan.ui.components;

import com.revalclan.api.points.PointsResponse;
import com.revalclan.ui.constants.UIConstants;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * A row displaying a single point source
 */
public class PointSourceRow extends JPanel {
    private final JLabel iconLabel;

    public PointSourceRow(PointsResponse.PointSource source) {
        this(source.getName(), source.getDescription(), source.getPointsDisplay(), source.getIcon());
    }

    public PointSourceRow(String name, String description, String points) {
        this(name, description, points, null);
    }

    public PointSourceRow(String name, String description, String points, String iconIdentifier) {
        setLayout(new BorderLayout(4, 0));
        setBackground(UIConstants.ROW_BG);
        setBorder(new EmptyBorder(5, 8, 5, 8));
        
        setToolTipText(name);

        iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(20, 20));
        iconLabel.setMinimumSize(new Dimension(20, 20));
        iconLabel.setMaximumSize(new Dimension(20, 20));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        boolean hasIcon = iconIdentifier != null && !iconIdentifier.isEmpty();
        if (hasIcon) {
            loadIconFromIdentifier(iconIdentifier);
        } else {
            iconLabel.setPreferredSize(new Dimension(0, 0));
            iconLabel.setMinimumSize(new Dimension(0, 0));
            iconLabel.setMaximumSize(new Dimension(0, 0));
            iconLabel.setVisible(false);
        }

        JPanel leftPanel = new JPanel(new BorderLayout(hasIcon ? 6 : 0, 0));
        leftPanel.setOpaque(false);
        leftPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        nameLabel.setForeground(UIConstants.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        if (description != null && !description.isEmpty()) {
            JTextArea descArea = new JTextArea(description);
            descArea.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            descArea.setForeground(UIConstants.TEXT_SECONDARY);
            descArea.setBackground(UIConstants.ROW_BG);
            descArea.setEditable(false);
            descArea.setFocusable(false);
            descArea.setWrapStyleWord(true);
            descArea.setLineWrap(true);
            descArea.setOpaque(false);
            descArea.setBorder(null);
            descArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            
            int wrapWidth = 300;
            descArea.setSize(wrapWidth, Integer.MAX_VALUE);
            Dimension preferredSize = descArea.getPreferredSize();
            
            descArea.setPreferredSize(new Dimension(0, preferredSize.height));
            descArea.setMinimumSize(new Dimension(0, preferredSize.height));
            descArea.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            
            textPanel.add(descArea);
        }

        if (hasIcon) {
            leftPanel.add(iconLabel, BorderLayout.WEST);
        }
        leftPanel.add(textPanel, BorderLayout.CENTER);

        JLabel pointsLabel = new JLabel(points);
        pointsLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        pointsLabel.setForeground(UIConstants.POINTS_COLOR);
        pointsLabel.setVerticalAlignment(SwingConstants.TOP);

        add(leftPanel, BorderLayout.CENTER);
        add(pointsLabel, BorderLayout.EAST);
        
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    /**
     * Load icon from OSRS Wiki using icon identifier
     */
    private void loadIconFromIdentifier(String iconIdentifier) {
        if (iconIdentifier == null || iconIdentifier.isEmpty()) return;

        // OSRS Wiki item icon URL pattern
        String wikiUrl = "https://oldschool.runescape.wiki/images/" + iconIdentifier + ".png";
        
        CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(wikiUrl);
                BufferedImage image = ImageIO.read(uri.toURL());
                
                if (image != null) {
                    BufferedImage scaled = resizeImagePreservingAspectRatio(image, 20, 20);
                    SwingUtilities.invokeLater(() -> {
                        iconLabel.setIcon(new ImageIcon(scaled));
                    });
                }
            } catch (Exception e) {}
        });
    }

    /**
     * Resize image preserving aspect ratio, centering it in the target size
     */
    private BufferedImage resizeImagePreservingAspectRatio(BufferedImage original, int targetWidth, int targetHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        // Calculate scaling factor to fit within target size while preserving aspect ratio
        double scale = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Create scaled image
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Center the scaled image
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;
        
        g2d.drawImage(original, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return scaled;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(getBackground());
        g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
        g2d.dispose();
    }
}
