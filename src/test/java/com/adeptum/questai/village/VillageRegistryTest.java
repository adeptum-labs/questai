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

package com.adeptum.questai.village;

import com.adeptum.questai.event.VillageKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VillageRegistryTest {

	private static final double RADIUS = 48.0;
	private static final UUID WORLD_ID =
		UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	@Mock private JavaPlugin plugin;
	@Mock private World world;

	@TempDir
	private Path tempDir;
	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
		// Silenced: the malformed-row test rightly triggers the registry's
		// skip-and-warn path, and its stack trace would read as a failure
		final Logger quiet = Logger.getAnonymousLogger();
		quiet.setUseParentHandlers(false);
		when(plugin.getLogger()).thenReturn(quiet);
		when(world.getUID()).thenReturn(WORLD_ID);
	}

	@AfterEach
	void tearDown() throws Exception {
		mocks.close();
	}

	private Location at(final double x, final double z) {
		return new Location(world, x, 64, z);
	}

	private VillageRegistry registry() {
		return new VillageRegistry(plugin, RADIUS);
	}

	@Test
	void anUnknownLocationHasNoVillage() {
		assertNull(registry().find(at(0, 0)));
		assertNull(registry().find(null));
	}

	@Test
	void aClaimedVillageIsFoundWithinItsRadius() {
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 100, 100), at(100, 100),
			"Ravenhollow");

		assertEquals("Ravenhollow", registry.find(at(100, 100)).name());
		assertEquals("Ravenhollow", registry.find(at(140, 100)).name());
	}

	@Test
	void aLocationBeyondTheRadiusIsOutside() {
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 0, 0), at(0, 0), "Ravenhollow");

		assertNull(registry.find(at(RADIUS + 5, 0)));
	}

	@Test
	void oneVillageKeepsItsNameAcrossACellBoundary() {
		// The centre sits just inside cell 0; 64 blocks east is cell 1, which
		// would be a different key but is the same village on the ground
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 40, 40), at(40, 40), "Ravenhollow");

		assertEquals(0, Math.floorDiv(40, 64));
		assertEquals(1, Math.floorDiv(80, 64));
		assertEquals("Ravenhollow", registry.find(at(80, 40)).name());
	}

	@Test
	void theNearestVillageWinsWhenClaimsOverlap() {
		// 80 apart, so both radii cover x=35 and the tie has to be broken
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 0, 0), at(0, 0), "Ravenhollow");
		registry.claim(VillageKey.from(WORLD_ID, 80, 0), at(80, 0), "Frostmere");

		assertEquals("Ravenhollow", registry.find(at(35, 0)).name());
		assertEquals("Frostmere", registry.find(at(45, 0)).name());
	}

	@Test
	void twoVillagesSharingACellBothKeepTheirNames() {
		// Both centres bucket to cell 0, so a cell-keyed store would drop one
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 0, 0), at(0, 0), "Ravenhollow");
		registry.claim(VillageKey.from(WORLD_ID, 60, 0), at(60, 0), "Frostmere");

		assertEquals(2, registry.size());
		assertEquals("Ravenhollow", registry.find(at(0, 0)).name());
		assertEquals("Frostmere", registry.find(at(60, 0)).name());
	}

	@Test
	void aClaimSurvivesAcrossInstances() {
		registry().claim(VillageKey.from(WORLD_ID, -300, 250), at(-300, 250),
			"Elder Mere");

		final NamedVillage reloaded = registry().find(at(-300, 250));
		assertNotNull(reloaded);
		assertEquals("Elder Mere", reloaded.name());
		assertEquals(WORLD_ID, reloaded.centre().worldId());
		assertEquals(-300, reloaded.centre().x());
	}

	@Test
	void otherWorldsAreNeverMatched() {
		final VillageRegistry registry = registry();
		registry.claim(VillageKey.from(WORLD_ID, 0, 0), at(0, 0), "Ravenhollow");

		final World other = mock(World.class);
		when(other.getUID()).thenReturn(UUID.randomUUID());
		assertNull(registry.find(new Location(other, 0, 64, 0)));
	}

	@Test
	void aMalformedRowDoesNotCostTheOthersTheirNames() throws Exception {
		registry().claim(VillageKey.from(WORLD_ID, 0, 0), at(0, 0), "Ravenhollow");

		// A world that is not a UUID throws inside the per-row parse
		final Path file = tempDir.resolve("village-names.yml");
		Files.writeString(file, Files.readString(file)
			+ "  not_a_key:\n    name: Broken\n    world: nonsense\n");

		assertEquals("Ravenhollow", registry().find(at(0, 0)).name());
	}
}
