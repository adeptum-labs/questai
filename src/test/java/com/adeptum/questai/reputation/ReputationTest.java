package com.adeptum.questai.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReputationTest {

	private static final long TWO_HOURS = 2L * 60 * 60 * 1000;

	@Test
	void standingIsClampedToItsBounds() {
		assertEquals(100, Reputation.clamp(250));
		assertEquals(-100, Reputation.clamp(-250));
		assertEquals(7, Reputation.clamp(7));
	}

	@Test
	void grudgesSoftenOnePointPerTwoHours() {
		assertEquals(-9, Reputation.mend(-10, TWO_HOURS));
		assertEquals(-7, Reputation.mend(-10, 3 * TWO_HOURS));
		assertEquals(-10, Reputation.mend(-10, TWO_HOURS - 1));
	}

	@Test
	void mendingStopsAtZeroAndLeavesGoodwillAlone() {
		assertEquals(0, Reputation.mend(-2, 10 * TWO_HOURS));
		assertEquals(60, Reputation.mend(60, 10 * TWO_HOURS));
		assertEquals(0, Reputation.mend(0, 10 * TWO_HOURS));
	}

	@Test
	void offersDryUpAsStandingFalls() {
		assertEquals(1.0, Reputation.offerScale(0), 1e-9);
		assertEquals(0.5, Reputation.offerScale(-50), 1e-9);
		assertEquals(0.0, Reputation.offerScale(Reputation.NO_QUESTS_AT), 1e-9);
		assertEquals(0.0, Reputation.offerScale(-100), 1e-9);
	}

	@Test
	void heroesHearOfWorkALittleMoreOften() {
		assertEquals(1.1, Reputation.offerScale(50), 1e-9);
		assertEquals(1.2, Reputation.offerScale(100), 1e-9);
	}

	@Test
	void rewardsFollowStanding() {
		assertEquals(80, Reputation.rewardScale(100, -1));
		assertEquals(100, Reputation.rewardScale(100, 0));
		assertEquals(100, Reputation.rewardScale(100, 49));
		assertEquals(110, Reputation.rewardScale(100, 50));
	}

	@Test
	void deepGrudgesEndTrade() {
		assertTrue(Reputation.tradesWith(-39));
		assertFalse(Reputation.tradesWith(Reputation.NO_TRADE_AT));
		assertFalse(Reputation.tradesWith(-100));
	}

	@Test
	void standingBucketsCoverTheWholeRange() {
		assertEquals(Reputation.Standing.HATED, Reputation.standing(-40));
		assertEquals(Reputation.Standing.DISLIKED, Reputation.standing(-1));
		assertEquals(Reputation.Standing.NEUTRAL, Reputation.standing(0));
		assertEquals(Reputation.Standing.NEUTRAL, Reputation.standing(24));
		assertEquals(Reputation.Standing.RESPECTED, Reputation.standing(25));
		assertEquals(Reputation.Standing.REVERED, Reputation.standing(75));
	}

	@Test
	void onlyNeutralStandingStaysUnspoken() {
		for (int rep = Reputation.MIN; rep <= Reputation.MAX; rep++) {
			final String clause = Reputation.standingClause(rep);
			if (Reputation.standing(rep) == Reputation.Standing.NEUTRAL) {
				assertNull(clause, "neutral must stay silent at " + rep);
			} else {
				assertNotNull(clause, "standing must speak at " + rep);
			}
		}
	}
}
