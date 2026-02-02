package com.revalclan.util;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Utility class for loading icons from OSRS Wiki.
 */
@Slf4j
@Singleton
public class WikiIconLoader {
    private static final String WIKI_BASE_URL = "https://oldschool.runescape.wiki/images/";
    private static final String WIKI_CLAN_ICON_URL = WIKI_BASE_URL + "Clan_icon_-_%s.png";
    
    // Cache for loaded images (key: icon identifier, value: scaled BufferedImage)
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    
    @Inject
    private OkHttpClient httpClient;
    
    /**
     * Load an icon from OSRS Wiki and set it on a JLabel.
     * 
     * @param iconIdentifier The icon identifier (e.g., "Fire_cape", "Collection_log_detail")
     * @param targetSize The target size for the icon (width and height)
     * @param iconLabel The JLabel to set the icon on
     */
    public void loadIconForLabel(String iconIdentifier, int targetSize, JLabel iconLabel) {
        if (iconIdentifier == null || iconIdentifier.isEmpty()) {
            return;
        }
        
        loadIcon(iconIdentifier, targetSize, targetSize, scaledImage -> {
            SwingUtilities.invokeLater(() -> {
                iconLabel.setIcon(new ImageIcon(scaledImage));
            });
        });
    }
    
    /**
     * Load an icon from OSRS Wiki and set it on a CollapsibleSection.
     * 
     * @param iconIdentifier The icon identifier
     * @param targetSize The target size for the icon
     * @param section The CollapsibleSection to set the icon on
     */
    public void loadIconForSection(String iconIdentifier, int targetSize, com.revalclan.ui.components.CollapsibleSection section) {
        if (iconIdentifier == null || iconIdentifier.isEmpty()) {
            return;
        }
        
        loadIcon(iconIdentifier, targetSize, targetSize, scaledImage -> {
            SwingUtilities.invokeLater(() -> {
                section.setIcon(new ImageIcon(scaledImage));
            });
        });
    }
    
    /**
     * Load a clan icon from OSRS Wiki using the clan icon name.
     * 
     * @param clanIconName The clan icon name (e.g., "Mentor", "Leader")
     * @param targetSize The target size for the icon
     * @param iconLabel The JLabel to set the icon on
     */
    public void loadClanIcon(String clanIconName, int targetSize, JLabel iconLabel) {
        if (clanIconName == null || clanIconName.isEmpty()) {
            return;
        }
        
        String url = String.format(WIKI_CLAN_ICON_URL, clanIconName);
        String cacheKey = "clan_" + clanIconName;
        
        loadIconFromUrl(url, cacheKey, targetSize, targetSize, scaledImage -> {
            SwingUtilities.invokeLater(() -> {
                iconLabel.setIcon(new ImageIcon(scaledImage));
            });
        });
    }
    
    /**
     * Load a clan icon from OSRS Wiki and set it on a CollapsibleSection.
     * 
     * @param clanIconName The clan icon name (e.g., "Mentor", "Brigadier")
     * @param targetSize The target size for the icon
     * @param section The CollapsibleSection to set the icon on
     */
    public void loadClanIconForSection(String clanIconName, int targetSize, com.revalclan.ui.components.CollapsibleSection section) {
        if (clanIconName == null || clanIconName.isEmpty()) {
            return;
        }
        
        String url = String.format(WIKI_CLAN_ICON_URL, clanIconName);
        String cacheKey = "clan_" + clanIconName;
        
        loadIconFromUrl(url, cacheKey, targetSize, targetSize, scaledImage -> {
            SwingUtilities.invokeLater(() -> {
                section.setIcon(new ImageIcon(scaledImage));
            });
        });
    }
    
    /**
     * Load an icon and provide it via callback (for custom handling).
     * 
     * @param iconIdentifier The icon identifier
     * @param targetWidth The target width
     * @param targetHeight The target height
     * @param onSuccess Callback that receives the scaled BufferedImage
     */
    public void loadIcon(String iconIdentifier, int targetWidth, int targetHeight, Consumer<BufferedImage> onSuccess) {
        if (iconIdentifier == null || iconIdentifier.isEmpty()) {
            return;
        }
        
        String url = WIKI_BASE_URL + iconIdentifier + ".png";
        loadIconFromUrl(url, iconIdentifier, targetWidth, targetHeight, onSuccess);
    }
    
    /**
     * Internal method to load an icon from a URL using OkHttp.
     */
    private void loadIconFromUrl(String url, String cacheKey, int targetWidth, int targetHeight, Consumer<BufferedImage> onSuccess) {
        // Check cache first
        BufferedImage cached = imageCache.get(cacheKey);
        if (cached != null) {
            onSuccess.accept(cached);
            return;
        }
        
        Request request = new Request.Builder()
            .url(url)
            .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                log.debug("Failed to load icon from {}: {}", url, e.getMessage());
            }
            
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        return;
                    }
                    
                    try (InputStream inputStream = response.body().byteStream()) {
                        BufferedImage image = ImageIO.read(inputStream);
                        
                        if (image != null) {
                            // Scale image preserving aspect ratio
                            BufferedImage scaled = resizeImagePreservingAspectRatio(image, targetWidth, targetHeight);
                            
                            // Cache the scaled image
                            imageCache.put(cacheKey, scaled);
                            
                            // Call success callback
                            onSuccess.accept(scaled);
                        }
                    }
                } catch (IOException e) {
                    log.debug("Failed to read icon image from {}: {}", url, e.getMessage());
                } finally {
                    response.close();
                }
            }
        });
    }
    
    /**
     * Resize image preserving aspect ratio, centering it in the target size.
     */
    private BufferedImage resizeImagePreservingAspectRatio(BufferedImage original, int targetWidth, int targetHeight) {
        int originalWidth = original.getWidth();
        int originalHeight = original.getHeight();
        
        // Calculate scaling factor to fit within target size while preserving aspect ratio
        double scale = Math.min((double) targetWidth / originalWidth, (double) targetHeight / originalHeight);
        
        int scaledWidth = (int) (originalWidth * scale);
        int scaledHeight = (int) (originalHeight * scale);
        
        // Create scaled image
        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Center the scaled image
        int x = (targetWidth - scaledWidth) / 2;
        int y = (targetHeight - scaledHeight) / 2;
        
        g2d.drawImage(original, x, y, scaledWidth, scaledHeight, null);
        g2d.dispose();
        
        return scaled;
    }
}
