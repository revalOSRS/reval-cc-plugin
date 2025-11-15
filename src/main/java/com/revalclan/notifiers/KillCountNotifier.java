package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Notifies on boss kill counts
 * Portions inspired by Dink plugin (BSD 2-Clause License)
 */
@Slf4j
@Singleton
public class KillCountNotifier extends BaseNotifier {
	// Primary pattern: "Your <boss> kill/chest/completion count is: <count>"
	private static final Pattern PRIMARY_REGEX = Pattern.compile(
		"Your (?<key>.+?)\\s+(?<type>kill|chest|completion|harvest|success|opened)\\s?count is:?\\s*(?<value>[\\d,]+)\\b",
		Pattern.CASE_INSENSITIVE
	);
	
	// Secondary pattern: "Your completed <raid> count is: <count>"
	private static final Pattern SECONDARY_REGEX = Pattern.compile(
		"Your (?:completed|subdued) (?<key>.+) count is: (?<value>[\\d,]+)\\b",
		Pattern.CASE_INSENSITIVE
	);
	
	// Time pattern: "Duration: <time>" or "Time: <time> (Personal best: <pb>)"
	private static final Pattern TIME_REGEX = Pattern.compile(
		"(?:Duration|time|Subdued in):?\\s*(?<time>[\\d:]+(?:\\.\\d+)?)\\.?(?:\\s*\\(new personal best\\))?(?:\\s*Personal best:\\s*(?<pbtime>[\\d:]+(?:\\.\\d+)?))?",
		Pattern.CASE_INSENSITIVE
	);

	@Inject private RevalClanConfig config;
	
	private String pendingBoss = null;
	private Integer pendingCount = null;
	private Duration pendingTime = null;
	private boolean pendingIsPb = false;
	private int badTicks = 0;
	private static final int MAX_BAD_TICKS = 10;

	@Override
	public boolean isEnabled() {
		return config.notifyKillCount() && filterManager.getFilters().isKillCountEnabled();
	}

	@Override
	protected String getEventType() {
		return "KILL_COUNT";
	}

	public void onChatMessage(String message) {
		if (!isEnabled()) return;
		
		// Skip preparation messages
		if (message.startsWith("Preparation")) return;

		// Try to parse boss kill count
		parseBossKillCount(message);
		
		// Try to parse time/duration
		parseTime(message);
	}
	
	/**
	 * Called on game tick to check if we have complete data to send
	 */
	public void onTick() {
		if (pendingBoss != null && pendingCount != null) {
			// We have boss name and count - send notification
			sendKillCountNotification();
			reset();
		} else if (pendingTime != null || pendingBoss != null || pendingCount != null) {
			// We have partial data - wait a bit for the rest
			badTicks++;
			if (badTicks > MAX_BAD_TICKS) {
				// Timeout - reset and give up
				reset();
			}
		}
	}

	private void parseBossKillCount(String message) {
		// Try primary pattern first
		Matcher primary = PRIMARY_REGEX.matcher(message);
		if (primary.find()) {
			String rawBoss = primary.group("key");
			String type = primary.group("type");
			String countStr = primary.group("value").replace(",", "");
			
			String boss = normalizeBossName(rawBoss, type);
			if (boss != null) {
				try {
					int count = Integer.parseInt(countStr);
					pendingBoss = boss;
					pendingCount = count;
					badTicks = 0; // Reset bad tick counter
				} catch (NumberFormatException e) {
					log.debug("Failed to parse kill count: {}", countStr);
				}
			}
			return;
		}
		
		// Try secondary pattern (raids)
		Matcher secondary = SECONDARY_REGEX.matcher(message);
		if (secondary.find()) {
			String rawBoss = secondary.group("key");
			String countStr = secondary.group("value").replace(",", "");
			
			String boss = normalizeRaidName(rawBoss);
			if (boss != null) {
				try {
					int count = Integer.parseInt(countStr);
					pendingBoss = boss;
					pendingCount = count;
					badTicks = 0;
				} catch (NumberFormatException e) {
					log.debug("Failed to parse kill count: {}", countStr);
				}
			}
		}
	}
	
	private void parseTime(String message) {
		// TOB special case: skip wave duration, get challenge time
		String msg = message;
		if (message.startsWith("Wave")) {
			int tobIndex = message.indexOf("Theatre of Blood");
			if (tobIndex > 0) {
				msg = message.substring(tobIndex);
				if (pendingBoss == null) {
					pendingBoss = "Theatre of Blood";
				}
				log.debug("TOB wave message detected - extracted: {}", msg);
			}
		}
		
		Matcher matcher = TIME_REGEX.matcher(msg);
		if (matcher.find()) {
			String timeStr = matcher.group("time");
			String pbTimeStr = matcher.group("pbtime");
			
			log.debug("Time pattern matched - Time: {}, PB Time: {}", timeStr, pbTimeStr);
			
			Duration duration = parseTimeString(timeStr);
			
			if (duration != null) {
				pendingTime = duration;
				pendingIsPb = msg.toLowerCase().contains("(new personal best)") || 
				              msg.toLowerCase().contains("new personal best");
				badTicks = 0;
				log.debug("Time parsed - Duration: {}, isPB: {}", duration, pendingIsPb);
			} else {
				log.debug("Failed to parse time string: {}", timeStr);
			}
		}
	}
	
	/**
	 * Normalize boss names based on message type
	 */
	private String normalizeBossName(String boss, String type) {
		if (boss == null) return null;
		
		switch (type.toLowerCase()) {
			case "chest":
				if ("Barrows".equalsIgnoreCase(boss)) return "Barrows";
				if ("Lunar".equals(boss)) return "Lunar Chest";
				return null;
				
			case "completion":
				if ("Gauntlet".equalsIgnoreCase(boss)) return "Crystalline Hunllef";
				if ("Corrupted Gauntlet".equalsIgnoreCase(boss)) return "Corrupted Hunllef";
				return null;
				
			case "harvest":
				if ("Herbiboar".equalsIgnoreCase(boss)) return "Herbiboar";
				return null;
				
			case "kill":
			case "success":
			case "opened":
				return boss;
				
			default:
				return null;
		}
	}
	
	/**
	 * Normalize raid names
	 */
	private String normalizeRaidName(String raid) {
		if (raid == null) return null;
		
		if ("Wintertodt".equalsIgnoreCase(raid)) return "Wintertodt";
		
		// Handle raid modes (e.g., "Theatre of Blood: Entry Mode")
		int modeSeparator = raid.lastIndexOf(':');
		String raidName = modeSeparator > 0 ? raid.substring(0, modeSeparator).trim() : raid;
		
		if (raidName.equalsIgnoreCase("Theatre of Blood") ||
		    raidName.equalsIgnoreCase("Tombs of Amascut") ||
		    raidName.equalsIgnoreCase("Chambers of Xeric") ||
		    raid.equalsIgnoreCase("Chambers of Xeric Challenge Mode")) {
			return raid; // Return full name with mode
		}
		
		return null;
	}
	
	/**
	 * Parse time string like "1:23" or "1:23.45" into Duration
	 */
	private Duration parseTimeString(String timeStr) {
		if (timeStr == null) return null;
		
		try {
			String[] parts = timeStr.split(":");
			if (parts.length == 2) {
				int minutes = Integer.parseInt(parts[0]);
				double seconds = Double.parseDouble(parts[1]);
				long totalMillis = (minutes * 60 * 1000L) + (long)(seconds * 1000);
				return Duration.ofMillis(totalMillis);
			} else if (parts.length == 3) {
				int hours = Integer.parseInt(parts[0]);
				int minutes = Integer.parseInt(parts[1]);
				double seconds = Double.parseDouble(parts[2]);
				long totalMillis = (hours * 3600 * 1000L) + (minutes * 60 * 1000L) + (long)(seconds * 1000);
				return Duration.ofMillis(totalMillis);
			}
		} catch (NumberFormatException e) {
			log.debug("Failed to parse time: {}", timeStr);
		}
		
		return null;
	}
	
	private void sendKillCountNotification() {
		log.info("Sending KC notification - Boss: {}, Count: {}, Time: {}, isPB: {}", 
			pendingBoss, pendingCount, pendingTime, pendingIsPb);
		
		Map<String, Object> kcData = new HashMap<>();
		kcData.put("boss", pendingBoss);
		kcData.put("killCount", pendingCount);
		
		if (pendingTime != null) {
			kcData.put("time", formatDuration(pendingTime));
			kcData.put("timeSeconds", pendingTime.getSeconds() + (pendingTime.getNano() / 1_000_000_000.0));
			log.debug("Including time data - formatted: {}, seconds: {}", 
				formatDuration(pendingTime), pendingTime.getSeconds() + (pendingTime.getNano() / 1_000_000_000.0));
		}
		
		if (pendingIsPb) {
			kcData.put("personalBest", true);
			log.debug("Marking as personal best");
		}

		sendNotification(kcData);
	}
	
	/**
	 * Format duration as MM:SS or HH:MM:SS
	 */
	private String formatDuration(Duration duration) {
		long totalSeconds = duration.getSeconds();
		long hours = totalSeconds / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;
		long millis = duration.getNano() / 1_000_000;
		
		if (hours > 0) {
			return String.format("%d:%02d:%02d.%d", hours, minutes, seconds, millis / 100);
		} else {
			return String.format("%d:%02d.%d", minutes, seconds, millis / 100);
		}
	}

	public void reset() {
		pendingBoss = null;
		pendingCount = null;
		pendingTime = null;
		pendingIsPb = false;
		badTicks = 0;
	}
}

