package com.adeptum.questai.fortify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class VillageWorkTest {

	@Test
	void tiersAreOrderedAndAddressable() {
		assertEquals(4, VillageWork.count());
		assertEquals(VillageWork.WATCHTOWER, VillageWork.byTier(0));
		assertEquals(VillageWork.BELL_TOWER, VillageWork.byTier(3));
		assertEquals(null, VillageWork.byTier(4));
		assertEquals(null, VillageWork.byTier(-1));
	}

	@Test
	void everyTierAsksForSomething() {
		for (final VillageWork work : VillageWork.values()) {
			assertFalse(work.getRequirements().isEmpty(),
				work + " asks for nothing");
			work.getRequirements().forEach((role, amount) -> {
				assertTrue(amount > 0, role + " wants a positive amount");
				assertFalse(work.accepted(role).isEmpty(),
					role + " accepts no material");
			});
		}
	}

	@Test
	void rewardsRiseWithTier() {
		for (int tier = 1; tier < VillageWork.count(); tier++) {
			final VillageWork previous = VillageWork.byTier(tier - 1);
			final VillageWork current = VillageWork.byTier(tier);
			assertTrue(current.getXpReward() > previous.getXpReward(),
				current + " must reward more experience than " + previous);
			assertTrue(current.getGearChance() >= previous.getGearChance(),
				current + " must not drop below " + previous + " for gear");
		}
	}

	@Test
	void gearChancesStayProbabilities() {
		for (final VillageWork work : VillageWork.values()) {
			assertTrue(work.getGearChance() >= 0.0 && work.getGearChance() <= 1.0);
		}
	}

	@Test
	void anyLogSatisfiesTheLogRole() {
		assertTrue(VillageWork.WATCHTOWER.accepted("logs").contains(Material.OAK_LOG));
		assertTrue(VillageWork.WATCHTOWER.accepted("logs").contains(Material.SPRUCE_LOG));
		assertTrue(VillageWork.WATCHTOWER.accepted("logs").contains(Material.ACACIA_LOG));
	}

	@Test
	void unknownRoleAcceptsNothing() {
		assertTrue(VillageWork.WATCHTOWER.accepted("diamonds").isEmpty());
	}

	@Test
	void aTierWithDrawingsAlwaysHasPlans() {
		assertTrue(VillageWork.WATCHTOWER.hasPlans());
		for (final VillageWork work : VillageWork.values()) {
			if (work.getSchematicResource() != null) {
				assertTrue(work.hasPlans(),
					work + " has drawings but no plans");
			}
		}
	}
}
