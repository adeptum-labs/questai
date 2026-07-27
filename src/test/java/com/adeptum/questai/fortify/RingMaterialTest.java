package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Pins which materials the ring band may blame a player for breaking —
 * the band is positional, so the material filter is what keeps a player's
 * own builds along the ring line out of the ledger.
 */
class RingMaterialTest {

	@BeforeEach
	void setUp() {
		MockBukkit.mock();
	}

	@AfterEach
	void tearDown() {
		MockBukkit.unmock();
	}

	@Test
	void wallStuffAlongTheRingCounts() {
		assertTrue(BuiltBlocks.ringMaterial(Material.OAK_LOG));
		assertTrue(BuiltBlocks.ringMaterial(Material.SPRUCE_FENCE));
		assertTrue(BuiltBlocks.ringMaterial(Material.COBBLESTONE));
		assertTrue(BuiltBlocks.ringMaterial(Material.STRIPPED_SPRUCE_LOG));
		assertTrue(BuiltBlocks.ringMaterial(Material.LANTERN));
	}

	@Test
	void aPlayersOwnBuildsAlongTheRingDoNot() {
		assertFalse(BuiltBlocks.ringMaterial(Material.DIRT));
		assertFalse(BuiltBlocks.ringMaterial(Material.GLASS));
		assertFalse(BuiltBlocks.ringMaterial(Material.CHEST));
	}
}
