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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
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
			.build();
		loadPlayers(sec.getConfigurationSection("players"), profile, cutoff);
		profiles.put(villagerId, profile);
	}

	private void loadPlayers(final ConfigurationSection playersSec,
		final VillagerProfile profile, final long cutoff) {

		if (playersSec == null) {
			return;
		}
		for (final String key : playersSec.getKeys(false)) {
			final ConfigurationSection sec = playersSec.getConfigurationSection(key);
			if (sec == null) {
				continue;
			}
			final long lastSeen = sec.getLong("lastSeen");
			if (lastSeen < cutoff) {
				continue;
			}

			final PlayerMemory memory = new PlayerMemory();
			memory.setConversations(sec.getInt("conversations"));
			memory.setLastSeen(lastSeen);
			loadEvents(sec.getMapList("events"), memory);
			profile.getPlayers().put(UUID.fromString(key), memory);
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
			try {
				final UUID villagerId = UUID.fromString(key);
				final String name = legacy.getString(key);
				if (name != null && !profiles.containsKey(villagerId)) {
					profiles.put(villagerId,
						VillagerProfile.builder().name(name).build());
					migrated++;
				}
			} catch (IllegalArgumentException e) {
				plugin.getLogger().log(Level.WARNING,
					"[VillagerProfileStore] Skipping malformed legacy name "
						+ key, e);
			}
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

		for (final Map.Entry<UUID, PlayerMemory> entry
			: profile.getPlayers().entrySet()) {

			final String playerBase = base + ".players." + entry.getKey();
			final PlayerMemory memory = entry.getValue();
			cfg.set(playerBase + ".conversations", memory.getConversations());
			cfg.set(playerBase + ".lastSeen", memory.getLastSeen());
			if (!memory.getEvents().isEmpty()) {
				cfg.set(playerBase + ".events", serializeEvents(memory));
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
}
