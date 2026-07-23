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
	void spreadIsTheDropFromHighestToLowest() {
		assertEquals(0, WorkSite.spread(new int[] {64, 64, 64}));
		assertEquals(7, WorkSite.spread(new int[] {64, 71, 66}));
		assertEquals(0, WorkSite.spread(new int[0]));
	}

	@Test
	void flatterGroundWinsOverClearerGround() {
		final WorkSite.Rating flat = new WorkSite.Rating(1, 40);
		final WorkSite.Rating clear = new WorkSite.Rating(3, 0);
		assertTrue(flat.betterThan(clear));
		assertFalse(clear.betterThan(flat));
	}

	@Test
	void equalGroundIsSettledByTheTreeCount() {
		final WorkSite.Rating pasture = new WorkSite.Rating(2, 3);
		final WorkSite.Rating wood = new WorkSite.Rating(2, 30);
		assertTrue(pasture.betterThan(wood));
		assertFalse(wood.betterThan(pasture));
	}

	@Test
	void anythingBeatsHavingFoundNothing() {
		assertTrue(new WorkSite.Rating(5, 81).betterThan(null));
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
