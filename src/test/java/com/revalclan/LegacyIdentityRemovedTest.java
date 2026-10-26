package com.revalclan;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Source-level guard: the numeric member code (a shared, guessable secret sent as an
 * identity header) is gone, and nothing outside the token manager decides who a request is for.
 */
public class LegacyIdentityRemovedTest {
	private static List<Path> sources(String root) throws IOException {
		Path dir = Paths.get(root);
		if (!Files.isDirectory(dir)) return new ArrayList<>();
		try (Stream<Path> walk = Files.walk(dir)) {
			return walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
		}
	}

	private static List<String> offenders(String root, String... forbidden) throws IOException {
		List<String> hits = new ArrayList<>();
		for (Path path : sources(root)) {
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			for (String word : forbidden) {
				if (text.contains(word)) hits.add(path.getFileName() + " mentions " + word);
			}
		}
		return hits;
	}

	@Test
	public void noMemberCodeHeaderOrFieldRemainsInThePlugin() throws Exception {
		assertTrue("expected the main sources to be found from the project directory",
			!sources("src/main/java").isEmpty());
		List<String> hits = offenders("src/main/java", "X-Member-Code", "memberCode", "getEnteredCode", "setSession(String");
		assertEquals(new ArrayList<String>(), hits);
	}

	@Test
	public void noSharedSecretIsHardcodedInTheJar() throws Exception {
		// A real-looking token or Link code (64 hex characters) must not appear in any source file.
		Pattern secretShaped = Pattern.compile("(rp_)?[0-9a-f]{64}");
		List<String> hits = offenders("src/main/java", "Bearer ey", "BEGIN PRIVATE KEY");
		for (Path path : sources("src/main/java")) {
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			if (secretShaped.matcher(text).find()) hits.add(path.getFileName() + " contains a secret-shaped literal");
		}
		assertEquals(new ArrayList<String>(), hits);
	}

	@Test
	public void onlyTheTokenManagerBuildsAuthorizationHeaders() throws Exception {
		List<String> hits = new ArrayList<>();
		for (Path path : sources("src/main/java")) {
			String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
			if (text.contains("\"Authorization\"") && !path.getFileName().toString().equals("LinkTokenManager.java")) {
				hits.add(path.getFileName().toString());
			}
		}
		assertEquals("Authorization is set in exactly one place", new ArrayList<String>(), hits);
	}
}
