package com.revalclan.player;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlayerManagerTest {
	@Test
	public void mainOnHardcoreWorldIsHardcoreMain() {
		assertEquals("HARDCORE_MAIN", PlayerManager.getAccountType(0, true));
	}

	@Test
	public void mainOnRegularWorldIsNormal() {
		assertEquals("NORMAL", PlayerManager.getAccountType(0, false));
	}

	@Test
	public void ironmanVarbitWinsOverWorldType() {
		assertEquals("IRONMAN", PlayerManager.getAccountType(1, false));
		assertEquals("HARDCORE_IRONMAN", PlayerManager.getAccountType(3, true));
		assertEquals("UNRANKED_GROUP_IRONMAN", PlayerManager.getAccountType(6, false));
	}
}
