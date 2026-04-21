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

package com.adeptum.questai.quest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists locations of quest-placed chests and hidden NPCs between sessions
 * so they can be cleaned up after a hard shutdown, crash, or /stop where the
 * in-memory quest state was lost before cleanup could run.
 */
public final class PlacedEntityStore {

	private final JavaPlugin plugin;
	private final File file;
	private final List<Entry> entries = new CopyOnWriteArrayList<>();

	public enum Kind { CHEST, HIDDEN_NPC }

	public record Entry(Kind kind, UUID worldId, int x, int y, int z) {
		public Location toLocation() {
			final World world = Bukkit.getWorld(worldId);
			return world == null ? null
				: new Location(world, x + 0.5, y, z + 0.5);
		}
	}

	public PlacedEntityStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "placed-entities.yml");
		load();
	}

	public synchronized void record(final Kind kind, final Location loc) {
		if (loc == null || loc.getWorld() == null) {
			return;
		}
		entries.add(new Entry(kind, loc.getWorld().getUID(),
			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
		save();
	}

	public synchronized void forget(final Kind kind, final Location loc) {
		if (loc == null || loc.getWorld() == null) {
			return;
		}
		final UUID worldId = loc.getWorld().getUID();
		final int bx = loc.getBlockX();
		final int by = loc.getBlockY();
		final int bz = loc.getBlockZ();
		final boolean removed = entries.removeIf(e -> e.kind() == kind
			&& e.worldId().equals(worldId) && e.x() == bx && e.y() == by
			&& e.z() == bz);
		if (removed) {
			save();
		}
	}

	/**
	 * Removes every persisted chest/hidden NPC from the world. Typically run
	 * once at onEnable before the player can interact with orphan leftovers.
	 */
	public synchronized int sweepOrphans() {
		if (entries.isEmpty()) {
			return 0;
		}
		int swept = 0;
		for (final Entry entry : new ArrayList<>(entries)) {
			final Location loc = entry.toLocation();
			if (loc == null) {
				continue;
			}
			if (entry.kind() == Kind.CHEST) {
				if (loc.getBlock().getType() == Material.CHEST) {
					loc.getBlock().setType(Material.AIR);
					swept++;
				}
			} else if (entry.kind() == Kind.HIDDEN_NPC) {
				for (final Entity e : loc.getWorld().getNearbyEntities(loc, 5, 5, 5)) {
					if (e instanceof Villager && hasHiddenName(e)) {
						e.remove();
						swept++;
					}
				}
			}
		}
		entries.clear();
		save();
		return swept;
	}

	private static boolean hasHiddenName(final Entity entity) {
		final String name = entity.getCustomName();
		return name != null && name.contains("Hidden NPC");
	}

	private void load() {
		if (!file.exists()) {
			return;
		}
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		entries.clear();
		loadKind(cfg, "chests", Kind.CHEST);
		loadKind(cfg, "hidden-npcs", Kind.HIDDEN_NPC);
	}

	private void loadKind(final YamlConfiguration cfg, final String key,
		final Kind kind) {

		final List<?> raw = cfg.getList(key);
		if (raw == null) {
			return;
		}
		for (final Object o : raw) {
			if (!(o instanceof ConfigurationSection || o instanceof java.util.Map)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			final java.util.Map<String, Object> map = o instanceof ConfigurationSection sec
				? sec.getValues(false)
				: (java.util.Map<String, Object>) o;
			try {
				final UUID world = UUID.fromString(String.valueOf(map.get("world")));
				final int x = ((Number) map.get("x")).intValue();
				final int y = ((Number) map.get("y")).intValue();
				final int z = ((Number) map.get("z")).intValue();
				entries.add(new Entry(kind, world, x, y, z));
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[PlacedEntityStore] Skipping malformed " + key + " entry", e);
			}
		}
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		cfg.set("chests", serialize(Kind.CHEST));
		cfg.set("hidden-npcs", serialize(Kind.HIDDEN_NPC));
		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[PlacedEntityStore] Could not save placed-entities.yml", e);
		}
	}

	private List<java.util.Map<String, Object>> serialize(final Kind kind) {
		final List<java.util.Map<String, Object>> out = new ArrayList<>();
		for (final Entry e : entries) {
			if (e.kind() != kind) {
				continue;
			}
			final java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
			row.put("world", e.worldId().toString());
			row.put("x", e.x());
			row.put("y", e.y());
			row.put("z", e.z());
			out.add(row);
		}
		return out;
	}
}
