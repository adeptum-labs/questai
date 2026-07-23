package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillageExtentTest {

	/** A ring of villagers at this distance from the given middle. */
	private static List<VillageExtent.Point> ringOf(final int count,
		final double centreX, final double centreZ, final double radius) {

		final List<VillageExtent.Point> points = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			final double angle = 2 * Math.PI * i / count;
			points.add(new VillageExtent.Point(
				centreX + radius * Math.cos(angle),
				centreZ + radius * Math.sin(angle)));
		}
		return points;
	}

	@Test
	void tooFewVillagersFallBackToTheStoredCentre() {
		final VillageExtent.Extent extent =
			VillageExtent.measure(ringOf(2, 100, -40, 20), 12.7, -3.2);
		assertEquals(13, extent.centreX());
		assertEquals(-3, extent.centreZ());
		assertEquals(24, extent.radius());
	}

	@Test
	void theCrowdOverrulesTheStoredCentre() {
		final VillageExtent.Extent extent =
			VillageExtent.measure(ringOf(12, 200, 80, 24), 260, 140);
		assertEquals(200, extent.centreX(), 4);
		assertEquals(80, extent.centreZ(), 4);
	}

	@Test
	void oneWandererDoesNotDragTheMiddle() {
		final List<VillageExtent.Point> crowd = ringOf(11, 0, 0, 20);
		crowd.add(new VillageExtent.Point(900, 900));

		final VillageExtent.Extent extent = VillageExtent.measure(crowd, 0, 0);
		assertTrue(Math.abs(extent.centreX()) <= 20,
			"the stray pulled the centre to " + extent.centreX());
		assertTrue(extent.radius() <= 64, "the stray widened the village");
	}

	@Test
	void theRadiusReachesTheVillagersButNotFurther() {
		assertEquals(40, VillageExtent.measure(ringOf(16, 0, 0, 40), 0, 0).radius(),
			2);
	}

	@Test
	void aHuddleStillCountsAsAVillage() {
		assertEquals(16, VillageExtent.measure(ringOf(6, 0, 0, 3), 0, 0).radius());
	}

	@Test
	void theWatchtowerStandsOutsideTheHouses() {
		final VillageExtent.Extent extent = new VillageExtent.Extent(0, 0, 32);
		final int[] band = VillageExtent.band(extent, VillageWork.WATCHTOWER);
		assertEquals(40, band[0]);
		assertEquals(56, band[1]);
	}

	@Test
	void theBellTowerStandsAmongThem() {
		final VillageExtent.Extent extent = new VillageExtent.Extent(0, 0, 30);
		final int[] band = VillageExtent.band(extent, VillageWork.BELL_TOWER);
		assertEquals(10, band[0]);
		assertEquals(20, band[1]);
		assertTrue(band[1] < 30, "the bell tower wandered out of the village");
	}

	@Test
	void tiersThatSiteThemselvesHaveNoBand() {
		final VillageExtent.Extent extent = new VillageExtent.Extent(0, 0, 32);
		assertNull(VillageExtent.band(extent, VillageWork.PALISADE));
		assertNull(VillageExtent.band(extent, VillageWork.GATE));
	}
}
