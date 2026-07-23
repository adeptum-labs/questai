package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkRewardsTest {

	@Test
	void skillsComeFromTheSharedPool() {
		final Set<String> seen = new HashSet<>();
		final Random rng = new Random(42);
		for (int i = 0; i < 200; i++) {
			seen.add(WorkRewards.pickSkill(rng));
		}
		assertEquals(Set.of("MINING", "WOODCUTTING", "EXCAVATION", "FISHING"),
			seen);
	}

	@Test
	void experienceBeatsAnOrdinaryQuest() {
		for (final VillageWork work : VillageWork.values()) {
			assertTrue(WorkRewards.xpFor(work) > 200,
				work + " should out-reward a normal quest");
		}
	}

	@Test
	void gearNeverRollsWhenTheDrawMisses() {
		final Random always = new Random() {
			@Override
			public double nextDouble() {
				return 0.99;
			}
		};
		assertNull(WorkRewards.rollGear(always, VillageWork.WATCHTOWER));
	}

	@Test
	void gearRollsWhenTheDrawHits() {
		final Random never = new Random(7) {
			@Override
			public double nextDouble() {
				return 0.0;
			}
		};
		final var roll = WorkRewards.rollGear(never, VillageWork.BELL_TOWER);
		assertNotNull(roll);
		assertNotNull(roll.item());
		assertTrue(roll.enchants().size() >= 1);
	}

	@Test
	void theBellTowerBoostsQuestXpByAQuarter() {
		assertEquals(125, WorkRewards.bellBoost(100));
	}
}
