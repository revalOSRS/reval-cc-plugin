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
	 */
	private static final Pattern CLAN_REGEX = Pattern.compile(
		"(?<user>[^:]+?) (?:has a funny feeling like .+? (?:followed|being followed)|feels something weird sneaking into .+? backpack|feels like .+? acquired something special): (?<pet>.+?)(?: at (?<milestone>[^.]+?))?(?:\\.|$)",
		Pattern.CASE_INSENSITIVE
	);

	/**
	 * Maximum number of ticks to wait for pet name before sending notification without it
	 */
	private static final int MAX_TICKS_WAIT = 5;

	/**
	 * Waiting state marker - indicates we've seen the initial pet message but not the name yet
	 */
	private static final String WAITING_FOR_NAME = "";

	@Inject private RevalClanConfig config;

	/**
	 * Current pet name (null = no pet, WAITING_FOR_NAME = waiting for name, actual name = ready to notify)
	 */
	private volatile String petName = null;

	/**
	 * Original pet message
	 */
	private volatile String originalMessage = null;

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

		// Only process initial pet message if we haven't seen one yet
		if (petName == null) {
			Matcher petMatcher = PET_PATTERN.matcher(message);
			if (petMatcher.find()) {
				// Mark as waiting - we've seen the initial message, now wait for pet name
				this.petName = WAITING_FOR_NAME;
				this.originalMessage = message;
				this.ticksWaited.set(0);
				log.debug("Pet drop detected, waiting for pet name...");
				return;
			}
		} 
		// If we're waiting for pet name, check for follow-up messages
		else if (WAITING_FOR_NAME.equals(petName)) {
			// Check for untradeable drop message
			Matcher untradeableMatcher = UNTRADEABLE_PATTERN.matcher(message);
			if (untradeableMatcher.find()) {
				String itemName = untradeableMatcher.group(1).trim();
				if (isPetItem(itemName)) {
					this.petName = itemName;
					log.debug("Pet name identified from untradeable drop: {}", itemName);
					return;
				}
			}

			// Check for collection log message as a backup
			Matcher collectionMatcher = COLLECTION_LOG_PATTERN.matcher(message);
			if (collectionMatcher.find()) {
				String itemName = collectionMatcher.group(1).trim();
				if (isPetItem(itemName)) {
					this.petName = itemName;
					log.debug("Pet name identified from collection log: {}", itemName);
					return;
				}
			}
		}
	}

	/**
	 * Handle clan notifications - only process if we've already seen an initial pet message
	 * This prevents clan messages from triggering notifications on their own
	 */
	public void onClanNotification(String message) {
		if (!isEnabled()) return;

		if (petName == null) {
			return;
		}

		Matcher clanMatcher = CLAN_REGEX.matcher(message);
		if (clanMatcher.find()) {
			String user = clanMatcher.group("user").trim();
			String playerName = getPlayerName();
			
			if (user.equalsIgnoreCase(playerName)) {
				String pet = null;
				String milestone = null;
				
				try {
					pet = clanMatcher.group("pet");
					milestone = clanMatcher.group("milestone");
				} catch (IllegalArgumentException e) {
					log.warn("Error extracting groups from clan pet notification: {}", e.getMessage());
				}
				
				if (pet != null && !pet.trim().isEmpty()) {
					this.petName = pet.trim();
					if (milestone != null && !milestone.trim().isEmpty()) {
						this.killCount = milestone.trim().replaceAll("\\.$", "");
					}
					this.originalMessage = message;
				}
			}
		}
	}

	/**
	 * Called on each game tick - checks if we should send notification
	 */
	public void onGameTick() {
		if (petName == null) return;

		// If we have a pet name (not waiting), send notification immediately
		if (!WAITING_FOR_NAME.equals(petName)) {
			handleNotify();
			reset();
			return;
		}

		// If we're still waiting, increment wait counter
		int ticks = ticksWaited.incrementAndGet();
		if (ticks > MAX_TICKS_WAIT) {
			// Timeout - send notification without pet name
			log.warn("Pet drop detected but pet name not found in follow-up messages");
			handleNotify();
			reset();
		}
	}

	/**
	 * Send the pet notification
	 */
	private void handleNotify() {
		if (!isEnabled()) return;

		boolean obtained = originalMessage != null && !originalMessage.contains("would have been");

		Map<String, Object> petData = new HashMap<>();
		petData.put("message", originalMessage);
		petData.put("obtained", obtained);

		// Add pet name if we have it
		if (petName != null && !WAITING_FOR_NAME.equals(petName)) {
			petData.put("petName", petName);
		}

		// Add kill count if available
		if (killCount != null && !killCount.isEmpty()) {
			petData.put("killCount", killCount);
		}

		sendNotification(petData);
	}

	/**
	 * Reset the notifier state
	 */
	public void reset() {
		this.petName = null;
		this.originalMessage = null;
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

}

