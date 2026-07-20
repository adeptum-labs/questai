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

package com.adeptum.questai.utility;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NaturalTerrainTest {

	@Test
	void ordinaryGroundCounts() {
		assertTrue(NaturalTerrain.isSurface(Material.GRASS_BLOCK));
		assertTrue(NaturalTerrain.isSurface(Material.STONE));
		assertTrue(NaturalTerrain.isSurface(Material.SAND));
		assertTrue(NaturalTerrain.isSurface(Material.TERRACOTTA));
		assertTrue(NaturalTerrain.isSurface(Material.WHITE_TERRACOTTA));
	}

	@Test
	void theSurfacesThatStrandedRaidersAreRejected() {
		// The motion-blocking heightmap reports each of these as a top block
		assertFalse(NaturalTerrain.isSurface(Material.OAK_LEAVES));
		assertFalse(NaturalTerrain.isSurface(Material.WATER));
		assertFalse(NaturalTerrain.isSurface(Material.OAK_PLANKS));
		assertFalse(NaturalTerrain.isSurface(Material.COBBLESTONE));
		assertFalse(NaturalTerrain.isSurface(Material.WHITE_GLAZED_TERRACOTTA));
	}

	@Test
	void airAndLivingBlocksAreNotGround() {
		assertFalse(NaturalTerrain.isSurface(Material.AIR));
		assertFalse(NaturalTerrain.isSurface(Material.OAK_LOG));
		assertFalse(NaturalTerrain.isSurface(Material.LAVA));
		assertFalse(NaturalTerrain.isSurface(Material.CHEST));
	}
}
