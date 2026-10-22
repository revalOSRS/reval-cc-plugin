package com.revalclan.util;

import javax.imageio.ImageIO;
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
    
    private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    
    /**
     * Load a BufferedImage from assets directory.
     * Results are cached for efficiency.
     * 
     * @param filename The filename (e.g., "reval.png")
     * @return The BufferedImage, or null if not found
     */
    public BufferedImage getImage(String filename) {
      if (filename == null || filename.isEmpty()) {
        return null;
      }
      
      String normalizedFilename = normalizeFilename(filename);
      
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
          imageCache.put(normalizedFilename, image);
        }
        return image;
      } catch (IOException e) {
        return null;
      }
    }
    
    /**
     * Load an icon from assets directory and scale it to the specified size.
     * Results are cached for efficiency.
     * 
     * @param filename The filename (e.g., "checkmark.png", "info.png")
     * @param size The target size (width and height)
     * @return The scaled ImageIcon, or null if not found
     */
    public ImageIcon getIcon(String filename, int size) {
      if (filename == null || filename.isEmpty()) {
        return null;
      }
      
      String cacheKey = normalizeFilename(filename) + "_" + size;
      
      ImageIcon cached = iconCache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      
      // Shares getImage's cache, so an image loaded at one size is reused for others
      BufferedImage image = getImage(filename);
      if (image == null) {
        return null;
      }
      
      Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
      ImageIcon icon = new ImageIcon(scaled);
      iconCache.put(cacheKey, icon);
      
      return icon;
    }
    
    /** Bare file name (anything up to the last '/' dropped), with ".png" added when missing. */
    private String normalizeFilename(String filename) {
      String normalized = filename.substring(filename.lastIndexOf('/') + 1);
      return normalized.endsWith(".png") ? normalized : normalized + ".png";
    }
}
