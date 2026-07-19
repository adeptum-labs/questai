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

import com.adeptum.questai.dialogue.ConversationManager;
import com.adeptum.questai.quest.DeliveryPackage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;

/**
 * Periodic main-thread task that lets profiled villagers call out their
 * cached greeting line to players passing close by. Uses only stored text,
 * shown on the action bar, with a per villager-and-player cooldown.
 */
public final class AmbientGreetingTask implements Runnable {

	private static final long GREETING_COOLDOWN_MILLIS = 5 * 60 * 1000L;
	private static final long HINT_COOLDOWN_MILLIS = 30 * 1000L;
	private static final int PRUNE_THRESHOLD = 256;
	private static final double RANGE_XZ = 6;
	private static final double RANGE_Y = 4;

	private final VillagerProfileStore profileStore;
	private final ConversationManager conversationManager;
	private final Map<PairKey, Long> lastGreeted = new HashMap<>();
	private final Map<PairKey, Long> lastHinted = new HashMap<>();

	/* default */ record PairKey(UUID villager, UUID player) {
	}

	public AmbientGreetingTask(final VillagerProfileStore profileStore,
		final ConversationManager conversationManager) {

		this.profileStore = profileStore;
		this.conversationManager = conversationManager;
	}

	@Override
	public void run() {
		for (final Player player : Bukkit.getOnlinePlayers()) {
			if (!conversationManager.isInConversation(player)) {
				greetFromNearbyVillager(player);
			}
		}
	}

	private void greetFromNearbyVillager(final Player player) {
		for (final Entity entity
			: player.getNearbyEntities(RANGE_XZ, RANGE_Y, RANGE_XZ)) {

			if (entity instanceof Villager villager
				&& notifyPlayer(player, villager)) {
				return;
			}
		}
	}

	/**
	 * Shows at most one notification for this villager: a parcel hint when
	 * the player is carrying a package for it, otherwise the cached
	 * greeting line.
	 *
	 * @return true when a notification was shown
	 */
	private boolean notifyPlayer(final Player player, final Villager villager) {
		final VillagerProfile profile = profileStore.get(villager.getUniqueId());
		if (profile == null) {
			return false;
		}

		final PairKey key = new PairKey(
			villager.getUniqueId(), player.getUniqueId());
		final long now = System.currentTimeMillis();

		if (DeliveryPackage.matches(
			player.getInventory().getItemInMainHand(), profile.getName())) {
			if (shouldHint(key, now)) {
				player.sendActionBar(Component.text("§6" + profile.getName()
					+ " is expecting a parcel."));
				return true;
			}
			return false;
		}

		if (profile.getGreeting() != null && shouldGreet(key, now)) {
			player.sendActionBar(Component.text("§a" + profile.getName()
				+ "§7: §f" + profile.getGreeting()));
			return true;
		}
		return false;
	}

	/* default */ boolean shouldGreet(final PairKey key, final long now) {
		return shouldNotify(lastGreeted, key, now, GREETING_COOLDOWN_MILLIS);
	}

	/* default */ boolean shouldHint(final PairKey key, final long now) {
		return shouldNotify(lastHinted, key, now, HINT_COOLDOWN_MILLIS);
	}

	/**
	 * Checks and records a notification cooldown for a villager-player pair.
	 */
	private static boolean shouldNotify(final Map<PairKey, Long> lastShown,
		final PairKey key, final long now, final long cooldownMillis) {

		final Long last = lastShown.get(key);
		if (last != null && now - last < cooldownMillis) {
			return false;
		}
		if (lastShown.size() > PRUNE_THRESHOLD) {
			lastShown.values().removeIf(t -> now - t >= cooldownMillis);
		}
		lastShown.put(key, now);
		return true;
	}
}
