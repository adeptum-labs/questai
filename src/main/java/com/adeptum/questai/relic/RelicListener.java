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

package com.adeptum.questai.relic;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.WanderingPeasantPlugin;
import com.adeptum.questai.villager.VillagerProfileStore;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Activates the right-click relics: the Wayfarer's Compass and the
 * Peddler's Bell. Only main-hand interactions count, which also filters
 * the event's off-hand double fire.
 */
public class RelicListener implements SubPlugin {

	private static final long COMPASS_COOLDOWN_MILLIS = 30_000L;
	private static final long BELL_COOLDOWN_MILLIS = 600_000L;

	private final VillagerProfileStore profileStore;
	private final WanderingPeasantPlugin peasants;
	private final RelicCooldowns cooldowns = new RelicCooldowns();

	public RelicListener(final VillagerProfileStore profileStore,
		final WanderingPeasantPlugin peasants) {

		this.profileStore = profileStore;
		this.peasants = peasants;
	}

	@Override
	public void onEnable() {
		// Event registration is handled by the plugin loader
	}

	@Override
	public void onDisable() {
		// Cooldowns are in-memory only; nothing to tear down
	}

	@EventHandler
	public void onPlayerInteract(final PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_AIR
			&& event.getAction() != Action.RIGHT_CLICK_BLOCK
			|| event.getHand() != EquipmentSlot.HAND) {
			return;
		}

		final QuestRelic relic = RelicItems.relicOf(event.getItem());
		if (relic == QuestRelic.WAYFARERS_COMPASS) {
			useCompass(event.getPlayer());
		} else if (relic == QuestRelic.PEDDLERS_BELL) {
			useBell(event.getPlayer());
		}
	}

	private void useCompass(final Player player) {
		final long now = System.currentTimeMillis();
		if (!cooldowns.isReady(player.getUniqueId(), QuestRelic.WAYFARERS_COMPASS,
			now, COMPASS_COOLDOWN_MILLIS)) {
			player.sendActionBar(Component.text("\u00a78The relic is still dormant."));
			return;
		}
		cooldowns.record(player.getUniqueId(), QuestRelic.WAYFARERS_COMPASS,
			now, COMPASS_COOLDOWN_MILLIS);

		final String line = RelicCompass.pointTo(
			profileStore.locatedVillagers(player.getWorld().getUID()),
			player.getLocation().getX(), player.getLocation().getZ());
		if (line == null) {
			player.sendActionBar(
				Component.text("\u00a77The needle drifts aimlessly."));
			return;
		}
		player.sendActionBar(Component.text("\u00a7b" + line));
		player.playSound(player.getLocation(),
			Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
	}

	private void useBell(final Player player) {
		final long now = System.currentTimeMillis();
		if (!cooldowns.isReady(player.getUniqueId(), QuestRelic.PEDDLERS_BELL,
			now, BELL_COOLDOWN_MILLIS)) {
			player.sendActionBar(Component.text("\u00a78The relic is still dormant."));
			return;
		}

		// The cooldown is only consumed when the summon succeeds
		if (!peasants.summonPeasant(player)) {
			player.sendActionBar(Component.text("\u00a77The bell rings hollow here."));
			return;
		}
		cooldowns.record(player.getUniqueId(), QuestRelic.PEDDLERS_BELL,
			now, BELL_COOLDOWN_MILLIS);
		player.sendMessage("\u00a7dSomewhere, a traveler turns toward the sound.");
		player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
	}
}
