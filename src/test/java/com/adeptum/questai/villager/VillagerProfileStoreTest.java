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

package com.adeptum.questai.villager;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillagerProfileStoreTest {

	@Mock private JavaPlugin plugin;

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
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	private static VillagerPersona persona(final String name) {
		return new VillagerPersona(name,
			List.of("gruff", "kind"), "Back again, traveler?");
	}

	@Test
	void registerRoundTripsAcrossInstances() {
		final UUID villagerId = UUID.randomUUID();
		final VillagerProfileStore first = new VillagerProfileStore(plugin);
		final String name = first.register(villagerId,
			persona("Edric Stone"), "FARMER");
		assertEquals("Edric Stone", name);

		final VillagerProfileStore second = new VillagerProfileStore(plugin);
		final VillagerProfile profile = second.get(villagerId);
		assertNotNull(profile);
		assertEquals("Edric Stone", profile.getName());
		assertEquals("FARMER", profile.getProfession());
		assertEquals(List.of("gruff", "kind"), profile.getTraits());
		assertEquals("Back again, traveler?", profile.getGreeting());
	}

	@Test
	void registerAppendsSuffixWhenNameTaken() {
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(UUID.randomUUID(), persona("Edric Stone"), "FARMER");

		final String second = store.register(UUID.randomUUID(),
			persona("Edric Stone"), "CLERIC");
		assertNotEquals("Edric Stone", second);
		assertTrue(second.startsWith("Edric Stone_"));
	}

	@Test
	void recordConversationIncrementsCountAndLastSeen() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");

		final long before = System.currentTimeMillis();
		store.recordConversation(villagerId, playerId);
		store.recordConversation(villagerId, playerId);

		final PlayerMemory memory = store.get(villagerId).getPlayers().get(playerId);
		assertEquals(2, memory.getConversations());
		assertTrue(memory.getLastSeen() >= before);
	}

	@Test
	void recordEventRoundTripsAndCapsAtEight() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");

		for (int i = 0; i < 12; i++) {
			store.recordEvent(villagerId, playerId,
				MemoryEvent.Type.QUEST_COMPLETED, "Quest " + i);
		}

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final List<MemoryEvent> events =
			reloaded.get(villagerId).getPlayers().get(playerId).getEvents();
		assertEquals(8, events.size());
		assertEquals("Quest 4", events.get(0).questTitle());
		assertEquals("Quest 11", events.get(7).questTitle());
		assertEquals(MemoryEvent.Type.QUEST_COMPLETED, events.get(0).type());
	}

	@Test
	void recordsAreIgnoredForUnknownVillagers() {
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		final UUID unknown = UUID.randomUUID();

		store.recordConversation(unknown, UUID.randomUUID());
		store.recordEvent(unknown, UUID.randomUUID(),
			MemoryEvent.Type.QUEST_ACCEPTED, "Quest");

		assertNull(store.get(unknown));
	}

	@Test
	void migratesLegacyNamesFromConfigOnce() throws Exception {
		final UUID villagerId = UUID.randomUUID();
		final YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("openai.api-key", "placeholder");
		cfg.set("villagerUniqueNames." + villagerId, "Old Name");
		cfg.save(new File(dataFolder, "config.yml"));

		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		assertEquals("Old Name", store.getName(villagerId));
		assertTrue(store.get(villagerId).getTraits().isEmpty());
		assertNull(store.get(villagerId).getGreeting());
		assertTrue(new File(dataFolder, "villager-profiles.yml").exists());

		final YamlConfiguration updated = YamlConfiguration.loadConfiguration(
			new File(dataFolder, "config.yml"));
		assertNull(updated.getConfigurationSection("villagerUniqueNames"));
		assertEquals("placeholder", updated.getString("openai.api-key"));

		// Second construction sees no legacy section and keeps the profile
		final VillagerProfileStore again = new VillagerProfileStore(plugin);
		assertEquals("Old Name", again.getName(villagerId));
	}

	@Test
	void malformedProfileEntriesAreSkipped() throws Exception {
		final String yaml = """
			villagers:
			  not-a-uuid:
			    name: Broken
			  %s:
			    name: Valid Name
			""".formatted(UUID.randomUUID());
		Files.writeString(
			new File(dataFolder, "villager-profiles.yml").toPath(), yaml);

		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		assertDoesNotThrow(store::save);
	}

	@Test
	void oldPlayerMemoryIsPrunedOnLoad() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");
		store.recordConversation(villagerId, playerId);

		// Age the memory beyond the retention window and persist it
		store.get(villagerId).getPlayers().get(playerId)
			.setLastSeen(System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000);
		store.save();

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertTrue(reloaded.get(villagerId).getPlayers().isEmpty());
	}
}
