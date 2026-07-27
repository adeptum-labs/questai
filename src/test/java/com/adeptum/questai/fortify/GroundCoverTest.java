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
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.FARMLAND));
		assertEquals(GroundCover.Verdict.BUILT, bottomOf(Material.CHEST));
	}

	@Test
	void aTroddenPathIsItsOwnAnswer() {
		assertEquals(GroundCover.Verdict.TRODDEN, bottomOf(Material.DIRT_PATH));
	}

	@Test
	void onlyGroundAndPathsCarryAFooting() {
		assertTrue(GroundCover.Verdict.GROUND.footable());
		assertTrue(GroundCover.Verdict.TRODDEN.footable());
		assertFalse(GroundCover.Verdict.BUILT.footable());
		assertFalse(GroundCover.Verdict.BLOCKED.footable());
		assertFalse(GroundCover.Verdict.COVER.footable());
		assertFalse(GroundCover.Verdict.EMPTY.footable());
	}

	@Test
	void growthIsCoverWithoutTheTimber() {
		assertTrue(GroundCover.isGrowth(Material.BIRCH_LEAVES));
		assertTrue(GroundCover.isGrowth(Material.SWEET_BERRY_BUSH));
		assertFalse(GroundCover.isGrowth(Material.STRIPPED_OAK_LOG),
			"bare timber might be somebody's wall");
		assertFalse(GroundCover.isGrowth(Material.COBBLESTONE));
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
