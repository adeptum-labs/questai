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

package com.adeptum.questai.village;

import com.adeptum.questai.fortify.VillageExtent;
import com.adeptum.questai.villager.StoredLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class VillageCrowdTest {

	private static final UUID WORLD_ID =
		UUID.fromString("00000000-0000-0000-0000-0000000000aa");

	@Mock private World world;

	private MockedStatic<Bukkit> bukkitMock;
	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		bukkitMock = mockStatic(Bukkit.class);
		bukkitMock.when(() -> Bukkit.getWorld(WORLD_ID)).thenReturn(world);
	}

	@AfterEach
	void tearDown() throws Exception {
		bukkitMock.close();
		mocks.close();
	}

	@Test
	void aCrowdNamesItsOwnMiddle() {
		final List<VillageExtent.Point> crowd = List.of(
			new VillageExtent.Point(130, 150),
			new VillageExtent.Point(137, 161),
			new VillageExtent.Point(150, 170));

		final VillageExtent.Extent extent =
			VillageExtent.measure(crowd, 140, 135);

		assertEquals(137, extent.centreX());
		assertEquals(161, extent.centreZ());
	}

	@Test
	void tooSmallACrowdKeepsTheStoredCentre() {
		final VillageExtent.Extent extent = VillageExtent.measure(
			List.of(new VillageExtent.Point(999, 999)), 140, 135);

		assertEquals(140, extent.centreX());
		assertEquals(135, extent.centreZ());
	}

	@Test
	void aCrowdBelowTheThresholdReturnsNull() {
		givenVillagers(130, 150, 150, 170);

		assertNull(VillageCrowd.measure(stored(140, 135)));
	}

	@Test
	void aCrowdAtTheThresholdReturnsAMeasuredCentre() {
		givenVillagers(130, 150, 137, 161, 150, 170);

		final StoredLocation centre = VillageCrowd.measure(stored(140, 135));

		assertNotNull(centre);
		assertEquals(137, centre.x());
		assertEquals(161, centre.z());
	}

	@Test
	void aSufficientCrowdCentredOnTheStoredPointReturnsThatCentre() {
		// Median of x is 140, median of z is 135 — the same as the stored
		// centre already on file, yet three villagers are enough to trust
		givenVillagers(130, 125, 140, 135, 150, 145);

		final StoredLocation centre = VillageCrowd.measure(stored(140, 135));

		assertNotNull(centre);
		assertEquals(140, centre.x());
		assertEquals(135, centre.z());
	}

	/** The stored centre the crowd's answer is measured against. */
	private StoredLocation stored(final double x, final double z) {
		return new StoredLocation(WORLD_ID, x, 64, z);
	}

	/** Stubs the world's nearby-entities search with villagers at each pair. */
	private void givenVillagers(final double... coordinates) {
		final List<Entity> villagers = new ArrayList<>();
		for (int i = 0; i < coordinates.length; i += 2) {
			villagers.add(villagerAt(coordinates[i], coordinates[i + 1]));
		}
		when(world.getNearbyEntities(any(Location.class), anyDouble(),
			anyDouble(), anyDouble())).thenReturn(villagers);
	}

	private Entity villagerAt(final double x, final double z) {
		final Entity entity = mock(Entity.class);
		when(entity.getType()).thenReturn(EntityType.VILLAGER);
		when(entity.getLocation()).thenReturn(new Location(world, x, 70, z));
		return entity;
	}
}
