package com.revalclan.util;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class UIAssetLoader {
    private static final String ASSETS_BASE_PATH = "/com/revalclan/ui/assets/";
    
    // Cache for loaded ImageIcons (key: "filename_size", value: ImageIcon)
    private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
    
    // Cache for loaded BufferedImages (key: filename, value: BufferedImage)
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    
    @Inject
    public UIAssetLoader() {}
    
    /**
     * Load an icon from assets directory by filename and scale it to the specified size.
     * The icon is cached by filename and size.
     * 
     * @param filename The filename of the asset (e.g., "checkmark.png", "info.png")
     * @param size The target size for the icon (width and height)
     * @return The scaled ImageIcon, or null if the asset could not be loaded
     */
    public ImageIcon getIcon(String filename, int size) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        
        // Normalize filename (remove leading slash if present, ensure .png extension)
        String normalizedFilename = normalizeFilename(filename);
        String cacheKey = normalizedFilename + "_" + size;
        
        // Check cache first
        ImageIcon cached = iconCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Load the image
        BufferedImage image = getImage(normalizedFilename);
        if (image == null) {
            return null;
        }
        
        // Scale the image
        Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        ImageIcon icon = new ImageIcon(scaled);
        
        // Cache the scaled icon
        iconCache.put(cacheKey, icon);
        
        return icon;
    }
    
    private BufferedImage getImage(String filename) {
        if (filename == null || filename.isEmpty()) {
          return null;
        }
        
        // Normalize filename
        String normalizedFilename = normalizeFilename(filename);
        
        // Check cache first
        BufferedImage cached = imageCache.get(normalizedFilename);
        if (cached != null) {
          return cached;
        }
        
        String resourcePath = ASSETS_BASE_PATH + normalizedFilename;
        URL imageUrl = getClass().getResource(resourcePath);
        
        if (imageUrl == null) {
            return null;
        }
        
        try {
          BufferedImage image = ImageIO.read(imageUrl);
          if (image != null) {
            // Cache the image
            imageCache.put(normalizedFilename, image);
            return image;
          } else {
              return null;
          }
        } catch (IOException e) {
          return null;
        }
    }
    
    /**
     * Normalize filename by removing leading slash and ensuring .png extension.
     */
    private String normalizeFilename(String filename) {
      String normalized = filename;
      if (normalized.startsWith("/")) {
        normalized = normalized.substring(1);
      }

      if (normalized.contains("/")) {
        normalized = normalized.substring(normalized.lastIndexOf("/") + 1);
      }

      if (!normalized.endsWith(".png")) {
        normalized = normalized + ".png";
      }
      return normalized;
    }
}
