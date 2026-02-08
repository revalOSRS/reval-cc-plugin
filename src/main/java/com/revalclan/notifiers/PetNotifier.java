package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class PetNotifier extends BaseNotifier {
	/**
	 * Pattern matching the initial pet drop message
	 */
	private static final Pattern PET_PATTERN = Pattern.compile(
		"You (?:have a funny feeling like you(?:'re being followed| would have been followed)|feel something weird sneaking into your backpack)\\.?",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern matching untradeable drop messages (e.g., "Untradeable drop: Pet zilyana")
	 */
	private static final Pattern UNTRADEABLE_PATTERN = Pattern.compile(
		"Untradeable drop: (.+)",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern matching collection log messages (e.g., "New item added to your collection log: Pet zilyana")
	 */
	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (.+)",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * Pattern matching clan pet notifications
	 * Matches formats like:
	 * - "Username has a funny feeling like she would have been followed: Pet name at 114 completions."
	 * - "Username has a funny feeling like she's being followed: Pet name at 50 kills."
	 * - "Username feels something weird sneaking into her backpack: Pet name at 100 kills."
	 * Note: Handles icons/special characters before username (e.g., Ironman helmet icons)
	 */
	private static final Pattern CLAN_REGEX = Pattern.compile(
		"(?:[^\\w\\s]*)?(?<user>[\\w\\s]+?) (?:has a funny feeling like .+? (?:would have been followed|being followed)|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (?<pet>.+?)(?: at (?<milestone>.+))?\\.$",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * Maximum number of ticks to wait for pet name before sending notification without it
	 */
	private static final int MAX_TICKS_WAIT = 10;

	@Inject private RevalClanConfig config;

	/**
	 * Whether we've seen the personal "You have a funny feeling" game message
	 */
	private volatile boolean seenGameMessage = false;

	/**
	 * The personal game message content
	 */
	private volatile String gameMessage = null;

	/**
	 * Pet name extracted from follow-up messages
	 */
	private volatile String petName = null;

	/**
	 * Kill count information (e.g., "50 kills")
	 */
	private volatile String killCount = null;

	/**
	 * Number of ticks waited for pet name
	 */
	private final AtomicInteger ticksWaited = new AtomicInteger(0);

	@Override
	public boolean isEnabled() {
		return config.notifyPet() && filterManager.getFilters().isPetEnabled();
	}

	@Override
	protected String getEventType() {
		return "PET";
	}

	/**
	 * Handle chat messages - looks for initial pet message and follow-up messages with pet name
	 */
	public void onChatMessage(String message) {
		if (!isEnabled()) return;

		// Check for the personal pet drop message
		Matcher petMatcher = PET_PATTERN.matcher(message);
		if (petMatcher.find()) {
			this.seenGameMessage = true;
			this.gameMessage = message;
			this.ticksWaited.set(0);
			return;
		}

		// If we've seen a pet message, look for follow-up messages with pet name
		if (this.seenGameMessage && this.petName == null) {
			// Check for untradeable drop message (new pets only, not duplicates)
			Matcher untradeableMatcher = UNTRADEABLE_PATTERN.matcher(message);
			if (untradeableMatcher.find()) {
				String itemName = untradeableMatcher.group(1).trim();
				if (isPetItem(itemName)) {
					this.petName = itemName;
					return;
				}
			}

			// Check for collection log message (new pets only, not duplicates)
			Matcher collectionMatcher = COLLECTION_LOG_PATTERN.matcher(message);
			if (collectionMatcher.find()) {
				String itemName = collectionMatcher.group(1).trim();
				if (isPetItem(itemName)) {
					this.petName = itemName;
					return;
				}
			}
		}
	}

	/**
	 * Handle clan notifications - extracts pet name and kill count for the current player
	 * This is especially important for duplicate pets where there's no "Untradeable drop:" message
	 */
	public void onClanNotification(String message) {
		if (!isEnabled()) return;

		Matcher clanMatcher = CLAN_REGEX.matcher(message);
		if (clanMatcher.find()) {
			String user = cleanUsername(clanMatcher.group("user"));
			String playerName = getPlayerName();
			
			if (user.equalsIgnoreCase(playerName)) {
				String pet = null;
				String milestone = null;
				
				try {
					pet = clanMatcher.group("pet");
					milestone = clanMatcher.group("milestone");
				} catch (IllegalArgumentException e) {
					return;
				}

				if (pet != null && !pet.trim().isEmpty()) {
					this.petName = pet.trim();
				}
				if (milestone != null && !milestone.trim().isEmpty()) {
					this.killCount = milestone.trim();
				}

				// If we haven't seen the game message yet, mark that we've seen clan info
				// The game message should arrive soon (or may have already arrived)
				if (!seenGameMessage) {
					this.ticksWaited.set(0);
				}
			}
		}
	}

	/**
	 * Called on each game tick - checks if we should send notification
	 */
	public void onGameTick() {
		if (!this.seenGameMessage && this.petName == null) {
			return;
		}

		// We need the personal game message to confirm it's actually a pet drop
		// But we may have gotten clan info first, so wait for game message
		if (!this.seenGameMessage) {
			int ticks = this.ticksWaited.incrementAndGet();
			if (ticks > MAX_TICKS_WAIT) {
				// Clan info arrived but no game message - this wasn't our pet
				reset();
			}
			return;
		}

		// We have the game message - check if we have complete info or should wait
		if (this.petName != null) {
			handleNotify();
			reset();
			return;
		}

		// Still waiting for pet name
		int ticks = this.ticksWaited.incrementAndGet();
		if (ticks > MAX_TICKS_WAIT) {
			// Timeout - send notification without pet name
			handleNotify();
			reset();
		}
	}

	/**
	 * Send the pet notification
	 */
	private void handleNotify() {
		if (!isEnabled()) return;

		// Determine if pet was obtained or duplicate based on game message
		boolean obtained = this.gameMessage != null && !this.gameMessage.contains("would have been");

		Map<String, Object> petData = new HashMap<>();
		petData.put("message", this.gameMessage);
		petData.put("obtained", obtained);

		// Add pet name if we have it
		if (this.petName != null && !this.petName.isEmpty()) {
			petData.put("petName", this.petName);
		}

		// Add kill count if available
		if (this.killCount != null && !this.killCount.isEmpty()) {
			petData.put("killCount", this.killCount);
		}

		sendNotification(petData);
	}

	/**
	 * Reset the notifier state
	 */
	public void reset() {
		this.seenGameMessage = false;
		this.gameMessage = null;
		this.petName = null;
		this.killCount = null;
		this.ticksWaited.set(0);
	}

	/**
	 * Set of all known pet names (case-insensitive matching)
	 */
	private static final Set<String> PET_NAMES = new HashSet<>();

	static {
		// Boss pets
		PET_NAMES.add("abyssal orphan");
		PET_NAMES.add("baby mole");
		PET_NAMES.add("baron");
		PET_NAMES.add("bran");
		PET_NAMES.add("butch");
		PET_NAMES.add("callisto cub");
		PET_NAMES.add("dom");
		PET_NAMES.add("gull");
		PET_NAMES.add("hellpuppy");
		PET_NAMES.add("huberte");
		PET_NAMES.add("ikkle hydra");
		PET_NAMES.add("jal-nib-rek");
		PET_NAMES.add("kalphite princess");
		PET_NAMES.add("lil' zik");
		PET_NAMES.add("lil'viathan");
		PET_NAMES.add("little nightmare");
		PET_NAMES.add("moxi");
		PET_NAMES.add("muphin");
		PET_NAMES.add("nexling");
		PET_NAMES.add("nid");
		PET_NAMES.add("noon");
		PET_NAMES.add("olmlet");
		PET_NAMES.add("pet chaos elemental");
		PET_NAMES.add("pet dagannoth prime");
		PET_NAMES.add("pet dagannoth rex");
		PET_NAMES.add("pet dagannoth supreme");
		PET_NAMES.add("pet dark core");
		PET_NAMES.add("pet general graardor");
		PET_NAMES.add("pet k'ril tsutsaroth");
		PET_NAMES.add("pet kraken");
		PET_NAMES.add("pet kree'arra");
		PET_NAMES.add("pet smoke devil");
		PET_NAMES.add("pet snakeling");
		PET_NAMES.add("pet zilyana");
		PET_NAMES.add("phoenix");
		PET_NAMES.add("prince black dragon");
		PET_NAMES.add("scorpia's offspring");
		PET_NAMES.add("scurry");
		PET_NAMES.add("skotos");
		PET_NAMES.add("smolcano");
		PET_NAMES.add("smol heredit");
		PET_NAMES.add("sraracha");
		PET_NAMES.add("tiny tempor");
		PET_NAMES.add("tumeken's guardian");
		PET_NAMES.add("tzrek-jad");
		PET_NAMES.add("venenatis spiderling");
		PET_NAMES.add("vet'ion jr.");
		PET_NAMES.add("vorki");
		PET_NAMES.add("wisp");
		PET_NAMES.add("yami");
		PET_NAMES.add("youngllef");

		// Skill pets
		PET_NAMES.add("baby chinchompa");
		PET_NAMES.add("beaver");
		PET_NAMES.add("giant squirrel");
		PET_NAMES.add("heron");
		PET_NAMES.add("rift guardian");
		PET_NAMES.add("rock golem");
		PET_NAMES.add("rocky");
		PET_NAMES.add("soup");
		PET_NAMES.add("tangleroot");

		// Other pets
		PET_NAMES.add("abyssal protector");
		PET_NAMES.add("bloodhound");
		PET_NAMES.add("chompy chick");
		PET_NAMES.add("herbi");
		PET_NAMES.add("lil' creator");
		PET_NAMES.add("pet penance queen");
		PET_NAMES.add("quetzin");
	}

	/**
	 * Check if an item name is a known pet
	 */
	private boolean isPetItem(String itemName) {
		if (itemName == null || itemName.isEmpty()) return false;

		// Normalize the item name (lowercase, trim)
		String normalized = itemName.toLowerCase().trim();

		// Direct match
		if (PET_NAMES.contains(normalized)) {
			return true;
		}

		// Check if it starts with "pet " and the rest matches
		if (normalized.startsWith("pet ")) {
			String withoutPrefix = normalized.substring(4).trim();
			return PET_NAMES.contains(withoutPrefix) || PET_NAMES.contains(normalized);
		}

		return false;
	}

	/**
	 * Clean username by removing icons, special characters, and extra whitespace
	 * This handles cases where Ironman icons or other special characters appear before the username
	 */
	private String cleanUsername(String username) {
		if (username == null) return "";
		
		// Remove common icon/special characters that might appear before usernames
		// This includes various Unicode symbols, emojis, and control characters
		String cleaned = username
			.replaceAll("^[^\\w\\s]+", "") // Remove leading non-word/non-space characters
			.replaceAll("[\\u0000-\\u001F\\u007F-\\u009F]", "") // Remove control characters
			.replaceAll("[\\u2000-\\u206F]", "") // Remove general punctuation
			.replaceAll("[\\u20A0-\\u20CF]", "") // Remove currency symbols
			.replaceAll("[\\u2190-\\u21FF]", "") // Remove arrows
			.replaceAll("[\\u2600-\\u26FF]", "") // Remove miscellaneous symbols (includes some icons)
			.replaceAll("[\\u2700-\\u27BF]", "") // Remove dingbats
			.trim();
		
		return cleaned;
	}
}
