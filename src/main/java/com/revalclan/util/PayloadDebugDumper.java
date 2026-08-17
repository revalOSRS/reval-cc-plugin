package com.revalclan.util;

import com.google.gson.Gson;
import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Debug mode: writes the exact payload maps handed to the webhook sender to
 * ~/.runelite/reval-debug/<name>-<timestamp>.json. Off the game thread, never throws.
 */
@Slf4j
@Singleton
public class PayloadDebugDumper {
	@Inject private RevalClanConfig config;
	@Inject private Gson gson;

	public void dump(String name, Map<String, Object> payload) {
		if (!config.debugMode()) return;
		new Thread(() -> {
			try {
				File dir = new File(RuneLite.RUNELITE_DIR, "reval-debug");
				if (!dir.exists()) dir.mkdirs();
				String file = name + "-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".json";
				Files.write(new File(dir, file).toPath(), gson.toJson(payload).getBytes(StandardCharsets.UTF_8));
				log.info("Payload dumped to reval-debug/{}", file);
			} catch (Exception e) {
				log.warn("Payload dump failed: {}", e.getMessage());
			}
		}, "reval-payload-dump").start();
	}
}
