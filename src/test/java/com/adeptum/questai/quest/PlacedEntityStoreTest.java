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

package com.adeptum.questai.quest;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlacedEntityStoreTest {

	@Mock private JavaPlugin plugin;
	@Mock private World world;

	@TempDir
	private Path tempDir;
	private AutoCloseable mocks;
	private File dataFolder;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		dataFolder = tempDir.toFile();
		when(plugin.getDataFolder()).thenReturn(dataFolder);
		when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
		when(world.getUID()).thenReturn(UUID.randomUUID());
	}

	@Test
	void recordPersistsEntryToDisk() throws Exception {
		final PlacedEntityStore store = new PlacedEntityStore(plugin);
		final Location loc = new Location(world, 100.5, 64, 200.5);

		store.record(PlacedEntityStore.Kind.CHEST, loc);

		final File file = new File(dataFolder, "placed-entities.yml");
		assertTrue(file.exists());

		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		assertEquals(1, cfg.getList("chests").size());
		assertEquals(0, cfg.getList("hidden-npcs").size());

		mocks.close();
	}

	@Test
	void forgetRemovesPersistedEntry() throws Exception {
		final PlacedEntityStore store = new PlacedEntityStore(plugin);
		final Location loc = new Location(world, 100.5, 64, 200.5);

		store.record(PlacedEntityStore.Kind.CHEST, loc);
		store.forget(PlacedEntityStore.Kind.CHEST, loc);

		final File file = new File(dataFolder, "placed-entities.yml");
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		assertEquals(0, cfg.getList("chests").size());

		mocks.close();
	}

	@Test
	void entriesRoundTripAcrossInstances() throws Exception {
		final PlacedEntityStore first = new PlacedEntityStore(plugin);
		final Location chestLoc = new Location(world, 100.5, 64, 200.5);
		final Location npcLoc = new Location(world, 50.5, 70, 150.5);
		first.record(PlacedEntityStore.Kind.CHEST, chestLoc);
		first.record(PlacedEntityStore.Kind.HIDDEN_NPC, npcLoc);

		final PlacedEntityStore second = new PlacedEntityStore(plugin);
		final File file = new File(dataFolder, "placed-entities.yml");
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		assertEquals(1, cfg.getList("chests").size());
		assertEquals(1, cfg.getList("hidden-npcs").size());

		// Round-trip does not lose coordinates even after reload
		second.forget(PlacedEntityStore.Kind.CHEST, chestLoc);
		final YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(file);
		assertEquals(0, reloaded.getList("chests").size());
		assertEquals(1, reloaded.getList("hidden-npcs").size());

		mocks.close();
	}
}
