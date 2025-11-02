package com.revalclan;

import com.revalclan.collectionlog.CollectionLogManager;
import com.revalclan.collectionlog.CollectionLogSyncButton;
import com.revalclan.combatachievements.CombatAchievementManager;
import com.revalclan.notifiers.*;
import com.revalclan.util.ClanValidator;
import com.revalclan.util.WebhookService;
import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import java.util.Map;

@Slf4j
@PluginDescriptor(
	name = "Reval Clan"
)
public class RevalClanPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private RevalClanConfig config;

	@Inject
	private PlayerDataCollector dataCollector;

	@Inject
	private CollectionLogManager collectionLogManager;

	@Inject
	private CollectionLogSyncButton syncButton;

	@Inject
	private CombatAchievementManager combatAchievementManager;

	@Inject
	private WebhookService webhookService;

	@Inject
	private LootNotifier lootNotifier;

	@Inject
	private PetNotifier petNotifier;

	@Inject
	private QuestNotifier questNotifier;

	@Inject
	private LevelNotifier levelNotifier;

	@Inject
	private KillCountNotifier killCountNotifier;

	@Inject
	private ClueNotifier clueNotifier;

	@Inject
	private DiaryNotifier diaryNotifier;

	@Inject
	private SlayerNotifier slayerNotifier;

	@Inject
	private CombatAchievementNotifier combatAchievementNotifier;

	@Inject
	private CollectionNotifier collectionNotifier;

	@Inject
	private DeathNotifier deathNotifier;

	@Inject
	private DetailedKillNotifier detailedKillNotifier;

	@Inject
	private AreaEntryNotifier areaEntryNotifier;

	@Inject
	private EmoteNotifier emoteNotifier;

	@Inject
	private EventBus eventBus;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	private boolean wasLoggedIn = false;
	private int itemsBeforeOpen = 0;
	private boolean needsCaLoad = false;

	@Override
	protected void startUp() throws Exception
	{
		log.info("Reval Clan plugin started!");
		wasLoggedIn = false;

		clientThread.invoke(() -> {
			if (client.getIndexConfig() == null || client.getGameState().ordinal() < GameState.LOGIN_SCREEN.ordinal())
			{
				return false;
			}

			collectionLogManager.parseCacheForCollectionLog();
			return true;
		});

		syncButton.startUp();
		
		eventBus.register(lootNotifier);
		eventBus.register(petNotifier);
		eventBus.register(questNotifier);
		eventBus.register(levelNotifier);
		eventBus.register(killCountNotifier);
		eventBus.register(clueNotifier);
		eventBus.register(diaryNotifier);
		eventBus.register(slayerNotifier);
		eventBus.register(combatAchievementNotifier);
		eventBus.register(collectionNotifier);
		eventBus.register(deathNotifier);
		eventBus.register(detailedKillNotifier);
		eventBus.register(areaEntryNotifier);
		eventBus.register(emoteNotifier);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("Reval Clan plugin stopped!");
		collectionLogManager.clearObtainedItems();
		syncButton.shutDown();
		
		eventBus.unregister(lootNotifier);
		eventBus.unregister(petNotifier);
		eventBus.unregister(questNotifier);
		eventBus.unregister(levelNotifier);
		eventBus.unregister(killCountNotifier);
		eventBus.unregister(clueNotifier);
		eventBus.unregister(diaryNotifier);
		eventBus.unregister(slayerNotifier);
		eventBus.unregister(combatAchievementNotifier);
		eventBus.unregister(collectionNotifier);
		eventBus.unregister(deathNotifier);
		eventBus.unregister(detailedKillNotifier);
		eventBus.unregister(areaEntryNotifier);
		eventBus.unregister(emoteNotifier);
		
		levelNotifier.reset();
		clueNotifier.reset();
		slayerNotifier.reset();
		detailedKillNotifier.reset();
		areaEntryNotifier.reset();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		diaryNotifier.onGameStateChanged(gameStateChanged);

		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			wasLoggedIn = true;

			collectionLogManager.clearObtainedItems();
			
			needsCaLoad = true;
		}
		else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN)
		{
			if (wasLoggedIn)
			{
				log.info("Player logged out, collecting data...");
				
				Map<String, Object> data = dataCollector.collectAllData();
				
				data.put("eventType", "SYNC");
				data.put("eventTimestamp", System.currentTimeMillis());
				
				if (config.enableWebhook())
				{
					String webhookUrl = config.webhookUrl();
					if (webhookUrl != null && !webhookUrl.trim().isEmpty())
					{
					if (ClanValidator.validateClan(client))
					{
						log.info("Sending data to webhook...");
						webhookService.sendDataAsync(webhookUrl, data);
					}
					else
					{
						log.info("Clan validation failed - SYNC webhook blocked");
					}
					}
					else
					{
						log.warn("Webhook is enabled but no URL is configured");
					}
				}
				
				if (config.saveLocalJson())
				{
					dataCollector.writeDataToFile();
				}
				
				wasLoggedIn = false;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		if (needsCaLoad && client.getGameState() == GameState.LOGGED_IN)
		{
			needsCaLoad = false;
			
			clientThread.invokeLater(() -> {
				combatAchievementManager.loadAllTasks();
				return true;
			});
		}

		areaEntryNotifier.onGameTick(gameTick);
		
		detailedKillNotifier.onGameTick(gameTick);
		
		diaryNotifier.onGameTick();
	}

	/**
	 * Handles collection log script events to track obtained items
	 * Script 4100 fires when collection log opens and for each item
	 */
	@Subscribe
	public void onScriptPreFired(ScriptPreFired preFired)
	{
		if (preFired.getScriptId() == 4100)
		{
			try
			{
				Object[] args = preFired.getScriptEvent().getArguments();
				
				if (args == null || args.length < 3)
				{
					log.warn("Script 4100 fired with insufficient arguments: {}", args != null ? args.length : "null");
					return;
				}
				
				int itemId = (int) args[1];
				int itemCount = (int) args[2];
				String itemName = itemManager.getItemComposition(itemId).getName();

				collectionLogManager.onCollectionLogItemObtained(itemId, itemCount, itemName);
			}
			catch (Exception e)
			{
				log.error("Error capturing collection log item", e);
			}
		}
		else if (preFired.getScriptId() == 2084)
		{
			itemsBeforeOpen = collectionLogManager.getObtainedItems().size();
		}
	}

	/**
	 * Detects when collection log closes to show summary
	 */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired postFired)
	{
		if (postFired.getScriptId() == 2083)
		{
			int itemsCaptured = collectionLogManager.getObtainedItems().size();
			int totalItems = collectionLogManager.getAllCollectionLogItems().size();
			
			if (itemsCaptured > itemsBeforeOpen)
			{
				log.info("✓ Collection log closed - captured {} unique items out of {} total!", 
					itemsCaptured, totalItems);
			}
			else
			{
				log.info("Collection log closed - {} items already captured", itemsCaptured);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();
		
		if (message.toLowerCase().startsWith("::testreval"))
		{
			handleTestCommand();
			return;
		}
		
		petNotifier.onChatMessage(message);
		killCountNotifier.onChatMessage(message);
		clueNotifier.onChatMessage(message);
		slayerNotifier.onChatMessage(message);
		combatAchievementNotifier.onChatMessage(message);
		collectionNotifier.onChatMessage(message);
	}

	/**
	 * Handles the ::testreval command to test webhook functionality
	 */
	private void handleTestCommand()
	{
		if (!config.enableWebhook())
		{
			log.warn("Webhook is not enabled. Enable it in the plugin configuration.");
			return;
		}

		String webhookUrl = config.webhookUrl();
		if (webhookUrl == null || webhookUrl.trim().isEmpty())
		{
			log.warn("No webhook URL configured. Set it in the plugin configuration.");
			return;
		}

		if (!ClanValidator.validateClan(client))
		{
			log.warn("Clan validation failed - test webhook blocked");
			return;
		}

		log.info("Sending test webhook...");

		Map<String, Object> testData = new java.util.HashMap<>();
		testData.put("eventType", "TEST");
		testData.put("eventTimestamp", System.currentTimeMillis());
		testData.put("player", client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : "Unknown");
		testData.put("message", "Test webhook from Reval Clan plugin");
		testData.put("command", "::testreval");

		webhookService.sendDataAsync(webhookUrl, testData);

		log.info("Test webhook sent to: {}", webhookUrl);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		levelNotifier.onStatChanged(event);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		questNotifier.onWidgetLoaded(event);
		clueNotifier.onWidgetLoaded(event);
	}

	@Subscribe
	public void onActorDeath(net.runelite.api.events.ActorDeath event)
	{
		deathNotifier.onActorDeath(event);
		detailedKillNotifier.onActorDeath(event);
	}

	@Subscribe
	public void onHitsplatApplied(net.runelite.api.events.HitsplatApplied event)
	{
		detailedKillNotifier.onHitsplatApplied(event);
	}

	@Subscribe
	public void onMenuOptionClicked(net.runelite.api.events.MenuOptionClicked event)
	{
		emoteNotifier.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onInteractingChanged(net.runelite.api.events.InteractingChanged event)
	{
		deathNotifier.onInteractingChanged(event);
	}

	@Subscribe
	public void onVarbitChanged(net.runelite.api.events.VarbitChanged event)
	{
		diaryNotifier.onVarbitChanged(event);
	}

	@Provides
	RevalClanConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RevalClanConfig.class);
	}
}


