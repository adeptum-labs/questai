package com.adeptum.questai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The arc a flying pig travels, argued with off-server. What matters is the
 * shape of it: a fall far slower than a real one, a beat only when there is
 * ground coming up to meet them, and wings that work only on the way up.
 */
class PigFlightTest {

	/** Vanilla pulls a falling entity down by this much every tick. */
	private static final double VANILLA_GRAVITY = 0.08;

	@Test
	void theyFallFarSlowerThanAnythingElseDoes() {
		assertTrue(PigFlight.GRAVITY > 0,
			"a pig that never falls is the floating one we started with");
		assertTrue(PigFlight.GRAVITY < VANILLA_GRAVITY / 5,
			"barely any gravity means barely any: " + PigFlight.GRAVITY);
	}

	@Test
	void aPigWithRoomBeneathItSimplyDrifts() {
		final double fallen = PigFlight.nextRise(0.0, 40.0, true);
		assertEquals(-PigFlight.GRAVITY, fallen, 1e-9);
		assertFalse(PigFlight.beating(fallen), "nothing to beat against yet");
	}

	@Test
	void groundComingUpToMeetThemIsWhatStartsAHop() {
		final double hopped = PigFlight.nextRise(-0.2, 1.0, true);
		assertEquals(PigFlight.HOP, hopped, 1e-9);
		assertTrue(PigFlight.beating(hopped), "a hop is a wingbeat");
	}

	@Test
	void aPigAlreadyOnItsWayUpIsLeftToClimb() {
		final double rising = PigFlight.nextRise(0.3, 1.0, true);
		assertEquals(0.3 - PigFlight.GRAVITY, rising, 1e-9,
			"an impulse mid-climb would stack into a rocket");
	}

	@Test
	void aPigStillRestingKeepsFalling() {
		final double resting = PigFlight.nextRise(-0.2, 1.0, false);
		assertTrue(resting < 0, "the rest is what spaces the hops out");
		assertEquals(-0.2 - PigFlight.GRAVITY, resting, 1e-9);
	}

	@Test
	void theWingsOnlyWorkOnTheWayUp() {
		assertTrue(PigFlight.beating(0.05));
		assertFalse(PigFlight.beating(0.0), "the top of the arc is a glide");
		assertFalse(PigFlight.beating(-0.05));
	}

	@Test
	void aHopCarriesThemHigherThanARealOneWould() {
		// Height reached is roughly v^2 / 2g, and the point of the whole
		// change is that a pig's ordinary jump goes a very long way here
		final double ours = PigFlight.HOP * PigFlight.HOP
			/ (2 * PigFlight.GRAVITY);
		final double vanilla = PigFlight.HOP * PigFlight.HOP
			/ (2 * VANILLA_GRAVITY);
		assertTrue(ours > vanilla * 5,
			"the same shove should carry them far further: " + ours);
	}

	@Test
	void theCeilingTurnsThemBackDownWithoutASnap() {
		assertTrue(PigFlight.nextRise(0.4, PigFlight.CEILING + 1, true) <= 0.0,
			"a pig at the ceiling must stop climbing");
	}
}
