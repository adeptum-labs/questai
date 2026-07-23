package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class GroundCoverTest {

	/** Reads a column top-down and judges its lowest block. */
	private static GroundCover.Verdict bottomOf(final Material... column) {
		final List<Material> materials = List.of(column);
		return GroundCover.verdict(materials, materials.size() - 1);
	}

	@Test
	void ordinaryGroundIsWhereTheFootingGoes() {
		assertEquals(GroundCover.Verdict.GROUND, bottomOf(Material.GRASS_BLOCK));
		assertEquals(GroundCover.Verdict.GROUND, bottomOf(Material.SAND));
		assertEquals(GroundCover.Verdict.GROUND, bottomOf(Material.STONE));
	}

	@Test
	void waterLavaAndIceCannotBeCleared() {
		assertEquals(GroundCover.Verdict.BLOCKED, bottomOf(Material.WATER));
		assertEquals(GroundCover.Verdict.BLOCKED, bottomOf(Material.LAVA));
		assertEquals(GroundCover.Verdict.BLOCKED, bottomOf(Material.ICE));
	}

	@Test
	void growthAndDriftComeOff() {
		assertEquals(GroundCover.Verdict.COVER, bottomOf(Material.OAK_LEAVES));
		assertEquals(GroundCover.Verdict.COVER, bottomOf(Material.SNOW));
		assertEquals(GroundCover.Verdict.COVER, bottomOf(Material.SUGAR_CANE));
	}

	@Test
	void aTrunkUnderItsCanopyIsATree() {
		assertEquals(GroundCover.Verdict.COVER, bottomOf(Material.OAK_LEAVES,
			Material.OAK_LEAVES, Material.AIR, Material.OAK_LOG));
	}

	@Test
	void aBareLogIsSomebodysWall() {
		assertEquals(GroundCover.Verdict.BUILT,
			bottomOf(Material.SPRUCE_LOG, Material.SPRUCE_LOG));
	}

	@Test
	void villageAndPlayerWorkTurnsASiteAway() {
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.OAK_PLANKS));
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.DIRT_PATH));
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.FARMLAND));
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.CHEST));
	}

	@Test
	void airSaysNothingEitherWay() {
		assertEquals(GroundCover.Verdict.EMPTY, bottomOf(Material.AIR));
	}

	@Test
	void coverIsGrowthAndTimberButNeverStone() {
		assertTrue(GroundCover.isCover(Material.BIRCH_LEAVES));
		assertTrue(GroundCover.isCover(Material.STRIPPED_OAK_LOG));
		assertTrue(GroundCover.isCover(Material.CACTUS));
		assertFalse(GroundCover.isCover(Material.COBBLESTONE));
		assertFalse(GroundCover.isCover(Material.GRASS_BLOCK));
	}
}
