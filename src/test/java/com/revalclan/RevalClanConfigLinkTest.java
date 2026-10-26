package com.revalclan;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.*;

public class RevalClanConfigLinkTest {
	@Test
	public void theLinkCodeSettingIsASecretFieldWithTheAgreedName() throws Exception {
		Method method = RevalClanConfig.class.getMethod("linkCode");
		ConfigItem item = method.getAnnotation(ConfigItem.class);

		assertNotNull(item);
		assertEquals("Link code", item.name());
		assertEquals("linkCode", item.keyName());
		assertTrue("shown as a password field", item.secret());
		assertFalse("the player must be able to paste into it", item.hidden());
		assertEquals(String.class, method.getReturnType());
		assertEquals("revalclan", RevalClanConfig.class.getAnnotation(ConfigGroup.class).value());
	}

	@Test
	public void theDescriptionSaysWhereMembersAndAdministratorsGetTheirCode() throws Exception {
		String description = RevalClanConfig.class.getMethod("linkCode").getAnnotation(ConfigItem.class).description();

		assertTrue(description, description.contains("Members create it in their Reval account settings"));
		assertTrue(description, description.contains("administrators create it from their own member record in the Reval admin console"));
		assertTrue(description, description.contains("Link my RuneLite plugin"));
		assertTrue("still one sentence about clearing", description.contains("cleared once the link works"));
	}

	@Test
	public void everyLinkInstructionTheyAreShownKeepsAdministratorsOutOfTheWrongPlace() {
		java.util.List<String> instructions = new java.util.ArrayList<>();
		instructions.add(com.revalclan.auth.LinkTokenManager.LINK_INSTRUCTION);
		for (com.revalclan.auth.LinkException.Kind kind : new com.revalclan.auth.LinkException.Kind[] {
			com.revalclan.auth.LinkException.Kind.NOT_LINKED, com.revalclan.auth.LinkException.Kind.EXPIRED,
			com.revalclan.auth.LinkException.Kind.EXPIRED_LOCALLY, com.revalclan.auth.LinkException.Kind.REFUSED}) {
			instructions.add(kind.message());
		}

		for (String text : instructions) {
			assertTrue(text, text.contains("Link code"));
			assertTrue(text, text.contains("account settings"));
			assertTrue(text, text.contains("administrators: from your own member record in the Reval admin console"));
		}
	}

	@Test
	public void noOtherSettingStoresACredential() {
		int secrets = 0;
		for (Method method : RevalClanConfig.class.getDeclaredMethods()) {
			ConfigItem item = method.getAnnotation(ConfigItem.class);
			if (item != null && item.secret()) secrets++;
			if (item != null) {
				assertFalse(item.keyName(), item.keyName().toLowerCase().contains("token"));
				assertFalse(item.keyName(), item.keyName().toLowerCase().contains("membercode"));
			}
		}
		assertEquals("the Link code is the only secret", 1, secrets);
	}
}
