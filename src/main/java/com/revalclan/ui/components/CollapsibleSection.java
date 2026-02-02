package com.revalclan.ui.components;

import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;

/**
 * A collapsible/expandable section with a header and content area
 */
public class CollapsibleSection extends JPanel {
    
    
    private final JPanel contentPanel;
    private final JLabel arrowLabel;
    private final JPanel headerPanel;
    private final JLabel iconLabel;
    private boolean expanded = false;
    
    public CollapsibleSection(String title, String subtitle, JPanel content) {
        this(title, subtitle, content, false, null);
    }
    
    public CollapsibleSection(String title, String subtitle, JPanel content, boolean startExpanded) {
        this(title, subtitle, content, startExpanded, null);
    }
    
    public CollapsibleSection(String title, String subtitle, JPanel content, boolean startExpanded, Icon icon) {
        setLayout(new BorderLayout());
        setBackground(UIConstants.BACKGROUND);
        setBorder(new EmptyBorder(0, 0, 3, 0));
        
        this.expanded = startExpanded;
        this.contentPanel = content;
        
        headerPanel = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2d.dispose();
            }
        };
        headerPanel.setBackground(UIConstants.HEADER_BG);
        headerPanel.setBorder(new EmptyBorder(6, 8, 6, 8));
        headerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerPanel.setOpaque(false);
        
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.setOpaque(false);
        
        arrowLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(UIConstants.ACCENT_GOLD);
                
                int size = 8;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                Path2D arrow = new Path2D.Float();
                if (expanded) {
                    // Down arrow
                    arrow.moveTo(x, y + 2);
                    arrow.lineTo(x + size / 2.0, y + size - 2);
                    arrow.lineTo(x + size, y + 2);
                } else {
                    // Right arrow
                    arrow.moveTo(x + 2, y);
                    arrow.lineTo(x + size - 2, y + size / 2.0);
                    arrow.lineTo(x + 2, y + size);
                }
                
                g2d.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2d.draw(arrow);
                g2d.dispose();
            }
        };
        arrowLabel.setPreferredSize(new Dimension(14, 14));
        leftPanel.add(arrowLabel);
        
        iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(24, 24));
        if (icon != null) {
            iconLabel.setIcon(icon);
        }
        leftPanel.add(iconLabel);
        
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLabel.setForeground(UIConstants.TEXT_PRIMARY);
        
        if (subtitle != null && !subtitle.isEmpty()) {
            // With subtitle: stack vertically
            titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            titlePanel.add(titleLabel);
            
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            subtitleLabel.setForeground(UIConstants.TEXT_SECONDARY);
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            titlePanel.add(subtitleLabel);
        } else {
            titlePanel.setLayout(new BorderLayout());
            titlePanel.add(titleLabel, BorderLayout.CENTER);
        }
        
        headerPanel.add(leftPanel, BorderLayout.WEST);
        headerPanel.add(titlePanel, BorderLayout.CENTER);
        
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(UIConstants.BACKGROUND);
        contentWrapper.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentWrapper.add(contentPanel, BorderLayout.CENTER);
        contentWrapper.setVisible(expanded);
        
        headerPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    expanded = !expanded;
                    contentWrapper.setVisible(expanded);
                    arrowLabel.repaint();
                    revalidate();
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                headerPanel.setBackground(UIConstants.HEADER_HOVER);
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                headerPanel.setBackground(UIConstants.HEADER_BG);
            }
        });
        
        add(headerPanel, BorderLayout.NORTH);
        add(contentWrapper, BorderLayout.CENTER);
    }
    
    /**
     * Set the icon (can be called later when async image loads)
     */
    public void setIcon(Icon icon) {
        iconLabel.setIcon(icon);
    }
    
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        ((JPanel) ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER)).setVisible(expanded);
        arrowLabel.repaint();
        revalidate();
    }
    
    public boolean isExpanded() {
        return expanded;
    }
}
