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

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The census stands in for a persisted "issued" flag: it decides one-per
 * -village purely from what is present in the world, so a lost stone frees
 * the village to grant another.
 */
class TeleportStoneCensusTest {

	private ServerMock server;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void anEmptyWorldHoldsNoStone() {
		assertFalse(TeleportStoneCensus.exists("world_10_20"));
		assertFalse(TeleportStoneCensus.exists(null));
	}

	@Test
	void findsAStoneInAPlayerInventory() {
		final PlayerMock player = server.addPlayer();
		player.getInventory()
			.addItem(VillageTeleportStone.create("world_10_20", "Harrowdale"));

		assertTrue(TeleportStoneCensus.exists("world_10_20"));
		assertFalse(TeleportStoneCensus.exists("world_99_99"));
	}

	@Test
	void findsAStoneInAnEnderChest() {
		final PlayerMock player = server.addPlayer();
		player.getEnderChest()
			.addItem(VillageTeleportStone.create("world_10_20", "Harrowdale"));

		assertTrue(TeleportStoneCensus.exists("world_10_20"));
	}

	@Test
	void findsADroppedStoneInALoadedChunk() {
		final WorldMock world = server.addSimpleWorld("drop-test");
		world.dropItem(new Location(world, 0, 64, 0),
			VillageTeleportStone.create("world_10_20", "Harrowdale"));

		assertTrue(TeleportStoneCensus.exists("world_10_20"));
	}
}
