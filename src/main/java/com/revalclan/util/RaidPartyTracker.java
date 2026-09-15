package com.revalclan.util;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.gameval.InterfaceID;
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

/**
 * The party behind a raid's loot and completion count (CoX, ToB, ToA), with
 * each member marked as a Reval member or a guest.
 *
 * The party is read live while the player is inside the raid. It is also saved
 * when the final boss dies: a ToB or ToA reward can be claimed later from the
 * chest outside the raid, where the party interface can already be empty.
 * Call on the client thread.
 */
@Singleton
public class RaidPartyTracker {
	static final String CHAMBERS_OF_XERIC = "Chambers of Xeric";
	static final String THEATRE_OF_BLOOD = "Theatre of Blood";
	static final String TOMBS_OF_AMASCUT = "Tombs of Amascut";

	/** First VarClientStr holding a ToB party member name (5 consecutive slots). */
	private static final int TOB_MEMBER_NAME_VARC = 330;
	private static final int TOB_PARTY_MAX_SIZE = 5;

	/** First VarClientStr holding a ToA party member name (8 consecutive slots). */
	private static final int TOA_MEMBER_NAME_VARC = 1099;
	private static final int TOA_PARTY_MAX_SIZE = 8;

	/** A saved party applies to rewards claimed up to this long after the final boss died. */
	static final long SAVED_PARTY_TTL_MS = 10 * 60_000L;

	/** Where a party was read. */
	public enum Origin { LIVE, FINAL_BOSS }

	@Inject private Client client;

	private Party savedParty;
	private long savedAtMs;

	/** Save the party when a raid's final boss dies. Not gated on membership: it only records. */
	public void onActorDeath(ActorDeath event) {
		Actor actor = event.getActor();
		if (!(actor instanceof NPC)) return;
		String raid = raidForFinalBoss(actor.getName());
		if (raid == null) return;

		Party party = readParty(raid, Origin.FINAL_BOSS);
		if (party != null) {
			savedParty = party;
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

		WorldView world = client.getTopLevelWorldView();
		boolean insideRaid = world != null && world.isInstance();
		return choose(raid, readParty(raid, Origin.LIVE), insideRaid, savedParty,
			System.currentTimeMillis() - savedAtMs);
	}

	/** Forget the saved party (logout, plugin stop). */
	public void reset() {
		savedParty = null;
		savedAtMs = 0;
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
		} else {
			names = varcNames(TOA_MEMBER_NAME_VARC, TOA_PARTY_MAX_SIZE);
		}
		return names.isEmpty() ? null : Party.of(raid, origin, names, revalRoster());
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
		return sanitize(name).toLowerCase(Locale.ROOT).replaceAll("[ _-]", "");
	}

	/** One raid party: its members in party order and whether each is in Reval. */
	public static final class Party {
		final String raid;
		final Origin origin;
		final List<String> names;
		/** Parallel to names; a null entry means the Reval roster was not loaded. */
		final List<Boolean> revalMembers;

		private Party(String raid, Origin origin, List<String> names, List<Boolean> revalMembers) {
			this.raid = raid;
			this.origin = origin;
			this.names = names;
			this.revalMembers = revalMembers;
		}

		static Party of(String raid, Origin origin, List<String> names, Set<String> revalRoster) {
			List<Boolean> flags = new ArrayList<>(names.size());
			for (String name : names) {
				flags.add(revalRoster == null ? null : revalRoster.contains(nameKey(name)));
			}
			return new Party(raid, origin, names, flags);
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
				members.add(member);
			}

			Map<String, Object> party = new LinkedHashMap<>();
			party.put("origin", origin == Origin.FINAL_BOSS ? "final_boss" : "live");
			party.put("members", members);
			data.put("party", party);
		}
	}
}
