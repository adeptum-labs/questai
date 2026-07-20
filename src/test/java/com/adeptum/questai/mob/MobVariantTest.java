/*
 * Copyright (C) 2026 Adeptum AB, org nr. 559494-1824
 *
 * This file is part of QuestAI.
 *
 * QuestAI is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * QuestAI is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with QuestAI. If not, see
 * <https://www.gnu.org/licenses/>.
 */

package com.adeptum.questai.mob;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobVariantTest {

	@Test
	void idsAreUniqueAndRoundTrip() {
		final Set<String> ids = new HashSet<>();
		for (final MobVariant variant : MobVariant.values()) {
			assertTrue(ids.add(variant.getId()));
			assertSame(variant, MobVariant.fromId(variant.getId()));
		}
		assertNull(MobVariant.fromId("no_such_mob"));
	}

	@Test
	void everyVariantHasSaneValues() {
		for (final MobVariant variant : MobVariant.values()) {
			final MobVariant.Combat combat = variant.getCombat();
			assertTrue(variant.getDisplayName().startsWith("\u00a7"));
			// Zero is legal: a passive variant drops no gear and cannot bite
			assertTrue(variant.getDropChance() >= 0
				&& variant.getDropChance() < 1);
			assertTrue(variant.getBonusXp() >= 0);
			assertTrue(combat.scale() > 0);
			assertTrue(combat.maxHealth() > 0);
			assertTrue(combat.movementSpeed() > 0);
			assertTrue(combat.attackDamage() >= 0);
			assertTrue(combat.knockbackResistance() >= 0
				&& combat.knockbackResistance() <= 1);
		}
	}

	@Test
	void gravehulkIsTheGiant() {
		assertEquals(2.0, MobVariant.GRAVEHULK.getCombat().scale());
		assertTrue(MobVariant.GRAVELING.getCombat().scale() < 1.0);
		assertTrue(MobVariant.CINDERLING.getCombat().scale() < 1.0);
	}

	@Test
	void theRatIsGenuinelyHarmless() {
		final MobVariant.Combat combat = MobVariant.RAT.getCombat();

		// The target-cancel handler is the first line of defence; a bite
		// that does nothing is the second. Both must hold.
		assertEquals(0.0, combat.attackDamage());
		assertEquals(0.0, MobVariant.RAT.getDropChance(),
			"a rat must never drop combat gear");
	}
}
