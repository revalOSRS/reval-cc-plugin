package com.revalclan.ui;

import com.revalclan.ui.constants.UIConstants;
import lombok.Getter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RevalTabGroup extends JPanel {
    private final Map<String, TabButton> tabs = new LinkedHashMap<>();
    private final Consumer<String> onTabSelected;
    
    @Getter
    private String selectedTab = null;
    
    public RevalTabGroup(Consumer<String> onTabSelected) {
        this.onTabSelected = onTabSelected;
        
        setLayout(new GridLayout(1, 4, 3, 0));
        setBackground(UIConstants.BACKGROUND);
        setBorder(new EmptyBorder(4, 6, 6, 6));
        
        addTab("PROFILE", "Profile");
        addTab("EVENTS", "Events");
        addTab("DIARY", "Diaries");
        addTab("TASKS", "Tasks");
    }
    
    /**
     * Add a new tab dynamically (e.g., Admin tab for authorized users)
     */
    public void addDynamicTab(String id, String label) {
        if (tabs.containsKey(id)) {
            return;
        }
        
        TabButton tab = new TabButton(id, label);
        tabs.put(id, tab);
        add(tab);
        
        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectTab(id);
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!id.equals(selectedTab)) {
                    tab.setHovered(true);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                tab.setHovered(false);
            }
        });
        
        // Update layout for new tab count
        setLayout(new GridLayout(1, tabs.size(), 3, 0));
        revalidate();
        repaint();
    }
    
    /**
     * Remove a dynamic tab
     */
    public void removeDynamicTab(String id) {
        if (!tabs.containsKey(id)) {
            return;
        }
        
        TabButton tab = tabs.remove(id);
        remove(tab);
        
        // Update layout
        setLayout(new GridLayout(1, tabs.size(), 3, 0));
        revalidate();
        repaint();
    }
    
    /**
     * Check if a tab exists
     */
    public boolean hasTab(String id) {
        return tabs.containsKey(id);
    }
    
    /**
     * Deselect the currently selected tab
     */
    public void deselectCurrentTab() {
        if (selectedTab != null && tabs.containsKey(selectedTab)) {
            tabs.get(selectedTab).setSelected(false);
            selectedTab = null;
        }
    }
    
    private void addTab(String id, String label) {
        TabButton tab = new TabButton(id, label);
        tabs.put(id, tab);
        add(tab);
        
        tab.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    selectTab(id);
                }
            }
            
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!id.equals(selectedTab)) {
                    tab.setHovered(true);
                }
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                tab.setHovered(false);
            }
        });
    }
    
    public void selectTab(String tabId) {
        if (tabId.equals(selectedTab)) {
            return;
        }
        
        if (selectedTab != null && tabs.containsKey(selectedTab)) {
            tabs.get(selectedTab).setSelected(false);
        }
        
        selectedTab = tabId;
        
        if (tabs.containsKey(tabId)) {
            tabs.get(tabId).setSelected(true);
        }
        
        if (onTabSelected != null) {
            onTabSelected.accept(tabId);
        }
    }
    
    /**
     * Show or hide a notification badge on a specific tab
     * @param tabId The tab ID (e.g., "EVENTS")
     * @param show Whether to show or hide the badge
     */
    public void setBadge(String tabId, boolean show) {
        if (tabs.containsKey(tabId)) {
            tabs.get(tabId).setBadge(show);
        }
    }
    
    /**
     * Check if a tab has a badge showing
     */
    public boolean hasBadge(String tabId) {
        if (tabs.containsKey(tabId)) {
            return tabs.get(tabId).hasBadge();
        }
        return false;
    }
    
    /**
     * Custom styled tab button with rounded corners, hover effects, and notification badge
     */
    private static class TabButton extends JPanel {
        private final String label;
        private boolean selected = false;
        private boolean hovered = false;
        private boolean showBadge = false;
        
        public TabButton(String id, String label) {
            this.label = label;
            
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(0, 28));
        }
        
        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }
        
        public void setHovered(boolean hovered) {
            this.hovered = hovered;
            repaint();
        }
        
        public void setBadge(boolean show) {
            this.showBadge = show;
            repaint();
        }
        
        public boolean hasBadge() {
            return showBadge;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            
            int width = getWidth();
            int height = getHeight();
            int arc = 8;
            
            Color bgColor;
            if (selected) {
                bgColor = UIConstants.ACCENT_GOLD;
            } else if (hovered) {
                bgColor = UIConstants.TAB_HOVER;
            } else {
                bgColor = UIConstants.TAB_ACTIVE;
            }
            
            g2d.setColor(bgColor);
            g2d.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
            
            // Subtle inner glow for active tab
            if (selected) {
                g2d.setColor(new Color(255, 255, 255, 30));
                g2d.fill(new RoundRectangle2D.Float(1, 1, width - 2, height / 2, arc - 1, arc - 1));
            }
            
            // Text
            Color textColor = selected ? UIConstants.BACKGROUND : (hovered ? UIConstants.TEXT_PRIMARY : UIConstants.TEXT_SECONDARY);
            g2d.setColor(textColor);
            g2d.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 11));
            
            FontMetrics fm = g2d.getFontMetrics();
            int textWidth = fm.stringWidth(label);
            int textX = (width - textWidth) / 2;
            int textY = (height + fm.getAscent() - fm.getDescent()) / 2;
            
            g2d.drawString(label, textX, textY);
            
            g2d.dispose();
        }
    }
}

