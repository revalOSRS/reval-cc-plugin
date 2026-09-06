package com.revalclan.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class DateTimeUtil {
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d HH:mm");

	/** "Sep 4 21:59" in the user's zone; the raw string when it does not parse. */
	public static String formatShort(String iso) {
		try {
			return parseToLocal(iso).format(SHORT);
		} catch (RuntimeException e) {
			return iso;
		}
	}


	/**
	 * Parses an ISO 8601 timestamp (with or without zone) as UTC,
	 * then converts to the user's local timezone.
	 */
	public static ZonedDateTime parseToLocal(String iso) {
		try {
			return ZonedDateTime.parse(iso)
				.withZoneSameInstant(ZoneId.systemDefault());
		} catch (DateTimeParseException e) {
			return LocalDateTime.parse(iso)
				.atZone(ZoneOffset.UTC)
				.withZoneSameInstant(ZoneId.systemDefault());
		}
	}

	/**
	 * Parses an ISO 8601 timestamp (with or without zone) to a UTC Instant.
	 */
	public static Instant parseToInstant(String iso) {
		try {
			return Instant.parse(iso);
		} catch (DateTimeParseException e) {
			return LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC);
		}
	}
}
