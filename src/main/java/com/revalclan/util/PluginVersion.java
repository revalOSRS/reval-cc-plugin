package com.revalclan.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * The plugin's own version, read once from the {@code runelite-plugin.properties}
 * that Gradle copies into the jar. Every HTTP call to the Reval backend identifies
 * itself with {@link #userAgent()} so the server can tell which builds are running.
 */
@Slf4j
public final class PluginVersion {
	private static final String USER_AGENT_PREFIX = "RuneLite-RevalClan-Plugin/";
	private static final String RESOURCE = "/com/revalclan/runelite-plugin.properties";
	private static final String VERSION = load();

	private PluginVersion() {
	}

	public static String userAgent() {
		return USER_AGENT_PREFIX + VERSION;
	}

	private static String load() {
		try (InputStream in = PluginVersion.class.getResourceAsStream(RESOURCE)) {
			if (in != null) {
				Properties props = new Properties();
				props.load(in);
				String version = props.getProperty("version", "").trim();
				if (!version.isEmpty()) {
					return version;
				}
			}
		} catch (IOException e) {
			log.debug("Could not read plugin version", e);
		}
		return "unknown";
	}
}
