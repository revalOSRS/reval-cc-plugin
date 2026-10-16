package com.revalclan.clanbrowser;

import com.revalclan.RevalClanPlugin;
import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.PluginManager;
import javax.swing.Timer;
import java.awt.Window;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Starts the real local client and opens the browser directly without sending chat. */
public class BrowserClientPreview {
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(RevalClanPlugin.class); RuneLite.main(args);
        String command = System.getProperty("reval.browserCommand", "leaderboard clog").replace('|', ' ');
        Timer ready = new Timer(1000, null);
        ready.addActionListener(event -> {
            if (RuneLite.getInjector() == null) return;
            PluginManager plugins = RuneLite.getInjector().getInstance(PluginManager.class);
            RevalClanPlugin reval = (RevalClanPlugin) plugins.getPlugins().stream()
                .filter(plugin -> plugin instanceof RevalClanPlugin && plugins.isPluginActive(plugin)).findFirst().orElse(null);
            if (reval == null) return;
            reval.getInjector().getInstance(ClanBrowserManager.class).open(BrowserCommand.parse(command));
            System.out.println("Opened local RuneLite clan browser: " + command); ready.stop();
            String screenshot = System.getProperty("reval.browserScreenshot");
            if (screenshot != null) captureWhenReady(screenshot);
        }); ready.start();
    }
    private static void captureWhenReady(String path) {
        Timer capture = new Timer(1000, null);
        capture.addActionListener(event -> {
            for (Window candidate : Window.getWindows()) if (candidate instanceof ClanBrowserWindow && candidate.isShowing()) {
                ClanBrowserWindow window = (ClanBrowserWindow) candidate;
                if (window.currentView() == null) return;
                try {
                    BufferedImage image = new BufferedImage(window.getContentPane().getWidth(), window.getContentPane().getHeight(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = image.createGraphics(); window.getContentPane().printAll(g); g.dispose();
                    ImageIO.write(image, "png", Path.of(path).toFile());
                    System.out.println("RuneLite browser render saved: " + path); capture.stop();
                } catch (Exception error) { error.printStackTrace(); capture.stop(); }
            }
        }); capture.start();
    }
}
