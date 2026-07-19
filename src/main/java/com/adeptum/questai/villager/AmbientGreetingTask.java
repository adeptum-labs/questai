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
	private static final int PRUNE_THRESHOLD = 256;
	private static final double RANGE_XZ = 6;
	private static final double RANGE_Y = 4;

	private final VillagerProfileStore profileStore;
	private final ConversationManager conversationManager;
	private final Map<PairKey, Long> lastGreeted = new HashMap<>();

	record PairKey(UUID villager, UUID player) {
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

			if (!(entity instanceof Villager villager)) {
				continue;
			}
			final VillagerProfile profile =
				profileStore.get(villager.getUniqueId());
			if (profile == null || profile.getGreeting() == null) {
				continue;
			}

			final PairKey key = new PairKey(
				villager.getUniqueId(), player.getUniqueId());
			if (shouldGreet(key, System.currentTimeMillis())) {
				player.sendActionBar(Component.text("§a" + profile.getName()
					+ "§7: §f" + profile.getGreeting()));
				return;
			}
		}
	}

	/**
	 * Checks and records the greeting cooldown for a villager-player pair.
	 */
	boolean shouldGreet(final PairKey key, final long now) {
		final Long last = lastGreeted.get(key);
		if (last != null && now - last < GREETING_COOLDOWN_MILLIS) {
			return false;
		}
		if (lastGreeted.size() > PRUNE_THRESHOLD) {
			lastGreeted.values().removeIf(
				t -> now - t >= GREETING_COOLDOWN_MILLIS);
		}
		lastGreeted.put(key, now);
		return true;
	}
}
