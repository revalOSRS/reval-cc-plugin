package com.revalclan.races;

import com.revalclan.RevalClanPlugin;
import com.revalclan.ui.RaceEventCard;
import com.revalclan.ui.RevalPanel;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Opens the real Competitions tab against the public API without a game login or fake results. */
public final class RaceClientPreview {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(RevalClanPlugin.class);
        RuneLite.main(args);
        Timer ready = new Timer(1000, null);
        ready.addActionListener(event -> {
            if (RuneLite.getInjector() == null) return;
            PluginManager plugins = RuneLite.getInjector().getInstance(PluginManager.class);
            RevalClanPlugin plugin = (RevalClanPlugin) plugins.getPlugins().stream()
                .filter(p -> p instanceof RevalClanPlugin && plugins.isPluginActive(p)).findFirst().orElse(null);
            if (plugin == null) return;
            try {
                Field panelField = RevalClanPlugin.class.getDeclaredField("revalPanel"); panelField.setAccessible(true);
                Field navField = RevalClanPlugin.class.getDeclaredField("navButton"); navField.setAccessible(true);
                RevalPanel panel = (RevalPanel) panelField.get(plugin);
                NavigationButton nav = (NavigationButton) navField.get(plugin);
                if (panel == null || nav == null) return;
                RuneLite.getInjector().getInstance(ClientToolbar.class).openPanel(nav);
                panel.showTab("COMPETITIONS");
                for (Window window : Window.getWindows()) if (window instanceof JFrame && window.isShowing()) {
                    JFrame frame = (JFrame) window;
                    frame.setTitle("Reval - Raid race preview");
                    Rectangle screen = frame.getGraphicsConfiguration().getBounds();
                    Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(frame.getGraphicsConfiguration());
                    int height = Math.min(920, screen.height - insets.top - insets.bottom);
                    frame.setSize(Math.max(frame.getWidth(), 1050), height);
                    frame.setLocation(screen.x + Math.max(0, (screen.width - frame.getWidth()) / 2), screen.y + insets.top);
                }
                ready.stop();
                System.out.println("Opened live race event in RuneLite Competitions tab");
                String screenshot = System.getProperty("reval.raceScreenshot");
                if (screenshot != null) capture(panel, screenshot);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        });
        ready.start();
    }
    private static RaceEventCard findRace(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof RaceEventCard && child.isShowing()) return (RaceEventCard) child;
            if (child instanceof Container) {
                RaceEventCard found = findRace((Container) child);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static void capture(RevalPanel panel, String destination) {
        Timer timer = new Timer(700, null);
        timer.addActionListener(event -> {
            RaceEventCard card = findRace(panel);
            if (card == null || panel.getWidth() <= 0 || panel.getHeight() <= 0) return;
            try {
                BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
                ImageIO.write(image, "png", Path.of(destination).toFile());
                BufferedImage fullCard = new BufferedImage(card.getWidth(), card.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D cardGraphics = fullCard.createGraphics();
                card.printAll(cardGraphics);
                cardGraphics.dispose();
                Path cardPath = Path.of(destination.replaceFirst("\\.png$", "-card.png"));
                ImageIO.write(fullCard, "png", cardPath.toFile());
                System.out.println("Race sidebar screenshot saved: " + destination); timer.stop();
            } catch (Exception error) { throw new IllegalStateException(error); }
        });
        timer.start();
    }
}
