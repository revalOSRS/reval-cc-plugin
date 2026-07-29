package com.revalclan.pbs;

import net.runelite.client.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/** Shared reader for RS-profile-scoped PB config stores (key -> seconds). */
final class PbStore {
	private PbStore() {}

	static Map<String, Object> read(ConfigManager configManager, String group) {
		Map<String, Object> pbs = new HashMap<>();
		String profile = configManager.getRSProfileKey();
		if (profile == null) return pbs;
		for (String key : configManager.getRSProfileConfigurationKeys(group, profile, "")) {
			try {
				pbs.put(key, Double.parseDouble(configManager.getRSProfileConfiguration(group, key)));
			} catch (Exception ignored) {}
		}
		return pbs;
	}
}
