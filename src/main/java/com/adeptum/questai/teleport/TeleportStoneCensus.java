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

package com.adeptum.questai.teleport;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * Decides whether a village's teleport stone already exists, so a village
 * never issues a second one. Rather than persisting an issued flag, this
 * looks at the live world: if the stone is destroyed or lost it stops being
 * found and the village is free to grant a new one.
 *
 * <p>The scan reaches online players' inventories and ender chests and any
 * loaded dropped stone. It cannot see a stone held by an offline player or
 * sitting in an unloaded chunk, so a rare duplicate is possible until that
 * player logs in. Must run on the server thread — it touches entities.
 */
public final class TeleportStoneCensus {

	private TeleportStoneCensus() {
	}

	/** Whether a stone bound to this village is anywhere the scan can reach. */
	public static boolean exists(final String rowId) {
		return rowId != null && (heldByPlayer(rowId) || droppedInWorld(rowId));
	}

	/** A stone in any online player's inventory or ender chest. */
	private static boolean heldByPlayer(final String rowId) {
		for (final Player player : Bukkit.getOnlinePlayers()) {
			if (VillageTeleportStone.holds(
					player.getInventory().getContents(), rowId)
				|| VillageTeleportStone.holds(
					player.getEnderChest().getContents(), rowId)) {
				return true;
			}
		}
		return false;
	}

	/** A stone dropped on the ground in any loaded chunk. */
	private static boolean droppedInWorld(final String rowId) {
		for (final World world : Bukkit.getWorlds()) {
			for (final Item drop : world.getEntitiesByClass(Item.class)) {
				if (VillageTeleportStone.bound(drop.getItemStack(), rowId)) {
					return true;
				}
			}
		}
		return false;
	}
}
