/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.teleport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeleportStoneStoreTest {

	private static final String ROW = "world_100_100";
	private static final UUID FIRST_HOLDER = UUID.randomUUID();
	private static final UUID SECOND_HOLDER = UUID.randomUUID();

	private static JavaPlugin pluginIn(final Path folder) {
		final JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getDataFolder()).thenReturn(folder.toFile());
		when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
		return plugin;
	}

	private static TeleportStoneStore store(final Path folder) {
		return new TeleportStoneStore(pluginIn(folder));
	}

	@Test
	void noVillageStartsWithAStoneIssued(@TempDir final Path folder) {
		assertFalse(store(folder).issued(ROW));
	}

	@Test
	void anIssuedStoneSurvivesAReload(@TempDir final Path folder) {
		final TeleportStoneStore store = store(folder);
		store.issue(ROW, FIRST_HOLDER);

		final TeleportStoneStore reloaded = store(folder);
		assertTrue(reloaded.issued(ROW));
		assertEquals(FIRST_HOLDER, reloaded.holderOf(ROW));
	}

	@Test
	void releasingFreesTheVillageToIssueAnother(@TempDir final Path folder) {
		final TeleportStoneStore store = store(folder);
		store.issue(ROW, FIRST_HOLDER);
		store.release(ROW);

		assertFalse(store.issued(ROW));
		assertEquals(0, store.size());
	}

	@Test
	void mergingKeepsTheEarlierStone(@TempDir final Path folder) {
		final TeleportStoneStore store = store(folder);
		store.issue("woldmere", FIRST_HOLDER);
		store.issue("hawthorn", SECOND_HOLDER);

		store.merge("hawthorn", "woldmere");

		assertEquals(FIRST_HOLDER, store.holderOf("woldmere"));
		assertFalse(store.issued("hawthorn"));
		assertEquals(1, store.size());
	}

	@Test
	void mergingCarriesAStoneTheSurvivorNeverIssued(
		@TempDir final Path folder) {

		final TeleportStoneStore store = store(folder);
		store.issue("hawthorn", SECOND_HOLDER);

		store.merge("hawthorn", "woldmere");

		assertEquals(SECOND_HOLDER, store.holderOf("woldmere"));
	}

	@Test
	void mergingAVillageIntoItselfLeavesItsStoneIntact(
		@TempDir final Path folder) {

		final TeleportStoneStore store = store(folder);
		store.issue(ROW, FIRST_HOLDER);

		store.merge(ROW, ROW);

		assertTrue(store.issued(ROW));
		assertEquals(FIRST_HOLDER, store.holderOf(ROW));
		assertEquals(1, store.size());
	}
}
