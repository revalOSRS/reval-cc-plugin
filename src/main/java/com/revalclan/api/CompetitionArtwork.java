package com.revalclan.api;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import okhttp3.ResponseBody;

/** Bounded optional artwork: inspect encoded size and dimensions before decoding a sampled image. */
final class CompetitionArtwork {
	private static final int MAX_BYTES = 8 * 1024 * 1024;
	private static final int MAX_PIXELS = 16_000_000;
	private static final int MAX_SIDE = 1600;

	static BufferedImage read(ResponseBody body) throws IOException {
		if (body.contentLength() > MAX_BYTES || body.source().request(MAX_BYTES + 1L)) {
			throw new IOException("Event artwork is too large");
		}
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
			new ByteArrayInputStream(body.source().readByteArray()))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw new IOException("Unsupported event image format");
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				int width = reader.getWidth(0), height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
					throw new IOException("Event artwork dimensions are too large");
				}
				int sample = Math.max(1, (Math.max(width, height) + MAX_SIDE - 1) / MAX_SIDE);
				ImageReadParam options = reader.getDefaultReadParam();
				options.setSourceSubsampling(sample, sample, 0, 0);
				BufferedImage image = reader.read(0, options);
				if (image == null) throw new IOException("Unsupported event image format");
				return image;
			} finally { reader.dispose(); }
		}
	}
}
