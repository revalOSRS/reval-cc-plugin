package com.revalclan.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Null-tolerant accessors for loosely typed Gson objects (requirements, progress metadata). */
public final class Json {
	private Json() {
	}

	public static String str(JsonObject o, String key, String fallback) {
		JsonElement e = o == null ? null : o.get(key);
		if (e == null || e.isJsonNull()) return fallback;
		try {
			return e.getAsString();
		} catch (RuntimeException ex) {
			return fallback;
		}
	}

	public static int num(JsonObject o, String key, int fallback) {
		Double d = number(o, key);
		return d == null ? fallback : (int) Math.round(d);
	}

	public static Double number(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		if (e == null || e.isJsonNull()) return null;
		try {
			return e.getAsDouble();
		} catch (RuntimeException ex) {
			return null;
		}
	}

	public static boolean bool(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		try {
			return e != null && !e.isJsonNull() && e.getAsBoolean();
		} catch (RuntimeException ex) {
			return false;
		}
	}

	/** The array under {@code key}, or null when absent or not an array. */
	public static JsonArray array(JsonObject o, String key) {
		JsonElement e = o == null ? null : o.get(key);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	/** String field {@code key} of every object in {@code array}, in order, skipping misses. */
	public static List<String> strings(JsonArray array, String key) {
		List<String> out = new ArrayList<>();
		if (array == null) return out;
		for (JsonElement e : array) {
			if (!e.isJsonObject()) continue;
			String v = str(e.getAsJsonObject(), key, null);
			if (v != null) out.add(v);
		}
		return out;
	}
}
