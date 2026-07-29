package com.adeptum.questai.reputation;

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
 * Every village's ledger of standing per player, persisted to
 * village-reputation.yml.
 *
 * <p>Rows are keyed by the village-names row id, the same key the works
 * store uses, so a village's opinion follows the thing that carries its
 * name. Mending is applied lazily from the stored timestamp — reading
 * computes the softened view, writing folds it in and re-stamps.
 */
public final class VillageReputationStore {

	private static final String ROOT = "reputation";

	private final JavaPlugin plugin;
	private final File file;
	private final Map<String, Map<UUID, Entry>> villages = new HashMap<>();

	public VillageReputationStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "village-reputation.yml");
		load();
	}

	/** The mended standing, without writing; zero for strangers. */
	public synchronized int get(final String rowId, final UUID playerId) {
		return getAt(rowId, playerId, System.currentTimeMillis());
	}

	/**
	 * Applies a change on top of the mended standing and writes through.
	 *
	 * @return the new standing
	 */
	public synchronized int adjust(final String rowId, final UUID playerId,
		final int delta) {

		return adjustAt(rowId, playerId, delta, System.currentTimeMillis());
	}

	synchronized int getAt(final String rowId, final UUID playerId,
		final long now) {

		final Map<UUID, Entry> players = villages.get(rowId);
		final Entry entry = players == null ? null : players.get(playerId);
		if (entry == null) {
			return 0;
		}
		return Reputation.mend(entry.value(), now - entry.at());
	}

	synchronized int adjustAt(final String rowId, final UUID playerId,
		final int delta, final long now) {

		final int value =
			Reputation.clamp(getAt(rowId, playerId, now) + delta);
		villages.computeIfAbsent(rowId, key -> new HashMap<>())
			.put(playerId, new Entry(value, now));
		save();
		return value;
	}

	/** Folds one village's ledger into another's. */
	public synchronized void merge(final String fromId, final String intoId) {
		mergeAt(fromId, intoId, System.currentTimeMillis());
	}

	/**
	 * Sums what each side is worth at one instant rather than adding the
	 * stored values, which are only true as of their own timestamps and
	 * would otherwise carry decay that has not yet happened.
	 */
	synchronized void mergeAt(final String fromId, final String intoId,
		final long now) {

		if (fromId == null || intoId == null || fromId.equals(intoId)) {
			return;
		}
		final Map<UUID, Entry> from = villages.remove(fromId);
		if (from == null || from.isEmpty()) {
			return;
		}
		foldInto(from, intoId, now);
		save();
	}

	/**
	 * Mends each of the departing row's entries to this instant and sums
	 * it onto the survivor's, one player at a time.
	 */
	private void foldInto(final Map<UUID, Entry> from, final String intoId,
		final long now) {

		final Map<UUID, Entry> into =
			villages.computeIfAbsent(intoId, key -> new HashMap<>());
		for (final Map.Entry<UUID, Entry> player : from.entrySet()) {
			final int carried = Reputation.mend(player.getValue().value(),
				now - player.getValue().at());
			final int value = Reputation.clamp(
				getAt(intoId, player.getKey(), now) + carried);
			into.put(player.getKey(), new Entry(value, now));
		}
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
					"[VillageReputationStore] Skipping malformed standing row "
						+ rowId, e);
			}
		}
	}

	private void loadRow(final String rowId, final ConfigurationSection row) {
		if (row == null) {
			return;
		}
		final Map<UUID, Entry> players = new HashMap<>();
		for (final String playerId : row.getKeys(false)) {
			final ConfigurationSection entry =
				row.getConfigurationSection(playerId);
			players.put(UUID.fromString(playerId), new Entry(
				Reputation.clamp(readInt(entry, "value")),
				readLong(entry, "at")));
		}
		villages.put(rowId, players);
	}

	/**
	 * Reads an integer, refusing a non-numeric value rather than silently
	 * defaulting it to zero, so a corrupt row is skipped as malformed.
	 */
	private static int readInt(final ConfigurationSection section,
		final String path) {

		final Object raw = section.get(path);
		if (raw == null) {
			return 0;
		}
		if (!(raw instanceof Number number)) {
			throw new IllegalArgumentException(path + " is not a number");
		}
		return number.intValue();
	}

	/** As {@link #readInt}, for the mending timestamp. */
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

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		villages.forEach((rowId, players) -> players.forEach(
			(playerId, entry) -> {
				final String base = ROOT + "." + rowId + "." + playerId;
				cfg.set(base + ".value", entry.value());
				cfg.set(base + ".at", entry.at());
			}));

		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[VillageReputationStore] Could not save village-reputation.yml",
				e);
		}
	}

	/** One player's stored standing and when it was last touched. */
	private record Entry(int value, long at) {
	}
}
