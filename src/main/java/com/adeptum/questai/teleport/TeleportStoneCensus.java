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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Finds the teleport stones the live world will admit to. Which villages
 * have a stone out is answered by {@link TeleportStoneStore}, because no
 * scan can see into a chest, an unloaded chunk or an offline player's
 * pockets; this exists to tell that store about stones it has never heard
 * of, so one carried over from before it kept records — or surfacing out of
 * a chest years later — is taken onto the books rather than counted as a
 * village free to issue another.
 *
 * <p>The scan reaches online players' inventories and ender chests and any
 * loaded dropped stone. Must run on the server thread: it touches entities.
 */
public final class TeleportStoneCensus {

	private TeleportStoneCensus() {
	}

	/** Whether a stone bound to this village is anywhere the scan can reach. */
	public static boolean exists(final String rowId) {
		return rowId != null && (heldByPlayer(rowId) || droppedInWorld(rowId));
	}

	/**
	 * Every stone the scan can reach, as village row id to whoever turned
	 * out to be carrying it — null for one lying on the ground.
	 */
	public static Map<String, UUID> found() {
		final Map<String, UUID> stones = new HashMap<>();
		for (final Player player : Bukkit.getOnlinePlayers()) {
			stones.putAll(carriedBy(player));
		}
		for (final World world : Bukkit.getWorlds()) {
			for (final Item drop : world.getEntitiesByClass(Item.class)) {
				final String rowId =
					VillageTeleportStone.rowIdOf(drop.getItemStack());
				if (rowId != null) {
					stones.putIfAbsent(rowId, null);
				}
			}
		}
		return stones;
	}

	/** The stones this player has about them, in pack or ender chest. */
	public static Map<String, UUID> carriedBy(final Player player) {
		final Map<String, UUID> stones = new HashMap<>();
		collect(stones, player.getInventory().getContents(),
			player.getUniqueId());
		collect(stones, player.getEnderChest().getContents(),
			player.getUniqueId());
		return stones;
	}

	private static void collect(final Map<String, UUID> stones,
		final ItemStack[] contents, final UUID holder) {

		if (contents == null) {
			return;
		}
		for (final ItemStack item : contents) {
			final String rowId = VillageTeleportStone.rowIdOf(item);
			if (rowId != null) {
				stones.put(rowId, holder);
			}
		}
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
