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

import com.adeptum.questai.event.VillageKey;
import com.adeptum.questai.villager.StoredLocation;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The names of every village discovered so far, persisted to
 * village-names.yml beside the villager profiles.
 *
 * <p>Lookups are by distance to a stored centre rather than by cell.
 * {@link VillageKey} buckets the world into 64-block columns, so a village
 * spanning a boundary would otherwise answer to two names depending on
 * which half the player stood in. The key only identifies the row.
 */
public class VillageRegistry {

	private final JavaPlugin plugin;
	private final File file;
	private final double claimRadius;
	// Keyed by the centre's block column, not by VillageKey: two villages can
	// share one 64-block cell, and a cell-keyed map would evict the first
	private final Map<String, NamedVillage> villages = new HashMap<>();

	public VillageRegistry(final JavaPlugin plugin, final double claimRadius) {
		this.plugin = plugin;
		this.claimRadius = claimRadius;
		this.file = new File(plugin.getDataFolder(), "village-names.yml");
		load();
	}

	/**
	 * The named village covering this location, or null when none does.
	 * Nearest wins, so overlapping claims resolve the same way from every
	 * approach rather than depending on map iteration order.
	 */
	public synchronized NamedVillage find(final Location location) {
		if (location == null || location.getWorld() == null) {
			return null;
		}
		final UUID worldId = location.getWorld().getUID();
		final double limit = claimRadius * claimRadius;

		NamedVillage best = null;
		double bestDistance = Double.MAX_VALUE;
		for (final NamedVillage village : villages.values()) {
			final double distance = distanceTo(village, worldId, location);
			if (distance <= limit && distance < bestDistance) {
				best = village;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Squared distance, or unreachable when the village is in another world. */
	private static double distanceTo(final NamedVillage village,
		final UUID worldId, final Location location) {

		return village.centre().worldId().equals(worldId)
			? village.centre().distanceSquaredXz(location.getX(), location.getZ())
			: Double.MAX_VALUE;
	}

	/** Records a newly discovered village and writes it through to disk. */
	public synchronized NamedVillage claim(final VillageKey key,
		final Location centre, final String name) {

		final NamedVillage village =
			new NamedVillage(key, StoredLocation.from(centre), name);
		villages.put(rowId(village.centre()), village);
		save();
		return village;
	}

	/** How many villages are known; the nameplate logs this on enable. */
	public synchronized int size() {
		return villages.size();
	}

	private void load() {
		if (!file.exists()) {
			return;
		}
		final ConfigurationSection root = YamlConfiguration
			.loadConfiguration(file).getConfigurationSection("villages");
		if (root == null) {
			return;
		}
		for (final String id : root.getKeys(false)) {
			// One unreadable row must not cost every other village its name
			try {
				loadVillage(id, root.getConfigurationSection(id));
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillageRegistry] Skipping malformed village " + id, e);
			}
		}
	}

	private void loadVillage(final String id, final ConfigurationSection sec) {
		final String name = sec.getString("name");
		final String world = sec.getString("world");
		if (name == null || world == null) {
			return;
		}
		final UUID worldId = UUID.fromString(world);
		final StoredLocation centre = new StoredLocation(worldId,
			sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
		villages.put(id, new NamedVillage(
			VillageKey.from(worldId, (int) Math.floor(centre.x()),
				(int) Math.floor(centre.z())), centre, name));
	}

	/** Stable per-village row name; the centre's column is unique enough. */
	private static String rowId(final StoredLocation centre) {
		return centre.worldId() + "_" + (int) Math.floor(centre.x())
			+ "_" + (int) Math.floor(centre.z());
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		for (final NamedVillage village : villages.values()) {
			final String path = "villages." + rowId(village.centre());
			cfg.set(path + ".name", village.name());
			cfg.set(path + ".world", village.centre().worldId().toString());
			cfg.set(path + ".x", village.centre().x());
			cfg.set(path + ".y", village.centre().y());
			cfg.set(path + ".z", village.centre().z());
		}
		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[VillageRegistry] Could not save village-names.yml", e);
		}
	}
}
