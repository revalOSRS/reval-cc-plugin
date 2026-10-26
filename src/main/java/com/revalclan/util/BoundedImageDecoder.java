package com.revalclan.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Enforce both compressed and decoded bounds before allocating remote artwork. */
public final class BoundedImageDecoder {
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long MAX_PIXELS = 8_000_000L;
    private static final int MAX_SIDE = 4096;

    private BoundedImageDecoder() {}

    public static BufferedImage read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Event image exceeds the download limit");
            bytes.write(buffer, 0, count);
        }
        try (ImageInputStream image = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            if (image == null) throw new IOException("Unsupported event image format");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) throw new IOException("Unsupported event image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(image, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_SIDE || height > MAX_SIDE
                    || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Event image exceeds the pixel limit");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IOException("Unsupported event image format");
                return decoded;
            } finally {
                reader.dispose();
            }
        }
    }
}
