package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WhetstoneRepairTest {

	/** An iron sword, so the numbers read against something real. */
	private static final int SWORD = 250;

	@Test
	void aStoneGivesBackItsShareOfTheWholeLife() {
		assertEquals(100, WhetstoneRepair.repaired(SWORD, 200,
			WhetstoneRepair.FRACTION));
	}

	@Test
	void aPieceIsNeverHonedPastNew() {
		assertEquals(0, WhetstoneRepair.repaired(SWORD, 40,
			WhetstoneRepair.FRACTION));
		assertEquals(0, WhetstoneRepair.repaired(SWORD, 0,
			WhetstoneRepair.FRACTION));
	}

	@Test
	void theShareIsOfTheLifeAndNotOfTheWear() {
		final int fromRuined = 249 - WhetstoneRepair.repaired(SWORD, 249,
			WhetstoneRepair.FRACTION);
		final int fromFresh = 120 - WhetstoneRepair.repaired(SWORD, 120,
			WhetstoneRepair.FRACTION);
		assertEquals(fromRuined, fromFresh,
			"grinding a piece down first should not pay better");
	}

	@Test
	void aWholePieceIsNotWorthAStone() {
		assertFalse(WhetstoneRepair.worthUsing(SWORD, 0));
		assertTrue(WhetstoneRepair.worthUsing(SWORD, 1));
	}

	@Test
	void somethingThatCannotWearIsNeverWorthAStone() {
		assertFalse(WhetstoneRepair.worthUsing(0, 0));
		assertFalse(WhetstoneRepair.worthUsing(0, 5));
	}
}
