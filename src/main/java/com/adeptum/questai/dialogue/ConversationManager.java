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

import com.adeptum.questai.craft.Commission;
import com.adeptum.questai.craft.CommissionDesk;
import com.adeptum.questai.craft.CommissionGate;
import com.adeptum.questai.craft.CommissionOrder;
import com.adeptum.questai.craft.CommissionOrders;
import com.adeptum.questai.fortify.MaterialTally;
import com.adeptum.questai.fortify.VillageWork;
import com.adeptum.questai.fortify.VillageWorksStore;
import com.adeptum.questai.fortify.WorkState;
import com.adeptum.questai.model.world.Npc;
import com.adeptum.questai.model.world.quest.Quest;
import com.adeptum.questai.model.world.quest.QuestObjective;
import com.adeptum.questai.quest.QuestChains;
import com.adeptum.questai.quest.QuestManager;
import com.adeptum.questai.reputation.Reputation;
import com.adeptum.questai.reputation.Standings;
import com.adeptum.questai.service.QuestGenerationService;
import com.adeptum.questai.utility.AiChat;
import com.adeptum.questai.village.NamedVillage;
import com.adeptum.questai.village.VillageRegistry;
import com.adeptum.questai.villager.ChainState;
import com.adeptum.questai.villager.MemoryEvent;
import com.adeptum.questai.villager.Relationship;
import com.adeptum.questai.villager.MemorySummarizer;
import com.adeptum.questai.villager.VillagerProfile;
import com.adeptum.questai.villager.VillagerProfileStore;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Manages NPC dialogue conversations, including AI-driven greetings,
 * casual chat, quest offers, and quest accept/reject flows.
 */
public class ConversationManager {

	private final JavaPlugin plugin;
	private final OpenAiChatModel chatModel;
	private final VillagerProfileStore profileStore;
	private final Map<UUID, ConversationState> conversations = new ConcurrentHashMap<>();

	private QuestManager questManager;
	private QuestGenerationService questService;
	private BiConsumer<Player, Quest> questAcceptHandler;
	private VillageWorksStore worksStore;
	private BiConsumer<Player, String> workDonationHandler;
	private Standings standings;
	private VillageRegistry registry;
	private BiConsumer<Player, String> stoneClaimHandler;
	private Predicate<String> stoneIssuedCheck;
	private CommissionDesk commissionDesk;
	private CommissionOrders commissionOrderHandler;
	private BiConsumer<Player, String> commissionCollectHandler;

	public ConversationManager(final JavaPlugin plugin, final OpenAiChatModel chatModel,
		final VillagerProfileStore profileStore) {

		this.plugin = plugin;
		this.chatModel = chatModel;
		this.profileStore = profileStore;
	}

	public void setQuestManager(final QuestManager questManager) {
		this.questManager = questManager;
	}

	public void setQuestService(final QuestGenerationService questService) {
		this.questService = questService;
	}

	public void setQuestAcceptHandler(final BiConsumer<Player, Quest> handler) {
		this.questAcceptHandler = handler;
	}

	public void setWorksStore(final VillageWorksStore worksStore) {
		this.worksStore = worksStore;
	}

	/** Called with the player and the village row id when they donate. */
	public void setWorkDonationHandler(
		final BiConsumer<Player, String> handler) {

		this.workDonationHandler = handler;
	}

	public void setStandings(final Standings standings) {
		this.standings = standings;
	}

	public void setRegistry(final VillageRegistry registry) {
		this.registry = registry;
	}

	/** Called with the player and village row id when they claim a stone. */
	public void setStoneClaimHandler(
		final BiConsumer<Player, String> handler) {

		this.stoneClaimHandler = handler;
	}

	/** Answers whether a village's stone is already out in the world. */
	public void setStoneIssuedCheck(final Predicate<String> check) {
		this.stoneIssuedCheck = check;
	}

	public void setCommissionDesk(final CommissionDesk desk) {
		this.commissionDesk = desk;
	}

	/** Called when the player agrees to a piece the craftsman named. */
	public void setCommissionOrderHandler(final CommissionOrders handler) {
		this.commissionOrderHandler = handler;
	}

	/** Called with the player and village row id when they collect. */
	public void setCommissionCollectHandler(
		final BiConsumer<Player, String> handler) {

		this.commissionCollectHandler = handler;
	}

	public void startConversation(final Player player, final UUID npcUuid,
		final String npcName, final String profession,
		final boolean questAvailable, final boolean tradeable,
		final String villageRowId) {

		if (isInConversation(player)) {
			endConversation(player);
		}

		final ConversationState state = ConversationState.builder()
			.phase(ConversationPhase.THINKING)
			.npcUuid(npcUuid)
			.npcName(npcName)
			.npcProfession(profession)
			.questAvailable(questAvailable)
			.tradeable(tradeable)
			.build();
		state.setVillageRowId(villageRowId);

		conversations.put(player.getUniqueId(), state);
		player.openInventory(DialogueGui.createThinking(npcName, profession));
		player.playSound(player.getLocation(),
			Sound.ENTITY_VILLAGER_AMBIENT, 1.0f, 1.0f);

		final String context = contextFor(npcUuid, player, villageRowId);
		if (profileStore != null) {
			profileStore.recordConversation(npcUuid, player.getUniqueId());
		}

		final String prompt = DialoguePrompts.greeting(npcName, profession, context);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String response = callAi(prompt);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setPhase(ConversationPhase.GREETING);
					state.setLastAiResponse(response);
					openGuiSync(player,
						DialogueGui.createGreeting(npcName, profession, response));
				});
			} catch (final Exception e) {
				plugin.getLogger().log(Level.SEVERE,
					"AI greeting call failed for " + npcName, e);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setPhase(ConversationPhase.GREETING);
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
			case GREETING -> handleGreeting(player, slot, state, npcName);
			case OPTIONS -> handleOptions(player, slot, state, npcName, profession);
			case QUEST_OFFER -> handleQuestOffer(player, slot, state, npcName);
			case QUEST_ACCEPT_REJECT -> handleQuestAcceptReject(player, slot, state);
			case CHAT_RESPONSE -> handleChatResponse(player, slot, state, npcName, profession);
			case WORK_OFFER -> handleWorkOffer(player, slot, state);
			case STONE_OFFER -> handleStoneOffer(player, slot, state);
			case COMMISSION_OFFER ->
				handleCommissionOffer(player, slot, state);
			case COMMISSION_WAIT -> endConversation(player);
			case COMMISSION_COLLECT ->
				handleCommissionCollect(player, slot, state);
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

	private void handleGreeting(final Player player, final int slot,
		final ConversationState state, final String npcName) {

		if (slot != DialogueGui.CENTER_SLOT) {
			return;
		}

		state.setPhase(ConversationPhase.OPTIONS);
		player.openInventory(
			DialogueGui.createOptions(npcName, state.getLastAiResponse(),
				optionsFor(player, state)));
	}

	/** Which doors this villager's main screen should show right now. */
	private DialogueOptions optionsFor(final Player player,
		final ConversationState state) {

		final CommissionOrder open = openCommission(player, state);
		final boolean ready = canCollectCommission(player, state);
		return DialogueOptions.builder()
			.questAvailable(state.isQuestAvailable())
			.tradeable(state.isTradeable())
			.worksOpen(hasOpenWorks(player, state))
			.stoneOffer(canClaimStone(player, state))
			.commissionOffer(open == null
				&& commissionOffer(player, state) != null)
			.commissionWaiting(open != null && !ready)
			.commissionReady(ready)
			.build();
	}

	private void handleOptions(final Player player, final int slot,
		final ConversationState state, final String npcName, final String profession) {

		if (slot == DialogueGui.OPTION_1_SLOT) {
			startCasualChat(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_2_SLOT && state.isQuestAvailable()) {
			startQuestOffer(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_3_SLOT && state.isTradeable()) {
			openVillagerTrade(player, state.getNpcUuid());
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		} else if (slot == DialogueGui.CENTER_SLOT && hasOpenWorks(player, state)) {
			startWorkOffer(player, state, npcName);
		} else if (slot == DialogueGui.CENTER_SLOT && canClaimStone(player, state)) {
			startStoneOffer(player, state, npcName);
		} else if (slot == DialogueGui.CRAFT_SLOT) {
			handleCraftSlot(player, state, npcName);
		}
	}

	/**
	 * The craftsman's slot means one of three things depending on what the
	 * village already has in hand for this player, so the decision lives
	 * here rather than adding three more arms to the options chain.
	 */
	private void handleCraftSlot(final Player player,
		final ConversationState state, final String npcName) {

		if (canCollectCommission(player, state)) {
			startCommissionCollect(player, state, npcName);
			return;
		}
		final CommissionOrder open = openCommission(player, state);
		if (open != null) {
			startCommissionWait(player, state, npcName, open);
		} else if (commissionOffer(player, state) != null) {
			startCommissionOffer(player, state, npcName);
		}
	}

	private void handleQuestOffer(final Player player, final int slot,
		final ConversationState state, final String npcName) {

		if (slot != DialogueGui.CENTER_SLOT) {
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
			recordQuestEvent(state.getNpcUuid(), player,
				MemoryEvent.Type.QUEST_ACCEPTED, quest);

			if (questAcceptHandler != null) {
				questAcceptHandler.accept(player, quest);
			}

			player.sendMessage("\u00a7aYou have accepted the quest: " + quest.getTitle());
			player.playSound(player.getLocation(),
				Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
			endConversation(player);

		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			questManager.setVillagerData(state.getNpcUuid(), null);
			recordQuestEvent(state.getNpcUuid(), player,
				MemoryEvent.Type.QUEST_REJECTED, quest);
			if (quest.getChainStep() > 0 && profileStore != null) {
				profileStore.clearChain(state.getNpcUuid(), player.getUniqueId());
			}
			player.sendMessage("\u00a7cYou have rejected the quest: "
				+ quest.getShortTitle());
			player.playSound(player.getLocation(),
				Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
			endConversation(player);
		}
	}

	private void handleChatResponse(final Player player, final int slot,
		final ConversationState state, final String npcName, final String profession) {

		if (slot == DialogueGui.OPTION_1_SLOT) {
			startCasualChat(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_2_SLOT && state.isQuestAvailable()) {
			startQuestOffer(player, state, npcName, profession);
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		}
	}

	private boolean hasOpenWorks(final Player player,
		final ConversationState state) {

		if (worksStore == null || state.getVillageRowId() == null) {
			return false;
		}
		if (!tradesWith(player, state.getVillageRowId())) {
			return false;
		}
		final WorkState works = worksStore.get(state.getVillageRowId());
		final int tier = works == null ? 0 : works.getTier();
		final VillageWork work = VillageWork.byTier(tier);
		return work != null && work.hasPlans();
	}

	private boolean tradesWith(final Player player, final String villageRowId) {
		return standings == null || Reputation.tradesWith(
			standings.of(villageRowId, player.getUniqueId()));
	}

	private void startWorkOffer(final Player player,
		final ConversationState state, final String npcName) {

		final WorkState works = worksStore.get(state.getVillageRowId());
		final VillageWork work =
			VillageWork.byTier(works == null ? 0 : works.getTier());
		if (work == null || !work.hasPlans()) {
			return;
		}

		final Map<String, Integer> outstanding = new LinkedHashMap<>();
		final Map<String, Integer> carried = new LinkedHashMap<>();
		work.getRequirements().forEach((role, needed) -> {
			final int have = works == null ? 0
				: works.getTally().getOrDefault(role, 0);
			outstanding.put(role, Math.max(0, needed - have));
			carried.put(role, MaterialTally.count(
				player.getInventory().getContents(), work.accepted(role)));
		});

		state.setPhase(ConversationPhase.WORK_OFFER);
		player.openInventory(
			DialogueGui.createWorkOffer(npcName, work, outstanding, carried));
	}

	private void handleWorkOffer(final Player player, final int slot,
		final ConversationState state) {

		if (slot == DialogueGui.OPTION_1_SLOT && workDonationHandler != null) {
			workDonationHandler.accept(player, state.getVillageRowId());
			endConversation(player);
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		}
	}

	/**
	 * Whether this villager may offer a teleport stone: the village must be
	 * fully fortified, the player trusted enough to trade, and no stone for
	 * the village may already exist anywhere the world scan can reach.
	 */
	private boolean canClaimStone(final Player player,
		final ConversationState state) {

		if (stoneClaimHandler == null || stoneIssuedCheck == null
			|| registry == null || worksStore == null
			|| state.getVillageRowId() == null
			|| !tradesWith(player, state.getVillageRowId())) {
			return false;
		}
		final WorkState works = worksStore.get(state.getVillageRowId());
		return works != null && works.getTier() >= VillageWork.count()
			&& !stoneIssuedCheck.test(state.getVillageRowId());
	}

	private void startStoneOffer(final Player player,
		final ConversationState state, final String npcName) {

		final NamedVillage village = registry.byRowId(state.getVillageRowId());
		if (village == null) {
			return;
		}
		state.setPhase(ConversationPhase.STONE_OFFER);
		player.openInventory(
			DialogueGui.createStoneOffer(npcName, village.name()));
	}

	/** The piece this craftsman would take on, or null for none. */
	private Commission commissionOffer(final Player player,
		final ConversationState state) {

		if (commissionDesk == null || commissionOrderHandler == null) {
			return null;
		}
		return commissionDesk.offerFor(state.getNpcProfession(),
			state.getVillageRowId(), player.getUniqueId());
	}

	/** The work this village already has in hand for the player, or null. */
	private CommissionOrder openCommission(final Player player,
		final ConversationState state) {

		return commissionDesk == null ? null
			: commissionDesk.openOrder(state.getVillageRowId(),
				player.getUniqueId());
	}

	/** Whether this villager will hand a finished piece over right now. */
	private boolean canCollectCommission(final Player player,
		final ConversationState state) {

		return commissionDesk != null && commissionCollectHandler != null
			&& commissionDesk.collectable(state.getNpcProfession(),
				state.getVillageRowId(), player.getUniqueId(),
				System.currentTimeMillis());
	}

	private void startCommissionOffer(final Player player,
		final ConversationState state, final String npcName) {

		final Commission commission = commissionOffer(player, state);
		if (commission == null) {
			return;
		}
		final Map<String, Integer> carried = new LinkedHashMap<>();
		commission.getRequirements().forEach((role, needed) -> carried.put(role,
			commissionDesk.carried(player.getInventory().getContents(),
				commission, role)));

		state.setPhase(ConversationPhase.COMMISSION_OFFER);
		player.openInventory(DialogueGui.createCommissionOffer(npcName,
			commission, commission.getRequirements(), carried));
	}

	private void handleCommissionOffer(final Player player, final int slot,
		final ConversationState state) {

		final Commission commission = commissionOffer(player, state);
		if (slot == DialogueGui.OPTION_1_SLOT && commission != null) {
			commissionOrderHandler.place(player, state.getVillageRowId(),
				state.getNpcUuid(), commission.name());
			endConversation(player);
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		}
	}

	private void startCommissionWait(final Player player,
		final ConversationState state, final String npcName,
		final CommissionOrder order) {

		final Commission commission = Commission.byName(order.commission());
		if (commission == null) {
			return;
		}
		state.setPhase(ConversationPhase.COMMISSION_WAIT);
		player.openInventory(DialogueGui.createCommissionWait(npcName,
			commission, CommissionGate.remainingMinutes(order.readyAt(),
				System.currentTimeMillis())));
	}

	private void startCommissionCollect(final Player player,
		final ConversationState state, final String npcName) {

		final CommissionOrder order = openCommission(player, state);
		final Commission commission =
			order == null ? null : Commission.byName(order.commission());
		if (commission == null) {
			return;
		}
		state.setPhase(ConversationPhase.COMMISSION_COLLECT);
		player.openInventory(
			DialogueGui.createCommissionCollect(npcName, commission));
	}

	private void handleCommissionCollect(final Player player, final int slot,
		final ConversationState state) {

		if (slot == DialogueGui.OPTION_1_SLOT
			&& commissionCollectHandler != null) {

			commissionCollectHandler.accept(player, state.getVillageRowId());
			endConversation(player);
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		}
	}

	private void handleStoneOffer(final Player player, final int slot,
		final ConversationState state) {

		if (slot == DialogueGui.OPTION_1_SLOT && stoneClaimHandler != null) {
			stoneClaimHandler.accept(player, state.getVillageRowId());
			endConversation(player);
		} else if (slot == DialogueGui.OPTION_4_SLOT) {
			endConversation(player);
		}
	}

	private void startQuestOffer(final Player player, final ConversationState state,
		final String npcName, final String profession) {

		if (!state.isQuestAvailable()) {
			return;
		}

		state.setPhase(ConversationPhase.THINKING);
		player.openInventory(DialogueGui.createThinking(npcName, profession));

		final Quest quest = questService.generateQuest(
			state.getNpcUuid(), player.getWorld(), player.getLocation());
		final ChainState chain = profileStore == null ? null
			: profileStore.chainState(state.getNpcUuid(), player.getUniqueId());
		applyChain(quest, chain);
		final String context = chainContext(chain, deliveryContext(quest,
			contextFor(state.getNpcUuid(), player, state.getVillageRowId())));

		questService.generateQuestDescription(quest, () -> {
			final String narrativePrompt =
				DialoguePrompts.questNarrative(npcName, profession, quest, context);

			Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
				try {
					final String narrative = callAi(narrativePrompt);
					Bukkit.getScheduler().runTask(plugin, () -> {
						state.setPhase(ConversationPhase.QUEST_OFFER);
						state.setLastAiResponse(narrative);
						state.setPendingQuest(quest);
						openGuiSync(player,
							DialogueGui.createQuestOffer(npcName, narrative));
					});
				} catch (final Exception e) {
					plugin.getLogger().log(Level.SEVERE,
						"AI narrative call failed for quest "
							+ quest.getShortTitle(), e);
					Bukkit.getScheduler().runTask(plugin, () -> {
						state.setPhase(ConversationPhase.QUEST_OFFER);
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
				state.setPhase(ConversationPhase.QUEST_OFFER);
				quest.setShortTitle("Mysterious Quest");
				quest.setTitle("A mysterious quest awaits...");
				state.setLastAiResponse("I have a task for you, traveler.");
				state.setPendingQuest(quest);
				openGuiSync(player,
					DialogueGui.createQuestOffer(npcName,
						state.getLastAiResponse()));
			});
		});
	}

	private void startCasualChat(final Player player, final ConversationState state,
		final String npcName, final String profession) {

		state.setPhase(ConversationPhase.THINKING);
		player.openInventory(DialogueGui.createThinking(npcName, profession));

		final boolean canOfferHelp = state.isQuestAvailable();
		final String context = contextFor(state.getNpcUuid(), player,
			state.getVillageRowId());
		final String prompt = canOfferHelp
			? DialoguePrompts.casualChatWithQuestHint(npcName, profession, context)
			: DialoguePrompts.casualChat(npcName, profession, context);

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				final String response = callAi(prompt);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setPhase(ConversationPhase.CHAT_RESPONSE);
					state.setLastAiResponse(response);
					openGuiSync(player,
						DialogueGui.createChatResponse(npcName, response,
							canOfferHelp));
				});
			} catch (final Exception e) {
				plugin.getLogger().log(Level.SEVERE,
					"AI casual chat call failed for " + npcName, e);
				Bukkit.getScheduler().runTask(plugin, () -> {
					state.setPhase(ConversationPhase.CHAT_RESPONSE);
					final String fallback = canOfferHelp
						? "Things have been difficult lately..."
							+ " I could really use some help."
						: "Nice weather today, isn't it?";
					state.setLastAiResponse(fallback);
					openGuiSync(player,
						DialogueGui.createChatResponse(npcName, fallback,
							canOfferHelp));
				});
			}
		});
	}

	private String callAi(final String prompt) {
		return AiChat.ask(chatModel, prompt, "...");
	}

	/**
	 * Tags a quest as its chain step and scales the reward, before the
	 * async description call so the prompt and GUI both see final values.
	 */
	private void applyChain(final Quest quest, final ChainState chain) {
		if (chain != null) {
			quest.setChainStep(chain.step());
			quest.setChainLength(chain.length());
			quest.setRewardAmount(
				QuestChains.scaledReward(quest.getRewardAmount(), chain.step()));
		}
	}

	private String chainContext(final ChainState chain, final String context) {
		return chain == null ? context
			: context + " " + QuestChains.context(chain);
	}

	/**
	 * Adds the delivery recipient to the narrative context so the giver
	 * talks about who the package is for, without any extra model call.
	 */
	private String deliveryContext(final Quest quest, final String context) {
		if (quest.getObjective().getType() != QuestObjective.Type.DELIVERY
			|| profileStore == null) {
			return context;
		}
		final VillagerProfile recipient = profileStore.get(quest.getRecipientUuid());
		if (recipient == null) {
			return context;
		}
		final Relationship tie = profileStore.tieBetween(
			quest.getVillagerUuid(), quest.getRecipientUuid());
		return context + " You are sending a sealed package to "
			+ (tie == null ? "" : "your " + tie.type().noun() + " ")
			+ recipient.getName() + ", the village "
			+ recipient.getProfession() + ".";
	}

	private String contextFor(final UUID npcUuid, final Player player,
		final String villageRowId) {

		if (profileStore == null) {
			return "";
		}
		final VillagerProfile profile = profileStore.get(npcUuid);
		if (profile == null) {
			return "";
		}
		final String clause = standings == null ? null
			: Reputation.standingClause(
				standings.of(villageRowId, player.getUniqueId()));
		return MemorySummarizer.context(profile, player.getUniqueId(), clause);
	}

	private void recordQuestEvent(final UUID npcUuid, final Player player,
		final MemoryEvent.Type type, final Quest quest) {

		if (profileStore != null) {
			profileStore.recordEvent(npcUuid, player.getUniqueId(),
				type, quest.getShortTitle());
		}
	}

	private void openVillagerTrade(final Player player, final UUID npcUuid) {
		endConversation(player);
		final var entity = Bukkit.getEntity(npcUuid);
		if (entity instanceof Villager villager) {
			player.openMerchant(villager, true);
		}
	}

	/**
	 * Opens a GUI inventory. Callers already run on the main thread inside
	 * a scheduled task, so the inventory can be opened directly.
	 */
	private void openGuiSync(final Player player, final Inventory inventory) {
		player.openInventory(inventory);
	}
}
