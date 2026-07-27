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

package com.adeptum.questai.craft;

/**
 * What honing a worn piece on a whetstone gives back.
 *
 * <p>The stone mends a share of the piece's whole life rather than a share
 * of the wear, so it is worth the same on a nearly-new blade as on a ruined
 * one and cannot be farmed by grinding an item down to nothing first.
 */
public final class WhetstoneRepair {

	/** The share of a piece's full life one stone restores. */
	public static final double FRACTION = 0.40;

	private WhetstoneRepair() {
	}

	/** The wear left after honing; a piece is never taken past new. */
	public static int repaired(final int maxDurability, final int damage,
		final double fraction) {

		final int restored = (int) Math.round(maxDurability * fraction);
		return Math.max(0, damage - restored);
	}

	/** Whether there is anything here worth spending a stone on. */
	public static boolean worthUsing(final int maxDurability,
		final int damage) {

		return maxDurability > 0 && damage > 0;
	}
}
