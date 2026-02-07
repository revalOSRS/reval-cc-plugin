package com.revalclan;

import com.revalclan.api.RevalApiService;
import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.CollectionLogSyncButton;
import com.revalclan.notifiers.*;
import com.revalclan.ui.RevalPanel;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.UIAssetLoader;
import com.google.inject.Provides;

import java.awt.image.BufferedImage;

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

	@Inject	private ClientToolbar clientToolbar;

	@Inject	private RevalApiService revalApiService;


	@Inject	private UIAssetLoader uiAssetLoader;

	@Inject	private RevalClanConfig config;

	private RevalPanel revalPanel;
	private NavigationButton navButton;

	private boolean wasLoggedIn = false;

	@Override
	protected void startUp() throws Exception {
		log.info("Reval Clan plugin started!");
		wasLoggedIn = false;

		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal()) {
				return false;
			}

			collectionLogManager.parseCacheForCollectionLog();
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
		collectionLogManager.clearObtainedItems();
		syncButton.shutDown();
		
		eventBus.unregister(lootNotifier);

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
				
				// Fetch dynamic event filters from API on login
				eventFilterManager.fetchFiltersAsync();
				
				// Load profile data for the side panel
				if (revalPanel != null) {
					revalPanel.onLoggedIn();
				}

				// Send login notification
				loginNotifier.onLogin();
			}
		} else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			if (wasLoggedIn) {
				logoutNotifier.onLogout();
				wasLoggedIn = false;
				
				// Show login messages on all panels
				if (revalPanel != null) {
					revalPanel.onLoggedOut();
				}
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
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
		if (preFired.getScriptId() == 4100) {
			try {
				Object[] args = preFired.getScriptEvent().getArguments();
				
				if (args == null || args.length < 3) {
					log.warn("Script 4100 fired with insufficient arguments: {}", args != null ? args.length : "null");
					return;
				}
				
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
		String message = event.getMessage();
		
		// Strip HTML color tags from the message before processing
		String cleanMessage = message.replaceAll("<col=[0-9a-fA-F]+>", "").replaceAll("</col>", "");
		
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
		levelNotifier.onStatChanged(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		questNotifier.onWidgetLoaded(event);
		clueNotifier.onWidgetLoaded(event);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event) {
		deathNotifier.onActorDeath(event);
		detailedKillNotifier.onActorDeath(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event) {
		detailedKillNotifier.onHitsplatApplied(event);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event) {
		emoteNotifier.onMenuOptionClicked(event);
		musicNotifier.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		deathNotifier.onInteractingChanged(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
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


