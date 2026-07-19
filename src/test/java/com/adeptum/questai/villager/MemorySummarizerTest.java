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

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemorySummarizerTest {

	private static final UUID PLAYER = UUID.randomUUID();

	@Test
	void nullProfileYieldsEmptyContext() {
		assertEquals("", MemorySummarizer.context(null, PLAYER));
	}

	@Test
	void profileWithoutTraitsOrMemoryYieldsEmptyContext() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Edric Stone").build();

		assertEquals("", MemorySummarizer.context(profile, PLAYER));
	}

	@Test
	void traitsAndConversationsAreDescribed() {
		final VillagerProfile profile = VillagerProfile.builder()
			.name("Edric Stone")
			.traits(List.of("gruff", "kind"))
			.build();
		final PlayerMemory memory = new PlayerMemory();
		memory.setConversations(3);
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains("Your personality: gruff; kind."));
		assertTrue(context.contains("talked with this player 3 time(s)"));
	}

	@Test
	void eventsAreNewestFirstAndCappedAtFour() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Edric Stone").build();
		final PlayerMemory memory = new PlayerMemory();
		for (int i = 0; i < 6; i++) {
			memory.getEvents().add(new MemoryEvent(
				MemoryEvent.Type.QUEST_COMPLETED, "Quest " + i, i));
		}
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.indexOf("Quest 5") < context.indexOf("Quest 2"));
		assertTrue(context.contains("Quest 2"));
		assertFalse(context.contains("Quest 1"));
		assertFalse(context.contains("Quest 0"));
	}

	@Test
	void eventTypesUseDistinctPhrasing() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Edric Stone").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.getEvents().add(new MemoryEvent(
			MemoryEvent.Type.QUEST_ACCEPTED, "Hunt", 1));
		memory.getEvents().add(new MemoryEvent(
			MemoryEvent.Type.QUEST_REJECTED, "Chores", 2));
		memory.getEvents().add(new MemoryEvent(
			MemoryEvent.Type.QUEST_COMPLETED, "Harvest", 3));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains("agreed to help with 'Hunt'"));
		assertTrue(context.contains("refused your quest 'Chores'"));
		assertTrue(context.contains("completed your quest 'Harvest'"));
	}

	@Test
	void parcelReceivedNamesTheSender() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Mira Bloom").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.getEvents().add(new MemoryEvent(
			MemoryEvent.Type.PARCEL_RECEIVED, "Edric Stone", 1));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains(
			"delivered a parcel to you from Edric Stone"));
	}

	@Test
	void hearsayUsesRumorPhrasingPerType() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Goran Dusk").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.getHearsay().add(new HearsayEvent(
			MemoryEvent.Type.QUEST_COMPLETED, "Turnip Trouble",
			"Edric Stone", 1));
		memory.getHearsay().add(new HearsayEvent(
			MemoryEvent.Type.QUEST_REJECTED, "Wolf Cull", "Mira Bloom", 2));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains("Word in the village is that they"
			+ " completed 'Turnip Trouble' for Edric Stone."));
		assertTrue(context.contains(
			"turned down Mira Bloom's request."));
	}

	@Test
	void hearsayFollowsFirstHandAndCapsAtTwo() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Goran Dusk").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.getEvents().add(new MemoryEvent(
			MemoryEvent.Type.QUEST_COMPLETED, "First Hand", 1));
		for (int i = 0; i < 3; i++) {
			memory.getHearsay().add(new HearsayEvent(
				MemoryEvent.Type.QUEST_ACCEPTED, "Rumor " + i,
				"Villager " + i, i));
		}
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.indexOf("First Hand")
			< context.indexOf("Word in the village"));
		assertTrue(context.contains("Villager 2"));
		assertTrue(context.contains("Villager 1"));
		assertFalse(context.contains("Villager 0"));
	}

	@Test
	void lengthCapDropsHearsayBeforeFirstHand() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Goran Dusk").build();
		final PlayerMemory memory = new PlayerMemory();
		final String longTitle = "T".repeat(120);
		for (int i = 0; i < 4; i++) {
			memory.getEvents().add(new MemoryEvent(
				MemoryEvent.Type.QUEST_COMPLETED, longTitle + i, i));
		}
		memory.getHearsay().add(new HearsayEvent(
			MemoryEvent.Type.QUEST_COMPLETED, "Rumor", "Edric Stone", 1));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains(longTitle + "3"));
		assertFalse(context.contains("Word in the village"));
	}

	@Test
	void hearsayOnlyStrangerStillGetsContext() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Goran Dusk").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.getHearsay().add(new HearsayEvent(
			MemoryEvent.Type.PARCEL_RECEIVED, "Edric Stone",
			"Mira Bloom", 1));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains(
			"delivered a parcel for Mira Bloom."));
	}

	@Test
	void pendingChainMentionsUnfinishedBusiness() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Edric Stone").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.setConversations(1);
		memory.setChain(new ChainState(2, 3, "The Lost Ledger"));
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains("unfinished business"));
		assertTrue(context.contains("The Lost Ledger"));
	}

	@Test
	void chainClauseSurvivesTheLengthCap() {
		final VillagerProfile profile =
			VillagerProfile.builder().name("Edric Stone").build();
		final PlayerMemory memory = new PlayerMemory();
		memory.setChain(new ChainState(2, 2, "The Lost Ledger"));
		final String longTitle = "T".repeat(150);
		for (int i = 0; i < 4; i++) {
			memory.getEvents().add(new MemoryEvent(
				MemoryEvent.Type.QUEST_COMPLETED, longTitle + i, i));
		}
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.contains("unfinished business"));
	}

	@Test
	void overlongContextDropsOldestEventClauses() {
		final VillagerProfile profile = VillagerProfile.builder()
			.name("Edric Stone")
			.traits(List.of("gruff"))
			.build();
		final PlayerMemory memory = new PlayerMemory();
		final String longTitle = "T".repeat(150);
		for (int i = 0; i < 4; i++) {
			memory.getEvents().add(new MemoryEvent(
				MemoryEvent.Type.QUEST_COMPLETED, longTitle + i, i));
		}
		profile.getPlayers().put(PLAYER, memory);

		final String context = MemorySummarizer.context(profile, PLAYER);
		assertTrue(context.length() <= 400 + 20);
		assertTrue(context.contains("Your personality: gruff."));
		assertTrue(context.contains(longTitle + "3"));
	}
}
