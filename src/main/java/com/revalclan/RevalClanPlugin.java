package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.CollectionLogSyncButton;
import com.revalclan.notifiers.*;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.EventFilterManager;
import com.revalclan.util.WebhookService;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Reval Clan"
)
public class RevalClanPlugin extends Plugin {
	@Inject private Client client;

	@Inject	private PlayerDataCollector dataCollector;

	@Inject	private CollectionLogManager collectionLogManager;

	@Inject	private CollectionLogSyncButton syncButton;

	@Inject	private WebhookService webhookService;

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

	@Inject	private AreaEntryNotifier areaEntryNotifier;

	@Inject	private EmoteNotifier emoteNotifier;

	@Inject	private EventBus eventBus;

	@Inject	private ClientThread clientThread;

	@Inject	private ItemManager itemManager;

	@Inject	private EventFilterManager eventFilterManager;

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
		areaEntryNotifier.reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged) {
		diaryNotifier.onGameStateChanged(gameStateChanged);

		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			wasLoggedIn = true;

			collectionLogManager.clearObtainedItems();
			
			// Fetch dynamic event filters from API on login
			eventFilterManager.fetchFiltersAsync();
		} else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			if (wasLoggedIn) {
				log.info("Player logged out, collecting data...");
				
				Map<String, Object> data = dataCollector.collectAllData();
				
				data.put("eventType", "SYNC");
				data.put("eventTimestamp", System.currentTimeMillis());
				
			if (ClanValidator.validateClan(client)) {
				log.info("Sending data to webhook...");
				// Log only top-level keys and value types
				Map<String, String> dataSummary = new HashMap<>();
				data.forEach((key, value) -> {
					if (value == null) {
						dataSummary.put(key, "null");
					} else if (value instanceof Map) {
						dataSummary.put(key, "Map[" + ((Map<?, ?>) value).size() + " entries]");
					} else if (value instanceof List) {
						dataSummary.put(key, "List[" + ((List<?>) value).size() + " items]");
					} else if (value.getClass().isArray()) {
						dataSummary.put(key, "Array[" + Array.getLength(value) + " items]");
					} else {
						dataSummary.put(key, String.valueOf(value));
					}
				});
				log.info("Data summary: {}", dataSummary);
				webhookService.sendDataAsync(data);
			}
				
			wasLoggedIn = false;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick) {
		areaEntryNotifier.onGameTick(gameTick);
		
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
		
		if (message.toLowerCase().startsWith("::testreval")) {
			handleTestCommand();
			return;
		}
		
		// Strip HTML color tags from the message before processing
		String cleanMessage = message.replaceAll("<col=[0-9a-fA-F]+>", "").replaceAll("</col>", "");
		
		// Only process game messages and clan messages for notifiers, not player chat
		net.runelite.api.ChatMessageType type = event.getType();
		if (type == net.runelite.api.ChatMessageType.GAMEMESSAGE || 
		    type == net.runelite.api.ChatMessageType.SPAM ||
		    type == net.runelite.api.ChatMessageType.ENGINE) {
			petNotifier.onChatMessage(cleanMessage);
			lootNotifier.onGameMessage(cleanMessage);
			killCountNotifier.onChatMessage(cleanMessage);
			clueNotifier.onChatMessage(cleanMessage);
			combatAchievementNotifier.onChatMessage(cleanMessage);
			collectionNotifier.onChatMessage(cleanMessage);
		} else if (type == net.runelite.api.ChatMessageType.CLAN_MESSAGE ||
		           type == net.runelite.api.ChatMessageType.CLAN_CHAT ||
		           type == net.runelite.api.ChatMessageType.CLAN_GUEST_CHAT) {
			petNotifier.onClanNotification(cleanMessage);
		}
	}

	/**
	 * Handles the ::testreval command to test webhook functionality
	 */
	private void handleTestCommand() {
		if (!ClanValidator.validateClan(client)) return;

		Map<String, Object> testData = new java.util.HashMap<>();
		testData.put("eventType", "TEST");
		testData.put("eventTimestamp", System.currentTimeMillis());
		testData.put("player", client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown");
		testData.put("message", "Test webhook from Reval Clan plugin");
		testData.put("command", "::testreval");

		webhookService.sendDataAsync(testData);
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
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event) {
		deathNotifier.onInteractingChanged(event);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		diaryNotifier.onVarbitChanged(event);
	}

	@Provides
	RevalClanConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RevalClanConfig.class);
	}
}


