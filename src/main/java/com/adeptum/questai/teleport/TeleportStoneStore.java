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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Which villages have a teleport stone out in the world, persisted to
 * teleport-stones.yml.
 *
 * <p>Exactly one stone per village may exist at a time, and a scan of the
 * live world cannot answer that on its own: a stone in a chest, in an
 * unloaded chunk, or in the pocket of a player who has logged off is
 * invisible to it, and a village asked while the holder is away would
 * cheerfully hand out a second. Writing the issue down is what makes the
 * rule hold across all of those.
 *
 * <p>A record is only given up when the stone is seen to be genuinely
 * destroyed, so the village can issue a replacement for one lost to lava or
 * a despawn. Records the store never heard of — stones that predate it, or
 * that surface from a chest — are adopted on sight rather than treated as
 * free, because the safe failure is a village that will not re-issue, not
 * one that issues twice.
 */
public final class TeleportStoneStore {

	private static final String ROOT = "stones";

	private final JavaPlugin plugin;
	private final File file;
	/** Village row id to who it was issued to, and when. */
	private final Map<String, Entry> issued = new HashMap<>();

	public TeleportStoneStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "teleport-stones.yml");
		load();
	}

	/** Whether this village's stone is already out in the world. */
	public synchronized boolean issued(final String rowId) {
		return rowId != null && issued.containsKey(rowId);
	}

	/** How many villages have a stone out; the enable log reports this. */
	public synchronized int size() {
		return issued.size();
	}

	/** Who holds this village's stone, or null when none is out. */
	public synchronized UUID holderOf(final String rowId) {
		final Entry entry = rowId == null ? null : issued.get(rowId);
		return entry == null ? null : entry.holder();
	}

	/** Writes down that this village has handed its stone to this player. */
	public synchronized void issue(final String rowId, final UUID holder) {
		if (rowId == null) {
			return;
		}
		issued.put(rowId, new Entry(holder, System.currentTimeMillis()));
		save();
	}

	/**
	 * Takes a stone the store had not heard of onto the books, naming who
	 * turned out to be carrying it. Answers whether this was news, so a
	 * scan can report what it adopted without asking twice.
	 */
	public synchronized boolean adopt(final String rowId, final UUID holder) {
		if (rowId == null || issued.containsKey(rowId)) {
			return false;
		}
		issue(rowId, holder);
		return true;
	}

	/** Gives up a village's stone, freeing it to issue another. */
	public synchronized void release(final String rowId) {
		if (rowId != null && issued.remove(rowId) != null) {
			save();
		}
	}

	/**
	 * Folds one village's stone record into another's, keeping whichever
	 * was issued first. A village may only have one stone out, and the
	 * later holder's item now aliases onto the survivor.
	 */
	public synchronized void merge(final String fromId, final String intoId) {
		if (fromId == null || intoId == null || fromId.equals(intoId)) {
			return;
		}
		final Entry from = issued.remove(fromId);
		if (from == null) {
			return;
		}
		issued.merge(intoId, from,
			(into, incoming) -> into.at() <= incoming.at() ? into : incoming);
		save();
	}

	private void load() {
		if (!file.exists()) {
			return;
		}
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		final ConfigurationSection root = cfg.getConfigurationSection(ROOT);
		if (root == null) {
			return;
		}
		for (final String rowId : root.getKeys(false)) {
			try {
				loadRow(rowId, root.getConfigurationSection(rowId));
			} catch (final RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[TeleportStoneStore] Skipping malformed stone row "
						+ rowId, e);
			}
		}
	}

	/**
	 * A row with an unreadable holder is still a row: the village issued a
	 * stone, and forgetting that because the name is corrupt would let it
	 * issue a second one.
	 */
	private void loadRow(final String rowId, final ConfigurationSection row) {
		if (row == null) {
			return;
		}
		issued.put(rowId, new Entry(readHolder(row.getString("holder")),
			row.getLong("at")));
	}

	private static UUID readHolder(final String raw) {
		if (raw == null) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		for (final Map.Entry<String, Entry> row : issued.entrySet()) {
			final String path = ROOT + "." + row.getKey();
			final UUID holder = row.getValue().holder();
			cfg.set(path + ".holder", holder == null ? null : holder.toString());
			cfg.set(path + ".at", row.getValue().at());
		}
		try {
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[TeleportStoneStore] Could not save teleport-stones.yml", e);
		}
	}

	/** One issued stone: who took it, and when they did. */
	private record Entry(UUID holder, long at) {
	}
}
