package com.adeptum.questai;

import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.utility.EnumUtil;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.COLLECT;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.FIND_NPC;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.KILL;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.TREASURE;
import com.gmail.nossr50.api.ExperienceAPI;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Plugin that creates generated villager quests and short villager dialogue.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class RandomQuestPlugin implements SubPlugin {

	// ------------------- Constants -------------------
	private static final int INVENTORY_SIZE = 9; // Single-row inventory
	private static final String INVENTORY_TITLE = "§6Quest Offered";
	private static final int ACCEPT_BUTTON_SLOT = 4;
	private static final int REJECT_BUTTON_SLOT = 5;

	private static final double QUEST_CHANCE = 0.3;

	private OpenAiChatModel chatModel;
	private QuestManager questManager;

	// Temporary mapping of player UUID to pending quest
	private final Map<UUID, Quest> pendingQuests = Collections.synchronizedMap(new HashMap<>());

	// Cache for villager unique names (loaded from config)
	private final Map<UUID, String> villagerUniqueNames = Collections.synchronizedMap(new HashMap<>());
	private final ReentrantLock villagerUniqueNamesLock = new ReentrantLock();

	private final JavaPlugin plugin;

	public RandomQuestPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	@SuppressWarnings("PMD.DataClass")
	public static class QuestProgress {
		private Quest quest;
		private int current;
		private long startTime; // Timestamp when the quest was accepted
		private BossBar objectiveBossBar; // BossBar for quest progression
		private BossBar timerBossBar;     // BossBar for quest timer
		private double maxDistance;       // Maximum distance for "FIND_NPC" or "TREASURE" quests

		public QuestProgress(Quest quest) {
			this.quest = quest;
			this.current = 0;
			this.startTime = System.currentTimeMillis();
		}

		// Getters and Setters
		public Quest getQuest() {
			return quest;
		}

		public void setQuest(Quest quest) {
			this.quest = quest;
		}

		public int getCurrent() {
			return current;
		}

		public void setCurrent(int current) {
			this.current = current;
		}

		public long getStartTime() {
			return startTime;
		}

		public void setStartTime(long startTime) {
			this.startTime = startTime;
		}

		public BossBar getObjectiveBossBar() {
			return objectiveBossBar;
		}

		public void setObjectiveBossBar(BossBar objectiveBossBar) {
			this.objectiveBossBar = objectiveBossBar;
		}

		public BossBar getTimerBossBar() {
			return timerBossBar;
		}

		public void setTimerBossBar(BossBar timerBossBar) {
			this.timerBossBar = timerBossBar;
		}

		public double getMaxDistance() {
			return maxDistance;
		}

		public void setMaxDistance(double maxDistance) {
			this.maxDistance = maxDistance;
		}
	}

	public class QuestManager {
		private final Map<UUID, QuestProgress> playerQuests = new HashMap<>();
		// Modified to store VillagerData instead of only Quest
		private final Map<UUID, Npc> npcs = new HashMap<>();
		private final Map<UUID, UUID> villagerIndicators = new HashMap<>();
		private final Map<UUID, String> villagerPersonality = new HashMap<>();
		private final Map<UUID, BukkitTask> questTasks = new HashMap<>(); // Tracks scheduled tasks per player

		/**
		 * Assigns a quest to a player and initializes BossBars.
		 *
		 * @param player The player to assign the quest to.
		 * @param quest The quest to assign.
		 * @param logger The logger for logging.
		 */
		public void assignQuest(Player player, Quest quest, Logger logger) {
			logger.info("[QuestManager] assignQuest() -> Assigning quest to player: " + player.getName());
			QuestProgress progress = new QuestProgress(quest);

			// Initialize maxDistance for "FIND_NPC" or "TREASURE" quests
			if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
				final Location playerLocation = player.getLocation();
				final Location destination = quest.getDestination();
				final double distance = playerLocation.distance(destination);

				progress.setMaxDistance(distance);
			}

			// Create BossBars
			BossBar objectiveBar = Bukkit.createBossBar("Objective Progress", BarColor.BLUE, BarStyle.SOLID);
			progress.setObjectiveBossBar(objectiveBar);
			objectiveBar.addPlayer(player);

			BossBar timerBar = Bukkit.createBossBar("Time Remaining", BarColor.RED, BarStyle.SOLID);
			progress.setTimerBossBar(timerBar);
			timerBar.addPlayer(player);

			playerQuests.put(player.getUniqueId(), progress);

			// Schedule a repeating task to update the BossBars every second
			BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
				updateQuestProgress(player, progress, logger);
			}, 20L, 20L); // 20 ticks = 1 second

			questTasks.put(player.getUniqueId(), task);
		}

		/**
		 * Updates the quest progress based on time and objectives.
		 *
		 * @param player The player whose quest is being updated.
		 * @param progress The current progress of the quest.
		 * @param logger The logger for logging.
		 */
		private void updateQuestProgress(Player player, QuestProgress progress, Logger logger) {
			Quest quest = progress.getQuest();
			long elapsedTime = System.currentTimeMillis() - progress.getStartTime();
			long remainingTime = 6 * 60 * 60 * 1000 - elapsedTime; // 6 hours in milliseconds

			if (remainingTime <= 0) {
				// Quest timer expired
				Bukkit.getScheduler().runTask(plugin, () -> {
					player.sendMessage("§cYour quest '" + quest.getShortTitle() + "' has expired.");
					completeQuest(player, logger);
					removeBossBars(player, progress);
					removeQuestBook(player);
				});
				cancelQuestTask(player);
				return;
			}

			// Update Timer BossBar
			final double timerProgress = Math.max(remainingTime / (double) (6 * 60 * 60 * 1000), 0);
			Bukkit.getScheduler().runTask(plugin, () -> {
				progress.getTimerBossBar().setProgress(timerProgress);
				progress.getTimerBossBar().setTitle("Time Remaining: " + formatTime(remainingTime));
			});

			// Update Objective
			if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
				Location dest = quest.getDestination();
				Location playerLoc = player.getLocation();
				double distance = playerLoc.distance(dest);
				final double progressPercent = Math.min(
					Math.max(1.0 - (distance / progress.getMaxDistance()), 0.0), 1.0
				);
				Bukkit.getScheduler().runTask(plugin, () -> {
					progress.getObjectiveBossBar().setProgress(progressPercent);
					progress.getObjectiveBossBar().setTitle(
						"Distance to Destination: " + String.format("%.2f", distance) + " blocks"
					);
				});

				// Check if player has reached the destination
				if (distance <= 10) { // Threshold distance
					Bukkit.getScheduler().runTask(plugin, () -> {
						player.sendMessage("§aYou have reached the destination for quest '"
							+ quest.getShortTitle() + "'.");
						completeQuest(player, logger);
						removeBossBars(player, progress);
						removeQuestBook(player);
					});
					cancelQuestTask(player);
				}
			} else if (EnumSet.of(KILL, COLLECT).contains(quest.getObjective().getType())) {
				int current = progress.getCurrent();
				int required = quest.getObjective().getAmount();
				final double progressPercent = Math.min(
					Math.max((double) current / required, 0.0), 1.0
				);
				Bukkit.getScheduler().runTask(plugin, () -> {
					progress.getObjectiveBossBar().setProgress(progressPercent);
					progress.getObjectiveBossBar().setTitle(
						"Objective Progress: " + current + "/" + required
					);
				});

				// Check if objective is completed
				if (current >= required) {
					Bukkit.getScheduler().runTask(plugin, () -> {
						player.sendMessage("§aYou have completed the objective for quest '"
							+ quest.getShortTitle() + "'.");
						completeQuest(player, logger);
						removeBossBars(player, progress);
						removeQuestBook(player);
					});
					cancelQuestTask(player);
				}
			}
		}

		/**
		 * Formats milliseconds into a readable time string.
		 *
		 * @param millis The time in milliseconds.
		 * @return Formatted time string.
		 */
		private String formatTime(long millis) {
			long seconds = millis / 1000;
			long hours = seconds / 3600;
			long minutes = seconds % 3600 / 60;
			long secs = seconds % 60;
			return String.format("%02dh %02dm %02ds", hours, minutes, secs);
		}

		/**
		 * Removes BossBars from the player.
		 *
		 * @param player The player whose BossBars are to be removed.
		 * @param progress The current progress of the quest.
		 */
		public void removeBossBars(Player player, QuestProgress progress) {
			if (progress.getObjectiveBossBar() != null) {
				progress.getObjectiveBossBar().removePlayer(player);
			}
			if (progress.getTimerBossBar() != null) {
				progress.getTimerBossBar().removePlayer(player);
			}
		}

		/**
		 * Cancels the scheduled task for a player.
		 *
		 * @param player The player whose task is to be canceled.
		 */
		public void cancelQuestTask(Player player) {
			UUID playerId = player.getUniqueId();
			BukkitTask task = questTasks.get(playerId);
			if (task != null) {
				task.cancel();
				questTasks.remove(playerId);
			}
		}

		/**
		 * Completes a quest for a player by removing their quest progress.
		 *
		 * @param player The player whose quest is to be completed.
		 * @param logger The logger for logging.
		 */
		public void completeQuest(Player player, Logger logger) {
			logger.info("[QuestManager] completeQuest() -> Removing quest for player: " + player.getName());
			playerQuests.remove(player.getUniqueId());
			cancelQuestTask(player);
		}

		/**
		 * Retrieves the current quest progress for a player.
		 *
		 * @param player The player whose quest progress is to be retrieved.
		 * @return The QuestProgress object or null if none exists.
		 */
		public QuestProgress getQuestProgress(Player player, Logger logger) {
			return playerQuests.get(player.getUniqueId());
		}

		/**
		 * Increments the quest progress for a player.
		 *
		 * @param player The player whose progress is to be incremented.
		 * @param amount The amount to increment.
		 * @param logger The logger for logging.
		 * @return True if the quest is completed after incrementing, false otherwise.
		 */
		public boolean incrementProgress(Player player, int amount, Logger logger) {
			QuestProgress progress = playerQuests.get(player.getUniqueId());
			if (progress == null) {
				logger.warning("[QuestManager] Player " + player.getName() + " has no active quest.");
				return false;
			}

			Quest quest = progress.getQuest();
			QuestObjective objective = quest.getObjective();

			if (objective.getType() == QuestObjective.Type.KILL
				|| objective.getType() == QuestObjective.Type.COLLECT) {
				progress.setCurrent(progress.getCurrent() + amount);
				logger.info("[QuestManager] Player " + player.getName()
					+ " progress updated: " + progress.getCurrent() + "/" + objective.getAmount());
				return progress.getCurrent() >= objective.getAmount();
			}

			return false;
		}

		// ------------------- Villager Quest Management -------------------
		public Npc getVillagerData(UUID villagerId, Logger logger) {
			return npcs.get(villagerId);
		}

		public void setVillagerData(UUID villagerId, Npc npc, Logger logger) {
			if (npc == null) {
				npcs.remove(villagerId);
				removeQuestIndicator(villagerId, logger);
				return;
			}
			npcs.put(villagerId, npc);
		}

		/**
		 * Retrieves the personality of a villager.
		 *
		 * @param villagerId The UUID of the villager.
		 * @param logger The logger for logging.
		 * @return The personality string or null if none exists.
		 */
		public String getVillagerPersonality(UUID villagerId, Logger logger) {
			return villagerPersonality.get(villagerId);
		}

		/**
		 * Sets the personality of a villager.
		 *
		 * @param villagerId The UUID of the villager.
		 * @param personality The personality string to set.
		 * @param logger The logger for logging.
		 */
		public void setVillagerPersonality(UUID villagerId, String personality, Logger logger) {
			villagerPersonality.put(villagerId, personality);
		}

		/**
		 * Retrieves the indicator ArmorStand UUID for a villager.
		 *
		 * @param villagerId The UUID of the villager.
		 * @return The UUID of the ArmorStand or null if none exists.
		 */
		public UUID getIndicator(UUID villagerId) {
			return villagerIndicators.get(villagerId);
		}

		/**
		 * Sets the indicator ArmorStand UUID for a villager.
		 *
		 * @param villagerId The UUID of the villager.
		 * @param armorStandId The UUID of the ArmorStand.
		 * @param logger The logger for logging.
		 */
		public void setIndicator(UUID villagerId, UUID armorStandId, Logger logger) {
			villagerIndicators.put(villagerId, armorStandId);
		}

		/**
		 * Removes the indicator ArmorStand UUID for a villager.
		 *
		 * @param villagerId The UUID of the villager.
		 * @param logger The logger for logging.
		 */
		public void removeIndicator(UUID villagerId, Logger logger) {
			villagerIndicators.remove(villagerId);
		}

		// ------------------- Cleanup Method -------------------
		/**
		 * Cleans up all active quests, BossBars, and scheduled tasks when the plugin is disabled.
		 *
		 * @param logger The logger for logging.
		 */
		public void cleanupAllQuests(Logger logger) {
			for (Map.Entry<UUID, QuestProgress> entry : playerQuests.entrySet()) {
				Player player = Bukkit.getPlayer(entry.getKey());
				if (player != null) {
					removeBossBars(player, entry.getValue());
					cancelQuestTask(player);
					removeQuestBook(player);
				}
			}
			playerQuests.clear();
			questTasks.clear();
			npcs.clear(); // Clear all VillagerData
		}
	}

	// ------------------- onEnable and onDisable -------------------
	@Override
	public void onEnable() {
		Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onEnable() start");

		// Register the event listener
		PluginManager pm = plugin.getServer().getPluginManager();
		pm.registerEvents(this, plugin);

		// Load configuration
		plugin.saveDefaultConfig();
		FileConfiguration config = plugin.getConfig();

		// Load villagerUniqueNames from config
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
		ConfigurationSection nameSection = cfg.getConfigurationSection("villagerUniqueNames");

		if (nameSection != null) {
			for (String key : nameSection.getKeys(false)) {
				UUID villagerId = UUID.fromString(key);
				String name = nameSection.getString(key);
				if (name != null) {
					villagerUniqueNames.put(villagerId, name);
				}
			}
			logger.info("[onEnable] Loaded " + villagerUniqueNames.size()
				+ " villager unique names from config.");
		}

		// Initialize ChatGPT model
		String apiKey = config.getString("openai.api-key");
		if (apiKey == null || apiKey.isEmpty()) {
			logger.severe("OpenAI API key is not set in config.yml!");
			Bukkit.getPluginManager().disablePlugin(plugin);
			return;
		}

		this.chatModel = OpenAiChatModel.builder()
			.apiKey(apiKey) // Use the API key from config
			.modelName(OpenAiChatModelName.GPT_4_O_MINI) // Use GPT_4 or the desired model
			.build();

		// Initialize QuestManager
		this.questManager = new QuestManager();
		logger.info("[RandomQuestPlugin] QuestManager initialized.");
		// Assign unique names to all existing villagers
		assignUniqueNamesToAllVillagers();
		logger.info("[RandomQuestPlugin] onEnable() end -> plugin fully enabled.");
	}

	@Override
	public void onDisable() {
		Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onDisable() start");

		// Cleanup all quests
		questManager.cleanupAllQuests(logger);

		// Save villagerUniqueNames to config
		saveVillagerUniqueNames();

		logger.info("[RandomQuestPlugin] onDisable() end -> plugin disabled.");
	}

	// ------------------- Villager Interaction -------------------
	@EventHandler
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	public void onVillagerClick(PlayerInteractEntityEvent event) {
		Logger logger = plugin.getLogger();
		logger.info("[onVillagerClick] Event triggered.");

		Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			logger.info("[onVillagerClick] Clicked entity is not a villager, ignoring.");
			return;
		}

		event.setCancelled(true);
		Player player = event.getPlayer();
		logger.info("[onVillagerClick] Player " + player.getName() + " clicked villager " + villager.getUniqueId());

		// Get the unique name
		String uniqueName = villagerUniqueNames.get(villager.getUniqueId());
		if (uniqueName == null) {
			logger.warning("[onVillagerClick] Villager " + villager.getUniqueId()
				+ " does not have a unique name.");
			return; // Villager should already have a unique name on startup
		}

		// Retrieve VillagerData
		Npc npc = questManager.getVillagerData(villager.getUniqueId(), logger);
		long currentTime = System.currentTimeMillis();
		long twoHoursMillis = 2 * 60 * 60 * 1000; // 2 hours in milliseconds

		if (npc != null) {
			if (currentTime - npc.getTimestamp() <= twoHoursMillis) {
				// Data is still valid
				if (npc.isQuest()) {
					logger.info("[onVillagerClick] Villager has an active quest. Opening GUI.");
					openQuestDialogue(player, npc.getQuest());
				} else if (npc.isPhrase()) {
					logger.info("[onVillagerClick] Sending stored nonsense phrase to player.");
					player.sendMessage("§e" + uniqueName + ": \"" + npc.getNonsensePhrase() + "\"");
				}
				return;
			} else {
				// Data expired
				logger.info("[onVillagerClick] Villager's data expired. Removing old data.");
				questManager.setVillagerData(villager.getUniqueId(), null, logger);
			}
		}

		// If no valid data, decide randomly to offer a quest or generate a nonsense phrase
		double randomVal = Math.random();
		logger.info("[onVillagerClick] randomVal=" + randomVal + " questChance=" + QUEST_CHANCE);
		if (randomVal <= QUEST_CHANCE) {
			logger.info("[onVillagerClick] Decided to generate a new quest for the player.");
			generateQuest(player, villager);
		} else {
			logger.info("[onVillagerClick] Decided to generate a nonsense line instead of a quest.");
			generateNonsenseLine(player, villager, uniqueName);
		}
	}

	// ------------------- VillagerData Management -------------------
	/**
	 * Assigns unique names to all existing villagers in all loaded worlds.
	 */
	private void assignUniqueNamesToAllVillagers() {
		Logger logger = plugin.getLogger();
		logger.info("[assignUniqueNamesToAllVillagers] Assigning unique names to all existing villagers.");

		for (World world : Bukkit.getWorlds()) {
			for (Villager villager : world.getEntitiesByClass(Villager.class)) {
				UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					String uniqueName = villagerUniqueNames.get(villagerId);
					villager.setCustomName("§a" + uniqueName);
					villager.setCustomNameVisible(true);
					logger.info("[assignUniqueNamesToAllVillagers] Assigned existing name '"
						+ uniqueName + "' to villager " + villagerId);
				} else {
					generateUniqueNameForVillager(villager);
				}
			}
		}

		logger.info("[assignUniqueNamesToAllVillagers] Completed assigning unique names.");
	}

	// ------------------- Handle New Villager Spawns -------------------
	/**
	 * Assigns a unique name to newly spawned villagers.
	 *
	 * @param event The CreatureSpawnEvent.
	 */
	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) {
			return; // Not a villager
		}

		UUID villagerId = villager.getUniqueId();
		if (!villagerUniqueNames.containsKey(villagerId)) {
			Logger logger = plugin.getLogger();
			logger.info("[onCreatureSpawn] Assigning unique name to newly spawned villager " + villagerId);
			generateUniqueNameForVillager(villager);
		}
	}

	/**
	 * Generates a unique name for a villager using ChatGPT and assigns it.
	 *
	 * @param villager The villager to name.
	 */
	private void generateUniqueNameForVillager(Villager villager) {
		Logger logger = plugin.getLogger();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			logger.info("[generateUniqueNameForVillager - Async] Generating unique name for villager "
				+ villager.getUniqueId());
			try {
				String prompt = "Provide a unique and creative name for a Minecraft villager with UUID: "
					+ villager.getUniqueId() + ". This villager is a " + villager.getProfession().name()
					+ ". *Output only first name and surname*";
				ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Villager";
				}
				AtomicReference<String> uniqueName = new AtomicReference<>(response);

				// Ensure the name is unique across all villagers
				villagerUniqueNamesLock.lock();
				try {
					if (villagerUniqueNames.containsValue(uniqueName.get())) {
						// If name already exists, append a number or regenerate
						uniqueName.set(uniqueName.get() + "_"
							+ ThreadLocalRandom.current().nextInt(1000, 10000));
					}
					villagerUniqueNames.put(villager.getUniqueId(), uniqueName.get());
				} finally {
					villagerUniqueNamesLock.unlock();
				}

				// Assign the unique name to the villager
				Bukkit.getScheduler().runTask(plugin, () -> {
					villager.setCustomName("§a" + uniqueName.get());
					villager.setCustomNameVisible(true);
					logger.info("[generateUniqueNameForVillager - SyncCallback] Set name for villager "
						+ villager.getUniqueId() + " to " + uniqueName.get());
					// Save the updated names to config
					saveVillagerUniqueNames();
				});
			} catch (Exception e) {
				logger.log(
					Level.SEVERE, "[generateUniqueNameForVillager] Failed to generate villager name.", e
				);
				Bukkit.getScheduler().runTask(plugin, ()
					-> villager.setCustomName("§a")
				);
			}
		});
	}

	// ------------------- Save Villager Unique Names to Config -------------------
	/**
	 * Saves the villagerUniqueNames map to config.yml.
	 */
	private void saveVillagerUniqueNames() {
		Logger logger = plugin.getLogger();
		logger.info("[saveVillagerUniqueNames] Saving villager unique names to config.");
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		FileConfiguration cfg = YamlConfiguration.loadConfiguration(configFile);
		ConfigurationSection nameSection = cfg.createSection("villagerUniqueNames");

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
			logger.info("[saveVillagerUniqueNames] Successfully saved villager unique names to config.");
		} catch (IOException e) {
			logger.severe("[saveVillagerUniqueNames] Failed to save villager unique names to config.");
			logger.log(Level.SEVERE, "[saveVillagerUniqueNames] Failed to save config.", e);
		}
	}

	// ------------------- Generate Quest (Async) -------------------
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	private void generateQuest(Player player, Villager villager) {
		Logger logger = plugin.getLogger();
		logger.info("[generateQuest] Start for player " + player.getName() + ", villager " + villager.getUniqueId());

		logger.info("[generateQuest] Determining random quest data (objective, reward).");

		QuestObjective objective = new QuestObjective();
		objective.setType(EnumUtil.random(QuestObjective.Type.class));

		if (objective.getType() == KILL) {
			String[] mobs = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER"};
			objective.setTarget(mobs[ThreadLocalRandom.current().nextInt(mobs.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else if (objective.getType() == COLLECT) {
			String[] items = {"IRON_INGOT", "WHEAT", "CARROT", "BONE"};
			objective.setTarget(items[ThreadLocalRandom.current().nextInt(items.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else {
			objective.setTarget("NONE");
			objective.setAmount(1);
		}

		// Reward
		String rewardType = "MCMMO";
		String[] possibleSkills = {"MINING", "WOODCUTTING", "EXCAVATION", "FISHING"};
		String rewardSkill = possibleSkills[ThreadLocalRandom.current().nextInt(possibleSkills.length)];
		int rewardXP = ThreadLocalRandom.current().nextInt(50, 201);

		// Create the Quest object
		Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType(rewardType);
		quest.setRewardTarget(rewardSkill);
		quest.setRewardAmount(rewardXP);
		quest.setVillagerUuid(villager.getUniqueId());

		// If category is TREASURE or FIND_NPC, assign a random location
		if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
			Location loc = getLogarithmicLocation(villager.getWorld(), logger);
			quest.setDestination(loc);
		}

		logger.info("[generateQuest] Chosen quest data -> objective="
			+ objective.getType() + " " + objective.getTarget());

		// 2. Prompt ChatGPT in async to get both shortTitle and description
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			logger.info("[generateQuest - Async] Building ChatGPT prompt & calling model...");
			try {
				String prompt = quest.prompt();
				ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Quest Title\nA mysterious quest awaits...";
				}

				// 3. Parse the AI response to extract shortTitle and description
				String[] parts = response.split("\n", 2);
				String shortTitle = parts.length > 0 ? parts[0].trim() : "Quest";
				String description = parts.length > 1 ? parts[1].trim() : "A mysterious quest awaits...";

				// 4. Set both shortTitle and title in the Quest object
				quest.setShortTitle(shortTitle);
				quest.setTitle(description);
				logger.info("[generateQuest - Async] Received quest short title: " + shortTitle);
				logger.info("[generateQuest - Async] Received quest description from AI: " + description);

				// 5. Schedule a synchronous task to update the game state
				Bukkit.getScheduler().runTask(plugin, () -> {
					final Npc npc = Npc.builder()
						.quest(quest)
						.timestamp(System.currentTimeMillis())
						.build();

					logger.info("[generateQuest - SyncCallback] Storing quest & opening GUI for player "
						+ player.getName());
					questManager.setVillagerData(villager.getUniqueId(), npc, logger);

					// Open the dialogue GUI to let player accept or reject
					openQuestDialogue(player, quest);

					// If TREASURE or FIND_NPC, spawn chest or NPC and give map
					if (objective.getType() == TREASURE) {
						spawnTreasureChest(quest.getDestination(), logger);
						ItemStack mapItem = createMapItem(
							quest.getDestination(), "Treasure Hunt", logger
						);
						player.getInventory().addItem(mapItem);
						logger.info("[generateQuest] Given Treasure Hunt map to player "
							+ player.getName());
					} else if (objective.getType() == FIND_NPC) {
						spawnHiddenVillager(quest.getDestination(), "Hidden NPC", logger);
						ItemStack mapItem = createMapItem(
							quest.getDestination(), "Find NPC", logger
						);
						player.getInventory().addItem(mapItem);
						logger.info("[generateQuest] Given Find NPC map to player "
							+ player.getName());
					}

					player.sendMessage("§a" + villagerUniqueNames.get(villager.getUniqueId())
						+ ": \"Here’s a quest for you! Open your inventory to see the details.\"");
					showQuestDetails(player, quest);
				});

			} catch (Exception e) {
				logger.log(Level.SEVERE, "[generateQuest] Failed to generate quest.", e);
				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§cError generating quest from villager."));
			}
		});

		logger.info("[generateQuest] end (async call triggered).");
	}

	// ------------------- AI "Nonsense" Lines -------------------
	private void generateNonsenseLine(Player player, Villager villager, String uniqueName) {
		Logger logger = plugin.getLogger();
		logger.info("[generateNonsenseLine] Start for player " + player.getName());

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				String prompt = "You are a " + uniqueName
					+ " Minecraft villager. Give exactly ONE short silly or nonsensical line.";
				ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Hmm, I'm speechless today...";
				}

				final String finalLine = response;
				final Npc npc = Npc.builder()
					.nonsensePhrase(finalLine)
					.timestamp(System.currentTimeMillis())
					.build();
				// Store the nonsense phrase with timestamp
				questManager.setVillagerData(villager.getUniqueId(), npc, logger);

				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§e" + uniqueName + ": \"" + finalLine + "\"")
				);
			} catch (Exception e) {
				logger.log(Level.SEVERE, "[generateNonsenseLine] Failed to generate villager line.", e);
				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§cVillager tried to speak but had no words..."));
			}
		});

		logger.info("[generateNonsenseLine] end (async call triggered).");
	}

	// ------------------- Reward MCMMO XP -------------------
	private void rewardPlayer(Player player, Quest quest) {
		Logger logger = plugin.getLogger();
		logger.info("[rewardPlayer] awarding XP to " + player.getName()
			+ ": " + quest.getRewardAmount() + " in " + quest.getRewardTarget());
		// We assume "MCMMO" is the reward type
		String skill = quest.getRewardTarget();
		int xp = quest.getRewardAmount();
		ExperienceAPI.addRawXP(player, skill, xp, "§aYou earned " + xp + " MCMMO XP in " + skill + "!");
	}

	private void removeQuestIndicator(UUID villagerId, Logger logger) {
		logger.info("[removeQuestIndicator] for villager " + villagerId);
		UUID standId = questManager.getIndicator(villagerId);
		if (standId != null) {
			Entity e = Bukkit.getEntity(standId);
			if (e instanceof ArmorStand) {
				e.remove();
				logger.info("[removeQuestIndicator] Removed indicator ArmorStand " + standId);
			}
		}
		questManager.removeIndicator(villagerId, logger);
	}

	// ------------------- Spawning a Treasure Chest -------------------
	private void spawnTreasureChest(Location loc, Logger logger) {
		logger.info("[spawnTreasureChest] at " + loc);
		loc.getBlock().setType(Material.CHEST);
		if (loc.getBlock().getState() instanceof org.bukkit.block.Chest cstate) {
			Inventory inv = cstate.getInventory();
			// Example loot
			inv.addItem(new ItemStack(Material.DIAMOND, 3));
			inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
			logger.info("[spawnTreasureChest] Added loot to chest at " + loc);
		}
	}

	// ------------------- Spawning a Hidden NPC -------------------
	private void spawnHiddenVillager(Location loc, String questTitle, Logger logger) {
		logger.info("[spawnHiddenVillager] at " + loc + ", questTitle=" + questTitle);
		loc.getWorld().spawn(loc, Villager.class, v -> {
			v.setPersistent(true);
			v.customName(Component.text("§6Hidden NPC: " + questTitle));
			v.setCustomNameVisible(true);
			v.setProfession(Villager.Profession.NITWIT);
			logger.info("[spawnHiddenVillager] Spawned Hidden NPC: " + v.getUniqueId());
			// Optionally, you can store the Hidden NPC's UUID for later removal
		});
	}

	// ------------------- Custom Map Renderer -------------------
	public static class DestinationMarkerRenderer extends MapRenderer {
		private final Location destination;
		private boolean hasRendered;

		public DestinationMarkerRenderer(Location destination) {
			super();
			this.destination = destination;
		}

		@Override
		public void render(MapView mapView, MapCanvas mapCanvas, Player player) {
			if (hasRendered) {
				return;
			}
			hasRendered = true;

			int dx = destination.getBlockX() - mapView.getCenterX();
			int dz = destination.getBlockZ() - mapView.getCenterZ();
			int scaleVal = 1 << mapView.getScale().getValue();
			int markerX = 64 + dx / scaleVal;
			int markerZ = 64 + dz / scaleVal;

			byte color = (byte) 116; // bright red
			if (markerX >= 1 && markerX < 127 && markerZ >= 1 && markerZ < 127) {
				mapCanvas.setPixel(markerX, markerZ, color);
				mapCanvas.setPixel(markerX - 1, markerZ, color);
				mapCanvas.setPixel(markerX + 1, markerZ, color);
				mapCanvas.setPixel(markerX, markerZ - 1, color);
				mapCanvas.setPixel(markerX, markerZ + 1, color);
			}
		}
	}

	// ------------------- Create a Map (for Treasure or NPC) -------------------
	private ItemStack createMapItem(Location dest, String title, Logger logger) {
		logger.info("[createMapItem] start for " + title + " at " + dest);
		ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
		MapMeta meta = (MapMeta) mapItem.getItemMeta();
		World world = dest.getWorld();
		world.getChunkAt(dest).load();

		MapView mapView = Bukkit.createMap(world);
		mapView.addRenderer(new DestinationMarkerRenderer(dest));

		mapView.setScale(MapView.Scale.FAR);
		mapView.setTrackingPosition(true);
		mapView.setCenterX(dest.getBlockX());
		mapView.setCenterZ(dest.getBlockZ());

		meta.setMapView(mapView);
		meta.displayName(Component.text("§6Quest Map: " + title));
		mapItem.setItemMeta(meta);

		logger.info("[createMapItem] end -> returning map item");
		return mapItem;
	}

	// ------------------- Random World Location with Logarithmic Distribution -------------------
	/**
	 * Returns a random generated location in the world using a log-weighted distance from spawn.
	 */
	private Location getLogarithmicLocation(World world, Logger logger) {
		logger.info("[getLogarithmicLocation] start for world " + world.getName());

		// 1) Grab spawn coords
		Location spawn = world.getSpawnLocation();
		double spawnX = spawn.getX();
		double spawnZ = spawn.getZ();

		// 2) Define min & max radius in blocks
		double rMin = 500;
		double rMax = 15000;

		// 3) Pick a radius with a log distribution
		//    random in [ln(rMin) .. ln(rMax)], then exponentiate
		double lnMin = Math.log(rMin);
		double lnMax = Math.log(rMax);
		double lnRand = ThreadLocalRandom.current().nextDouble(lnMin, lnMax);
		double radius = Math.exp(lnRand);

		// 4) Random angle in [0..2π)
		double theta = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);

		// 5) Convert polar -> cartesian, offset by spawn
		double x = spawnX + radius * Math.cos(theta);
		double z = spawnZ + radius * Math.sin(theta);

		// 6) Round to int for block coordinates
		int blockX = (int) Math.floor(x);
		int blockZ = (int) Math.floor(z);

		// 7) Load the chunk to ensure it's generated
		Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
		chunk.load(true); // force load
		logger.info("[getLogarithmicLocation] Loaded chunk at X="
			+ (blockX >> 4) + ", Z=" + (blockZ >> 4));

		// 8) Get the highest block (for a safe spawn)
		int y = world.getHighestBlockYAt(blockX, blockZ);
		Location loc = new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);
		logger.info("[getLogarithmicLocation] Generated location: " + loc);
		return loc;
	}

	/**
	 * Splits a given text into multiple lines based on the specified maximum length.
	 *
	 * @param text The original text to split.
	 * @param maxLength The maximum number of characters per line.
	 * @return A list of text segments, each within the maxLength.
	 */
	private List<String> splitText(String text, int maxLength) {
		List<String> lines = new ArrayList<>();
		String remainingText = text;

		while (remainingText.length() > maxLength) {
			int spaceIndex = remainingText.lastIndexOf(' ', maxLength);
			if (spaceIndex == -1) {
				spaceIndex = maxLength;
			}

			String line = remainingText.substring(0, spaceIndex).trim();
			lines.add("§f" + line);
			remainingText = remainingText.substring(spaceIndex).trim();
		}

		lines.add("§f" + remainingText);
		return lines;
	}

	// ------------------- Show Quest Details -------------------
	private void showQuestDetails(Player player, Quest quest) {
		Logger logger = plugin.getLogger();
		logger.info("[showQuestDetails] for player " + player.getName() + ", quest " + quest.getTitle());

		// quest.getTitle() is the AI-generated storyline
		player.sendMessage("§7§lQuest: §f" + quest.getTitle());

		if (EnumSet.of(TREASURE, FIND_NPC).contains(quest.getObjective().getType())) {
			player.sendMessage("§7Destination: Check your new map!");
		} else if (EnumSet.of(KILL, COLLECT).contains(quest.getObjective().getType())) {
			player.sendMessage("§7Objective: "
				+ quest.getObjective().getType()
				+ " " + quest.getObjective().getTarget()
				+ " x" + quest.getObjective().getAmount());
		}

		player.sendMessage("§7Reward: §f" + quest.getRewardType() + " "
			+ quest.getRewardTarget() + " x" + quest.getRewardAmount());
	}

	// Define decorative border item
	private ItemStack createBorderItem() {
		ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		ItemMeta borderMeta = border.getItemMeta();
		borderMeta.displayName(Component.text(" "));
		border.setItemMeta(borderMeta);
		return border;
	}

	// Define separator line
	private String createSeparator() {
		return "§8§m------------------------------";
	}

	/**
	 * Opens the quest dialogue GUI for the player.
	 *
	 * @param player The player to open the GUI for.
	 * @param quest The quest to display.
	 */
	@SuppressWarnings("PMD.NcssCount")
	private void openQuestDialogue(Player player, Quest quest) {
		Logger logger = plugin.getLogger();
		logger.info("[openQuestDialogue] Opening quest dialogue for player " + player.getName());

		// Update the pendingQuests map with the quest
		pendingQuests.put(player.getUniqueId(), quest);
		logger.info("[openQuestDialogue] Quest '" + quest.getShortTitle()
			+ "' is pending for player " + player.getName());

		// Create a new inventory with the specified size and title
		Inventory questInventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);

		// Define decorative border item
		ItemStack border = createBorderItem();

		// Add borders to specific slots in the GUI
		questInventory.setItem(0, border);
		questInventory.setItem(3, border);
		questInventory.setItem(7, border);

		// Add a separator at the bottom of the GUI
		ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		ItemMeta separatorMeta = separator.getItemMeta();
		separatorMeta.setDisplayName(createSeparator());
		separator.setItemMeta(separatorMeta);
		questInventory.setItem(8, separator);

		// ------------------- Title Item (Using shortTitle) -------------------
		ItemStack titleItem = new ItemStack(Material.ENCHANTED_BOOK);
		ItemMeta titleMeta = titleItem.getItemMeta();
		titleMeta.setDisplayName("§6§l" + quest.getShortTitle()); // Use shortTitle here

		// Split and add quest description into lore
		List<String> descriptionLines = splitText(quest.getTitle(), 50); // Use title as description
		List<String> titleLore = new ArrayList<>();
		titleLore.addAll(descriptionLines);
		titleMeta.setLore(titleLore);
		titleItem.setItemMeta(titleMeta);
		questInventory.setItem(1, titleItem);

		// ------------------- Objective Icon -------------------
		ItemStack objectiveIcon = new ItemStack(Material.CANDLE);
		ItemMeta objectiveMeta = objectiveIcon.getItemMeta();
		objectiveMeta.setDisplayName("§7§lObjective");
		List<String> objectiveLore = new ArrayList<>();

		String objectiveText = "§f"
			+ quest.getObjective().getType()
			+ " " + quest.getObjective().getTarget()
			+ " x" + quest.getObjective().getAmount();
		List<String> objectiveLines = splitText(objectiveText, 30); // Adjust maxLineLength as needed
		objectiveLore.addAll(objectiveLines);

		objectiveMeta.setLore(objectiveLore);
		objectiveIcon.setItemMeta(objectiveMeta);
		questInventory.setItem(2, objectiveIcon);

		// ------------------- Reward Icon -------------------
		ItemStack rewardIcon = new ItemStack(Material.EMERALD);
		ItemMeta rewardMeta = rewardIcon.getItemMeta();
		rewardMeta.setDisplayName("§7§lReward");
		List<String> rewardLore = new ArrayList<>();

		String rewardText = "§a"
			+ quest.getRewardType() + " "
			+ quest.getRewardTarget() + " x" + quest.getRewardAmount();
		List<String> rewardLines = splitText(rewardText, 30); // Adjust maxLineLength as needed
		rewardLore.addAll(rewardLines);

		rewardMeta.setLore(rewardLore);
		rewardIcon.setItemMeta(rewardMeta);
		questInventory.setItem(6, rewardIcon);

		// ------------------- Accept Button -------------------
		ItemStack acceptButton = new ItemStack(Material.GREEN_WOOL);
		ItemMeta acceptMeta = acceptButton.getItemMeta();
		acceptMeta.setDisplayName("§a§lAccept Quest");

		List<String> acceptLore = new ArrayList<>();
		acceptLore.add("§7Click to accept this quest.");
		acceptMeta.setLore(acceptLore);
		acceptButton.setItemMeta(acceptMeta);
		questInventory.setItem(4, acceptButton);

		// ------------------- Reject Button -------------------
		ItemStack rejectButton = new ItemStack(Material.RED_WOOL);
		ItemMeta rejectMeta = rejectButton.getItemMeta();
		rejectMeta.setDisplayName("§c§lReject Quest");
		List<String> rejectLore = new ArrayList<>();
		rejectLore.add("§7Click to reject this quest.");
		rejectMeta.setLore(rejectLore);
		rejectButton.setItemMeta(rejectMeta);
		questInventory.setItem(5, rejectButton);

		// ------------------- Fill Remaining Slots with Filler Items -------------------
		ItemStack filler = createBorderItem(); // Reuse border as filler
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			if (questInventory.getItem(i) == null) {
				questInventory.setItem(i, filler);
			}
		}

		// ------------------- Open the Inventory for the Player -------------------
		player.openInventory(questInventory);
	}

	// ------------------- Inventory Click Event -------------------
	@EventHandler
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	public void onInventoryClick(InventoryClickEvent event) {
		// Check if the inventory is our quest dialogue
		if (!event.getView().getTitle().equals(INVENTORY_TITLE)) {
			return; // Not our GUI
		}

		event.setCancelled(true); // Prevent item movement

		Inventory clickedInventory = event.getClickedInventory();
		if (clickedInventory == null) {
			return;
		}

		ItemStack clickedItem = event.getCurrentItem();
		if (clickedItem == null || clickedItem.getType() == Material.AIR) {
			return;
		}

		Logger logger = plugin.getLogger();
		Player player = (Player) event.getWhoClicked();
		int slot = event.getRawSlot();
		logger.info("[onInventoryClick] Player " + player.getName()
			+ " clicked slot " + slot + " with item " + clickedItem.getType());

		if (slot == ACCEPT_BUTTON_SLOT) {
			logger.info("[onInventoryClick] Player " + player.getName() + " accepted the quest.");
			Quest pendingQuest = getPlayerPendingQuest(player);

			if (pendingQuest != null) {
				// Assign the quest to the player
				questManager.assignQuest(player, pendingQuest, logger);
				player.sendMessage("§aYou have accepted the quest: " + pendingQuest.getTitle());
				logger.info("[onInventoryClick] Quest '" + pendingQuest.getTitle()
					+ "' assigned to player " + player.getName());

				// Play accept sound
				player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

				// Add Quest Book to Player's Inventory
				addQuestBook(player, pendingQuest, logger);

				// Remove the quest from pendingQuests
				pendingQuests.remove(player.getUniqueId());
			} else {
				player.sendMessage("§cNo quest found to accept.");
				logger.warning("[onInventoryClick] No matching quest found for player " + player.getName());
			}
			player.closeInventory();
		} else if (slot == REJECT_BUTTON_SLOT) {
			logger.info("[onInventoryClick] Player " + player.getName() + " rejected the quest.");
			Quest pendingQuest = getPlayerPendingQuest(player);

			if (pendingQuest != null) {
				// Remove the quest from villagerDataMap
				questManager.setVillagerData(pendingQuest.getVillagerUuid(), null, logger);
				removeQuestIndicator(pendingQuest.getVillagerUuid(), logger);
				player.sendMessage("§cYou have rejected the quest: " + pendingQuest.getTitle());
				logger.info("[onInventoryClick] Quest '" + pendingQuest.getTitle()
					+ "' rejected by player " + player.getName());

				// Play reject sound
				player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);

				// Remove the quest from pendingQuests
				pendingQuests.remove(player.getUniqueId());
			} else {
				player.sendMessage("§cNo quest found to reject.");
				logger.warning("[onInventoryClick] No matching quest found for player " + player.getName());
			}
			player.closeInventory();
		}
	}

	/**
	 * Helper method to retrieve the pending quest for a player.
	 *
	 * @param player The player whose pending quest is to be retrieved.
	 * @return The pending Quest or null if none exists.
	 */
	private Quest getPlayerPendingQuest(Player player) {
		return pendingQuests.get(player.getUniqueId());
	}

	// ------------------- Event Listener for Mob Kills -------------------
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		Logger logger = plugin.getLogger();
		LivingEntity entity = event.getEntity();

		Player killer = getKillerEntity(entity);

		if (killer == null) {
			logger.info("[onEntityDeath] Entity killed without a player killer or killer not a player.");
			return; // No player to attribute the kill
		}

		QuestProgress questProgress = questManager.getQuestProgress(killer, logger);

		if (questProgress == null) {
			logger.info("[onEntityDeath] Player " + killer.getName() + " has no active quest.");
			return; // Player has no active quest
		}

		Quest quest = questProgress.getQuest();
		QuestObjective objective = quest.getObjective();

		if (objective.getType() != QuestObjective.Type.KILL) {
			logger.info("[onEntityDeath] Active quest for player " + killer.getName()
				+ " is not a kill-type quest.");
			return; // Not a kill-type quest
		}

		String targetMob = objective.getTarget().toUpperCase();
		String killedMob = entity.getType().name();

		if (killedMob.equals(targetMob)) {
			boolean isComplete = questManager.incrementProgress(killer, 1, logger);
			logger.info("[onEntityDeath] Player " + killer.getName() + " killed " + killedMob
				+ ". Progress: " + questProgress.getCurrent() + "/" + quest.getObjective().getAmount());

			if (isComplete) {
				logger.info("[onEntityDeath] Player " + killer.getName()
					+ " has completed the quest: " + quest.getTitle());
				killer.sendMessage("§6Quest Update: You've completed the objective!");
				rewardPlayer(killer, quest);
				questManager.completeQuest(killer, logger);
				questManager.setVillagerData(quest.getVillagerUuid(), null, logger);
				removeQuestIndicator(quest.getVillagerUuid(), logger);
				removeQuestBook(killer);
			} else {
				killer.sendMessage("§eQuest Update: " + questProgress.getCurrent()
					+ "/" + quest.getObjective().getAmount() + " " + targetMob + "(s) killed.");
			}
		}
	}

	// ------------------- Event Listener for Item Pickup -------------------
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		Logger logger = plugin.getLogger();
		Player player = event.getPlayer();
		ItemStack item = event.getItem().getItemStack();

		QuestProgress questProgress = questManager.getQuestProgress(player, logger);

		if (questProgress == null) {
			logger.info("[onPlayerPickupItem] Player " + player.getName() + " has no active quest.");
			return; // Player has no active quest
		}

		Quest quest = questProgress.getQuest();
		QuestObjective objective = quest.getObjective();

		if (objective.getType() != QuestObjective.Type.COLLECT) {
			logger.info("[onPlayerPickupItem] Active quest for player " + player.getName()
				+ " is not a collect-type quest.");
			return; // Not a collect-type quest
		}

		String targetItem = objective.getTarget().toUpperCase();
		String pickedItem = item.getType().name();

		if (pickedItem.equals(targetItem)) {
			int amount = item.getAmount();
			boolean isComplete = questManager.incrementProgress(player, amount, logger);
			logger.info("[onPlayerPickupItem] Player " + player.getName() + " picked up "
				+ amount + " " + pickedItem + "(s). Progress: "
				+ questProgress.getCurrent() + "/" + quest.getObjective().getAmount());

			if (isComplete) {
				logger.info("[onPlayerPickupItem] Player " + player.getName()
					+ " has completed the quest: " + quest.getTitle());
				player.sendMessage("§6Quest Update: You've completed the objective!");
				rewardPlayer(player, quest);
				questManager.completeQuest(player, logger);
				questManager.setVillagerData(quest.getVillagerUuid(), null, logger);
				removeQuestIndicator(quest.getVillagerUuid(), logger);
				removeQuestBook(player);
			} else {
				player.sendMessage("§eQuest Update: " + questProgress.getCurrent()
					+ "/" + quest.getObjective().getAmount() + " " + targetItem + "(s) collected.");
			}
		}
	}

	// ------------------- Helper Method to Get Killer -------------------
	private Player getKillerEntity(LivingEntity entity) {
		// Attempt to use getKiller() (available in Bukkit 1.11+)
		Player killer = entity.getKiller();
		if (killer != null) {
			return killer;
		}

		return null;
	}

	// ------------------- Add Quest Book to Player -------------------
	/**
	 * Adds a quest book to the player's inventory with all quest details.
	 *
	 * @param player The player to receive the book.
	 * @param quest The quest details.
	 * @param logger The logger for logging.
	 */
	private void addQuestBook(Player player, Quest quest, Logger logger) {
		logger.info("[addQuestBook] Creating quest book for player " + player.getName());

		ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
		BookMeta meta = (BookMeta) book.getItemMeta();
		if (meta != null) {
			meta.setTitle(quest.getShortTitle());
			String villagerName = villagerUniqueNames.get(quest.getVillagerUuid());
			meta.setAuthor(villagerName != null ? villagerName : "Unknown Quest Giver");

			List<String> pages = new ArrayList<>();
			pages.add(ChatColor.GOLD + "Quest: " + quest.getShortTitle());
			pages.add(ChatColor.GREEN + "Description: " + quest.getTitle());

			if (EnumSet.of(TREASURE, FIND_NPC).contains(quest.getObjective().getType())) {
				pages.add(ChatColor.BLUE + "Destination: " + ChatColor.WHITE
					+ "Check your quest map for the location.");
			} else if (EnumSet.of(KILL, COLLECT).contains(quest.getObjective().getType())) {

				String objectiveText = quest.getObjective().getType() + " "
					+ quest.getObjective().getTarget() + " x" + quest.getObjective().getAmount();
				pages.add(ChatColor.YELLOW + "Objective: " + ChatColor.WHITE + objectiveText);
			}

			String rewardText = quest.getRewardType() + " " + quest.getRewardTarget()
				+ " x" + quest.getRewardAmount();
			pages.add(ChatColor.AQUA + "Reward: " + ChatColor.WHITE + rewardText);

			meta.setPages(pages);
			book.setItemMeta(meta);

			// Add the book to the player's inventory
			Map<Integer, ItemStack> leftover = player.getInventory().addItem(book);
			if (leftover.isEmpty()) {
				player.sendMessage("§aA quest book has been added to your inventory.");
				logger.info("[addQuestBook] Quest book added to player "
					+ player.getName() + "'s inventory.");
			} else {
				// If inventory is full, drop the book at player's location
				player.getWorld().dropItemNaturally(player.getLocation(), book);
				player.sendMessage(
					"§cYour inventory is full! The quest book has been dropped at your location."
				);
				logger.warning("[addQuestBook] Player " + player.getName()
					+ " inventory full. Dropped quest book.");
			}
		}
	}

	// ------------------- Remove Quest Book from Player -------------------
	/**
	 * Removes the quest book from the player's inventory based on the quest's short title.
	 *
	 * @param player The player from whose inventory the book should be removed.
	 */
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	private void removeQuestBook(Player player) {
		Logger logger = plugin.getLogger();
		logger.info("[removeQuestBook] Attempting to remove quest book from player " + player.getName());

		Inventory inv = player.getInventory();
		QuestProgress progress = questManager.getQuestProgress(player, logger);
		if (progress == null) {
			logger.info("[removeQuestBook] Player " + player.getName() + " has no active quest.");
			return;
		}

		String questTitle = progress.getQuest().getShortTitle();

		for (int i = 0; i < inv.getSize(); i++) {
			if (isQuestBook(inv.getItem(i), questTitle)) {
				inv.setItem(i, null);
				logger.info("[removeQuestBook] Removed quest book titled '" + questTitle
					+ "' from player " + player.getName());
				player.sendMessage("§cYour quest book has been removed.");
				return;
			}
		}

		logger.info("[removeQuestBook] No matching quest book found for player " + player.getName());
	}

	private boolean isQuestBook(ItemStack item, String questTitle) {
		if (item == null || item.getType() != Material.WRITTEN_BOOK) {
			return false;
		}
		BookMeta meta = (BookMeta) item.getItemMeta();
		return meta != null && questTitle.equals(meta.getTitle());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		Logger logger = plugin.getLogger();
		Chunk chunk = event.getChunk();

		removeAllQuestIndicators(chunk, logger);

		// Retrieve all entities in the chunk
		Entity[] entities = chunk.getEntities();
		for (Entity entity : entities) {
			if (entity instanceof Villager) {
				Villager villager = (Villager) entity;
				UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					String uniqueName = villagerUniqueNames.get(villagerId);
					villager.setCustomName("§a" + uniqueName);
					villager.setCustomNameVisible(true);
					logger.info("[onChunkLoad] Assigned existing name '"
						+ uniqueName + "' to villager " + villagerId);
				} else {
					logger.info("[onChunkLoad] Villager " + villagerId
						+ " does not have a unique name. Generating now.");
					generateUniqueNameForVillager(villager);
				}
			}
		}
	}

	// ------------------- Event Listener for Returning to Villager to Complete Quest -------------------
	@EventHandler
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	public void onPlayerInteractVillagerCompletion(PlayerInteractEntityEvent event) {
		Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			return;
		}

		Player player = event.getPlayer();
		Logger logger = plugin.getLogger();

		// Check if the player has an active quest assigned by this villager
		final Npc npc = questManager.getVillagerData(villager.getUniqueId(), logger);
		if (npc == null || !npc.isQuest()) {
			return; // No active quest from this villager
		}

		Quest quest = npc.getQuest();
		QuestProgress progress = questManager.getQuestProgress(player, logger);
		if (progress == null) {
			return; // Player has no active quest
		}

		// Check if the quest is of type TREASURE or FIND_NPC and the player has reached the destination
		if (EnumSet.of(TREASURE, FIND_NPC).contains(quest.getObjective().getType())
			&& quest.getDestination().distance(player.getLocation()) <= 10) {

			// Complete the quest
			player.sendMessage("§aYou have completed the quest: " + quest.getTitle());
			rewardPlayer(player, quest);
			questManager.completeQuest(player, logger);
			questManager.setVillagerData(villager.getUniqueId(), null, logger);
			removeQuestIndicator(villager.getUniqueId(), logger);
			removeQuestBook(player);

			// Optionally, remove the chest or hidden NPC
			if (quest.getObjective().getType() == TREASURE) {
				// Remove the treasure chest
				quest.getDestination().getBlock().setType(Material.AIR);
				logger.info("[onPlayerInteractVillagerCompletion] Removed treasure chest at "
					+ quest.getDestination());
			}

			// Play completion sound
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
		}
	}

	public void removeAllQuestIndicators(Chunk chunk, Logger logger) {
		// Iterate through all loaded worlds
		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof ArmorStand armorStand) {
				String customName = armorStand.getCustomName();
				if (customName != null && customName.contains("Quest")) {
					armorStand.setPersistent(false);
					armorStand.remove();
					logger.info("[onChunkLoad] Removed ArmorStand with UUID: "
						+ armorStand.getUniqueId() + " and Name: \"" + customName + "\"");
				}
			}
		}
	}
}
