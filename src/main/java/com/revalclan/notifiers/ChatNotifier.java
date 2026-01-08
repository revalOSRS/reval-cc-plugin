package com.revalclan.notifiers;

import com.revalclan.RevalClanConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Notifier for system chat messages - excludes player chats, private messages, clan chats, etc.
 * Useful for tracking game events, broadcasts, and system notifications for bingo/challenges.
 */
@Slf4j
@Singleton
public class ChatNotifier extends BaseNotifier {
	@Inject private RevalClanConfig config;
	
	/**
	 * Chat message types that are considered "system" messages.
	 * These are game-generated messages, not player-typed chats.
	 */
	private static final Set<ChatMessageType> SYSTEM_MESSAGE_TYPES = EnumSet.of(
		ChatMessageType.GAMEMESSAGE,      // General game messages (most common)
		ChatMessageType.SPAM,             // Spam filter messages (loot, drops, etc.)
		ChatMessageType.ENGINE,           // Engine messages
		ChatMessageType.CONSOLE,          // Console messages
		ChatMessageType.MESBOX,           // Message boxes (NPC dialog, etc.)
		ChatMessageType.DIALOG,           // Dialog messages
    ChatMessageType.OBJECT_EXAMINE,   // Object examine messages
    ChatMessageType.NPC_SAY,          // NPC say messages
    ChatMessageType.NPC_EXAMINE       // NPC examine messages
	);
	
	@Override
	public boolean isEnabled() {
		return config.notifyChat() && filterManager.getFilters().isChatEnabled();
	}
	
	@Override
	protected String getEventType() {
		return "CHAT";
	}
	
	/**
	 * Handle a chat message event
	 * @param messageType The type of chat message
	 * @param source The source/sender of the message (can be null for system messages)
	 * @param message The message content
	 */
	public void onChatMessage(ChatMessageType messageType, String source, String message) {
		if (!isEnabled()) return;

		if (!SYSTEM_MESSAGE_TYPES.contains(messageType)) return;

		String cleanMessage = cleanMessage(message);

		List<String> patterns = filterManager.getFilters().getChatPatterns();

		if (!patterns.isEmpty() && !hasMatch(cleanMessage, patterns)) {
			return;
		}
		
		handleNotify(messageType, source, cleanMessage);
	}
	
	/**
	 * Clean HTML tags and formatting from a message
	 */
	private String cleanMessage(String message) {
		if (message == null) return "";
		return message
			.replaceAll("<col=[0-9a-fA-F]+>", "")
			.replaceAll("</col>", "")
			.replaceAll("<br>", " ")
			.replaceAll("<[^>]+>", "")
			.trim();
	}
	
	/**
	 * Check if the message matches any configured patterns
	 * @param message The message to check
	 * @param patterns List of regex pattern strings from the API
	 */
	private boolean hasMatch(String message, List<String> patterns) {
		for (String patternStr : patterns) {
			try {
				Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
				if (pattern.matcher(message).find()) {
					return true;
				}
			} catch (Exception e) {
				log.warn("Invalid chat pattern from API: {}", patternStr);
			}
		}
		return false;
	}
	
	/**
	 * Send a chat notification
	 */
	private void handleNotify(ChatMessageType messageType, String source, String message) {
		Map<String, Object> chatData = new HashMap<>();
		chatData.put("messageType", messageType.name());
		chatData.put("message", message);
		
		if (source != null && !source.isEmpty()) {
			chatData.put("source", source);
		}
		
		chatData.put("category", categorizeMessage(messageType, message));
		
		sendNotification(chatData);
	}
	
	/**
	 * Categorize the message type for easier processing
	 */
	private String categorizeMessage(ChatMessageType type, String message) {
		switch (type) {
			case GAMEMESSAGE:
			case ENGINE:
				return "GAME";
			case SPAM:
				return "SPAM";
			case CONSOLE:
				return "CONSOLE";
			case MESBOX:
			case DIALOG:
				return "DIALOG";
			case OBJECT_EXAMINE:
			case NPC_EXAMINE:
				return "EXAMINE";
			case NPC_SAY:
				return "NPC";
			default:
				return "OTHER";
		}
	}
}
