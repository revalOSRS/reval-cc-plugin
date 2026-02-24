package com.revalclan.notifiers;

import net.runelite.api.ChatMessageType;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
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
@Singleton
public class ChatNotifier extends BaseNotifier {
	
	/**
	 * Chat message types that are considered "system" messages.
	 * These are game-generated messages, not player-typed chats.
	 */
	private static final Set<ChatMessageType> SYSTEM_MESSAGE_TYPES = EnumSet.of(
		ChatMessageType.GAMEMESSAGE,
		ChatMessageType.SPAM,
		ChatMessageType.ENGINE,
		ChatMessageType.CONSOLE,
		ChatMessageType.MESBOX,
		ChatMessageType.DIALOG,
		ChatMessageType.OBJECT_EXAMINE,
		ChatMessageType.NPC_SAY,
		ChatMessageType.NPC_EXAMINE
	);

	private static final Pattern HTML_BR = Pattern.compile("<br>");
	private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");

	private List<String> cachedPatternStrings = Collections.emptyList();
	private List<Pattern> cachedPatterns = Collections.emptyList();
	
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
		String cleaned = HTML_BR.matcher(message).replaceAll(" ");
		return HTML_TAGS.matcher(cleaned).replaceAll("").trim();
	}

	/**
	 * Check if the message matches any configured patterns
	 * @param message The message to check
	 * @param patterns List of regex pattern strings from the API
	 */
	private boolean hasMatch(String message, List<String> patterns) {
		if (!patterns.equals(cachedPatternStrings)) {
			List<Pattern> compiled = new ArrayList<>(patterns.size());
			for (String patternStr : patterns) {
				try {
					compiled.add(Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE));
				} catch (Exception e) {}
			}
			cachedPatterns = compiled;
			cachedPatternStrings = new ArrayList<>(patterns);
		}

		for (Pattern pattern : cachedPatterns) {
			if (pattern.matcher(message).find()) {
				return true;
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
