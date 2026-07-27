package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommissionGateTest {

	private static final long MINUTE = 60L * 1000;
	private static final long HOUR = 60 * MINUTE;
	private static final int RESPECTED = 25;
	private static final int REVERED = 75;

	@Test
	void aTradeOpensExactlyAtItsGateAndNotOneStepBelow() {
		assertTrue(CommissionGate.unlocked(Commission.HUNTERS_BOW, 1, RESPECTED));
		assertFalse(CommissionGate.unlocked(Commission.HUNTERS_BOW, 0, RESPECTED),
			"an unbuilt village should not have a bowyer's ear");
		assertFalse(CommissionGate.unlocked(Commission.HUNTERS_BOW, 1,
			RESPECTED - 1), "respect should be earned before the bow");
	}

	@Test
	void aSouredVillageMakesNothingAtAll() {
		for (final Commission commission : Commission.values()) {
			assertFalse(CommissionGate.unlocked(commission, 4, -1),
				commission + " should not be made for a disliked player");
			assertFalse(CommissionGate.unlocked(commission, 4, -100),
				commission + " should not be made for an enemy");
		}
	}

	@Test
	void aTradeOffersTheBestPieceEarnedSoFar() {
		assertEquals(Commission.QUIVER_OF_ARROWS,
			CommissionGate.bestFor("FLETCHER", 0, 0));
		assertEquals(Commission.QUIVER_OF_ARROWS,
			CommissionGate.bestFor("FLETCHER", 1, 0),
			"the bow needs respect, not only a watchtower");
		assertEquals(Commission.HUNTERS_BOW,
			CommissionGate.bestFor("FLETCHER", 1, RESPECTED));
		assertEquals(Commission.EMBERBRAND,
			CommissionGate.bestFor("WEAPONSMITH", 4, REVERED));
	}

	@Test
	void tradesWithNoCatalogueOfferNothing() {
		assertNull(CommissionGate.bestFor("NITWIT", 4, 100));
		assertNull(CommissionGate.bestFor("NONE", 4, 100));
		assertNull(CommissionGate.bestFor("BLACKSMITH_OF_NOWHERE", 4, 100));
		assertNull(CommissionGate.bestFor(null, 4, 100));
	}

	@Test
	void nothingIsOfferedBeforeTheVillageHasBuiltAnything() {
		assertNull(CommissionGate.bestFor("WEAPONSMITH", 0, 100),
			"the smith's first blade waits on a watchtower");
	}

	@Test
	void theWaitEndsOnTheStrokeOfTheHour() {
		assertFalse(CommissionGate.ready(1000L, 999L));
		assertTrue(CommissionGate.ready(1000L, 1000L));
		assertTrue(CommissionGate.ready(1000L, 1001L));
	}

	@Test
	void aPartMinuteStillCountsAsAMinuteToWait() {
		assertEquals(1, CommissionGate.remainingMinutes(MINUTE, 1));
		assertEquals(1, CommissionGate.remainingMinutes(MINUTE, MINUTE - 1));
		assertEquals(2, CommissionGate.remainingMinutes(2 * MINUTE, 1));
		assertEquals(0, CommissionGate.remainingMinutes(MINUTE, MINUTE));
		assertEquals(0, CommissionGate.remainingMinutes(MINUTE, MINUTE + HOUR));
	}

	@Test
	void speedShortensTheWaitAndNeverInvertsIt() {
		assertEquals(30 * MINUTE, CommissionGate.readyAt(0, 30, 1.0));
		assertEquals(15 * MINUTE, CommissionGate.readyAt(0, 30, 2.0));
		assertEquals(60 * MINUTE, CommissionGate.readyAt(0, 30, 0.5));
		assertEquals(30 * MINUTE, CommissionGate.readyAt(0, 30, 0.0),
			"a nonsense speed should leave the wait alone");
		assertEquals(30 * MINUTE, CommissionGate.readyAt(0, 30, -4.0));
	}

	@Test
	void theTradeThatTookTheOrderHandsItOver() {
		assertTrue(CommissionGate.collectableBy("WEAPONSMITH", "WEAPONSMITH",
			1000L, 1000L));
		assertFalse(CommissionGate.collectableBy("WEAPONSMITH", "WEAPONSMITH",
			1000L, 999L), "nothing is collected before it is finished");
		assertFalse(CommissionGate.collectableBy("FARMER", "WEAPONSMITH",
			1000L, 1000L), "a farmer does not keep the smith's shelf");
	}

	@Test
	void aPieceLeftOnTheShelfEventuallyGoesToAnyone() {
		final long ready = 1000L;
		final long stale = ready + 72 * HOUR;
		assertFalse(CommissionGate.collectableBy("FARMER", "WEAPONSMITH",
			ready, stale), "the shelf is not open on the stroke of the limit");
		assertTrue(CommissionGate.collectableBy("FARMER", "WEAPONSMITH",
			ready, stale + 1),
			"a village that lost its smith should still pay out");
	}
}
