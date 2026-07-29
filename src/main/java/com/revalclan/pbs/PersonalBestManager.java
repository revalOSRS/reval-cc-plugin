package com.revalclan.pbs;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class PersonalBestManager {
	@Inject private ConfigManager configManager;

	public Map<String, Object> sync() {
		return PbStore.read(configManager, "personalbest");
	}
}
