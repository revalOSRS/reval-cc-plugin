package com.revalclan.ui;

import com.revalclan.ui.components.Labels;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.NumberFmt;
import lombok.Value;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** The same compact standings table for weekly competitions and race KC. */
final class CompetitionStandings extends JPanel {
    private final int rankWidth, pointsWidth, resultWidth;

    @Value
    static class Entry {
        int rank;
        String name;
        Long value;
        Integer points;
    }

    CompetitionStandings(String metric, List<Entry> entries, String emptyText, String currentPlayer) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(LEFT_ALIGNMENT);
        setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        FontMetrics metrics = getFontMetrics(FontManager.getRunescapeSmallFont());
        int rank = metrics.stringWidth("#"), value = metrics.stringWidth(metric), points = metrics.stringWidth("Pts");
        for (Entry entry : entries) {
            rank = Math.max(rank, metrics.stringWidth(String.valueOf(entry.getRank())));
            value = Math.max(value, metrics.stringWidth(entry.getValue() == null ? "-" : NumberFmt.compact(entry.getValue())));
            points = Math.max(points, metrics.stringWidth(entry.getPoints() != null && entry.getPoints() > 0 ? "+" + entry.getPoints() : "-"));
        }
        rankWidth = Math.max(14, rank + 4);
        pointsWidth = points + 4;
        resultWidth = value + pointsWidth + 10;
        add(row("#", "Player", metric, "Pts", null, false, true, false));
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean current = currentPlayer != null && currentPlayer.equalsIgnoreCase(entry.getName());
            String exact = entry.getValue() == null ? "-" : NumberFmt.group(entry.getValue());
            add(row(String.valueOf(entry.getRank()), entry.getName(),
                entry.getValue() == null ? "-" : NumberFmt.compact(entry.getValue()),
                entry.getPoints() != null && entry.getPoints() > 0 ? "+" + entry.getPoints() : "-",
                exact, current, false, i % 2 == 0));
        }
        if (entries.isEmpty()) {
            JLabel empty = Labels.small(emptyText, UIConstants.TEXT_SECONDARY);
            empty.setBorder(new EmptyBorder(12, 6, 12, 6));
            add(empty);
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(1, super.getPreferredSize().height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height); }

    private JPanel row(String rank, String name, String value, String points, String exact,
                       boolean current, boolean header, boolean stripe) {
        JPanel row = new JPanel(new BorderLayout(3, 0));
        row.setName(header ? "competition-standings-header" : "competition-standings-row");
        row.setOpaque(header || stripe);
        row.setBackground(header ? UIConstants.CARD_HOVER : UIConstants.ROW_BG);
        row.setBorder(current
            ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIConstants.ACCENT_BLUE), new EmptyBorder(4, 4, 4, 4))
            : new EmptyBorder(5, 5, 5, 5));
        row.setAlignmentX(LEFT_ALIGNMENT);
        Font font = header ? FontManager.getRunescapeSmallFont().deriveFont(12f) : FontManager.getRunescapeSmallFont();
        Color text = header ? UIConstants.TEXT_SECONDARY : UIConstants.TEXT_PRIMARY;
        Color rankColor = header ? text : "1".equals(rank) ? new Color(255, 215, 0)
            : "2".equals(rank) ? new Color(198, 198, 198) : "3".equals(rank) ? new Color(205, 127, 80) : text;
        JLabel place = Labels.label(rank, font, rankColor);
        place.setPreferredSize(new Dimension(rankWidth, 17));
        row.add(place, BorderLayout.WEST);
        JLabel player = Labels.label(name, font, current ? UIConstants.ACCENT_BLUE : text);
        player.setToolTipText(name);
        row.add(player, BorderLayout.CENTER);
        JPanel result = new JPanel(new BorderLayout(6, 0));
        result.setOpaque(false);
        result.setPreferredSize(new Dimension(resultWidth, 17));
        JLabel amount = Labels.label(value, font, text);
        amount.setHorizontalAlignment(SwingConstants.RIGHT);
        amount.setToolTipText(exact);
        JLabel reward = Labels.label(points, font, header ? text : UIConstants.ACCENT_GOLD);
        reward.setHorizontalAlignment(SwingConstants.RIGHT);
        reward.setPreferredSize(new Dimension(pointsWidth, 17));
        reward.setToolTipText(header ? "Projected placement points" : "-".equals(points) ? null : points + " placement points");
        result.add(amount, BorderLayout.CENTER);
        result.add(reward, BorderLayout.EAST);
        row.add(result, BorderLayout.EAST);
        return row;
    }
}
