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
 * which half the player stood in. The key only identifies the cell a
 * village was discovered in; its id is what a row is filed under.
 */
public class VillageRegistry {

	private final JavaPlugin plugin;
	private final File file;
	private final double claimRadius;
	// Keyed by id, not by VillageKey: two villages can share one 64-block
	// cell, and a cell-keyed map would evict the first
	private final Map<String, NamedVillage> villages = new HashMap<>();
	// An absorbed village's id still arrives from teleport stones already in
	// players' hands, so it must keep answering rather than become a stranger
	private final Map<String, String> aliases = new HashMap<>();

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

		final StoredLocation stored = StoredLocation.from(centre);
		final NamedVillage village = new NamedVillage(mintId(stored), key,
			stored, name, System.currentTimeMillis());
		villages.put(village.id(), village);
		save();
		return village;
	}

	/** How many villages are known; the nameplate logs this on enable. */
	public synchronized int size() {
		return villages.size();
	}

	/**
	 * The known village whose row id matches, or null when none does. A
	 * donor can stand just past {@link #find}'s claim radius, at the
	 * village's edge, while the works row still names the village by id.
	 */
	public synchronized NamedVillage byRowId(final String rowId) {
		return rowId == null ? null : villages.get(resolve(rowId));
	}

	/**
	 * The live id this one stands for. Ids handed out before a merge keep
	 * turning up on stones and in conversations long afterwards, so every
	 * lookup goes through here rather than trusting the id it was given.
	 */
	public synchronized String resolve(final String rowId) {
		String id = rowId;
		// Bounded by the table's size: a cycle would otherwise spin forever
		for (int hops = 0; hops < aliases.size() + 1; hops++) {
			final String next = aliases.get(id);
			if (next == null) {
				return id;
			}
			id = next;
		}
		return id;
	}

	/**
	 * Folds one village's id into another's and forgets the absorbed row.
	 * The state filed under the old id is moved by {@code VillageMerger};
	 * this only settles which id is the living one.
	 */
	public synchronized void absorb(final String absorbedId,
		final String survivorId) {

		if (absorbedId == null || survivorId == null
			|| absorbedId.equals(survivorId)) {
			return;
		}
		villages.remove(absorbedId);
		aliases.put(absorbedId, survivorId);
		save();
	}

	/**
	 * The persistence key for a village, shared with any store that hangs
	 * state off a village rather than off a cell. Fixed at discovery: a
	 * village that is re-surveyed onto a truer centre must not thereby
	 * become a stranger to its own reputation and works.
	 */
	public static String rowIdFor(final NamedVillage village) {
		return village.id();
	}

	/** The id a newly discovered village is given, unique among the known. */
	private String mintId(final StoredLocation centre) {
		final String base = centre.worldId() + "_" + (int) Math.floor(centre.x())
			+ "_" + (int) Math.floor(centre.z());
		String id = base;
		// A retired id must stay retired, or a new village at the same
		// column would be shadowed by the alias its predecessor left behind
		for (int suffix = 2; villages.containsKey(id) || aliases.containsKey(id);
			suffix++) {
			id = base + "_" + suffix;
		}
		return id;
	}

	private void load() {
		if (!file.exists()) {
			return;
		}
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		loadVillages(cfg);
		loadAliases(cfg);
	}

	private void loadVillages(final YamlConfiguration cfg) {
		final ConfigurationSection root = cfg.getConfigurationSection("villages");
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

	private void loadAliases(final YamlConfiguration cfg) {
		final ConfigurationSection alias = cfg.getConfigurationSection("aliases");
		if (alias == null) {
			return;
		}
		for (final String id : alias.getKeys(false)) {
			aliases.put(id, alias.getString(id));
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
		villages.put(id, new NamedVillage(id,
			VillageKey.from(worldId, (int) Math.floor(centre.x()),
				(int) Math.floor(centre.z())), centre, name,
			sec.getLong("discoveredAt")));
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		for (final NamedVillage village : villages.values()) {
			final String path = "villages." + village.id();
			cfg.set(path + ".name", village.name());
			cfg.set(path + ".world", village.centre().worldId().toString());
			cfg.set(path + ".x", village.centre().x());
			cfg.set(path + ".y", village.centre().y());
			cfg.set(path + ".z", village.centre().z());
			cfg.set(path + ".discoveredAt", village.discoveredAt());
		}
		for (final Map.Entry<String, String> alias : aliases.entrySet()) {
			cfg.set("aliases." + alias.getKey(), alias.getValue());
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
