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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class VillageSurveyTest {

	private static final int GROUND_Y = 64;

	@BeforeEach
	void setUp() {
		// The door tag reads off the server's registry
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** A block in the laid-out world, keyed the way the reader asks for it. */
	private record Spot(int x, int y, int z) {
	}

	/**
	 * Stands in for a loaded world. Records every read so a test can show
	 * that the survey never touched a chunk it was told to skip.
	 */
	private static final class FakeBlocks implements VillageSurvey.Blocks {
		private final Map<Spot, Material> types = new HashMap<>();
		private final Set<Spot> doorTops = new HashSet<>();
		private final Set<Long> unloaded = new HashSet<>();
		private int reads;

		void put(final int x, final int y, final int z, final Material type) {
			types.put(new Spot(x, y, z), type);
		}

		/** Lays a whole door, whose upper half must not be counted twice. */
		void door(final int x, final int y, final int z) {
			put(x, y, z, Material.OAK_DOOR);
			put(x, y + 1, z, Material.OAK_DOOR);
			doorTops.add(new Spot(x, y + 1, z));
		}

		void unload(final int chunkX, final int chunkZ) {
			unloaded.add(key(chunkX, chunkZ));
		}

		private static long key(final int chunkX, final int chunkZ) {
			return (long) chunkX << 32 | chunkZ & 0xffff_ffffL;
		}

		@Override
		public boolean openChunk(final int chunkX, final int chunkZ) {
			return !unloaded.contains(key(chunkX, chunkZ));
		}

		@Override
		public Material typeAt(final int x, final int y, final int z) {
			reads++;
			return types.getOrDefault(new Spot(x, y, z), Material.AIR);
		}

		@Override
		public boolean isDoorBottom(final int x, final int y, final int z) {
			return !doorTops.contains(new Spot(x, y, z));
		}
	}

	private static VillageSurvey.Bounds box(final int radius,
		final int verticalRadius) {

		return VillageSurvey.around(0, GROUND_Y, 0, radius, verticalRadius,
			-64, 320);
	}

	@Test
	void anEmptyBoxHoldsNoVillage() {
		final VillageSurvey.Tally tally =
			VillageSurvey.count(box(8, 4), new FakeBlocks());

		assertEquals(0, tally.doors());
		assertEquals(0, tally.workstations());
	}

	@Test
	void doorsAndWorkstationsAreTalliedApart() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.door(1, GROUND_Y, 1);
		blocks.door(3, GROUND_Y, 1);
		blocks.put(5, GROUND_Y, 1, Material.LECTERN);
		blocks.put(5, GROUND_Y, 3, Material.BARREL);
		blocks.put(5, GROUND_Y, 5, Material.BELL);

		final VillageSurvey.Tally tally =
			VillageSurvey.count(box(8, 4), blocks);

		assertEquals(2, tally.doors());
		assertEquals(3, tally.workstations());
	}

	@Test
	void onlyTheLowerHalfOfADoorCounts() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.door(0, GROUND_Y, 0);

		assertEquals(1, VillageSurvey.count(box(4, 4), blocks).doors());
	}

	@Test
	void blocksOutsideTheBoxAreLeftAlone() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.door(0, GROUND_Y, 0);
		blocks.door(20, GROUND_Y, 0);
		blocks.put(0, GROUND_Y + 9, 0, Material.LECTERN);

		final VillageSurvey.Tally tally =
			VillageSurvey.count(box(8, 8), blocks);

		assertEquals(1, tally.doors());
		assertEquals(0, tally.workstations());
	}

	@Test
	void aSurveySpansEveryChunkItReachesInto() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.door(-20, GROUND_Y, -20);
		blocks.door(20, GROUND_Y, 20);
		blocks.door(-20, GROUND_Y, 20);
		blocks.door(20, GROUND_Y, -20);

		assertEquals(4, VillageSurvey.count(box(24, 4), blocks).doors());
	}

	@Test
	void anUnloadedChunkIsNeverRead() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.door(20, GROUND_Y, 20);
		blocks.unload(1, 1);

		final VillageSurvey.Tally tally =
			VillageSurvey.count(box(24, 4), blocks);

		assertEquals(0, tally.doors());
		assertTrue(blocks.reads > 0, "the loaded chunks went unread");
	}

	@Test
	void theBoxIsClampedToTheWorldsRoofAndFloor() {
		final VillageSurvey.Bounds bounds =
			VillageSurvey.around(0, 0, 0, 32, 8, -4, 4);

		assertEquals(-4, bounds.minY());
		assertEquals(4, bounds.maxY());
	}

	/**
	 * Air is the bulk of any survey box, so it short-circuits before either
	 * material test. The tally must stay the same either way.
	 */
	@Test
	void airIsSkippedWithoutChangingTheTally() {
		final FakeBlocks blocks = new FakeBlocks();
		blocks.put(0, GROUND_Y, 0, Material.CAVE_AIR);
		blocks.put(1, GROUND_Y, 0, Material.VOID_AIR);
		blocks.put(2, GROUND_Y, 0, Material.COMPOSTER);

		final VillageSurvey.Tally tally =
			VillageSurvey.count(box(4, 2), blocks);

		assertEquals(0, tally.doors());
		assertEquals(1, tally.workstations());
	}
}
