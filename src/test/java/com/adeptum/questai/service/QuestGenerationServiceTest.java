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
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.EnumSet;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.adeptum.questai.model.world.quest.QuestObjective.Type.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestGenerationServiceTest {

	@Mock
	private JavaPlugin plugin;

	@Mock
	private OpenAiChatModel chatModel;

	@Mock
	private World world;

	private QuestGenerationService service;
	private AutoCloseable mocks;

	@BeforeEach
	void setUp() {
		mocks = MockitoAnnotations.openMocks(this);
		service = new QuestGenerationService(plugin, chatModel);
	}

	@Test
	void generateRandomObjectiveReturnsValidObjective() {
		for (int i = 0; i < 100; i++) {
			final QuestObjective obj = service.generateRandomObjective();

			assertNotNull(obj.getType());
			assertNotNull(obj.getTarget());
			assertTrue(obj.getAmount() >= 1);

			if (obj.getType() == KILL) {
				assertTrue(obj.getAmount() >= 3 && obj.getAmount() <= 50);
			} else if (obj.getType() == COLLECT) {
				assertTrue(obj.getAmount() >= 3 && obj.getAmount() <= 50);
			} else {
				assertEquals("NONE", obj.getTarget());
				assertEquals(1, obj.getAmount());
			}
		}
	}

	@Test
	void buildQuestSetsRewardAndObjective() {
		final QuestObjective obj = new QuestObjective();
		obj.setType(KILL);
		obj.setTarget("ZOMBIE");
		obj.setAmount(10);

		final UUID npcUuid = UUID.randomUUID();
		final Quest quest = service.buildQuest(obj, npcUuid, world);

		assertSame(obj, quest.getObjective());
		assertEquals(npcUuid, quest.getVillagerUuid());
		assertEquals("MCMMO", quest.getRewardType());
		assertNotNull(quest.getRewardTarget());
		assertTrue(quest.getRewardAmount() >= 50 && quest.getRewardAmount() <= 200);
		assertNull(quest.getDestination()); // KILL quests have no destination
	}

	@Test
	void buildQuestSetsDestinationForTreasureQuest() {
		final QuestObjective obj = new QuestObjective();
		obj.setType(TREASURE);
		obj.setTarget("NONE");
		obj.setAmount(1);

		final Location spawn = mock(Location.class);
		when(world.getSpawnLocation()).thenReturn(spawn);
		when(spawn.getX()).thenReturn(0.0);
		when(spawn.getZ()).thenReturn(0.0);

		final org.bukkit.Chunk chunk = mock(org.bukkit.Chunk.class);
		when(world.getChunkAt(anyInt(), anyInt())).thenReturn(chunk);
		when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);

		final Quest quest = service.buildQuest(obj, UUID.randomUUID(), world);

		assertNotNull(quest.getDestination());
	}
}
