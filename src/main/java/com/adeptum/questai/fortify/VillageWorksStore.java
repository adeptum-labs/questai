package com.adeptum.questai.fortify;

import com.adeptum.questai.villager.StoredLocation;
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
 * Construction progress per village, persisted to village-works.yml.
 *
 * <p>Rows are keyed by the village-names row id rather than by a cell, so a
 * village's buildings follow the thing that carries its name. Two villages can
 * share a 64-block cell, which is why cell keying is wrong here.
 */
public final class VillageWorksStore {

	private static final String ROOT = "works";

	private final JavaPlugin plugin;
	private final File file;
	private final Map<String, WorkState> states = new HashMap<>();

	public VillageWorksStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "village-works.yml");
		load();
	}

	/** The state for this village, or null when it has never been touched. */
	public synchronized WorkState get(final String rowId) {
		return states.get(rowId);
	}

	/** Every known village's state, for the build scheduler to walk. */
	public synchronized Map<String, WorkState> all() {
		return Map.copyOf(states);
	}

	/** Adds material toward the active project and writes through. */
	public synchronized void donate(final String rowId, final String role,
		final int amount) {

		if (amount <= 0) {
			return;
		}
		final WorkState state =
			states.computeIfAbsent(rowId, key -> new WorkState());
		state.getTally().merge(role, amount, Integer::sum);
		save();
	}

	/** Records where the funded project will stand and how it is turned. */
	public synchronized void beginBuild(final String rowId,
		final StoredLocation site, final int rotation) {

		final WorkState state =
			states.computeIfAbsent(rowId, key -> new WorkState());
		state.setSite(site);
		state.setRotation(rotation);
		state.setStage(0);
		state.setStageAt(System.currentTimeMillis());
		save();
	}

	/** Marks one more construction stage as standing. */
	public synchronized void advanceStage(final String rowId, final long now) {
		final WorkState state = states.get(rowId);
		if (state == null) {
			return;
		}
		state.setStage(state.getStage() + 1);
		state.setStageAt(now);
		save();
	}

	/** Records how far each palisade front has got; stamps the pulse clock. */
	public synchronized void advanceFronts(final String rowId,
		final int forward, final int backward, final long now) {

		final WorkState state = states.get(rowId);
		if (state == null) {
			return;
		}
		state.setFrontForward(forward);
		state.setFrontBackward(backward);
		state.setStageAt(now);
		save();
	}

	/** Remembers a ring slot the wall must forever skip. */
	public synchronized void recordGap(final String rowId, final int index) {
		final WorkState state = states.get(rowId);
		if (state == null || state.getRingGaps().contains(index)) {
			return;
		}
		state.getRingGaps().add(index);
		save();
	}

	/** Forgets a skipped ring slot, once the wall has caught up with it. */
	public synchronized void clearGap(final String rowId, final int index) {
		final WorkState state = states.get(rowId);
		// Removes the slot number, not the list position at that number
		if (state == null || !state.getRingGaps().remove(Integer.valueOf(index))) {
			return;
		}
		save();
	}

	/** Moves the village up a rung and clears the finished project. */
	public synchronized void completeTier(final String rowId) {
		final WorkState state = states.get(rowId);
		if (state == null) {
			return;
		}
		if (state.getSite() != null) {
			state.getBuiltSites().put(state.getTier(),
				new WorkState.BuiltSite(state.getSite(), state.getRotation()));
		}
		state.setTier(state.getTier() + 1);
		state.setStage(0);
		state.setStageAt(0L);
		state.setSite(null);
		state.setRotation(0);
		state.getTally().clear();
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
				final ConfigurationSection row =
					root.getConfigurationSection(rowId);
				if (row != null) {
					states.put(rowId, loadState(row));
				}
			} catch (final RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillageWorksStore] Skipping malformed works row " + rowId, e);
			}
		}
	}

	private static WorkState loadState(final ConfigurationSection row) {
		final WorkState state = new WorkState();
		state.setTier(readInt(row, "tier"));
		state.setStage(readInt(row, "stage"));
		state.setStageAt(readLong(row, "stageAt"));
		state.setRotation(readInt(row, "rotation"));

		final ConfigurationSection site = row.getConfigurationSection("site");
		if (site != null) {
			state.setSite(new StoredLocation(
				UUID.fromString(site.getString("world")),
				readDouble(site, "x"), readDouble(site, "y"),
				readDouble(site, "z")));
		}

		final ConfigurationSection tally = row.getConfigurationSection("tally");
		if (tally != null) {
			for (final String role : tally.getKeys(false)) {
				state.getTally().put(role, readInt(tally, role));
			}
		}

		state.setFrontForward(readInt(row, "frontForward"));
		state.setFrontBackward(readInt(row, "frontBackward"));
		state.getRingGaps().addAll(row.getIntegerList("ringGaps"));

		final ConfigurationSection built = row.getConfigurationSection("built");
		if (built != null) {
			for (final String tier : built.getKeys(false)) {
				final ConfigurationSection entry =
					built.getConfigurationSection(tier);
				state.getBuiltSites().put(Integer.parseInt(tier),
					new WorkState.BuiltSite(new StoredLocation(
						UUID.fromString(entry.getString("world")),
						readDouble(entry, "x"), readDouble(entry, "y"),
						readDouble(entry, "z")), readInt(entry, "rotation")));
			}
		}
		return state;
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

	/**
	 * Reads a long, refusing a non-numeric value rather than silently
	 * defaulting it to zero, so a corrupt row is skipped as malformed.
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

	/**
	 * Reads a double, refusing a non-numeric value rather than silently
	 * defaulting it to zero, so a corrupt row is skipped as malformed.
	 */
	private static double readDouble(final ConfigurationSection section,
		final String path) {

		final Object raw = section.get(path);
		if (raw == null) {
			return 0.0;
		}
		if (!(raw instanceof Number number)) {
			throw new IllegalArgumentException(path + " is not a number");
		}
		return number.doubleValue();
	}

	private void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		states.forEach((rowId, state) -> {
			final String base = ROOT + "." + rowId;
			cfg.set(base + ".tier", state.getTier());
			cfg.set(base + ".stage", state.getStage());
			cfg.set(base + ".stageAt", state.getStageAt());
			cfg.set(base + ".rotation", state.getRotation());
			if (state.getSite() != null) {
				cfg.set(base + ".site.world", state.getSite().worldId().toString());
				cfg.set(base + ".site.x", state.getSite().x());
				cfg.set(base + ".site.y", state.getSite().y());
				cfg.set(base + ".site.z", state.getSite().z());
			}
			state.getTally().forEach((role, amount) ->
				cfg.set(base + ".tally." + role, amount));
			state.getBuiltSites().forEach((tier, built) -> {
				final String key = base + ".built." + tier;
				cfg.set(key + ".world", built.origin().worldId().toString());
				cfg.set(key + ".x", built.origin().x());
				cfg.set(key + ".y", built.origin().y());
				cfg.set(key + ".z", built.origin().z());
				cfg.set(key + ".rotation", built.rotation());
			});
			cfg.set(base + ".frontForward", state.getFrontForward());
			cfg.set(base + ".frontBackward", state.getFrontBackward());
			if (!state.getRingGaps().isEmpty()) {
				cfg.set(base + ".ringGaps", state.getRingGaps());
			}
		});

		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[VillageWorksStore] Could not save village-works.yml", e);
		}
	}
}
