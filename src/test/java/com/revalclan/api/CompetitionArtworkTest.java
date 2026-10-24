package com.revalclan.api;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompetitionArtworkTest {
	@Test public void largeArtworkIsSampledBeforeItIsRetained() throws Exception {
		ByteArrayOutputStream encoded = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(3200, 40, BufferedImage.TYPE_INT_RGB), "png", encoded);
		try (ResponseBody body = ResponseBody.create(null, encoded.toByteArray())) {
			BufferedImage image = CompetitionArtwork.read(body);
			assertEquals(1600, image.getWidth());
			assertEquals(20, image.getHeight());
		}
	}

	@Test public void declaredOversizedBodiesAreRejectedBeforeReading() throws Exception {
		try (ResponseBody body = new ResponseBody() {
			@Override public MediaType contentType() { return null; }
			@Override public long contentLength() { return 8 * 1024 * 1024 + 1; }
			@Override public BufferedSource source() { fail("Oversized artwork must not be downloaded"); return null; }
			@Override public void close() {}
		}) {
			try { CompetitionArtwork.read(body); fail("Expected size rejection"); }
			catch (IOException expected) { assertTrue(expected.getMessage().contains("too large")); }
		}
	}

	@Test public void chunkedBodiesHaveTheSameByteLimit() throws Exception {
		try (ResponseBody body = new ResponseBody() {
			private final BufferedSource bytes = new Buffer().write(new byte[8 * 1024 * 1024 + 1]);
			@Override public MediaType contentType() { return null; }
			@Override public long contentLength() { return -1; }
			@Override public BufferedSource source() { return bytes; }
		}) {
			try { CompetitionArtwork.read(body); fail("Expected size rejection"); }
			catch (IOException expected) { assertTrue(expected.getMessage().contains("too large")); }
		}
	}

	@Test public void imageDimensionsAreCheckedBeforePixelAllocation() throws Exception {
		// A PNG header with valid CRC and 5000 x 5000 pixels, but no pixel data.
		byte[] header = okio.ByteString.decodeHex("89504e470d0a1a0a0000000d49484452000013880000138808020000002e129964").toByteArray();
		try (ResponseBody body = ResponseBody.create(null, header)) {
			try { CompetitionArtwork.read(body); fail("Expected dimension rejection"); }
			catch (IOException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("dimensions")); }
		}
	}
}
