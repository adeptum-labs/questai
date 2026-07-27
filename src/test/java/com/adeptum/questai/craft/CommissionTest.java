package com.adeptum.questai.craft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.adeptum.questai.reputation.Reputation;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CommissionTest {

	@Test
	void everyPieceHasAPriceAndSomethingToShowForIt() {
		for (final Commission commission : Commission.values()) {
			assertFalse(commission.getRequirements().isEmpty(),
				commission + " should cost something");
			assertFalse(commission.getOutputs().isEmpty(),
				commission + " should hand something back");
			assertNotNull(commission.icon(), commission + " needs an icon");
		}
	}

	@Test
	void everyPriceIsWrittenInARoleThatMaterialsAnswer() {
		for (final Commission commission : Commission.values()) {
			commission.getRequirements().forEach((role, amount) -> {
				assertFalse(CraftRoles.accepted(role).isEmpty(),
					commission + " prices in the unknown role " + role);
				assertTrue(amount > 0,
					commission + " asks for " + amount + " " + role);
			});
		}
	}

	@Test
	void everyPieceTakesRealTimeToMake() {
		for (final Commission commission : Commission.values()) {
			assertTrue(commission.getGate().minutes() > 0,
				commission + " should take a while");
		}
	}

	@Test
	void gearOnlyCarriesEnchantsItsPatternAllows() {
		for (final Commission commission : Commission.values()) {
			for (final CommissionOutput output : commission.getOutputs()) {
				if (output.kind() == CommissionOutput.Kind.GEAR) {
					assertGearIsSound(commission, output);
				}
			}
		}
	}

	@Test
	void withinATradeTheGatesOnlyRise() {
		final Map<String, Commission> previous = new HashMap<>();
		for (final Commission commission : Commission.values()) {
			final Commission earlier = previous.put(
				commission.getProfession(), commission);
			if (earlier != null) {
				assertTrue(rank(commission) >= rank(earlier),
					commission + " should not be gated below " + earlier);
			}
		}
	}

	@Test
	void theIdleTradesTakeNoCommissions() {
		for (final Commission commission : Commission.values()) {
			assertFalse("NITWIT".equals(commission.getProfession()),
				commission + " should not fall to a nitwit");
			assertFalse("NONE".equals(commission.getProfession()),
				commission + " should not fall to an unemployed villager");
		}
	}

	@Test
	void exactlyOnePieceIsAMasterwork() {
		final long masterworks = Arrays.stream(Commission.values())
			.flatMap(commission -> commission.getOutputs().stream())
			.filter(output -> output.kind() == CommissionOutput.Kind.MASTERWORK)
			.count();
		assertEquals(1, masterworks);
	}

	@Test
	void storedNamesResolveBackAndStrangeOnesDoNot() {
		for (final Commission commission : Commission.values()) {
			assertEquals(commission, Commission.byName(commission.name()));
		}
		assertNull(Commission.byName("A_PIECE_WE_NO_LONGER_MAKE"));
		assertNull(Commission.byName(""));
	}

	private static void assertGearIsSound(final Commission commission,
		final CommissionOutput output) {

		assertNotNull(output.displayName(), commission + " should name its gear");
		output.enchants().forEach((enchant, level) -> {
			assertTrue(output.gear().getEnchants().contains(enchant),
				commission + " puts " + enchant + " on gear that cannot take it");
			assertTrue(level >= enchant.getMinLevel()
				&& level <= enchant.getMaxLevel(),
				commission + " asks for " + enchant + " " + level);
		});
	}

	/** Orders a gate so a later entry can be checked against an earlier one. */
	private static int rank(final Commission commission) {
		final Commission.Gate gate = commission.getGate();
		return gate.minTier() * Reputation.Standing.values().length
			+ gate.minStanding().ordinal();
	}
}
