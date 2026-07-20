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

package com.adeptum.questai.mob;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs on a real server because the swap constructs genuine tagged items,
 * which needs a live item factory; the events are built for real too, so
 * what is asserted is exactly what a furnace would hand the player.
 */
class RatCookingTest {

	private ServerMock server;
	private RatCooking cooking;
	private Block block;

	@BeforeEach
	void setUp() {
		server = MockBukkit.mock();
		final World world = server.addSimpleWorld("kitchen");
		block = world.getBlockAt(0, 64, 0);
		cooking = new RatCooking();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	/** The vanilla-shaped recipe the events carry on current Paper. */
	private static FurnaceRecipe recipe() {
		return new FurnaceRecipe(new NamespacedKey("questai", "test_rabbit"),
			new ItemStack(Material.COOKED_RABBIT), Material.RABBIT, 0.0f, 200);
	}

	@Test
	void aFurnaceTurnsRatMeatIntoCookedRatMeat() {
		final FurnaceSmeltEvent event = new FurnaceSmeltEvent(block,
			RatMeat.create(), new ItemStack(Material.COOKED_RABBIT), recipe());

		cooking.onSmelt(event);

		assertTrue(RatMeat.isCookedRatMeat(event.getResult()));
	}

	@Test
	void aCampfireDoesTheSame() {
		final BlockCookEvent event = new BlockCookEvent(block,
			RatMeat.create(), new ItemStack(Material.COOKED_RABBIT), recipe());

		cooking.onCampfireCook(event);

		assertTrue(RatMeat.isCookedRatMeat(event.getResult()));
	}

	@Test
	void anOrdinaryRabbitIsLeftAlone() {
		final FurnaceSmeltEvent event = new FurnaceSmeltEvent(block,
			new ItemStack(Material.RABBIT),
			new ItemStack(Material.COOKED_RABBIT), recipe());

		cooking.onSmelt(event);

		assertFalse(RatMeat.isCookedRatMeat(event.getResult()));
		assertEquals(Material.COOKED_RABBIT, event.getResult().getType());
	}

	@Test
	void cookedRatMeatIsNeverCookedTwice() {
		// A cooked morsel back in the furnace matches no vanilla recipe,
		// but if it ever did, the swap must not fire on it
		final FurnaceSmeltEvent event = new FurnaceSmeltEvent(block,
			RatMeat.createCooked(), new ItemStack(Material.COOKED_RABBIT), recipe());

		cooking.onSmelt(event);

		assertFalse(RatMeat.isCookedRatMeat(event.getResult()));
	}
}
