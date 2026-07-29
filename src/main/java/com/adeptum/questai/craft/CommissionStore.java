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
 * The work every village has in hand for a player, persisted to
 * village-commissions.yml.
 *
 * <p>Rows are keyed by the village-names row id and then by player, the same
 * shape the standing ledger uses, so a village keeps one piece of work per
 * player at a time. Nothing is scheduled in memory: the wait is a stored
 * timestamp, so an order placed before a restart — or before a week away —
 * is simply finished when the player comes back for it.
 *
 * <p>A village that loses its name loses the key its orders were filed
 * under, and those orders are stranded. That is the same exposure the works
 * ledger already carries, and it is not solved here.
 */
public final class CommissionStore {

	private static final String ROOT = "commissions";

	private final JavaPlugin plugin;
	private final File file;
	private final Map<String, Map<UUID, CommissionOrder>> villages =
		new HashMap<>();

	public CommissionStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "village-commissions.yml");
		load();
	}

	/** The work this village has in hand for the player, or null. */
	public synchronized CommissionOrder get(final String rowId,
		final UUID playerId) {

		final Map<UUID, CommissionOrder> players = villages.get(rowId);
		return players == null ? null : players.get(playerId);
	}

	/** Takes the order, replacing anything the village was already making. */
	public synchronized void place(final String rowId, final UUID playerId,
		final CommissionOrder order) {

		villages.computeIfAbsent(rowId, key -> new HashMap<>())
			.put(playerId, order);
		save();
	}

	/** How many orders this village has on its shelf. */
	public synchronized int orderCount(final String rowId) {
		final Map<UUID, CommissionOrder> orders = villages.get(rowId);
		return orders == null ? 0 : orders.size();
	}

	/** Clears the shelf once the piece has been handed over. */
	public synchronized void clear(final String rowId, final UUID playerId) {
		final Map<UUID, CommissionOrder> players = villages.get(rowId);
		if (players == null || players.remove(playerId) == null) {
			return;
		}
		if (players.isEmpty()) {
			villages.remove(rowId);
		}
		save();
	}

	/**
	 * Folds one village's orders into another's. A player may only have one
	 * order open per village, so where both rows hold one the earlier is
	 * kept — it is the one they have been waiting longer for.
	 */
	public synchronized void merge(final String fromId, final String intoId) {
		if (fromId == null || intoId == null || fromId.equals(intoId)) {
			return;
		}
		final Map<UUID, CommissionOrder> from = villages.remove(fromId);
		if (from == null) {
			return;
		}
		final Map<UUID, CommissionOrder> into =
			villages.computeIfAbsent(intoId, key -> new HashMap<>());
		from.forEach((playerId, order) -> into.merge(playerId, order,
			(kept, incoming) ->
				kept.placedAt() <= incoming.placedAt() ? kept : incoming));
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
					"[CommissionStore] Skipping malformed commission row "
						+ rowId, e);
			}
		}
	}

	private void loadRow(final String rowId, final ConfigurationSection row) {
		if (row == null) {
			return;
		}
		final Map<UUID, CommissionOrder> players = new HashMap<>();
		for (final String playerId : row.getKeys(false)) {
			players.put(UUID.fromString(playerId),
				readOrder(row.getConfigurationSection(playerId)));
		}
		villages.put(rowId, players);
	}

	/**
	 * Reads one order. A piece the catalogue no longer makes is refused
	 * rather than carried forward as a name nothing can build.
	 */
	private static CommissionOrder readOrder(final ConfigurationSection entry) {
		if (entry == null) {
			throw new IllegalArgumentException("commission row is empty");
		}
		final String commission = readString(entry, "commission");
		if (Commission.byName(commission) == null) {
			throw new IllegalArgumentException(
				commission + " is not a piece we make");
		}
		return new CommissionOrder(commission,
			readUuid(entry, "craftsman"), readLong(entry, "placedAt"),
			readLong(entry, "readyAt"));
	}

	/**
	 * Reads a timestamp, refusing a non-numeric value rather than silently
	 * defaulting it, so a corrupt row is skipped as malformed.
	 */
	private static long readLong(final ConfigurationSection section,
		final String path) {

		final Object raw = section.get(path);
		if (raw == null) {
			return 0L;
		}
		if (!(raw instanceof Number number)) {
			throw new IllegalArgumentException(path + " is not a number");
		}
		return number.longValue();
	}

	/** As {@link #readLong}, for a value that must be present and textual. */
	private static String readString(final ConfigurationSection section,
		final String path) {

		final Object raw = section.get(path);
		if (!(raw instanceof String text) || text.isEmpty()) {
			throw new IllegalArgumentException(path + " is not a name");
		}
		return text;
	}

	/** As {@link #readString}, for the craftsman who took the order. */
	private static UUID readUuid(final ConfigurationSection section,
		final String path) {

		return UUID.fromString(readString(section, path));
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		villages.forEach((rowId, players) -> players.forEach(
			(playerId, order) -> {
				final String base = ROOT + "." + rowId + "." + playerId;
				cfg.set(base + ".commission", order.commission());
				cfg.set(base + ".craftsman", order.craftsman().toString());
				cfg.set(base + ".placedAt", order.placedAt());
				cfg.set(base + ".readyAt", order.readyAt());
			}));

		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[CommissionStore] Could not save village-commissions.yml", e);
		}
	}
}
