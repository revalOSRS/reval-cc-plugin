package com.revalclan.races;

import com.revalclan.api.competitions.FeaturedRaceResponse;
import com.revalclan.ui.RaceEventCard;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** Headless regression check: real cards must fit the narrow RuneLite sidebar. */
public final class RaceCardLayoutCheck {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int width : new int[] {219, 300}) {
                for (boolean details : new boolean[] {false, true}) {
                    RaceEventCard card = new RaceEventCard(fixture(), null, details, () -> {});
                    card.setSize(width, card.getPreferredSize().height);
                    layout(card);
                    check(card);
                    if (!hasLabel(card, "Clan party (4)")) throw new AssertionError("Missing compact party claimer");
                    if (hasLabel(card, "each player") || hasLabel(card, "All-Reval party")) throw new AssertionError("Unnecessary completion captions are visible");
                }
            }
        });
        System.out.println("Verified race cards at 219px and 300px: bounded icons, equal row heights, and compact party claimer");
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static void check(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && ((JLabel) child).getIcon() != null) {
                Icon icon = ((JLabel) child).getIcon();
                if (icon.getIconWidth() > 32 || icon.getIconHeight() > 32) throw new AssertionError("Objective artwork exceeds its inventory slot");
            }
            if (child.getName() != null && child.getName().startsWith("race-objective-") && child.getHeight() != 51)
                throw new AssertionError("Race objective rows must have the same 51px height");
            if ("race-objective-completion".equals(child.getName())) {
                Container row = (Container) child;
                BorderLayout border = (BorderLayout) row.getLayout();
                Component slot = border.getLayoutComponent(BorderLayout.WEST);
                Component copy = border.getLayoutComponent(BorderLayout.CENTER);
                Component reward = border.getLayoutComponent(BorderLayout.EAST);
                if (slot.getWidth() > 36 || copy.getWidth() < 60 || reward.getX() < copy.getX() + copy.getWidth())
                    throw new AssertionError("First completion artwork or points obscure the text");
                if (((Container) copy).getComponentCount() != 2) throw new AssertionError("Only the title and claimer should be visible");
                JLabel title = (JLabel) ((Container) copy).getComponent(0);
                if (title.getFontMetrics(title.getFont()).stringWidth(title.getText()) > copy.getWidth()) throw new AssertionError("First completion title is clipped");
            }
            if (child instanceof Container) check((Container) child);
        }
    }

    private static boolean hasLabel(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) return true;
            if (child instanceof Container && hasLabel((Container) child, text)) return true;
        }
        return false;
    }

    private static FeaturedRaceResponse.Race fixture() {
        FeaturedRaceResponse.Race race = new FeaturedRaceResponse.Race();
        race.setName("The Fractured Archive"); race.setState("active"); race.setTimeZone("Europe/Tallinn");
        race.setPlannedStartDate("2026-10-20"); race.setEndDate("2026-11-01T21:59:59.999Z");
        List<FeaturedRaceResponse.Objective> objectives = new ArrayList<>();
        String[] kinds = {"completion", "pet", "unique", "purple"};
        String[] titles = {"First raid completion", "First raid pet", "First unique", "First purple"};
        for (int i = 0; i < kinds.length; i++) {
            FeaturedRaceResponse.Objective objective = new FeaturedRaceResponse.Objective();
            objective.setKind(kinds[i]); objective.setTitle(titles[i]); objective.setPoints(100);
            if (i == 0) {
                objective.setClanPartyOnly(true);
                FeaturedRaceResponse.Winner winner = new FeaturedRaceResponse.Winner();
                winner.setMembers(List.of("Clan One", "Clan Two", "Clan Three", "Clan Four"));
                winner.setName(String.join(", ", winner.getMembers())); objective.setWinner(winner);
            }
            objectives.add(objective);
        }
        race.setObjectives(objectives);
        FeaturedRaceResponse.Kc kc = new FeaturedRaceResponse.Kc();
        kc.setRewards(List.of(300, 225, 150, 100)); kc.setLeaderboard(List.of()); race.setKc(kc);
        return race;
    }
}
