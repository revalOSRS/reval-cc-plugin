package com.revalclan.notifiers;

import com.revalclan.collectionlog.GreenlogCounterSnapshot;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class PetNotifier extends BaseNotifier {
	@Inject private GreenlogCounterSnapshot greenlogCounterSnapshot;

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
	 * Pattern matching clan pet notifications
	 * Matches formats like:
	 * - "Username has a funny feeling like she would have been followed: Pet name at 114 completions."
	 * - "Username has a funny feeling like she's being followed: Pet name at 50 kills."
	 * - "Username feels something weird sneaking into her backpack: Pet name at 100 kills."
	 * - "Username feels like he's acquired something special: Abyssal orphan"
	 * Sentence punctuation is optional; periods belonging to a pet name are preserved.
	 */
	private static final Pattern CLAN_REGEX = Pattern.compile(
		"^(?<user>[\\w -]+?) (?:has a funny feeling like .+? (?:would have been followed|being followed)|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (?<pet>.+?)(?: at (?<milestone>.+?))?$",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern CLAN_COLLECTION_LOG_PATTERN = Pattern.compile(
		"^(?<user>[\\w -]+?) received a new collection log item: (?<item>.+?)(?: \\(\\d+/\\d+\\))?\\.?$",
		Pattern.CASE_INSENSITIVE
	);

	private static final Pattern PET_SOURCE_SUFFIX = Pattern.compile("\\s+from\\s+.+$", Pattern.CASE_INSENSITIVE);

	/**
	 * Maximum number of ticks to wait for pet name before sending notification without it
	 */
	private static final int MAX_TICKS_WAIT = 10;

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

	/**
	 * Event data started on the first tick after the personal message, with the
	 * game counters read then (e.g. Rifts closed), not when the name arrives
	 */
	private volatile Map<String, Object> pendingData = null;

	@Override
	public boolean isEnabled() {
		return filterManager.getFilters().isPetEnabled();
	}

	@Override
	protected String getEventType() {
		return "PET";
	}

	/**
	 * Handle plain game messages from RevalClanPlugin: personal confirmation and item names.
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

		Matcher untradeableMatcher = UNTRADEABLE_PATTERN.matcher(message);
		if (untradeableMatcher.find()) offerItemName(untradeableMatcher.group(1));
		Matcher collectionMatcher = COLLECTION_LOG_PATTERN.matcher(message);
		if (collectionMatcher.find()) offerItemName(collectionMatcher.group("item"));
	}

	/**
	 * Handle plain system clan broadcasts from RevalClanPlugin for the current player.
	 * This is especially important for duplicate pets where there's no "Untradeable drop:" message
	 */
	public void onClanNotification(String message) {
		if (!isEnabled()) return;

		Matcher collectionMatcher = CLAN_COLLECTION_LOG_PATTERN.matcher(message);
		if (collectionMatcher.matches()) {
			if (isLocalPlayer(collectionMatcher.group("user"))) offerItemName(collectionMatcher.group("item"));
			return;
		}

		Matcher clanMatcher = CLAN_REGEX.matcher(message);
		if (!clanMatcher.matches() || !isLocalPlayer(clanMatcher.group("user"))) return;
		this.petName = broadcastPetName(clanMatcher.group("pet"));
		String milestone = clanMatcher.group("milestone");
		if (milestone != null && !milestone.trim().isEmpty()) {
			milestone = milestone.trim();
			this.killCount = milestone.endsWith(".")
				? milestone.substring(0, milestone.length() - 1) : milestone;
		}

		// Clan info can arrive before the personal confirmation.
		if (!seenGameMessage) {
			this.ticksWaited.set(0);
		}
	}

	/** Item messages can only name a pending personal pet drop. */
	private void offerItemName(String item) {
		String name = item.trim();
		if (seenGameMessage && petName == null && isPetItem(name)) petName = name;
	}

	/** Trust system broadcasts; the catalogue only disambiguates a pet's own final period. */
	private String broadcastPetName(String rawName) {
		String name = PET_SOURCE_SUFFIX.matcher(rawName.trim()).replaceFirst("");
		return isPetItem(name) || !name.endsWith(".") ? name : name.substring(0, name.length() - 1).trim();
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

		// First tick after the game message: the drop tick's varp and XP updates are applied
		if (this.pendingData == null) {
			this.pendingData = new HashMap<>();
			greenlogCounterSnapshot.attachTo(this.pendingData, null);
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

		// Started by onGameTick on the first tick after the game message
		Map<String, Object> petData = this.pendingData;
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
		this.pendingData = null;
		this.ticksWaited.set(0);
	}

	/**
	 * Set of all known pet names (case-insensitive matching)
	 */
	private static final Set<String> PET_NAMES = Set.of(
		// Boss pets
		"abyssal orphan",
		"aggy",
		"baby mole",
		"baron",
		"beef",
		"bran",
		"butch",
		"callisto cub",
		"dom",
		"gull",
		"hellpuppy",
		"huberte",
		"ikkle hydra",
		"jal-nib-rek",
		"kalphite princess",
		"lil' zik",
		"lil'viathan",
		"little nightmare",
		"maggot marquess",
		"moxi",
		"mr mcgroot",
		"muphin",
		"nexling",
		"nid",
		"noon",
		"olmlet",
		"orion",
		"pet chaos elemental",
		"pet dagannoth prime",
		"pet dagannoth rex",
		"pet dagannoth supreme",
		"pet dark core",
		"pet general graardor",
		"pet k'ril tsutsaroth",
		"pet kraken",
		"pet kree'arra",
		"pet smoke devil",
		"pet snakeling",
		"pet zilyana",
		"phoenix",
		"prince black dragon",
		"scorpia's offspring",
		"scurry",
		"skotos",
		"smolcano",
		"smol heredit",
		"sraracha",
		"tiny tempor",
		"tumeken's guardian",
		"tzrek-jad",
		"venenatis spiderling",
		"vet'ion jr.",
		"vorki",
		"wisp",
		"yami",
		"youngllef",
		// Skill pets
		"baby chinchompa",
		"beaver",
		"giant squirrel",
		"heron",
		"rift guardian",
		"rock golem",
		"rocky",
		"soup",
		"tangleroot",
		// Other pets
		"abyssal protector",
		"bloodhound",
		"chompy chick",
		"herbi",
		"lil' creator",
		"pet penance queen",
		"quetzin"
	);

	/**
	 * Check if an item name is a known pet
	 */
	private boolean isPetItem(String itemName) {
		if (itemName == null || itemName.isEmpty()) return false;

		// Normalize the item name (lowercase, trim)
		String normalized = itemName.toLowerCase().trim();

		// Direct match, or "pet " followed by a known name
		return PET_NAMES.contains(normalized)
			|| (normalized.startsWith("pet ") && PET_NAMES.contains(normalized.substring(4).trim()));
	}

	private boolean isLocalPlayer(String name) {
		String playerName = getPlayerName();
		return playerName != null && Text.toJagexName(name).equalsIgnoreCase(Text.toJagexName(playerName));
	}
}
