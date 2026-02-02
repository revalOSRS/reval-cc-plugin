package com.revalclan.ui.components;

import com.revalclan.api.events.EventsResponse;
import com.revalclan.ui.constants.UIConstants;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.BiConsumer;

public class EventCard extends JPanel {
    
    private final EventsResponse.EventSummary event;
    private final EventType eventType;
    private final String currentPlayerName;
    private final String currentRegistrationStatus;
    private boolean isHovered = false;
    
    public enum EventType {
        PAST, ACTIVE, UPCOMING
    }
    
    public EventCard(EventsResponse.EventSummary event, EventType type, String currentPlayerName, 
                     BiConsumer<String, Boolean> onRegisterAction) {
        this.event = event;
        this.eventType = type;
        this.currentPlayerName = currentPlayerName;
        this.currentRegistrationStatus = findCurrentRegistrationStatus();
        
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(0, 0, 12, 0));
        
        buildCard(onRegisterAction);
        
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                isHovered = true;
                repaint();
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                repaint();
            }
        });
    }
    
    private String findCurrentRegistrationStatus() {
        if (currentPlayerName == null || event.getRegistrations() == null) return null;
        
        return event.getRegistrations().stream()
            .filter(r -> currentPlayerName.equalsIgnoreCase(r.getOsrsNickname()))
            .findFirst()
            .map(EventsResponse.EventRegistration::getStatus)
            .orElse(null);
    }
    
    private void buildCard(BiConsumer<String, Boolean> onRegisterAction) {
        JPanel cardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g2d.setColor(isHovered ? UIConstants.CARD_HOVER : UIConstants.CARD_BG);
                g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                
                g2d.setColor(UIConstants.BORDER_COLOR);
                g2d.setStroke(new BasicStroke(1));
                g2d.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
                
                Color accentColor = getStatusColor();
                g2d.setColor(accentColor);
                g2d.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
                
                g2d.dispose();
            }
        };
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));
        cardPanel.setOpaque(false);
        cardPanel.setBorder(new EmptyBorder(12, 16, 12, 12));
        
        JPanel headerPanel = buildHeader();
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardPanel.add(headerPanel);
        
        JPanel contentPanel = buildContent();
        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cardPanel.add(contentPanel);
        
        if (eventType == EventType.UPCOMING) {
            JPanel footerPanel = buildFooter(onRegisterAction);
            footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
            cardPanel.add(footerPanel);
        }
        
        add(cardPanel, BorderLayout.CENTER);
    }
    
    private Color getStatusColor() {
        switch (eventType) {
            case ACTIVE: return UIConstants.ACCENT_GREEN;
            case UPCOMING: return UIConstants.ACCENT_BLUE;
            case PAST: return UIConstants.TEXT_MUTED;
            default: return UIConstants.TEXT_MUTED;
        }
    }
    
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        
        JLabel typeBadge = createBadge(event.getEventTypeDisplay(), getStatusColor());
        typeBadge.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel titleLabel = new JLabel(event.getName());
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(UIConstants.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        leftPanel.add(typeBadge);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        leftPanel.add(titleLabel);
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        
        String statusText = getStatusText();
        JLabel statusLabel = createBadge(statusText, getStatusColor());
        rightPanel.add(statusLabel);
        
        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        
        return header;
    }
    
    private String getStatusText() {
        switch (eventType) {
            case ACTIVE: return "LIVE";
            case UPCOMING: return event.getTimeUntilStart();
            case PAST: return "COMPLETED";
            default: return "";
        }
    }
    
    private JPanel buildContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(8, 0, 0, 0));
        
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            JLabel descLabel = new JLabel("<html><body style='width: 180px'>" + 
                event.getDescription() + "</body></html>");
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descLabel.setForeground(UIConstants.TEXT_SECONDARY);
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(descLabel);
            content.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        
        JPanel statsRow = new JPanel();
        statsRow.setLayout(new BoxLayout(statsRow, BoxLayout.X_AXIS));
        statsRow.setOpaque(false);
        statsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel durationLabel = new JLabel("Duration: " + event.getDuration());
        durationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        durationLabel.setForeground(UIConstants.TEXT_SECONDARY);
        
        int participantCount = event.getActiveRegistrationCount();
        JLabel participantsLabel = new JLabel("  |  " + participantCount + " participant" + (participantCount != 1 ? "s" : ""));
        participantsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        participantsLabel.setForeground(UIConstants.TEXT_SECONDARY);
        
        statsRow.add(durationLabel);
        statsRow.add(participantsLabel);
        
        content.add(statsRow);
        
        JPanel dateRow = new JPanel();
        dateRow.setLayout(new BoxLayout(dateRow, BoxLayout.X_AXIS));
        dateRow.setOpaque(false);
        dateRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateRow.setBorder(new EmptyBorder(4, 0, 0, 0));
        
        JLabel dateLabel = new JLabel(event.getFormattedStartDate() + " - " + event.getFormattedEndDate());
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        dateLabel.setForeground(UIConstants.TEXT_MUTED);
        dateRow.add(dateLabel);
        
        content.add(dateRow);
        
        return content;
    }
    
    private JPanel buildFooter(BiConsumer<String, Boolean> onRegisterAction) {
        JPanel footerWrapper = new JPanel();
        footerWrapper.setLayout(new BoxLayout(footerWrapper, BoxLayout.Y_AXIS));
        footerWrapper.setOpaque(false);
        footerWrapper.setBorder(new EmptyBorder(12, 0, 0, 0));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        
        if (currentRegistrationStatus == null) {
            JButton registerBtn = createActionButton("Register", UIConstants.ACCENT_BLUE, UIConstants.TEXT_PRIMARY, true);
            registerBtn.addActionListener(e -> {
                if (onRegisterAction != null) {
                    onRegisterAction.accept(event.getId(), true);
                }
            });
            buttonPanel.add(registerBtn);
        } else if (currentRegistrationStatus.equals("pending")) {
            JButton pendingBtn = createPendingButton("Pending", onRegisterAction);
            buttonPanel.add(pendingBtn);
        } else if (currentRegistrationStatus.equals("registered")) {
            JButton registeredBtn = createActionButton("Registered", UIConstants.ACCENT_GREEN, UIConstants.TEXT_PRIMARY, false);
            registeredBtn.setToolTipText("You are registered for this event!");
            buttonPanel.add(registeredBtn);
        } else {
            JButton statusBtn = createActionButton(capitalize(currentRegistrationStatus), UIConstants.CARD_HOVER, UIConstants.TEXT_SECONDARY, false);
            buttonPanel.add(statusBtn);
        }
        
        footerWrapper.add(buttonPanel);
        
        return footerWrapper;
    }
    
    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
    
    private JLabel createBadge(String text, Color color) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(new Font("Segoe UI", Font.BOLD, 9));
        badge.setForeground(color);
        badge.setBorder(new EmptyBorder(3, 8, 3, 8));
        badge.setOpaque(false);
        return badge;
    }
    
    private JButton createActionButton(String text, Color bgColor, Color textColor, boolean enabled) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color bg;
                if (!isEnabled()) {
                    bg = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 150);
                } else {
                    bg = getModel().isPressed() ? bgColor.darker() : 
                         getModel().isRollover() ? bgColor.brighter() : bgColor;
                }
                g2d.setColor(bg);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                
                g2d.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setForeground(enabled ? textColor : new Color(textColor.getRed(), textColor.getGreen(), textColor.getBlue(), 180));
        btn.setBackground(bgColor);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setEnabled(enabled);
        if (enabled) {
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        btn.setPreferredSize(new Dimension(100, 28));
        
        return btn;
    }
    
    private JButton createPendingButton(String text, BiConsumer<String, Boolean> onRegisterAction) {
        JButton btn = new JButton(text) {
            private boolean isHovering = false;
            
            {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        isHovering = true;
                        repaint();
                    }
                    
                    @Override
                    public void mouseExited(MouseEvent e) {
                        isHovering = false;
                        repaint();
                    }
                });
            }
            
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g.create();
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                Color bg = isHovering ? UIConstants.CARD_HOVER.brighter() : UIConstants.CARD_HOVER;
                if (getModel().isPressed()) {
                    bg = UIConstants.CARD_HOVER.darker();
                }
                g2d.setColor(bg);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                
                g2d.dispose();
                super.paintComponent(g);
            }
            
            @Override
            public Color getForeground() {
                if (isHovering) {
                    return new Color(200, 150, 0);
                }
                return UIConstants.ACCENT_GOLD;
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setForeground(UIConstants.ACCENT_GOLD);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(100, 28));
        btn.setToolTipText("Click to cancel registration. Awaiting admin approval.");
        
        btn.addActionListener(e -> {
            if (onRegisterAction != null) {
                onRegisterAction.accept(event.getId(), false);
            }
        });
        
        return btn;
    }
}
