package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkSiteTest {

	@Test
	void flatGroundFits() {
		assertTrue(WorkSite.levelFits(new int[] {64, 64, 64, 64}, 3));
	}

	@Test
	void gentleSlopeFits() {
		assertTrue(WorkSite.levelFits(new int[] {64, 65, 66, 67}, 3));
	}

	@Test
	void cliffIsRejected() {
		assertFalse(WorkSite.levelFits(new int[] {64, 65, 71, 64}, 3));
	}

	@Test
	void emptyGroundIsRejected() {
		assertFalse(WorkSite.levelFits(new int[0], 3));
	}

	@Test
	void medianIgnoresASingleOutlier() {
		assertEquals(64, WorkSite.medianHeight(new int[] {64, 64, 64, 66}));
	}

	@Test
	void medianOfOneIsItself() {
		assertEquals(70, WorkSite.medianHeight(new int[] {70}));
	}

	@Test
	void southIsTheUnrotatedFacing() {
		assertEquals(0, WorkSite.facingRotation(0, 5));
	}

	@Test
	void facingTheOtherWayIsHalfATurn() {
		assertEquals(2, WorkSite.facingRotation(0, -5));
	}

	@Test
	void facingEastAndWestAreQuarterTurns() {
		assertEquals(3, WorkSite.facingRotation(5, 0));
		assertEquals(1, WorkSite.facingRotation(-5, 0));
	}
}
