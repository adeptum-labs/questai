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

import java.util.UUID;

/**
 * A piece of work a village has taken on for one player.
 *
 * <p>The craftsman is remembered by id rather than keyed by it: the order
 * belongs to the village, so a smith who dies before the piece is collected
 * does not take the player's materials with him.
 *
 * @param commission the catalogue name of the work, stored rather than the
 *     constant so an unknown name can be skipped on load
 * @param craftsman the villager who took the order
 * @param placedAt when the materials were handed over
 * @param readyAt when the piece can be collected
 */
public record CommissionOrder(String commission, UUID craftsman, long placedAt,
	long readyAt) {
}
