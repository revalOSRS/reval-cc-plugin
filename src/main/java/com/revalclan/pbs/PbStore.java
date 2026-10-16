package com.revalclan.pbs;

import net.runelite.client.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;

/** Shared reader for RS-profile-scoped PB config stores (key -> seconds). */
final class PbStore {
	// Both game interfaces feed the verified PB domain. The wire field remains
	// clogPersonalBests for compatibility with deployed backends.
	static final String VERIFIED_GROUP = "revalclanclogpbv2";
	private PbStore() {}

	static void recordVerified(ConfigManager configManager, String key, int ticks, int completions) {
		if (ticks <= 0 || completions <= 0) {
			configManager.unsetRSProfileConfiguration(VERIFIED_GROUP, key);
		} else {
			configManager.setRSProfileConfiguration(VERIFIED_GROUP, key, ticks * 0.6);
		}
	}

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
