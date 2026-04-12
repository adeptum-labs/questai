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

package com.adeptum.questai.dialogue;

import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.quest.QuestManager;
import com.adeptum.questai.service.QuestGenerationService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages NPC dialogue conversations, including AI-driven greetings,
 * casual chat, quest offers, and quest accept/reject flows.
 */
public class ConversationManager {

	private final JavaPlugin plugin;
	private final OpenAiChatModel chatModel;
	private final Map<UUID, ConversationState> conversations = new ConcurrentHashMap<>();

	private QuestManager questManager;
	private QuestGenerationService questService;
	private BiConsumer<Player, Quest> questAcceptHandler;

	public ConversationManager(final JavaPlugin plugin, final OpenAiChatModel chatModel) {
		this.plugin = plugin;
		this.chatModel = chatModel;
	}

	// ------------------- Setters (break circular dependencies) -------------------

	public void setQuestManager(final QuestManager questManager) {
		this.questManager = questManager;
	}

	public void setQuestService(final QuestGenerationService questService) {
		this.questService = questService;
	}

	public void setQuestAcceptHandler(final BiConsumer<Player, Quest> handler) {
		this.questAcceptHandler = handler;
	}

	// ------------------- Conversation lifecycle -------------------

	public void startConversation(final Player player, final UUID npcUuid,
		final String npcName, final String profession, final boolean questAvailable) {

		if (isInConversation(player)) {
			endConversation(player);
		}

		final ConversationState state = ConversationState.builder()
			.phase(ConversationPhase.GREETING)
			.npcUuid(npcUuid)
			.npcName(npcName)
			.npcProfession(profession)
			.questAvailable(questAvailable)
			.build();

		conversations.put(player.getUniqueId(), state);
		player.openInventory(DialogueGui.createThinking(npcName, profession));

		final String prompt = DialoguePrompts.greeting(npcName, profession);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String response = callAi(prompt);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setLastAiResponse(response);
					openGuiSync(player,
						DialogueGui.createGreeting(npcName, profession, response));
				});
			} catch (final Exception e) {
				plugin.getLogger().log(Level.SEVERE,
					"AI greeting call failed for " + npcName, e);
				Bukkit.getScheduler().runTask(plugin, () -> {
					final String fallback = "Hello there, traveler.";
					state.setLastAiResponse(fallback);
					openGuiSync(player,
						DialogueGui.createGreeting(npcName, profession, fallback));
				});
			}
		});
	}

	public void handleClick(final Player player, final int slot) {
		final ConversationState state = conversations.get(player.getUniqueId());
		if (state == null) {
			return;
		}

		final String npcName = state.getNpcName();
		final String profession = state.getNpcProfession();

		switch (state.getPhase()) {
			case GREETING -> handleGreeting(player, slot, state, npcName, profession);
			case OPTIONS -> handleOptions(player, slot, state, npcName, profession);
			case QUEST_OFFER -> handleQuestOffer(player, slot, state, npcName);
			case QUEST_ACCEPT_REJECT -> handleQuestAcceptReject(player, slot, state);
			case CHAT_RESPONSE -> handleChatResponse(player, slot, state, npcName, profession);
		}
	}

	public void endConversation(final Player player) {
		conversations.remove(player.getUniqueId());
		player.closeInventory();
	}

	public boolean isInConversation(final Player player) {
		return conversations.containsKey(player.getUniqueId());
	}

	public ConversationState getState(final Player player) {
		return conversations.get(player.getUniqueId());
	}

	// ------------------- Phase handlers -------------------

	private void handleGreeting(final Player player, final int slot,
		final ConversationState state, final String npcName, final String profession) {

		if (slot != DialogueGui.OPTION_2_SLOT) {
			return;
		}

		state.setPhase(ConversationPhase.OPTIONS);
		final boolean canAskQuest =
			state.isQuestAvailable() && questManager.getQuestProgress(player) == null;
		player.openInventory(
			DialogueGui.createOptions(npcName, state.getLastAiResponse(), canAskQuest));
	}

	private void handleOptions(final Player player, final int slot,
		final ConversationState state, final String npcName, final String profession) {

		if (slot == DialogueGui.OPTION_1_SLOT) {
			startQuestOffer(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_2_SLOT) {
			startCasualChat(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_3_SLOT) {
			endConversation(player);
		}
	}

	private void handleQuestOffer(final Player player, final int slot,
		final ConversationState state, final String npcName) {

		if (slot != DialogueGui.OPTION_2_SLOT) {
			return;
		}

		state.setPhase(ConversationPhase.QUEST_ACCEPT_REJECT);
		player.openInventory(
			DialogueGui.createQuestAcceptReject(npcName, state.getPendingQuest()));
	}

	private void handleQuestAcceptReject(final Player player, final int slot,
		final ConversationState state) {

		final Quest quest = state.getPendingQuest();

		if (slot == DialogueGui.OPTION_1_SLOT) {
			questManager.assignQuest(player, quest);
			questManager.setVillagerData(state.getNpcUuid(),
				Npc.builder()
					.quest(quest)
					.timestamp(System.currentTimeMillis())
					.build());

			if (questAcceptHandler != null) {
				questAcceptHandler.accept(player, quest);
			}

			player.sendMessage("§aYou have accepted the quest: " + quest.getTitle());
			player.playSound(player.getLocation(),
				Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
			endConversation(player);

		} else if (slot == DialogueGui.OPTION_3_SLOT) {
			questManager.setVillagerData(state.getNpcUuid(), null);
			player.sendMessage("§cYou have rejected the quest: " + quest.getShortTitle());
			player.playSound(player.getLocation(),
				Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
			endConversation(player);
		}
	}

	private void handleChatResponse(final Player player, final int slot,
		final ConversationState state, final String npcName, final String profession) {

		if (slot == DialogueGui.OPTION_1_SLOT) {
			startCasualChat(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_2_SLOT) {
			startQuestOffer(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_3_SLOT) {
			endConversation(player);
		}
	}

	// ------------------- Async flows -------------------

	private void startQuestOffer(final Player player, final ConversationState state,
		final String npcName, final String profession) {

		final boolean canAskQuest =
			state.isQuestAvailable() && questManager.getQuestProgress(player) == null;
		if (!canAskQuest) {
			return;
		}

		state.setPhase(ConversationPhase.QUEST_OFFER);
		player.openInventory(DialogueGui.createThinking(npcName, profession));

		final Quest quest = questService.buildQuest(
			questService.generateRandomObjective(),
			state.getNpcUuid(),
			player.getWorld());

		questService.generateQuestDescription(quest, () -> {
			// Quest now has shortTitle and title set; generate narrative
			final String narrativePrompt =
				DialoguePrompts.questNarrative(npcName, profession, quest);

			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					final String narrative = callAi(narrativePrompt);
					Bukkit.getScheduler().runTask(plugin, () -> {
						state.setLastAiResponse(narrative);
						state.setPendingQuest(quest);
						openGuiSync(player,
							DialogueGui.createQuestOffer(npcName, narrative));
					});
				} catch (final Exception e) {
					plugin.getLogger().log(Level.SEVERE,
						"AI narrative call failed for quest " + quest.getShortTitle(), e);
					Bukkit.getScheduler().runTask(plugin, () -> {
						final String fallback = "I have a task for you, traveler.";
						state.setLastAiResponse(fallback);
						state.setPendingQuest(quest);
						openGuiSync(player,
							DialogueGui.createQuestOffer(npcName, fallback));
					});
				}
			});
		}, error -> {
			plugin.getLogger().log(Level.SEVERE,
				"Quest description generation failed", error);
			Bukkit.getScheduler().runTask(plugin, () -> {
				quest.setShortTitle("Mysterious Quest");
				quest.setTitle("A mysterious quest awaits...");
				state.setLastAiResponse("I have a task for you, traveler.");
				state.setPendingQuest(quest);
				openGuiSync(player,
					DialogueGui.createQuestOffer(npcName, state.getLastAiResponse()));
			});
		});
	}

	private void startCasualChat(final Player player, final ConversationState state,
		final String npcName, final String profession) {

		state.setPhase(ConversationPhase.CHAT_RESPONSE);
		player.openInventory(DialogueGui.createThinking(npcName, profession));

		final boolean canAskQuest =
			state.isQuestAvailable() && questManager.getQuestProgress(player) == null;
		final String prompt = DialoguePrompts.casualChat(npcName, profession);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String response = callAi(prompt);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setLastAiResponse(response);
					openGuiSync(player,
						DialogueGui.createChatResponse(npcName, response, canAskQuest));
				});
			} catch (final Exception e) {
				plugin.getLogger().log(Level.SEVERE,
					"AI casual chat call failed for " + npcName, e);
				Bukkit.getScheduler().runTask(plugin, () -> {
					final String fallback = "Nice weather today, isn't it?";
					state.setLastAiResponse(fallback);
					openGuiSync(player,
						DialogueGui.createChatResponse(npcName, fallback, canAskQuest));
				});
			}
		});
	}

	// ------------------- Helpers -------------------

	private String callAi(final String prompt) {
		final ChatRequest request = ChatRequest.builder()
			.messages(UserMessage.from(prompt))
			.build();
		final String text = chatModel.chat(request).aiMessage().text().trim();
		return text.isEmpty() ? "..." : text;
	}

	private void openGuiSync(final Player player, final Inventory inventory) {
		Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(inventory));
	}
}
