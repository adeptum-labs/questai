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

package com.adeptum.questai.star;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StarfallTest {

	@Test
	void starsFallOnlyAtNightOffCooldownOnARareRoll() {
		assertTrue(Starfall.canFall(13_000, 0.0, 100, 100));
		assertTrue(Starfall.canFall(23_000, 0.0, 100, 100));
		assertFalse(Starfall.canFall(12_999, 0.0, 100, 100));
		assertFalse(Starfall.canFall(23_001, 0.0, 100, 100));
		assertFalse(Starfall.canFall(15_000, 0.0, 99, 100));
		assertFalse(Starfall.canFall(15_000,
			Starfall.STARFALL_CHANCE, 100, 100));
	}

	@Test
	void impactOffsetsStayInTheBand() {
		final Random rng = new Random(42);
		for (int i = 0; i < 200; i++) {
			final Starfall.Offset offset = Starfall.impactOffset(rng);
			final double distance =
				Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z());
			assertTrue(distance >= 199 && distance <= 501);
		}
	}

	@Test
	void naturalSurfacesAcceptTerrainAndRejectEverythingElse() {
		assertTrue(Starfall.isNaturalSurface(Material.GRASS_BLOCK));
		assertTrue(Starfall.isNaturalSurface(Material.STONE));
		assertTrue(Starfall.isNaturalSurface(Material.RED_SAND));
		assertTrue(Starfall.isNaturalSurface(Material.ORANGE_TERRACOTTA));
		assertTrue(Starfall.isNaturalSurface(Material.SNOW));

		assertFalse(Starfall.isNaturalSurface(Material.WATER));
		assertFalse(Starfall.isNaturalSurface(Material.LAVA));
		assertFalse(Starfall.isNaturalSurface(Material.OAK_PLANKS));
		assertFalse(Starfall.isNaturalSurface(Material.CHEST));
		assertFalse(Starfall.isNaturalSurface(Material.OAK_LEAVES));
		assertFalse(Starfall.isNaturalSurface(Material.OAK_LOG));
		assertFalse(Starfall.isNaturalSurface(Material.BEDROCK));
		assertFalse(Starfall.isNaturalSurface(Material.DIRT_PATH));
		assertFalse(Starfall.isNaturalSurface(
			Material.ORANGE_GLAZED_TERRACOTTA));
	}

	@Test
	void craterIsAHemisphereContainingCenterAndBottom() {
		final List<Starfall.Offset> crater = Starfall.craterOffsets();

		assertTrue(crater.contains(new Starfall.Offset(0, 0, 0)));
		assertTrue(crater.contains(new Starfall.Offset(0, -3, 0)));
		for (final Starfall.Offset offset : crater) {
			assertTrue(offset.y() <= 0 && offset.y() >= -3);
			assertTrue(offset.x() * offset.x() + offset.y() * offset.y()
				+ offset.z() * offset.z() <= 3.5 * 3.5);
		}
	}

	@Test
	void shellAndRimSurroundTheBowlWithoutOverlap() {
		final List<Starfall.Offset> crater = Starfall.craterOffsets();

		for (final Starfall.Offset offset : Starfall.shellOffsets()) {
			assertTrue(offset.y() < 0);
			assertFalse(crater.contains(offset));
		}
		for (final Starfall.Offset offset : Starfall.rimOffsets()) {
			assertEquals(0, offset.y());
			assertFalse(crater.contains(offset));
			final int distSq = offset.x() * offset.x()
				+ offset.z() * offset.z();
			assertTrue(distSq > 3.5 * 3.5 && distSq <= 5.0 * 5.0);
		}
	}

	@Test
	void guardsRingTheCrater() {
		final List<Starfall.Offset> guards =
			Starfall.guardOffsets(new Random(42));

		assertEquals(3, guards.size());
		for (final Starfall.Offset offset : guards) {
			final double distance =
				Math.sqrt(offset.x() * offset.x() + offset.z() * offset.z());
			assertTrue(distance >= 3.2 && distance <= 6.6);
		}
	}

	@Test
	void streakArcsFromWitnessSkyToImpact() {
		final List<Starfall.Vec> points =
			Starfall.streakPoints(0, 100, 0, 300, 64, 0);

		assertEquals(Starfall.STREAK_STEPS, points.size());
		assertEquals(0, points.get(0).x(), 1e-9);
		assertEquals(100, points.get(0).y(), 1e-9);
		assertEquals(300, points.get(points.size() - 1).x(), 1e-9);
		assertEquals(64, points.get(points.size() - 1).y(), 1e-9);

		final Starfall.Vec mid = points.get(Starfall.STREAK_STEPS / 2);
		assertTrue(mid.y() > (100 + 64) / 2.0);
	}

	@Test
	void directionPhrasesSpeakAllEightWinds() {
		assertEquals("east", Starfall.directionPhrase(10, 0));
		assertEquals("north", Starfall.directionPhrase(0, -10));
		assertEquals("south-west", Starfall.directionPhrase(-10, 10));
	}

	@Test
	void sellRewardConstantsHold() {
		assertEquals(400, Starfall.SELL_XP);
		assertEquals(4, Starfall.SELL_SKILLS.length);
	}
}
