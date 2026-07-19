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

import com.adeptum.questai.quest.QuestChains;
import com.adeptum.questai.service.DeliveryRecipientPicker;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persists villager profiles (unique name, personality, per-player memory)
 * to villager-profiles.yml. Also migrates the legacy villagerUniqueNames
 * section from config.yml into name-only profiles on first load.
 */
public final class VillagerProfileStore {

	private static final int MAX_EVENTS = 8;
	private static final int MAX_HEARSAY = 4;
	private static final long PLAYER_MEMORY_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000;

	private final JavaPlugin plugin;
	private final File file;
	private final Map<UUID, VillagerProfile> profiles = new HashMap<>();

	public VillagerProfileStore(final JavaPlugin plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "villager-profiles.yml");
		load();
		migrateLegacyNames();
	}

	public synchronized VillagerProfile get(final UUID villagerId) {
		return profiles.get(villagerId);
	}

	public synchronized String getName(final UUID villagerId) {
		final VillagerProfile profile = profiles.get(villagerId);
		return profile == null ? null : profile.getName();
	}

	public synchronized boolean hasProfile(final UUID villagerId) {
		return profiles.containsKey(villagerId);
	}

	/**
	 * Remembers where a profiled villager was last seen. The in-memory value
	 * always updates, but the file is only rewritten when the villager is
	 * new to a world or has moved more than 32 blocks, to bound save churn
	 * from chunk loads.
	 */
	public synchronized void updateLocation(final UUID villagerId,
		final Location location) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return;
		}
		final StoredLocation previous = profile.getLocation();
		final StoredLocation current = StoredLocation.from(location);
		profile.setLocation(current);

		final boolean movedFar = previous == null
			|| !previous.worldId().equals(current.worldId())
			|| previous.distanceSquaredXz(current.x(), current.z()) > 32 * 32;
		if (movedFar) {
			save();
		}
	}

	/** Forgets a villager's position, e.g. when the villager dies. */
	public synchronized void clearLocation(final UUID villagerId) {
		final VillagerProfile profile = profiles.get(villagerId);
		if (profile != null && profile.getLocation() != null) {
			profile.setLocation(null);
			save();
		}
	}

	/**
	 * Ties two villagers together symmetrically, re-validating profiles,
	 * caps and existing ties under the lock. Does not save; callers batch.
	 *
	 * @return true when the tie was formed
	 */
	/* default */ synchronized boolean addTie(final UUID firstId,
		final UUID secondId, final Relationship.Type type) {

		final VillagerProfile first = profiles.get(firstId);
		final VillagerProfile second = profiles.get(secondId);
		if (!tieAllowed(first, second, firstId, secondId)) {
			return false;
		}
		first.getRelationships().add(
			new Relationship(type, secondId, second.getName()));
		second.getRelationships().add(
			new Relationship(type, firstId, first.getName()));
		return true;
	}

	private boolean tieAllowed(final VillagerProfile first,
		final VillagerProfile second, final UUID firstId, final UUID secondId) {

		return first != null && second != null
			&& first.getRelationships().size() < Relationship.MAX_TIES
			&& second.getRelationships().size() < Relationship.MAX_TIES
			&& tieBetween(firstId, secondId) == null
			&& tieBetween(secondId, firstId) == null;
	}

	/** The villagers this villager is tied to; empty when unknown. */
	public synchronized Set<UUID> tieTargets(final UUID villagerId) {
		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return Set.of();
		}
		final Set<UUID> targets = new HashSet<>();
		for (final Relationship tie : profile.getRelationships()) {
			targets.add(tie.otherId());
		}
		return targets;
	}

	/** This villager's tie to a specific other villager, or null. */
	public synchronized Relationship tieBetween(final UUID villagerId,
		final UUID otherId) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return null;
		}
		for (final Relationship tie : profile.getRelationships()) {
			if (tie.otherId().equals(otherId)) {
				return tie;
			}
		}
		return null;
	}

	/** The player's pending quest chain with a villager, or null. */
	public synchronized ChainState chainState(final UUID villagerId,
		final UUID playerId) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return null;
		}
		final PlayerMemory memory = profile.getPlayers().get(playerId);
		return memory == null ? null : memory.getChain();
	}

	/**
	 * Opens a quest chain unless one is already pending with this
	 * villager. Silently ignored for villagers without a profile.
	 *
	 * @return true when the chain was opened
	 */
	public synchronized boolean openChain(final UUID villagerId,
		final UUID playerId, final int length, final String lastTitle) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return false;
		}
		final PlayerMemory memory = profile.getPlayers()
			.computeIfAbsent(playerId, k -> new PlayerMemory());
		if (memory.getChain() != null) {
			return false;
		}
		memory.setChain(new ChainState(2, length, lastTitle));
		save();
		return true;
	}

	/** Moves a pending chain to its next step after a completed quest. */
	public synchronized void advanceChain(final UUID villagerId,
		final UUID playerId, final String completedTitle) {

		final ChainState chain = chainState(villagerId, playerId);
		if (chain != null) {
			profiles.get(villagerId).getPlayers().get(playerId).setChain(
				new ChainState(chain.step() + 1, chain.length(), completedTitle));
			save();
		}
	}

	/** Forgets a pending chain, e.g. when a step is rejected or abandoned. */
	public synchronized void clearChain(final UUID villagerId,
		final UUID playerId) {

		if (chainState(villagerId, playerId) != null) {
			profiles.get(villagerId).getPlayers().get(playerId).setChain(null);
			save();
		}
	}

	/**
	 * Runs one gossip exchange over stored locations and saves once when
	 * anything changed. Cheap enough for the main thread.
	 */
	public synchronized void spreadGossip() {
		final Random random = new Random();
		boolean changed = false;
		for (final GossipSpreader.Share share
			: GossipSpreader.plan(snapshots(), random)) {
			changed |= addHearsay(share.receiverId(), share.playerId(),
				share.hearsay());
		}
		for (final RelationshipFormer.Tie tie
			: RelationshipFormer.plan(tieSnapshots(), random)) {
			changed |= addTie(tie.firstId(), tie.secondId(), tie.type());
		}
		if (changed) {
			save();
		}
	}

	private List<RelationshipFormer.Snapshot> tieSnapshots() {
		final List<RelationshipFormer.Snapshot> snapshots = new ArrayList<>();
		for (final Map.Entry<UUID, VillagerProfile> entry : profiles.entrySet()) {
			final VillagerProfile profile = entry.getValue();
			if (profile.getLocation() != null) {
				snapshots.add(new RelationshipFormer.Snapshot(entry.getKey(),
					profile.getName(), profile.getLocation(),
					profile.getRelationships().size(),
					tieTargets(entry.getKey())));
			}
		}
		return snapshots;
	}

	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	private List<GossipSpreader.Snapshot> snapshots() {
		final List<GossipSpreader.Snapshot> snapshots = new ArrayList<>();
		for (final Map.Entry<UUID, VillagerProfile> entry : profiles.entrySet()) {
			final VillagerProfile profile = entry.getValue();
			if (profile.getLocation() == null) {
				continue;
			}
			final Map<UUID, List<MemoryEvent>> events = new HashMap<>();
			for (final Map.Entry<UUID, PlayerMemory> player
				: profile.getPlayers().entrySet()) {
				if (!player.getValue().getEvents().isEmpty()) {
					events.put(player.getKey(),
						List.copyOf(player.getValue().getEvents()));
				}
			}
			snapshots.add(new GossipSpreader.Snapshot(entry.getKey(),
				profile.getName(), profile.getLocation(), events));
		}
		return snapshots;
	}

	/** All villagers in the given world with a known location. */
	public synchronized List<DeliveryRecipientPicker.Candidate> locatedVillagers(
		final UUID worldId) {

		// A null exclusion id matches no villager, so nothing is excluded
		return deliveryCandidates(null, worldId);
	}

	/**
	 * Returns all villagers in the given world with a known location that
	 * could receive a delivery, excluding the quest giver.
	 */
	public synchronized List<DeliveryRecipientPicker.Candidate> deliveryCandidates(
		final UUID excludeVillagerId, final UUID worldId) {

		final List<DeliveryRecipientPicker.Candidate> candidates = new ArrayList<>();
		for (final Map.Entry<UUID, VillagerProfile> entry : profiles.entrySet()) {
			final VillagerProfile profile = entry.getValue();
			if (!entry.getKey().equals(excludeVillagerId)
				&& profile.getLocation() != null
				&& profile.getLocation().worldId().equals(worldId)) {
				candidates.add(new DeliveryRecipientPicker.Candidate(
					entry.getKey(), profile.getName(),
					profile.getProfession(), profile.getLocation()));
			}
		}
		return candidates;
	}

	/**
	 * Stores a profile for a newly generated persona. If the name is already
	 * taken by another villager a numeric suffix is appended.
	 *
	 * @return the final, possibly suffixed, unique name
	 */
	public synchronized String register(final UUID villagerId,
		final VillagerPersona persona, final String profession) {

		String name = persona.name();
		if (isNameTaken(villagerId, name)) {
			name = name + "_" + ThreadLocalRandom.current().nextInt(1000, 10000);
		}

		profiles.put(villagerId, VillagerProfile.builder()
			.name(name)
			.profession(profession)
			.traits(persona.traits())
			.greeting(persona.greeting())
			.build());
		save();
		return name;
	}

	/**
	 * Bumps the conversation count for a player. Silently ignored for
	 * villagers without a profile.
	 */
	public synchronized void recordConversation(final UUID villagerId,
		final UUID playerId) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return;
		}
		final PlayerMemory memory = profile.getPlayers()
			.computeIfAbsent(playerId, k -> new PlayerMemory());
		memory.setConversations(memory.getConversations() + 1);
		memory.setLastSeen(System.currentTimeMillis());
		save();
	}

	/**
	 * Remembers a quest interaction, keeping only the most recent events.
	 * Silently ignored for villagers without a profile.
	 */
	public synchronized void recordEvent(final UUID villagerId, final UUID playerId,
		final MemoryEvent.Type type, final String questTitle) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null) {
			return;
		}
		final PlayerMemory memory = profile.getPlayers()
			.computeIfAbsent(playerId, k -> new PlayerMemory());
		memory.setLastSeen(System.currentTimeMillis());
		memory.getEvents().add(
			new MemoryEvent(type, questTitle, System.currentTimeMillis()));
		while (memory.getEvents().size() > MAX_EVENTS) {
			memory.getEvents().remove(0);
		}
		save();
	}

	/**
	 * Stores one piece of hearsay, ignoring duplicates of the same deed and
	 * gossip about the villager's own doings. Does not save; callers batch.
	 *
	 * @return true when the hearsay was stored
	 */
	/* default */ synchronized boolean addHearsay(final UUID villagerId,
		final UUID playerId, final HearsayEvent hearsay) {

		final VillagerProfile profile = profiles.get(villagerId);
		if (profile == null || hearsay.sourceName().equals(profile.getName())) {
			return false;
		}
		final PlayerMemory memory = profile.getPlayers()
			.computeIfAbsent(playerId, k -> new PlayerMemory());
		if (memory.getHearsay().stream().anyMatch(hearsay::sameDeed)) {
			return false;
		}
		memory.getHearsay().add(hearsay);
		while (memory.getHearsay().size() > MAX_HEARSAY) {
			memory.getHearsay().remove(0);
		}
		return true;
	}

	private boolean isNameTaken(final UUID villagerId, final String name) {
		return profiles.entrySet().stream()
			.anyMatch(e -> !e.getKey().equals(villagerId)
				&& name.equals(e.getValue().getName()));
	}

	private void load() {
		if (!file.exists()) {
			return;
		}
		final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		final ConfigurationSection root = cfg.getConfigurationSection("villagers");
		if (root == null) {
			return;
		}

		final long cutoff = System.currentTimeMillis() - PLAYER_MEMORY_TTL_MILLIS;
		for (final String key : root.getKeys(false)) {
			try {
				loadProfile(UUID.fromString(key),
					root.getConfigurationSection(key), cutoff);
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillagerProfileStore] Skipping malformed profile " + key, e);
			}
		}
	}

	private void loadProfile(final UUID villagerId, final ConfigurationSection sec,
		final long cutoff) {

		final String name = sec.getString("name");
		if (name == null) {
			return;
		}
		final VillagerProfile profile = VillagerProfile.builder()
			.name(name)
			.profession(sec.getString("profession"))
			.traits(List.copyOf(sec.getStringList("traits")))
			.greeting(sec.getString("greeting"))
			.location(loadLocation(sec.getConfigurationSection("location")))
			.build();
		loadRelationships(sec.getMapList("relationships"), profile);
		loadPlayers(sec.getConfigurationSection("players"), profile, cutoff);
		profiles.put(villagerId, profile);
	}

	private void loadRelationships(final List<Map<?, ?>> rows,
		final VillagerProfile profile) {

		for (final Map<?, ?> row : rows) {
			if (profile.getRelationships().size() >= Relationship.MAX_TIES) {
				return;
			}
			try {
				profile.getRelationships().add(new Relationship(
					Relationship.Type.valueOf(String.valueOf(row.get("type"))),
					UUID.fromString(String.valueOf(row.get("other"))),
					String.valueOf(row.get("name"))));
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillagerProfileStore] Skipping malformed relationship", e);
			}
		}
	}

	private StoredLocation loadLocation(final ConfigurationSection sec) {
		if (sec == null) {
			return null;
		}
		try {
			return new StoredLocation(
				UUID.fromString(String.valueOf(sec.getString("world"))),
				sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
		} catch (IllegalArgumentException e) {
			plugin.getLogger().log(Level.WARNING,
				"[VillagerProfileStore] Skipping malformed location", e);
			return null;
		}
	}

	private void loadPlayers(final ConfigurationSection playersSec,
		final VillagerProfile profile, final long cutoff) {

		if (playersSec == null) {
			return;
		}
		for (final String key : playersSec.getKeys(false)) {
			final ConfigurationSection sec = playersSec.getConfigurationSection(key);
			if (sec != null) {
				loadPlayer(sec, key, profile, cutoff);
			}
		}
	}

	/**
	 * Loads one player's memory. First-hand data is dropped entirely when
	 * the player was last seen outside the retention window, while hearsay
	 * rows are pruned individually by their own timestamps.
	 */
	private void loadPlayer(final ConfigurationSection sec, final String key,
		final VillagerProfile profile, final long cutoff) {

		final PlayerMemory memory = new PlayerMemory();
		final long lastSeen = sec.getLong("lastSeen");
		if (lastSeen >= cutoff) {
			memory.setConversations(sec.getInt("conversations"));
			memory.setLastSeen(lastSeen);
			loadEvents(sec.getMapList("events"), memory);
			memory.setChain(loadChain(sec.getConfigurationSection("chain")));
		}
		loadHearsay(sec.getMapList("hearsay"), memory, cutoff);

		if (memory.getLastSeen() >= cutoff || !memory.getHearsay().isEmpty()) {
			profile.getPlayers().put(UUID.fromString(key), memory);
		}
	}

	private ChainState loadChain(final ConfigurationSection sec) {
		if (sec == null) {
			return null;
		}
		final int step = sec.getInt("step");
		final int length = sec.getInt("length");
		final String lastTitle = sec.getString("lastTitle");
		if (step < 2 || step > length || length > QuestChains.MAX_LENGTH
			|| lastTitle == null) {
			plugin.getLogger().warning(
				"[VillagerProfileStore] Skipping malformed chain state");
			return null;
		}
		return new ChainState(step, length, lastTitle);
	}

	private void loadHearsay(final List<Map<?, ?>> rows,
		final PlayerMemory memory, final long cutoff) {

		for (final Map<?, ?> row : rows) {
			try {
				final long at = ((Number) row.get("at")).longValue();
				if (at >= cutoff) {
					memory.getHearsay().add(new HearsayEvent(
						MemoryEvent.Type.valueOf(String.valueOf(row.get("type"))),
						String.valueOf(row.get("title")),
						String.valueOf(row.get("source")), at));
				}
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillagerProfileStore] Skipping malformed hearsay", e);
			}
		}
	}

	private void loadEvents(final List<Map<?, ?>> rows, final PlayerMemory memory) {
		for (final Map<?, ?> row : rows) {
			try {
				memory.getEvents().add(new MemoryEvent(
					MemoryEvent.Type.valueOf(String.valueOf(row.get("type"))),
					String.valueOf(row.get("title")),
					((Number) row.get("at")).longValue()));
			} catch (RuntimeException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillagerProfileStore] Skipping malformed memory event", e);
			}
		}
	}

	private void migrateLegacyNames() {
		final File configFile = new File(plugin.getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			return;
		}
		final FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
		final ConfigurationSection legacy =
			cfg.getConfigurationSection("villagerUniqueNames");
		if (legacy == null) {
			return;
		}

		int migrated = 0;
		for (final String key : legacy.getKeys(false)) {
			migrated += migrateLegacyName(key, legacy.getString(key));
		}

		save();
		cfg.set("villagerUniqueNames", null);
		try {
			cfg.save(configFile);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[VillagerProfileStore] Could not update config.yml", e);
		}
		plugin.getLogger().info("[VillagerProfileStore] Migrated " + migrated
			+ " legacy villager name(s).");
	}

	private int migrateLegacyName(final String key, final String name) {
		try {
			final UUID villagerId = UUID.fromString(key);
			if (name != null && !profiles.containsKey(villagerId)) {
				profiles.put(villagerId,
					VillagerProfile.builder().name(name).build());
				return 1;
			}
		} catch (IllegalArgumentException e) {
			plugin.getLogger().log(Level.WARNING,
				"[VillagerProfileStore] Skipping malformed legacy name " + key, e);
		}
		return 0;
	}

	public synchronized void save() {
		final YamlConfiguration cfg = new YamlConfiguration();
		for (final Map.Entry<UUID, VillagerProfile> entry : profiles.entrySet()) {
			saveProfile(cfg, "villagers." + entry.getKey(), entry.getValue());
		}
		try {
			file.getParentFile().mkdirs();
			cfg.save(file);
		} catch (final IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[VillagerProfileStore] Could not save villager-profiles.yml", e);
		}
	}

	private void saveProfile(final YamlConfiguration cfg, final String base,
		final VillagerProfile profile) {

		cfg.set(base + ".name", profile.getName());
		cfg.set(base + ".profession", profile.getProfession());
		if (!profile.getTraits().isEmpty()) {
			cfg.set(base + ".traits", profile.getTraits());
		}
		if (profile.getGreeting() != null) {
			cfg.set(base + ".greeting", profile.getGreeting());
		}
		if (profile.getLocation() != null) {
			final StoredLocation loc = profile.getLocation();
			cfg.set(base + ".location.world", loc.worldId().toString());
			cfg.set(base + ".location.x", loc.x());
			cfg.set(base + ".location.y", loc.y());
			cfg.set(base + ".location.z", loc.z());
		}
		if (!profile.getRelationships().isEmpty()) {
			cfg.set(base + ".relationships", serializeRelationships(profile));
		}
		savePlayers(cfg, base, profile);
	}

	@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
	private List<Map<String, Object>> serializeRelationships(
		final VillagerProfile profile) {

		final List<Map<String, Object>> rows = new ArrayList<>();
		for (final Relationship tie : profile.getRelationships()) {
			final Map<String, Object> row = new LinkedHashMap<>();
			row.put("type", tie.type().name());
			row.put("other", tie.otherId().toString());
			row.put("name", tie.otherName());
			rows.add(row);
		}
		return rows;
	}

	private void savePlayers(final YamlConfiguration cfg, final String base,
		final VillagerProfile profile) {

		for (final Map.Entry<UUID, PlayerMemory> entry
			: profile.getPlayers().entrySet()) {

			final String playerBase = base + ".players." + entry.getKey();
			final PlayerMemory memory = entry.getValue();
			cfg.set(playerBase + ".conversations", memory.getConversations());
			cfg.set(playerBase + ".lastSeen", memory.getLastSeen());
			if (!memory.getEvents().isEmpty()) {
				cfg.set(playerBase + ".events", serializeEvents(memory));
			}
			if (!memory.getHearsay().isEmpty()) {
				cfg.set(playerBase + ".hearsay", serializeHearsay(memory));
			}
			if (memory.getChain() != null) {
				cfg.set(playerBase + ".chain.step", memory.getChain().step());
				cfg.set(playerBase + ".chain.length", memory.getChain().length());
				cfg.set(playerBase + ".chain.lastTitle",
					memory.getChain().lastTitle());
			}
		}
	}

	private List<Map<String, Object>> serializeEvents(final PlayerMemory memory) {
		final List<Map<String, Object>> rows = new ArrayList<>();
		for (final MemoryEvent event : memory.getEvents()) {
			final Map<String, Object> row = new LinkedHashMap<>();
			row.put("type", event.type().name());
			row.put("title", event.questTitle());
			row.put("at", event.at());
			rows.add(row);
		}
		return rows;
	}

	private List<Map<String, Object>> serializeHearsay(final PlayerMemory memory) {
		final List<Map<String, Object>> rows = new ArrayList<>();
		for (final HearsayEvent event : memory.getHearsay()) {
			final Map<String, Object> row = new LinkedHashMap<>();
			row.put("type", event.type().name());
			row.put("title", event.questTitle());
			row.put("source", event.sourceName());
			row.put("at", event.at());
			rows.add(row);
		}
		return rows;
	}
}
