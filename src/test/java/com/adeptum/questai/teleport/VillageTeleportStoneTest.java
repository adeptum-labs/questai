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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stone must be recognised by its bound village rather than its name,
 * so renaming can neither forge one nor sever the bond.
 */
class VillageTeleportStoneTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void createBindsTheVillageAndRoundTrips() {
		final ItemStack stone =
			VillageTeleportStone.create("world_10_20", "Harrowdale", 120);

		assertEquals("world_10_20", VillageTeleportStone.rowIdOf(stone));
		assertTrue(VillageTeleportStone.isStone(stone));
		assertTrue(VillageTeleportStone.bound(stone, "world_10_20"));
		assertFalse(VillageTeleportStone.bound(stone, "world_99_99"));
	}

	@Test
	void theLoreSaysHowLongTheStoneCools() {
		final ItemStack stone =
			VillageTeleportStone.create("world_10_20", "Harrowdale", 120);

		assertTrue(stone.getItemMeta().getLore().stream()
			.anyMatch(line -> line.contains("2m")),
			"the cooldown has to be readable off the stone itself");
	}

	@Test
	void aSpanReadsTheWayTheStoneWouldSayIt() {
		assertEquals("45s", VillageTeleportStone.duration(45));
		assertEquals("2m", VillageTeleportStone.duration(120));
		assertEquals("1m 30s", VillageTeleportStone.duration(90));
		assertEquals("1s", VillageTeleportStone.duration(0),
			"a countdown that ends on nothing reads as a refusal");
	}

	@Test
	void rejectsUntaggedItemsAndNull() {
		assertNull(VillageTeleportStone.rowIdOf(null));
		assertFalse(VillageTeleportStone.isStone(null));
		assertFalse(VillageTeleportStone.isStone(
			new ItemStack(Material.HEART_OF_THE_SEA)));
		assertFalse(VillageTeleportStone.isStone(new ItemStack(Material.STONE)));
		assertFalse(VillageTeleportStone.bound(null, "world_10_20"));
	}

	@Test
	void holdsFindsTheBoundStoneAmongOtherItems() {
		final ItemStack[] contents = {
			null, new ItemStack(Material.DIRT),
			VillageTeleportStone.create("world_10_20", "Harrowdale", 120)
		};

		assertTrue(VillageTeleportStone.holds(contents, "world_10_20"));
		assertFalse(VillageTeleportStone.holds(contents, "world_99_99"));
		assertFalse(VillageTeleportStone.holds(null, "world_10_20"));
	}
}
