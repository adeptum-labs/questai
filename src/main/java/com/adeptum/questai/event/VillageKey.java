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

package com.adeptum.questai.event;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Identifies a village area by bucketing its location into 64-block cells.
 * Villages have no intrinsic identity, so events key their cooldowns on
 * the cell and stamp the surrounding neighborhood too.
 */
public record VillageKey(UUID worldId, int cellX, int cellZ) {

	private static final int CELL_SIZE = 64;

	public static VillageKey from(final UUID worldId, final int blockX,
		final int blockZ) {

		return new VillageKey(worldId,
			Math.floorDiv(blockX, CELL_SIZE), Math.floorDiv(blockZ, CELL_SIZE));
	}

	/** This cell plus its eight neighbors. */
	public List<VillageKey> neighborhood() {
		final List<VillageKey> keys = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				keys.add(new VillageKey(worldId, cellX + dx, cellZ + dz));
			}
		}
		return keys;
	}
}
