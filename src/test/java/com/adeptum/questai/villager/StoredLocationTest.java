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

package com.adeptum.questai.villager;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoredLocationTest {

	@Test
	void fromCopiesWorldAndCoordinates() {
		final UUID worldId = UUID.randomUUID();
		final World world = mock(World.class);
		when(world.getUID()).thenReturn(worldId);

		final StoredLocation stored =
			StoredLocation.from(new Location(world, 10.5, 64, -20.5));

		assertEquals(worldId, stored.worldId());
		assertEquals(10.5, stored.x());
		assertEquals(64, stored.y());
		assertEquals(-20.5, stored.z());
	}

	@Test
	void distanceSquaredXzIgnoresHeight() {
		final StoredLocation stored =
			new StoredLocation(UUID.randomUUID(), 3, 200, 4);

		assertEquals(25.0, stored.distanceSquaredXz(0, 0));
		assertEquals(0.0, stored.distanceSquaredXz(3, 4));
	}
}
