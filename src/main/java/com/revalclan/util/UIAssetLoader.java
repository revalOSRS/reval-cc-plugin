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
    
    private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
    
    @Inject
    public UIAssetLoader() {}
    
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
      
      String normalizedFilename = normalizeFilename(filename);
      String cacheKey = normalizedFilename + "_" + size;
      
      ImageIcon cached = iconCache.get(cacheKey);
      if (cached != null) {
        return cached;
      }
      
      BufferedImage image = imageCache.get(normalizedFilename);
      if (image == null) {
        String resourcePath = ASSETS_BASE_PATH + normalizedFilename;
        URL imageUrl = getClass().getResource(resourcePath);
        
        if (imageUrl == null) {
          return null;
        }
        
        try {
          image = ImageIO.read(imageUrl);
          if (image != null) {
            imageCache.put(normalizedFilename, image);
          }
        } catch (IOException e) {
          return null;
        }
      }
      
      if (image == null) {
        return null;
      }
      
      Image scaled = image.getScaledInstance(size, size, Image.SCALE_SMOOTH);
      ImageIcon icon = new ImageIcon(scaled);
      iconCache.put(cacheKey, icon);
      
      return icon;
    }
    
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
