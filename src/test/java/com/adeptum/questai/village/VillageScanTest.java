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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.questai.AutoVillagerPlugin;
import com.adeptum.questai.model.VillageInfo;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Drives the whole survey the way the server does: the ground copied on the
 * server thread, the block walk off it, the verdict handed back on it.
 *
 * <p>Only the middle chunk is loaded, so the survey has one chunk's worth of
 * ground to read and skips the rest — which is also what proves it leaves
 * unloaded ground alone. Doorways are laid a block at a time here because a
 * mock door reports both of its halves as the lower one; the real rule about
 * halves is pinned down in {@link VillageSurveyTest} instead.
 */
class VillageScanTest {

	private static final int GROUND_Y = 64;
	private static final int VERTICAL_REACH = 8;
	private static final int CENTRE = 8;

	private ServerMock server;
	private World world;
	private AutoVillagerPlugin scanner;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		world = server.addSimpleWorld("scan-test");
		world.loadChunk(0, 0);
		fillWithAir();
		scanner = new AutoVillagerPlugin(MockBukkit.createMockPlugin(), List.of());
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** Gives the middle chunk ground for the survey to read across. */
	private void fillWithAir() {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				for (int y = GROUND_Y - VERTICAL_REACH;
					y <= GROUND_Y + VERTICAL_REACH; y++) {

					world.getBlockAt(x, y, z).setType(Material.AIR);
				}
			}
		}
	}

	private void put(final int x, final int z, final Material type) {
		world.getBlockAt(x, GROUND_Y, z).setType(type);
	}

	private void fourDoorways() {
		put(2, 2, Material.OAK_DOOR);
		put(4, 2, Material.OAK_DOOR);
		put(6, 2, Material.OAK_DOOR);
		put(8, 2, Material.SPRUCE_DOOR);
	}

	/** Runs the survey through both thread hops and returns its verdict. */
	private VillageInfo scanAt(final int x, final int z) {
		final AtomicReference<VillageInfo> caught = new AtomicReference<>();
		scanner.scan(new Location(world, x, GROUND_Y, z), caught::set);

		server.getScheduler().waitAsyncTasksFinished();
		server.getScheduler().performOneTick();
		return caught.get();
	}

	@Test
	void doorsAndWorkstationsTogetherMakeAVillage() {
		fourDoorways();
		put(2, 6, Material.LECTERN);
		put(4, 6, Material.BARREL);
		put(6, 6, Material.BELL);

		final VillageInfo info = scanAt(CENTRE, CENTRE);

		assertNotNull(info, "the survey never came back");
		assertTrue(info.village());
		assertEquals(4, info.doorCount());
		assertEquals(3, info.workstationCount());
	}

	@Test
	void doorsWithoutWorkstationsAreJustHouses() {
		fourDoorways();

		final VillageInfo info = scanAt(CENTRE, CENTRE);

		assertNotNull(info);
		assertFalse(info.village());
		assertEquals(4, info.doorCount());
		assertEquals(0, info.workstationCount());
	}

	@Test
	void tooFewDoorwaysAreNoVillageEither() {
		put(2, 2, Material.OAK_DOOR);
		put(2, 6, Material.LECTERN);
		put(4, 6, Material.BARREL);
		put(6, 6, Material.BELL);

		final VillageInfo info = scanAt(CENTRE, CENTRE);

		assertNotNull(info);
		assertFalse(info.village());
		assertEquals(1, info.doorCount());
	}

	@Test
	void barePlainsHoldNoVillage() {
		final VillageInfo info = scanAt(CENTRE, CENTRE);

		assertNotNull(info);
		assertFalse(info.village());
		assertEquals(0, info.doorCount());
		assertEquals(0, info.workstationCount());
	}

	/** Ground nobody has loaded is skipped rather than dragged in. */
	@Test
	void unloadedGroundIsLeftAlone() {
		fourDoorways();
		put(2, 6, Material.LECTERN);
		put(4, 6, Material.BARREL);
		put(6, 6, Material.BELL);

		final VillageInfo info = scanAt(5000, 5000);

		assertNotNull(info, "the survey never came back");
		assertFalse(info.village());
		assertEquals(0, info.doorCount());
	}
}
