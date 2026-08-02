package com.revalclan.shops;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXPERIMENTAL: self-contained JSONL logger for shop transaction research.
 *
 * While a shop-ish interface is open and debug mode is enabled, {@link ShopTracker}
 * funnels every relevant client event through here. Each line is a standalone JSON
 * object so the resulting file can be replayed/grepped/jq'd to map the widget ids,
 * container ids and varp/varbit ids of specific reward shops.
 *
 * Output: ~/.runelite/reval-debug/shop-log-&lt;yyyyMMdd-HHmmss&gt;.jsonl
 * (one file per plugin session, created lazily on first write).
 */
@Slf4j
@Singleton
public class ShopDebugLog {
	private static final SimpleDateFormat FILE_STAMP = new SimpleDateFormat("yyyyMMdd-HHmmss");

	@Inject
	private Gson gson;

	private BufferedWriter writer;
	private File file;

	/**
	 * Append one JSONL record. Safe to call from the client thread (I/O is a
	 * single buffered line write + flush; acceptable for a debug tool).
	 *
	 * @param type   short record type tag, e.g. "menu_click", "varbit", "transaction"
	 * @param tick   current client tick counter (tracker-local)
	 * @param fields record payload; may be null for marker records
	 */
	public synchronized void write(String type, int tick, Map<String, Object> fields) {
		try {
			ensureOpen();

			Map<String, Object> line = new LinkedHashMap<>();
			line.put("t", type);
			line.put("tick", tick);
			line.put("ts", System.currentTimeMillis());
			if (fields != null) {
				line.putAll(fields);
			}

			writer.write(gson.toJson(line));
			writer.newLine();
			writer.flush();
		} catch (Exception e) {
			// Never let debug logging break the game client
			log.warn("ShopDebugLog write failed", e);
		}
	}

	/** @return the current log file, or null if nothing has been written yet */
	public synchronized File getFile() {
		return file;
	}

	public synchronized void close() {
		if (writer != null) {
			try {
				writer.flush();
				writer.close();
			} catch (IOException e) {
				log.warn("ShopDebugLog close failed", e);
			}
			writer = null;
			file = null;
		}
	}

	private void ensureOpen() throws IOException {
		if (writer != null) {
			return;
		}

		File dir = new File(RuneLite.RUNELITE_DIR, "reval-debug");
		if (!dir.exists() && !dir.mkdirs()) {
			throw new IOException("Could not create " + dir);
		}

		file = new File(dir, "shop-log-" + FILE_STAMP.format(new Date()) + ".jsonl");
		writer = new BufferedWriter(new FileWriter(file, true));
		log.info("Shop debug log opened: {}", file.getAbsolutePath());
	}
}
