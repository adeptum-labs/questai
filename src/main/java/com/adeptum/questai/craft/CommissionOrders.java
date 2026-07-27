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
import org.bukkit.entity.Player;

/**
 * Placing an order, once the player has agreed to it.
 *
 * <p>The dialogue's other handlers are plain two-argument consumers, but an
 * order also has to name the villager who takes it and the piece agreed on,
 * which no such consumer can carry.
 */
@FunctionalInterface
public interface CommissionOrders {

	/**
	 * Takes payment for the piece and puts it on the village's bench.
	 *
	 * @param player who is paying
	 * @param rowId the village taking the order
	 * @param craftsman the villager standing in front of the player
	 * @param commission the catalogue name of the piece agreed on
	 */
	void place(Player player, String rowId, UUID craftsman, String commission);
}
