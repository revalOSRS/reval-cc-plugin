package com.revalclan;

import com.revalclan.api.RevalApiService;
import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.CollectionLogSyncButton;
import com.revalclan.notifiers.*;
import com.revalclan.ui.RevalPanel;
import com.revalclan.util.AnnouncementService;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.UIAssetLoader;
import com.google.inject.Provides;

import java.awt.image.BufferedImage;
import java.util.regex.Pattern;

import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
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

@Slf4j
@PluginDescriptor(
	name = "Reval Clan"
)
public class RevalClanPlugin extends Plugin {
	@Inject private Client client;

	@Inject	private CollectionLogManager collectionLogManager;

	@Inject	private CollectionLogSyncButton syncButton;

	@Inject	private LootNotifier lootNotifier;

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

	@Inject	private EmoteNotifier emoteNotifier;

	@Inject	private ChatNotifier chatNotifier;

	@Inject	private MusicNotifier musicNotifier;

	@Inject	private LoginNotifier loginNotifier;

	@Inject	private LogoutNotifier logoutNotifier;

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

	private volatile boolean inRequiredClan = false;
	private int clanValidationAttempt = -1;

	private static final int FAST_VALIDATION_TICKS = 25;
	private static final int SLOW_VALIDATION_INTERVAL = 5;
	private static final int MAX_CLAN_VALIDATION_TICKS = 1000;

	private static final Pattern COL_OPEN = Pattern.compile("<col=[0-9a-fA-F]+>");
	private static final Pattern COL_CLOSE = Pattern.compile("</col>");

	@Override
	protected void startUp() throws Exception {
		log.info("Reval Clan plugin started!");
		wasLoggedIn = false;
		pendingLoginNotification = false;
		inRequiredClan = false;
		clanValidationAttempt = -1;

		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) {
				return false;
			}

			collectionLogManager.parseCacheForCollectionLog();

			if (client.getGameState() == GameState.LOGGED_IN) {
				wasLoggedIn = true;
				clanValidationAttempt = 0;
			}

			return true;
		});

		syncButton.startUp();
		
		eventBus.register(lootNotifier);

		// Initialize and add the side panel
		try {
			revalPanel = new RevalPanel();
			revalPanel.init(revalApiService, client, uiAssetLoader, itemManager, spriteManager, config);
			
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
		clanValidationAttempt = -1;
		wasLoggedIn = false;

		collectionLogManager.clearObtainedItems();
		syncButton.shutDown();
		
		eventBus.unregister(lootNotifier);

		announcementService.reset();
		levelNotifier.reset();
		clueNotifier.reset();
		killCountNotifier.reset();
		detailedKillNotifier.reset();

		// Remove the side panel
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		diaryNotifier.onGameStateChanged(gameStateChanged);

		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			// Only trigger login events on actual login, not world hops
			// wasLoggedIn is false only when coming from LOGIN_SCREEN
			if (!wasLoggedIn) {
				wasLoggedIn = true;
				collectionLogManager.clearObtainedItems();

				pendingLoginNotification = true;

				clanValidationAttempt = 0;
			}
		} else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			boolean wasInClan = inRequiredClan;
			inRequiredClan = false;
			clanValidationAttempt = -1;
			pendingLoginNotification = false;
			announcementService.reset();

			if (wasLoggedIn) {
				if (wasInClan) {
					logoutNotifier.onLogout();
				}
				wasLoggedIn = false;

				if (revalPanel != null) {
					revalPanel.onLoggedOut();
				}
			}
		}
	}

	private void onClanValidated() {
		eventFilterManager.fetchFiltersAsync();

		if (pendingLoginNotification) {
			pendingLoginNotification = false;
			loginNotifier.onLogin();
		}

		if (revalPanel != null) {
			revalPanel.onLoggedIn();
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		if (clanValidationAttempt >= 0) {
			if (clanValidationAttempt > MAX_CLAN_VALIDATION_TICKS) {
				clanValidationAttempt = -1;
			} else {
				boolean shouldCheck = clanValidationAttempt < FAST_VALIDATION_TICKS
					|| clanValidationAttempt % SLOW_VALIDATION_INTERVAL == 0;

				if (shouldCheck && ClanValidator.validateClan(client)) {
					inRequiredClan = true;
					clanValidationAttempt = -1;
					onClanValidated();
				} else {
					clanValidationAttempt++;
				}
			}
		}

		if (!inRequiredClan) return;

		announcementService.onGameTick();
		detailedKillNotifier.onGameTick(gameTick);
		killCountNotifier.onTick();
		diaryNotifier.onGameTick();
		petNotifier.onGameTick();
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
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		if (!inRequiredClan) return;
		deathNotifier.onActorDeath(event);
		detailedKillNotifier.onActorDeath(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		if (!inRequiredClan) return;
		detailedKillNotifier.onHitsplatApplied(event);
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


