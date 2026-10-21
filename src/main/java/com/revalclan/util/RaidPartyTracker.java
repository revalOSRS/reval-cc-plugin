package com.revalclan.util;

import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The party behind a raid's loot and completion count, with
 * each member marked as a Reval member or a guest.
 *
 * The party is read live and frozen, with known per-member deaths, at completion.
 * ToB/ToA rewards claimed later outside the raid keep that snapshot until an
 * observation gap. Final boss deaths also save a names-only fallback.
 * Call on the client thread.
 */
@Singleton
public class RaidPartyTracker {
	static final String CHAMBERS_OF_XERIC = "Chambers of Xeric";
	static final String THEATRE_OF_BLOOD = "Theatre of Blood";
	static final String TOMBS_OF_AMASCUT = "Tombs of Amascut";
	public static final String FRACTURED_ARCHIVE = "The Fractured Archive";

	/** First VarClientStr holding a ToB party member name (5 consecutive slots). */
	private static final int TOB_MEMBER_NAME_VARC = 330;
	private static final int TOB_PARTY_MAX_SIZE = 5;

	/** First VarClientStr holding a ToA party member name (8 consecutive slots). */
	private static final int TOA_MEMBER_NAME_VARC = 1099;
	private static final int TOA_PARTY_MAX_SIZE = 8;
	private static final int ARCHIVE_PARTY_MAX_SIZE = 8;
	private static final Pattern ARCHIVE_COMPLETED = Pattern.compile(
		"^Your completed (?:The )?Fractured Archive count is: ([\\d,]+)\\.?$", Pattern.CASE_INSENSITIVE);

	/** A saved party applies to rewards claimed up to this long after the final boss died. */
	static final long SAVED_PARTY_TTL_MS = 10 * 60_000L;

	/** Where a party was read. */
	public enum Origin { LIVE, FINAL_BOSS, COMPLETION }

	private static final Pattern COMPLETED = Pattern.compile(
		"^Your completed (Chambers of Xeric(?: Challenge Mode)?|Theatre of Blood(?::[^:]*)?|Tombs of Amascut(?::[^:]*)?) count is:");

	@Inject private Client client;
	@Inject private RaidDeathTracker deathTracker;
	private final Map<String, Party> completedParties = new LinkedHashMap<>();

	private Party savedParty;
	private Party pendingCompletion;
	private long savedAtMs;
	private final Map<String, String> archiveNames = new LinkedHashMap<>();
	private Party archiveParty;
	private int archiveMaxSize;
	private int archiveVerifiedSize;
	private int archiveCompletionCount = -1;
	private boolean archiveInside;
	private boolean archiveCompleted;
	private boolean archiveRosterOverflow;
	private long archiveLastActiveAt;

	/** Save the party when a raid's final boss dies. Not gated on membership: it only records. */
	public void onActorDeath(ActorDeath event) {
		deathTracker.onActorDeath(event);
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;
		String raid = raidForFinalBoss(actor.getName());
		if (raid == null) return;

		Party party = readParty(raid, Origin.FINAL_BOSS);
		if (party != null) {
			// An NPC name can be a Warden's earlier phase. Only the completion
			// message freezes death counts; this fallback saves names alone.
			savedParty = party.withDeaths(Map.of());
			savedAtMs = System.currentTimeMillis();
		}
	}

	/**
	 * The party for a raid loot or completion source, possibly with a mode suffix
	 * ("Theatre of Blood: Hard Mode"). Null when the source is not a raid or no
	 * party is known.
	 */
	public Party partyFor(String source) {
		String raid = raidOf(source);
		if (raid == null) return null;
		if (FRACTURED_ARCHIVE.equals(raid)) {
			observeArchive();
			return archiveParty;
		}
		Party completed = completedParties.get(raid);
		if (completed != null && deathTracker.isCompleted(raid)) return completed;

		boolean insideRaid = deathTracker.isInside(raid);
		Party live = readParty(raid, Origin.LIVE);
		// Keep each raid's completed reward across scene changes and long breaks
		// in this login. An observation gap clears the cache: an offline raid
		// could otherwise leave us annotating a new reward with an old count.
		if (!insideRaid) {
			if (completed != null) return completed;
			if (live != null) live = live.withDeaths(Map.of());
		}
		return choose(raid, live, insideRaid, savedParty,
			System.currentTimeMillis() - savedAtMs);
	}

	/** Observe game messages before notification settings or clan validation can filter them. */
	public void onChatMessage(ChatMessageType type, String rawMessage) {
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& type != ChatMessageType.SPAM && type != ChatMessageType.ENGINE) return;
		String message = sanitize(rawMessage);
		Matcher archiveCompletion = ARCHIVE_COMPLETED.matcher(message);
		if (archiveCompletion.matches()) {
			observeArchive();
			completeArchive(archiveCompletion.group(1));
			return;
		}
		if (deathTracker.onChatMessage(message)) {
			savedParty = null;
			savedAtMs = 0;
		}
		String raid = null;
		Matcher completed = COMPLETED.matcher(message);
		if (completed.find()) raid = raidOf(completed.group(1));
		if (message.startsWith("Congratulations - your raid is complete!")) raid = CHAMBERS_OF_XERIC;
		if (message.startsWith("Challenge complete: The Wardens.")) raid = TOMBS_OF_AMASCUT;
		if (raid == null) return;
		if (deathTracker.isCompleted(raid) && completedParties.containsKey(raid)) return;

		Party party = readParty(raid, Origin.COMPLETION);
		if (party == null && savedParty != null && savedParty.raid.equals(raid)
			&& System.currentTimeMillis() - savedAtMs <= SAVED_PARTY_TTL_MS) {
			party = new Party(raid, Origin.COMPLETION, savedParty.names, savedParty.revalMembers,
				savedParty.deaths).withDeaths(deathTracker.countsFor(raid, savedParty.names));
		}
		deathTracker.complete(raid);
		if (party == null) {
			completedParties.remove(raid);
			if (savedParty != null && savedParty.raid.equals(raid)) savedParty = null;
		} else {
			savedParty = party;
			savedAtMs = System.currentTimeMillis();
			completedParties.put(raid, party);
			pendingCompletion = party;
		}
	}

	public void onGameTick() {
		observeArchive();
		// Finish the server tick before freezing: a final death and completion
		// can arrive in either order. Buffered loot/KC retain this same snapshot.
		if (pendingCompletion != null) {
			if (deathTracker.isCompleted(pendingCompletion.raid)) {
				pendingCompletion.updateDeaths(deathTracker.countsFor(pendingCompletion.raid, pendingCompletion.names));
			}
			pendingCompletion = null;
		}
		deathTracker.onGameTick();
	}

	public void onGameStateChanged(GameState state) {
		if (state == GameState.CONNECTION_LOST || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN) reset();
	}

	/** Forget observation after logout/plugin stop; a later unmatched reward has unknown counts. */
	public void reset() {
		savedParty = null;
		pendingCompletion = null;
		savedAtMs = 0;
		deathTracker.reset();
		completedParties.clear();
		clearArchiveParty();
		archiveInside = false;
		archiveCompletionCount = -1;
	}

	/** TFA's native party slots survive room changes; nearby players are never roster evidence. */
	private void observeArchive() {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		int status = client.getVarbitValue(VarbitID.TFA_CLIENT_PARTYSTATUS);
		WorldView world = client.getTopLevelWorldView();
		boolean inside = (status == 2 || status == 3) && world != null && world.isInstance();
		long now = System.currentTimeMillis();
		if (inside && !archiveInside) clearArchiveParty();
		archiveInside = inside;
		if (!inside) {
			if (now - archiveLastActiveAt > SAVED_PARTY_TTL_MS) clearArchiveParty();
			return;
		}
		if (archiveCompleted) return;

		int size = 0;
		Set<String> liveNames = new HashSet<>();
		Set<String> occupiedNames = new HashSet<>();
		for (int i = 0; i < ARCHIVE_PARTY_MAX_SIZE; i++) {
			boolean occupied = client.getVarbitValue(VarbitID.TFA_CLIENT_P0 + i) > 0;
			if (occupied) size++;
			String name = sanitize(client.getVarcStrValue(VarClientID.TFA_CLIENT_NAME0 + i));
			String key = nameKey(name);
			if (!name.matches("[a-zA-Z0-9 _-]{1,12}") || key.trim().isEmpty()) continue;
			liveNames.add(key);
			if (occupied) occupiedNames.add(key);
			if (archiveNames.size() < ARCHIVE_PARTY_MAX_SIZE || archiveNames.containsKey(key)) {
				archiveNames.putIfAbsent(key, name);
			} else {
				archiveRosterOverflow = true;
			}
		}
		archiveMaxSize = Math.max(archiveMaxSize, size);
		// Verify names and occupied slots together; stale names in empty slots cannot fill a gap.
		if (size > 0 && occupiedNames.size() == size && occupiedNames.equals(liveNames)) {
			archiveVerifiedSize = Math.max(archiveVerifiedSize, size);
		}
		archiveLastActiveAt = now;
		if (archiveNames.isEmpty()) return;
		Player local = client.getLocalPlayer();
		boolean complete = !archiveRosterOverflow && archiveMaxSize > 0 && archiveVerifiedSize == archiveMaxSize
			&& archiveNames.size() == archiveMaxSize
			&& local != null && archiveNames.containsKey(nameKey(local.getName()));
		archiveParty = Party.of(FRACTURED_ARCHIVE, Origin.LIVE, new ArrayList<>(archiveNames.values()), revalRoster())
			.withRosterEvidence(archiveMaxSize, complete);
	}

	private void completeArchive(String countText) {
		final int count;
		try {
			count = Integer.parseInt(countText.replace(",", ""));
		} catch (NumberFormatException e) {
			return;
		}
		if (count <= archiveCompletionCount) return;
		archiveCompletionCount = count;
		// Without an observed new entry, a second completion must not reuse the previous party.
		if (archiveCompleted) archiveParty = null;
		archiveCompleted = true;
		if (archiveParty != null) {
			archiveParty = archiveParty.withOrigin(Origin.COMPLETION);
			archiveLastActiveAt = System.currentTimeMillis();
		}
	}

	private void clearArchiveParty() {
		archiveNames.clear();
		archiveParty = null;
		archiveMaxSize = 0;
		archiveVerifiedSize = 0;
		archiveRosterOverflow = false;
		archiveCompleted = false;
		archiveLastActiveAt = 0;
	}

	/**
	 * Inside the raid the live party is the truth. Outside it, a party saved at
	 * this raid's final boss within the TTL wins over whatever the interface
	 * still holds.
	 */
	static Party choose(String raid, Party live, boolean insideRaid, Party saved, long savedAgeMs) {
		if (insideRaid && live != null) return live;
		boolean savedApplies = saved != null && saved.raid.equals(raid)
			&& savedAgeMs >= 0 && savedAgeMs <= SAVED_PARTY_TTL_MS;
		return savedApplies ? saved : live;
	}

	/** The raid a loot or completion source belongs to, or null. */
	static String raidOf(String source) {
		if (source == null) return null;
		if (source.startsWith(CHAMBERS_OF_XERIC)) return CHAMBERS_OF_XERIC;
		if (source.startsWith(THEATRE_OF_BLOOD)) return THEATRE_OF_BLOOD;
		if (source.startsWith(TOMBS_OF_AMASCUT)) return TOMBS_OF_AMASCUT;
		if (source.equalsIgnoreCase(FRACTURED_ARCHIVE) || source.equalsIgnoreCase("Fractured Archive")) return FRACTURED_ARCHIVE;
		return null;
	}

	/** The raid whose last boss this NPC is, or null. */
	static String raidForFinalBoss(String npcName) {
		switch (sanitize(npcName).replace('’', '\'')) {
			case "Great Olm":
				return CHAMBERS_OF_XERIC;
			case "Verzik Vitur":
				return THEATRE_OF_BLOOD;
			case "Tumeken's Warden":
			case "Elidinis' Warden":
				return TOMBS_OF_AMASCUT;
			default:
				return null;
		}
	}

	private Party readParty(String raid, Origin origin) {
		List<String> names;
		if (CHAMBERS_OF_XERIC.equals(raid)) {
			names = chambersNames();
		} else if (THEATRE_OF_BLOOD.equals(raid)) {
			names = varcNames(TOB_MEMBER_NAME_VARC, TOB_PARTY_MAX_SIZE);
		} else if (TOMBS_OF_AMASCUT.equals(raid)) {
			names = varcNames(TOA_MEMBER_NAME_VARC, TOA_PARTY_MAX_SIZE);
		} else {
			return null;
		}
		return names.isEmpty() ? null : Party.of(raid, origin, names, revalRoster())
			.withDeaths(deathTracker.countsFor(raid, names));
	}

	/**
	 * CoX lists the whole party in the raid side panel. When that widget is not
	 * loaded, the players in the raid instance stand in for it.
	 */
	private List<String> chambersNames() {
		List<String> names = new ArrayList<>();
		Widget list = client.getWidget(InterfaceID.RaidsSidepanel.LIST);
		Widget[] children = list == null ? null : list.getChildren();
		if (children != null) {
			for (Widget child : children) {
				if (child != null) addName(names, child.getName());
			}
		}
		if (!names.isEmpty()) return names;

		WorldView world = client.getTopLevelWorldView();
		if (world != null && world.isInstance()) {
			for (Player player : world.players()) {
				if (player != null) addName(names, player.getName());
			}
		}
		return names;
	}

	private List<String> varcNames(int initialVarcId, int maxSize) {
		List<String> names = new ArrayList<>(maxSize);
		for (int i = 0; i < maxSize; i++) {
			addName(names, client.getVarcStrValue(initialVarcId + i));
		}
		return names;
	}

	/**
	 * Name keys of every Reval member, or null when the loaded clan settings are
	 * not Reval's. Guests are never in the clan settings, so absence from a
	 * loaded roster means guest.
	 */
	private Set<String> revalRoster() {
		if (ClanValidator.isCheckDisabled()) return null;
		ClanSettings settings = client.getClanSettings();
		if (settings == null || !ClanValidator.REQUIRED_CLAN_NAME.equalsIgnoreCase(settings.getName())) return null;
		List<ClanMember> members = settings.getMembers();
		if (members == null || members.isEmpty()) return null;

		Set<String> keys = new HashSet<>();
		for (ClanMember member : members) {
			if (member != null && member.getName() != null) keys.add(nameKey(member.getName()));
		}
		return keys;
	}

	private static void addName(List<String> names, String raw) {
		String name = sanitize(raw);
		if (name.isEmpty()) return;
		for (String existing : names) {
			if (existing.equalsIgnoreCase(name)) return;
		}
		names.add(name);
	}

	private static String sanitize(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		return Text.removeTags(raw).replace(' ', ' ').trim();
	}

	/** RuneScape treats space, underscore and hyphen in a name as the same character. */
	static String nameKey(String name) {
		return sanitize(name).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
	}

	/** One raid party: its members in party order and whether each is in Reval. */
	public static final class Party {
		final String raid;
		final Origin origin;
		final List<String> names;
		/** Parallel to names; a null entry means the Reval roster was not loaded. */
		final List<Boolean> revalMembers;
		/** Parallel to names; null means the full raid count is unknown. */
		final List<Integer> deaths;
		private final Integer size;
		private final Boolean rosterComplete;

		private Party(String raid, Origin origin, List<String> names, List<Boolean> revalMembers, List<Integer> deaths) {
			this(raid, origin, names, revalMembers, deaths, null, null);
		}

		private Party(String raid, Origin origin, List<String> names, List<Boolean> revalMembers, List<Integer> deaths,
			Integer size, Boolean rosterComplete) {
			this.raid = raid;
			this.origin = origin;
			this.names = names;
			this.revalMembers = revalMembers;
			this.deaths = deaths;
			this.size = size;
			this.rosterComplete = rosterComplete;
		}

		static Party of(String raid, Origin origin, List<String> names, Set<String> revalRoster) {
			List<Boolean> flags = new ArrayList<>(names.size());
			List<Integer> deaths = new ArrayList<>(names.size());
			for (String name : names) {
				flags.add(revalRoster == null ? null : revalRoster.contains(nameKey(name)));
				deaths.add(null);
			}
			return new Party(raid, origin, names, flags, deaths);
		}

		private Party withDeaths(Map<String, Integer> counts) {
			List<Integer> values = new ArrayList<>(names.size());
			for (String name : names) values.add(counts.get(nameKey(name)));
			return new Party(raid, origin, names, revalMembers, values, size, rosterComplete);
		}

		private Party withRosterEvidence(int size, boolean complete) {
			return new Party(raid, origin, names, revalMembers, deaths, size, complete);
		}

		private Party withOrigin(Origin origin) {
			return new Party(raid, origin, names, revalMembers, deaths, size, rosterComplete);
		}

		private void updateDeaths(Map<String, Integer> counts) {
			for (int i = 0; i < names.size(); i++) deaths.set(i, counts.get(nameKey(names.get(i))));
		}

		/**
		 * Adds {@code partyMembers} (the names, as the backend has always received
		 * them) and {@code party}: where the party was read and each member's
		 * {@code clanMember} flag, left out when the Reval roster was not loaded.
		 */
		public void addTo(Map<String, Object> data) {
			data.put("partyMembers", names);

			List<Map<String, Object>> members = new ArrayList<>(names.size());
			for (int i = 0; i < names.size(); i++) {
				Map<String, Object> member = new LinkedHashMap<>();
				member.put("name", names.get(i));
				if (revalMembers.get(i) != null) member.put("clanMember", revalMembers.get(i));
				if (deaths.get(i) != null) member.put("deaths", deaths.get(i));
				members.add(member);
			}

			Map<String, Object> party = new LinkedHashMap<>();
			party.put("origin", origin.name().toLowerCase(Locale.ROOT));
			party.put("members", members);
			if (size != null) party.put("size", size);
			if (rosterComplete != null) party.put("rosterComplete", rosterComplete);
			data.put("party", party);
		}
	}
}
