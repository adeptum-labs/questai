package com.adeptum.questai.reputation;

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
class DemolitionWatchTest {

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
		assertTrue(DemolitionWatch.ringMaterial(Material.OAK_LOG));
		assertTrue(DemolitionWatch.ringMaterial(Material.SPRUCE_FENCE));
		assertTrue(DemolitionWatch.ringMaterial(Material.COBBLESTONE));
		assertTrue(DemolitionWatch.ringMaterial(Material.STRIPPED_SPRUCE_LOG));
		assertTrue(DemolitionWatch.ringMaterial(Material.LANTERN));
	}

	@Test
	void aPlayersOwnBuildsAlongTheRingDoNot() {
		assertFalse(DemolitionWatch.ringMaterial(Material.DIRT));
		assertFalse(DemolitionWatch.ringMaterial(Material.GLASS));
		assertFalse(DemolitionWatch.ringMaterial(Material.CHEST));
	}
}
