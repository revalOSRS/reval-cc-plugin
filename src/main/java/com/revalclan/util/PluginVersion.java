package com.revalclan.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import lombok.extern.slf4j.Slf4j;

/**
 * The plugin's own version, read once from {@code version.properties} which Gradle
 * fills in from {@code build.gradle}'s {@code version} at build time. Every HTTP call
 * to the Reval backend identifies itself with {@link #userAgent()} so the server can
 * tell which builds are in the wild.
 */
@Slf4j
public final class PluginVersion
{
	private static final String USER_AGENT_PREFIX = "RuneLite-RevalClan-Plugin/";
	private static final String VERSION = load();

	private PluginVersion()
	{
	}

	public static String get()
	{
		return VERSION;
	}

	public static String userAgent()
	{
		return USER_AGENT_PREFIX + VERSION;
	}

	private static String load()
	{
		try (InputStream in = PluginVersion.class.getResourceAsStream("/com/revalclan/version.properties"))
		{
			if (in != null)
			{
				Properties props = new Properties();
				props.load(in);
				String v = props.getProperty("version", "").trim();
				// An unexpanded template means the resource was not processed by Gradle.
				if (!v.isEmpty() && !v.startsWith("$"))
				{
					return v;
				}
			}
		}
		catch (IOException e)
		{
			log.debug("Could not read plugin version", e);
		}
		return "unknown";
	}
}
