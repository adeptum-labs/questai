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
import com.adeptum.questai.quest.PlacedEntityStore;
import com.adeptum.questai.quest.QuestManager;
import com.adeptum.questai.quest.QuestProgress;
import com.adeptum.questai.service.QuestGenerationService;
import com.adeptum.questai.utility.AiChat;
import com.adeptum.questai.utility.EnumUtil;
import com.adeptum.questai.villager.AmbientGreetingTask;
import com.adeptum.questai.villager.MemoryEvent;
import com.adeptum.questai.villager.VillagerPersona;
import com.adeptum.questai.villager.VillagerProfileStore;
import com.gmail.nossr50.api.ExperienceAPI;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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
	private final VillagerProfileStore profileStore;

	private QuestManager questManager;
	private PlacedEntityStore placedEntityStore;
	private BukkitTask ambientGreetingTask;

	public RandomQuestPlugin(JavaPlugin plugin, ConversationManager conversationManager,
		QuestGenerationService questService, OpenAiChatModel chatModel,
		VillagerProfileStore profileStore) {

		super();
		this.plugin = plugin;
		this.conversationManager = conversationManager;
		this.questService = questService;
		this.chatModel = chatModel;
		this.profileStore = profileStore;
	}
	@Override
	public void onEnable() {
		final Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onEnable() start");

		this.placedEntityStore = new PlacedEntityStore(plugin);
		final int swept = placedEntityStore.sweepOrphans();
		if (swept > 0) {
			logger.info("[RandomQuestPlugin] Swept " + swept
				+ " orphaned quest block(s)/entity(ies) from previous session.");
		}

		this.questManager = new QuestManager(plugin);
		questManager.setQuestCleanup(this::cleanupQuestEntities);
		conversationManager.setQuestManager(questManager);
		conversationManager.setQuestAcceptHandler(this::onQuestAccepted);

		assignUniqueNamesToAllVillagers();
		this.ambientGreetingTask = Bukkit.getScheduler().runTaskTimer(plugin,
			new AmbientGreetingTask(profileStore, conversationManager), 100L, 100L);
		logger.info("[RandomQuestPlugin] onEnable() end -> plugin fully enabled.");
	}

	@Override
	public void onDisable() {
		ambientGreetingTask.cancel();
		questManager.cleanupAllQuests();
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
				return obj.getType().isCountable()
					&& p.getCurrent() >= obj.getAmount();
			})
			.toList();

		for (final QuestProgress progress : completed) {
			completeAndReward(player, progress, "\u00a7aCompleted quest: "
				+ progress.getQuest().getShortTitle());
		}
	}

	/**
	 * Runs the shared quest-completion sequence: boss bars, quest removal,
	 * villager data, indicator, completion message and reward.
	 *
	 * @return true if the quest was still active and is now completed
	 */
	private boolean completeAndReward(Player player, QuestProgress progress,
		String message) {

		final Quest quest = progress.getQuest();
		questManager.removeBossBars(player, progress);
		if (!questManager.completeQuest(player, quest)) {
			return false;
		}
		questManager.setVillagerData(quest.getVillagerUuid(), null);
		removeQuestIndicator(quest.getVillagerUuid());

		String completionMessage = message;
		if (quest.getVillagerUuid() != null) {
			profileStore.recordEvent(quest.getVillagerUuid(), player.getUniqueId(),
				MemoryEvent.Type.QUEST_COMPLETED, quest.getShortTitle());
			final String giverName = profileStore.getName(quest.getVillagerUuid());
			if (giverName != null) {
				completionMessage = message + " §7— §a" + giverName
					+ "§7 is grateful.";
			}
		}
		player.sendMessage(completionMessage);
		rewardPlayer(player, quest);
		return true;
	}

	/**
	 * Abandons all active quests when the player disconnects so that any
	 * placed treasure chests or hidden NPCs are cleaned up rather than
	 * leaking into the world until the 6-hour timeout fires.
	 */
	@EventHandler
	public void onPlayerQuit(final PlayerQuitEvent event) {
		questManager.abandonAllQuests(event.getPlayer());
	}

	@EventHandler
	public void onVillagerClick(PlayerInteractEntityEvent event) {
		final Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			return;
		}

		final String uniqueName = profileStore.getName(villager.getUniqueId());
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
		if (!DialogueGui.isDialogueInventory(event.getView())) {
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
		if (!DialogueGui.isDialogueInventory(event.getView())) {
			return;
		}

		final Player player = (Player) event.getPlayer();
		if (conversationManager.isInConversation(player)) {
			Bukkit.getScheduler().runTask(plugin, () -> {
				if (conversationManager.isInConversation(player)) {
					final var view = player.getOpenInventory();
					if (!DialogueGui.isDialogueInventory(view)) {
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
			placedEntityStore.forget(PlacedEntityStore.Kind.CHEST, dest);
		}

		if (type == FIND_NPC && dest != null) {
			PlacedEntityStore.removeHiddenNpcsNear(dest);
			placedEntityStore.forget(PlacedEntityStore.Kind.HIDDEN_NPC, dest);
		}

		// Remove quest maps from player inventory
		if (type.needsDestination()) {
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
	private void assignUniqueNamesToAllVillagers() {
		for (World world : Bukkit.getWorlds()) {
			world.getEntitiesByClass(Villager.class).forEach(this::applyVillagerName);
		}
	}

	/**
	 * Applies the stored unique name to a villager, or generates one if the
	 * villager has not been named yet.
	 */
	private void applyVillagerName(Villager villager) {
		final String name = profileStore.getName(villager.getUniqueId());
		if (name == null) {
			generateUniqueNameForVillager(villager);
		} else {
			villager.setCustomName("§a" + name);
			villager.setCustomNameVisible(true);
		}
	}

	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) {
			return;
		}
		if (!profileStore.hasProfile(villager.getUniqueId())) {
			generateUniqueNameForVillager(villager);
		}
	}

	private void generateUniqueNameForVillager(Villager villager) {
		final Logger logger = plugin.getLogger();
		final String profession = villager.getProfession().name();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final VillagerPersona persona = VillagerPersona.parse(
					AiChat.ask(chatModel,
						VillagerPersona.prompt(villager.getUniqueId(), profession),
						"Villager"));
				final String uniqueName = profileStore.register(
					villager.getUniqueId(), persona, profession);

				Bukkit.getScheduler().runTask(plugin, () -> {
					villager.setCustomName("§a" + uniqueName);
					villager.setCustomNameVisible(true);
				});
			} catch (Exception e) {
				logger.log(Level.SEVERE,
					"[generateUniqueNameForVillager] Failed to generate name.", e);
				Bukkit.getScheduler().runTask(plugin,
					() -> villager.setCustomName("§aVillager"));
			}
		});
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
			fillTreasureLoot(inv);
		}
		placedEntityStore.record(PlacedEntityStore.Kind.CHEST, loc);
	}

	private void fillTreasureLoot(final Inventory inv) {
		final var rng = ThreadLocalRandom.current();
		final ItemStack[] common = {
			new ItemStack(Material.IRON_INGOT, 4 + rng.nextInt(5)),
			new ItemStack(Material.BREAD, 4 + rng.nextInt(4)),
			new ItemStack(Material.ARROW, 8 + rng.nextInt(9)),
			new ItemStack(Material.GOLDEN_CARROT, 2 + rng.nextInt(3)),
			new ItemStack(Material.EMERALD, 1 + rng.nextInt(3)),
		};
		final ItemStack[] uncommon = {
			new ItemStack(Material.DIAMOND, 1 + rng.nextInt(3)),
			new ItemStack(Material.GOLDEN_APPLE, 1),
			new ItemStack(Material.ENCHANTED_BOOK, 1),
			new ItemStack(Material.EXPERIENCE_BOTTLE, 2 + rng.nextInt(4)),
			new ItemStack(Material.NAME_TAG, 1),
		};
		final ItemStack[] rare = {
			new ItemStack(Material.NETHERITE_SCRAP, 1),
			new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
			new ItemStack(Material.TOTEM_OF_UNDYING, 1),
			new ItemStack(Material.NETHER_STAR, 1),
		};

		// Always give 2-3 common drops, 1-2 uncommon, and a 15% rare
		for (int i = 0; i < 2 + rng.nextInt(2); i++) {
			inv.addItem(EnumUtil.random(common).clone());
		}
		for (int i = 0; i < 1 + rng.nextInt(2); i++) {
			inv.addItem(EnumUtil.random(uncommon).clone());
		}
		if (rng.nextDouble() < 0.15) {
			inv.addItem(EnumUtil.random(rare).clone());
		}
	}

	private void spawnHiddenVillager(org.bukkit.Location loc, String questTitle) {
		loc.getWorld().spawn(loc, Villager.class, v -> {
			v.setPersistent(true);
			v.customName(Component.text("§6Hidden NPC: " + questTitle));
			v.setCustomNameVisible(true);
			v.setProfession(Villager.Profession.NITWIT);
		});
		placedEntityStore.record(PlacedEntityStore.Kind.HIDDEN_NPC, loc);
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
		handleCountProgress(killer, KILL, entity.getType().name(), 1, "killed");
	}
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		final ItemStack item = event.getItem().getItemStack();
		handleCountProgress(event.getPlayer(), COLLECT,
			item.getType().name(), item.getAmount(), "collected");
	}

	/**
	 * Applies count-based quest progress and either completes the quest
	 * or shows the current progress to the player.
	 */
	private void handleCountProgress(Player player, QuestObjective.Type type,
		String target, int amount, String verb) {

		final QuestProgress completed = questManager.incrementProgress(
			player, type, target, amount);

		if (completed != null) {
			completeAndReward(player, completed,
				"§6Quest Update: You've completed the objective!");
			return;
		}

		final QuestProgress progress = questManager.findQuest(player, type, target);
		if (progress != null) {
			final QuestObjective obj = progress.getQuest().getObjective();
			player.sendMessage("§eQuest Update: " + progress.getCurrent()
				+ "/" + obj.getAmount() + " " + target + "(s) " + verb + ".");
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
		if (!quest.getObjective().getType().needsDestination()) {
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

		if (matching == null || !completeAndReward(player, matching,
			"§aYou have completed the quest: " + quest.getTitle())) {
			return;
		}

		if (quest.getObjective().getType() == TREASURE) {
			quest.getDestination().getBlock().setType(Material.AIR);
			placedEntityStore.forget(PlacedEntityStore.Kind.CHEST,
				quest.getDestination());
		}

		player.playSound(
			player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
	}
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		for (Entity entity : event.getChunk().getEntities()) {
			if (entity instanceof ArmorStand armorStand) {
				final String customName = armorStand.getCustomName();
				if (customName != null && customName.contains("Quest")) {
					armorStand.setPersistent(false);
					armorStand.remove();
				}
			} else if (entity instanceof Villager villager) {
				applyVillagerName(villager);
			}
		}
	}
}
