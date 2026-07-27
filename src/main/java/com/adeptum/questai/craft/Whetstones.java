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

import com.adeptum.questai.SubPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Spends a Craftsman's Whetstone on the worn piece in the player's off
 * hand.
 *
 * <p>The stone has to be held to be used, so the piece being mended is the
 * one in the other hand. A stone is only spent when it actually gives
 * something back: a whole piece, or something that cannot wear at all,
 * leaves the stone in the pack.
 */
public class Whetstones implements SubPlugin {

	@Override
	public void onEnable() {
		// Event registration is handled by the plugin loader
	}

	@Override
	public void onDisable() {
		// Nothing is held between uses
	}

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR
			&& event.getAction() != Action.RIGHT_CLICK_BLOCK
			|| event.getHand() != EquipmentSlot.HAND) {
			return;
		}
		if (CraftsmansWhetstone.isWhetstone(event.getItem())) {
			event.setCancelled(true);
			hone(event.getPlayer());
		}
	}

	private void hone(final Player player) {
		final ItemStack worn = player.getInventory().getItemInOffHand();
		final ItemMeta meta = worn.getItemMeta();
		final short max = worn.getType().getMaxDurability();
		if (!(meta instanceof Damageable wear)
			|| !WhetstoneRepair.worthUsing(max, wear.getDamage())) {

			player.sendActionBar(Component.text(
				"\u00a78Hold the worn piece in your off hand."));
			return;
		}

		wear.setDamage(WhetstoneRepair.repaired(max, wear.getDamage(),
			WhetstoneRepair.FRACTION));
		worn.setItemMeta(meta);
		spendStone(player);

		player.sendActionBar(Component.text(
			"\u00a77The edge comes back under the stone."));
		player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE,
			1.0f, 1.1f);
	}

	private static void spendStone(final Player player) {
		final ItemStack held = player.getInventory().getItemInMainHand();
		held.setAmount(held.getAmount() - 1);
	}
}
