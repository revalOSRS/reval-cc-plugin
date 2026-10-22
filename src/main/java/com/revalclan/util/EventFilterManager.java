package com.revalclan.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages dynamic event filters fetched from the backend API
 */
@Slf4j
@Singleton
public class EventFilterManager{
	private static final String FILTERS_URL = "https://api.revalosrs.ee/event-filters";
	
	@Inject private OkHttpClient httpClient;
	
	@Inject private Gson gson;
	
	@Getter private volatile EventFilters filters;
	private String appliedVersion;
	private boolean fetchInProgress;
	private int generation;
	private int ticksRemaining;
	private static final int DAILY_TICKS = 144_000;
	private static final int RETRY_TICKS = 100; // One minute after a failed fetch.

	/** Runs after every successful fetch, once the new filters are in place. */
	@Setter private Runnable onFiltersApplied;
	
	/**
	 * Holds all filter configurations
	 */
	@Getter
	public static class EventFilters {
		// Loot filter settings
		private long lootMinValue = 1_000_000; // 1M default
		private Set<Integer> lootWhitelist = new HashSet<>();
		private Set<Integer> lootBlacklist = new HashSet<>(Arrays.asList(526, 995)); // Bones, Coins
		
		// Detailed kill filter settings
		private Set<Integer> detailedKillNpcIdWhitelist = new HashSet<>();
		private Set<Integer> detailedKillNpcIdBlacklist = new HashSet<>();
		/** NPC names (lowercase) needed by active server-side requirements; matched by containment */
		private Set<String> detailedKillNpcNameWhitelist = new HashSet<>();
		
		// Chat filter settings
		private List<String> chatPatterns = new ArrayList<>(); // Empty by default = no patterns, all messages pass

		/**
		 * Item names (lowercase) whose silent consumption is reported. Derived by
		 * the backend from live tile requirements; nothing is built into the
		 * plugin, and empty means watch nothing.
		 */
		private Set<String> inventoryWatchItems = new HashSet<>();
		/** Watched item names as the backend spelled them, keyed lowercased. */
		private Map<String, String> inventoryWatchItemNames = new HashMap<>();

		/** Varbit ids whose value the plugin reports (varbits.watch). Empty by default. */
		private Set<Integer> varbitWatch = new HashSet<>();
		
		// Event toggles
		private boolean lootEnabled = true;
		private boolean petEnabled = true;
		private boolean questEnabled = true;
		private boolean levelEnabled = true;
		private boolean killCountEnabled = true;
		private boolean clueEnabled = true;
		private boolean diaryEnabled = true;
		private boolean combatAchievementEnabled = true;
		private boolean collectionEnabled = true;
		private boolean deathEnabled = true;
		private boolean detailedKillEnabled = true;
		private boolean emoteEnabled = true;
		private boolean chatEnabled = true;
		private boolean musicEnabled = true;
		private boolean leaguesEnabled = false;
	}
	
	public EventFilterManager() {
		// Initialize with defaults
		filters = new EventFilters();
	}
	
	public synchronized void resetSession() {
		generation++;
		fetchInProgress = false;
		appliedVersion = null;
		ticksRemaining = 0;
	}

	public synchronized void onGameTick() {
		if (!fetchInProgress && --ticksRemaining <= 0) fetchFiltersAsync();
	}

	/** A missing hint falls back to heartbeat-paced fetching during rollout/outages. */
	public synchronized void onServerVersion(String version) {
		if (version == null || !version.equals(appliedVersion)) fetchFiltersAsync();
	}

	public synchronized void fetchFiltersAsync() {
		if (fetchInProgress) return;
		fetchInProgress = true;
		final int requestGeneration = generation;
		Request request = new Request.Builder().url(FILTERS_URL).get()
			.addHeader("User-Agent", PluginVersion.userAgent()).build();
		httpClient.newCall(request).enqueue(new Callback() {
			@Override public void onFailure(Call call, IOException error) {
				synchronized (EventFilterManager.this) {
					if (requestGeneration != generation) return;
					fetchInProgress = false;
					ticksRemaining = RETRY_TICKS;
				}
				log.warn("Failed to fetch filters", error);
			}

			@Override public void onResponse(Call call, Response response) {
				try (Response closeable = response) {
					JsonObject json = response.isSuccessful() && response.body() != null
						? gson.fromJson(response.body().string(), JsonObject.class) : null;
					EventFilters parsed = json == null ? null : parseFilters(json);
					Runnable listener;
					synchronized (EventFilterManager.this) {
						if (requestGeneration != generation) return;
						fetchInProgress = false;
						ticksRemaining = RETRY_TICKS;
						if (parsed == null) return;
						filters = parsed;
						appliedVersion = response.header("X-Reval-Filters-Version");
						ticksRemaining = DAILY_TICKS;
						listener = onFiltersApplied;
					}
					if (listener != null) listener.run();
				} catch (Exception error) {
					onFailure(call, new IOException("Invalid filter response", error));
				}
			}
		});
	}

	/**
	 * Parse the filters JSON response
	 */
	private EventFilters parseFilters(JsonObject json) {
		EventFilters newFilters = new EventFilters();
		
		try {
			// A fresh EventFilters has empty lists, except the loot blacklist's defaults
			// Parse loot filters
			if (json.has("loot")) {
				JsonObject loot = json.getAsJsonObject("loot");
				
				if (loot.has("minValue")) {
					newFilters.lootMinValue = loot.get("minValue").getAsLong();
				}
				
				forEachIn(loot, "whitelist", item -> newFilters.lootWhitelist.add(item.getAsInt()));
				
				// Clear defaults and only use API values
				newFilters.lootBlacklist.clear();
				forEachIn(loot, "blacklist", item -> newFilters.lootBlacklist.add(item.getAsInt()));
			}
			
			// Parse detailed kill filters
			if (json.has("detailedKill")) {
				JsonObject detailedKill = json.getAsJsonObject("detailedKill");
				forEachIn(detailedKill, "npcIdWhitelist", id -> newFilters.detailedKillNpcIdWhitelist.add(id.getAsInt()));
				forEachIn(detailedKill, "npcIdBlacklist", id -> newFilters.detailedKillNpcIdBlacklist.add(id.getAsInt()));
				forEachIn(detailedKill, "npcNameWhitelist", name ->
					newFilters.detailedKillNpcNameWhitelist.add(name.getAsString().toLowerCase())
				);
			}
			
			// Parse inventory watches
			if (json.has("inventory")) {
				forEachIn(json.getAsJsonObject("inventory"), "watchItems", item -> {
					String name = item.getAsString().trim().toLowerCase();
					if (!name.isEmpty()) {
						newFilters.inventoryWatchItems.add(name);
						newFilters.inventoryWatchItemNames.put(name, item.getAsString().trim());
					}
				});
			}

			if (json.has("varbits")) {
				forEachIn(json.getAsJsonObject("varbits"), "watch", id -> {
					if (id.isJsonPrimitive() && id.getAsJsonPrimitive().isNumber()) {
						newFilters.varbitWatch.add(id.getAsInt());
					}
				});
				log.info("[Reval] watching varbits {}", newFilters.varbitWatch);
			}

			// Parse chat filters
			if (json.has("chat")) {
				forEachIn(json.getAsJsonObject("chat"), "patterns", pattern -> newFilters.chatPatterns.add(pattern.getAsString()));
			}
			
			// Parse event toggles; an omitted toggle keeps its default
			if (json.has("enabled")) {
				JsonObject enabled = json.getAsJsonObject("enabled");
				
				newFilters.lootEnabled = flag(enabled, "loot", newFilters.lootEnabled);
				newFilters.petEnabled = flag(enabled, "pet", newFilters.petEnabled);
				newFilters.questEnabled = flag(enabled, "quest", newFilters.questEnabled);
				newFilters.levelEnabled = flag(enabled, "level", newFilters.levelEnabled);
				newFilters.killCountEnabled = flag(enabled, "killCount", newFilters.killCountEnabled);
				newFilters.clueEnabled = flag(enabled, "clue", newFilters.clueEnabled);
				newFilters.diaryEnabled = flag(enabled, "diary", newFilters.diaryEnabled);
				newFilters.combatAchievementEnabled = flag(enabled, "combatAchievement", newFilters.combatAchievementEnabled);
				newFilters.collectionEnabled = flag(enabled, "collection", newFilters.collectionEnabled);
				newFilters.deathEnabled = flag(enabled, "death", newFilters.deathEnabled);
				newFilters.detailedKillEnabled = flag(enabled, "detailedKill", newFilters.detailedKillEnabled);
				newFilters.emoteEnabled = flag(enabled, "emote", newFilters.emoteEnabled);
				newFilters.chatEnabled = flag(enabled, "chat", newFilters.chatEnabled);
				newFilters.musicEnabled = flag(enabled, "music", newFilters.musicEnabled);
				newFilters.leaguesEnabled = flag(enabled, "leagues", newFilters.leaguesEnabled);
			}
			
			return newFilters;
		} catch (Exception e) {
			log.error("Error parsing filters JSON", e);
			return null;
		}
	}

	/** Runs {@code action} on each element of array {@code key}; an absent or non-array value does nothing. */
	private static void forEachIn(JsonObject parent, String key, Consumer<JsonElement> action) {
		if (parent.has(key) && parent.get(key).isJsonArray()) {
			parent.getAsJsonArray(key).forEach(action);
		}
	}

	private static boolean flag(JsonObject enabled, String key, boolean current) {
		return enabled.has(key) ? enabled.get(key).getAsBoolean() : current;
	}
}

