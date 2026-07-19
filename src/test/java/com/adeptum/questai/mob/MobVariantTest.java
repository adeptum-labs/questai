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
			assertTrue(variant.getDisplayName().startsWith("\u00a7"));
			assertTrue(variant.getDropChance() > 0
				&& variant.getDropChance() < 1);
			assertTrue(variant.getBonusXp() >= 0);
			assertTrue(variant.getScale() > 0);
			assertTrue(variant.getMaxHealth() > 0);
			assertTrue(variant.getMovementSpeed() > 0);
			assertTrue(variant.getAttackDamage() > 0);
			assertTrue(variant.getKnockbackResistance() >= 0
				&& variant.getKnockbackResistance() <= 1);
		}
	}

	@Test
	void gravehulkIsTheGiant() {
		assertEquals(2.0, MobVariant.GRAVEHULK.getScale());
		assertTrue(MobVariant.GRAVELING.getScale() < 1.0);
		assertTrue(MobVariant.CINDERLING.getScale() < 1.0);
	}
}
