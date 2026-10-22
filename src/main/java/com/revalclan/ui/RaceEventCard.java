package com.revalclan.ui;

import com.revalclan.api.competitions.FeaturedRaceResponse;
import com.revalclan.ui.components.Clickable;
import com.revalclan.ui.components.Labels;
import com.revalclan.ui.constants.UIConstants;
import com.revalclan.util.SpriteIcons;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A native inventory-style race card, backed by the same verified results as the homepage. */
public final class RaceEventCard extends JPanel {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private final FeaturedRaceResponse.Race race;
    private final BufferedImage artwork;
    private final ItemManager itemManager;
    private final BiConsumer<String, Consumer<BufferedImage>> imageLoader;

    public RaceEventCard(FeaturedRaceResponse.Race race, BufferedImage artwork, ItemManager itemManager,
                         BiConsumer<String, Consumer<BufferedImage>> imageLoader, boolean details, Runnable open) {
        this.race = race;
        this.artwork = artwork;
        this.itemManager = itemManager;
        this.imageLoader = imageLoader;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UIConstants.CARD_BG);
        setBorder(BorderFactory.createLineBorder(UIConstants.ACCENT_GOLD));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        add(new RaceHeader());
        JPanel body = column();
        body.setBorder(new EmptyBorder(4, 8, 10, 8));
        add(body);
        body.add(section("Race firsts"));
        List<FeaturedRaceResponse.Objective> objectives = race.getObjectives() == null ? Collections.emptyList() : race.getObjectives();
        for (FeaturedRaceResponse.Objective objective : objectives) {
            body.add(objectiveRow(objective));
            body.add(Box.createVerticalStrut(5));
        }
        body.add(Box.createVerticalStrut(8));
        body.add(section("Top 10 raid KC"));
        body.add(rewards());
        body.add(Box.createVerticalStrut(10));
        body.add(section(details ? "KC leaderboard" : "Top 5 raid KC"));
        JPanel table = standings(details);
        body.add(table);
        if (!details) {
            makeClickable(table, open);
            body.add(Box.createVerticalStrut(10));
            body.add(button("Full KC leaderboard >", open));
        }
    }

    @Override public Dimension getPreferredSize() { return new Dimension(1, super.getPreferredSize().height); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height); }

    private JPanel objectiveRow(FeaturedRaceResponse.Objective objective) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setName("race-objective-" + objective.getKind());
        row.setBackground(UIConstants.ROW_BG);
        row.setBorder(new EmptyBorder(6, 5, 6, 5));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean partyObjective = objective.isClanPartyOnly();
        List<String> members = objective.getWinner() == null ? null : objective.getWinner().getMembers();
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 51));
        row.setPreferredSize(new Dimension(1, 51));
        JLabel slot = new JLabel();
        slot.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        slot.setPreferredSize(new Dimension(34, 39));
        loadObjectiveIcon(objective, slot);
        row.add(slot, BorderLayout.WEST);
        JPanel copy = column();
        copy.add(Labels.small(objective.getTitle(), UIConstants.TEXT_PRIMARY));
        String result = objective.getWinner() == null ? "Unclaimed" : partyObjective && members != null
            ? "Clan party (" + members.size() + ")" : objective.getWinner().getName();
        JLabel winner = Labels.label(result, objective.getWinner() == null ? smallFont(12f) : FontManager.getRunescapeSmallFont(),
            objective.getWinner() == null ? UIConstants.TEXT_SECONDARY : UIConstants.ACCENT_GREEN);
        winner.setToolTipText(objective.getWinner() == null ? result : objective.getWinner().getName());
        copy.add(winner);
        row.add(copy, BorderLayout.CENTER);
        row.add(Labels.bold("+" + objective.getPoints() + " pts", UIConstants.ACCENT_GOLD), BorderLayout.EAST);
        if (partyObjective && members != null && !members.isEmpty()) row.setToolTipText(objective.getWinner().getName());
        return row;
    }

    private void loadObjectiveIcon(FeaturedRaceResponse.Objective objective, JLabel slot) {
        int fallbackId = "completion".equals(objective.getKind()) ? ItemID.CA_BOOK
            : "pet".equals(objective.getKind()) ? ItemID.CHAOSELEPET
            : "purple".equals(objective.getKind()) ? ItemID.BIRTHDAY_CAKE : ItemID.COLLECTION_LOG;
        int itemId = objective.getIconItemId() != null && objective.getIconItemId() > 0 ? objective.getIconItemId() : fallbackId;
        boolean[] customLoaded = {false};
        if (itemManager != null) {
            try {
                AsyncBufferedImage image = itemManager.getImage(itemId);
                image.onLoaded(() -> SwingUtilities.invokeLater(() -> {
                    if (!customLoaded[0]) slot.setIcon(SpriteIcons.fit(image, 32));
                }));
            } catch (RuntimeException ignored) {
                // An unavailable cache item must not prevent the race from rendering.
            }
        }
        if (imageLoader != null && objective.getImageUrl() != null && !objective.getImageUrl().trim().isEmpty()) {
            imageLoader.accept(objective.getImageUrl(), image -> {
                customLoaded[0] = true;
                slot.setIcon(SpriteIcons.fit(image, 32));
            });
        }
    }

    private JPanel rewards() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 5, 5));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setPreferredSize(new Dimension(1, 86));
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        List<Integer> points = race.getKc() == null || race.getKc().getRewards() == null
            ? Collections.emptyList() : race.getKc().getRewards();
        String[] ranks = {"1st place", "2nd place", "3rd place", "4th-10th"};
        for (int i = 0; i < ranks.length; i++) {
            JPanel cell = column();
            cell.setOpaque(true); cell.setBackground(UIConstants.ROW_BG);
            cell.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR), new EmptyBorder(5, 7, 5, 7)));
            cell.add(Labels.label(ranks[i], smallFont(12f), UIConstants.TEXT_SECONDARY));
            cell.add(Labels.bold(i < points.size() ? "+" + points.get(i) + " pts" : "TBC", UIConstants.ACCENT_GOLD));
            grid.add(cell);
        }
        return grid;
    }

    private JPanel standings(boolean details) {
        JPanel table = column();
        table.setBorder(BorderFactory.createLineBorder(UIConstants.BORDER_COLOR));
        table.add(standingRow("#", "Player", "KC", "Pts", true, false));
        List<FeaturedRaceResponse.Standing> rows = race.getKc() == null || race.getKc().getLeaderboard() == null
            ? Collections.emptyList() : race.getKc().getLeaderboard();
        if (rows.isEmpty()) {
            JLabel empty = Labels.label("upcoming".equals(race.getState()) ? "Standings begin at raid release" : "No completions recorded yet",
                smallFont(12f), UIConstants.TEXT_SECONDARY);
            empty.setBorder(new EmptyBorder(12, 6, 12, 6));
            table.add(empty);
        } else {
            int count = details ? rows.size() : Math.min(5, rows.size());
            for (int i = 0; i < count; i++) {
                FeaturedRaceResponse.Standing player = rows.get(i);
                table.add(standingRow(String.valueOf(player.getRank()), player.getName(),
                    String.format(Locale.ENGLISH, "%,d", player.getValue()),
                    player.getPoints() > 0 ? "+" + player.getPoints() : "-", false, i % 2 == 0));
            }
        }
        table.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getPreferredSize().height));
        return table;
    }

    private JPanel standingRow(String rank, String name, String kc, String points, boolean header, boolean stripe) {
        JPanel row = new JPanel(new BorderLayout(3, 0));
        row.setName(header ? "race-kc-header" : "race-kc-row");
        row.setOpaque(header || stripe);
        row.setBackground(header ? UIConstants.CARD_HOVER : UIConstants.ROW_BG);
        row.setBorder(new EmptyBorder(5, 5, 5, 5));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        Font font = header ? smallFont(12f) : FontManager.getRunescapeSmallFont();
        Color text = header ? UIConstants.TEXT_SECONDARY : UIConstants.TEXT_PRIMARY;
        JLabel place = Labels.label(rank, font, text);
        place.setPreferredSize(new Dimension(18, 17));
        row.add(place, BorderLayout.WEST);
        JLabel player = Labels.label(name, font, text);
        if (!header) player.setToolTipText(name);
        row.add(player, BorderLayout.CENTER);
        JPanel result = new JPanel(new GridLayout(1, 2, 4, 0));
        result.setOpaque(false);
        result.setPreferredSize(new Dimension(72, 17));
        JLabel kills = Labels.label(kc, font, text);
        kills.setHorizontalAlignment(SwingConstants.RIGHT);
        JLabel reward = Labels.label(points, font, header ? text : UIConstants.ACCENT_GOLD);
        reward.setHorizontalAlignment(SwingConstants.RIGHT);
        result.add(kills);
        result.add(reward);
        row.add(result, BorderLayout.EAST);
        return row;
    }

    private final class RaceHeader extends JPanel {
        private final Font titleFont = FontManager.getRunescapeBoldFont().deriveFont(21f);

        RaceHeader() {
            setOpaque(true);
            setBackground(UIConstants.ROW_BG);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setToolTipText("Event dates and times are shown in " + race.getTimeZone());
        }

        private List<String> titleLines() {
            int width = getWidth() > 24 ? getWidth() - 24 : 190;
            FontMetrics metrics = getFontMetrics(titleFont);
            List<String> lines = new ArrayList<>();
            String line = "";
            for (String word : race.getName().split("\\s+")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && metrics.stringWidth(candidate) > width) {
                    lines.add(line);
                    line = word;
                } else line = candidate;
            }
            lines.add(line);
            return lines;
        }

        @Override public Dimension getPreferredSize() {
            int height = 54 + titleLines().size() * 23;
            if ("upcoming".equals(race.getState()) && race.getStartDate() == null) height += 17;
            return new Dimension(1, height);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public void setBounds(int x, int y, int width, int height) {
            boolean changed = width != getWidth();
            super.setBounds(x, y, width, height);
            if (changed) SwingUtilities.invokeLater(() -> RaceEventCard.this.revalidate());
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            if (artwork != null) {
                g.setComposite(AlphaComposite.SrcOver.derive(.65f));
                double scale = Math.max((double) getWidth() / artwork.getWidth(), (double) getHeight() / artwork.getHeight());
                int width = (int) Math.ceil(artwork.getWidth() * scale);
                int height = (int) Math.ceil(artwork.getHeight() * scale);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(artwork, (getWidth() - width) / 2, (getHeight() - height) / 2, width, height, null);
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.setFont(smallFont(12f));
            g.setColor(UIConstants.ACCENT_GOLD);
            String phase = "active".equals(race.getState()) ? "RACE IN PROGRESS" : "completed".equals(race.getState()) ? "RACE RESULTS"
                : "paused".equals(race.getState()) ? "RACE PAUSED" : "UPCOMING RACE";
            g.drawString(phase, 12, 17);
            g.setFont(titleFont);
            g.setColor(UIConstants.TEXT_PRIMARY);
            int y = 39;
            for (String line : titleLines()) {
                g.drawString(line, 12, y);
                y += 23;
            }
            g.setFont(smallFont(13f));
            g.setColor(UIConstants.TEXT_SECONDARY);
            ZoneId zone = ZoneId.of(race.getTimeZone());
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ENGLISH);
            String start = race.getStartDate() == null ? DAY.format(LocalDate.parse(race.getPlannedStartDate()))
                : dateTime.format(Instant.parse(race.getStartDate()).atZone(zone));
            String period = start + " - " + dateTime.format(Instant.parse(race.getEndDate()).atZone(zone)) + " (EE)";
            while (g.getFontMetrics().stringWidth(period) > getWidth() - 24 && g.getFont().getSize2D() > 11f)
                g.setFont(g.getFont().deriveFont(g.getFont().getSize2D() - .5f));
            g.drawString(period, 12, y);
            if ("upcoming".equals(race.getState()) && race.getStartDate() == null)
                g.drawString("Starts at raid release - time TBC", 12, y + 17);
            g.dispose();
        }
    }

    private static void makeClickable(JComponent component, Runnable action) {
        Clickable.onPress(component, action);
        for (Component child : component.getComponents())
            if (child instanceof JComponent) makeClickable((JComponent) child, action);
    }

    private static JLabel section(String value) {
        JLabel title = Labels.sectionTitle(value);
        title.setBorder(new EmptyBorder(3, 1, 7, 0));
        return title;
    }
    private static JPanel column() {
        JPanel panel = Labels.column();
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }
    private static Font smallFont(float size) {
        return FontManager.getRunescapeSmallFont().deriveFont(size);
    }
    private static JButton button(String title, Runnable action) {
        JButton button = new JButton(title); button.setFont(FontManager.getRunescapeSmallFont());
        button.setForeground(UIConstants.ACCENT_GOLD); button.setBackground(UIConstants.CARD_HOVER);
        button.setFocusPainted(false); button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        button.addActionListener(event -> action.run());
        return button;
    }
}
