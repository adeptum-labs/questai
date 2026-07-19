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
	void locationRoundTripsAcrossInstances() {
		final UUID villagerId = UUID.randomUUID();
		final UUID worldId = UUID.randomUUID();
		final org.bukkit.World world = mock(org.bukkit.World.class);
		when(world.getUID()).thenReturn(worldId);

		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");
		store.updateLocation(villagerId,
			new org.bukkit.Location(world, 100.5, 70, -200.5));

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final StoredLocation location = reloaded.get(villagerId).getLocation();
		assertNotNull(location);
		assertEquals(worldId, location.worldId());
		assertEquals(100.5, location.x());
		assertEquals(-200.5, location.z());
	}

	@Test
	void clearLocationForgetsThePosition() {
		final UUID villagerId = UUID.randomUUID();
		final org.bukkit.World world = mock(org.bukkit.World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());

		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");
		store.updateLocation(villagerId,
			new org.bukkit.Location(world, 1, 2, 3));
		store.clearLocation(villagerId);

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertNull(reloaded.get(villagerId).getLocation());
	}

	@Test
	void profilesWithoutLocationLoadAsNull() {
		final UUID villagerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Mira Bloom"), "LIBRARIAN");

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertNull(reloaded.get(villagerId).getLocation());
	}

	private static HearsayEvent hearsay(final String title, final String source) {
		return new HearsayEvent(MemoryEvent.Type.QUEST_COMPLETED,
			title, source, System.currentTimeMillis());
	}

	@Test
	void hearsayRoundTripsAcrossInstances() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");

		assertTrue(store.addHearsay(villagerId, playerId,
			hearsay("Turnip Trouble", "Edric Stone")));
		store.save();

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final HearsayEvent loaded = reloaded.get(villagerId)
			.getPlayers().get(playerId).getHearsay().get(0);
		assertEquals(MemoryEvent.Type.QUEST_COMPLETED, loaded.type());
		assertEquals("Turnip Trouble", loaded.questTitle());
		assertEquals("Edric Stone", loaded.sourceName());
	}

	@Test
	void hearsayCapsAtFourDroppingTheOldest() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");

		for (int i = 0; i < 6; i++) {
			store.addHearsay(villagerId, playerId,
				hearsay("Quest " + i, "Edric Stone"));
		}

		final List<HearsayEvent> stored = store.get(villagerId)
			.getPlayers().get(playerId).getHearsay();
		assertEquals(4, stored.size());
		assertEquals("Quest 2", stored.get(0).questTitle());
		assertEquals("Quest 5", stored.get(3).questTitle());
	}

	@Test
	void duplicateHearsayIsIgnored() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");

		assertTrue(store.addHearsay(villagerId, playerId,
			hearsay("Turnip Trouble", "Edric Stone")));
		assertFalse(store.addHearsay(villagerId, playerId,
			hearsay("Turnip Trouble", "Edric Stone")));
		assertEquals(1, store.get(villagerId)
			.getPlayers().get(playerId).getHearsay().size());
	}

	@Test
	void hearsayAboutOwnDeedsIsIgnored() {
		final UUID villagerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");

		assertFalse(store.addHearsay(villagerId, UUID.randomUUID(),
			hearsay("Turnip Trouble", "Goran Dusk")));
	}

	@Test
	void hearsayOnlyMemorySurvivesReload() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");
		store.addHearsay(villagerId, playerId,
			hearsay("Turnip Trouble", "Edric Stone"));
		store.save();

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final PlayerMemory memory =
			reloaded.get(villagerId).getPlayers().get(playerId);
		assertNotNull(memory);
		assertEquals(0, memory.getConversations());
		assertEquals(1, memory.getHearsay().size());
	}

	@Test
	void staleHearsayIsPrunedOnLoad() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Goran Dusk"), "CLERIC");
		store.addHearsay(villagerId, playerId,
			new HearsayEvent(MemoryEvent.Type.QUEST_COMPLETED, "Old News",
				"Edric Stone",
				System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000));
		store.save();

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertNull(reloaded.get(villagerId).getPlayers().get(playerId));
	}

	@Test
	void tiesAreSymmetricAndRoundTrip() {
		final UUID firstId = UUID.randomUUID();
		final UUID secondId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(firstId, persona("Edric Dusk"), "FARMER");
		store.register(secondId, persona("Mira Dusk"), "LIBRARIAN");

		assertTrue(store.addTie(firstId, secondId, Relationship.Type.KIN));
		store.save();

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final Relationship forward = reloaded.tieBetween(firstId, secondId);
		final Relationship backward = reloaded.tieBetween(secondId, firstId);
		assertEquals(Relationship.Type.KIN, forward.type());
		assertEquals("Mira Dusk", forward.otherName());
		assertEquals("Edric Dusk", backward.otherName());
		assertEquals(java.util.Set.of(secondId),
			reloaded.tieTargets(firstId));
	}

	@Test
	void tiesRespectCapAndDedupe() {
		final UUID firstId = UUID.randomUUID();
		final UUID secondId = UUID.randomUUID();
		final UUID thirdId = UUID.randomUUID();
		final UUID fourthId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(firstId, persona("Edric Dusk"), "FARMER");
		store.register(secondId, persona("Mira Bloom"), "LIBRARIAN");
		store.register(thirdId, persona("Goran Ashe"), "CLERIC");
		store.register(fourthId, persona("Sela Reed"), "FISHERMAN");

		assertTrue(store.addTie(firstId, secondId,
			Relationship.Type.OLD_FRIEND));
		assertFalse(store.addTie(secondId, firstId, Relationship.Type.RIVAL));
		assertTrue(store.addTie(firstId, thirdId, Relationship.Type.RIVAL));
		assertFalse(store.addTie(firstId, fourthId, Relationship.Type.KIN));
		assertFalse(store.addTie(UUID.randomUUID(), secondId,
			Relationship.Type.KIN));
	}

	@Test
	void malformedRelationshipRowsAreSkipped() throws Exception {
		final UUID villagerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Dusk"), "FARMER");
		store.save();

		final File file = new File(dataFolder, "villager-profiles.yml");
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		cfg.set("villagers." + villagerId + ".relationships", java.util.List.of(
			java.util.Map.of("type", "NOT_A_TYPE", "other", "x", "name", "y")));
		cfg.save(file);

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertTrue(reloaded.get(villagerId).getRelationships().isEmpty());
	}

	@Test
	void gossipRoundFormsKinBetweenNearbyNamesakes() {
		final UUID firstId = UUID.randomUUID();
		final UUID secondId = UUID.randomUUID();
		final org.bukkit.World world = mock(org.bukkit.World.class);
		when(world.getUID()).thenReturn(UUID.randomUUID());
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(firstId, persona("Edric Dusk"), "FARMER");
		store.register(secondId, persona("Mira Dusk"), "LIBRARIAN");
		store.updateLocation(firstId, new org.bukkit.Location(world, 0, 64, 0));
		store.updateLocation(secondId,
			new org.bukkit.Location(world, 10, 64, 0));

		// Only one pair exists and kinship needs no roll — deterministic
		store.spreadGossip();

		assertEquals(Relationship.Type.KIN,
			store.tieBetween(firstId, secondId).type());
		assertEquals(Relationship.Type.KIN,
			store.tieBetween(secondId, firstId).type());
	}

	@Test
	void openChainRoundTripsAcrossInstances() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Stone"), "FARMER");
		store.recordConversation(villagerId, playerId);

		assertTrue(store.openChain(villagerId, playerId, 3, "The Lost Ledger"));

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final ChainState chain = reloaded.chainState(villagerId, playerId);
		assertNotNull(chain);
		assertEquals(2, chain.step());
		assertEquals(3, chain.length());
		assertEquals("The Lost Ledger", chain.lastTitle());
	}

	@Test
	void openChainRefusedWhilePendingOrUnknown() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Stone"), "FARMER");

		assertFalse(store.openChain(UUID.randomUUID(), playerId, 2, "X"));
		assertTrue(store.openChain(villagerId, playerId, 2, "First"));
		assertFalse(store.openChain(villagerId, playerId, 3, "Second"));
		assertEquals("First",
			store.chainState(villagerId, playerId).lastTitle());
	}

	@Test
	void advanceChainBumpsStepAndTitle() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Stone"), "FARMER");
		store.recordConversation(villagerId, playerId);
		store.openChain(villagerId, playerId, 3, "Step One");

		store.advanceChain(villagerId, playerId, "Step Two");

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		final ChainState chain = reloaded.chainState(villagerId, playerId);
		assertEquals(3, chain.step());
		assertEquals("Step Two", chain.lastTitle());
	}

	@Test
	void clearChainForgetsTheChain() {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Stone"), "FARMER");
		store.recordConversation(villagerId, playerId);
		store.openChain(villagerId, playerId, 2, "Step One");

		store.clearChain(villagerId, playerId);

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertNull(reloaded.chainState(villagerId, playerId));
	}

	@Test
	void malformedChainStateIsSkippedOnLoad() throws Exception {
		final UUID villagerId = UUID.randomUUID();
		final UUID playerId = UUID.randomUUID();
		final VillagerProfileStore store = new VillagerProfileStore(plugin);
		store.register(villagerId, persona("Edric Stone"), "FARMER");
		store.recordConversation(villagerId, playerId);
		store.save();

		final File file = new File(dataFolder, "villager-profiles.yml");
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		final String base = "villagers." + villagerId + ".players." + playerId;
		cfg.set(base + ".chain.step", 9);
		cfg.set(base + ".chain.length", 2);
		cfg.set(base + ".chain.lastTitle", "Broken");
		cfg.save(file);

		final VillagerProfileStore reloaded = new VillagerProfileStore(plugin);
		assertNull(reloaded.chainState(villagerId, playerId));
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
