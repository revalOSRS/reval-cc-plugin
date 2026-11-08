package com.revalclan.ui;

import com.revalclan.api.competitions.FeaturedRaceResponse;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RaceEventCardTest {
    @Test public void missingCustomUrlsKeepEveryNativeItemFallback() throws Exception {
        ItemManager items = mock(ItemManager.class);
        List<AsyncBufferedImage> pending = new ArrayList<>();
        when(items.getImage(anyInt())).thenAnswer(invocation -> {
            AsyncBufferedImage image = new AsyncBufferedImage(null, 32, 32, BufferedImage.TYPE_INT_RGB);
            fill(image, Color.GREEN);
            pending.add(image);
            return image;
        });
        FeaturedRaceResponse.Objective explicit = objective("custom", null);
        explicit.setIconItemId(123);
        RaceEventCard[] card = new RaceEventCard[1];
        edt(() -> {
            card[0] = new RaceEventCard(race(objective("completion", null), objective("pet", "  "),
                objective("purple", null), objective("other", ""), explicit), null, items, Map.of(), true, null);
            pending.forEach(AsyncBufferedImage::loaded);
        });
        verify(items).getImage(ItemID.CA_BOOK);
        verify(items).getImage(ItemID.CHAOSELEPET);
        verify(items).getImage(ItemID.BIRTHDAY_CAKE);
        verify(items).getImage(ItemID.COLLECTION_LOG);
        verify(items).getImage(123);
        List<JLabel> slots = iconSlots(card[0]);
        assertEquals(5, slots.size());
        for (JLabel slot : slots) assertEquals(Color.GREEN.getRGB(), iconPixel(slot));
    }

    @Test public void customArtworkReplacesFallbackAndWinsAgainstLateNativeIcons() throws Exception {
        ItemManager items = mock(ItemManager.class);
        List<AsyncBufferedImage> pending = new ArrayList<>();
        when(items.getImage(anyInt())).thenAnswer(invocation -> {
            AsyncBufferedImage image = new AsyncBufferedImage(null, 32, 32, BufferedImage.TYPE_INT_RGB);
            fill(image, Color.GREEN);
            pending.add(image);
            return image;
        });
        RaceEventCard[] card = new RaceEventCard[1];
        edt(() -> card[0] = new RaceEventCard(race(objective("pet", " /early.png "), objective("pet", "/late.png")),
            null, items, Map.of(), true, null));
        edt(pending.get(0)::loaded);
        List<JLabel> slots = iconSlots(card[0]);
        assertEquals(Color.GREEN.getRGB(), iconPixel(slots.get(0)));
        assertNull(slots.get(1).getIcon());
        BufferedImage custom = image(Color.RED);
        edt(() -> {
            card[0].updateArtwork(null, Map.of("/early.png", custom, "/late.png", custom));
            pending.get(1).loaded();
        });
        for (JLabel slot : slots) assertEquals(Color.RED.getRGB(), iconPixel(slot));
    }

    @Test public void bannerUpdateRepaintsTheExistingHeader() throws Exception {
        RaceEventCard[] card = new RaceEventCard[1];
        edt(() -> card[0] = new RaceEventCard(race(), image(Color.RED), null, Map.of(), true, null));
        JComponent header = (JComponent) card[0].getComponent(0);
        int[] before = new int[1], after = new int[1];
        edt(() -> {
            header.setSize(225, header.getPreferredSize().height);
            before[0] = headerPixel(header);
            card[0].updateArtwork(image(Color.BLUE), Map.of());
            after[0] = headerPixel(header);
        });
        assertSame(header, card[0].getComponent(0));
        assertNotEquals(before[0], after[0]);
    }

    private static FeaturedRaceResponse.Race race(FeaturedRaceResponse.Objective... objectives) {
        FeaturedRaceResponse.Race race = new FeaturedRaceResponse.Race();
        race.setName("Race");
        race.setTimeZone("Europe/Tallinn");
        race.setStartDate("2099-01-01T00:00:00Z");
        race.setEndDate("2099-01-08T00:00:00Z");
        race.setObjectives(Arrays.asList(objectives));
        return race;
    }

    private static FeaturedRaceResponse.Objective objective(String kind, String url) {
        FeaturedRaceResponse.Objective objective = new FeaturedRaceResponse.Objective();
        objective.setKind(kind);
        objective.setTitle("First " + kind);
        objective.setImageUrl(url);
        return objective;
    }

    private static List<JLabel> iconSlots(Container root) {
        List<JLabel> slots = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (child instanceof JPanel && child.getName() != null && child.getName().startsWith("race-objective-")) {
                slots.add((JLabel) ((BorderLayout) ((JPanel) child).getLayout()).getLayoutComponent(BorderLayout.WEST));
            } else if (child instanceof Container) slots.addAll(iconSlots((Container) child));
        }
        return slots;
    }

    private static int iconPixel(JLabel slot) {
        return ((BufferedImage) ((ImageIcon) slot.getIcon()).getImage()).getRGB(16, 16);
    }

    private static int headerPixel(JComponent header) {
        BufferedImage canvas = new BufferedImage(header.getWidth(), header.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics graphics = canvas.getGraphics();
        header.paint(graphics);
        graphics.dispose();
        return canvas.getRGB(0, 0);
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        fill(image, color);
        return image;
    }

    private static void fill(BufferedImage image, Color color) {
        Graphics graphics = image.getGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
    }

    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
        SwingUtilities.invokeAndWait(() -> {});
    }
}
