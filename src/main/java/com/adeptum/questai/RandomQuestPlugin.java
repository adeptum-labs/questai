package com.adeptum.questai;

import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.COLLECT;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.FIND_NPC;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.KILL;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.TREASURE;
import com.adeptum.questai.quest.DestinationMarkerRenderer;
import com.adeptum.questai.quest.QuestManager;
import com.adeptum.questai.quest.QuestProgress;
import com.adeptum.questai.utility.EnumUtil;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.meta.BookMeta;

/**
 * Plugin that creates generated villager quests and short villager dialogue.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class RandomQuestPlugin implements SubPlugin {

	// ------------------- Constants -------------------
	private static final int INVENTORY_SIZE = 9;
	private static final String INVENTORY_TITLE = "§6Quest Offered";
	private static final int ACCEPT_BUTTON_SLOT = 4;
	private static final int REJECT_BUTTON_SLOT = 5;

	private static final double QUEST_CHANCE = 0.3;

	private OpenAiChatModel chatModel;
	private QuestManager questManager;

	private final Map<UUID, Quest> pendingQuests =
		Collections.synchronizedMap(new HashMap<>());

	private final Map<UUID, String> villagerUniqueNames =
		Collections.synchronizedMap(new HashMap<>());
	private final ReentrantLock villagerUniqueNamesLock = new ReentrantLock();

	private final JavaPlugin plugin;

	public RandomQuestPlugin(JavaPlugin plugin) {
		super();
		this.plugin = plugin;
	}

	// ------------------- onEnable and onDisable -------------------
	@Override
	public void onEnable() {
		final Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onEnable() start");

		final PluginManager pm = plugin.getServer().getPluginManager();
		pm.registerEvents(this, plugin);

		plugin.saveDefaultConfig();
		final FileConfiguration config = plugin.getConfig();

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
			logger.info("[onEnable] Loaded " + villagerUniqueNames.size()
				+ " villager unique names from config.");
		}

		final String apiKey = config.getString("openai.api-key");
		if (apiKey == null || apiKey.isEmpty()) {
			logger.severe("OpenAI API key is not set in config.yml!");
			Bukkit.getPluginManager().disablePlugin(plugin);
			return;
		}

		this.chatModel = OpenAiChatModel.builder()
			.apiKey(apiKey)
			.modelName(OpenAiChatModelName.GPT_4_O_MINI)
			.build();

		this.questManager = new QuestManager(plugin, this::removeQuestBook);
		logger.info("[RandomQuestPlugin] QuestManager initialized.");
		assignUniqueNamesToAllVillagers();
		logger.info("[RandomQuestPlugin] onEnable() end -> plugin fully enabled.");
	}

	@Override
	public void onDisable() {
		final Logger logger = plugin.getLogger();
		logger.info("[RandomQuestPlugin] onDisable() start");

		questManager.cleanupAllQuests();
		saveVillagerUniqueNames();

		logger.info("[RandomQuestPlugin] onDisable() end -> plugin disabled.");
	}

	// ------------------- Villager Interaction -------------------
	@EventHandler
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	public void onVillagerClick(PlayerInteractEntityEvent event) {
		final Logger logger = plugin.getLogger();
		logger.info("[onVillagerClick] Event triggered.");

		final Entity clicked = event.getRightClicked();
		if (!(clicked instanceof Villager villager)) {
			logger.info("[onVillagerClick] Clicked entity is not a villager, ignoring.");
			return;
		}

		event.setCancelled(true);
		final Player player = event.getPlayer();
		logger.info("[onVillagerClick] Player " + player.getName()
			+ " clicked villager " + villager.getUniqueId());

		final String uniqueName = villagerUniqueNames.get(villager.getUniqueId());
		if (uniqueName == null) {
			logger.warning("[onVillagerClick] Villager " + villager.getUniqueId()
				+ " does not have a unique name.");
			return;
		}

		final Npc npc = questManager.getVillagerData(villager.getUniqueId());
		final long currentTime = System.currentTimeMillis();
		final long twoHoursMillis = 2L * 60 * 60 * 1000;

		if (npc != null) {
			if (currentTime - npc.getTimestamp() <= twoHoursMillis) {
				if (npc.isQuest()) {
					logger.info("[onVillagerClick] Villager has an active quest. Opening GUI.");
					openQuestDialogue(player, npc.getQuest());
				} else if (npc.isPhrase()) {
					logger.info("[onVillagerClick] Sending stored nonsense phrase to player.");
					player.sendMessage("§e" + uniqueName + ": \""
						+ npc.getNonsensePhrase() + "\"");
				}
				return;
			} else {
				logger.info("[onVillagerClick] Villager's data expired. Removing old data.");
				questManager.setVillagerData(villager.getUniqueId(), null);
			}
		}

		final double randomVal = Math.random();
		logger.info("[onVillagerClick] randomVal=" + randomVal
			+ " questChance=" + QUEST_CHANCE);
		if (randomVal <= QUEST_CHANCE) {
			logger.info("[onVillagerClick] Decided to generate a new quest for the player.");
			generateQuest(player, villager);
		} else {
			logger.info("[onVillagerClick] Decided to generate a nonsense line instead.");
			generateNonsenseLine(player, villager, uniqueName);
		}
	}

	// ------------------- VillagerData Management -------------------
	private void assignUniqueNamesToAllVillagers() {
		final Logger logger = plugin.getLogger();
		logger.info("[assignUniqueNamesToAllVillagers] Assigning unique names.");

		for (World world : Bukkit.getWorlds()) {
			for (Villager villager : world.getEntitiesByClass(Villager.class)) {
				final UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					final String uniqueName = villagerUniqueNames.get(villagerId);
					villager.setCustomName("§a" + uniqueName);
					villager.setCustomNameVisible(true);
				} else {
					generateUniqueNameForVillager(villager);
				}
			}
		}

		logger.info("[assignUniqueNamesToAllVillagers] Completed assigning unique names.");
	}

	// ------------------- Handle New Villager Spawns -------------------
	@EventHandler
	public void onCreatureSpawn(CreatureSpawnEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) {
			return;
		}

		final UUID villagerId = villager.getUniqueId();
		if (!villagerUniqueNames.containsKey(villagerId)) {
			final Logger logger = plugin.getLogger();
			logger.info("[onCreatureSpawn] Assigning unique name to newly spawned villager "
				+ villagerId);
			generateUniqueNameForVillager(villager);
		}
	}

	private void generateUniqueNameForVillager(Villager villager) {
		final Logger logger = plugin.getLogger();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				String prompt = "Provide a unique and creative name for a Minecraft villager"
					+ " with UUID: " + villager.getUniqueId()
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
					"[generateUniqueNameForVillager] Failed to generate villager name.", e
				);
				Bukkit.getScheduler().runTask(plugin, ()
					-> villager.setCustomName("§a")
				);
			}
		});
	}

	// ------------------- Save Villager Unique Names -------------------
	private void saveVillagerUniqueNames() {
		final Logger logger = plugin.getLogger();
		logger.info("[saveVillagerUniqueNames] Saving villager unique names to config.");
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
			logger.log(Level.SEVERE,
				"[saveVillagerUniqueNames] Failed to save config.", e);
		}
	}

	// ------------------- Generate Quest (Async) -------------------
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	private void generateQuest(Player player, Villager villager) {
		final Logger logger = plugin.getLogger();
		logger.info("[generateQuest] Start for player " + player.getName()
			+ ", villager " + villager.getUniqueId());

		final QuestObjective objective = new QuestObjective();
		objective.setType(EnumUtil.random(QuestObjective.Type.class));

		if (objective.getType() == KILL) {
			final String[] mobs = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER"};
			objective.setTarget(mobs[ThreadLocalRandom.current().nextInt(mobs.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else if (objective.getType() == COLLECT) {
			final String[] items = {"IRON_INGOT", "WHEAT", "CARROT", "BONE"};
			objective.setTarget(items[ThreadLocalRandom.current().nextInt(items.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else {
			objective.setTarget("NONE");
			objective.setAmount(1);
		}

		final String rewardType = "MCMMO";
		final String[] possibleSkills = {"MINING", "WOODCUTTING", "EXCAVATION", "FISHING"};
		final String rewardSkill =
			possibleSkills[ThreadLocalRandom.current().nextInt(possibleSkills.length)];
		final int rewardXP = ThreadLocalRandom.current().nextInt(50, 201);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType(rewardType);
		quest.setRewardTarget(rewardSkill);
		quest.setRewardAmount(rewardXP);
		quest.setVillagerUuid(villager.getUniqueId());

		if (EnumSet.of(FIND_NPC, TREASURE).contains(quest.getObjective().getType())) {
			final Location loc = getLogarithmicLocation(villager.getWorld());
			quest.setDestination(loc);
		}

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String prompt = quest.prompt();
				final ChatRequest req = ChatRequest.builder()
					.messages(UserMessage.from(prompt))
					.build();
				String response = chatModel.chat(req).aiMessage().text().trim();
				if (response.isEmpty()) {
					response = "Quest Title\nA mysterious quest awaits...";
				}

				final String[] parts = response.split("\n", 2);
				final String shortTitle = parts.length > 0 ? parts[0].trim() : "Quest";
				final String description = parts.length > 1
					? parts[1].trim() : "A mysterious quest awaits...";

				quest.setShortTitle(shortTitle);
				quest.setTitle(description);

				Bukkit.getScheduler().runTask(plugin, () -> {
					final Npc npc = Npc.builder()
						.quest(quest)
						.timestamp(System.currentTimeMillis())
						.build();

					questManager.setVillagerData(villager.getUniqueId(), npc);
					openQuestDialogue(player, quest);

					if (objective.getType() == TREASURE) {
						spawnTreasureChest(quest.getDestination());
						final ItemStack mapItem = createMapItem(
							quest.getDestination(), "Treasure Hunt"
						);
						player.getInventory().addItem(mapItem);
					} else if (objective.getType() == FIND_NPC) {
						spawnHiddenVillager(
							quest.getDestination(), "Hidden NPC"
						);
						final ItemStack mapItem = createMapItem(
							quest.getDestination(), "Find NPC"
						);
						player.getInventory().addItem(mapItem);
					}

					player.sendMessage("§a"
						+ villagerUniqueNames.get(villager.getUniqueId())
						+ ": \"Here's a quest for you!"
						+ " Open your inventory to see the details.\"");
					showQuestDetails(player, quest);
				});

			} catch (Exception e) {
				logger.log(Level.SEVERE, "[generateQuest] Failed to generate quest.", e);
				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§cError generating quest from villager."));
			}
		});
	}

	// ------------------- AI "Nonsense" Lines -------------------
	private void generateNonsenseLine(Player player, Villager villager,
		String uniqueName) {

		final Logger logger = plugin.getLogger();
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String prompt = "You are a " + uniqueName
					+ " Minecraft villager. Give exactly ONE short silly"
					+ " or nonsensical line.";
				final ChatRequest req = ChatRequest.builder()
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
				questManager.setVillagerData(villager.getUniqueId(), npc);

				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§e" + uniqueName + ": \"" + finalLine + "\"")
				);
			} catch (Exception e) {
				logger.log(Level.SEVERE,
					"[generateNonsenseLine] Failed to generate villager line.", e);
				Bukkit.getScheduler().runTask(plugin, ()
					-> player.sendMessage("§cVillager tried to speak but had no words..."));
			}
		});
	}

	// ------------------- Reward MCMMO XP -------------------
	private void rewardPlayer(Player player, Quest quest) {
		final Logger logger = plugin.getLogger();
		logger.info("[rewardPlayer] awarding XP to " + player.getName()
			+ ": " + quest.getRewardAmount() + " in " + quest.getRewardTarget());
		final String skill = quest.getRewardTarget();
		final int xp = quest.getRewardAmount();
		ExperienceAPI.addRawXP(player, skill, xp,
			"§aYou earned " + xp + " MCMMO XP in " + skill + "!");
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

	// ------------------- Spawning a Treasure Chest -------------------
	private void spawnTreasureChest(Location loc) {
		loc.getBlock().setType(Material.CHEST);
		if (loc.getBlock().getState() instanceof org.bukkit.block.Chest cstate) {
			final Inventory inv = cstate.getInventory();
			inv.addItem(new ItemStack(Material.DIAMOND, 3));
			inv.addItem(new ItemStack(Material.GOLDEN_APPLE, 1));
		}
	}

	// ------------------- Spawning a Hidden NPC -------------------
	private void spawnHiddenVillager(Location loc, String questTitle) {
		loc.getWorld().spawn(loc, Villager.class, v -> {
			v.setPersistent(true);
			v.customName(Component.text("§6Hidden NPC: " + questTitle));
			v.setCustomNameVisible(true);
			v.setProfession(Villager.Profession.NITWIT);
		});
	}

	// ------------------- Create a Map -------------------
	private ItemStack createMapItem(Location dest, String title) {
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

	// ------------------- Random World Location -------------------
	private Location getLogarithmicLocation(World world) {
		final Location spawn = world.getSpawnLocation();
		final double spawnX = spawn.getX();
		final double spawnZ = spawn.getZ();

		final double rMin = 500;
		final double rMax = 15000;

		final double lnMin = Math.log(rMin);
		final double lnMax = Math.log(rMax);
		final double lnRand = ThreadLocalRandom.current().nextDouble(lnMin, lnMax);
		final double radius = Math.exp(lnRand);

		final double theta = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);

		final int blockX = (int) Math.floor(spawnX + radius * Math.cos(theta));
		final int blockZ = (int) Math.floor(spawnZ + radius * Math.sin(theta));

		final Chunk chunk = world.getChunkAt(blockX >> 4, blockZ >> 4);
		chunk.load(true);

		final int y = world.getHighestBlockYAt(blockX, blockZ);
		return new Location(world, blockX + 0.5, y + 1, blockZ + 0.5);
	}

	private List<String> splitText(String text, int maxLength) {
		final List<String> lines = new ArrayList<>();
		String remainingText = text;

		while (remainingText.length() > maxLength) {
			int spaceIndex = remainingText.lastIndexOf(' ', maxLength);
			if (spaceIndex == -1) {
				spaceIndex = maxLength;
			}

			final String line = remainingText.substring(0, spaceIndex).trim();
			lines.add("§f" + line);
			remainingText = remainingText.substring(spaceIndex).trim();
		}

		lines.add("§f" + remainingText);
		return lines;
	}

	// ------------------- Show Quest Details -------------------
	private void showQuestDetails(Player player, Quest quest) {
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

	private ItemStack createBorderItem() {
		final ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
		final ItemMeta borderMeta = border.getItemMeta();
		borderMeta.displayName(Component.text(" "));
		border.setItemMeta(borderMeta);
		return border;
	}

	private String createSeparator() {
		return "§8§m------------------------------";
	}

	private void openQuestDialogue(Player player, Quest quest) {
		pendingQuests.put(player.getUniqueId(), quest);

		final Inventory questInventory =
			Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);

		final ItemStack border = createBorderItem();
		questInventory.setItem(0, border);
		questInventory.setItem(3, border);
		questInventory.setItem(7, border);

		final ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		final ItemMeta separatorMeta = separator.getItemMeta();
		separatorMeta.setDisplayName(createSeparator());
		separator.setItemMeta(separatorMeta);
		questInventory.setItem(8, separator);

		// Title item
		final ItemStack titleItem = new ItemStack(Material.ENCHANTED_BOOK);
		final ItemMeta titleMeta = titleItem.getItemMeta();
		titleMeta.setDisplayName("§6§l" + quest.getShortTitle());
		final List<String> descriptionLines = splitText(quest.getTitle(), 50);
		titleMeta.setLore(new ArrayList<>(descriptionLines));
		titleItem.setItemMeta(titleMeta);
		questInventory.setItem(1, titleItem);

		// Objective icon
		final ItemStack objectiveIcon = new ItemStack(Material.CANDLE);
		final ItemMeta objectiveMeta = objectiveIcon.getItemMeta();
		objectiveMeta.setDisplayName("§7§lObjective");
		final String objectiveText = "§f"
			+ quest.getObjective().getType()
			+ " " + quest.getObjective().getTarget()
			+ " x" + quest.getObjective().getAmount();
		objectiveMeta.setLore(new ArrayList<>(splitText(objectiveText, 30)));
		objectiveIcon.setItemMeta(objectiveMeta);
		questInventory.setItem(2, objectiveIcon);

		// Reward icon
		final ItemStack rewardIcon = new ItemStack(Material.EMERALD);
		final ItemMeta rewardMeta = rewardIcon.getItemMeta();
		rewardMeta.setDisplayName("§7§lReward");
		final String rewardText = "§a"
			+ quest.getRewardType() + " "
			+ quest.getRewardTarget() + " x" + quest.getRewardAmount();
		rewardMeta.setLore(new ArrayList<>(splitText(rewardText, 30)));
		rewardIcon.setItemMeta(rewardMeta);
		questInventory.setItem(6, rewardIcon);

		// Accept button
		final ItemStack acceptButton = new ItemStack(Material.GREEN_WOOL);
		final ItemMeta acceptMeta = acceptButton.getItemMeta();
		acceptMeta.setDisplayName("§a§lAccept Quest");
		acceptMeta.setLore(List.of("§7Click to accept this quest."));
		acceptButton.setItemMeta(acceptMeta);
		questInventory.setItem(4, acceptButton);

		// Reject button
		final ItemStack rejectButton = new ItemStack(Material.RED_WOOL);
		final ItemMeta rejectMeta = rejectButton.getItemMeta();
		rejectMeta.setDisplayName("§c§lReject Quest");
		rejectMeta.setLore(List.of("§7Click to reject this quest."));
		rejectButton.setItemMeta(rejectMeta);
		questInventory.setItem(5, rejectButton);

		// Fill remaining slots
		final ItemStack filler = createBorderItem();
		for (int i = 0; i < INVENTORY_SIZE; i++) {
			if (questInventory.getItem(i) == null) {
				questInventory.setItem(i, filler);
			}
		}

		player.openInventory(questInventory);
	}

	// ------------------- Inventory Click Event -------------------
	@EventHandler
	@SuppressWarnings({"PMD.CognitiveComplexity", "checkstyle:CyclomaticComplexity"})
	public void onInventoryClick(InventoryClickEvent event) {
		if (!event.getView().getTitle().equals(INVENTORY_TITLE)) {
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
		final int slot = event.getRawSlot();

		if (slot == ACCEPT_BUTTON_SLOT) {
			final Quest pendingQuest = pendingQuests.get(player.getUniqueId());

			if (pendingQuest != null) {
				questManager.assignQuest(player, pendingQuest);
				player.sendMessage("§aYou have accepted the quest: "
					+ pendingQuest.getTitle());

				player.playSound(
					player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f
				);

				addQuestBook(player, pendingQuest);
				pendingQuests.remove(player.getUniqueId());
			} else {
				player.sendMessage("§cNo quest found to accept.");
			}
			player.closeInventory();
		} else if (slot == REJECT_BUTTON_SLOT) {
			final Quest pendingQuest = pendingQuests.get(player.getUniqueId());

			if (pendingQuest != null) {
				questManager.setVillagerData(pendingQuest.getVillagerUuid(), null);
				removeQuestIndicator(pendingQuest.getVillagerUuid());
				player.sendMessage("§cYou have rejected the quest: "
					+ pendingQuest.getTitle());

				player.playSound(
					player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f
				);

				pendingQuests.remove(player.getUniqueId());
			} else {
				player.sendMessage("§cNo quest found to reject.");
			}
			player.closeInventory();
		}
	}

	// ------------------- Event Listener for Mob Kills -------------------
	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		final LivingEntity entity = event.getEntity();
		final Player killer = entity.getKiller();

		if (killer == null) {
			return;
		}

		final QuestProgress questProgress = questManager.getQuestProgress(killer);
		if (questProgress == null) {
			return;
		}

		final Quest quest = questProgress.getQuest();
		final QuestObjective objective = quest.getObjective();

		if (objective.getType() != KILL) {
			return;
		}

		final String targetMob = objective.getTarget().toUpperCase();
		final String killedMob = entity.getType().name();

		if (killedMob.equals(targetMob)) {
			final boolean isComplete = questManager.incrementProgress(killer, 1);

			if (isComplete) {
				killer.sendMessage("§6Quest Update: You've completed the objective!");
				rewardPlayer(killer, quest);
				questManager.completeQuest(killer);
				questManager.setVillagerData(quest.getVillagerUuid(), null);
				removeQuestIndicator(quest.getVillagerUuid());
				removeQuestBook(killer);
			} else {
				killer.sendMessage("§eQuest Update: " + questProgress.getCurrent()
					+ "/" + objective.getAmount() + " " + targetMob + "(s) killed.");
			}
		}
	}

	// ------------------- Event Listener for Item Pickup -------------------
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		final Player player = event.getPlayer();
		final ItemStack item = event.getItem().getItemStack();

		final QuestProgress questProgress = questManager.getQuestProgress(player);
		if (questProgress == null) {
			return;
		}

		final Quest quest = questProgress.getQuest();
		final QuestObjective objective = quest.getObjective();

		if (objective.getType() != COLLECT) {
			return;
		}

		final String targetItem = objective.getTarget().toUpperCase();
		final String pickedItem = item.getType().name();

		if (pickedItem.equals(targetItem)) {
			final int amount = item.getAmount();
			final boolean isComplete = questManager.incrementProgress(player, amount);

			if (isComplete) {
				player.sendMessage("§6Quest Update: You've completed the objective!");
				rewardPlayer(player, quest);
				questManager.completeQuest(player);
				questManager.setVillagerData(quest.getVillagerUuid(), null);
				removeQuestIndicator(quest.getVillagerUuid());
				removeQuestBook(player);
			} else {
				player.sendMessage("§eQuest Update: " + questProgress.getCurrent()
					+ "/" + objective.getAmount()
					+ " " + targetItem + "(s) collected.");
			}
		}
	}

	// ------------------- Add Quest Book -------------------
	private void addQuestBook(Player player, Quest quest) {
		final ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
		final BookMeta meta = (BookMeta) book.getItemMeta();
		if (meta != null) {
			meta.setTitle(quest.getShortTitle());
			final String villagerName =
				villagerUniqueNames.get(quest.getVillagerUuid());
			meta.setAuthor(villagerName != null ? villagerName : "Unknown Quest Giver");

			final List<String> pages = new ArrayList<>();
			pages.add(ChatColor.GOLD + "Quest: " + quest.getShortTitle());
			pages.add(ChatColor.GREEN + "Description: " + quest.getTitle());

			if (EnumSet.of(TREASURE, FIND_NPC)
				.contains(quest.getObjective().getType())) {
				pages.add(ChatColor.BLUE + "Destination: " + ChatColor.WHITE
					+ "Check your quest map for the location.");
			} else if (EnumSet.of(KILL, COLLECT)
				.contains(quest.getObjective().getType())) {
				final String objectiveText = quest.getObjective().getType() + " "
					+ quest.getObjective().getTarget()
					+ " x" + quest.getObjective().getAmount();
				pages.add(ChatColor.YELLOW + "Objective: "
					+ ChatColor.WHITE + objectiveText);
			}

			final String rewardText = quest.getRewardType() + " "
				+ quest.getRewardTarget() + " x" + quest.getRewardAmount();
			pages.add(ChatColor.AQUA + "Reward: " + ChatColor.WHITE + rewardText);

			meta.setPages(pages);
			book.setItemMeta(meta);

			final Map<Integer, ItemStack> leftover =
				player.getInventory().addItem(book);
			if (leftover.isEmpty()) {
				player.sendMessage("§aA quest book has been added to your inventory.");
			} else {
				player.getWorld().dropItemNaturally(player.getLocation(), book);
				player.sendMessage(
					"§cYour inventory is full!"
						+ " The quest book has been dropped at your location."
				);
			}
		}
	}

	// ------------------- Remove Quest Book -------------------
	@SuppressWarnings("checkstyle:CyclomaticComplexity")
	private void removeQuestBook(Player player) {
		final Inventory inv = player.getInventory();
		final QuestProgress progress = questManager.getQuestProgress(player);
		if (progress == null) {
			return;
		}

		final String questTitle = progress.getQuest().getShortTitle();

		for (int i = 0; i < inv.getSize(); i++) {
			if (isQuestBook(inv.getItem(i), questTitle)) {
				inv.setItem(i, null);
				player.sendMessage("§cYour quest book has been removed.");
				return;
			}
		}
	}

	private boolean isQuestBook(ItemStack item, String questTitle) {
		if (item == null || item.getType() != Material.WRITTEN_BOOK) {
			return false;
		}
		final BookMeta meta = (BookMeta) item.getItemMeta();
		return meta != null && questTitle.equals(meta.getTitle());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoad(ChunkLoadEvent event) {
		final Chunk chunk = event.getChunk();

		removeAllQuestIndicators(chunk);

		final Entity[] entities = chunk.getEntities();
		for (Entity entity : entities) {
			if (entity instanceof Villager villager) {
				final UUID villagerId = villager.getUniqueId();
				if (villagerUniqueNames.containsKey(villagerId)) {
					final String uniqueName = villagerUniqueNames.get(villagerId);
					villager.setCustomName("§a" + uniqueName);
					villager.setCustomNameVisible(true);
				} else {
					generateUniqueNameForVillager(villager);
				}
			}
		}
	}

	// ------------------- Villager Completion Interaction -------------------
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
		final QuestProgress progress = questManager.getQuestProgress(player);
		if (progress == null) {
			return;
		}

		if (EnumSet.of(TREASURE, FIND_NPC).contains(quest.getObjective().getType())
			&& quest.getDestination().distance(player.getLocation()) <= 10) {

			player.sendMessage("§aYou have completed the quest: " + quest.getTitle());
			rewardPlayer(player, quest);
			questManager.completeQuest(player);
			questManager.setVillagerData(villager.getUniqueId(), null);
			removeQuestIndicator(villager.getUniqueId());
			removeQuestBook(player);

			if (quest.getObjective().getType() == TREASURE) {
				quest.getDestination().getBlock().setType(Material.AIR);
			}

			player.playSound(
				player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f
			);
		}
	}

	public void removeAllQuestIndicators(Chunk chunk) {
		final Logger logger = plugin.getLogger();
		for (Entity entity : chunk.getEntities()) {
			if (entity instanceof ArmorStand armorStand) {
				final String customName = armorStand.getCustomName();
				if (customName != null && customName.contains("Quest")) {
					armorStand.setPersistent(false);
					armorStand.remove();
					logger.info("[onChunkLoad] Removed ArmorStand with UUID: "
						+ armorStand.getUniqueId()
						+ " and Name: \"" + customName + "\"");
				}
			}
		}
	}
}
