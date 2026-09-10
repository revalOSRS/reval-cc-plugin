package com.revalclan;

import com.revalclan.api.RevalApiService;
import com.revalclan.combat.KillTracker;
import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.CollectionLogSyncButton;
import com.revalclan.collectionlog.SyncGuide;
import com.revalclan.collectionlog.SyncGuideOverlay;
import com.revalclan.events.RegistrationMarks;
import com.revalclan.events.RegistrationMarksOverlay;
import com.revalclan.playercards.PlayerCardManager;
import com.revalclan.playercards.PlayerCardOverlay;
import com.revalclan.teams.ClanTeamColors;
import com.revalclan.ui.leaguesbingo.LeaguesBingoPanel;
import com.revalclan.notifiers.*;
import com.revalclan.pbs.ClogPersonalBestCapture;
import com.revalclan.session.SessionTracker;
import com.revalclan.util.ClanMembership;
import com.revalclan.ui.RevalPanel;
import com.revalclan.util.AnnouncementService;
import com.revalclan.util.ClanRankIconResolver;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.SyncStateManager;
import com.revalclan.util.UIAssetLoader;
import com.revalclan.util.Worlds;
import com.google.inject.Provides;

import java.awt.image.BufferedImage;
import java.util.regex.Pattern;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ClanChannelChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Reval Clan"
)
public class RevalClanPlugin extends Plugin {
	@Inject private Client client;

	@Inject	private CollectionLogManager collectionLogManager;

	@Inject	private CollectionLogSyncButton syncButton;
	@Inject	private SyncGuide syncGuide;
	@Inject	private SyncGuideOverlay syncGuideOverlay;
	@Inject	private ClanTeamColors clanTeamColors;
	@Inject	private RegistrationMarks registrationMarks;
	@Inject	private RegistrationMarksOverlay registrationMarksOverlay;
	@Inject	private PlayerCardManager playerCardManager;
	@Inject	private PlayerCardOverlay playerCardOverlay;
	@Inject	private OverlayManager overlayManager;
	@Inject	private ClanRankIconResolver rankIconResolver;

	@Inject	private LootNotifier lootNotifier;
	@Inject	private VarbitNotifier varbitNotifier;

	@Inject	private ClogPersonalBestCapture clogPersonalBestCapture;

	@Inject	private PetNotifier petNotifier;

	@Inject	private QuestNotifier questNotifier;

	@Inject	private LevelNotifier levelNotifier;

	@Inject	private KillCountNotifier killCountNotifier;

	@Inject	private ClueNotifier clueNotifier;

	@Inject	private DiaryNotifier diaryNotifier;

	@Inject	private CombatAchievementNotifier combatAchievementNotifier;

	@Inject	private CollectionNotifier collectionNotifier;

	@Inject	private DeathNotifier deathNotifier;

	@Inject	private DetailedKillNotifier detailedKillNotifier;
	@Inject	private KillTracker killTracker;

	@Inject	private EmoteNotifier emoteNotifier;

	@Inject	private ChatNotifier chatNotifier;

	@Inject	private MusicNotifier musicNotifier;

	@Inject	private LeaguesNotifier leaguesNotifier;

	@Inject	private LeaguesSyncNotifier leaguesSyncNotifier;

	@Inject	private LoginNotifier loginNotifier;

	@Inject	private LogoutNotifier logoutNotifier;

	@Inject	private SyncNotifier syncNotifier;

	@Inject	private SessionTracker sessionTracker;
	@Inject	private ClanMembership clanMembership;

	@Inject	private SyncStateManager syncStateManager;

	@Inject	private EventBus eventBus;

	@Inject	private ClientThread clientThread;

	@Inject	private ItemManager itemManager;

	@Inject	private SpriteManager spriteManager;

	@Inject	private EventFilterManager eventFilterManager;

	@Inject	private AnnouncementService announcementService;

	@Inject	private ClientToolbar clientToolbar;

	@Inject	private RevalApiService revalApiService;


	@Inject	private UIAssetLoader uiAssetLoader;

	@Inject	private RevalClanConfig config;

	private RevalPanel revalPanel;
	private NavigationButton navButton;

	private boolean wasLoggedIn = false;
	private boolean pendingLoginNotification = false;

	/**
	 * Cached clan answer for this login (see ClanMembership). Proven once from
	 * the clan settings, sticky until a source says otherwise — never cleared
	 * by a hop's channel drop or a left clan channel. Event subscribers gate on
	 * it; the session accumulator does NOT (it records regardless, only sending
	 * is gated).
	 */
	private volatile boolean inRequiredClan = false;

	/** Refetch event filters every ~10 minutes so activated events propagate without a relog */
	private static final int FILTER_REFETCH_INTERVAL_TICKS = 1000;
	private int filterRefetchTicks = 0;

	private static final Pattern COL_OPEN = Pattern.compile("<col=[0-9a-fA-F]+>");
	private static final Pattern COL_CLOSE = Pattern.compile("</col>");

	@Override
	protected void startUp() throws Exception {
		log.info("Reval Clan plugin started!");
		wasLoggedIn = false;
		pendingLoginNotification = false;
		inRequiredClan = false;
		clanMembership.reset();

		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) {
				return false;
			}

			collectionLogManager.parseCacheForCollectionLog();

			// Plugin enabled mid-game: treat it as a login so the session starts now
			// and LOGIN goes out once membership is proven (used to wait for a relog)
			if (client.getGameState() == GameState.LOGGED_IN) {
				onLoggedIn();
			}

			return true;
		});

		syncButton.startUp();
		overlayManager.add(syncGuideOverlay);

		// Replay sessions the server never acknowledged (crash / X-out / lost response)
		sessionTracker.recoverPersistedSessions(false);

		eventBus.register(lootNotifier);
		eventBus.register(clogPersonalBestCapture);
		eventBus.register(clanTeamColors);
		clanTeamColors.startUp();
		eventBus.register(registrationMarks);
		registrationMarks.startUp();
		overlayManager.add(registrationMarksOverlay);
		eventBus.register(playerCardManager);
		overlayManager.add(playerCardOverlay);

		// Initialize and add the side panel
		try {
			revalPanel = new RevalPanel();
			revalPanel.init(revalApiService, client, uiAssetLoader, itemManager, spriteManager, config,
				rankIconResolver);
			revalPanel.getEventsPanel().setLeaguesBingoPanel(injector.getInstance(LeaguesBingoPanel.class));
			revalPanel.setOnSyncGuide(() -> {
				syncGuide.arm();
				clientThread.invoke(() -> {
					if (client.getGameState() == GameState.LOGGED_IN) {
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
							"Reval: Open your Collection Log - the sync button will be highlighted.", "");
					}
				});
			});
			
			BufferedImage icon = uiAssetLoader.getImage("reval.png");
			
			navButton = NavigationButton.builder()
				.tooltip("Reval Clan")
				.icon(icon)
				.priority(1)
				.panel(revalPanel)
				.build();
			
			clientToolbar.addNavigation(navButton);
		} catch (Exception e) {
			log.error("Failed to initialize Reval Clan panel", e);
		}
	}

	@Override
	protected void shutDown() throws Exception {
		log.info("Reval Clan plugin stopped!");
		inRequiredClan = false;
		clanMembership.reset();
		wasLoggedIn = false;

		collectionLogManager.clearObtainedItems();
		syncButton.shutDown();
		overlayManager.remove(syncGuideOverlay);
		if (revalPanel != null) {
			revalPanel.shutDown();
		}

		eventBus.unregister(lootNotifier);
		eventBus.unregister(clogPersonalBestCapture);
		eventBus.unregister(clanTeamColors);
		clanTeamColors.shutDown();
		eventBus.unregister(registrationMarks);
		overlayManager.remove(registrationMarksOverlay);
		eventBus.unregister(playerCardManager);
		playerCardManager.shutDown();
		overlayManager.remove(playerCardOverlay);

		// In-memory only — the persisted copy replays at the next startUp/login
		sessionTracker.reset();

		announcementService.reset();
		levelNotifier.reset();
		clueNotifier.reset();
		killCountNotifier.reset();
		killTracker.reset();
		leaguesNotifier.reset();
		leaguesSyncNotifier.reset();

		// Remove the side panel
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		diaryNotifier.onGameStateChanged(gameStateChanged);

		switch (gameStateChanged.getGameState()) {
			case LOGGED_IN:
				// Only a real login starts a session; a hop's LOGGED_IN starts the next segment
				if (!wasLoggedIn) {
					onLoggedIn();
				} else if (pendingHopSegment) {
					pendingHopSegment = false;
					sessionTracker.startSession();
				}
				break;

			case HOPPING:
				// Each session is one world: close this segment now, open the next on LOGGED_IN
				if (wasLoggedIn) {
					sessionTracker.cutForHop();
					pendingHopSegment = true;
				}
				break;

			case LOGIN_SCREEN: {
				boolean wasInClan = inRequiredClan;
				inRequiredClan = false;
				clanMembership.reset();
				pendingLoginNotification = false;
				pendingHopSegment = false;
				announcementService.reset();
				leaguesNotifier.reset();
				leaguesSyncNotifier.reset();
				lootNotifier.reset();
				varbitNotifier.reset();

				if (wasLoggedIn) {
					if (wasInClan) {
						logoutNotifier.onLogout(sessionTracker.finalizeSession());
					} else {
						// Not (yet) proven a member this login: keep the file, a later login that proves it replays it
						sessionTracker.finalizeSession();
					}
					wasLoggedIn = false;

					if (revalPanel != null) {
						revalPanel.onLoggedOut();
					}
				}
				break;
			}

			default:
				break;
		}
	}

	/** A world hop closed the session; the next LOGGED_IN opens a new segment */
	private boolean pendingHopSegment = false;

	/** Fresh login (or plugin enabled while logged in): start recording immediately, prove membership on ticks. */
	private void onLoggedIn() {
		wasLoggedIn = true;
		collectionLogManager.clearObtainedItems();
		pendingLoginNotification = true;
		sessionTracker.startSession();
	}

	@Subscribe
	public void onClanChannelChanged(ClanChannelChanged event) {
		// The channel arriving is the fastest proof at login; refresh right away
		if (clanMembership.refresh()) {
			inRequiredClan = true;
			onClanValidated();
		}
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event) {
		if (event.getIndex() == VarClientID.ACCOUNT_SUMMARY_PLAYTIME) {
			sessionTracker.onPlaytimeVarcChanged();
		}
	}

	private void onClanValidated() {
		eventFilterManager.setOnFiltersApplied(varbitNotifier::syncBaselines);
		eventFilterManager.fetchFiltersAsync();

		// Fetch leagues config if on a seasonal world
		if (Worlds.isSeasonal(client)) {
			leaguesNotifier.fetchConfig();
			leaguesSyncNotifier.onLogin();
		}

		if (pendingLoginNotification) {
			pendingLoginNotification = false;
			loginNotifier.onLogin();
		}

		// Membership is proven: anything recorded before we knew (this login or an
		// earlier one on this machine) can go out now
		sessionTracker.recoverPersistedSessions(true);

		if (revalPanel != null) {
			revalPanel.onLoggedIn();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		// The session records regardless of clan state — only sending is gated
		sessionTracker.onGameTick();

		// Cheap re-read of the clan sources every tick; flips to member exactly once per login
		if (clanMembership.refresh()) {
			inRequiredClan = true;
			onClanValidated();
		} else if (inRequiredClan && !clanMembership.isMember()) {
			inRequiredClan = false;
		}

		if (!inRequiredClan) return;

		announcementService.onGameTick();
		lootNotifier.onGameTick();
		varbitNotifier.onGameTick();
		killTracker.onGameTick(gameTick);
		killCountNotifier.onTick();
		diaryNotifier.onGameTick();
		petNotifier.onGameTick();
		leaguesNotifier.onGameTick();
		leaguesSyncNotifier.onGameTick();

		// Activated events change the server-derived whitelists; refetch so a relog isn't needed
		if (++filterRefetchTicks >= FILTER_REFETCH_INTERVAL_TICKS) {
			filterRefetchTicks = 0;
			eventFilterManager.fetchFiltersAsync();
		}

		// Server flagged our fingerprint stale — repair with a full sync. Polled
		// here (not invokeLater from the ack) because a stale ack can arrive on a
		// LOGOUT response: GameTick only fires while logged in, so the repair
		// naturally waits for the next login.
		if (syncStateManager.consumeFullSyncRequest()) {
			syncNotifier.triggerSync();
		}
	}

	/**
	 * Handles collection log script events to track obtained items
	 * Script 4100 fires when collection log opens and for each item
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired) {
		if (!inRequiredClan) return;

		if (preFired.getScriptId() == 4100) {
			try {
				Object[] args = preFired.getScriptEvent().getArguments();
				if (args == null || args.length < 3) return;

				int itemId = (int) args[1];
				int itemCount = (int) args[2];
				String itemName = itemManager.getItemComposition(itemId).getName();
				collectionLogManager.onCollectionLogItemObtained(itemId, itemCount, itemName);
			} catch (Exception e) {
				log.error("Error capturing collection log item", e);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event) {
		if (!inRequiredClan) return;

		String message = event.getMessage();
		String cleanMessage = COL_CLOSE.matcher(COL_OPEN.matcher(message).replaceAll("")).replaceAll("");

		ChatMessageType type = event.getType();

		chatNotifier.onChatMessage(type, event.getName(), cleanMessage);

		if (type == ChatMessageType.GAMEMESSAGE ||
			type == ChatMessageType.SPAM ||
			type == ChatMessageType.ENGINE) {
			petNotifier.onChatMessage(cleanMessage);
			lootNotifier.onGameMessage(cleanMessage);
			killCountNotifier.onChatMessage(cleanMessage);
			clueNotifier.onChatMessage(cleanMessage);
			combatAchievementNotifier.onChatMessage(cleanMessage);
			collectionNotifier.onChatMessage(cleanMessage);
			leaguesNotifier.onChatMessage(cleanMessage);
		} else if (type == ChatMessageType.CLAN_MESSAGE ||
			type == ChatMessageType.CLAN_CHAT ||
			type == ChatMessageType.CLAN_GUEST_CHAT) {
			petNotifier.onClanNotification(cleanMessage);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event) {
		if (!inRequiredClan) return;
		levelNotifier.onStatChanged(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (!inRequiredClan) return;
		questNotifier.onWidgetLoaded(event);
		clueNotifier.onWidgetLoaded(event);
		leaguesSyncNotifier.onWidgetLoaded(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		lootNotifier.onActorDeath(event);
		// Kills feed the session accumulator regardless of clan state
		KillTracker.KillData kill = killTracker.onActorDeath(event);
		if (kill != null) sessionTracker.addKill(kill.npcName);
		if (!inRequiredClan) return;
		deathNotifier.onActorDeath(event);
		if (kill != null) detailedKillNotifier.onKill(kill);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		killTracker.onHitsplatApplied(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event) {
		// Not gated on inRequiredClan: must always evict the accumulator entry
		// so damaged-but-never-died NPCs don't pin memory
		killTracker.onNpcDespawned(event);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		if (!inRequiredClan) return;
		emoteNotifier.onMenuOptionClicked(event);
		musicNotifier.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		if (!inRequiredClan) return;
		deathNotifier.onInteractingChanged(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (!inRequiredClan) return;
		diaryNotifier.onVarbitChanged(event);
		varbitNotifier.onVarbitChanged(event);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!"revalclan".equals(event.getGroup())) return;

		if ("hideCompletedItems".equals(event.getKey()) && revalPanel != null) {
			revalPanel.getProfilePanel().rebuild();
		}
	}

	@Provides
	RevalClanConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RevalClanConfig.class);
	}
}


