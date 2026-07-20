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

package com.adeptum.questai.village;

import com.adeptum.questai.SubPlugin;
import com.adeptum.questai.event.VillageCheckListener;
import com.adeptum.questai.event.VillageKey;
import com.adeptum.questai.model.VillageInfo;
import com.adeptum.questai.utility.AiChat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Greets a player with the village's name as they walk in, and names
 * villages the first time anyone walks into one.
 *
 * <p>A large fading title rather than a boss bar: a bar reads as progress
 * and jostles with the quest objective and timer bars. The title shows once
 * on entry; walking out re-arms it, with a short debounce so skirting the
 * boundary does not replay it.
 *
 * <p>Presence is checked every second, but only against already-named
 * villages, which costs a few squared distances and reads no blocks.
 * Discovery is the expensive half and is kept rare.
 */
public class VillageNameplate implements SubPlugin, VillageCheckListener {

	private static final long TICK_INTERVAL = 20L;

	/** Floor between two on-demand scans, across all players. */
	private static final long PROBE_INTERVAL_MILLIS = 5_000L;

	/**
	 * How long a cell where a scan found nothing is left alone. Short:
	 * a scan can miss from the edge of a village the player is still
	 * walking toward, and the villager pre-filter already carries the
	 * common no-village case.
	 */
	private static final long CELL_RETRY_MILLIS = 90_000L;

	/** Consecutive outside ticks before the title re-arms. */
	private static final int LEAVE_MISSES = 3;

	private static final Title.Times TITLE_TIMES = Title.Times.times(
		Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1));

	private static final int PRUNE_THRESHOLD = 256;
	private static final String DEFAULT_BIOME = "plains";

	private final JavaPlugin plugin;
	private final OpenAiChatModel chatModel;
	private final VillageRegistry registry;
	private final boolean enabled;

	/** The village name each player was last greeted in. */
	private final Map<UUID, String> inside = new HashMap<>();
	private final Map<UUID, Integer> misses = new HashMap<>();
	private final Set<VillageKey> naming = new HashSet<>();
	private final Map<VillageKey, Long> probedUntil = new HashMap<>();

	private VillageScanner scanner;
	private BukkitTask task;
	private long nextProbeAt;

	public VillageNameplate(final JavaPlugin plugin,
		final OpenAiChatModel chatModel, final VillageRegistry registry) {

		this.plugin = plugin;
		this.chatModel = chatModel;
		this.registry = registry;
		this.enabled = plugin.getConfig()
			.getBoolean("villages.nameplate.enabled", true);
	}

	/**
	 * Supplies the on-demand scanner. Set after construction because the
	 * scanner also delivers this class its village checks.
	 */
	public void setScanner(final VillageScanner scanner) {
		this.scanner = scanner;
	}

	@Override
	public void onEnable() {
		if (!enabled) {
			return;
		}
		plugin.getLogger().info("[VillageNameplate] Enabled with "
			+ registry.size() + " named villages.");
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
			TICK_INTERVAL, TICK_INTERVAL);
	}

	@Override
	public void onDisable() {
		if (task != null) {
			task.cancel();
		}
		// Titles fade on their own; only the greeting state needs dropping
		inside.clear();
		misses.clear();
	}

	@EventHandler
	public void onPlayerQuit(final PlayerQuitEvent event) {
		inside.remove(event.getPlayer().getUniqueId());
		misses.remove(event.getPlayer().getUniqueId());
	}

	@Override
	public void onVillageCheck(final Player player, final Location location,
		final VillageInfo info) {

		if (enabled && registry.find(location) == null) {
			nameVillage(location, info);
		}
	}

	/* default */ void tick() {
		for (final Player player : Bukkit.getOnlinePlayers()) {
			final NamedVillage village = registry.find(player.getLocation());
			if (village == null) {
				leaveTick(player.getUniqueId());
				probe(player);
			} else {
				greet(player, village.name());
			}
		}
	}

	/**
	 * Shows the title once per stay. A different name while already greeted
	 * means the player walked straight from one village into another, which
	 * greets again without requiring a spell outside.
	 */
	private void greet(final Player player, final String name) {
		misses.remove(player.getUniqueId());
		if (!name.equals(inside.get(player.getUniqueId()))) {
			inside.put(player.getUniqueId(), name);
			player.showTitle(Title.title(
				Component.text(name, NamedTextColor.GOLD),
				Component.empty(), TITLE_TIMES));
		}
	}

	/**
	 * Re-arms the greeting only after a few consecutive ticks outside, so a
	 * player skirting the boundary is not greeted again on every crossing.
	 */
	private void leaveTick(final UUID playerId) {
		if (!inside.containsKey(playerId)) {
			return;
		}
		final int count = misses.merge(playerId, 1, Integer::sum);
		if (count >= LEAVE_MISSES) {
			inside.remove(playerId);
			misses.remove(playerId);
		}
	}

	/** The name the player is currently greeted under, or null when outside. */
	/* default */ String insideOf(final UUID playerId) {
		return inside.get(playerId);
	}

	/**
	 * Looks for an unnamed village around a player who is not in a known one.
	 * The scan is far too costly to run per player per second, so it is gated
	 * on depth, on a villager actually being nearby, on a global interval and
	 * on the cell not having been searched recently.
	 */
	private void probe(final Player player) {
		final long now = System.currentTimeMillis();
		if (scanner == null || now < nextProbeAt) {
			return;
		}
		final Location location = player.getLocation();
		if (!worthScanning(location)) {
			return;
		}
		final VillageKey key = keyOf(location);
		if (searchedRecently(key, now)) {
			return;
		}

		nextProbeAt = now + PROBE_INTERVAL_MILLIS;
		prune(now);

		final VillageInfo info = scanner.scan(location);
		if (info.village()) {
			nameVillage(location, info);
		} else {
			// Only a miss cools the cell down; a success gets named and the
			// registry answers from then on
			probedUntil.put(key, now + CELL_RETRY_MILLIS);
		}
	}

	/** The two cheap gates that keep the block survey off the hot path. */
	private boolean worthScanning(final Location location) {
		return scanner.isNearSurface(location)
			&& scanner.hasVillagersNearby(location);
	}

	private boolean searchedRecently(final VillageKey key, final long now) {
		final Long quietUntil = probedUntil.get(key);
		return quietUntil != null && now < quietUntil;
	}

	private void nameVillage(final Location location, final VillageInfo info) {
		final VillageKey key = keyOf(location);
		// Two players walking in together must not each spend a model call
		if (!naming.add(key)) {
			return;
		}
		final Location centre = location.clone();
		final String prompt = VillageNames.prompt(biomeOf(location),
			info.doorCount(), info.villagerCount());

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			String name = null;
			try {
				name = VillageNames.sanitise(AiChat.ask(chatModel, prompt, ""));
			} catch (Exception e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillageNameplate] Could not name a village; using a fallback.", e);
			}
			final String chosen = name == null ? VillageNames.fallbackName(key) : name;
			Bukkit.getScheduler().runTask(plugin, () -> {
				registry.claim(key, centre, chosen);
				naming.remove(key);
			});
		});
	}

	private static VillageKey keyOf(final Location location) {
		return VillageKey.from(location.getWorld().getUID(),
			location.getBlockX(), location.getBlockZ());
	}

	private static String biomeOf(final Location location) {
		try {
			return location.getBlock().getBiome().getKey().getKey();
		} catch (RuntimeException e) {
			return DEFAULT_BIOME;
		}
	}

	/** Drops expired cells once the map grows, as relic cooldowns do. */
	private void prune(final long now) {
		if (probedUntil.size() < PRUNE_THRESHOLD) {
			return;
		}
		final Iterator<Map.Entry<VillageKey, Long>> it =
			probedUntil.entrySet().iterator();
		while (it.hasNext()) {
			if (it.next().getValue() < now) {
				it.remove();
			}
		}
	}
}
