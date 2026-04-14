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

package com.adeptum.questai;

import static com.adeptum.questai.model.world.quest.QuestObjective.Type.COLLECT;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.FIND_NPC;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.KILL;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.TREASURE;

import com.adeptum.questai.dialogue.ConversationManager;
import com.adeptum.questai.dialogue.DialogueGui;
import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import com.adeptum.questai.quest.DestinationMarkerRenderer;
import com.adeptum.questai.quest.QuestManager;
import com.adeptum.questai.quest.QuestProgress;
import com.adeptum.questai.service.QuestGenerationService;
import com.gmail.nossr50.api.ExperienceAPI;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin that creates generated villager quests and conversational dialogue.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class RandomQuestPlugin implements SubPlugin {

	private static final double QUEST_CHANCE = 0.3;

	private final JavaPlugin plugin;
	private final ConversationManager conversationManager;
	private final QuestGenerationService questService;
	private final OpenAiChatModel chatModel;

	private QuestManager questManager;

	private final Map<UUID, String> villagerUniqueNames =
		Collections.synchronizedMap(new HashMap<>());
	private final ReentrantLock villagerUniqueNamesLock = new ReentrantLock();

	public RandomQuestPlugin(JavaPlugin plugin, ConversationManager conversationManager,
		QuestGenerationService questService, OpenAiChatModel chatModel) {

		super();
		this.plugin = plugin;
		this.conversationManager = conversationManager;
		this.questService = questService;
		this.chatModel = chatModel;
	}
	@Override
	public void onEnable() {
		final Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onEnable() start");

		loadVillagerNames();

		this.questManager = new QuestManager(plugin);
		questManager.setQuestCleanup(this::cleanupQuestEntities);
		conversationManager.setQuestManager(questManager);
		conversationManager.setQuestAcceptHandler(this::onQuestAccepted);

		assignUniqueNamesToAllVillagers();
		logger.info("[RandomQuestPlugin] onEnable() end -> plugin fully enabled.");
	}

	@Override
	public void onDisable() {
		questManager.cleanupAllQuests();
		saveVillagerUniqueNames();
	}

	public QuestManager getQuestManager() {
		return questManager;
	}

	@EventHandler
	public void onPlayerJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		final List<QuestProgress> quests = questManager.getActiveQuests(player);
		if (quests.isEmpty()) {
			return;
		}

		final List<QuestProgress> completed = quests.stream()
			.filter(p -> {
				final QuestObjective obj = p.getQuest().getObjective();
				return (obj.getType() == KILL || obj.getType() == COLLECT)
					&& p.getCurrent() >= obj.getAmount();
			})
			.toList();

		for (final QuestProgress progress : completed) {
			final Quest quest = progress.getQuest();
			questManager.removeBossBars(player, progress);
			questManager.completeQuest(player, quest);
			questManager.setVillagerData(quest.getVillagerUuid(), null);
			removeQuestIndicator(quest.getVillagerUuid());
			player.sendMessage("\u00a7aCompleted quest: " + quest.getShortTitle());
			rewardPlayer(player, quest);
		}
	}

	@EventHandler
	public void onVillagerClick(PlayerInteractEntityEvent event) {
		final Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			return;
		}

		final String uniqueName = villagerUniqueNames.get(villager.getUniqueId());
		if (uniqueName == null) {
			return;
		}

		event.setCancelled(true);
		final Player player = event.getPlayer();

		final Villager.Profession profession = villager.getProfession();
		final boolean tradeable = profession != Villager.Profession.NONE
			&& profession != Villager.Profession.NITWIT;

		final Npc npc = questManager.getVillagerData(villager.getUniqueId());
		final long currentTime = System.currentTimeMillis();
		final long twoHoursMillis = 2L * 60 * 60 * 1000;

		boolean questAvailable = false;

		if (npc != null && currentTime - npc.getTimestamp() <= twoHoursMillis) {
			questAvailable = npc.isQuest();
		} else {
			questManager.setVillagerData(villager.getUniqueId(), null);
			questAvailable = Math.random() <= QUEST_CHANCE;
		}

		conversationManager.startConversation(player, villager.getUniqueId(),
			uniqueName, profession.name(), questAvailable, tradeable);
	}
	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (!event.getView().getTitle().equals(DialogueGui.DIALOGUE_TITLE)) {
			return;
		}

		event.setCancelled(true);

		final Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) {
			return;
		}

		final ItemStack clickedItem = event.getCurrentItem();
		if (clickedItem == null || clickedItem.getType() == Material.AIR) {
			return;
		}

		final Player player = (Player) event.getWhoClicked();
		conversationManager.handleClick(player, event.getRawSlot());
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		if (!event.getView().getTitle().equals(DialogueGui.DIALOGUE_TITLE)) {
			return;
		}

		final Player player = (Player) event.getPlayer();
		if (conversationManager.isInConversation(player)) {
			conversationManager.getState(player);
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (conversationManager.isInConversation(player)) {
					final var view = player.getOpenInventory();
					if (!view.getTitle().equals(DialogueGui.DIALOGUE_TITLE)) {
						conversationManager.endConversation(player);
					}
				}
			});
		}
	}
	private void onQuestAccepted(Player player, Quest quest) {
		final QuestObjective.Type type = quest.getObjective().getType();

		if (type == TREASURE) {
			spawnTreasureChest(quest.getDestination());
			player.getInventory().addItem(
				createMapItem(quest.getDestination(), "Treasure Hunt"));
		} else if (type == FIND_NPC) {
			spawnHiddenVillager(quest.getDestination(), "Hidden NPC");
			player.getInventory().addItem(
				createMapItem(quest.getDestination(), "Find NPC"));
		}
	}
	/**
	 * Cleans up spawned entities and items associated with an abandoned or
	 * expired quest: treasure chests, hidden NPCs, quest maps.
	 */
	private void cleanupQuestEntities(Player player, Quest quest) {
		final QuestObjective.Type type = quest.getObjective().getType();
		final org.bukkit.Location dest = quest.getDestination();

		if (type == TREASURE && dest != null) {
			dest.getBlock().setType(Material.AIR);
		}

		if (type == FIND_NPC && dest != null) {
			dest.getWorld().getNearbyEntities(dest, 5, 5, 5).stream()
				.filter(e -> e instanceof Villager)
				.filter(e -> {
					final String name = e.getCustomName();
					return name != null && name.contains("Hidden NPC");
				})
				.forEach(Entity::remove);
		}

		// Remove quest maps from player inventory
		if (EnumSet.of(TREASURE, FIND_NPC).contains(type)) {
			removeQuestMaps(player, quest);
		}

		// Clear villager NPC data and indicator
		if (quest.getVillagerUuid() != null) {
			questManager.setVillagerData(quest.getVillagerUuid(), null);
			removeQuestIndicator(quest.getVillagerUuid());
		}
	}

	private void removeQuestMaps(Player player, Quest quest) {
		final Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getSize(); i++) {
			final ItemStack item = inv.getItem(i);
			if (item != null && item.getType() == Material.FILLED_MAP) {
				final var meta = item.getItemMeta();
				if (meta != null) {
					final var display = meta.displayName();
					if (display != null && display.toString().contains("Quest Map")) {
						inv.setItem(i, null);
					}
				}
			}
		}
	}
	private void loadVillagerNames() {
		final File configFile = new File(plugin.getDataFolder(), "config.yml");
		final FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
		final ConfigurationSection nameSection =
			cfg.getConfigurationSection("villagerUniqueNames");

		if (nameSection != null) {
			for (String key : nameSection.getKeys(false)) {
				final UUID villagerId = UUID.fromString(key);
				final String name = nameSection.getString(key);
				if (name != null) {
					villagerUniqueNames.put(villagerId, name);
				}
			}
			plugin.getLogger().info("[onEnable] Loaded " + villagerUniqueNames.size()
				+ " villager unique names from config.");
		}
	}

	private void assignUniqueNamesToAllVillagers() {
		for (World world : Bukkit.getWorlds()) {
			for (Villager villager : world.getEntitiesByClass(Villager.class)) {
				final UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					villager.setCustomName("§a" + villagerUniqueNames.get(villagerId));
					villager.setCustomNameVisible(true);
				} else {
					generateUniqueNameForVillager(villager);
				}
			}
		}
	}

	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) {
			return;
		}
		if (!villagerUniqueNames.containsKey(villager.getUniqueId())) {
			generateUniqueNameForVillager(villager);
		}
	}

	private void generateUniqueNameForVillager(Villager villager) {
		final Logger logger = plugin.getLogger();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String prompt = "Provide a unique and creative name for a Minecraft"
					+ " villager with UUID: " + villager.getUniqueId()
					+ ". This villager is a " + villager.getProfession().name()
					+ ". *Output only first name and surname*";
				final ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Villager";
				}
				final AtomicReference<String> uniqueName = new AtomicReference<>(response);

				villagerUniqueNamesLock.lock();
				try {
					if (villagerUniqueNames.containsValue(uniqueName.get())) {
						uniqueName.set(uniqueName.get() + "_"
							+ ThreadLocalRandom.current().nextInt(1000, 10000));
					}
					villagerUniqueNames.put(villager.getUniqueId(), uniqueName.get());
				} finally {
					villagerUniqueNamesLock.unlock();
				}

				Bukkit.getScheduler().runTask(plugin, () -> {
					villager.setCustomName("§a" + uniqueName.get());
					villager.setCustomNameVisible(true);
					saveVillagerUniqueNames();
				});
			} catch (Exception e) {
				logger.log(Level.SEVERE,
					"[generateUniqueNameForVillager] Failed to generate name.", e);
				Bukkit.getScheduler().runTask(plugin,
					() -> villager.setCustomName("§aVillager"));
			}
		});
	}

	private void saveVillagerUniqueNames() {
		final File configFile = new File(plugin.getDataFolder(), "config.yml");
		final FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
		final ConfigurationSection nameSection = cfg.createSection("villagerUniqueNames");

		villagerUniqueNamesLock.lock();
		try {
			for (Map.Entry<UUID, String> entry : villagerUniqueNames.entrySet()) {
				nameSection.set(entry.getKey().toString(), entry.getValue());
			}
		} finally {
			villagerUniqueNamesLock.unlock();
		}

		try {
			cfg.save(configFile);
		} catch (IOException e) {
			plugin.getLogger().log(Level.SEVERE,
				"[saveVillagerUniqueNames] Failed to save config.", e);
		}
	}
	private void rewardPlayer(Player player, Quest quest) {
		final String skill = quest.getRewardTarget();
		final int xp = quest.getRewardAmount();
		ExperienceAPI.addXP(player, skill, xp, "COMMAND");
		player.sendMessage("§aYou earned " + xp + " MCMMO XP in " + skill + "!");
	}
	private void removeQuestIndicator(UUID villagerId) {
		final UUID standId = questManager.getIndicator(villagerId);
		if (standId != null) {
			final Entity e = Bukkit.getEntity(standId);
			if (e instanceof ArmorStand) {
				e.remove();
			}
		}
		questManager.removeIndicator(villagerId);
	}
	private void spawnTreasureChest(org.bukkit.Location loc) {
		loc.getBlock().setType(Material.CHEST);
		if (loc.getBlock().getState() instanceof org.bukkit.block.Chest cstate) {
			final Inventory inv = cstate.getInventory();
			inv.addItem(new ItemStack(Material.DIAMOND, 3));
			inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
		}
	}

	private void spawnHiddenVillager(org.bukkit.Location loc, String questTitle) {
		loc.getWorld().spawn(loc, Villager.class, v -> {
			v.setPersistent(true);
			v.customName(Component.text("§6Hidden NPC: " + questTitle));
			v.setCustomNameVisible(true);
			v.setProfession(Villager.Profession.NITWIT);
		});
	}

	private ItemStack createMapItem(org.bukkit.Location dest, String title) {
		final ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
		final MapMeta meta = (MapMeta) mapItem.getItemMeta();
		final World world = dest.getWorld();
		world.getChunkAt(dest).load();

		final MapView mapView = Bukkit.createMap(world);
		mapView.addRenderer(new DestinationMarkerRenderer(dest));
		mapView.setScale(MapView.Scale.FAR);
		mapView.setTrackingPosition(true);
		mapView.setCenterX(dest.getBlockX());
		mapView.setCenterZ(dest.getBlockZ());

		meta.setMapView(mapView);
		meta.displayName(Component.text("§6Quest Map: " + title));
		mapItem.setItemMeta(meta);
		return mapItem;
	}
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		final LivingEntity entity = event.getEntity();
		final Player killer = entity.getKiller();
		if (killer == null) {
			return;
		}

		final String killedMob = entity.getType().name();
		final QuestProgress completed = questManager.incrementProgress(
			killer, KILL, killedMob, 1);

		if (completed != null) {
			final Quest quest = completed.getQuest();
			questManager.removeBossBars(killer, completed);
			questManager.completeQuest(killer, quest);
			questManager.setVillagerData(quest.getVillagerUuid(), null);
			removeQuestIndicator(quest.getVillagerUuid());
			killer.sendMessage("§6Quest Update: You've completed the objective!");
			rewardPlayer(killer, quest);
		} else {
			// Show progress message if there's a matching quest in progress
			final QuestProgress progress =
				questManager.findQuest(killer, KILL, killedMob);
			if (progress != null) {
				final QuestObjective obj = progress.getQuest().getObjective();
				killer.sendMessage("§eQuest Update: " + progress.getCurrent()
					+ "/" + obj.getAmount() + " " + killedMob + "(s) killed.");
			}
		}
	}
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		final Player player = event.getPlayer();
		final ItemStack item = event.getItem().getItemStack();
		final String pickedItem = item.getType().name();

		final QuestProgress completed = questManager.incrementProgress(
			player, COLLECT, pickedItem, item.getAmount());

		if (completed != null) {
			final Quest quest = completed.getQuest();
			questManager.removeBossBars(player, completed);
			questManager.completeQuest(player, quest);
			questManager.setVillagerData(quest.getVillagerUuid(), null);
			removeQuestIndicator(quest.getVillagerUuid());
			player.sendMessage("§6Quest Update: You've completed the objective!");
			rewardPlayer(player, quest);
		} else {
			final QuestProgress progress =
				questManager.findQuest(player, COLLECT, pickedItem);
			if (progress != null) {
				final QuestObjective obj = progress.getQuest().getObjective();
				player.sendMessage("§eQuest Update: " + progress.getCurrent()
					+ "/" + obj.getAmount() + " " + pickedItem + "(s) collected.");
			}
		}
	}
	@EventHandler
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	public void onPlayerInteractVillagerCompletion(PlayerInteractEntityEvent event) {
		final Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			return;
		}

		final Player player = event.getPlayer();
		final Npc npc = questManager.getVillagerData(villager.getUniqueId());
		if (npc == null || !npc.isQuest()) {
			return;
		}

		final Quest quest = npc.getQuest();
		if (!EnumSet.of(TREASURE, FIND_NPC).contains(quest.getObjective().getType())) {
			return;
		}
		if (quest.getDestination().distance(player.getLocation()) > 10) {
			return;
		}

		// Find matching quest in the player's active list
		final List<QuestProgress> activeQuests = questManager.getActiveQuests(player);
		final QuestProgress matching = activeQuests.stream()
			.filter(p -> p.getQuest().getVillagerUuid() != null
				&& p.getQuest().getVillagerUuid().equals(quest.getVillagerUuid()))
			.findFirst().orElse(null);

		if (matching == null) {
			return;
		}

		questManager.removeBossBars(player, matching);
		if (!questManager.completeQuest(player, matching.getQuest())) {
			return;
		}

		player.sendMessage("§aYou have completed the quest: " + quest.getTitle());
		rewardPlayer(player, quest);
		questManager.setVillagerData(villager.getUniqueId(), null);
		removeQuestIndicator(villager.getUniqueId());

		if (quest.getObjective().getType() == TREASURE) {
			quest.getDestination().getBlock().setType(Material.AIR);
		}

		player.playSound(
			player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
	}
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		final Chunk chunk = event.getChunk();
		removeAllQuestIndicators(chunk);

		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof Villager villager) {
				final UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					villager.setCustomName("§a" + villagerUniqueNames.get(villagerId));
					villager.setCustomNameVisible(true);
				} else {
					generateUniqueNameForVillager(villager);
				}
			}
		}
	}

	public void removeAllQuestIndicators(Chunk chunk) {
		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof ArmorStand armorStand) {
				final String customName = armorStand.getCustomName();
				if (customName != null && customName.contains("Quest")) {
					armorStand.setPersistent(false);
					armorStand.remove();
				}
			}
		}
	}
}
