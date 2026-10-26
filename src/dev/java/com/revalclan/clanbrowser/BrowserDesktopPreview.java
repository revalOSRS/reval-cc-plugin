package com.revalclan.clanbrowser;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.revalclan.auth.CredentialStore;
import com.revalclan.auth.LinkTokenManager;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import okhttp3.OkHttpClient;

/** Desktop QA harness: production window and live read-only API, without needing a game login. */
public class BrowserDesktopPreview {
    public static void main(String[] args) throws Exception {
        com.formdev.flatlaf.FlatDarkLaf.setup();
        Gson gson = new Gson(); Map<Integer, BufferedImage> icons = new java.util.HashMap<>();
        String atlasPath = System.getProperty("reval.browserAtlas");
        if (atlasPath != null) {
            Path path = Path.of(atlasPath); BufferedImage atlas = ImageIO.read(path.resolve("items.png").toFile());
            JsonObject data = gson.fromJson(Files.readString(path.resolve("items.json")), JsonObject.class);
            data.entrySet().forEach(entry -> {
                JsonObject sprite = entry.getValue().getAsJsonObject();
                icons.put(Integer.parseInt(entry.getKey()), atlas.getSubimage(sprite.get("x").getAsInt(), sprite.get("y").getAsInt(), sprite.get("width").getAsInt(), sprite.get("height").getAsInt()));
            });
        }
        BrowserCommand command = BrowserCommand.parse(System.getProperty("reval.browserCommand", "reval clog forestry Valgusruum").replace('|', ' '));
        // No game login here, so nothing is ever linked and no account-scoped read can be made: the store is never touched.
        OkHttpClient http = new OkHttpClient();
        BrowserApi api = new BrowserApi(http, gson, new LinkTokenManager(new CredentialStore(null, gson), http, gson));
        SwingUtilities.invokeLater(() -> {
            ClanBrowserWindow window = new ClanBrowserWindow(null, api, icons::get); window.identity(0, "Valgusruum"); window.open(command);
            window.setSize(Integer.getInteger("reval.browserWidth", window.getWidth()), Integer.getInteger("reval.browserHeight", window.getHeight()));
            String screenshot = System.getProperty("reval.browserScreenshot");
            if (screenshot != null) {
                Timer timer = new Timer(350, null);
                timer.addActionListener(event -> {
                    BrowserModels.View view = window.currentView(); if (view == null) return;
                    try {
                        BufferedImage image = new BufferedImage(window.getContentPane().getWidth(), window.getContentPane().getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = image.createGraphics(); window.getContentPane().printAll(g); g.dispose(); ImageIO.write(image, "png", Path.of(screenshot).toFile());
                        System.out.println("Browser screenshot saved: " + screenshot); timer.stop();
                        if (Boolean.getBoolean("reval.browserExitAfterCapture")) { window.dispose(); System.exit(0); }
                    } catch (Exception error) { throw new RuntimeException(error); }
                }); timer.start();
            }
        });
    }
}
