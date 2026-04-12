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

package com.adeptum.questai.service;

import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import com.adeptum.questai.utility.EnumUtil;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.adeptum.questai.model.world.quest.QuestObjective.Type.COLLECT;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.FIND_NPC;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.KILL;
import static com.adeptum.questai.model.world.quest.QuestObjective.Type.TREASURE;

/**
 * Stateless service that generates random quests and their AI descriptions.
 */
public class QuestGenerationService {

	private static final String[] MOBS = {"ZOMBIE", "SKELETON", "SPIDER", "CREEPER"};
	private static final String[] ITEMS = {"IRON_INGOT", "WHEAT", "CARROT", "BONE"};
	private static final String[] SKILLS = {"MINING", "WOODCUTTING", "EXCAVATION", "FISHING"};

	private final JavaPlugin plugin;
	private final OpenAiChatModel chatModel;

	public QuestGenerationService(final JavaPlugin plugin, final OpenAiChatModel chatModel) {
		this.plugin = plugin;
		this.chatModel = chatModel;
	}

	public QuestObjective generateRandomObjective() {
		final QuestObjective objective = new QuestObjective();
		final QuestObjective.Type type = EnumUtil.random(QuestObjective.Type.class);
		objective.setType(type);

		if (type == KILL) {
			objective.setTarget(MOBS[ThreadLocalRandom.current().nextInt(MOBS.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else if (type == COLLECT) {
			objective.setTarget(ITEMS[ThreadLocalRandom.current().nextInt(ITEMS.length)]);
			objective.setAmount(ThreadLocalRandom.current().nextInt(3, 51));
		} else {
			objective.setTarget("NONE");
			objective.setAmount(1);
		}

		return objective;
	}

	public Quest buildQuest(final QuestObjective objective, final UUID npcUuid,
		final World world) {

		final String[] possibleSkills = SKILLS;
		final String rewardSkill =
			possibleSkills[ThreadLocalRandom.current().nextInt(possibleSkills.length)];
		final int rewardXP = ThreadLocalRandom.current().nextInt(50, 201);

		final Quest quest = new Quest();
		quest.setObjective(objective);
		quest.setRewardType("MCMMO");
		quest.setRewardTarget(rewardSkill);
		quest.setRewardAmount(rewardXP);
		quest.setVillagerUuid(npcUuid);

		if (EnumSet.of(FIND_NPC, TREASURE).contains(objective.getType())) {
			final Location loc = getLogarithmicLocation(world);
			quest.setDestination(loc);
		}

		return quest;
	}

	public void generateQuestDescription(final Quest quest, final Runnable onComplete,
		final Consumer<Exception> onError) {

		final Logger logger = plugin.getLogger();

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

				Bukkit.getScheduler().runTask(plugin, onComplete);
			} catch (Exception e) {
				logger.log(Level.SEVERE,
					"[QuestGenerationService] Failed to generate quest description.", e);
				Bukkit.getScheduler().runTask(plugin, () -> onError.accept(e));
			}
		});
	}

	public Location getLogarithmicLocation(final World world) {
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
}
