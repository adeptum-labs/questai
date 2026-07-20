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

import java.util.ArrayList;
import java.util.List;

/**
 * Clearance geometry for scaled variants. The server only checks that a
 * default-sized mob fits before it allows a natural spawn, so raising the
 * scale afterwards can leave the mob wedged in terrain that was never
 * measured against its real hitbox. These offsets name the blocks that
 * must be free before the scale is worth applying.
 *
 * <p>Values are plain integers so this class stays free of registry-backed
 * Bukkit types and safe to load in tests; the forge reads the world.
 */
public final class MobFit {

	/** Vanilla zombie hitbox width in blocks. */
	private static final double BASE_WIDTH = 0.6;

	/** Vanilla zombie hitbox height in blocks. */
	private static final double BASE_HEIGHT = 1.95;

	/** Keeps a box edge that merely grazes a face from claiming it. */
	private static final double EDGE = 1.0e-4;

	private MobFit() {
	}

	/**
	 * Blocks, relative to the spawn block, that must be free of collision
	 * for a mob at this scale. Empty at or below vanilla size: the server
	 * already proved that much room exists, and shrinking never needs more.
	 */
	public static List<Offset> clearanceOffsets(final double scale) {
		if (scale <= 1.0) {
			return List.of();
		}
		// The mob stands centred on its block with its feet on the floor,
		// so the box spans 0.5 +/- half the width and rises from the floor
		final double half = BASE_WIDTH * scale / 2.0;
		final int min = (int) Math.floor(0.5 - half + EDGE);
		final int max = (int) Math.floor(0.5 + half - EDGE);
		final int top = (int) Math.floor(BASE_HEIGHT * scale - EDGE);

		final List<Offset> offsets = new ArrayList<>();
		for (int y = 0; y <= top; y++) {
			for (int x = min; x <= max; x++) {
				for (int z = min; z <= max; z++) {
					offsets.add(new Offset(x, y, z));
				}
			}
		}
		return offsets;
	}

	/** A block offset from the spawn block. */
	public record Offset(int x, int y, int z) {
	}
}
