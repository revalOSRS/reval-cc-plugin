package com.revalclan.util;

import com.revalclan.session.SessionScoped;
import com.revalclan.session.SessionState;

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
 * Owns run boundaries, final-tick freezing and retention for roster and numeric
 * evidence together. ArchiveRoster and RaidMetadata only read native evidence.
 * Classic rewards survive until an observation gap; Archive rewards retain
 * their existing ten-minute lifetime.
 * Call on the client thread.
 */
@Singleton
public class RaidPartyTracker implements SessionScoped {
	@Inject void registerSessionState(SessionState state) { state.register(this); }
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

	/** A saved party applies to rewards claimed up to this long after the final boss died. */
	static final long SAVED_PARTY_TTL_MS = 10 * 60_000L;

	/** Where a party was read. */
	public enum Origin { LIVE, FINAL_BOSS, COMPLETION }

	static final Pattern COMPLETED = Pattern.compile(
		"^Your completed (.+?) count is: ([\\d,]+)\\.?$", Pattern.CASE_INSENSITIVE);

	@Inject private Client client;
	@Inject private RaidDeathTracker deathTracker;
	@Inject private ArchiveRoster archiveRoster;
	private final Map<String, Snapshot> completedSnapshots = new LinkedHashMap<>();
	private Snapshot active;
	private Snapshot pendingCompletion;
	private int startTick = -1;
	private int outsideTicks;
	private Party savedParty;
	private long savedAtMs;

	public void onActorDeath(ActorDeath event) {
		deathTracker.onActorDeath(event);
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;
		String raid = raidForFinalBoss(actor.getName());
		if (raid == null) return;
		Party party = readParty(raid, Origin.FINAL_BOSS);
		if (party != null) {
			// Warden names also occur in earlier phases; this fallback proves names only.
			savedParty = party.withDeaths(Map.of());
			savedAtMs = System.currentTimeMillis();
		}
	}

	/** One snapshot supplies both roster and numeric evidence to buffered KC/loot. */
	public Snapshot snapshotFor(String source) {
		String raid = raidOf(source);
		if (raid == null || externalSpectator(raid)) return null;
		if (active != null && active.raid.equals(raid) && deathTracker.isInside(raid)) {
			if (active.completedAtMs == 0) active.party = readParty(raid, Origin.LIVE);
			return active;
		}
		Snapshot completed = completedSnapshots.get(raid);
		if (completed != null) {
			if (!FRACTURED_ARCHIVE.equals(raid)) return completed;
			if (System.currentTimeMillis() - completed.completedAtMs <= SAVED_PARTY_TTL_MS) {
				if (completed.party != null) completed.party = completed.party.withClanRoster(revalRoster());
				return completed;
			}
			completedSnapshots.remove(raid);
		}
		Party fallback = choose(raid, null, false, savedParty, System.currentTimeMillis() - savedAtMs);
		return fallback == null ? null : new Snapshot(raid, fallback, null);
	}

	/** Observe before notification settings or clan validation can filter messages. */
	public void onChatMessage(ChatMessageType type, String rawMessage) {
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.FRIENDSCHATNOTIFICATION
			&& type != ChatMessageType.SPAM && type != ChatMessageType.ENGINE) return;
		String message = sanitize(rawMessage);
		String started = startedRaid(message);
		if (started != null) {
			begin(started, true);
			return;
		}
		deathTracker.onChatMessage(message);
		Matcher completed = COMPLETED.matcher(message);
		String raid = null;
		String source = null;
		Integer count = null;
		if (completed.matches()) {
			source = completed.group(1);
			raid = raidOf(source);
			try { count = Integer.parseInt(completed.group(2).replace(",", "")); }
			catch (NumberFormatException e) { return; }
		} else if (message.startsWith("Congratulations - your raid is complete!")) raid = CHAMBERS_OF_XERIC;
		else if (message.startsWith("Challenge complete: The Wardens.")) raid = TOMBS_OF_AMASCUT;
		if (raid == null || externalSpectator(raid)) return;
		Snapshot previous = completedSnapshots.get(raid);
		if (count != null && previous != null && previous.metadata.matchesCompletion(source, count)) return;
		if (count == null && previous != null) return;
		if (active == null || !active.raid.equals(raid) || active.metadata.completionCount != null) begin(raid, false);
		if (count != null) active.metadata.complete(source, count);
		observe();
		if (active.party == null || active.completedAtMs == 0) {
			active.party = readParty(raid, Origin.COMPLETION);
			if (active.party == null && savedParty != null && savedParty.raid.equals(raid)
				&& System.currentTimeMillis() - savedAtMs <= SAVED_PARTY_TTL_MS) {
				active.party = new Party(raid, Origin.COMPLETION, savedParty.names, savedParty.revalMembers,
					savedParty.deaths).withDeaths(deathTracker.countsFor(raid, savedParty.names));
			}
		}
		deathTracker.complete(raid);
		active.completedAtMs = System.currentTimeMillis();
		completedSnapshots.put(raid, active);
		pendingCompletion = active;
	}

	private void begin(String raid, boolean observedStart) {
		active = new Snapshot(raid, null, new RaidMetadata(raid));
		completedSnapshots.remove(raid);
		pendingCompletion = null;
		startTick = observedStart ? client.getTickCount() : -1;
		outsideTicks = 0;
		savedParty = null;
		savedAtMs = 0;
		archiveRoster.reset();
		if (observedStart) deathTracker.start(raid);
		else deathTracker.reset();
	}

	public void onGameTick() {
		if (client.getGameState() != GameState.LOGGED_IN) return;
		if (active == null) {
			for (String raid : new String[] {CHAMBERS_OF_XERIC, THEATRE_OF_BLOOD, TOMBS_OF_AMASCUT, FRACTURED_ARCHIVE}) {
				if (deathTracker.isInside(raid) && !externalSpectator(raid) && !completedSnapshots.containsKey(raid)) {
					begin(raid, false);
					break;
				}
			}
		}
		if (active != null) {
			if (deathTracker.isInside(active.raid) && !externalSpectator(active.raid)) {
				outsideTicks = 0;
				if (active.metadata.completionCount == null || pendingCompletion == active) observe();
			} else if (++outsideTicks > 3) {
				active = null;
				startTick = -1;
				deathTracker.reset();
				archiveRoster.reset();
			}
		}
		// Final deaths and numeric updates can precede or follow the completion message.
		if (pendingCompletion != null) {
			Party party = pendingCompletion.party;
			if (party != null && deathTracker.isCompleted(pendingCompletion.raid)) {
				party.updateDeaths(deathTracker.countsFor(pendingCompletion.raid, party.names));
			}
			pendingCompletion = null;
		}
	}

	private void observe() {
		if (!deathTracker.isInside(active.raid) || externalSpectator(active.raid)) return;
		// Native entry fields settle over several ticks; a missed start stays unknown.
		active.metadata.observe(client, startTick >= 0 && active.metadata.completionCount == null && client.getTickCount() - startTick <= 10);
		if (FRACTURED_ARCHIVE.equals(active.raid) && (active.completedAtMs == 0 || pendingCompletion == active)) archiveRoster.observe();
	}

	private boolean externalSpectator(String raid) {
		return FRACTURED_ARCHIVE.equals(raid) && client.getVarbitValue(VarbitID.TFA_CLIENT_SPECTATE_MODE) >= 2;
	}

	private static String startedRaid(String message) {
		if ("The raid has begun!".equals(message)) return CHAMBERS_OF_XERIC;
		if (message.startsWith("You enter the Theatre of Blood")) return THEATRE_OF_BLOOD;
		if (message.startsWith("You enter the Tombs of Amascut")) return TOMBS_OF_AMASCUT;
		return RaidDeathTracker.ARCHIVE_START_MESSAGE.equals(message) ? FRACTURED_ARCHIVE : null;
	}

	public void onGameStateChanged(GameState state) {
		if (state == GameState.CONNECTION_LOST || state == GameState.HOPPING || state == GameState.LOGIN_SCREEN) reset();
	}

	public void reset() {
		active = null;
		pendingCompletion = null;
		startTick = -1;
		outsideTicks = 0;
		savedParty = null;
		savedAtMs = 0;
		completedSnapshots.clear();
		deathTracker.reset();
		archiveRoster.reset();
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
	public static String raidOf(String source) {
		if (source == null) return null;
		String name = sanitize(source).toLowerCase(Locale.ROOT);
		if (name.startsWith(CHAMBERS_OF_XERIC.toLowerCase(Locale.ROOT))) return CHAMBERS_OF_XERIC;
		if (name.startsWith(THEATRE_OF_BLOOD.toLowerCase(Locale.ROOT))) return THEATRE_OF_BLOOD;
		if (name.startsWith(TOMBS_OF_AMASCUT.toLowerCase(Locale.ROOT))) return TOMBS_OF_AMASCUT;
		if (name.equals(FRACTURED_ARCHIVE.toLowerCase(Locale.ROOT)) || name.equals("fractured archive")) return FRACTURED_ARCHIVE;
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
		if (FRACTURED_ARCHIVE.equals(raid)) return archiveRoster.party(origin, revalRoster());
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
		ClanSettings settings = client.getClanSettings();
		if (settings == null || !ClanMembership.CLAN_NAME.equalsIgnoreCase(settings.getName())) return null;
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

	static String sanitize(String raw) {
		if (raw == null || raw.isEmpty()) return "";
		return Text.removeTags(raw).replace(' ', ' ').trim();
	}

	/** RuneScape treats space, underscore and hyphen in a name as the same character. */
	static String nameKey(String name) {
		return sanitize(name).toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
	}

	/** Shared by buffered events so the final tick reaches both metadata and deaths. */
	public static final class Snapshot {
		private final String raid;
		private Party party;
		private final RaidMetadata metadata;
		private long completedAtMs;

		private Snapshot(String raid, Party party, RaidMetadata metadata) {
			this.raid = raid;
			this.party = party;
			this.metadata = metadata;
		}

		public void addTo(Map<String, Object> data) {
			if (party != null) party.addTo(data);
			if (metadata != null) metadata.addTo(data);
		}
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

		/** Share completion deaths so the final-tick update reaches every membership view. */
		Party withClanRoster(Set<String> revalRoster) {
			List<Boolean> flags = new ArrayList<>(names.size());
			for (String name : names) flags.add(revalRoster == null ? null : revalRoster.contains(nameKey(name)));
			return new Party(raid, origin, names, flags, deaths, size, rosterComplete);
		}

		private Party withDeaths(Map<String, Integer> counts) {
			List<Integer> values = new ArrayList<>(names.size());
			for (String name : names) values.add(counts.get(nameKey(name)));
			return new Party(raid, origin, names, revalMembers, values, size, rosterComplete);
		}

		Party withRosterEvidence(int size, boolean complete) {
			return new Party(raid, origin, names, revalMembers, deaths, size, complete);
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
